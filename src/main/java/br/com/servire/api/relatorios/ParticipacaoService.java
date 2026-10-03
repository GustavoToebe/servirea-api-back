package br.com.servire.api.relatorios;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.escala.*;
import br.com.servire.api.relatorios.dto.ParticipacaoDtos.*;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.web.BadRequestException;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Projeção mínima, somente vagas ocupadas de escalas finalizadas; intenção não equivale a presença. */
@Service
public class ParticipacaoService {
  @PersistenceContext private EntityManager em;
  private final AuditLogService audit;

  public ParticipacaoService(AuditLogService audit) {
    this.audit = audit;
  }

  private static final String BASE =
      " from EscalaVaga v join v.evento e join e.escala s join v.voluntario p where"
          + " s.status=:status and e.referencia=false and e.data between :de and :ate";
  private static final String COLUNAS =
      "select"
          + " v.id,s.titulo,e.celebracao,e.data,e.horario,p.pessoa.nomeCompleto,v.funcao,v.presenca,v.resposta";

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Pagina listar(
      LocalDate de,
      LocalDate ate,
      String busca,
      Presenca presenca,
      RespostaParticipacao resposta,
      FuncaoEscala funcao,
      int pagina,
      int tamanho) {
    validar(de, ate, busca);
    if (pagina < 0 || pagina > 100000 || tamanho < 1 || tamanho > 100)
      throw new BadRequestException("Paginação inválida.");
    var f = filtros(busca, presenca, resposta, funcao);
    var q =
        em.createQuery(
            COLUNAS + BASE + f + " order by e.data desc,e.horario desc,v.id", Object[].class);
    parametros(q, de, ate, busca, presenca, resposta, funcao);
    var c = em.createQuery("select count(v)" + BASE + f, Long.class);
    parametros(c, de, ate, busca, presenca, resposta, funcao);
    return new Pagina(
        q.setFirstResult(pagina * tamanho).setMaxResults(tamanho).getResultList().stream()
            .map(this::linha)
            .toList(),
        c.getSingleResult(),
        pagina,
        tamanho);
  }

  @Transactional
  public byte[] exportar(
      LocalDate de,
      LocalDate ate,
      String busca,
      Presenca presenca,
      RespostaParticipacao resposta,
      FuncaoEscala funcao) {
    validar(de, ate, busca);
    var q =
        em.createQuery(
            COLUNAS
                + BASE
                + filtros(busca, presenca, resposta, funcao)
                + " order by e.data desc,e.horario desc,v.id",
            Object[].class);
    parametros(q, de, ate, busca, presenca, resposta, funcao);
    var rows = q.setMaxResults(5001).getResultList();
    if (rows.size() > 5000)
      throw new BadRequestException(
          "A exportação aceita até 5.000 linhas. Reduza o período ou os filtros.");
    StringBuilder csv =
        new StringBuilder(
            "\uFEFFEscala;Celebração;Data;Horário;Pessoa;Função;Presença;Participação\r\n");
    for (var r : rows) {
      var l = linha(r);
      csv.append(
              String.join(
                  ";",
                  celula(l.escala()),
                  celula(l.celebracao()),
                  celula(l.data().toString()),
                  celula(l.horario().toString()),
                  celula(l.pessoa()),
                  celula(l.funcao().name()),
                  celula(l.presenca().name()),
                  celula(l.resposta().name())))
          .append("\r\n");
    }
    audit.registrar(
        "EXPORTAR",
        "RELATORIO_PARTICIPACAO",
        br.com.servire.api.tenant.TenantContext.get(),
        List.of("linhas=" + rows.size(), "de=" + de, "ate=" + ate));
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** Aspas/CRLF/separadores são escapados; neutraliza fórmulas também após espaços e controles. */
  static String celula(String valor) {
    String s = valor == null ? "" : valor;
    int i = 0;
    while (i < s.length()
        && (Character.isWhitespace(s.charAt(i))
            || Character.isISOControl(s.charAt(i))
            || s.charAt(i) == '\uFEFF')) i++;
    if (i < s.length() && "=+-@".indexOf(s.charAt(i)) >= 0) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private void validar(LocalDate de, LocalDate ate, String busca) {
    if (de == null
        || ate == null
        || ate.isBefore(de)
        || ChronoUnit.DAYS.between(de, ate) > 366
        || busca != null && busca.length() > 160)
      throw new BadRequestException(
          "Informe período de até 366 dias e busca de até 160 caracteres.");
  }

  private String filtros(String busca, Presenca p, RespostaParticipacao r, FuncaoEscala f) {
    return (busca == null || busca.isBlank()
            ? ""
            : " and lower(p.pessoa.nomeCompleto) like :busca escape '\\'")
        + (p == null ? "" : " and v.presenca=:presenca")
        + (r == null ? "" : " and v.resposta=:resposta")
        + (f == null ? "" : " and v.funcao=:funcao");
  }

  private void parametros(
      Query q,
      LocalDate de,
      LocalDate ate,
      String busca,
      Presenca p,
      RespostaParticipacao r,
      FuncaoEscala f) {
    q.setParameter("status", StatusEscala.FINALIZADA)
        .setParameter("de", de)
        .setParameter("ate", ate);
    if (busca != null && !busca.isBlank())
      q.setParameter(
          "busca",
          "%"
              + busca
                  .trim()
                  .toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%");
    if (p != null) q.setParameter("presenca", p);
    if (r != null) q.setParameter("resposta", r);
    if (f != null) q.setParameter("funcao", f);
  }

  private Linha linha(Object[] r) {
    return new Linha(
        (UUID) r[0],
        (String) r[1],
        (String) r[2],
        (LocalDate) r[3],
        (LocalTime) r[4],
        (String) r[5],
        (FuncaoEscala) r[6],
        (Presenca) r[7],
        (RespostaParticipacao) r[8]);
  }
}
