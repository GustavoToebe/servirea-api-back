package br.com.servire.api.tarefas;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tarefas.dto.TarefaDtos.*;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atribuição não altera permissões: o diretório devolve somente id/nome do vínculo desta paróquia. */
@Service
public class TarefaService {
  private final TarefaRepository repo;
  private final AuditLogService audit;
  private final FuncionalidadesPlano plano;
  @PersistenceContext private EntityManager em;

  public TarefaService(TarefaRepository repo, AuditLogService audit, FuncionalidadesPlano plano) {
    this.repo = repo;
    this.audit = audit;
    this.plano = plano;
  }

  @Transactional(readOnly = true)
  public Pagina listar(
      String busca,
      Tarefa.Status status,
      UUID responsavel,
      boolean minhas,
      int pagina,
      int tamanho) {
    if (pagina < 0
        || pagina > 100000
        || tamanho < 1
        || tamanho > 100
        || busca != null && busca.length() > 160) throw new BadRequestException("Filtro inválido.");
    if (minhas) {
      if (responsavel != null)
        throw new BadRequestException("Escolha minhas tarefas ou um responsável.");
      var a = SecurityContextHolder.getContext().getAuthentication();
      if (a == null
          || !(a.getPrincipal() instanceof AuthenticatedUser u)
          || u.suporte()
          || !Objects.equals(u.tenantId(), TenantContext.get()))
        throw new BadRequestException("Minhas tarefas exige conta pessoal.");
      responsavel = u.usuarioId();
    }
    Specification<Tarefa> filtro = (r, q, b) -> b.conjunction();
    if (status != null) filtro = filtro.and((r, q, b) -> b.equal(r.get("status"), status));
    if (responsavel != null) {
      UUID id = responsavel;
      filtro = filtro.and((r, q, b) -> b.equal(r.get("responsavelUsuarioId"), id));
    }
    if (busca != null && !busca.isBlank()) {
      String texto = "%" + literal(busca) + "%";
      filtro = filtro.and((r, q, b) -> b.like(b.lower(r.get("titulo")), texto, '\\'));
    }
    var p =
        repo.findAll(
            filtro,
            PageRequest.of(
                pagina, tamanho, Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"))));
    var nomes =
        nomes(
            p.getContent().stream()
                .map(e -> e.responsavelUsuarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return new Pagina(
        p.getContent().stream().map(e -> resposta(e, nomes)).toList(),
        p.getTotalElements(),
        pagina,
        tamanho);
  }

  @Transactional(readOnly = true)
  public List<Responsavel> responsaveis(String busca) {
    if (busca != null && busca.length() > 160) throw new BadRequestException("Busca muito longa.");
    return em
        .createQuery(
            "select v.usuario.id,v.usuario.nome from UsuarioTenant v where v.tenant.id=:tenant and"
                + " v.status=:ativo and lower(v.usuario.nome) like :busca escape '\\' order by"
                + " lower(v.usuario.nome),v.usuario.id",
            Object[].class)
        .setParameter("tenant", TenantContext.get())
        .setParameter("ativo", UsuarioTenant.Status.ATIVO)
        .setParameter("busca", "%" + literal(busca == null ? "" : busca) + "%")
        .setMaxResults(30)
        .getResultList()
        .stream()
        .map(r -> new Responsavel((UUID) r[0], (String) r[1]))
        .toList();
  }

  @Transactional(readOnly = true)
  public Resposta buscar(UUID id) {
    var e = carregar(id);
    return resposta(
        e, nomes(e.responsavelUsuarioId == null ? List.of() : List.of(e.responsavelUsuarioId)));
  }

  @Transactional
  public Resposta salvar(UUID id, Salvar req) {
    plano.exigir("TAREFAS");
    var e = id == null ? new Tarefa() : carregar(id);
    if (id != null && (req.versao() == null || req.versao() != e.versao))
      throw new ConflictException("O registro mudou. Atualize a página antes de salvar.");
    if (req.responsavelUsuarioId() != null
        && !Objects.equals(req.responsavelUsuarioId(), e.responsavelUsuarioId)) {
      Long n =
          em.createQuery(
                  "select count(v) from UsuarioTenant v where v.usuario.id=:id and"
                      + " v.tenant.id=:tenant and v.status=:ativo",
                  Long.class)
              .setParameter("id", req.responsavelUsuarioId())
              .setParameter("tenant", TenantContext.get())
              .setParameter("ativo", UsuarioTenant.Status.ATIVO)
              .getSingleResult();
      if (n != 1)
        throw new BadRequestException("Responsável precisa de vínculo ativo nesta paróquia.");
    }
    if (id == null) e.criadoEm = Instant.now();
    e.responsavelUsuarioId = req.responsavelUsuarioId();
    e.titulo = req.titulo().trim();
    e.descricao = req.descricao().trim();
    e.status = req.status();
    e.prazo = req.prazo();
    e.equipe = req.equipe() == null || req.equipe().isBlank() ? null : req.equipe().trim();
    e.atualizadoEm = Instant.now();
    repo.saveAndFlush(e);
    audit.registrar(
        id == null ? "CRIAR" : "ALTERAR",
        "TAREFAS",
        e.id,
        List.of("titulo", "descricao", "status", "prazo", "equipe", "responsavelUsuarioId"));
    return resposta(
        e, nomes(e.responsavelUsuarioId == null ? List.of() : List.of(e.responsavelUsuarioId)));
  }

  private Map<UUID, String> nomes(List<UUID> ids) {
    var m = new HashMap<UUID, String>();
    if (!ids.isEmpty())
      for (var r :
          em.createQuery(
                  "select v.usuario.id,v.usuario.nome from UsuarioTenant v where"
                      + " v.tenant.id=:tenant and v.usuario.id in :ids",
                  Object[].class)
              .setParameter("tenant", TenantContext.get())
              .setParameter("ids", ids)
              .getResultList()) m.put((UUID) r[0], (String) r[1]);
    return m;
  }

  private Resposta resposta(Tarefa e, Map<UUID, String> nomes) {
    return new Resposta(
        e.id,
        e.titulo,
        e.descricao,
        e.status,
        e.prazo,
        e.equipe,
        e.versao,
        e.criadoEm,
        e.atualizadoEm,
        e.responsavelUsuarioId,
        nomes.get(e.responsavelUsuarioId));
  }

  private static String literal(String s) {
    return s.trim()
        .toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_");
  }

  private Tarefa carregar(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Registro não encontrado."));
  }
}
