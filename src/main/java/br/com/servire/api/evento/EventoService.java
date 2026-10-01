package br.com.servire.api.evento;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.comunicacao.ComunicadoService;
import br.com.servire.api.comunicacao.ContextoDeEnvio;
import br.com.servire.api.comunicacao.EnvioAvulso;
import br.com.servire.api.comunicacao.Layout;
import br.com.servire.api.comunicacao.LayoutRepository;
import br.com.servire.api.comunicacao.Renderizador;
import br.com.servire.api.comunicacao.StatusEnvio;
import br.com.servire.api.comunicacao.TipoEnvio;
import br.com.servire.api.comunicacao.TipoLayout;
import br.com.servire.api.evento.EventoDtos.CancelarRequest;
import br.com.servire.api.evento.EventoDtos.EventoDetalhe;
import br.com.servire.api.evento.EventoDtos.EventoRequest;
import br.com.servire.api.evento.EventoDtos.EventoResumo;
import br.com.servire.api.evento.EventoDtos.FotoResponse;
import br.com.servire.api.evento.EventoDtos.InscritoResponse;
import br.com.servire.api.evento.EventoDtos.SituacaoMensagem;
import br.com.servire.api.evento.EventoDtos.SituacaoTela;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaEmail;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.PessoaTelefone;
import br.com.servire.api.storage.ExtensaoDeFoto;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.Formatos;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Eventos da paróquia: cadastro, publicação, inscrição de pessoas já cadastradas e as mensagens de WhatsApp.
 * Mensagem só sai para quem autorizou WhatsApp no cadastro de voluntário (é o único lugar onde a autorização
 * existe hoje); os demais são inscritos normalmente e ficam como SEM_AUTORIZACAO.
 */
@Service
public class EventoService {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_FOTOS = 10;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final EventoRepository eventos;
    private final EventoFotoRepository fotos;
    private final EventoInscricaoRepository inscricoes;
    private final PessoaRepository pessoas;
    private final TenantRepository tenants;
    private final StorageService storage;
    private final EnvioAvulso envio;
    private final LayoutRepository layouts;
    private final ComunicadoService comunicados;
    private final AuditLogService audit;
    private final Clock clock;

    @Autowired
    public EventoService(EventoRepository eventos, EventoFotoRepository fotos, EventoInscricaoRepository inscricoes,
                         PessoaRepository pessoas, TenantRepository tenants, StorageService storage, EnvioAvulso envio,
                         LayoutRepository layouts, ComunicadoService comunicados, AuditLogService audit) {
        this(eventos, fotos, inscricoes, pessoas, tenants, storage, envio, layouts, comunicados, audit,
                Clock.system(BRASILIA));
    }

    EventoService(EventoRepository eventos, EventoFotoRepository fotos, EventoInscricaoRepository inscricoes,
                  PessoaRepository pessoas, TenantRepository tenants, StorageService storage, EnvioAvulso envio,
                  LayoutRepository layouts, ComunicadoService comunicados, AuditLogService audit, Clock clock) {
        this.eventos = eventos;
        this.fotos = fotos;
        this.inscricoes = inscricoes;
        this.pessoas = pessoas;
        this.tenants = tenants;
        this.storage = storage;
        this.envio = envio;
        this.layouts = layouts;
        this.comunicados = comunicados;
        this.audit = audit;
        this.clock = clock;
    }

    LocalDateTime agora() {
        return LocalDateTime.now(clock.withZone(BRASILIA));
    }

    // ---- Leitura

    @Transactional(readOnly = true)
    public List<EventoResumo> listar() {
        LocalDateTime agora = agora();
        return eventos.findAllByOrderByInicioDesc().stream().map(e -> new EventoResumo(
                e.getId(), e.getTitulo(), e.getInicio(), e.getTermino(), e.getLocalNome(), situacao(e, agora),
                inscricoes.countByEventoId(e.getId()), e.getVagas(), capaUrl(e.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public EventoDetalhe detalhe(UUID id) {
        return detalhe(buscar(id));
    }

    private EventoDetalhe detalhe(Evento e) {
        List<FotoResponse> listaFotos = fotos.findByEventoIdOrderByCapaDescCreatedAtAsc(e.getId()).stream()
                .map(f -> new FotoResponse(f.getId(), storage.gerarUrlAssinada(f.getCaminho()), f.isCapa())).toList();
        return new EventoDetalhe(e.getId(), e.getTitulo(), e.getDescricao(), e.getInicio(), e.getTermino(), e.getLocalNome(),
                e.getCep(), e.getLogradouro(), e.getNumero(), e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf(),
                e.getMapaUrl(), e.getVagas(), e.getResponsavelNome(), e.getResponsavelTelefone(), e.getLembreteDiasList(),
                e.isWhatsappHabilitado(), e.getWhatsappLayoutConfirmacaoId(), e.getWhatsappLayoutLembreteId(),
                e.isEmailHabilitado(), e.getEmailLayoutConfirmacaoId(), e.getEmailLayoutLembreteId(),
                e.getMensagemConfirmacao(), e.getMensagemLembrete(), situacao(e, agora()), listaFotos,
                inscritos(e.getId()), MensagensDeEvento.TAGS);
    }

    private List<InscritoResponse> inscritos(UUID eventoId) {
        List<EventoInscricao> lista = inscricoes.findByEventoIdOrderByCreatedAtAsc(eventoId);
        Set<UUID> ids = new HashSet<>();
        for (EventoInscricao i : lista) {
            if (i.getConfirmacaoDestinatarioId() != null) ids.add(i.getConfirmacaoDestinatarioId());
            if (i.getLembreteDestinatarioId() != null) ids.add(i.getLembreteDestinatarioId());
            if (i.getConfirmacaoEmailDestinatarioId() != null) ids.add(i.getConfirmacaoEmailDestinatarioId());
            if (i.getLembreteEmailDestinatarioId() != null) ids.add(i.getLembreteEmailDestinatarioId());
        }
        Map<UUID, StatusEnvio> status = envio.situacoes(ids);
        return lista.stream().map(i -> {
            String telefone = telefonePrincipal(i.getPessoa());
            String email = emailPrincipal(i.getPessoa());
            SituacaoMensagem confWhats = mensagemWhatsapp(i, telefone, i.getConfirmacaoDestinatarioId(), status);
            SituacaoMensagem lembWhats = i.getLembreteDestinatarioId() == null && i.isAutorizaWhatsapp() && telefone != null
                    ? null : mensagemWhatsapp(i, telefone, i.getLembreteDestinatarioId(), status);
            SituacaoMensagem confEmail = mensagemEmail(email, i.getConfirmacaoEmailDestinatarioId(), status);
            SituacaoMensagem lembEmail = i.getLembreteEmailDestinatarioId() == null && email != null
                    ? null : mensagemEmail(email, i.getLembreteEmailDestinatarioId(), status);
            return new InscritoResponse(i.getId(), i.getPessoa().getId(), i.getPessoa().getNomeCompleto(), telefone, email,
                    confWhats, lembWhats, confEmail, lembEmail, confWhats, lembWhats);
        }).sorted(Comparator.comparing(InscritoResponse::nome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static SituacaoMensagem mensagemWhatsapp(EventoInscricao i, String telefone, UUID destinatarioId, Map<UUID, StatusEnvio> status) {
        if (!i.isAutorizaWhatsapp()) return SituacaoMensagem.SEM_AUTORIZACAO;
        if (telefone == null) return SituacaoMensagem.SEM_TELEFONE;
        if (destinatarioId == null) return null;
        StatusEnvio s = status.get(destinatarioId);
        if (s == null) return null;
        return switch (s) {
            case PENDENTE -> SituacaoMensagem.PENDENTE;
            case ENVIADO -> SituacaoMensagem.ENVIADA;
            case FALHA -> SituacaoMensagem.FALHOU;
        };
    }

    private static SituacaoMensagem mensagemEmail(String email, UUID destinatarioId, Map<UUID, StatusEnvio> status) {
        if (email == null) return SituacaoMensagem.SEM_EMAIL;
        if (destinatarioId == null) return null;
        StatusEnvio s = status.get(destinatarioId);
        if (s == null) return null;
        return switch (s) {
            case PENDENTE -> SituacaoMensagem.PENDENTE;
            case ENVIADO -> SituacaoMensagem.ENVIADA;
            case FALHA -> SituacaoMensagem.FALHOU;
        };
    }

    static SituacaoTela situacao(Evento e, LocalDateTime agora) {
        return switch (e.getSituacao()) {
            case RASCUNHO -> SituacaoTela.RASCUNHO;
            case CANCELADO -> SituacaoTela.CANCELADO;
            case PUBLICADO -> e.jaAconteceu(agora) ? SituacaoTela.ENCERRADO : SituacaoTela.PUBLICADO;
        };
    }

    private String capaUrl(UUID eventoId) {
        return fotos.findByEventoIdOrderByCapaDescCreatedAtAsc(eventoId).stream().findFirst()
                .map(f -> storage.gerarUrlAssinada(f.getCaminho())).orElse(null);
    }

    // ---- Cadastro

    @Transactional
    public EventoDetalhe criar(EventoRequest req) {
        Evento e = new Evento(req.titulo().trim(), req.inicio());
        aplicar(e, req);
        eventos.save(e);
        audit.registrar("CRIACAO", "EVENTO", e.getId(), List.of());
        return detalhe(e);
    }

    @Transactional
    public EventoDetalhe atualizar(UUID id, EventoRequest req) {
        Evento e = buscar(id);
        if (e.getSituacao() == Evento.Situacao.CANCELADO) {
            throw new ConflictException("Evento cancelado não pode ser alterado.");
        }
        aplicar(e, req);
        audit.registrar("ALTERACAO", "EVENTO", id, List.of());
        return detalhe(e);
    }

    private void aplicar(Evento e, EventoRequest req) {
        if (req.termino() != null && req.termino().isBefore(req.inicio())) {
            throw new BadRequestException("O término precisa ser depois do início.");
        }
        e.setTitulo(req.titulo().trim());
        e.setDescricao(texto(req.descricao()));
        e.setInicio(req.inicio());
        e.setTermino(req.termino());
        e.setLocalNome(texto(req.localNome()));
        e.setCep(Formatos.cep(req.cep()));
        e.setLogradouro(texto(req.logradouro()));
        e.setNumero(texto(req.numero()));
        e.setComplemento(texto(req.complemento()));
        e.setBairro(texto(req.bairro()));
        e.setCidade(texto(req.cidade()));
        e.setUf(Formatos.uf(req.uf()));
        e.setMapaUrl(link(req.mapaUrl()));
        e.setVagas(req.vagas());
        e.setResponsavelNome(texto(req.responsavelNome()));
        e.setResponsavelTelefone(Formatos.telefone(req.responsavelTelefone()));
        e.setLembreteDiasList(diasDeLembrete(req.lembreteDias()));
        e.setWhatsappHabilitado(req.whatsappHabilitado() == null || req.whatsappHabilitado());
        e.setEmailHabilitado(Boolean.TRUE.equals(req.emailHabilitado()));
        e.setWhatsappLayoutConfirmacaoId(layoutDoEvento(req.whatsappLayoutConfirmacaoId(), TipoEnvio.WHATSAPP));
        e.setWhatsappLayoutLembreteId(layoutDoEvento(req.whatsappLayoutLembreteId(), TipoEnvio.WHATSAPP));
        e.setEmailLayoutConfirmacaoId(layoutDoEvento(req.emailLayoutConfirmacaoId(), TipoEnvio.EMAIL));
        e.setEmailLayoutLembreteId(layoutDoEvento(req.emailLayoutLembreteId(), TipoEnvio.EMAIL));
        e.setMensagemConfirmacao(textoOu(req.mensagemConfirmacao(), MensagensDeEvento.CONFIRMACAO_PADRAO));
        e.setMensagemLembrete(textoOu(req.mensagemLembrete(), MensagensDeEvento.LEMBRETE_PADRAO));
        if (e.getVagas() != null && e.getId() != null && inscricoes.countByEventoId(e.getId()) > e.getVagas()) {
            throw new ConflictException("Já há mais inscritos do que essas vagas.");
        }
    }

    @Transactional
    public EventoDetalhe publicar(UUID id) {
        Evento e = buscar(id);
        if (e.getSituacao() == Evento.Situacao.CANCELADO) throw new ConflictException("Evento cancelado não pode ser publicado.");
        if (e.jaAconteceu(agora())) throw new BadRequestException("A data do evento já passou. Ajuste a data antes de publicar.");
        e.setSituacao(Evento.Situacao.PUBLICADO);
        audit.registrar("PUBLICACAO", "EVENTO", id, List.of("situacao"));
        return detalhe(e);
    }

    @Transactional
    public EventoDetalhe cancelar(UUID id, CancelarRequest req) {
        Evento e = buscar(id);
        if (e.getSituacao() == Evento.Situacao.CANCELADO) return detalhe(e);
        boolean avisar = req != null && req.avisarInscritos() && e.getSituacao() == Evento.Situacao.PUBLICADO && !e.jaAconteceu(agora());
        e.setSituacao(Evento.Situacao.CANCELADO);
        if (avisar) {
            Tenant tenant = tenantAtual();
            LocalDate hoje = agora().toLocalDate();
            List<EnvioAvulso.Mensagem> whats = new ArrayList<>();
            List<EnvioAvulso.MensagemEmail> emails = new ArrayList<>();
            for (EventoInscricao i : inscricoes.findByEventoIdOrderByCreatedAtAsc(id)) {
                Pessoa pessoa = i.getPessoa();
                String telefone = telefonePrincipal(pessoa);
                if (e.isWhatsappHabilitado() && i.isAutorizaWhatsapp() && telefone != null) {
                    whats.add(new EnvioAvulso.Mensagem(pessoa.getId(), pessoa.getNomeCompleto(), telefone,
                            montar(e, null, TipoEnvio.WHATSAPP, Momento.CANCELAMENTO, pessoa, tenant, hoje).corpo()));
                }
                String email = emailPrincipal(pessoa);
                if (e.isEmailHabilitado() && email != null) {
                    Pronta p = montar(e, null, TipoEnvio.EMAIL, Momento.CANCELAMENTO, pessoa, tenant, hoje);
                    emails.add(new EnvioAvulso.MensagemEmail(pessoa.getId(), pessoa.getNomeCompleto(), email, p.assunto(), p.corpo()));
                }
            }
            envio.enfileirarWhatsapp("Evento cancelado: " + e.getTitulo(), whats);
            envio.enfileirarEmail("Evento cancelado: " + e.getTitulo(), "Evento cancelado: " + e.getTitulo(), emails);
        }
        audit.registrar("CANCELAMENTO", "EVENTO", id, List.of("situacao"));
        return detalhe(e);
    }

    // ---- Inscrições

    @Transactional
    public EventoDetalhe inscrever(UUID id, UUID pessoaId) {
        Evento e = buscar(id);
        if (e.getSituacao() != Evento.Situacao.PUBLICADO) {
            throw new ConflictException("Publique o evento antes de inscrever pessoas.");
        }
        if (e.jaAconteceu(agora())) throw new ConflictException("Este evento já aconteceu.");
        Pessoa pessoa = pessoas.findById(pessoaId).orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));
        if (inscricoes.existsByEventoIdAndPessoa_Id(id, pessoaId)) {
            throw new ConflictException(pessoa.getNomeCompleto() + " já está inscrita neste evento.");
        }
        if (e.getVagas() != null && inscricoes.countByEventoId(id) >= e.getVagas()) {
            throw new ConflictException("Não há mais vagas neste evento.");
        }
        boolean autoriza = pessoa.getVoluntario() != null && pessoa.getVoluntario().isAutorizaWhatsapp();
        EventoInscricao inscricao = inscricoes.save(new EventoInscricao(id, pessoa, autoriza));
        Tenant tenant = tenantAtual();
        LocalDate hoje = agora().toLocalDate();
        String telefone = telefonePrincipal(pessoa);
        if (e.isWhatsappHabilitado() && autoriza && telefone != null) {
            Pronta p = montar(e, e.getWhatsappLayoutConfirmacaoId(), TipoEnvio.WHATSAPP, Momento.CONFIRMACAO, pessoa, tenant, hoje);
            List<UUID> ids = envio.enfileirarWhatsapp("Evento: " + e.getTitulo() + " (inscrição)",
                    List.of(new EnvioAvulso.Mensagem(pessoa.getId(), pessoa.getNomeCompleto(), telefone, p.corpo())));
            inscricao.setConfirmacaoDestinatarioId(ids.get(0));
        }
        String email = emailPrincipal(pessoa);
        if (e.isEmailHabilitado() && email != null) {
            Pronta p = montar(e, e.getEmailLayoutConfirmacaoId(), TipoEnvio.EMAIL, Momento.CONFIRMACAO, pessoa, tenant, hoje);
            List<UUID> ids = envio.enfileirarEmail("Evento: " + e.getTitulo() + " (inscrição)", p.assunto(),
                    List.of(new EnvioAvulso.MensagemEmail(pessoa.getId(), pessoa.getNomeCompleto(), email, p.assunto(), p.corpo())));
            inscricao.setConfirmacaoEmailDestinatarioId(ids.get(0));
        }
        audit.registrar("INSCRICAO", "EVENTO", id, List.of("inscricao"));
        return detalhe(e);
    }

    @Transactional
    public EventoDetalhe removerInscricao(UUID id, UUID inscricaoId) {
        Evento e = buscar(id);
        EventoInscricao i = inscricoes.findById(inscricaoId)
                .filter(x -> x.getEventoId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
        inscricoes.delete(i);
        audit.registrar("REMOCAO_INSCRICAO", "EVENTO", id, List.of("inscricao"));
        return detalhe(e);
    }

    /**
     * Lembretes do dia, para a paróquia do contexto. O evento tem uma lista de "N dias antes"; vale o marco mais
     * próximo já alcançado (o menor N que ainda cobre os dias que faltam). Cada inscrição recebe um lembrete por
     * marco e por canal: quem já recebeu o deste marco não recebe de novo, e se o job não rodou num dia o próximo
     * recupera. Devolve quantas mensagens entraram na fila.
     */
    @Transactional
    public int enviarLembretes() {
        LocalDateTime agora = agora();
        LocalDate hoje = agora.toLocalDate();
        Instant quando = Instant.now(clock);
        Tenant tenant = null;
        int total = 0;
        for (Evento e : eventos.publicadosEntre(agora, hoje.plusDays(31).atStartOfDay())) {
            List<Integer> dias = e.getLembreteDiasList();
            if (dias.isEmpty() || (!e.isWhatsappHabilitado() && !e.isEmailHabilitado())) continue;
            LocalDate diaDoEvento = e.getInicio().toLocalDate();
            long diasAte = ChronoUnit.DAYS.between(hoje, diaDoEvento);
            Integer marco = dias.stream().filter(d -> diasAte <= d).min(Integer::compare).orElse(null);
            if (marco == null) continue;
            Instant desde = diaDoEvento.minusDays(marco).atStartOfDay(BRASILIA).toInstant();
            if (e.isWhatsappHabilitado()) {
                List<EventoInscricao> pendentes = inscricoes.pendentesDeLembreteWhatsapp(e.getId(), desde);
                List<EventoInscricao> comTelefone = new ArrayList<>();
                List<EnvioAvulso.Mensagem> mensagens = new ArrayList<>();
                if (tenant == null && !pendentes.isEmpty()) tenant = tenantAtual();
                for (EventoInscricao i : pendentes) {
                    String telefone = telefonePrincipal(i.getPessoa());
                    if (telefone == null) continue;
                    comTelefone.add(i);
                    mensagens.add(new EnvioAvulso.Mensagem(i.getPessoa().getId(), i.getPessoa().getNomeCompleto(), telefone,
                            montar(e, e.getWhatsappLayoutLembreteId(), TipoEnvio.WHATSAPP, Momento.LEMBRETE, i.getPessoa(), tenant, hoje).corpo()));
                }
                List<UUID> ids = envio.enfileirarWhatsapp("Evento: " + e.getTitulo() + " (lembrete)", mensagens);
                for (int k = 0; k < comTelefone.size(); k++) comTelefone.get(k).lembreteEnfileirado(ids.get(k), quando);
                total += ids.size();
            }
            if (e.isEmailHabilitado()) {
                List<EventoInscricao> pendentes = inscricoes.pendentesDeLembreteEmail(e.getId(), desde);
                List<EventoInscricao> comEmail = new ArrayList<>();
                List<EnvioAvulso.MensagemEmail> mensagens = new ArrayList<>();
                if (tenant == null && !pendentes.isEmpty()) tenant = tenantAtual();
                for (EventoInscricao i : pendentes) {
                    String email = emailPrincipal(i.getPessoa());
                    if (email == null) continue;
                    comEmail.add(i);
                    Pronta p = montar(e, e.getEmailLayoutLembreteId(), TipoEnvio.EMAIL, Momento.LEMBRETE, i.getPessoa(), tenant, hoje);
                    mensagens.add(new EnvioAvulso.MensagemEmail(i.getPessoa().getId(), i.getPessoa().getNomeCompleto(), email, p.assunto(), p.corpo()));
                }
                List<UUID> ids = envio.enfileirarEmail("Evento: " + e.getTitulo() + " (lembrete)", "Lembrete: " + e.getTitulo(), mensagens);
                for (int k = 0; k < comEmail.size(); k++) comEmail.get(k).lembreteEmailEnfileirado(ids.get(k), quando);
                total += ids.size();
            }
        }
        return total;
    }

    // ---- Fotos

    @Transactional
    public EventoDetalhe enviarFoto(UUID id, MultipartFile arquivo) {
        Evento e = buscar(id);
        if (arquivo == null || arquivo.isEmpty()) throw new BadRequestException("Nenhuma foto enviada.");
        if (fotos.countByEventoId(id) >= MAX_FOTOS) throw new ConflictException("No máximo " + MAX_FOTOS + " fotos por evento.");
        String caminho = "eventos/" + TenantContext.get() + "/" + id + "/" + UUID.randomUUID() + ExtensaoDeFoto.de(arquivo.getContentType());
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler a foto enviada.", ex);
        }
        String salvo = storage.armazenar(caminho, conteudo, arquivo.getContentType());
        fotos.save(new EventoFoto(id, salvo, fotos.countByEventoId(id) == 0));
        audit.registrar("FOTO", "EVENTO", id, List.of("fotos"));
        return detalhe(e);
    }

    @Transactional
    public EventoDetalhe definirCapa(UUID id, UUID fotoId) {
        Evento e = buscar(id);
        List<EventoFoto> lista = fotos.findByEventoIdOrderByCapaDescCreatedAtAsc(id);
        if (lista.stream().noneMatch(f -> f.getId().equals(fotoId))) throw new ResourceNotFoundException("Foto não encontrada.");
        lista.forEach(f -> f.setCapa(f.getId().equals(fotoId)));
        return detalhe(e);
    }

    @Transactional
    public EventoDetalhe excluirFoto(UUID id, UUID fotoId) {
        Evento e = buscar(id);
        EventoFoto foto = fotos.findById(fotoId).filter(f -> f.getEventoId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));
        fotos.delete(foto);
        fotos.flush();
        if (foto.isCapa()) {
            fotos.findByEventoIdOrderByCapaDescCreatedAtAsc(id).stream().findFirst().ifPresent(f -> f.setCapa(true));
        }
        storage.excluir(foto.getCaminho());
        audit.registrar("FOTO_EXCLUIDA", "EVENTO", id, List.of("fotos"));
        return detalhe(e);
    }

    // ---- Apoio

    private Evento buscar(UUID id) {
        return eventos.findById(id).orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
    }

    private String nomeParoquia() {
        UUID tenant = TenantContext.get();
        return tenant == null ? null : tenants.findById(tenant).map(t -> t.getNome()).orElse(null);
    }

    static String telefonePrincipal(Pessoa pessoa) {
        return pessoa.getTelefones().stream()
                .filter(t -> t.getNumero() != null && !t.getNumero().isBlank())
                .sorted(Comparator.comparing((PessoaTelefone t) -> !t.isPrincipal()))
                .map(t -> t.getNumero().trim()).findFirst().orElse(null);
    }

    private enum Momento { CONFIRMACAO, LEMBRETE, CANCELAMENTO }

    /** Mensagem pronta: assunto só existe no e-mail (o corpo do e-mail é HTML). */
    private record Pronta(String assunto, String corpo) {
    }

    /**
     * Texto de uma mensagem do evento. Com layout escolhido (ativo, do tipo EVENTO e do canal certo) vale o layout;
     * sem layout, ou se ele sumiu ou foi inativado, vale o texto padrão do sistema (o que o evento já tinha).
     */
    private Pronta montar(Evento e, UUID layoutId, TipoEnvio canal, Momento momento, Pessoa pessoa, Tenant tenant, LocalDate hoje) {
        Layout layout = layoutId == null ? null : layouts.findById(layoutId)
                .filter(l -> l.isAtivo() && l.getTipoLayout() == TipoLayout.EVENTO && l.getTipoEnvio() == canal)
                .orElse(null);
        if (layout != null) {
            ContextoDeEnvio ctx = comunicados.contextoDa(tenant, pessoa).comEvento(
                    e.getTitulo(), e.getInicio().format(DATA), e.getInicio().format(HORA),
                    MensagensDeEvento.quando(hoje, e.getInicio().toLocalDate()), e.getLocalNome(),
                    MensagensDeEvento.endereco(e), e.getMapaUrl(), e.getResponsavelNome(), e.getResponsavelTelefone());
            if (canal == TipoEnvio.WHATSAPP) {
                return new Pronta(null, Renderizador.renderizarSemLinhasVazias(layout.getConteudo(), canal, ctx));
            }
            String assunto = layout.getAssunto() == null || layout.getAssunto().isBlank()
                    ? assuntoPadrao(e, momento)
                    : Renderizador.renderizar(layout.getAssunto(), TipoEnvio.WHATSAPP, ctx);
            return new Pronta(assunto, Renderizador.renderizar(layout.getConteudo(), canal, ctx));
        }
        String modelo = switch (momento) {
            case CONFIRMACAO -> textoOu(e.getMensagemConfirmacao(), MensagensDeEvento.CONFIRMACAO_PADRAO);
            case LEMBRETE -> textoOu(e.getMensagemLembrete(), MensagensDeEvento.LEMBRETE_PADRAO);
            case CANCELAMENTO -> MensagensDeEvento.CANCELAMENTO;
        };
        String texto = MensagensDeEvento.renderizar(modelo, e, pessoa.getNomeCompleto(), tenant.getNome(), hoje);
        return canal == TipoEnvio.WHATSAPP ? new Pronta(null, texto) : new Pronta(assuntoPadrao(e, momento), paraHtml(texto));
    }

    private static String assuntoPadrao(Evento e, Momento momento) {
        return switch (momento) {
            case CONFIRMACAO -> "Inscrição confirmada: " + e.getTitulo();
            case LEMBRETE -> "Lembrete: " + e.getTitulo();
            case CANCELAMENTO -> "Evento cancelado: " + e.getTitulo();
        };
    }

    /** Texto puro vira HTML de e-mail: escapa e troca quebra de linha por parágrafo. */
    static String paraHtml(String texto) {
        String escapado = texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
        return "<p>" + escapado.replace("\n", "<br>") + "</p>";
    }

    private Tenant tenantAtual() {
        return tenants.findById(TenantContext.get()).orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
    }

    /** Lista de "N dias antes": vazia = sem lembrete; zero é ignorado; de 1 a 30. */
    private static List<Integer> diasDeLembrete(List<Integer> dias) {
        if (dias == null) return List.of(1);
        for (Integer d : dias) {
            if (d != null && (d < 0 || d > 30)) throw new BadRequestException("Lembrete: de 1 a 30 dias antes.");
        }
        return dias;
    }

    /** Só aceita layout do tipo EVENTO e do canal certo (a busca já é da paróquia do contexto). */
    private UUID layoutDoEvento(UUID id, TipoEnvio canal) {
        if (id == null) return null;
        return layouts.findById(id)
                .filter(l -> l.getTipoLayout() == TipoLayout.EVENTO && l.getTipoEnvio() == canal)
                .map(Layout::getId)
                .orElseThrow(() -> new BadRequestException("Layout inválido para o envio por "
                        + (canal == TipoEnvio.EMAIL ? "e-mail" : "WhatsApp") + "."));
    }

    static String emailPrincipal(Pessoa pessoa) {
        return pessoa.getEmails().stream()
                .filter(m -> m.getEmail() != null && !m.getEmail().isBlank())
                .sorted(Comparator.comparing((PessoaEmail m) -> !m.isPrincipal()))
                .map(m -> m.getEmail().trim()).findFirst().orElse(null);
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String textoOu(String s, String padrao) {
        return s == null || s.isBlank() ? padrao : s.trim();
    }

    private static String link(String s) {
        String v = texto(s);
        if (v == null) return null;
        if (!v.startsWith("https://") && !v.startsWith("http://")) {
            throw new BadRequestException("O link do mapa precisa começar com https://");
        }
        return v;
    }
}
