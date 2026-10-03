package br.com.servire.api.site;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.integracao.AcessoParoquia;
import br.com.servire.api.site.dto.SiteDtos.*;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Somente snapshot explicitamente publicado é público; nunca serializa entidades de pessoas/eventos/avisos internos. */
@Service
public class SiteService {
  private final TenantRepository tenants;
  private final AcessoParoquia acesso;
  private final AuditLogService audit;
  private final JsonMapper json;
  private final TransactionTemplate leitura;
  @PersistenceContext private EntityManager em;

  public SiteService(
      TenantRepository tenants,
      AcessoParoquia acesso,
      AuditLogService audit,
      JsonMapper json,
      PlatformTransactionManager tm) {
    this.tenants = tenants;
    this.acesso = acesso;
    this.audit = audit;
    this.json = json;
    leitura = new TransactionTemplate(tm);
    leitura.setReadOnly(true);
    leitura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(readOnly = true)
  public Estado consultar() {
    var t =
        tenants
            .findById(TenantContext.get())
            .orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
    return estado(carregar(), t);
  }

  @Transactional
  public Estado salvar(Salvar req) {
    var t = travar();
    var e = carregar();
    conferir(e, req.versao());
    boolean novo = e == null;
    if (novo) {
      e = new SiteParoquia();
      e.versao = 1;
    }
    e.rascunho = json.writeValueAsString(req.dados());
    if (e.rascunho.length() > 65536)
      throw new BadRequestException("Conteúdo muito extenso. Reduza os textos.");
    if (novo) em.persist(e);
    em.flush();
    audit.registrar("SALVAR_RASCUNHO", "SITE", e.id, List.of("rascunho"));
    return estado(e, t);
  }

  @Transactional
  public Estado publicar(Publicar req) {
    if (!req.confirmar())
      throw new BadRequestException("Confirme a publicação pública dos textos e contatos.");
    var t = travar();
    var e = carregar();
    conferir(e, req.versao());
    if (e == null) throw new BadRequestException("Salve o rascunho antes de publicar.");
    e.publicado = e.rascunho;
    e.publicadoEm = Instant.now();
    em.flush();
    audit.registrar("PUBLICAR", "SITE", e.id, List.of("publicado"));
    return estado(e, t);
  }

  @Transactional
  public Estado despublicar(Revisao req) {
    var t = travar();
    var e = carregar();
    conferir(e, req.versao());
    if (e == null) throw new BadRequestException("Não há página publicada.");
    e.publicado = null;
    e.publicadoEm = null;
    em.flush();
    audit.registrar("DESPUBLICAR", "SITE", e.id, List.of("publicado"));
    return estado(e, t);
  }

  public Dados publico(String slug) {
    if (slug == null || slug.length() > 160) throw ausente();
    var t = tenants.findBySlug(slug).orElseThrow(SiteService::ausente);
    UUID anterior = TenantContext.get();
    TenantContext.set(t.getId());
    try {
      return leitura.execute(
          tx -> {
            var atual = tenants.findById(t.getId()).orElseThrow(SiteService::ausente);
            if (!acesso.liberada(atual)) throw ausente();
            var e = carregar();
            if (e == null || e.publicado == null) throw ausente();
            return json.readValue(e.publicado, Dados.class);
          });
    } finally {
      if (anterior == null) TenantContext.clear();
      else TenantContext.set(anterior);
    }
  }

  private Estado estado(SiteParoquia e, Tenant t) {
    return new Estado(
        e == null ? 0 : e.versao,
        t.getSlug(),
        e == null
            ? new Dados(t.getNome(), "", null, null, null, List.of())
            : json.readValue(e.rascunho, Dados.class),
        e == null || e.publicado == null ? null : json.readValue(e.publicado, Dados.class),
        e == null ? null : e.publicadoEm);
  }

  private SiteParoquia carregar() {
    return em.createQuery("select s from SiteParoquia s", SiteParoquia.class)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  private Tenant travar() {
    return tenants.bloquearParaCotas(TenantContext.get()).orElseThrow(SiteService::ausente);
  }

  private void conferir(SiteParoquia e, long versao) {
    if (versao != (e == null ? 0 : e.versao))
      throw new ConflictException("A página mudou. Atualize antes de salvar ou publicar.");
  }

  private static ResourceNotFoundException ausente() {
    return new ResourceNotFoundException("Página não encontrada.");
  }
}
