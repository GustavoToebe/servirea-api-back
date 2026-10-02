package br.com.servire.api.notificacao;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.comunicacao.ComunicadoDestinatario;
import br.com.servire.api.comunicacao.EnvioAvulso;
import br.com.servire.api.comunicacao.StatusEnvio;
import br.com.servire.api.comunicacao.TipoEnvio;
import br.com.servire.api.escala.Escala;
import br.com.servire.api.escala.EscalaEvento;
import br.com.servire.api.escala.EscalaVaga;
import br.com.servire.api.escala.StatusEscala;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.mural.Aviso;
import br.com.servire.api.notificacao.NotificacaoDtos.*;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaEmail;
import br.com.servire.api.pessoa.PessoaTelefone;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Notificações de escala e mural (F05/F13). Tudo entra na fila de comunicados já existente: este serviço só
 * monta o texto, confere contato e autorização e registra uma entrega por origem, versão e canal, o que impede
 * repetir o mesmo aviso. Gatilhos automáticos nascem desligados. Nenhuma mensagem sai daqui diretamente.
 */
@Service
public class NotificacaoService {
    static final int LIMITE_DESTINATARIOS = 2000;
    private static final int PAGINA = 30;
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final Duration ANTECEDENCIA_LEMBRETE = Duration.ofHours(24);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @PersistenceContext
    private EntityManager em;
    private final FuncionalidadesPlano plano;
    private final EnvioAvulso envio;
    private final AuditLogService audit;

    public NotificacaoService(FuncionalidadesPlano plano, EnvioAvulso envio, AuditLogService audit) {
        this.plano = plano;
        this.envio = envio;
        this.audit = audit;
    }

    // ---- Configuração dos gatilhos

    @Transactional(readOnly = true)
    public List<Config> configuracoes() {
        List<Config> lista = new ArrayList<>();
        for (OrigemNotificacao o : OrigemNotificacao.values()) {
            for (TipoEnvio c : TipoEnvio.values()) {
                NotificacaoConfig e = config(o, c);
                lista.add(new Config(o, c, e != null && e.ativo, e == null ? 0 : e.versao));
            }
        }
        return lista;
    }

    @Transactional
    public Config configurar(OrigemNotificacao origem, TipoEnvio canal, Configurar req) {
        plano.exigir("COMUNICACAO");
        NotificacaoConfig e = config(origem, canal);
        long atual = e == null ? 0 : e.versao;
        if (req.versao() != atual) {
            throw new ConflictException("A configuração mudou. Atualize antes de salvar.");
        }
        if (e == null) {
            e = new NotificacaoConfig();
            e.versao = 1;
            e.origem = origem;
            e.canal = canal;
        }
        e.ativo = req.ativo();
        em.persist(e);
        em.flush();
        audit.registrar("CONFIGURAR", "NOTIFICACAO", e.id, List.of("ativo"));
        return new Config(origem, canal, e.ativo, e.versao);
    }

    // ---- Escala

    @Transactional
    public Resultado notificarEscala(UUID escalaId, TipoEnvio canal) {
        plano.exigir("COMUNICACAO");
        Escala escala = em.find(Escala.class, escalaId, LockModeType.PESSIMISTIC_WRITE);
        if (escala == null) {
            throw new ResourceNotFoundException("Escala não encontrada.");
        }
        if (escala.getStatus() != StatusEscala.FINALIZADA) {
            throw new ConflictException("Só é possível avisar os escalados de uma escala finalizada.");
        }
        if (existe(OrigemNotificacao.ESCALA, escalaId, escala.getVersion(), canal)) {
            throw new ConflictException("Os escalados já foram avisados por este canal nesta versão da escala.");
        }
        Resultado r = enfileirarEscala(escala, canal, NotificacaoEntrega.Gatilho.MANUAL);
        if (r.total() == 0) {
            throw new BadRequestException("Nenhuma pessoa escalada tem contato" + (canal == TipoEnvio.WHATSAPP ? " e autorização de WhatsApp" : "") + " neste canal.");
        }
        return r;
    }

    /** Chamado dentro da transação de finalizar a escala. Só age com gatilho ligado e plano de comunicação ativo. */
    public void aoFinalizarEscala(Escala escala) {
        if (!plano.permitida("COMUNICACAO")) {
            return;
        }
        em.flush();
        for (TipoEnvio canal : TipoEnvio.values()) {
            NotificacaoConfig c = config(OrigemNotificacao.ESCALA, canal);
            if (c != null && c.ativo && !existe(OrigemNotificacao.ESCALA, escala.getId(), escala.getVersion(), canal)) {
                enfileirarEscala(escala, canal, NotificacaoEntrega.Gatilho.AUTOMATICO);
            }
        }
    }

    private Resultado enfileirarEscala(Escala escala, TipoEnvio canal, NotificacaoEntrega.Gatilho gatilho) {
        Map<UUID, List<EscalaVaga>> porPessoa = new LinkedHashMap<>();
        for (EscalaEvento ev : escala.getEventos()) {
            if (ev.isReferencia()) {
                continue;
            }
            for (EscalaVaga v : ev.getVagas()) {
                if (v.getVoluntario() != null) {
                    porPessoa.computeIfAbsent(v.getVoluntario().getId(), k -> new ArrayList<>()).add(v);
                }
            }
        }
        if (porPessoa.size() > LIMITE_DESTINATARIOS) {
            throw new BadRequestException("A notificação aceita até " + LIMITE_DESTINATARIOS + " pessoas.");
        }
        String paroquia = nomeDaParoquia();
        List<EnvioAvulso.Mensagem> whats = new ArrayList<>();
        List<EnvioAvulso.MensagemEmail> emails = new ArrayList<>();
        int ignorados = 0;
        for (List<EscalaVaga> vagas : porPessoa.values()) {
            vagas.sort(Comparator.comparing((EscalaVaga v) -> v.getEvento().getData()).thenComparing(v -> v.getEvento().getHorario()));
            Voluntario voluntario = vagas.getFirst().getVoluntario();
            Pessoa pessoa = voluntario.getPessoa();
            String contato = contato(pessoa, voluntario, canal);
            if (contato == null) {
                ignorados++;
                continue;
            }
            if (canal == TipoEnvio.WHATSAPP) {
                whats.add(new EnvioAvulso.Mensagem(pessoa.getId(), pessoa.getNomeCompleto(), contato, textoEscala(pessoa, escala, vagas, paroquia)));
            } else {
                emails.add(new EnvioAvulso.MensagemEmail(pessoa.getId(), pessoa.getNomeCompleto(), contato, null,
                        html(textoEscala(pessoa, escala, vagas, paroquia))));
            }
        }
        String origem = "Escala: " + escala.getTitulo();
        EnvioAvulso.Enfileiradas f = canal == TipoEnvio.WHATSAPP
                ? envio.enfileirarWhatsappDetalhado(origem, whats)
                : envio.enfileirarEmailDetalhado(origem, "Escala: " + escala.getTitulo(), emails);
        return registrar(OrigemNotificacao.ESCALA, escala.getId(), escala.getVersion(), canal, gatilho, f, ignorados);
    }

    private static String textoEscala(Pessoa pessoa, Escala escala, List<EscalaVaga> vagas, String paroquia) {
        StringBuilder t = new StringBuilder("Olá " + pessoa.getNomeCompleto() + "! A escala *" + escala.getTitulo()
                + "* foi finalizada. Suas participações:\n");
        for (EscalaVaga v : vagas) {
            t.append("• ").append(v.getEvento().getData().format(DATA)).append(" ").append(v.getEvento().getHorario().format(HORA))
                    .append(" — ").append(v.getEvento().getCelebracao()).append(" (").append(v.getFuncao().name().toLowerCase(Locale.ROOT)).append(")\n");
        }
        return t.append(paroquia).toString();
    }

    // ---- Lembrete de escala

    /**
     * Lembrete das celebrações de escala finalizada que começam nas próximas 24 horas (horário de Brasília), para quem está
     * escalado e não recusou. Um aviso por celebração e canal (chave com versão 0), só com o gatilho ESCALA_LEMBRETE ligado.
     * Rodar de novo é seguro. Devolve quantas mensagens foram para a fila.
     */
    @Transactional
    public int lembretesDeEscala() {
        if (!plano.permitida("COMUNICACAO")) {
            return 0;
        }
        ZonedDateTime agora = ZonedDateTime.now(BRASILIA);
        Instant limite = agora.toInstant().plus(ANTECEDENCIA_LEMBRETE);
        int total = 0;
        for (TipoEnvio canal : TipoEnvio.values()) {
            NotificacaoConfig c = config(OrigemNotificacao.ESCALA_LEMBRETE, canal);
            if (c == null || !c.ativo) {
                continue;
            }
            List<EscalaEvento> eventos = em.createQuery("select e from EscalaEvento e where e.referencia=false and e.escala.status=:fin "
                            + "and e.data between :de and :ate order by e.data, e.horario", EscalaEvento.class)
                    .setParameter("fin", StatusEscala.FINALIZADA).setParameter("de", agora.toLocalDate())
                    .setParameter("ate", agora.toLocalDate().plusDays(1)).setMaxResults(200).getResultList();
            for (EscalaEvento ev : eventos) {
                Instant inicio = ev.getData().atTime(ev.getHorario()).atZone(BRASILIA).toInstant();
                if (!inicio.isAfter(agora.toInstant()) || inicio.isAfter(limite) || existe(OrigemNotificacao.ESCALA_LEMBRETE, ev.getId(), 0, canal)) {
                    continue;
                }
                total += enfileirarLembrete(ev, canal);
            }
        }
        return total;
    }

    private int enfileirarLembrete(EscalaEvento ev, TipoEnvio canal) {
        String paroquia = nomeDaParoquia();
        List<EnvioAvulso.Mensagem> whats = new ArrayList<>();
        List<EnvioAvulso.MensagemEmail> emails = new ArrayList<>();
        int ignorados = 0;
        for (EscalaVaga v : ev.getVagas()) {
            if (v.getVoluntario() == null || v.getResposta() == br.com.servire.api.escala.RespostaParticipacao.RECUSADA) {
                continue;
            }
            Pessoa pessoa = v.getVoluntario().getPessoa();
            String contato = contato(pessoa, v.getVoluntario(), canal);
            if (contato == null) {
                ignorados++;
                continue;
            }
            String texto = "Olá " + pessoa.getNomeCompleto() + "! Lembrete: você está escalado(a) para *" + ev.getCelebracao() + "* em "
                    + ev.getData().format(DATA) + " às " + ev.getHorario().format(HORA) + " ("
                    + v.getFuncao().name().toLowerCase(Locale.ROOT) + ").\n" + paroquia;
            if (canal == TipoEnvio.WHATSAPP) {
                whats.add(new EnvioAvulso.Mensagem(pessoa.getId(), pessoa.getNomeCompleto(), contato, texto));
            } else {
                emails.add(new EnvioAvulso.MensagemEmail(pessoa.getId(), pessoa.getNomeCompleto(), contato, null, html(texto)));
            }
        }
        String origem = "Lembrete: " + ev.getCelebracao() + " " + ev.getData().format(DATA) + " " + ev.getHorario().format(HORA);
        EnvioAvulso.Enfileiradas f = canal == TipoEnvio.WHATSAPP
                ? envio.enfileirarWhatsappDetalhado(origem, whats)
                : envio.enfileirarEmailDetalhado(origem, origem, emails);
        registrar(OrigemNotificacao.ESCALA_LEMBRETE, ev.getId(), 0, canal, NotificacaoEntrega.Gatilho.AUTOMATICO, f, ignorados);
        return f.destinatarios().size();
    }

    // ---- Mural

    @Transactional
    public Resultado notificarAviso(UUID avisoId, TipoEnvio canal, long versao) {
        plano.exigir("COMUNICACAO");
        Aviso aviso = em.find(Aviso.class, avisoId, LockModeType.PESSIMISTIC_WRITE);
        if (aviso == null) {
            throw new ResourceNotFoundException("Aviso não encontrado.");
        }
        if (aviso.status != Aviso.Status.PUBLICADO) {
            throw new ConflictException("Só é possível notificar um aviso publicado.");
        }
        if (aviso.versao != versao) {
            throw new ConflictException("O aviso mudou. Atualize antes de notificar.");
        }
        if (existe(OrigemNotificacao.MURAL, avisoId, aviso.versao, canal)) {
            throw new ConflictException("Este aviso já foi notificado por este canal nesta versão.");
        }
        Resultado r = enfileirarAviso(aviso, canal, NotificacaoEntrega.Gatilho.MANUAL);
        if (r.total() == 0) {
            throw new BadRequestException("Nenhum destinatário tem contato" + (canal == TipoEnvio.WHATSAPP ? " e autorização de WhatsApp" : "") + " neste canal.");
        }
        return r;
    }

    /** Chamado ao publicar um aviso novo ou voltar um arquivado a publicado, já com a versão gravada. */
    public void aoPublicarAviso(Aviso aviso) {
        if (aviso.status != Aviso.Status.PUBLICADO || !plano.permitida("COMUNICACAO")) {
            return;
        }
        for (TipoEnvio canal : TipoEnvio.values()) {
            NotificacaoConfig c = config(OrigemNotificacao.MURAL, canal);
            if (c != null && c.ativo && !existe(OrigemNotificacao.MURAL, aviso.id, aviso.versao, canal)) {
                try {
                    enfileirarAviso(aviso, canal, NotificacaoEntrega.Gatilho.AUTOMATICO);
                } catch (BadRequestException limite) {
                    // Público acima do limite não é notificado sozinho; a coordenação decide pelo envio manual.
                }
            }
        }
    }

    private Resultado enfileirarAviso(Aviso aviso, TipoEnvio canal, NotificacaoEntrega.Gatilho gatilho) {
        List<UUID> pessoas = aviso.publico == Aviso.Publico.TODOS
                ? em.createQuery("select distinct v.pessoaId from UsuarioTenant v where v.tenant.id=:t and v.status=:ativo and v.pessoaId is not null", UUID.class)
                        .setParameter("t", TenantContext.get()).setParameter("ativo", UsuarioTenant.Status.ATIVO)
                        .setMaxResults(LIMITE_DESTINATARIOS + 1).getResultList()
                : em.createQuery("select distinct v.pessoaId from UsuarioTenant v, br.com.servire.api.mural.AvisoDestinatario d where d.avisoId=:a and d.usuarioId=v.usuario.id "
                                + "and v.tenant.id=:t and v.status=:ativo and v.pessoaId is not null", UUID.class)
                        .setParameter("a", aviso.id).setParameter("t", TenantContext.get()).setParameter("ativo", UsuarioTenant.Status.ATIVO)
                        .setMaxResults(LIMITE_DESTINATARIOS + 1).getResultList();
        if (pessoas.size() > LIMITE_DESTINATARIOS) {
            throw new BadRequestException("A notificação aceita até " + LIMITE_DESTINATARIOS + " pessoas.");
        }
        String paroquia = nomeDaParoquia();
        List<EnvioAvulso.Mensagem> whats = new ArrayList<>();
        List<EnvioAvulso.MensagemEmail> emails = new ArrayList<>();
        int ignorados = 0;
        for (UUID id : pessoas) {
            Pessoa pessoa = em.find(Pessoa.class, id);
            String contato = pessoa == null ? null : contato(pessoa, pessoa.getVoluntario(), canal);
            if (contato == null) {
                ignorados++;
                continue;
            }
            String texto = "Olá " + pessoa.getNomeCompleto() + "! Novo aviso no mural: *" + aviso.titulo + "*\n"
                    + limitar(aviso.descricao, 500) + "\n" + paroquia;
            if (canal == TipoEnvio.WHATSAPP) {
                whats.add(new EnvioAvulso.Mensagem(pessoa.getId(), pessoa.getNomeCompleto(), contato, texto));
            } else {
                emails.add(new EnvioAvulso.MensagemEmail(pessoa.getId(), pessoa.getNomeCompleto(), contato, null, html(texto)));
            }
        }
        String origem = "Mural: " + aviso.titulo;
        EnvioAvulso.Enfileiradas f = canal == TipoEnvio.WHATSAPP
                ? envio.enfileirarWhatsappDetalhado(origem, whats)
                : envio.enfileirarEmailDetalhado(origem, "Mural: " + aviso.titulo, emails);
        return registrar(OrigemNotificacao.MURAL, aviso.id, aviso.versao, canal, gatilho, f, ignorados);
    }

    // ---- Centro de entregas

    @Transactional(readOnly = true)
    public Pagina entregas(OrigemNotificacao origem, int pagina) {
        if (pagina < 0 || pagina > 100000) {
            throw new BadRequestException("Página inválida.");
        }
        String filtro = origem == null ? "" : " where e.origem=:o";
        var consulta = em.createQuery("select e from NotificacaoEntrega e" + filtro + " order by e.criadoEm desc, e.id desc", NotificacaoEntrega.class);
        var contagem = em.createQuery("select count(e) from NotificacaoEntrega e" + filtro, Long.class);
        if (origem != null) {
            consulta.setParameter("o", origem);
            contagem.setParameter("o", origem);
        }
        List<NotificacaoEntrega> linhas = consulta.setFirstResult(pagina * PAGINA).setMaxResults(PAGINA).getResultList();
        Set<UUID> comunicados = linhas.stream().map(e -> e.comunicadoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Map<StatusEnvio, Long>> status = new HashMap<>();
        if (!comunicados.isEmpty()) {
            for (Object[] r : em.createQuery("select d.comunicadoId, d.status, count(d) from ComunicadoDestinatario d where d.comunicadoId in :ids group by d.comunicadoId, d.status", Object[].class)
                    .setParameter("ids", comunicados).getResultList()) {
                status.computeIfAbsent((UUID) r[0], k -> new EnumMap<>(StatusEnvio.class)).put((StatusEnvio) r[1], (Long) r[2]);
            }
        }
        Map<UUID, String> titulos = titulos(linhas);
        List<Entrega> itens = linhas.stream().map(e -> {
            Map<StatusEnvio, Long> s = status.getOrDefault(e.comunicadoId, Map.of());
            return new Entrega(e.id, e.origem, e.referenciaId, titulos.get(e.referenciaId), e.referenciaVersao, e.canal, e.gatilho,
                    e.total, e.ignorados, s.getOrDefault(StatusEnvio.PENDENTE, 0L), s.getOrDefault(StatusEnvio.ENVIADO, 0L),
                    s.getOrDefault(StatusEnvio.FALHA, 0L), e.criadoEm);
        }).toList();
        return new Pagina(itens, contagem.getSingleResult(), pagina, PAGINA);
    }

    private Map<UUID, String> titulos(List<NotificacaoEntrega> linhas) {
        Map<UUID, String> mapa = new HashMap<>();
        Set<UUID> escalas = linhas.stream().filter(e -> e.origem == OrigemNotificacao.ESCALA).map(e -> e.referenciaId).collect(Collectors.toSet());
        Set<UUID> avisos = linhas.stream().filter(e -> e.origem == OrigemNotificacao.MURAL).map(e -> e.referenciaId).collect(Collectors.toSet());
        if (!escalas.isEmpty()) {
            em.createQuery("select e.id, e.titulo from Escala e where e.id in :ids", Object[].class).setParameter("ids", escalas)
                    .getResultList().forEach(r -> mapa.put((UUID) r[0], (String) r[1]));
        }
        Set<UUID> celebracoes = linhas.stream().filter(e -> e.origem == OrigemNotificacao.ESCALA_LEMBRETE).map(e -> e.referenciaId).collect(Collectors.toSet());
        if (!celebracoes.isEmpty()) {
            em.createQuery("select e.id, e.celebracao, e.data, e.horario from EscalaEvento e where e.id in :ids", Object[].class).setParameter("ids", celebracoes)
                    .getResultList().forEach(r -> mapa.put((UUID) r[0], r[1] + " " + ((java.time.LocalDate) r[2]).format(DATA) + " " + ((java.time.LocalTime) r[3]).format(HORA)));
        }
        if (!avisos.isEmpty()) {
            em.createQuery("select a.id, a.titulo from Aviso a where a.id in :ids", Object[].class).setParameter("ids", avisos)
                    .getResultList().forEach(r -> mapa.put((UUID) r[0], (String) r[1]));
        }
        return mapa;
    }

    // ---- Apoio

    private Resultado registrar(OrigemNotificacao origem, UUID referencia, long versao, TipoEnvio canal,
                                NotificacaoEntrega.Gatilho gatilho, EnvioAvulso.Enfileiradas f, int ignorados) {
        NotificacaoEntrega e = new NotificacaoEntrega();
        e.origem = origem;
        e.referenciaId = referencia;
        e.referenciaVersao = versao;
        e.canal = canal;
        e.gatilho = gatilho;
        e.total = f.destinatarios().size();
        e.ignorados = ignorados;
        e.comunicadoId = f.comunicadoId();
        e.criadoPor = usuarioAtual();
        e.criadoEm = Instant.now();
        em.persist(e);
        em.flush();
        audit.registrar("NOTIFICAR", origem.name(), referencia, List.of("canal", "total", "ignorados"));
        return new Resultado(e.id, e.total, e.ignorados);
    }

    /** WhatsApp exige autorização do voluntário e telefone; e-mail exige apenas o e-mail cadastrado. */
    private static String contato(Pessoa pessoa, Voluntario voluntario, TipoEnvio canal) {
        if (canal == TipoEnvio.WHATSAPP) {
            if (voluntario == null || !voluntario.isAutorizaWhatsapp()) {
                return null;
            }
            return pessoa.getTelefones().stream().filter(t -> t.getNumero() != null && !t.getNumero().isBlank())
                    .sorted(Comparator.comparing((PessoaTelefone t) -> !t.isPrincipal()))
                    .map(t -> t.getNumero().trim()).findFirst().orElse(null);
        }
        return pessoa.getEmails().stream().filter(m -> m.getEmail() != null && !m.getEmail().isBlank())
                .sorted(Comparator.comparing((PessoaEmail m) -> !m.isPrincipal()))
                .map(m -> m.getEmail().trim()).findFirst().orElse(null);
    }

    private boolean existe(OrigemNotificacao origem, UUID referencia, long versao, TipoEnvio canal) {
        return em.createQuery("select count(e) from NotificacaoEntrega e where e.origem=:o and e.referenciaId=:r and e.referenciaVersao=:v and e.canal=:c", Long.class)
                .setParameter("o", origem).setParameter("r", referencia).setParameter("v", versao).setParameter("c", canal)
                .getSingleResult() > 0;
    }

    private NotificacaoConfig config(OrigemNotificacao origem, TipoEnvio canal) {
        return em.createQuery("select c from NotificacaoConfig c where c.origem=:o and c.canal=:c", NotificacaoConfig.class)
                .setParameter("o", origem).setParameter("c", canal).getResultStream().findFirst().orElse(null);
    }

    private String nomeDaParoquia() {
        Tenant t = em.find(Tenant.class, TenantContext.get());
        return t == null ? "" : t.getNome();
    }

    private static String html(String texto) {
        String limpo = HtmlUtils.htmlEscape(texto).replace("*", "");
        return "<p>" + limpo.replace("\n", "<br>") + "</p>";
    }

    private static String limitar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static UUID usuarioAtual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthenticatedUser u ? u.usuarioId() : null;
    }
}
