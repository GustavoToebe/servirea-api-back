package br.com.servire.api.voluntario;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.dto.VoluntarioResponse;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class VoluntarioServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VoluntarioService voluntarioService;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @MockitoBean
    private StorageService storageService;

    @BeforeEach
    void definirTenant() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-VOL-TESTE-" + sufixo, "tenant-vol-teste-" + sufixo, "Paróquia de teste (voluntario)",
                Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void definirFotoChamaStorageServiceEGravaCaminhoRetornado() {
        Voluntario criado = Pessoas.persistirVoluntario(pessoaRepository, "Com Foto");
        MockMultipartFile foto = new MockMultipartFile("foto", "perfil.png", "image/png", new byte[]{1, 2, 3});
        when(storageService.armazenar(anyString(), any(byte[].class), Mockito.eq("image/png")))
                .thenReturn("caminho-retornado-pelo-storage.png");

        Voluntario atualizado = voluntarioService.definirFoto(criado.getId(), foto);

        assertThat(atualizado.getFotoPath()).isEqualTo("caminho-retornado-pelo-storage.png");
        assertThatCode(() -> VoluntarioResponse.de(atualizado)).doesNotThrowAnyException();
    }

    @Test
    void definirFotoSemArquivoLancaBadRequestException() {
        Voluntario criado = Pessoas.persistirVoluntario(pessoaRepository, "Sem Arquivo");

        assertThatThrownBy(() -> voluntarioService.definirFoto(criado.getId(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void obterUrlFotoSemFotoCadastradaLancaResourceNotFoundException() {
        Voluntario criado = Pessoas.persistirVoluntario(pessoaRepository, "Sem Foto");

        assertThatThrownBy(() -> voluntarioService.obterUrlFoto(criado.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarSemFiltrosNaoLancaExcecaoDeSql() {
        Pessoas.persistirVoluntario(pessoaRepository, "Listável");

        List<Voluntario> todos = voluntarioService.buscar(null, null, null);

        assertThat(todos).isNotEmpty();
        assertThatCode(() -> todos.forEach(VoluntarioResponse::de)).doesNotThrowAnyException();
    }

    @Test
    void setAtivoAtualizaFlag() {
        Voluntario criado = Pessoas.persistirVoluntario(pessoaRepository, "Ativo");
        assertThat(criado.isAtivo()).isTrue();

        voluntarioService.setAtivo(criado.getId(), false);

        assertThat(voluntarioRepository.findById(criado.getId())).get()
                .extracting(Voluntario::isAtivo).isEqualTo(false);
    }
}
