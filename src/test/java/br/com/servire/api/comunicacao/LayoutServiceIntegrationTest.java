package br.com.servire.api.comunicacao;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.comunicacao.dto.LayoutRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayoutServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LayoutService layoutService;

    @Autowired
    private LayoutRepository layoutRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioTenantRepository usuarioTenantRepository;

    private UUID tenantId;
    private UUID outroTenantId;

    @BeforeEach
    void criarTenants() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(
                new Tenant("LY-" + sufixo.substring(0, 8), "slug-" + sufixo, "Paróquia Layout", Tenant.Status.ATIVO));
        tenantId = tenant.getId();

        Tenant outro = tenantRepository.saveAndFlush(
                new Tenant("LO-" + sufixo.substring(0, 8), "outro-" + sufixo, "Outra Paróquia", Tenant.Status.ATIVO));
        outroTenantId = outro.getId();

        TenantContext.set(tenantId);
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    void criarLayoutEListar() {
        LayoutRequest req = new LayoutRequest("Boas-vindas Email", TipoLayout.TODOS, TipoEnvio.EMAIL,
                "Bem-vindo, #PESSOA.PRIMEIRO_NOME#!", "Olá #PESSOA.NOME#", true);
        Layout criado = layoutService.criar(req);

        assertThat(criado.getId()).isNotNull();
        assertThat(criado.getNome()).isEqualTo("Boas-vindas Email");
        assertThat(criado.getTipoLayout()).isEqualTo(TipoLayout.TODOS);
        assertThat(criado.getTipoEnvio()).isEqualTo(TipoEnvio.EMAIL);
        assertThat(criado.getAssunto()).isEqualTo("Bem-vindo, #PESSOA.PRIMEIRO_NOME#!");
        assertThat(criado.isAtivo()).isTrue();

        List<Layout> lista = layoutService.listar(null, null, null, null);
        assertThat(lista).anyMatch(l -> l.getId().equals(criado.getId()));
    }

    @Test
    void filtrarPorTipoEnvioETipoLayout() {
        layoutService.criar(new LayoutRequest("Layout WA", TipoLayout.COROINHA, TipoEnvio.WHATSAPP,
                null, "Olá #PESSOA.NOME#", true));
        layoutService.criar(new LayoutRequest("Layout Email", TipoLayout.MINISTRO, TipoEnvio.EMAIL,
                "Assunto", "Olá #PESSOA.NOME# mandato #PESSOA.MANDATO_FIM#", true));

        assertThat(layoutService.listar(null, TipoEnvio.WHATSAPP, null, null)).hasSize(1);
        assertThat(layoutService.listar(TipoLayout.MINISTRO, null, null, null)).hasSize(1);
        assertThat(layoutService.listar(null, null, null, "Email")).hasSize(1);
    }

    @Test
    void editarLayout() {
        Layout criado = layoutService.criar(new LayoutRequest("Original", TipoLayout.TODOS, TipoEnvio.EMAIL,
                null, "Conteúdo", true));

        Layout editado = layoutService.atualizar(criado.getId(),
                new LayoutRequest("Alterado", TipoLayout.TODOS, TipoEnvio.EMAIL, "Novo assunto", "Novo conteúdo", false));

        assertThat(editado.getNome()).isEqualTo("Alterado");
        assertThat(editado.isAtivo()).isFalse();
    }

    @Test
    void excluirLayout() {
        Layout criado = layoutService.criar(new LayoutRequest("Para excluir", TipoLayout.TODOS, TipoEnvio.EMAIL,
                null, "Conteúdo", true));
        UUID id = criado.getId();

        layoutService.excluir(id);

        assertThatThrownBy(() -> layoutService.buscarPorId(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nomeRepetidoNaParoquiaLancaConflict() {
        layoutService.criar(new LayoutRequest("Nome Duplicado", TipoLayout.TODOS, TipoEnvio.EMAIL,
                null, "Conteúdo", true));

        assertThatThrownBy(() -> layoutService.criar(
                new LayoutRequest("nome duplicado", TipoLayout.TODOS, TipoEnvio.WHATSAPP,
                        null, "Outro", true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void tagErradaLanca400() {
        assertThatThrownBy(() -> layoutService.criar(
                new LayoutRequest("Tag errada", TipoLayout.RESPONSAVEL, TipoEnvio.EMAIL,
                        null, "Olá #RESPONSAVEL.NOME# #TAG.INEXISTENTE#", true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tags que não existem");
    }

    @Test
    void whatsappComMaisDe4096CaracteresLanca400() {
        String conteudoLongo = "A".repeat(4097);
        assertThatThrownBy(() -> layoutService.criar(
                new LayoutRequest("WA longo", TipoLayout.TODOS, TipoEnvio.WHATSAPP,
                        null, conteudoLongo, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("4096");
    }

    @Test
    void outraParoquiaNaoEnxergaOLayout() {
        layoutService.criar(new LayoutRequest("Layout Isolado", TipoLayout.TODOS, TipoEnvio.EMAIL,
                null, "Conteúdo", true));

        TenantContext.clear();
        TenantContext.set(outroTenantId);

        List<Layout> lista = layoutService.listar(null, null, null, null);
        assertThat(lista).noneMatch(l -> l.getNome().equals("Layout Isolado"));
    }
}
