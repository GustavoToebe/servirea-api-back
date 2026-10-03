package br.com.servire.api.relatorios;

import br.com.servire.api.escala.*;
import br.com.servire.api.web.BadRequestException;
import jakarta.persistence.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Contagens privadas, ordem alfabética; intenção e presença não são pontuação. */
@Service
public class IndicadoresService {
  @PersistenceContext private EntityManager em;
  private static final String BASE =
      " from EscalaVaga v join v.evento e join e.escala s join v.voluntario p where"
          + " s.status=:finalizada and e.referencia=false and e.data between :de and :ate and"
          + " lower(p.pessoa.nomeCompleto) like :busca escape '\\'";
  private static final String CONTAGENS =
      "count(v),sum(case when v.presenca=:presente then 1 else 0 end),sum(case when"
          + " v.presenca=:faltou then 1 else 0 end),sum(case when v.presenca=:pendente then 1 else"
          + " 0 end),sum(case when v.resposta=:confirmada then 1 else 0 end),sum(case when"
          + " v.resposta=:recusada then 1 else 0 end)";

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Pagina consultar(LocalDate de, LocalDate ate, String busca, int pagina) {
    if (de == null
        || ate == null
        || ate.isBefore(de)
        || ChronoUnit.DAYS.between(de, ate) > 366
        || busca != null && busca.length() > 160
        || pagina < 0
        || pagina > 100000)
      throw new BadRequestException(
          "Informe período até 366 dias, busca até 160 caracteres e página válida.");
    var q =
        em.createQuery(
            "select p.id,p.pessoa.nomeCompleto,"
                + CONTAGENS
                + BASE
                + " group by p.id,p.pessoa.nomeCompleto order by lower(p.pessoa.nomeCompleto),p.id",
            Object[].class);
    parametros(q, de, ate, busca);
    var n = em.createQuery("select count(distinct p.id)" + BASE, Long.class);
    n.setParameter("finalizada", StatusEscala.FINALIZADA)
        .setParameter("de", de)
        .setParameter("ate", ate)
        .setParameter("busca", literal(busca));
    var resumo = em.createQuery("select " + CONTAGENS + BASE, Object[].class);
    parametros(resumo, de, ate, busca);
    return new Pagina(
        q.setFirstResult(pagina * 30).setMaxResults(30).getResultList().stream()
            .map(r -> new Pessoa((UUID) r[0], (String) r[1], contagens(r, 2)))
            .toList(),
        n.getSingleResult(),
        pagina,
        30,
        contagens(resumo.getSingleResult(), 0));
  }

  private void parametros(Query q, LocalDate de, LocalDate ate, String busca) {
    q.setParameter("finalizada", StatusEscala.FINALIZADA)
        .setParameter("de", de)
        .setParameter("ate", ate)
        .setParameter("busca", literal(busca))
        .setParameter("presente", Presenca.PRESENTE)
        .setParameter("faltou", Presenca.FALTOU)
        .setParameter("pendente", Presenca.PENDENTE)
        .setParameter("confirmada", RespostaParticipacao.CONFIRMADA)
        .setParameter("recusada", RespostaParticipacao.RECUSADA);
  }

  private String literal(String b) {
    return "%"
        + (b == null ? "" : b.trim().toLowerCase(Locale.ROOT))
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        + "%";
  }

  private Contagens contagens(Object[] r, int i) {
    return new Contagens(
        numero(r[i]),
        numero(r[i + 1]),
        numero(r[i + 2]),
        numero(r[i + 3]),
        numero(r[i + 4]),
        numero(r[i + 5]));
  }

  private long numero(Object v) {
    return v == null ? 0 : ((Number) v).longValue();
  }

  public record Contagens(
      long alocacoes,
      long presentes,
      long faltas,
      long presencasPendentes,
      long confirmacoes,
      long recusas) {}

  public record Pessoa(UUID pessoaId, String nome, Contagens contagens) {}

  public record Pagina(List<Pessoa> itens, long total, int pagina, int tamanho, Contagens resumo) {}
}
