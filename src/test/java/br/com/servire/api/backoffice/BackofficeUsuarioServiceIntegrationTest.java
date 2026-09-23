package br.com.servire.api.backoffice;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.backoffice.dto.AtualizarUsuarioRequest;
import br.com.servire.api.backoffice.dto.CriarUsuarioRequest;
import br.com.servire.api.backoffice.dto.SubstituirVinculosRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackofficeUsuarioServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String SENHA = "SenhaForte123!";

    @Autowired
    private BackofficeUsuarioService backofficeUsuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioTenantRepository usuarioTenantRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BackofficeLogService backofficeLogService;

    @Test
    void criarUsuarioComVinculoPersisteENaoListaOperador() {
        Tenant tenant = criarTenant("usr");
        Usuario criado = backofficeUsuarioService.criar(new CriarUsuarioRequest(
                "Padre Teste",
                "padre-" + UUID.randomUUID() + "@teste.com",
                SENHA,
                true,
                List.of(new CriarUsuarioRequest.Vinculo(
                        tenant.getId(), UsuarioTenant.Role.COORDENADOR, UsuarioTenant.Status.ATIVO))));

        assertThat(criado.isOperadorSaas()).isFalse();
        assertThat(usuarioTenantRepository.findByUsuario_Id(criado.getId())).hasSize(1);
        assertThat(backofficeUsuarioService.listar(true, "Padre Teste"))
                .anyMatch(u -> u.getId().equals(criado.getId()));

        Usuario operador = new Usuario("op-" + UUID.randomUUID() + "@teste.com", "Operador");
        operador.setOperadorSaas(true);
        operador.setSenhaHash(passwordEncoder.encode(SENHA));
        usuarioRepository.saveAndFlush(operador);
        assertThat(backofficeUsuarioService.listar(null, operador.getEmail())).isEmpty();
        assertThatThrownBy(() -> backofficeUsuarioService.buscar(operador.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void emailDuplicadoLancaConflictException() {
        Usuario existente = new Usuario("dup-" + UUID.randomUUID() + "@teste.com", "Já existe");
        existente.setSenhaHash(passwordEncoder.encode(SENHA));
        usuarioRepository.saveAndFlush(existente);

        assertThatThrownBy(() -> backofficeUsuarioService.criar(new CriarUsuarioRequest(
                "Outro", existente.getEmail(), SENHA, true, List.of())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void atualizarTrocaNomeEmailESenha() {
        Usuario usuario = backofficeUsuarioService.criar(new CriarUsuarioRequest(
                "Antes", "antes-" + UUID.randomUUID() + "@teste.com", SENHA, true, List.of()));

        Usuario atualizado = backofficeUsuarioService.atualizar(usuario.getId(), new AtualizarUsuarioRequest(
                "Depois", "depois-" + UUID.randomUUID() + "@teste.com", "NovaSenha123", false));

        assertThat(atualizado.getNome()).isEqualTo("Depois");
        assertThat(atualizado.isAtivo()).isFalse();
        assertThat(passwordEncoder.matches("NovaSenha123", atualizado.getSenhaHash())).isTrue();
    }

    @Test
    void substituirVinculosRemoveOAntigoECriaONovo() {
        Tenant a = criarTenant("va");
        Tenant b = criarTenant("vb");
        Usuario usuario = backofficeUsuarioService.criar(new CriarUsuarioRequest(
                "Coord", "coord-" + UUID.randomUUID() + "@teste.com", SENHA, true,
                List.of(new CriarUsuarioRequest.Vinculo(a.getId(), UsuarioTenant.Role.ADMIN, null))));

        List<UsuarioTenant> depois = backofficeUsuarioService.substituirVinculos(usuario.getId(), List.of(
                new SubstituirVinculosRequest.Vinculo(b.getId(), UsuarioTenant.Role.VISUALIZADOR, UsuarioTenant.Status.ATIVO)));

        assertThat(depois).hasSize(1);
        assertThat(depois.getFirst().getTenant().getId()).isEqualTo(b.getId());
        assertThat(depois.getFirst().getRole()).isEqualTo(UsuarioTenant.Role.VISUALIZADOR);
        assertThat(usuarioTenantRepository.findByUsuario_IdAndTenant_Id(usuario.getId(), a.getId())).isEmpty();
        assertThat(backofficeLogService.listar())
                .anyMatch(l -> "VINCULOS".equals(l.getAcao()) && usuario.getId().equals(l.getEntidadeId()));
    }

    private Tenant criarTenant(String rotulo) {
        return tenantRepository.saveAndFlush(new Tenant(
                "T-" + rotulo + "-" + UUID.randomUUID().toString().substring(0, 8),
                "t-" + rotulo + "-" + UUID.randomUUID(),
                "Paróquia " + rotulo,
                Tenant.Status.ATIVO));
    }
}
