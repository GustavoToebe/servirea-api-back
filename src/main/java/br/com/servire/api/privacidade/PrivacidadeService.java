package br.com.servire.api.privacidade;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.comunicacao.StatusComunicado;
import br.com.servire.api.comunicacao.StatusEnvio;
import br.com.servire.api.escala.EscalaVaga;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaRelacao;
import br.com.servire.api.privacidade.Privacidade.Consentimento;
import br.com.servire.api.privacidade.Privacidade.Execucao;
import br.com.servire.api.privacidade.Privacidade.Politica;
import br.com.servire.api.privacidade.PrivacidadeDtos.*;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

/**
 * Exportação autorizada dos dados de uma pessoa e política de retenção dos comunicados (F09). A exportação traz
 * só dados da própria pessoa (nunca o corpo das mensagens nem contatos de terceiros) e cuidados apenas para quem
 * tem permissão. A retenção anonimiza destinatários de comunicados concluídos; é manual e opcional.
 */
@Service
public class PrivacidadeService {
    static final String MARCADOR = "[removido pela política de retenção]";
    private static final int LIMITE_LINHAS = 500;

    @PersistenceContext
    private EntityManager em;
    private final AuditLogService audit;

    public PrivacidadeService(AuditLogService audit) {
        this.audit = audit;
    }

    // ---- Exportação

    @Transactional
    public Exportacao exportar(UUID pessoaId) {
        Pessoa p = em.find(Pessoa.class, pessoaId);
        if (p == null) {
            throw new ResourceNotFoundException("Pessoa não encontrada.");
        }
        Hibernate.initialize(p.getEmails());
        Hibernate.initialize(p.getTelefones());
        List<ContatoExport> emails = p.getEmails().stream().map(e -> new ContatoExport(e.getTipo(), e.getEmail(), e.isPrincipal())).toList();
        List<ContatoExport> telefones = p.getTelefones().stream().map(t -> new ContatoExport(t.getTipo(), t.getNumero(), t.isPrincipal())).toList();
        List<RelacaoExport> responsaveis = p.getResponsaveis().stream()
                .map(r -> new RelacaoExport(r.getResponsavel().getNomeCompleto(), r.getParentesco(), r.isPrincipal())).toList();
        List<RelacaoExport> dependentes = p.getDependentes().stream()
                .map((PessoaRelacao r) -> new RelacaoExport(r.getVoluntario().getNomeCompleto(), r.getParentescoInverso(), r.isPrincipal())).toList();
        Voluntario v = p.getVoluntario();
        VoluntarioExport voluntario = v == null ? null : new VoluntarioExport(v.getTipo() == null ? null : v.getTipo().name(), v.isAtivo(),
                v.getFuncoesHabilitadas() == null ? List.of() : Arrays.stream(v.getFuncoesHabilitadas()).map(FuncaoEscala::name).toList(),
                v.isAutorizaWhatsapp());
        List<ParticipacaoExport> participacoes = v == null ? List.of()
                : em.createQuery("select s from EscalaVaga s join fetch s.evento e where s.voluntario.id=:id and e.referencia=false order by e.data desc, e.horario desc", EscalaVaga.class)
                        .setParameter("id", pessoaId).setMaxResults(LIMITE_LINHAS).getResultList().stream()
                        .map(s -> new ParticipacaoExport(s.getEvento().getData(), s.getEvento().getHorario(), s.getEvento().getCelebracao(),
                                s.getFuncao().name(), s.getPresenca().name(), s.getResposta().name())).toList();
        List<ConsentimentoItem> consentimentos = em.createQuery("select c from Consentimento c where c.pessoaId=:id order by c.registradoEm desc", Consentimento.class)
                .setParameter("id", pessoaId).setMaxResults(LIMITE_LINHAS).getResultList().stream()
                .map(c -> new ConsentimentoItem(c.tipo, c.concedido, c.fonte, c.registradoEm)).toList();
        List<ComunicacaoExport> comunicacoes = em.createQuery("select c.canal, c.layoutNome, d.assunto, d.status, d.enviadoEm from ComunicadoDestinatario d, Comunicado c "
                        + "where d.comunicadoId=c.id and d.pessoaId=:id order by c.createdAt desc", Object[].class)
                .setParameter("id", pessoaId).setMaxResults(LIMITE_LINHAS).getResultList().stream()
                .map(r -> new ComunicacaoExport(String.valueOf(r[0]), (String) r[1], (String) r[2], String.valueOf(r[3]), (Instant) r[4])).toList();
        CuidadosExport cuidados = podeVerCuidados()
                ? new CuidadosExport(p.getCondicoes() == null ? List.of() : Arrays.stream(p.getCondicoes()).map(Enum::name).toList(),
                        p.getNivelSuporteTea(), p.getCondicaoOutra(), p.getCuidados())
                : null;
        audit.registrar("EXPORTAR_DADOS", "PESSOA", pessoaId, List.of("dados pessoais"));
        return new Exportacao(Instant.now(), p.getId(), p.getSequencial(), p.getNomeCompleto(),
                p.getPapeis().stream().map(Enum::name).toList(), p.getDataNascimento(), p.getSexo(), p.getCpf(), p.getRg(), endereco(p),
                p.getObservacoes(), emails, telefones, responsaveis, dependentes, voluntario, participacoes, consentimentos,
                comunicacoes, cuidados);
    }

    private static String endereco(Pessoa p) {
        String rua = String.join(", ", Stream.of(p.getLogradouro(), p.getNumero(), p.getComplemento(), p.getBairro())
                .filter(s -> s != null && !s.isBlank()).toList());
        String cidade = String.join("/", Stream.of(p.getCidade(), p.getUf()).filter(s -> s != null && !s.isBlank()).toList());
        return String.join(" - ", Stream.of(rua, cidade, p.getCep()).filter(s -> s != null && !s.isBlank()).toList());
    }

    private static boolean podeVerCuidados() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(x -> "PERM_PESSOA_CUIDADOS_LER".equals(x.getAuthority()));
    }

    // ---- Retenção

    @Transactional(readOnly = true)
    public Retencao retencao() {
        Politica pol = politica();
        long elegiveis = pol == null || pol.comunicadosDias == null ? 0 : elegiveis(corte(pol.comunicadosDias));
        List<ExecucaoItem> execucoes = em.createQuery("select e from Execucao e order by e.executadoEm desc", Execucao.class)
                .setMaxResults(10).getResultList().stream().map(e -> new ExecucaoItem(e.executadoEm, e.corte, e.comunicadosAnonimizados)).toList();
        return new Retencao(pol == null ? null : pol.comunicadosDias, pol == null ? 0 : pol.versao, elegiveis, execucoes);
    }

    @Transactional
    public Retencao atualizar(AtualizarRetencao req) {
        Politica pol = politica();
        long atual = pol == null ? 0 : pol.versao;
        if (req.versao() != atual) {
            throw new ConflictException("A política mudou. Atualize antes de salvar.");
        }
        if (pol == null) {
            pol = new Politica();
            pol.versao = 1;
        }
        pol.comunicadosDias = req.comunicadosDias();
        em.persist(pol);
        em.flush();
        audit.registrar("CONFIGURAR", "RETENCAO", pol.id, List.of("comunicadosDias"));
        return retencao();
    }

    @Transactional
    public ResultadoRetencao executar(ExecutarRetencao req) {
        Politica pol = politica();
        if (pol == null || pol.comunicadosDias == null) {
            throw new BadRequestException("Defina o prazo de retenção antes de executar.");
        }
        if (req.versao() != pol.versao) {
            throw new ConflictException("A política mudou. Atualize antes de executar.");
        }
        Instant corte = corte(pol.comunicadosDias);
        int n = em.createQuery("update ComunicadoDestinatario d set d.nome=:marca, d.destino=:marca, d.conteudo=:marca, d.assunto=null, d.erro=null, d.pessoaId=null "
                        + "where d.status in :finais and d.conteudo<>:marca and d.comunicadoId in "
                        + "(select c.id from Comunicado c where c.createdAt<:corte and c.status=:concluido)")
                .setParameter("marca", MARCADOR).setParameter("finais", List.of(StatusEnvio.ENVIADO, StatusEnvio.FALHA))
                .setParameter("corte", corte).setParameter("concluido", StatusComunicado.CONCLUIDO).executeUpdate();
        Execucao e = new Execucao();
        e.executadoEm = Instant.now();
        e.executadoPor = usuarioAtual();
        e.corte = corte;
        e.comunicadosAnonimizados = n;
        em.persist(e);
        em.flush();
        audit.registrar("RETENCAO", "COMUNICADO", e.id, List.of("anonimizados"));
        return new ResultadoRetencao(corte, n);
    }

    private long elegiveis(Instant corte) {
        return em.createQuery("select count(d) from ComunicadoDestinatario d where d.status in :finais and d.conteudo<>:marca and d.comunicadoId in "
                        + "(select c.id from Comunicado c where c.createdAt<:corte and c.status=:concluido)", Long.class)
                .setParameter("marca", MARCADOR).setParameter("finais", List.of(StatusEnvio.ENVIADO, StatusEnvio.FALHA))
                .setParameter("corte", corte).setParameter("concluido", StatusComunicado.CONCLUIDO).getSingleResult();
    }

    private static Instant corte(int dias) {
        return Instant.now().minus(dias, ChronoUnit.DAYS);
    }

    private Politica politica() {
        return em.createQuery("select p from Politica p", Politica.class).getResultStream().findFirst().orElse(null);
    }

    private static UUID usuarioAtual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthenticatedUser u ? u.usuarioId() : null;
    }
}
