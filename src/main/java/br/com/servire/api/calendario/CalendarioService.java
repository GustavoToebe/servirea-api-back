package br.com.servire.api.calendario;

import br.com.servire.api.acesso.PermissoesDaSessao;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.auth.*;
import br.com.servire.api.integracao.*;
import br.com.servire.api.portal.PortalService;
import br.com.servire.api.security.*;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CalendarioService {
  private final CalendarioRepository repo;
  private final PortalService portal;
  private final TenantRepository tenants;
  private final UsuarioTenantRepository usuarios;
  private final AcessoParoquia acesso;
  private final FuncionalidadesPlano plano;
  private final AuditLogService audit;
  private final TransactionTemplate leitura;

  public CalendarioService(
      CalendarioRepository repo,
      PortalService portal,
      TenantRepository tenants,
      UsuarioTenantRepository usuarios,
      AcessoParoquia acesso,
      FuncionalidadesPlano plano,
      AuditLogService audit,
      PlatformTransactionManager tm) {
    this.repo = repo;
    this.portal = portal;
    this.tenants = tenants;
    this.usuarios = usuarios;
    this.acesso = acesso;
    this.plano = plano;
    this.audit = audit;
    leitura = new TransactionTemplate(tm);
    leitura.setReadOnly(true);
    leitura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional
  public Criada criar() {
    plano.exigir("CALENDARIO");
    plano.exigir("PORTAL_VOLUNTARIO");
    tenants.bloquearParaCotas(TenantContext.get()).orElseThrow();
    UUID pessoa = portal.pessoaAtual();
    if (pessoa == null)
      throw new BadRequestException(
          "Peça ao administrador para vincular sua pessoa antes de gerar o calendário.");
    var u =
        (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    var c =
        repo.findByTenantIdAndUsuarioId(u.tenantId(), u.usuarioId())
            .orElseGet(CalendarioAssinatura::new);
    String token = OpaqueTokenGenerator.gerar();
    c.tenantId = u.tenantId();
    c.usuarioId = u.usuarioId();
    c.pessoaId = pessoa;
    c.tokenHash = OpaqueTokenGenerator.hash(token);
    c.criadoEm = Instant.now();
    c.expiraEm = c.criadoEm.plusSeconds(365L * 86400);
    repo.saveAndFlush(c);
    audit.registrar("GERAR_LINK", "CALENDARIO", c.id, List.of("assinatura"));
    return new Criada(token, c.expiraEm);
  }

  @Transactional
  public void revogar() {
    var u =
        (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    tenants.bloquearParaCotas(TenantContext.get()).orElseThrow();
    repo.findByTenantIdAndUsuarioId(u.tenantId(), u.usuarioId())
        .ifPresent(
            c -> {
              repo.delete(c);
              audit.registrar("REVOGAR_LINK", "CALENDARIO", c.id, List.of("assinatura"));
            });
  }

  public String feed(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw indisponivel();
    var c =
        repo.findByTokenHash(OpaqueTokenGenerator.hash(token))
            .orElseThrow(CalendarioService::indisponivel);
    if (!c.expiraEm.isAfter(Instant.now())) throw indisponivel();
    UUID anterior = TenantContext.get();
    TenantContext.set(c.tenantId);
    try {
      return leitura.execute(
          tx -> {
            var v =
                usuarios
                    .findComPerfilByUsuario_IdAndTenant_Id(c.usuarioId, c.tenantId)
                    .orElseThrow(CalendarioService::indisponivel);
            if (v.getStatus() != UsuarioTenant.Status.ATIVO
                || !Objects.equals(v.getPessoaId(), c.pessoaId)
                || v.getPerfil() != null && !v.getPerfil().isAtivo()
                || !acesso.liberada(v.getTenant())
                || !plano.permitida("CALENDARIO")
                || !plano.permitida("PORTAL_VOLUNTARIO")
                || PermissoesDaSessao.de(v).stream()
                    .noneMatch(a -> a.getAuthority().equals("PERM_CALENDARIO"))
                || PermissoesDaSessao.de(v).stream()
                    .noneMatch(a -> a.getAuthority().equals("PERM_PORTAL_VOLUNTARIO")))
              throw indisponivel();
            LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
            return Icalendar.gerar(
                portal.compromissos(c.pessoaId, hoje.minusDays(30), hoje.plusDays(335)));
          });
    } finally {
      if (anterior == null) TenantContext.clear();
      else TenantContext.set(anterior);
    }
  }

  private static ResourceNotFoundException indisponivel() {
    return new ResourceNotFoundException("Calendário indisponível.");
  }

  public record Criada(String token, Instant expiraEm) {}
}
