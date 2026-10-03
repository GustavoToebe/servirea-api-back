package br.com.servire.api.onboarding;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.escala.StatusEscala;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.onboarding.dto.OnboardingDtos.*;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * F07: revisão da configuração, sem criar dados, convidar ou finalizar uma escala automaticamente.
 * Consultas de existência limitadas a um registro, sem fichas/contagens. Só consultar o módulo autorizado.
 * Paróquia -> progresso em toda escrita; versão 0 indica ausência, primeira gravação avança para 1.
 */
@Service
public class OnboardingService {
  private final TenantRepository tenants;
  private final FuncionalidadesPlano plano;
  private final AuditLogService audit;
  @PersistenceContext private EntityManager em;

  public OnboardingService(
      TenantRepository tenants, FuncionalidadesPlano plano, AuditLogService audit) {
    this.tenants = tenants;
    this.plano = plano;
    this.audit = audit;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Resposta buscar() {
    return resposta(progresso());
  }

  @Transactional
  public Resposta alterar(EtapaOnboarding codigo, Salvar req) {
    tenants
        .bloquearParaCotas(TenantContext.get())
        .orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
    var p = progresso();
    long versao = p == null ? 0 : p.versao;
    if (req.versao() == null || req.versao() != versao)
      throw new ConflictException("O checklist mudou. Atualize antes de registrar sua revisão.");
    if (!tem(permissao(codigo)))
      throw new ForbiddenException("Seu perfil não permite revisar este módulo.");
    if (req.acao() == Acao.PULAR && codigo != EtapaOnboarding.CONVITE)
      throw new BadRequestException("Somente o convite de outro usuário pode ser dispensado.");
    if (req.acao() == Acao.CONCLUIR) {
      if (codigo == EtapaOnboarding.ESCALA) plano.exigir("ESCALAS");
      if (!pronta(codigo))
        throw new BadRequestException(
            "Complete a configuração indicada antes de concluir a etapa.");
    }
    boolean novo = p == null;
    if (novo) {
      p = new OnboardingProgresso();
      p.iniciadoEm = Instant.now();
    }
    p.revisar(codigo, req.acao() == Acao.CONCLUIR);
    if (codigo == EtapaOnboarding.CONVITE) p.conviteDispensado = req.acao() == Acao.PULAR;
    p.versao++;
    p.atualizadoEm = Instant.now();
    if (novo) em.persist(p);
    em.flush();
    audit.registrar(req.acao().name(), "ONBOARDING", p.id, List.of(codigo.name()));
    return resposta(p);
  }

  private OnboardingProgresso progresso() {
    return em.createQuery("select p from OnboardingProgresso p", OnboardingProgresso.class)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  private Resposta resposta(OnboardingProgresso p) {
    var etapas = new ArrayList<Etapa>();
    int concluidas = 0, total = 0;
    EtapaOnboarding proxima = null;
    boolean gerenciar = tem("ONBOARDING_GERENCIAR");
    var recursos = plano.liberadas();
    for (var e : EtapaOnboarding.values()) {
      boolean acesso = tem(permissao(e));
      boolean contratado = e != EtapaOnboarding.ESCALA || recursos.contains("ESCALAS");
      boolean pronta = acesso && contratado && pronta(e);
      boolean revisada = p != null && p.revisada(e);
      boolean dispensada = e == EtapaOnboarding.CONVITE && p != null && p.conviteDispensado;
      Situacao estado =
          !acesso
              ? Situacao.SEM_PERMISSAO
              : !contratado
                  ? Situacao.NAO_CONTRATADA
                  : dispensada
                      ? Situacao.DISPENSADA
                      : revisada
                          ? (pronta ? Situacao.CONCLUIDA : Situacao.REVISAR)
                          : pronta ? Situacao.PRONTA : Situacao.PENDENTE;
      if (acesso && contratado) {
        total++;
        if (estado == Situacao.CONCLUIDA || estado == Situacao.DISPENSADA) concluidas++;
        else if (proxima == null && contratado) proxima = e;
      }
      etapas.add(
          new Etapa(
              e,
              titulo(e),
              orientacao(e),
              acesso ? url(e) : null,
              permissao(e),
              estado,
              gerenciar && acesso && contratado && pronta && estado != Situacao.CONCLUIDA,
              gerenciar && acesso && (revisada || dispensada),
              gerenciar && acesso && e == EtapaOnboarding.CONVITE && !dispensada));
    }
    return new Resposta(
        p == null ? 0 : p.versao,
        List.copyOf(etapas),
        concluidas,
        total,
        total == 0 ? 0 : concluidas * 100 / total,
        proxima,
        p == null ? null : p.iniciadoEm,
        p == null ? null : p.atualizadoEm);
  }

  private boolean pronta(EtapaOnboarding e) {
    return switch (e) {
      case PAROQUIA -> {
        var t = em.find(Tenant.class, TenantContext.get());
        yield t != null && !vazio(t.getNome()) && !vazio(t.getCidade()) && !vazio(t.getUf());
      }
      case CONVITE ->
          em.createQuery(
                      "select v.usuario.id from UsuarioTenant v where v.tenant.id=:tenant and"
                          + " v.status=:ativo",
                      UUID.class)
                  .setParameter("tenant", TenantContext.get())
                  .setParameter("ativo", UsuarioTenant.Status.ATIVO)
                  .setMaxResults(2)
                  .getResultList()
                  .size()
              >= 2;
      case PESSOAS ->
          !em.createQuery("select p.id from Pessoa p", UUID.class)
              .setMaxResults(1)
              .getResultList()
              .isEmpty();
      case VOLUNTARIOS ->
          !em.createQuery("select v.id from Voluntario v where v.ativo=true", UUID.class)
              .setMaxResults(1)
              .getResultList()
              .isEmpty();
      case ESCALA ->
          !em.createQuery(
                  "select v.id from EscalaVaga v join v.evento e join e.escala s where"
                      + " s.status=:status and e.referencia=false and v.voluntario is not null",
                  UUID.class)
              .setParameter("status", StatusEscala.FINALIZADA)
              .setMaxResults(1)
              .getResultList()
              .isEmpty();
    };
  }

  private boolean tem(String codigo) {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a != null
        && a.getAuthorities().stream().anyMatch(p -> p.getAuthority().equals("PERM_" + codigo));
  }

  private boolean vazio(String s) {
    return s == null || s.isBlank();
  }

  private String permissao(EtapaOnboarding e) {
    return switch (e) {
      case PAROQUIA -> "PAROQUIA";
      case CONVITE -> "USUARIO";
      case PESSOAS, VOLUNTARIOS -> "PESSOA";
      case ESCALA -> "ESCALA";
    };
  }

  private String titulo(EtapaOnboarding e) {
    return switch (e) {
      case PAROQUIA -> "Revisar a paróquia";
      case CONVITE -> "Convidar a equipe";
      case PESSOAS -> "Cadastrar ou importar pessoas";
      case VOLUNTARIOS -> "Preparar os voluntários";
      case ESCALA -> "Publicar a primeira escala";
    };
  }

  private String url(EtapaOnboarding e) {
    return switch (e) {
      case PAROQUIA -> "/paroquia";
      case CONVITE -> "/usuarios";
      case PESSOAS, VOLUNTARIOS -> "/pessoas";
      case ESCALA -> "/escalas";
    };
  }

  private String orientacao(EtapaOnboarding e) {
    return switch (e) {
      case PAROQUIA ->
          "Confira nome, cidade/UF e contatos. Salve os dados antes de registrar a revisão.";
      case CONVITE ->
          "Convide outra conta em Usuários e confira as permissões do perfil. Ter vínculo ativo não"
              + " confirma que a pessoa já entrou. Se trabalha sozinho, pode dispensar este passo.";
      case PESSOAS ->
          "Cadastre uma pessoa ou use a importação CSV, se o plano permitir. Confira a prévia e"
              + " evite duplicados.";
      case VOLUNTARIOS ->
          "Configure pelo menos um voluntário ativo em Pessoas. Revise funções habilitadas e"
              + " disponibilidade antes de montar a escala.";
      case ESCALA ->
          "Monte as celebrações, distribua os voluntários e revise antes de finalizar. É necessário"
              + " ao menos uma vaga ocupada, sem referência, em uma escala finalizada.";
    };
  }
}
