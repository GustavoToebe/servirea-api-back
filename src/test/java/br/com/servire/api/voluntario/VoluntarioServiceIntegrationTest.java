package br.com.servire.api.voluntario;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.dto.ResponsavelRequest;
import br.com.servire.api.voluntario.dto.VoluntarioRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Testes de regra de negócio de {@link VoluntarioService} (débito técnico
 * da Fase 6, seção 37/38/39/106 do plano mestre, adiado até esta rodada
 * — 22/09/2026, junto com a Fase 10).
 *
 * <p>{@code StorageService} é substituído por um mock ({@code @MockitoBean}
 * — o Spring Boot 4 usa este em vez do {@code @MockBean} descontinuado)
 * porque {@code servire.storage.base-url/service-role-key} ficam vazios
 * no profile de teste (ver {@code application-test.yml}) — sem o mock,
 * qualquer teste que exercitasse {@link VoluntarioService#definirFoto}
 * falharia com {@code IllegalStateException} de configuração ausente, não
 * pela regra de negócio que o teste realmente quer verificar. Como o
 * conjunto de beans mockados é diferente de {@code TenantIsolationIntegrationTest}
 * (que não mocka nada), esta classe roda num {@code ApplicationContext}
 * próprio (cache de contexto do Spring é por configuração de teste) —
 * mais lento, mas ainda reaproveita o MESMO container Postgres estático
 * de {@link AbstractIntegrationTest}.</p>
 *
 * <p>Cada método de teste cria seu próprio tenant descartável (mesmo
 * padrão de {@code TenantIsolationIntegrationTest}, seção 110 da Fase 10)
 * — esta classe não usa rollback automático por teste, e {@code buscar}/
 * listagens acumulariam dados de outros testes se todos usassem o mesmo
 * tenant.</p>
 */
class VoluntarioServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VoluntarioService voluntarioService;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private ResponsavelRepository responsavelRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @MockitoBean
    private StorageService storageService;

    @BeforeEach
    void definirTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-VOL-TESTE", "tenant-vol-teste-" + UUID.randomUUID(), "Paróquia de teste (voluntario)",
                Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void criarSemNenhumResponsavelPrincipalLancaBadRequestException() {
        VoluntarioRequest request = requestCom(List.of(
                new ResponsavelRequest("Mãe", "Fulana", null, null, null, false)));

        assertThatThrownBy(() -> voluntarioService.criar(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exatamente um responsável principal");
    }

    @Test
    void criarComDoisResponsaveisPrincipaisLancaBadRequestException() {
        VoluntarioRequest request = requestCom(List.of(
                new ResponsavelRequest("Mãe", "Fulana", null, null, null, true),
                new ResponsavelRequest("Pai", "Sicrano", null, null, null, true)));

        assertThatThrownBy(() -> voluntarioService.criar(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exatamente um responsável principal");
    }

    @Test
    void criarComExatamenteUmPrincipalSalvaVoluntarioEResponsavel() {
        VoluntarioRequest request = requestCom(List.of(
                new ResponsavelRequest("Mãe", "Fulana", "11999990000", null, null, true)));

        Voluntario salvo = voluntarioService.criar(request);

        assertThat(salvo.getId()).isNotNull();
        assertThat(responsavelRepository.findByVoluntario_Id(salvo.getId()))
                .extracting(Responsavel::getNome, Responsavel::isPrincipal)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Fulana", true));
    }

    /**
     * "Apaga tudo e reinsere" (mesmo padrão do Angular atual — ver o
     * método privado {@code substituirResponsaveis} de
     * {@link VoluntarioService}, testado aqui só pelo efeito observável,
     * já que é privado): atualizar com uma lista de responsáveis
     * diferente da original remove os antigos (orphanRemoval) e insere só
     * os novos.
     */
    @Test
    void atualizarSubstituiTodosOsResponsaveisAntigosPelosNovos() {
        Voluntario criado = voluntarioService.criar(requestCom(List.of(
                new ResponsavelRequest("Mãe", "Responsável Original", null, null, null, true),
                new ResponsavelRequest("Pai", "Segundo Original", null, null, null, false))));
        assertThat(responsavelRepository.findByVoluntario_Id(criado.getId())).hasSize(2);

        voluntarioService.atualizar(criado.getId(), requestCom(List.of(
                new ResponsavelRequest("Avó", "Responsável Novo", null, null, null, true))));

        List<Responsavel> atuais = responsavelRepository.findByVoluntario_Id(criado.getId());
        assertThat(atuais).extracting(Responsavel::getNome).containsExactly("Responsável Novo");
    }

    @Test
    void definirFotoChamaStorageServiceEGravaCaminhoRetornado() {
        Voluntario criado = voluntarioService.criar(requestCom(List.of(
                new ResponsavelRequest("Mãe", "Fulana", null, null, null, true))));
        MockMultipartFile foto = new MockMultipartFile("foto", "perfil.png", "image/png", new byte[]{1, 2, 3});
        when(storageService.armazenar(anyString(), any(byte[].class), Mockito.eq("image/png")))
                .thenReturn("caminho-retornado-pelo-storage.png");

        Voluntario atualizado = voluntarioService.definirFoto(criado.getId(), foto);

        assertThat(atualizado.getFotoPath()).isEqualTo("caminho-retornado-pelo-storage.png");
    }

    @Test
    void definirFotoSemArquivoLancaBadRequestException() {
        Voluntario criado = voluntarioService.criar(requestCom(List.of(
                new ResponsavelRequest("Mãe", "Fulana", null, null, null, true))));

        assertThatThrownBy(() -> voluntarioService.definirFoto(criado.getId(), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void obterUrlFotoSemFotoCadastradaLancaResourceNotFoundException() {
        Voluntario criado = voluntarioService.criar(requestCom(List.of(
                new ResponsavelRequest("Mãe", "Fulana", null, null, null, true))));

        assertThatThrownBy(() -> voluntarioService.obterUrlFoto(criado.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setAtivoAtualizaFlag() {
        Voluntario criado = voluntarioService.criar(requestCom(List.of(
                new ResponsavelRequest("Mãe", "Fulana", null, null, null, true))));
        assertThat(criado.isAtivo()).isTrue();

        voluntarioService.setAtivo(criado.getId(), false);

        assertThat(voluntarioRepository.findById(criado.getId())).get()
                .extracting(Voluntario::isAtivo).isEqualTo(false);
    }

    private VoluntarioRequest requestCom(List<ResponsavelRequest> responsaveis) {
        return new VoluntarioRequest(
                "Nome de Teste " + UUID.randomUUID(), null, TipoVoluntario.COROINHA, true,
                null, null, null, null, null, null, null, null, null, null, null,
                false, List.of(), responsaveis);
    }
}
