package br.com.servire.api.pastoral;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.pastoral.dto.PastoralDtos.*;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PastoralService {
  private final EquipePastoralRepository equipes;
  private final MembroPastoralRepository membros;
  private final PessoaRepository pessoas;
  private final FuncionalidadesPlano plano;
  private final AuditLogService audit;
  @PersistenceContext private EntityManager em;

  public PastoralService(
      EquipePastoralRepository equipes,
      MembroPastoralRepository membros,
      PessoaRepository pessoas,
      FuncionalidadesPlano plano,
      AuditLogService audit) {
    this.equipes = equipes;
    this.membros = membros;
    this.pessoas = pessoas;
    this.plano = plano;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Pagina<Equipe> listar(String busca, int pagina) {
    if (pagina < 0 || pagina > 100000 || busca != null && busca.length() > 120)
      throw new BadRequestException("Busca inválida.");
    Specification<EquipePastoral> filtro = (r, q, b) -> b.conjunction();
    if (busca != null && !busca.isBlank()) {
      String texto = "%" + literal(busca) + "%";
      filtro = (r, q, b) -> b.like(b.lower(r.get("nome")), texto, '\\');
    }
    var p = equipes.findAll(filtro, PageRequest.of(pagina, 30, Sort.by("nome", "id")));
    return new Pagina<>(p.map(Equipe::de).getContent(), p.getTotalElements(), pagina, 30);
  }

  @Transactional
  public Equipe salvar(UUID id, SalvarEquipe req) {
    plano.exigir("PASTORAIS");
    var e =
        id == null
            ? new EquipePastoral()
            : equipes
                .bloquear(id)
                .orElseThrow(() -> new ResourceNotFoundException("Equipe não encontrada."));
    if (id != null && (req.versao() == null || req.versao() != e.versao))
      throw new ConflictException("A equipe mudou. Atualize antes de salvar.");
    e.nome = req.nome().trim();
    e.descricao = req.descricao();
    e.ativo = req.ativo();
    equipes.saveAndFlush(e);
    audit.registrar(
        id == null ? "CRIAR" : "ALTERAR", "PASTORAL", e.id, List.of("nome", "descricao", "ativo"));
    return Equipe.de(e);
  }

  @Transactional(readOnly = true)
  public Pagina<Membro> membros(UUID equipe, int pagina) {
    if (pagina < 0 || pagina > 100000) throw new BadRequestException("Página inválida.");
    equipes
        .findById(equipe)
        .orElseThrow(() -> new ResourceNotFoundException("Equipe não encontrada."));
    var p = membros.findByEquipeId(equipe, PageRequest.of(pagina, 30, Sort.by("id")));
    Map<UUID, String> nomes = new HashMap<>();
    if (!p.isEmpty())
      for (Object[] n :
          em.createQuery(
                  "select p.id,p.nomeCompleto from Pessoa p where p.id in :ids", Object[].class)
              .setParameter("ids", p.stream().map(m -> m.pessoaId).toList())
              .getResultList()) nomes.put((UUID) n[0], (String) n[1]);
    return new Pagina<>(
        p.stream()
            .map(
                m ->
                    new Membro(m.id, m.pessoaId, nomes.get(m.pessoaId), m.papel, m.ativo, m.versao))
            .toList(),
        p.getTotalElements(),
        pagina,
        30);
  }

  @Transactional
  public void salvarMembro(UUID equipe, SalvarMembro req) {
    plano.exigir("PASTORAIS");
    var e =
        equipes
            .bloquear(equipe)
            .orElseThrow(() -> new ResourceNotFoundException("Equipe não encontrada."));
    if (!e.ativo) throw new ConflictException("Reative a equipe antes de alterar participantes.");
    pessoas
        .findById(req.pessoaId())
        .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));
    var existente = membros.findByEquipeIdAndPessoaId(equipe, req.pessoaId());
    var m = existente.orElseGet(MembroPastoral::new);
    if (existente.isPresent() && (req.versao() == null || req.versao() != m.versao))
      throw new ConflictException("O participante já existe ou mudou. Atualize a lista.");
    m.equipeId = equipe;
    m.pessoaId = req.pessoaId();
    m.papel = req.papel();
    m.ativo = req.ativo();
    membros.saveAndFlush(m);
    audit.registrar("PARTICIPANTE", "PASTORAL", m.id, List.of("pessoaId", "papel", "ativo"));
  }

  @Transactional(readOnly = true)
  public List<PessoaOpcao> pessoas(String busca) {
    if (busca == null || busca.trim().length() < 2 || busca.length() > 120) return List.of();
    return em
        .createQuery(
            "select p.id,p.nomeCompleto from Pessoa p where lower(p.nomeCompleto) like :texto"
                + " escape '\\' order by p.nomeCompleto,p.id",
            Object[].class)
        .setParameter("texto", "%" + literal(busca) + "%")
        .setMaxResults(30)
        .getResultList()
        .stream()
        .map(p -> new PessoaOpcao((UUID) p[0], (String) p[1]))
        .toList();
  }

  private UUID pessoaDaCoordenacao() {
    var a =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (a == null
        || !(a.getPrincipal() instanceof br.com.servire.api.security.AuthenticatedUser u)
        || u.suporte()
        || !Objects.equals(u.tenantId(), br.com.servire.api.tenant.TenantContext.get()))
      throw new ForbiddenException("Coordenação exige conta pessoal.");
    var v =
        em.createQuery(
                "select v.pessoaId from UsuarioTenant v where v.usuario.id=:u and v.tenant.id=:t"
                    + " and v.status=:s and v.pessoaId is not null",
                UUID.class)
            .setParameter("u", u.usuarioId())
            .setParameter("t", u.tenantId())
            .setParameter("s", br.com.servire.api.auth.UsuarioTenant.Status.ATIVO)
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (v == null) throw new ForbiddenException("Vincule a conta à pessoa da coordenação.");
    return v;
  }

  private void exigirCoordenacao(UUID equipe) {
    var m =
        membros
            .findByEquipeIdAndPessoaId(equipe, pessoaDaCoordenacao())
            .orElseThrow(() -> new ResourceNotFoundException("Equipe não encontrada."));
    if (!m.ativo
        || m.papel != MembroPastoral.Papel.COORDENADOR
        || !equipes.findById(equipe).orElseThrow().ativo)
      throw new ResourceNotFoundException("Equipe não encontrada.");
  }

  @Transactional(readOnly = true)
  public List<Equipe> minhasEquipes(int pagina) {
    if (pagina < 0 || pagina > 100000) throw new BadRequestException("Página inválida.");
    plano.exigir("PASTORAIS");
    UUID pessoa = pessoaDaCoordenacao();
    return em
        .createQuery(
            "select e from EquipePastoral e,MembroPastoral m where m.equipeId=e.id and"
                + " m.pessoaId=:p and m.papel=:papel and m.ativo=true and e.ativo=true order by"
                + " e.nome,e.id",
            EquipePastoral.class)
        .setParameter("p", pessoa)
        .setParameter("papel", MembroPastoral.Papel.COORDENADOR)
        .setFirstResult(pagina * 30)
        .setMaxResults(30)
        .getResultList()
        .stream()
        .map(Equipe::de)
        .toList();
  }

  @Transactional(readOnly = true)
  public Pagina<Membro> membrosProprios(UUID equipe, int pagina) {
    plano.exigir("PASTORAIS");
    exigirCoordenacao(equipe);
    return membros(equipe, pagina);
  }

  @Transactional
  public void alterarMembroProprio(UUID equipe, UUID pessoa, boolean ativo, long versao) {
    plano.exigir("PASTORAIS");
    equipes
        .bloquear(equipe)
        .orElseThrow(() -> new ResourceNotFoundException("Equipe não encontrada."));
    exigirCoordenacao(equipe);
    var m =
        membros
            .findByEquipeIdAndPessoaId(equipe, pessoa)
            .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
    if (m.papel == MembroPastoral.Papel.COORDENADOR)
      throw new ForbiddenException("Somente a administração altera coordenadores.");
    if (m.versao != versao) throw new ConflictException("O membro mudou. Atualize a lista.");
    m.ativo = ativo;
    em.flush();
    audit.registrar("MEMBRO_PROPRIO_ALTERADO", "PASTORAL", m.id, List.of("ativo"));
  }

  private static String literal(String s) {
    return s.trim()
        .toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_");
  }
}
