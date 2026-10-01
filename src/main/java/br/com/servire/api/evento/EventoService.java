package br.com.servire.api.evento;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.comunicacao.EnvioAvulso;
import br.com.servire.api.comunicacao.StatusEnvio;
import br.com.servire.api.evento.EventoDtos.CancelarRequest;
import br.com.servire.api.evento.EventoDtos.EventoDetalhe;
import br.com.servire.api.evento.EventoDtos.EventoRequest;
import br.com.servire.api.evento.EventoDtos.EventoResumo;
import br.com.servire.api.evento.EventoDtos.FotoResponse;
import br.com.servire.api.evento.EventoDtos.InscritoResponse;
import br.com.servire.api.evento.EventoDtos.SituacaoMensagem;
import br.com.servire.api.evento.EventoDtos.SituacaoTela;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.PessoaTelefone;
import br.com.servire.api.storage.ExtensaoDeFoto;
import br.com.servire.api.storage.StorageService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private final EventoRepository eventos;
    private final EventoFotoRepository fotos;
    private final EventoInscricaoRepository inscricoes;
    private final PessoaRepository pessoas;
    private final TenantRepository tenants;
    private final StorageService storage;
    private final EnvioAvulso envio;
    private final AuditLogService audit;
    private final Clock clock;

    @Autowired
    public EventoService(EventoRepository eventos, EventoFotoRepository fotos, EventoInscricaoRepository inscricoes,
                         PessoaRepository pessoas, TenantRepository tenants, StorageService storage, EnvioAvulso envio,
                         AuditLogService audit) {
        this(eventos, fotos, inscricoes, pessoas, tenants, storage, envio, audit, Clock.system(BRASILIA));
    }

    EventoService(EventoRepository eventos, EventoFotoRepository fotos, EventoInscricaoRepository inscricoes,
                  PessoaRepository pessoas, TenantRepository tenants, StorageService storage, EnvioAvulso envio,
                  AuditLogService audit, Clock clock) {
        this.eventos = eventos;
        this.fotos = fotos;
        this.inscricoes = inscricoes;
        this.pessoas = pessoas;
        this.tenants = tenants;
        this.storage = storage;
        this.envio = envio;
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
                e.getMapaUrl(), e.getVagas(), e.getResponsavelNome(), e.getResponsavelTelefone(), e.getLembreteDias(),
                e.getMensagemConfirmacao(), e.getMensagemLembrete(), situacao(e, agora()), listaFotos,
                inscritos(e.getId()), MensagensDeEvento.TAGS);
    }

    private List<InscritoResponse> inscritos(UUID eventoId) {
        List<EventoInscricao> lista = inscricoes.findByEventoIdOrderByCreatedAtAsc(eventoId);
        Set<UUID> ids = new HashSet<>();
        for (EventoInscricao i : lista) {
            if (i.getConfirmacaoDestinatarioId() != null) ids.add(i.getConfirmacaoDestinatarioId());
            if (i.getLembreteDestinatarioId() != null) ids.add(i.getLembreteDestinatarioId());
        }
        Map<UUID, StatusEnvio> status = envio.situacoes(ids);
        return lista.stream().map(i -> {
            String telefone = telefonePrincipal(i.getPessoa());
            return new InscritoResponse(i.getId(), i.getPessoa().getId(), i.getPessoa().getNomeCompleto(), telefone,
                    mensagem(i, telefone, i.getConfirmacaoDestinatarioId(), status),
                    i.getLembreteDestinatarioId() == null && i.isAutorizaWhatsapp() && telefone != null
                            ? null : mensagem(i, telefone, i.getLembreteDestinatarioId(), status));
        }).sorted(Comparator.comparing(InscritoResponse::nome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static SituacaoMensagem mensagem(EventoInscricao i, String telefone, UUID destinatarioId, Map<UUID, StatusEnvio> status) {
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
        e.setLembreteDias(req.lembreteDias() == null ? 1 : req.lembreteDias());
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
            List<EnvioAvulso.Mensagem> mensagens = new ArrayList<>();
            String paroquia = nomeParoquia();
            for (EventoInscricao i : inscricoes.findByEventoIdOrderByCreatedAtAsc(id)) {
                String telefone = telefonePrincipal(i.getPessoa());
                if (!i.isAutorizaWhatsapp() || telefone == null) continue;
                mensagens.add(new EnvioAvulso.Mensagem(i.getPessoa().getId(), i.getPessoa().getNomeCompleto(), telefone,
                        MensagensDeEvento.renderizar(MensagensDeEvento.CANCELAMENTO, e, i.getPessoa().getNomeCompleto(),
                                paroquia, agora().toLocalDate())));
            }
            envio.enfileirarWhatsapp("Evento cancelado: " + e.getTitulo(), mensagens);
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
        String telefone = telefonePrincipal(pessoa);
        if (autoriza && telefone != null) {
            String texto = MensagensDeEvento.renderizar(e.getMensagemConfirmacao(), e, pessoa.getNomeCompleto(),
                    nomeParoquia(), agora().toLocalDate());
            List<UUID> ids = envio.enfileirarWhatsapp("Evento: " + e.getTitulo() + " (inscrição)",
                    List.of(new EnvioAvulso.Mensagem(pessoa.getId(), pessoa.getNomeCompleto(), telefone, texto)));
            inscricao.setConfirmacaoDestinatarioId(ids.get(0));
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
     * Lembretes do dia, para a paróquia do contexto: eventos publicados que começam em até {@code lembreteDias}
     * dias e ainda não aconteceram. Quem já recebeu não recebe de novo; se o job não rodou num dia, o próximo
     * recupera. Devolve quantas mensagens entraram na fila.
     */
    @Transactional
    public int enviarLembretes() {
        LocalDateTime agora = agora();
        LocalDate hoje = agora.toLocalDate();
        String paroquia = null;
        int total = 0;
        for (Evento e : eventos.publicadosEntre(agora, hoje.plusDays(31).atStartOfDay())) {
            long diasAte = java.time.temporal.ChronoUnit.DAYS.between(hoje, e.getInicio().toLocalDate());
            if (diasAte > e.getLembreteDias()) continue;
            List<EventoInscricao> pendentes = inscricoes.findByEventoIdAndAutorizaWhatsappTrueAndLembreteEnviadoEmIsNull(e.getId());
            List<EventoInscricao> comTelefone = new ArrayList<>();
            List<EnvioAvulso.Mensagem> mensagens = new ArrayList<>();
            if (paroquia == null && !pendentes.isEmpty()) paroquia = nomeParoquia();
            for (EventoInscricao i : pendentes) {
                String telefone = telefonePrincipal(i.getPessoa());
                if (telefone == null) continue;
                comTelefone.add(i);
                mensagens.add(new EnvioAvulso.Mensagem(i.getPessoa().getId(), i.getPessoa().getNomeCompleto(), telefone,
                        MensagensDeEvento.renderizar(e.getMensagemLembrete(), e, i.getPessoa().getNomeCompleto(), paroquia, hoje)));
            }
            List<UUID> ids = envio.enfileirarWhatsapp("Evento: " + e.getTitulo() + " (lembrete)", mensagens);
            java.time.Instant quando = java.time.Instant.now(clock);
            for (int k = 0; k < comTelefone.size(); k++) {
                comTelefone.get(k).lembreteEnfileirado(ids.get(k), quando);
            }
            total += ids.size();
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
