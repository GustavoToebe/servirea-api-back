package br.com.servire.api.pessoa;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.pessoa.dto.RelacaoRequest;
import br.com.servire.api.pessoa.dto.VoluntarioPerfilRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.web.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
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
    void criarVoluntarioSemResponsavelPrincipalLancaBadRequestException() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Fulana"));
        PessoaRequest request = requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", false)));

        assertThatThrownBy(() -> pessoaService.criar(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exatamente um responsável principal");
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
                .hasMessageContaining("exatamente um responsável principal");
    }

    @Test
    void criarVoluntarioComUmPrincipalPersistePessoaPerfilERelacao() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Fulana"));

        Pessoa salvo = pessoaService.criar(requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true))));

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getPapel()).isEqualTo(PessoaPapel.VOLUNTARIO);
        assertThat(salvo.getVoluntario()).isNotNull();
        assertThat(salvo.getVoluntario().getId()).isEqualTo(salvo.getId());
        assertThat(salvo.getResponsaveis()).hasSize(1);
        assertThat(salvo.getResponsaveis().getFirst().getResponsavel().getId()).isEqualTo(mae.getId());
        assertThat(salvo.getResponsaveis().getFirst().isPrincipal()).isTrue();
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
    void papelNaoPodeSerAlterado() {
        Pessoa mae = pessoaService.criar(requestResponsavel("Fulana"));

        assertThatThrownBy(() -> pessoaService.atualizar(mae.getId(), requestVoluntario(List.of(
                new RelacaoRequest(mae.getId(), "Mãe", "Filho", true)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("papel");
    }

    @Test
    void doisEmailsExigemExatamenteUmPrincipal() {
        PessoaRequest request = new PessoaRequest(
                PessoaPapel.RESPONSAVEL, "Sem Principal", null, null, null, null,
                List.of(
                        new ContatoEmailRequest("E-mail pessoal", "a@teste.com", false),
                        new ContatoEmailRequest("Secundário", "b@teste.com", false)),
                null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> pessoaService.criar(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("e-mail principal");
    }

    private PessoaRequest requestResponsavel(String nome) {
        return new PessoaRequest(
                PessoaPapel.RESPONSAVEL, nome, null, null, null, null,
                List.of(new ContatoEmailRequest("E-mail pessoal", nome.toLowerCase() + UUID.randomUUID() + "@teste.com", true)),
                List.of(new ContatoTelefoneRequest("celular", "11999990000", true)),
                List.of(), null, null, null, null, null, null, null, null, null);
    }

    private PessoaRequest requestVoluntario(List<RelacaoRequest> relacoes) {
        return new PessoaRequest(
                PessoaPapel.VOLUNTARIO, "Nome de Teste " + UUID.randomUUID(), null, null, null, null,
                List.of(new ContatoEmailRequest("E-mail pessoal", "vol-" + UUID.randomUUID() + "@teste.com", true)),
                List.of(new ContatoTelefoneRequest("celular", "11988887777", true)),
                relacoes, null, null, null, null, null, null, null, null,
                new VoluntarioPerfilRequest(TipoVoluntario.COROINHA, true, null, null, null, null, false, List.of()));
    }
}
