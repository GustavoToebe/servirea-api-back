package br.com.servire.api.portal;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.pessoa.PessoaRelacao;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Parentesco é cadastro; acesso ao portal depende de autorização explícita revogável. */
@Service
public class AcessosDependentesService {
  @PersistenceContext private EntityManager em;
  private final PortalService portal;
  private final TenantRepository tenants;
  private final AuditLogService audit;
  private final FuncionalidadesPlano plano;

  public AcessosDependentesService(
      PortalService portal,
      TenantRepository tenants,
      AuditLogService audit,
      FuncionalidadesPlano plano) {
    this.portal = portal;
    this.tenants = tenants;
    this.audit = audit;
    this.plano = plano;
  }

  @Transactional(readOnly = true)
  public List<Dependente> dependentes(int pagina) {
    plano.exigir("PORTAL_VOLUNTARIO");
    validarPagina(pagina);
    UUID atual = portal.pessoaAtual();
    if (atual == null) return List.of();
    return em
        .createQuery(
            "select r.voluntario.id,r.voluntario.nomeCompleto,r.portalResposta from PessoaRelacao r"
                + " where r.responsavel.id=:id and r.portalConsulta=true order by"
                + " r.voluntario.nomeCompleto,r.voluntario.id",
            Object[].class)
        .setParameter("id", atual)
        .setFirstResult(pagina * 30)
        .setMaxResults(30)
        .getResultList()
        .stream()
        .map(v -> new Dependente((UUID) v[0], (String) v[1], (Boolean) v[2]))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Autorizacao> listar(UUID responsavel, int pagina) {
    validarPagina(pagina);
    return em
        .createQuery(
            "select r from PessoaRelacao r join fetch r.voluntario where r.responsavel.id=:id order"
                + " by r.voluntario.nomeCompleto,r.id",
            PessoaRelacao.class)
        .setParameter("id", responsavel)
        .setFirstResult(pagina * 30)
        .setMaxResults(30)
        .getResultList()
        .stream()
        .map(
            r ->
                new Autorizacao(
                    r.getId(),
                    r.getVoluntario().getId(),
                    r.getVoluntario().getNomeCompleto(),
                    r.isPortalConsulta(),
                    r.isPortalResposta(),
                    r.getPortalVersao()))
        .toList();
  }

  @Transactional
  public void autorizar(
      UUID responsavel, UUID dependente, boolean consulta, boolean resposta, long versao) {
    plano.exigir("PORTAL_VOLUNTARIO");
    if (resposta && !consulta)
      throw new BadRequestException("Responder exige autorização para consultar.");
    tenants.bloquearParaCotas(TenantContext.get()).orElseThrow();
    var r =
        em.createQuery(
                "select r from PessoaRelacao r where r.responsavel.id=:r and r.voluntario.id=:d",
                PessoaRelacao.class)
            .setParameter("r", responsavel)
            .setParameter("d", dependente)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Relação não encontrada."));
    if (r.getPortalVersao() != versao)
      throw new ConflictException("A autorização mudou. Atualize antes de salvar.");
    r.autorizarPortal(consulta, resposta);
    em.flush();
    audit.registrar(
        "PORTAL_DEPENDENTE_AUTORIZADO",
        "PESSOA_RELACAO",
        r.getId(),
        List.of("portalConsulta", "portalResposta"));
  }

  private static void validarPagina(int pagina) {
    if (pagina < 0 || pagina > 100000) throw new BadRequestException("Página inválida.");
  }

  public record Dependente(UUID pessoaId, String nome, boolean podeResponder) {}

  public record Autorizacao(
      UUID relacaoId,
      UUID pessoaId,
      String nome,
      boolean consulta,
      boolean resposta,
      long versao) {}
}
