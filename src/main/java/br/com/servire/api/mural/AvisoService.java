package br.com.servire.api.mural;
import br.com.servire.api.mural.dto.AvisoDtos.*;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.tenant.*;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.*;
/** Público não concede permissão MURAL. Leituras valem para a versão exata, nunca por abertura automática. */
@Service
public class AvisoService {
 private final AvisoRepository repo;private final AuditLogService audit;private final FuncionalidadesPlano plano;private final TenantRepository tenants;
 @PersistenceContext private EntityManager em;
 public AvisoService(AvisoRepository repo,AuditLogService audit,FuncionalidadesPlano plano,TenantRepository tenants){this.repo=repo;this.audit=audit;this.plano=plano;this.tenants=tenants;}
 @Transactional(readOnly=true) public Pagina listar(String busca,Aviso.Status status,int pagina,int tamanho){
  if(pagina<0||pagina>100000||tamanho<1||tamanho>100||busca!=null&&busca.length()>160)throw new BadRequestException("Filtro inválido.");
  Specification<Aviso> filtro=(r,q,b)->b.conjunction();
  if(!editor()){
   UUID usuario=pessoal();
   filtro=filtro.and((r,q,b)->{var sub=q.subquery(UUID.class);var d=sub.from(AvisoDestinatario.class);sub.select(d.get("avisoId")).where(b.equal(d.get("tenantId"),TenantContext.get()),b.equal(d.get("usuarioId"),usuario),b.equal(d.get("avisoId"),r.get("id")));
    return b.and(b.equal(r.get("status"),Aviso.Status.PUBLICADO),b.or(b.equal(r.get("publico"),Aviso.Publico.TODOS),b.exists(sub)));});
  }
  if(status!=null)filtro=filtro.and((r,q,b)->b.equal(r.get("status"),status));
  if(busca!=null&&!busca.isBlank())filtro=filtro.and((r,q,b)->b.like(b.lower(r.get("titulo")),"%"+literal(busca)+"%",'\\'));
  var p=repo.findAll(filtro,PageRequest.of(pagina,tamanho,Sort.by(Sort.Order.desc("criadoEm"),Sort.Order.desc("id"))));
  return new Pagina(respostas(p.getContent()),p.getTotalElements(),pagina,tamanho);
 }
 @Transactional(readOnly=true) public Resposta buscar(UUID id){var e=carregar(id);autorizar(e);return respostas(List.of(e)).getFirst();}
 @Transactional(readOnly=true) public List<Conta> contas(String busca){
  if(busca!=null&&busca.length()>160)throw new BadRequestException("Busca muito longa.");
  return em.createQuery("select v.usuario.id,v.usuario.nome from UsuarioTenant v where v.tenant.id=:tenant and v.status=:ativo and lower(v.usuario.nome) like :busca escape '\\' order by lower(v.usuario.nome),v.usuario.id",Object[].class)
   .setParameter("tenant",TenantContext.get()).setParameter("ativo",UsuarioTenant.Status.ATIVO).setParameter("busca","%"+literal(busca==null?"":busca)+"%").setMaxResults(30).getResultList().stream().map(r->new Conta((UUID)r[0],(String)r[1])).toList();
 }
 @Transactional public Resposta salvar(UUID id,Salvar req){
  tenants.bloquearParaCotas(TenantContext.get()).orElseThrow(()->new ResourceNotFoundException("Paróquia não encontrada."));plano.exigir("MURAL");
  var e=id==null?new Aviso():carregar(id);
  if(id!=null&&(req.versao()==null||req.versao()!=e.versao))throw new ConflictException("O registro mudou. Atualize a página antes de salvar.");
  var publico=req.publico()==null?Aviso.Publico.TODOS:req.publico();var ids=req.destinatarios()==null?List.<UUID>of():req.destinatarios().stream().distinct().toList();
  if(publico==Aviso.Publico.SELECIONADOS){
   if(ids.isEmpty()||ids.size()>100)throw new BadRequestException("Selecione de 1 a 100 contas.");
   long n=em.createQuery("select count(v) from UsuarioTenant v where v.tenant.id=:tenant and v.usuario.id in :ids and v.status=:ativo",Long.class).setParameter("tenant",TenantContext.get()).setParameter("ids",ids).setParameter("ativo",UsuarioTenant.Status.ATIVO).getSingleResult();
   if(n!=ids.size())throw new BadRequestException("Destinatário sem vínculo ativo nesta paróquia.");
  }else ids=List.of();
  if(id==null)e.criadoEm=Instant.now();e.titulo=req.titulo().trim();e.descricao=req.descricao().trim();e.status=req.status();e.prazo=req.prazo();e.publico=publico;e.atualizadoEm=Instant.now();repo.saveAndFlush(e);
  em.createQuery("delete from AvisoDestinatario d where d.avisoId=:id").setParameter("id",e.id).executeUpdate();
  for(var usuario:ids){var d=new AvisoDestinatario();d.avisoId=e.id;d.usuarioId=usuario;em.persist(d);}em.flush();
  audit.registrar(id==null?"CRIAR":"ALTERAR","MURAL",e.id,List.of("titulo","descricao","status","prazo","publico","destinatarios"));return respostas(List.of(e)).getFirst();
 }
 @Transactional public Resposta confirmar(UUID id,long versao){
  UUID usuario=pessoal();tenants.bloquearParaCotas(TenantContext.get()).orElseThrow(()->new ResourceNotFoundException("Paróquia não encontrada."));plano.exigir("MURAL");
  var e=carregar(id);autorizar(e);if(e.status!=Aviso.Status.PUBLICADO)throw new BadRequestException("Aviso arquivado.");if(e.versao!=versao)throw new ConflictException("O aviso mudou. Leia a versão atual antes de confirmar.");
  var l=em.createQuery("select l from AvisoLeitura l where l.avisoId=:id and l.usuarioId=:usuario",AvisoLeitura.class).setParameter("id",id).setParameter("usuario",usuario).getResultStream().findFirst().orElse(null);
  if(l==null){l=new AvisoLeitura();l.avisoId=id;l.usuarioId=usuario;l.versaoAviso=versao;l.lidoEm=Instant.now();em.persist(l);audit.registrar("CONFIRMAR_LEITURA","MURAL",id,List.of("versao"));}
  else if(l.versaoAviso!=versao){l.versaoAviso=versao;l.lidoEm=Instant.now();audit.registrar("CONFIRMAR_LEITURA","MURAL",id,List.of("versao"));}em.flush();return respostas(List.of(e)).getFirst();
 }
 private List<Resposta> respostas(List<Aviso> avisos){
  if(avisos.isEmpty())return List.of();var ids=avisos.stream().map(a->a.id).toList();boolean editor=editor();
  var auth=SecurityContextHolder.getContext().getAuthentication();UUID usuario=auth!=null&&auth.getPrincipal() instanceof AuthenticatedUser u&&!u.suporte()?u.usuarioId():null;
  Map<UUID,Long> proprias=new HashMap<>();if(usuario!=null)for(var l:em.createQuery("select l from AvisoLeitura l where l.avisoId in :ids and l.usuarioId=:usuario",AvisoLeitura.class).setParameter("ids",ids).setParameter("usuario",usuario).getResultList())proprias.put(l.avisoId,l.versaoAviso);
  Map<UUID,Long> totais=new HashMap<>();if(editor)for(var r:em.createQuery("select l.avisoId,count(l) from AvisoLeitura l, Aviso a where l.avisoId=a.id and l.avisoId in :ids and l.versaoAviso=a.versao group by l.avisoId",Object[].class).setParameter("ids",ids).getResultList())totais.put((UUID)r[0],(Long)r[1]);
  Map<UUID,List<UUID>> destinatarios=new HashMap<>();if(editor)for(var d:em.createQuery("select d from AvisoDestinatario d where d.avisoId in :ids",AvisoDestinatario.class).setParameter("ids",ids).getResultList())destinatarios.computeIfAbsent(d.avisoId,k->new ArrayList<>()).add(d.usuarioId);
  return avisos.stream().map(e->new Resposta(e.id,e.titulo,e.descricao,e.status,e.prazo,e.versao,e.criadoEm,e.atualizadoEm,e.publico,List.copyOf(destinatarios.getOrDefault(e.id,List.of())),Objects.equals(proprias.get(e.id),e.versao),editor?totais.getOrDefault(e.id,0L):0)).toList();
 }
 private void autorizar(Aviso e){if(editor())return;UUID usuario=pessoal();if(e.status!=Aviso.Status.PUBLICADO||e.publico==Aviso.Publico.SELECIONADOS&&em.createQuery("select count(d) from AvisoDestinatario d where d.avisoId=:id and d.usuarioId=:u",Long.class).setParameter("id",e.id).setParameter("u",usuario).getSingleResult()==0)throw new ResourceNotFoundException("Aviso não encontrado.");}
 private UUID pessoal(){var a=SecurityContextHolder.getContext().getAuthentication();if(a==null||!(a.getPrincipal() instanceof AuthenticatedUser u)||u.suporte()||!Objects.equals(u.tenantId(),TenantContext.get()))throw new ForbiddenException("Confirmação e consulta pessoal exigem uma conta da paróquia.");return u.usuarioId();}
 private boolean editor(){var a=SecurityContextHolder.getContext().getAuthentication();return a!=null&&a.getAuthorities().stream().anyMatch(x->Set.of("PERM_MURAL_CRIAR","PERM_MURAL_ALTERAR").contains(x.getAuthority()));}
 private static String literal(String s){return s.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
 private Aviso carregar(UUID id){return repo.findById(id).orElseThrow(()->new ResourceNotFoundException("Aviso não encontrado."));}
}
