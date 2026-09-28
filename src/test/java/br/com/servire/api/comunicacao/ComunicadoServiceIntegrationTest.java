package br.com.servire.api.comunicacao;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.CriarRequest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Criado;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.DestinatarioPrevia;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.DestinatariosRequest;
import br.com.servire.api.comunicacao.dto.LayoutRequest;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaService;
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
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComunicadoServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ComunicadoService service;
    @Autowired private LayoutService layoutService;
    @Autowired private PessoaService pessoaService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ComunicadoDestinatarioRepository destinatarioRepository;

    private UUID tenantId;
    private UUID outroTenantId;
    private Pessoa maria;
    private Pessoa ana;
    private Pessoa bruno;
    private String emailMaria;
    private String emailAna;

    @BeforeEach
    void montar() {
        String s = UUID.randomUUID().toString();
        tenantId = tenantRepository.saveAndFlush(new Tenant("CM-" + s.substring(0, 8), "cm-" + s, "Paróquia Comunicado", Tenant.Status.ATIVO)).getId();
        outroTenantId = tenantRepository.saveAndFlush(new Tenant("CO-" + s.substring(0, 8), "co-" + s, "Outra", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenantId);

        emailMaria = "maria-" + s + "@teste.com";
        maria = pessoaService.criar(new PessoaRequest(Set.of(PessoaPapel.RESPONSAVEL), "Maria Souza", null, null, null, null,
                List.of(new ContatoEmailRequest("Pessoal", emailMaria, true),
                        new ContatoEmailRequest("Trabalho", "maria-trab-" + s + "@teste.com", false)),
                List.of(new ContatoTelefoneRequest("celular", "45999998888", true)),
                List.of(), List.of(), null, null, null, null, null, null, null, null, null, null, null, null, null));
        emailAna = "ana-" + s + "@teste.com";
        ana = pessoaService.criar(filho("Ana Souza", emailAna));
        bruno = pessoaService.criar(filho("Bruno Souza", null));
    }

    private PessoaRequest filho(String nome, String email) {
        return new PessoaRequest(Set.of(PessoaPapel.VOLUNTARIO), nome, null, null, null, null,
                email == null ? List.of() : List.of(new ContatoEmailRequest("Pessoal", email, true)),
                List.of(), List.of(new RelacaoRequest(maria.getId(), "Mãe", "Filho(a)", true)), List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                new VoluntarioPerfilRequest(TipoVoluntario.COROINHA, true, null, null, null, null, true, List.of(), null, null));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    private Layout layout(TipoEnvio canal, String conteudo, boolean ativo) {
        return layoutService.criar(new LayoutRequest("Layout " + UUID.randomUUID(), TipoLayout.TODOS, canal,
                canal == TipoEnvio.EMAIL ? "Aviso para #PESSOA.PRIMEIRO_NOME#" : null, conteudo, ativo));
    }

    private List<String> destinos(EnviarPara para, QuaisContatos quais, Pessoa... ps) {
        List<DestinatarioPrevia> r = service.destinatarios(new DestinatariosRequest(TipoEnvio.EMAIL,
                java.util.Arrays.stream(ps).map(Pessoa::getId).toList(), para, quais));
        return r.stream().flatMap(d -> d.destinos().stream().map(x -> d.nome() + "->" + x.endereco())).toList();
    }

    @Test
    void destinatariosPorParaEContatos() {
        assertThat(destinos(EnviarPara.PESSOA, QuaisContatos.PRINCIPAL, ana, bruno))
                .containsExactly("Ana Souza->" + emailAna);
        assertThat(destinos(EnviarPara.RESPONSAVEIS, QuaisContatos.PRINCIPAL, ana))
                .containsExactly("Ana Souza->" + emailMaria);
        assertThat(destinos(EnviarPara.AMBOS, QuaisContatos.PRINCIPAL, ana)).hasSize(2);
        assertThat(destinos(EnviarPara.RESPONSAVEIS, QuaisContatos.TODOS, ana)).hasSize(2);
        // A própria mãe marcada, sem responsáveis: "Responsáveis" usa os contatos dela.
        assertThat(destinos(EnviarPara.RESPONSAVEIS, QuaisContatos.PRINCIPAL, maria)).containsExactly("Maria Souza->" + emailMaria);
        assertThat(service.destinatarios(new DestinatariosRequest(TipoEnvio.WHATSAPP, List.of(ana.getId()),
                EnviarPara.PESSOA, QuaisContatos.PRINCIPAL)).getFirst().autorizaWhatsapp()).isTrue();
    }

    @Test
    void maeDeDoisFilhosRecebeUmaMensagemPorFilhoComONomeCerto() {
        Layout l = layout(TipoEnvio.EMAIL, "<p>Olá #RESPONSAVEL.PRIMEIRO_NOME#, sobre #PESSOA.PRIMEIRO_NOME#</p>", true);
        Criado criado = service.criar(new CriarRequest(TipoEnvio.EMAIL, l.getId(), null, EnviarPara.RESPONSAVEIS,
                QuaisContatos.PRINCIPAL, List.of(ana.getId(), bruno.getId())), List.of());

        assertThat(criado.total()).isEqualTo(2);
        List<ComunicadoDestinatario> ds = destinatarioRepository.findByComunicadoIdOrderByNome(criado.id());
        assertThat(ds).extracting(ComunicadoDestinatario::getDestino).containsOnly(emailMaria);
        assertThat(ds).extracting(ComunicadoDestinatario::getConteudo)
                .containsExactlyInAnyOrder("<p>Olá Maria, sobre Ana</p>", "<p>Olá Maria, sobre Bruno</p>");
        assertThat(ds).extracting(ComunicadoDestinatario::getAssunto)
                .containsExactlyInAnyOrder("Aviso para Ana", "Aviso para Bruno");
        assertThat(ds).allMatch(d -> d.getStatus() == StatusEnvio.PENDENTE);
        assertThat(service.detalhe(criado.id()).comunicado().status()).isEqualTo(StatusComunicado.NA_FILA);
    }

    @Test
    void layoutInativoOuDeOutroCanalDa400() {
        Layout inativo = layout(TipoEnvio.EMAIL, "Olá", false);
        Layout zap = layout(TipoEnvio.WHATSAPP, "Olá", true);
        assertThatThrownBy(() -> service.criar(req(TipoEnvio.EMAIL, inativo), List.of())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.criar(req(TipoEnvio.EMAIL, zap), List.of())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void seisAnexosDa400() {
        Layout l = layout(TipoEnvio.EMAIL, "Olá", true);
        List<MultipartFile> arquivos = new ArrayList<>();
        for (int i = 0; i < 6; i++) arquivos.add(new MockMultipartFile("anexos", "a" + i + ".pdf", "application/pdf", "%PDF".getBytes()));
        assertThatThrownBy(() -> service.criar(req(TipoEnvio.EMAIL, l), arquivos))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("5 anexos");
    }

    @Test
    void whatsappSemConfiguracaoDa400() {
        Layout l = layout(TipoEnvio.WHATSAPP, "Olá", true);
        assertThatThrownBy(() -> service.criar(req(TipoEnvio.WHATSAPP, l), List.of()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Configure o WhatsApp");
    }

    @Test
    void semContatoNoCanalDa400() {
        Layout l = layout(TipoEnvio.EMAIL, "Olá", true);
        assertThatThrownBy(() -> service.criar(new CriarRequest(TipoEnvio.EMAIL, l.getId(), null, EnviarPara.PESSOA,
                QuaisContatos.PRINCIPAL, List.of(bruno.getId())), List.of()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Nenhum dos selecionados");
    }

    @Test
    void outraParoquiaNaoVeOComunicado() {
        Layout l = layout(TipoEnvio.EMAIL, "Olá", true);
        Criado criado = service.criar(req(TipoEnvio.EMAIL, l), List.of());
        TenantContext.set(outroTenantId);
        assertThat(service.listar(null, null)).isEmpty();
        assertThatThrownBy(() -> service.detalhe(criado.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    private CriarRequest req(TipoEnvio canal, Layout l) {
        return new CriarRequest(canal, l.getId(), null, EnviarPara.RESPONSAVEIS, QuaisContatos.PRINCIPAL, List.of(ana.getId()));
    }
}
