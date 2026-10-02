package br.com.servire.api.checkin;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.checkin.Checkin.Registro;
import br.com.servire.api.checkin.Checkin.Sessao;
import br.com.servire.api.checkin.CheckinDtos.*;
import br.com.servire.api.escala.*;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.portal.PortalService;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Check-in por encontro (F15). A coordenação abre uma sessão com validade curta e mostra o código; cada pessoa
 * escalada, logada com conta vinculada, registra a própria presença uma única vez. O código é aleatório de 256 bits,
 * guardado só como hash, e vale para um encontro. O registro manual de presença da coordenação continua existindo.
 */
@Service
public class CheckinService {
    static final Duration ANTECEDENCIA = Duration.ofHours(1);
    static final Duration LIMITE_APOS_INICIO = Duration.ofHours(8);
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String NAO_ENCONTRADO = "Código inválido ou expirado.";

    @PersistenceContext
    private EntityManager em;
    private final FuncionalidadesPlano plano;
    private final EscalaRepository escalas;
    private final PortalService portal;
    private final AuditLogService audit;

    public CheckinService(FuncionalidadesPlano plano, EscalaRepository escalas, PortalService portal, AuditLogService audit) {
        this.plano = plano;
        this.escalas = escalas;
        this.portal = portal;
        this.audit = audit;
    }

    @Transactional
    public Aberto abrir(UUID eventoId, Integer minutos) {
        plano.exigir("ESCALAS");
        EscalaEvento evento = evento(eventoId);
        Escala escala = escalas.bloquear(evento.getEscala().getId()).orElseThrow(() -> new ResourceNotFoundException("Encontro não encontrado."));
        if (escala.getStatus() != StatusEscala.FINALIZADA) {
            throw new ConflictException("O check-in só abre para escala finalizada.");
        }
        Instant agora = Instant.now();
        Instant inicio = inicio(evento);
        Instant limite = inicio.plus(LIMITE_APOS_INICIO);
        Instant expira = agora.plus(Duration.ofMinutes(minutos == null ? 180 : minutos));
        if (expira.isAfter(limite)) {
            expira = limite;
        }
        if (!expira.isAfter(agora)) {
            throw new ConflictException("Este encontro já terminou para o check-in.");
        }
        revogar(eventoId, agora);
        byte[] bruto = new byte[32];
        ALEATORIO.nextBytes(bruto);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
        Sessao s = new Sessao();
        s.eventoId = eventoId;
        s.tokenHash = hash(token);
        s.criadoPor = usuarioAtual();
        s.criadoEm = agora;
        s.expiraEm = expira;
        em.persist(s);
        em.flush();
        audit.registrar("CHECKIN_ABRIR", "ESCALA_EVENTO", eventoId, List.of("validade"));
        return new Aberto(s.id, token, expira);
    }

    @Transactional
    public void encerrar(UUID eventoId) {
        evento(eventoId);
        if (revogar(eventoId, Instant.now()) > 0) {
            audit.registrar("CHECKIN_ENCERRAR", "ESCALA_EVENTO", eventoId, List.of("revogado"));
        }
    }

    @Transactional(readOnly = true)
    public Estado estado(UUID eventoId) {
        evento(eventoId);
        Instant agora = Instant.now();
        Sessao ativa = em.createQuery("select s from CheckinSessao s where s.eventoId=:e and s.revogadoEm is null and s.expiraEm>:agora order by s.criadoEm desc", Sessao.class)
                .setParameter("e", eventoId).setParameter("agora", agora).setMaxResults(1).getResultStream().findFirst().orElse(null);
        int escalados = em.createQuery("select count(v) from EscalaVaga v where v.evento.id=:e and v.voluntario is not null", Long.class)
                .setParameter("e", eventoId).getSingleResult().intValue();
        int presentes = em.createQuery("select count(v) from EscalaVaga v where v.evento.id=:e and v.presenca=:p", Long.class)
                .setParameter("e", eventoId).setParameter("p", Presenca.PRESENTE).getSingleResult().intValue();
        List<Presente> registros = em.createQuery("select v.voluntario.pessoa.nomeCompleto, v.funcao, r.registradoEm from CheckinRegistro r, EscalaVaga v "
                        + "where r.vagaId=v.id and v.evento.id=:e order by r.registradoEm", Object[].class)
                .setParameter("e", eventoId).setMaxResults(300).getResultList().stream()
                .map(r -> new Presente((String) r[0], String.valueOf(r[1]), (Instant) r[2])).toList();
        return new Estado(ativa != null, ativa == null ? null : ativa.expiraEm, escalados, presentes, registros);
    }

    @Transactional
    public Resultado registrar(String token) {
        plano.exigir("PORTAL_VOLUNTARIO");
        UUID pessoa = portal.pessoaAtual();
        if (pessoa == null) {
            throw new ResourceNotFoundException(NAO_ENCONTRADO);
        }
        Instant agora = Instant.now();
        Sessao sessao = em.createQuery("select s from CheckinSessao s where s.tokenHash=:h", Sessao.class)
                .setParameter("h", hash(token.trim())).getResultStream().findFirst().orElse(null);
        if (sessao == null || !sessao.ativa(agora)) {
            throw new ResourceNotFoundException(NAO_ENCONTRADO);
        }
        EscalaEvento evento = em.find(EscalaEvento.class, sessao.eventoId);
        if (evento == null || evento.isReferencia()) {
            throw new ResourceNotFoundException(NAO_ENCONTRADO);
        }
        Escala escala = escalas.bloquear(evento.getEscala().getId()).orElseThrow(() -> new ResourceNotFoundException(NAO_ENCONTRADO));
        if (escala.getStatus() != StatusEscala.FINALIZADA) {
            throw new ResourceNotFoundException(NAO_ENCONTRADO);
        }
        if (agora.isBefore(inicio(evento).minus(ANTECEDENCIA))) {
            throw new ConflictException("O check-in abre 1 hora antes do encontro.");
        }
        EscalaVaga vaga = em.createQuery("select v from EscalaVaga v where v.evento.id=:e and v.voluntario.id=:p", EscalaVaga.class)
                .setParameter("e", evento.getId()).setParameter("p", pessoa).getResultStream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Você não está escalado neste encontro."));
        Resultado pronto = new Resultado(evento.getCelebracao(), evento.getData(), evento.getHorario(), vaga.getFuncao().name(), true);
        boolean jaRegistrado = em.createQuery("select count(r) from CheckinRegistro r where r.vagaId=:v", Long.class)
                .setParameter("v", vaga.getId()).getSingleResult() > 0;
        if (jaRegistrado) {
            return pronto;
        }
        if (vaga.getPresenca() == Presenca.FALTOU) {
            throw new ConflictException("A coordenação registrou falta para você neste encontro. Fale com a coordenação para corrigir.");
        }
        Registro r = new Registro();
        r.sessaoId = sessao.id;
        r.vagaId = vaga.getId();
        r.usuarioId = usuarioAtual();
        r.registradoEm = agora;
        em.persist(r);
        vaga.setPresenca(Presenca.PRESENTE);
        em.flush();
        audit.registrar("CHECKIN", "ESCALA_VAGA", vaga.getId(), List.of("presenca"));
        return new Resultado(pronto.celebracao(), pronto.data(), pronto.horario(), pronto.funcao(), false);
    }

    private EscalaEvento evento(UUID id) {
        EscalaEvento e = em.find(EscalaEvento.class, id);
        if (e == null) {
            throw new ResourceNotFoundException("Encontro não encontrado.");
        }
        if (e.isReferencia()) {
            throw new BadRequestException("Linha de referência não tem check-in.");
        }
        return e;
    }

    private int revogar(UUID eventoId, Instant agora) {
        return em.createQuery("update CheckinSessao s set s.revogadoEm=:agora where s.eventoId=:e and s.revogadoEm is null")
                .setParameter("agora", agora).setParameter("e", eventoId).executeUpdate();
    }

    private static Instant inicio(EscalaEvento e) {
        return e.getData().atTime(e.getHorario()).atZone(BRASILIA).toInstant();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UUID usuarioAtual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthenticatedUser u ? u.usuarioId() : null;
    }
}
