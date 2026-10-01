package br.com.servire.api.evento;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.comunicacao.ComunicadoDestinatario;
import br.com.servire.api.comunicacao.ComunicadoDestinatarioRepository;
import br.com.servire.api.comunicacao.Layout;
import br.com.servire.api.comunicacao.LayoutRepository;
import br.com.servire.api.comunicacao.TipoEnvio;
import br.com.servire.api.comunicacao.TipoLayout;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaEmail;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.PessoaTelefone;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API de eventos: cadastro, publicação, inscrições, mensagens, lembrete, fotos, permissão e isolamento. */
@AutoConfigureMockMvc
class EventoHttpIntegrationTest extends AbstractIntegrationTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String[] TUDO = {"PERM_EVENTO", "PERM_EVENTO_CRIAR", "PERM_EVENTO_ALTERAR",
            "PERM_EVENTO_INSCREVER", "PERM_EVENTO_CANCELAR"};

    @Autowired private MockMvc mvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PessoaRepository pessoas;
    @Autowired private ComunicadoDestinatarioRepository destinatarios;
    @Autowired private LembretesDeEvento lembretes;
    @Autowired private LayoutRepository layouts;
    @Autowired private br.com.servire.api.auth.UsuarioRepository usuarios;
    private UUID usuarioId;
    @MockitoBean private StorageService storage;

    private UUID paroquia;

    @BeforeEach
    void preparar() {
        paroquia = novaParoquia();
        TenantContext.set(paroquia);
        usuarioId = usuarios.saveAndFlush(new br.com.servire.api.auth.Usuario("evt-" + UUID.randomUUID() + "@teste.com", "Secretária")).getId();
        when(storage.armazenar(anyString(), any(), anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(storage.gerarUrlAssinada(anyString())).thenAnswer(inv -> "https://storage.test/" + inv.getArgument(0));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Autowired private EventoRepository eventos;
    @Autowired private EventoFotoRepository fotos;
    @Autowired private EventoInscricaoRepository inscricoes;

    @Test void paginaOrdenaAntesDoLimiteIsolaTenantEAssinaFotoForaDaTransacao() throws Exception {
        var agora=agoraBrasilia();
        var proximo=eventos.saveAndFlush(new Evento("Mais próximo",agora.plusDays(1)));
        fotos.saveAndFlush(new EventoFoto(proximo.getId(),"foto/capa.jpg",true));
        inscricoes.saveAndFlush(new EventoInscricao(proximo.getId(),pessoa("Inscrito",false,null),false));
        for(int n=2;n<=35;n++)eventos.saveAndFlush(new Evento("Próximo "+n,agora.plusDays(n)));
        var passado=new Evento("Passado",agora.minusDays(1));passado.setSituacao(Evento.Situacao.PUBLICADO);eventos.saveAndFlush(passado);
        var cancelado=new Evento("Cancelado",agora.plusDays(60));cancelado.setSituacao(Evento.Situacao.CANCELADO);eventos.saveAndFlush(cancelado);
        UUID outra=novaParoquia();TenantContext.set(outra);eventos.saveAndFlush(new Evento("Outra paróquia",agora));TenantContext.set(paroquia);
        when(storage.gerarUrlAssinada(anyString())).thenAnswer(inv -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return "https://storage.test/"+inv.getArgument(0);
        });
        mvc.perform(get("/eventos/pagina").with(authentication(usuario("PERM_EVENTO"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(37)).andExpect(jsonPath("$.itens.length()").value(30))
            .andExpect(jsonPath("$.itens[0].titulo").value("Mais próximo")).andExpect(jsonPath("$.itens[0].inscritos").value(1))
            .andExpect(jsonPath("$.itens[0].capaUrl").value("https://storage.test/foto/capa.jpg"));
        mvc.perform(get("/eventos/pagina").param("pagina","1").with(authentication(usuario("PERM_EVENTO"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens.length()").value(7))
            .andExpect(jsonPath("$.itens[5].titulo").value("Cancelado")).andExpect(jsonPath("$.itens[6].titulo").value("Passado"));
    }
    @Test void paginaExigePermissaoELimitaTamanho() throws Exception {
        mvc.perform(get("/eventos/pagina").with(authentication(usuario("PERM_PESSOA")))).andExpect(status().isForbidden());
        mvc.perform(get("/eventos/pagina").param("tamanho","101").with(authentication(usuario("PERM_EVENTO")))).andExpect(status().isBadRequest());
    }

    // ---- apoio

    private UUID novaParoquia() {
        String s = UUID.randomUUID().toString();
        return tenantRepository.saveAndFlush(new Tenant("TENANT-EVT-" + s, "tenant-evt-" + s,
                "Paróquia São José", Tenant.Status.ATIVO)).getId();
    }

    private Authentication usuario(String... permissoes) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(usuarioId, paroquia, UsuarioTenant.Role.ADMIN, false), null,
                Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    private Pessoa pessoa(String nome, boolean autoriza, String telefone) {
        Pessoa p = Pessoas.voluntario(nome);
        p.getVoluntario().setAutorizaWhatsapp(autoriza);
        if (telefone != null) {
            PessoaTelefone t = new PessoaTelefone("celular", telefone, true);
            t.setPessoa(p);
            p.getTelefones().add(t);
        }
        return pessoas.saveAndFlush(p);
    }

    private static LocalDateTime agoraBrasilia() {
        return LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).withNano(0);
    }

    private String corpo(String titulo, LocalDateTime inicio, Integer vagas, int lembreteDias) {
        return """
                {"titulo":"%s","descricao":"Encontro de jovens","inicio":"%s","localNome":"Salão paroquial",
                 "cep":"85810000","logradouro":"Rua General Osório","numero":"3191","bairro":"Centro",
                 "cidade":"Cascavel","uf":"pr","mapaUrl":"https://maps.app.goo.gl/abc","vagas":%s,
                 "responsavelNome":"Maria","responsavelTelefone":"45999650660","lembreteDias":%d}
                """.formatted(titulo, ISO.format(inicio), vagas == null ? "null" : vagas, lembreteDias);
    }

    private ResultActions criar(String json) throws Exception {
        return mvc.perform(post("/eventos").contentType(MediaType.APPLICATION_JSON).content(json).with(csrf()).with(authentication(usuario(TUDO))));
    }

    private String criarPublicado(String titulo, LocalDateTime inicio, Integer vagas, int lembreteDias) throws Exception {
        String id = JsonPath.read(criar(corpo(titulo, inicio, vagas, lembreteDias)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/eventos/" + id + "/publicar").with(csrf()).with(authentication(usuario(TUDO)))).andExpect(status().isOk());
        return id;
    }

    private ResultActions inscrever(String eventoId, UUID pessoaId) throws Exception {
        return mvc.perform(post("/eventos/" + eventoId + "/inscricoes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pessoaId\":\"" + pessoaId + "\"}").with(csrf()).with(authentication(usuario(TUDO))));
    }

    // ---- cadastro

    @Test
    void criaComCepUfETelefoneFormatadosEMensagensPadrao() throws Exception {
        criar(corpo("Retiro", agoraBrasilia().plusDays(10), 30, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("RASCUNHO"))
                .andExpect(jsonPath("$.cep").value("85810-000"))
                .andExpect(jsonPath("$.uf").value("PR"))
                .andExpect(jsonPath("$.responsavelTelefone").value("(45) 99965-0660"))
                .andExpect(jsonPath("$.mensagemConfirmacao").value(MensagensDeEvento.CONFIRMACAO_PADRAO))
                .andExpect(jsonPath("$.tags['#EVENTO.TITULO#']").exists());
    }

    @Test
    void validaTituloVagasTerminoCepELinkDoMapa() throws Exception {
        LocalDateTime inicio = agoraBrasilia().plusDays(5);
        criar(corpo("", inicio, null, 1)).andExpect(status().isBadRequest());
        criar(corpo("x".repeat(151), inicio, null, 1)).andExpect(status().isBadRequest());
        criar(corpo("Retiro", inicio, 0, 1)).andExpect(status().isBadRequest());
        criar(corpo("Retiro", inicio, null, 31)).andExpect(status().isBadRequest());
        criar(corpo("Retiro", inicio, null, 1).replace("\"85810000\"", "\"123\"")).andExpect(status().isBadRequest());
        criar(corpo("Retiro", inicio, null, 1).replace("https://maps.app.goo.gl/abc", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
        String comTermino = corpo("Retiro", inicio, null, 1).replace("\"localNome\"",
                "\"termino\":\"" + ISO.format(inicio.minusHours(1)) + "\",\"localNome\"");
        criar(comTermino).andExpect(status().isBadRequest());
    }

    @Test
    void naoPublicaEventoComDataPassada() throws Exception {
        String id = JsonPath.read(criar(corpo("Antigo", agoraBrasilia().minusDays(1), null, 1))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/eventos/" + id + "/publicar").with(csrf()).with(authentication(usuario(TUDO)))).andExpect(status().isBadRequest());
    }

    // ---- permissão e isolamento

    @Test
    void cadaAcaoExigeASuaPermissao() throws Exception {
        String id = criarPublicado("Missa jovem", agoraBrasilia().plusDays(3), null, 1);
        mvc.perform(get("/eventos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/eventos").with(csrf()).with(authentication(usuario("PERM_PESSOA")))).andExpect(status().isForbidden());
        mvc.perform(post("/eventos").contentType(MediaType.APPLICATION_JSON).content(corpo("X", agoraBrasilia().plusDays(2), null, 1))
                .with(csrf()).with(authentication(usuario("PERM_EVENTO")))).andExpect(status().isForbidden());
        mvc.perform(post("/eventos/" + id + "/inscricoes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"pessoaId\":\"" + UUID.randomUUID() + "\"}")
                .with(csrf()).with(authentication(usuario("PERM_EVENTO", "PERM_EVENTO_ALTERAR")))).andExpect(status().isForbidden());
        mvc.perform(post("/eventos/" + id + "/cancelar").with(csrf()).with(authentication(usuario("PERM_EVENTO", "PERM_EVENTO_ALTERAR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void outraParoquiaNaoVeNemMexeNoEvento() throws Exception {
        String id = criarPublicado("Só nossa", agoraBrasilia().plusDays(3), null, 1);
        UUID nossa = paroquia;

        paroquia = novaParoquia();
        TenantContext.set(paroquia);
        mvc.perform(get("/eventos").with(csrf()).with(authentication(usuario(TUDO)))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/eventos/" + id).with(csrf()).with(authentication(usuario(TUDO)))).andExpect(status().isNotFound());
        Pessoa daOutra = pessoa("Pessoa da outra", true, "45999990000");

        paroquia = nossa;
        TenantContext.set(nossa);
        inscrever(id, daOutra.getId()).andExpect(status().isNotFound());
    }

    // ---- inscrições e mensagens

    @Test
    void inscricaoComAutorizacaoPoeAConfirmacaoNaFilaComOsDadosDoEvento() throws Exception {
        String id = criarPublicado("Encontro de Casais", agoraBrasilia().plusDays(4).withHour(19).withMinute(30), null, 1);
        Pessoa ana = pessoa("Ana Souza", true, "(45) 99965-0660");

        String resposta = inscrever(id, ana.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.inscritos.length()").value(1))
                .andExpect(jsonPath("$.inscritos[0].nome").value("Ana Souza"))
                .andExpect(jsonPath("$.inscritos[0].confirmacao").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();

        List<ComunicadoDestinatario> fila = destinatarios.findAll().stream()
                .filter(d -> ana.getId().equals(d.getPessoaId())).toList();
        assertThat(fila).hasSize(1);
        String texto = fila.get(0).getConteudo();
        assertThat(texto).startsWith("Olá Ana!");
        assertThat(texto).contains("*Encontro de Casais*", "às 19:30", "Salão paroquial",
                "Rua General Osório, 3191 – Centro – Cascavel/PR", "https://maps.app.goo.gl/abc",
                "Maria (45) 99965-0660", "Paróquia São José");
        assertThat(fila.get(0).getDestino()).isEqualTo("(45) 99965-0660");
        assertThat(resposta).doesNotContain("tenantId");
    }

    @Test
    void semAutorizacaoInscreveMasNaoMandaMensagem() throws Exception {
        String id = criarPublicado("Encontro", agoraBrasilia().plusDays(4), null, 1);
        Pessoa jose = pessoa("José", false, "45999991111");

        inscrever(id, jose.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.inscritos[0].confirmacao").value("SEM_AUTORIZACAO"))
                .andExpect(jsonPath("$.inscritos[0].lembrete").value("SEM_AUTORIZACAO"));
        assertThat(destinatarios.findAll()).noneMatch(d -> jose.getId().equals(d.getPessoaId()));
    }

    @Test
    void naoInscreveEmRascunhoNemDuasVezesNemAlemDasVagas() throws Exception {
        String rascunho = JsonPath.read(criar(corpo("Rascunho", agoraBrasilia().plusDays(4), null, 1))
                .andReturn().getResponse().getContentAsString(), "$.id");
        Pessoa a = pessoa("A", true, "45999990001");
        Pessoa b = pessoa("B", true, "45999990002");
        inscrever(rascunho, a.getId()).andExpect(status().isConflict());

        String id = criarPublicado("Uma vaga", agoraBrasilia().plusDays(4), 1, 1);
        inscrever(id, a.getId()).andExpect(status().isOk());
        inscrever(id, a.getId()).andExpect(status().isConflict());
        inscrever(id, b.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Não há mais vagas neste evento."));
        inscrever(id, UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void removerInscricaoLiberaAVaga() throws Exception {
        String id = criarPublicado("Uma vaga", agoraBrasilia().plusDays(4), 1, 1);
        Pessoa a = pessoa("A", false, null);
        Pessoa b = pessoa("B", false, null);
        String inscricaoId = JsonPath.read(inscrever(id, a.getId()).andReturn().getResponse().getContentAsString(), "$.inscritos[0].id");
        mvc.perform(delete("/eventos/" + id + "/inscricoes/" + inscricaoId).with(csrf()).with(authentication(usuario(TUDO))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inscritos.length()").value(0));
        inscrever(id, b.getId()).andExpect(status().isOk());
    }

    @Test
    void cancelarComAvisoMandaSoParaQuemAutorizou() throws Exception {
        String id = criarPublicado("Vai cancelar", agoraBrasilia().plusDays(4), null, 1);
        Pessoa sim = pessoa("Sim", true, "45999992222");
        Pessoa nao = pessoa("Nao", false, "45999993333");
        inscrever(id, sim.getId());
        inscrever(id, nao.getId());

        mvc.perform(post("/eventos/" + id + "/cancelar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avisarInscritos\":true}").with(csrf()).with(authentication(usuario(TUDO))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("CANCELADO"));

        assertThat(destinatarios.findAll().stream().filter(d -> sim.getId().equals(d.getPessoaId())))
                .anyMatch(d -> d.getConteudo().contains("foi cancelado"));
        assertThat(destinatarios.findAll()).noneMatch(d -> nao.getId().equals(d.getPessoaId()));
        mvc.perform(put("/eventos/" + id).contentType(MediaType.APPLICATION_JSON).content(corpo("X", agoraBrasilia().plusDays(4), null, 1))
                .with(csrf()).with(authentication(usuario(TUDO)))).andExpect(status().isConflict());
    }

    // ---- lembrete

    @Test
    void lembreteSaiUmaVezSoParaEventoDentroDoPrazoEPublicado() throws Exception {
        String amanha = criarPublicado("Amanhã", agoraBrasilia().plusDays(1).withHour(23).withMinute(0), null, 1);
        String longe = criarPublicado("Longe", agoraBrasilia().plusDays(10), null, 1);
        String semLembrete = criarPublicado("Sem lembrete", agoraBrasilia().plusDays(1).withHour(23), null, 0);
        String cancelado = criarPublicado("Cancelado", agoraBrasilia().plusDays(1).withHour(23), null, 1);
        Pessoa ana = pessoa("Ana", true, "45999994444");
        for (String id : List.of(amanha, longe, semLembrete, cancelado)) inscrever(id, ana.getId());
        mvc.perform(post("/eventos/" + cancelado + "/cancelar").with(csrf()).with(authentication(usuario(TUDO)))).andExpect(status().isOk());

        lembretes.enviarAgora();
        lembretes.enviarAgora();

        TenantContext.set(paroquia);
        List<ComunicadoDestinatario> deAna = destinatarios.findAll().stream().filter(d -> ana.getId().equals(d.getPessoaId())).toList();
        List<ComunicadoDestinatario> lembretesDeAna = deAna.stream().filter(d -> d.getConteudo().contains("Lembrete")).toList();
        assertThat(lembretesDeAna).hasSize(1);
        assertThat(lembretesDeAna.get(0).getConteudo()).contains("*Amanhã*", "é amanhã");

        mvc.perform(get("/eventos/" + amanha).with(csrf()).with(authentication(usuario(TUDO))))
                .andExpect(jsonPath("$.inscritos[0].lembrete").value("PENDENTE"));
        mvc.perform(get("/eventos/" + longe).with(csrf()).with(authentication(usuario(TUDO))))
                .andExpect(jsonPath("$.inscritos[0].lembrete").doesNotExist());
    }

    // ---- canais (WhatsApp e e-mail) e layouts

    private Pessoa pessoaComEmail(String nome, boolean autoriza, String telefone, String email) {
        Pessoa p = Pessoas.voluntario(nome);
        p.getVoluntario().setAutorizaWhatsapp(autoriza);
        if (telefone != null) {
            PessoaTelefone t = new PessoaTelefone("celular", telefone, true);
            t.setPessoa(p);
            p.getTelefones().add(t);
        }
        PessoaEmail m = new PessoaEmail("pessoal", email, true);
        m.setPessoa(p);
        p.getEmails().add(m);
        return pessoas.saveAndFlush(p);
    }

    private Layout layoutDeEvento(String nome, TipoEnvio canal, String assunto, String conteudo) {
        return layouts.saveAndFlush(new Layout(nome, TipoLayout.EVENTO, canal, assunto, conteudo, true));
    }

    /** Evento com campos extras no JSON (canais, layouts, lista de dias do lembrete). */
    private String criarPublicadoCom(String titulo, LocalDateTime inicio, String extras) throws Exception {
        String base = corpo(titulo, inicio, null, 1);
        String json = base.replace("\"lembreteDias\":1}", extras + "}");
        String id = JsonPath.read(criar(json).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/eventos/" + id + "/publicar").with(csrf()).with(authentication(usuario(TUDO)))).andExpect(status().isOk());
        return id;
    }

    @Test
    void inscricaoUsaOsLayoutsEscolhidosEMandaPorWhatsappEPorEmail() throws Exception {
        Layout zap = layoutDeEvento("Zap evento", TipoEnvio.WHATSAPP, null,
                "Oi #PESSOA.PRIMEIRO_NOME#, vai ao #EVENTO.TITULO#?\nMapa: #EVENTO.MAPA#\nSem local: #EVENTO.LOCAL#");
        Layout mail = layoutDeEvento("Email evento", TipoEnvio.EMAIL, "Inscrito em #EVENTO.TITULO#",
                "<p>Olá #PESSOA.PRIMEIRO_NOME#, #EVENTO.DATA#</p>");
        String id = criarPublicadoCom("Retiro", agoraBrasilia().plusDays(4).withHour(19).withMinute(30),
                "\"lembreteDias\":[1],\"whatsappHabilitado\":true,\"emailHabilitado\":true,"
                        + "\"whatsappLayoutConfirmacaoId\":\"" + zap.getId() + "\","
                        + "\"emailLayoutConfirmacaoId\":\"" + mail.getId() + "\"");
        Pessoa ana = pessoaComEmail("Ana Souza", true, "(45) 99965-0660", "ana@teste.com");

        inscrever(id, ana.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.inscritos[0].confirmacaoWhatsapp").value("PENDENTE"))
                .andExpect(jsonPath("$.inscritos[0].confirmacaoEmail").value("PENDENTE"))
                .andExpect(jsonPath("$.inscritos[0].email").value("ana@teste.com"));

        List<ComunicadoDestinatario> fila = destinatarios.findAll().stream().filter(d -> ana.getId().equals(d.getPessoaId())).toList();
        assertThat(fila).hasSize(2);
        ComunicadoDestinatario whats = fila.stream().filter(d -> "(45) 99965-0660".equals(d.getDestino())).findFirst().orElseThrow();
        ComunicadoDestinatario email = fila.stream().filter(d -> "ana@teste.com".equals(d.getDestino())).findFirst().orElseThrow();
        assertThat(whats.getConteudo()).isEqualTo("Oi Ana, vai ao Retiro?\nMapa: https://maps.app.goo.gl/abc\nSem local: Salão paroquial");
        assertThat(email.getAssunto()).isEqualTo("Inscrito em Retiro");
        assertThat(email.getConteudo()).startsWith("<p>Olá Ana, ").contains(agoraBrasilia().plusDays(4).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @Test
    void linhaDoLayoutComTagVaziaSomeDoWhatsapp() throws Exception {
        Layout zap = layoutDeEvento("Zap sem mapa", TipoEnvio.WHATSAPP, null, "Oi #PESSOA.PRIMEIRO_NOME#\nMapa: #EVENTO.MAPA#\nFim");
        String json = corpo("Sem mapa", agoraBrasilia().plusDays(4), null, 1)
                .replace("\"mapaUrl\":\"https://maps.app.goo.gl/abc\",", "")
                .replace("\"lembreteDias\":1}", "\"whatsappLayoutConfirmacaoId\":\"" + zap.getId() + "\"}");
        String id = JsonPath.read(criar(json).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/eventos/" + id + "/publicar").with(csrf()).with(authentication(usuario(TUDO)))).andExpect(status().isOk());
        Pessoa ana = pessoa("Ana", true, "45999995555");

        inscrever(id, ana.getId()).andExpect(status().isOk());

        assertThat(destinatarios.findAll().stream().filter(d -> ana.getId().equals(d.getPessoaId())).findFirst().orElseThrow().getConteudo())
                .isEqualTo("Oi Ana\nFim");
    }

    @Test
    void semLayoutOEmailSaiComOTextoPadraoEmHtmlEEscapado() throws Exception {
        String id = criarPublicadoCom("Festa <b>junina</b>", agoraBrasilia().plusDays(4), "\"lembreteDias\":[1],\"emailHabilitado\":true");
        Pessoa ana = pessoaComEmail("Ana", false, null, "ana@teste.com");

        inscrever(id, ana.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.inscritos[0].confirmacaoEmail").value("PENDENTE"));

        ComunicadoDestinatario email = destinatarios.findAll().stream().filter(d -> ana.getId().equals(d.getPessoaId())).findFirst().orElseThrow();
        assertThat(email.getAssunto()).isEqualTo("Inscrição confirmada: Festa <b>junina</b>");
        assertThat(email.getConteudo()).startsWith("<p>Olá Ana!<br>").contains("Festa &lt;b&gt;junina&lt;/b&gt;").doesNotContain("<b>");
    }

    @Test
    void canalDesabilitadoNaoMandaENoPadraoOEmailVemDesligado() throws Exception {
        String semWhats = criarPublicadoCom("Só e-mail", agoraBrasilia().plusDays(4), "\"lembreteDias\":[1],\"whatsappHabilitado\":false,\"emailHabilitado\":true");
        String padrao = criarPublicado("Padrão", agoraBrasilia().plusDays(4), null, 1);
        Pessoa ana = pessoaComEmail("Ana", true, "45999996666", "ana@teste.com");

        inscrever(semWhats, ana.getId()).andExpect(status().isOk());
        assertThat(destinatarios.findAll().stream().filter(d -> ana.getId().equals(d.getPessoaId())))
                .extracting(ComunicadoDestinatario::getDestino).containsExactly("ana@teste.com");

        inscrever(padrao, ana.getId()).andExpect(status().isOk());
        assertThat(destinatarios.findAll().stream().filter(d -> ana.getId().equals(d.getPessoaId())))
                .extracting(ComunicadoDestinatario::getDestino).containsExactlyInAnyOrder("ana@teste.com", "45999996666");
    }

    @Test
    void recusaLayoutDeOutroTipoOuDeOutroCanal() throws Exception {
        Layout todos = layouts.saveAndFlush(new Layout("Geral", TipoLayout.TODOS, TipoEnvio.WHATSAPP, null, "Oi", true));
        Layout mail = layoutDeEvento("Mail", TipoEnvio.EMAIL, "Assunto", "<p>x</p>");
        String json = corpo("Evento", agoraBrasilia().plusDays(4), null, 1);

        criar(json.replace("\"lembreteDias\":1}", "\"whatsappLayoutConfirmacaoId\":\"" + todos.getId() + "\"}"))
                .andExpect(status().isBadRequest());
        criar(json.replace("\"lembreteDias\":1}", "\"whatsappLayoutLembreteId\":\"" + mail.getId() + "\"}"))
                .andExpect(status().isBadRequest());
        criar(json.replace("\"lembreteDias\":1}", "\"emailLayoutLembreteId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
        criar(json.replace("\"lembreteDias\":1}", "\"lembreteDias\":[31]}")).andExpect(status().isBadRequest());
    }

    @Test
    void excluirUmLayoutEmUsoSoLimpaAEscolhaDoEvento() throws Exception {
        Layout mail = layoutDeEvento("Mail em uso", TipoEnvio.EMAIL, "Assunto", "<p>x</p>");
        String id = criarPublicadoCom("Evento", agoraBrasilia().plusDays(4),
                "\"lembreteDias\":[1],\"emailHabilitado\":true,\"emailLayoutLembreteId\":\"" + mail.getId() + "\"");
        mvc.perform(get("/eventos/" + id).with(authentication(usuario(TUDO))))
                .andExpect(jsonPath("$.emailLayoutLembreteId").value(mail.getId().toString()));

        layouts.deleteById(mail.getId());
        layouts.flush();

        mvc.perform(get("/eventos/" + id).with(authentication(usuario(TUDO))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailLayoutLembreteId").doesNotExist());
    }

    @Test
    void lembreteEmVariosDiasSaiUmPorMarcoECanalSemRepetir() throws Exception {
        String id = criarPublicadoCom("Em dois dias", agoraBrasilia().plusDays(2).withHour(23).withMinute(0),
                "\"lembreteDias\":[1,3,7],\"emailHabilitado\":true");
        String longe = criarPublicadoCom("Longe", agoraBrasilia().plusDays(10), "\"lembreteDias\":[1,3,7],\"emailHabilitado\":true");
        Pessoa ana = pessoaComEmail("Ana", true, "45999997777", "ana@teste.com");
        inscrever(id, ana.getId());
        inscrever(longe, ana.getId());

        lembretes.enviarAgora();
        lembretes.enviarAgora();

        TenantContext.set(paroquia);
        List<ComunicadoDestinatario> lembretesDeAna = destinatarios.findAll().stream()
                .filter(d -> ana.getId().equals(d.getPessoaId()) && d.getConteudo().contains("Lembrete")).toList();
        assertThat(lembretesDeAna).extracting(ComunicadoDestinatario::getDestino).containsExactlyInAnyOrder("45999997777", "ana@teste.com");
        mvc.perform(get("/eventos/" + id).with(authentication(usuario(TUDO))))
                .andExpect(jsonPath("$.lembreteDias.length()").value(3))
                .andExpect(jsonPath("$.inscritos[0].lembreteWhatsapp").value("PENDENTE"))
                .andExpect(jsonPath("$.inscritos[0].lembreteEmail").value("PENDENTE"));
        mvc.perform(get("/eventos/" + longe).with(authentication(usuario(TUDO))))
                .andExpect(jsonPath("$.inscritos[0].lembreteEmail").doesNotExist());
    }

    // ---- fotos

    @Test
    void fotosPrimeiraViraCapaTipoInvalidoRecusadoEExcluirPassaACapa() throws Exception {
        String id = criarPublicado("Com fotos", agoraBrasilia().plusDays(4), null, 1);
        MockMultipartFile png = new MockMultipartFile("foto", "a.png", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile txt = new MockMultipartFile("foto", "a.txt", "text/plain", new byte[]{1});

        mvc.perform(multipart("/eventos/" + id + "/fotos").file(txt).with(csrf()).with(authentication(usuario(TUDO)))).andExpect(status().isBadRequest());
        String r = mvc.perform(multipart("/eventos/" + id + "/fotos").file(png).with(csrf()).with(authentication(usuario(TUDO))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotos[0].capa").value(true))
                .andExpect(jsonPath("$.fotos[0].url").value(org.hamcrest.Matchers.startsWith("https://storage.test/eventos/" + paroquia + "/")))
                .andReturn().getResponse().getContentAsString();
        String primeira = JsonPath.read(r, "$.fotos[0].id");
        mvc.perform(multipart("/eventos/" + id + "/fotos").file(png).with(csrf()).with(authentication(usuario(TUDO))))
                .andExpect(jsonPath("$.fotos.length()").value(2)).andExpect(jsonPath("$.fotos[1].capa").value(false));

        mvc.perform(delete("/eventos/" + id + "/fotos/" + primeira).with(csrf()).with(authentication(usuario(TUDO))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fotos.length()").value(1))
                .andExpect(jsonPath("$.fotos[0].capa").value(true));
        mvc.perform(get("/eventos").with(csrf()).with(authentication(usuario(TUDO))))
                .andExpect(jsonPath("$[0].capaUrl").value(org.hamcrest.Matchers.startsWith("https://storage.test/")));
    }

    @Test
    void duasInscricoesSimultaneasDisputamAUltimaVaga() throws Exception {
        String eventoId = criarPublicado("Última vaga", agoraBrasilia().plusDays(3), 1, 0);
        UUID ana = pessoa("Ana concorrente", false, null).getId();
        UUID jose = pessoa("José concorrente", false, null).getId();
        var prontas = new java.util.concurrent.CountDownLatch(2);
        var largada = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tarefas = java.util.stream.Stream.of(ana, jose).map(pessoaId -> executor.submit(() -> {
                TenantContext.set(paroquia);
                try {
                    prontas.countDown();
                    if (!largada.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Sem largada");
                    return inscrever(eventoId, pessoaId).andReturn().getResponse().getStatus();
                } finally {
                    TenantContext.clear();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            })).toList();
            boolean iniciadas = prontas.await(10, java.util.concurrent.TimeUnit.SECONDS);
            largada.countDown();
            assertThat(iniciadas).isTrue();
            assertThat(List.of(tarefas.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
                    tarefas.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        mvc.perform(get("/eventos/" + eventoId).with(authentication(usuario(TUDO))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inscritos.length()").value(1));
    }
}
