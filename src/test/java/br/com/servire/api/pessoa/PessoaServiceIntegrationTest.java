package br.com.servire.api.pessoa;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.pessoa.dto.NovaPessoaRequest;
import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.pessoa.dto.PessoaResponse;
import br.com.servire.api.pessoa.dto.RelacaoResponse;
import br.com.servire.api.pessoa.dto.RelacaoRequest;
import br.com.servire.api.pessoa.dto.VoluntarioPerfilRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PessoaServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PessoaService pessoaService;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void definirTenant() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-PESSOA-" + sufixo, "tenant-pessoa-" + sufixo,
                "Paróquia de teste (pessoa)", Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void criarVoluntarioAdultoSemRelacaoPersiste() {
        Pessoa salvo = pessoaService.criar(requestVoluntario(List.of()));

        assertThat(salvo.isVoluntario()).isTrue();
        assertThat(salvo.isResponsavel()).isFalse();
        assertThat(salvo.getVoluntario()).isNotNull();
        assertThat(salvo.getVoluntario().getId()).isEqualTo(salvo.getId());
        assertThat(salvo.getResponsaveis()).isEmpty();
    }

    @Test
    void criarVoluntarioComDoisPrincipaisLancaBadRequestException() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Fulana"));
        Pessoa pai = pessoaService.criar(requestResponsavel("Sicrano"));
        PessoaRequest request = requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true),
                new RelacaoRequest(pai.getId(), "Pai", "Filho", true)));

        assertThatThrownBy(() -> pessoaService.criar(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("responsável principal");
    }

    @Test
    void criarVoluntarioComUmPrincipalPersistePessoaPerfilERelacao() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Fulana"));

        Pessoa salvo = pessoaService.criar(requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true))));

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.isVoluntario()).isTrue();
        assertThat(salvo.getVoluntario()).isNotNull();
        assertThat(salvo.getVoluntario().getId()).isEqualTo(salvo.getId());
        assertThat(salvo.getResponsaveis()).hasSize(1);
        assertThat(salvo.getResponsaveis().getFirst().getResponsavel().getId()).isEqualTo(mae.getId());
        assertThat(salvo.getResponsaveis().getFirst().isPrincipal()).isTrue();
    }

    @Test
    void criarAdultoVoluntarioEResponsavelAoMesmoTempo() {
        Pessoa salvo = pessoaService.criar(requestAmbos("Ministro Pai"));

        assertThat(salvo.isVoluntario()).isTrue();
        assertThat(salvo.isResponsavel()).isTrue();
        assertThat(salvo.getVoluntario()).isNotNull();
        assertThat(salvo.getResponsaveis()).isEmpty();
    }

    @Test
    void atualizarSubstituiRelacoesDoVoluntario() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Original"));
        Pessoa avo = pessoaService.criar(requestResponsavel("Novo"));
        Pessoa voluntario = pessoaService.criar(requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true))));

        Pessoa atualizado = pessoaService.atualizar(voluntario.getId(), requestVoluntario(List.of(
                new RelacaoRequest(avo.getId(), "Avó", "Neto", true))));

        assertThat(atualizado.getResponsaveis()).hasSize(1);
        assertThat(atualizado.getResponsaveis().getFirst().getResponsavel().getNomeCompleto()).isEqualTo("Novo");
    }

    @Test
    void podeAcrescentarPapelResponsavelEmVoluntario() {
        Pessoa voluntario = pessoaService.criar(requestVoluntario(List.of()));

        Pessoa atualizado = pessoaService.atualizar(voluntario.getId(), requestAmbos(voluntario.getNomeCompleto()));

        assertThat(atualizado.isVoluntario()).isTrue();
        assertThat(atualizado.isResponsavel()).isTrue();
    }

    @Test
    void naoPodeRemoverPapelExistente() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Fulana"));

        assertThatThrownBy(() -> pessoaService.atualizar(mae.getId(), requestVoluntario(List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("remover o papel RESPONSAVEL");
    }

    @Test
    void doisEmailsExigemExatamenteUmPrincipal() {
        PessoaRequest request = new PessoaRequest(
                Set.of(PessoaPapel.RESPONSAVEL), "Sem Principal", null, null, null, null,
                List.of(
                        new ContatoEmailRequest("E-mail pessoal", "a@teste.com", false),
                        new ContatoEmailRequest("Secundário", "b@teste.com", false)),
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> pessoaService.criar(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("e-mail principal");
    }

    @Test
    void salvarFichaDoResponsavelSemMudarNadaMantemOParentesco() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Mãe Ida e Volta"));
        Pessoa filho = pessoaService.criar(requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true))));

        PessoaResponse antes = PessoaResponse.de(pessoaService.buscarPorId(mae.getId()));
        assertThat(antes.dependentes()).singleElement()
                .satisfies(r -> {
                    assertThat(r.parentesco()).isEqualTo("Filho");
                    assertThat(r.parentescoInverso()).isEqualTo("Mãe");
                });

        // o front reenvia exatamente o que recebeu, duas vezes
        pessoaService.atualizar(mae.getId(), requestResponsavel("Mãe Ida e Volta", ida(antes.dependentes())));
        PessoaResponse meio = PessoaResponse.de(pessoaService.buscarPorId(mae.getId()));
        pessoaService.atualizar(mae.getId(), requestResponsavel("Mãe Ida e Volta", ida(meio.dependentes())));

        PessoaResponse depois = PessoaResponse.de(pessoaService.buscarPorId(mae.getId()));
        assertThat(depois.dependentes()).singleElement()
                .satisfies(r -> {
                    assertThat(r.parentesco()).isEqualTo("Filho");
                    assertThat(r.parentescoInverso()).isEqualTo("Mãe");
                    assertThat(r.principal()).isTrue();
                });
        PessoaResponse vistaDoFilho = PessoaResponse.de(pessoaService.buscarPorId(filho.getId()));
        assertThat(vistaDoFilho.responsaveis()).singleElement()
                .satisfies(r -> {
                    assertThat(r.parentesco()).isEqualTo("Mãe");
                    assertThat(r.parentescoInverso()).isEqualTo("Filho");
                });
    }

    @Test
    void responsavelComDependenteViraVoluntarioSemPerderAsRelacoes() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Mãe Ministra"));
        Pessoa avo = pessoaService.criar(requestResponsavel("Avó Da Ministra"));
        Pessoa filho = pessoaService.criar(requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true))));
        PessoaResponse antes = PessoaResponse.de(pessoaService.buscarPorId(mae.getId()));

        pessoaService.atualizar(mae.getId(), requestAmbos("Mãe Ministra",
                List.of(new RelacaoRequest(avo.getId(), "Mãe", "Filha", true)),
                ida(antes.dependentes())));

        PessoaResponse depois = PessoaResponse.de(pessoaService.buscarPorId(mae.getId()));
        assertThat(depois.papeis()).containsExactlyInAnyOrder(PessoaPapel.VOLUNTARIO, PessoaPapel.RESPONSAVEL);
        assertThat(depois.responsaveis()).extracting(RelacaoResponse::pessoaId).containsExactly(avo.getId());
        assertThat(depois.dependentes()).extracting(RelacaoResponse::pessoaId).containsExactly(filho.getId());

        // e a ficha de quem tem os dois papéis continua salvando ida e volta
        pessoaService.atualizar(mae.getId(), requestAmbos("Mãe Ministra",
                ida(depois.responsaveis()), ida(depois.dependentes())));
        PessoaResponse denovo = PessoaResponse.de(pessoaService.buscarPorId(mae.getId()));
        assertThat(denovo.responsaveis()).hasSize(1);
        assertThat(denovo.dependentes()).hasSize(1);
    }

    @Test
    void responsavelNovoNaFichaECriadoNaMesmaTransacao() {
        Pessoa salvo = pessoaService.criar(requestVoluntario(List.of(new RelacaoRequest(null,
                new NovaPessoaRequest("Mãe Nova Inline", "inline-" + UUID.randomUUID() + "@teste.com", "11911112222"),
                "Mãe", "Filho", true))));

        PessoaResponse resposta = PessoaResponse.de(pessoaService.buscarPorId(salvo.getId()));
        assertThat(resposta.responsaveis()).singleElement()
                .satisfies(r -> {
                    assertThat(r.nomeCompleto()).isEqualTo("Mãe Nova Inline");
                    assertThat(r.papeisOutro()).containsExactly(PessoaPapel.RESPONSAVEL);
                });
    }

    @Test
    void responsavelNovoNaoFicaOrfaoQuandoOCadastroFalha() {
        String nome = "Órfã " + UUID.randomUUID();
        Pessoa naoResponsavel = pessoaService.criar(requestVoluntario(List.of()));
        PessoaRequest request = requestVoluntario(List.of(
                new RelacaoRequest(null, new NovaPessoaRequest(nome, null, null), "Mãe", "Filho", true),
                new RelacaoRequest(naoResponsavel.getId(), "Tio", "Sobrinho", false)));

        assertThatThrownBy(() -> pessoaService.criar(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("não tem o papel RESPONSAVEL");
        assertThat(pessoaService.buscar(null, nome)).isEmpty();
    }

    @Test
    void dependentePrincipalDeOutroResponsavelLancaConflictException() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Mãe Principal"));
        Pessoa pai = pessoaService.criar(requestResponsavel("Pai Secundário"));
        Pessoa filho = pessoaService.criar(requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true))));

        assertThatThrownBy(() -> pessoaService.atualizar(pai.getId(), requestResponsavel("Pai Secundário",
                List.of(new RelacaoRequest(filho.getId(), "Filho", "Pai", true)))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Mãe Principal");
    }

    @Test
    void mesmaPessoaRepetidaNaListaLancaBadRequestException() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Mãe Repetida"));

        assertThatThrownBy(() -> pessoaService.criar(requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true),
                new RelacaoRequest(mae.getId(), "Madrinha", "Afilhado", false)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("mais de uma vez");
    }

    @Test
    void dependenteSemPapelResponsavelLancaBadRequestException() {
        Pessoa filho = pessoaService.criar(requestVoluntario(List.of()));
        PessoaRequest request = new PessoaRequest(
                Set.of(PessoaPapel.VOLUNTARIO), "Só Voluntário", null, null, null, null,
                List.of(), List.of(), List.of(), List.of(new RelacaoRequest(filho.getId(), "Irmão", "Irmão", false)),
                null, null, null, null, null, null, null, null,
                new VoluntarioPerfilRequest(TipoVoluntario.COROINHA, true, null, null, null, null, false, List.of()));

        assertThatThrownBy(() -> pessoaService.criar(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dependentes");
    }

    private static List<RelacaoRequest> ida(List<RelacaoResponse> relacoes) {
        return relacoes.stream()
                .map(r -> new RelacaoRequest(r.pessoaId(), r.parentesco(), r.parentescoInverso(), r.principal()))
                .toList();
    }

    private PessoaRequest requestResponsavel(String nome) {
        return new PessoaRequest(
                Set.of(PessoaPapel.RESPONSAVEL), nome, null, null, null, null,
                List.of(new ContatoEmailRequest("E-mail pessoal", nome.toLowerCase() + UUID.randomUUID() + "@teste.com", true)),
                List.of(new ContatoTelefoneRequest("celular", "11999990000", true)),
                List.of(), List.of(), null, null, null, null, null, null, null, null, null);
    }

    private PessoaRequest requestVoluntario(List<RelacaoRequest> relacoes) {
        return new PessoaRequest(
                Set.of(PessoaPapel.VOLUNTARIO), "Nome de Teste " + UUID.randomUUID(), null, null, null, null,
                List.of(new ContatoEmailRequest("E-mail pessoal", "vol-" + UUID.randomUUID() + "@teste.com", true)),
                List.of(new ContatoTelefoneRequest("celular", "11988887777", true)),
                relacoes, List.of(), null, null, null, null, null, null, null, null,
                new VoluntarioPerfilRequest(TipoVoluntario.COROINHA, true, null, null, null, null, false, List.of()));
    }

    private PessoaRequest requestAmbos(String nome) {
        return requestAmbos(nome, List.of(), List.of());
    }

    private PessoaRequest requestResponsavel(String nome, List<RelacaoRequest> dependentes) {
        return new PessoaRequest(
                Set.of(PessoaPapel.RESPONSAVEL), nome, null, null, null, null,
                List.of(), List.of(), List.of(), dependentes,
                null, null, null, null, null, null, null, null, null);
    }

    private PessoaRequest requestAmbos(String nome, List<RelacaoRequest> responsaveis, List<RelacaoRequest> dependentes) {
        return new PessoaRequest(
                Set.of(PessoaPapel.VOLUNTARIO, PessoaPapel.RESPONSAVEL), nome, null, null, null, null,
                List.of(new ContatoEmailRequest("E-mail pessoal", "ambos-" + UUID.randomUUID() + "@teste.com", true)),
                List.of(new ContatoTelefoneRequest("celular", "11977776666", true)),
                responsaveis, dependentes, null, null, null, null, null, null, null, null,
                new VoluntarioPerfilRequest(TipoVoluntario.ACOLITO, true, null, null, null, null, false, List.of()));
    }
}
