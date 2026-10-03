package br.com.servire.api.estoque;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.estoque.dto.EstoqueDtos.*;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saldo alterado somente por movimentos imutáveis; trava raiz, versão e chave de repetição. */
@Service
public class EstoqueService {
  @PersistenceContext private EntityManager em;
  private final TenantRepository tenants;
  private final AuditLogService audit;

  public EstoqueService(TenantRepository tenants, AuditLogService audit) {
    this.tenants = tenants;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Pagina<Item> listar(String busca, int pagina) {
    validarBusca(busca, pagina);
    var q =
        em.createQuery(
                "select e from EstoqueItem e where lower(e.nome) like :b escape '\\' or"
                    + " lower(e.codigo) like :b escape '\\' order by lower(e.nome),e.id",
                EstoqueItem.class)
            .setParameter("b", literal(busca));
    long total =
        em.createQuery(
                "select count(e) from EstoqueItem e where lower(e.nome) like :b escape '\\' or"
                    + " lower(e.codigo) like :b escape '\\'",
                Long.class)
            .setParameter("b", literal(busca))
            .getSingleResult();
    return new Pagina<>(
        itens(q.setFirstResult(pagina * 30).setMaxResults(30).getResultList()), total, pagina, 30);
  }

  @Transactional(readOnly = true)
  public Item buscar(UUID id) {
    return item(exigir(id));
  }

  @Transactional(readOnly = true)
  public Pagina<Movimento> historico(UUID id, int pagina) {
    validarBusca("", pagina);
    exigir(id);
    var q =
        em.createQuery(
                "select m from EstoqueMovimento m where m.itemId=:id order by m.registradoEm"
                    + " desc,m.id desc",
                EstoqueMovimento.class)
            .setParameter("id", id);
    long total =
        em.createQuery("select count(m) from EstoqueMovimento m where m.itemId=:id", Long.class)
            .setParameter("id", id)
            .getSingleResult();
    return new Pagina<>(
        movimentos(q.setFirstResult(pagina * 30).setMaxResults(30).getResultList()),
        total,
        pagina,
        30);
  }

  @Transactional(readOnly = true)
  public List<Responsavel> responsaveis(String busca) {
    validarBusca(busca, 0);
    return em
        .createQuery(
            "select v.usuario.id,v.usuario.nome from UsuarioTenant v where v.tenant.id=:t and"
                + " v.status=:ativo and lower(v.usuario.nome) like :b escape '\\' order by"
                + " lower(v.usuario.nome),v.usuario.id",
            Object[].class)
        .setParameter("t", TenantContext.get())
        .setParameter("ativo", UsuarioTenant.Status.ATIVO)
        .setParameter("b", literal(busca))
        .setMaxResults(30)
        .getResultList()
        .stream()
        .map(r -> new Responsavel((UUID) r[0], (String) r[1]))
        .toList();
  }

  @Transactional
  public Item salvar(UUID id, Salvar r) {
    travar();
    var e = id == null ? new EstoqueItem() : exigir(id);
    conferir(id == null ? 0 : e.versao, r.versao());
    if (!Set.of("CONSUMIVEL", "PATRIMONIO").contains(r.tipo()))
      throw new BadRequestException("Tipo de item inválido.");
    responsavel(r.responsavelUsuarioId());
    String codigo = r.codigo().trim().toUpperCase(Locale.ROOT);
    var duplicados =
        em.createQuery("select e.id from EstoqueItem e where e.codigo=:codigo", UUID.class)
            .setParameter("codigo", codigo)
            .setMaxResults(1)
            .getResultList();
    if (!duplicados.isEmpty() && !duplicados.getFirst().equals(id))
      throw new ConflictException("Código já utilizado nesta paróquia.");
    if (id != null
        && (!e.tipo.equals(r.tipo()) || !e.unidade.equals(r.unidade().trim()))
        && em.createQuery("select count(m) from EstoqueMovimento m where m.itemId=:id", Long.class)
                .setParameter("id", id)
                .getSingleResult()
            > 0) throw new BadRequestException("Tipo e unidade não mudam após movimentação.");
    e.nome = r.nome().trim();
    e.codigo = codigo;
    e.tipo = r.tipo();
    e.unidade = r.unidade().trim();
    e.local = r.local();
    e.responsavelUsuarioId = r.responsavelUsuarioId();
    e.ativo = r.ativo();
    e.atualizadoEm = Instant.now();
    if (id == null) {
      e.versao = 1;
      em.persist(e);
    }
    em.flush();
    audit.registrar(
        id == null ? "CRIAR" : "ALTERAR",
        "ESTOQUE_ITEM",
        e.id,
        List.of("nome", "codigo", "tipo", "unidade", "local", "responsavel", "ativo"));
    return item(e);
  }

  @Transactional
  public Movimento movimentar(UUID id, Movimentar r) {
    travar();
    var e = exigir(id);
    var repetido =
        em.createQuery(
                "select m from EstoqueMovimento m where m.itemId=:id and m.chave=:chave",
                EstoqueMovimento.class)
            .setParameter("id", id)
            .setParameter("chave", r.chave())
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (repetido != null) {
      if (!repetido.tipo.equals(r.tipo())
          || repetido.quantidade.compareTo(r.quantidade()) != 0
          || !repetido.motivo.equals(r.motivo().trim())
          || !Objects.equals(repetido.responsavelUsuarioId, r.responsavelUsuarioId()))
        throw new ConflictException("Chave já usada com outro movimento.");
      return movimento(repetido);
    }
    conferir(e.versao, r.versao());
    if (!e.ativo) throw new BadRequestException("Item inativo não recebe movimentos.");
    if (!Set.of("ENTRADA", "SAIDA", "AJUSTE").contains(r.tipo())
        || !r.tipo().equals("AJUSTE") && r.quantidade().signum() <= 0)
      throw new BadRequestException("Tipo ou quantidade inválida.");
    if (e.tipo.equals("PATRIMONIO") && r.quantidade().stripTrailingZeros().scale() > 0)
      throw new BadRequestException("Patrimônio usa quantidade inteira.");
    responsavel(r.responsavelUsuarioId());
    BigDecimal saldo =
        switch (r.tipo()) {
          case "ENTRADA" -> e.saldo.add(r.quantidade());
          case "SAIDA" -> e.saldo.subtract(r.quantidade());
          default -> r.quantidade();
        };
    if (saldo.signum() < 0 || saldo.compareTo(new BigDecimal("999999999.999")) > 0)
      throw new BadRequestException("Saldo insuficiente ou fora do limite.");
    var m = new EstoqueMovimento();
    m.itemId = id;
    m.chave = r.chave();
    m.tipo = r.tipo();
    m.quantidade = r.quantidade();
    m.saldoAntes = e.saldo;
    m.saldoDepois = saldo;
    m.motivo = r.motivo().trim();
    m.responsavelUsuarioId = r.responsavelUsuarioId();
    var a = SecurityContextHolder.getContext().getAuthentication();
    m.registradoPor =
        a != null && a.getPrincipal() instanceof AuthenticatedUser u && !u.suporte()
            ? u.usuarioId()
            : null;
    m.registradoEm = Instant.now();
    e.saldo = saldo;
    e.atualizadoEm = Instant.now();
    em.persist(m);
    em.flush();
    audit.registrar("MOVIMENTAR", "ESTOQUE_ITEM", id, List.of("saldo", "tipo=" + r.tipo()));
    return movimento(m);
  }

  private Map<UUID, String> nomes(Collection<UUID> ids) {
    var distintos = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (distintos.isEmpty()) return Map.of();
    var nomes = new HashMap<UUID, String>();
    em.createQuery(
            "select v.usuario.id,v.usuario.nome from UsuarioTenant v where v.tenant.id=:t and"
                + " v.usuario.id in :ids",
            Object[].class)
        .setParameter("t", TenantContext.get())
        .setParameter("ids", distintos)
        .getResultList()
        .forEach(r -> nomes.put((UUID) r[0], (String) r[1]));
    return nomes;
  }

  private List<Item> itens(List<EstoqueItem> rows) {
    var n = nomes(rows.stream().map(e -> e.responsavelUsuarioId).toList());
    return rows.stream().map(e -> item(e, n)).toList();
  }

  private Item item(EstoqueItem e) {
    return item(e, nomes(Arrays.asList(e.responsavelUsuarioId)));
  }

  private Item item(EstoqueItem e, Map<UUID, String> n) {
    return new Item(
        e.id,
        e.versao,
        e.nome,
        e.codigo,
        e.tipo,
        e.unidade,
        e.local,
        e.responsavelUsuarioId,
        e.ativo,
        e.saldo,
        e.responsavelUsuarioId == null ? null : n.get(e.responsavelUsuarioId));
  }

  private List<Movimento> movimentos(List<EstoqueMovimento> rows) {
    var ids = new ArrayList<UUID>();
    for (var m : rows) {
      ids.add(m.responsavelUsuarioId);
      ids.add(m.registradoPor);
    }
    var n = nomes(ids);
    return rows.stream().map(m -> movimento(m, n)).toList();
  }

  private Movimento movimento(EstoqueMovimento m) {
    return movimento(m, nomes(Arrays.asList(m.responsavelUsuarioId, m.registradoPor)));
  }

  private Movimento movimento(EstoqueMovimento m, Map<UUID, String> n) {
    return new Movimento(
        m.id,
        m.itemId,
        m.chave,
        m.tipo,
        m.quantidade,
        m.saldoAntes,
        m.saldoDepois,
        m.motivo,
        m.responsavelUsuarioId,
        m.registradoPor,
        m.registradoEm,
        m.responsavelUsuarioId == null ? null : n.get(m.responsavelUsuarioId),
        m.registradoPor == null ? null : n.get(m.registradoPor));
  }

  private EstoqueItem exigir(UUID id) {
    var e = em.find(EstoqueItem.class, id);
    if (e == null) throw new ResourceNotFoundException("Item não encontrado.");
    return e;
  }

  private void responsavel(UUID id) {
    if (id != null
        && em.createQuery(
                    "select count(v) from UsuarioTenant v where v.tenant.id=:t and v.usuario.id=:u"
                        + " and v.status=:s",
                    Long.class)
                .setParameter("t", TenantContext.get())
                .setParameter("u", id)
                .setParameter("s", UsuarioTenant.Status.ATIVO)
                .getSingleResult()
            != 1)
      throw new BadRequestException("Responsável precisa de vínculo ativo nesta paróquia.");
  }

  private void travar() {
    tenants.bloquearParaCotas(TenantContext.get()).orElseThrow();
  }

  private void conferir(long atual, long recebida) {
    if (atual != recebida)
      throw new ConflictException("O item mudou. Atualize antes de salvar ou movimentar.");
  }

  private void validarBusca(String b, int p) {
    if (b != null && b.length() > 160 || p < 0 || p > 100000)
      throw new BadRequestException("Busca ou página inválida.");
  }

  private String literal(String b) {
    return "%"
        + (b == null ? "" : b.trim().toLowerCase(Locale.ROOT))
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        + "%";
  }
}
