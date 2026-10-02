package br.com.servire.api.portal;
import br.com.servire.api.portal.dto.TrocaDtos.*;
import br.com.servire.api.portal.Troca.Situacao;
import br.com.servire.api.auth.*;
import br.com.servire.api.escala.*;
import br.com.servire.api.tenant.*;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
@Service
public class TrocaService {
 private static final ZoneId BRASILIA=ZoneId.of("America/Sao_Paulo");
 private final PortalService portal;private final FuncionalidadesPlano plano;private final TenantRepository tenants;
 private final EscalaRepository escalas;private final UsuarioTenantRepository usuarios;private final ElegibilidadeEscalaService elegibilidade;private final AuditLogService audit;
 @PersistenceContext private EntityManager em;
 public TrocaService(PortalService portal,FuncionalidadesPlano plano,TenantRepository tenants,EscalaRepository escalas,UsuarioTenantRepository usuarios,ElegibilidadeEscalaService elegibilidade,AuditLogService audit){this.portal=portal;this.plano=plano;this.tenants=tenants;this.escalas=escalas;this.usuarios=usuarios;this.elegibilidade=elegibilidade;this.audit=audit;}
 /** Diretório mínimo, limitado, sem contatos/ficha; somente voluntários com conta ativa e vínculo explícito. */
 @Transactional(readOnly=true)
 public List<Substituto> substitutos(String busca){
  plano.exigir("PORTAL_VOLUNTARIO");UUID pessoa=pessoa();String termo=busca==null?"":busca.trim();
  if(termo.length()<2||termo.length()>80)throw new BadRequestException("Busque um nome entre 2 e 80 caracteres.");
  String literal=termo.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");
  return em.createQuery("select p.id,p.nomeCompleto from Pessoa p where p.id<>:pessoa and p.voluntario.ativo=true and lower(p.nomeCompleto) like :busca escape '!' and exists (select u.id from UsuarioTenant u where u.tenant.id=:tenant and u.pessoaId=p.id and u.status=:ativo) order by p.nomeCompleto,p.id",Object[].class)
   .setParameter("pessoa",pessoa).setParameter("tenant",TenantContext.get()).setParameter("busca","%"+literal+"%").setParameter("ativo",UsuarioTenant.Status.ATIVO).setMaxResults(30).getResultList().stream().map(p->new Substituto((UUID)p[0],(String)p[1])).toList();
 }
 @Transactional
 public Pedido solicitar(UUID vagaId,Solicitar req){
  plano.exigir("PORTAL_VOLUNTARIO");bloquearTenant();UUID pessoa=pessoa();var v=bloquearVaga(vagaId);
  if(v.getVoluntario()==null||!v.getVoluntario().getId().equals(pessoa))throw naoEncontrada();
  if(!aberta(v)||v.getRespostaVersao()!=req.versao())throw mudou();
  if(pessoa.equals(req.substitutoId()))throw new BadRequestException("Escolha outra pessoa para substituir você.");
  elegibilidade.validar(v,req.substitutoId());
  var vinculo=em.createQuery("select u from UsuarioTenant u where u.tenant.id=:tenant and u.pessoaId=:pessoa and u.status=:ativo",UsuarioTenant.class).setParameter("tenant",TenantContext.get()).setParameter("pessoa",req.substitutoId()).setParameter("ativo",UsuarioTenant.Status.ATIVO).getResultStream().findFirst().orElseThrow(TrocaService::naoEncontrada);
  for(var antiga:em.createQuery("select t from Troca t where t.vagaId=:vaga and t.situacao in :ativas",Troca.class).setParameter("vaga",vagaId).setParameter("ativas",List.of(Situacao.AGUARDANDO_ACEITE,Situacao.ACEITA)).getResultList()){
   if(vigente(antiga,v))throw new ConflictException("Já existe uma troca em andamento para esta vaga.");
   antiga.situacao=Situacao.EXPIRADA;antiga.atualizadaEm=Instant.now();
  }
  em.flush();var t=new Troca();t.escalaId=v.getEvento().getEscala().getId();t.vagaId=vagaId;t.solicitanteId=pessoa;t.substitutoId=req.substitutoId();t.solicitanteUsuarioId=usuario();t.substitutoUsuarioId=vinculo.getUsuario().getId();t.vagaVersao=v.getRespostaVersao();t.situacao=Situacao.AGUARDANDO_ACEITE;t.criadaEm=Instant.now();t.atualizadaEm=t.criadaEm;t.celebracao=v.getEvento().getCelebracao();t.inicio=inicio(v);t.funcao=v.getFuncao().name();em.persist(t);em.flush();registrar(t,"SOLICITAR");return pedido(t,v,pessoa,nomes(List.of(t)));
 }
 @Transactional
 public Pedido responder(UUID id,Decisao req){
  plano.exigir("PORTAL_VOLUNTARIO");bloquearTenant();UUID pessoa=pessoa();var t=carregar(id);
  if(!t.substitutoId.equals(pessoa))throw naoEncontrada();var v=bloquearVaga(t.vagaId);em.refresh(t);validar(t,v,req.versao());
  if(t.situacao!=Situacao.AGUARDANDO_ACEITE)throw mudou();
  if(req.aprovar()){validarVinculos(t);elegibilidade.validar(v,t.substitutoId);}
  t.situacao=req.aprovar()?Situacao.ACEITA:Situacao.RECUSADA;finalizar(t,req.aprovar()?"ACEITAR":"RECUSAR_SUBSTITUTO");return pedido(t,v,pessoa,nomes(List.of(t)));
 }
 @Transactional
 public Pedido cancelar(UUID id,Versao req){
  bloquearTenant();UUID pessoa=pessoa();var t=carregar(id);if(!t.solicitanteId.equals(pessoa))throw naoEncontrada();
  // Retirada é permitida inclusive após perda do plano ou exclusão da vaga.
  if(t.versao!=req.versao()||!ativa(t))throw mudou();t.situacao=Situacao.CANCELADA;finalizar(t,"CANCELAR");return pedido(t,em.find(EscalaVaga.class,t.vagaId),pessoa,nomes(List.of(t)));
 }
 @Transactional
 public Pedido decidir(UUID id,Decisao req){
  plano.exigir("PORTAL_VOLUNTARIO");plano.exigir("ESCALAS");bloquearTenant();var t=carregar(id);var v=bloquearVaga(t.vagaId);em.refresh(t);validar(t,v,req.versao());
  if(t.situacao!=Situacao.ACEITA)throw new ConflictException("A troca precisa do aceite do substituto antes da decisão.");
  if(req.aprovar()){
   validarVinculos(t);var substituto=elegibilidade.validar(v,t.substitutoId);v.setVoluntario(substituto);v.setPresenca(Presenca.PENDENTE);
   audit.registrar("ALOCACAO_TROCA","ESCALA_VAGA",v.getId(),List.of("voluntario","resposta","presenca"));
  }
  t.situacao=req.aprovar()?Situacao.APROVADA:Situacao.RECUSADA;finalizar(t,req.aprovar()?"APROVAR":"RECUSAR_COORDENACAO");return pedido(t,v,null,nomes(List.of(t)));
 }
 @Transactional(readOnly=true) public Pagina<Pedido> minhas(int pagina){plano.exigir("PORTAL_VOLUNTARIO");UUID p=pessoa();return listar("(t.solicitanteId=:chave or t.substitutoId=:chave)",p,pagina,p);}
 @Transactional(readOnly=true) public Pagina<Pedido> coordenacao(UUID escala,int pagina){escalas.findById(escala).orElseThrow(TrocaService::naoEncontrada);return listar("t.escalaId=:chave",escala,pagina,null);}
 private Pagina<Pedido> listar(String filtro,UUID chave,int pagina,UUID pessoa){
  if(pagina<0||pagina>100000)throw new BadRequestException("Página inválida.");
  var itens=em.createQuery("select t from Troca t where "+filtro+" order by t.criadaEm desc,t.id desc",Troca.class).setParameter("chave",chave).setFirstResult(pagina*30).setMaxResults(30).getResultList();
  long total=em.createQuery("select count(t) from Troca t where "+filtro,Long.class).setParameter("chave",chave).getSingleResult();Map<UUID,EscalaVaga> vagas=new HashMap<>();
  if(!itens.isEmpty())for(var v:em.createQuery("select v from EscalaVaga v join fetch v.evento e join fetch e.escala where v.id in :ids",EscalaVaga.class).setParameter("ids",itens.stream().map(t->t.vagaId).toList()).getResultList())vagas.put(v.getId(),v);
  var nomes=nomes(itens);return new Pagina<>(itens.stream().map(t->pedido(t,vagas.get(t.vagaId),pessoa,nomes)).toList(),total,pagina,30);
 }
 private Map<UUID,String> nomes(List<Troca> itens){Map<UUID,String> r=new HashMap<>();if(itens.isEmpty())return r;Set<UUID> ids=new HashSet<>();for(var t:itens){ids.add(t.solicitanteId);ids.add(t.substitutoId);}for(var p:em.createQuery("select p.id,p.nomeCompleto from Pessoa p where p.id in :ids",Object[].class).setParameter("ids",ids).getResultList())r.put((UUID)p[0],(String)p[1]);return r;}
 private Pedido pedido(Troca t,EscalaVaga v,UUID pessoa,Map<UUID,String> nomes){boolean vigente=vigente(t,v);return new Pedido(t.id,t.vagaId,t.celebracao,t.inicio,t.funcao,ativa(t)&&!vigente?Situacao.EXPIRADA:t.situacao,t.versao,t.criadaEm,t.atualizadaEm,nomes.get(t.solicitanteId),nomes.get(t.substitutoId),t.solicitanteId.equals(pessoa),t.substitutoId.equals(pessoa),vigente);}
 private static boolean ativa(Troca t){return t.situacao==Situacao.AGUARDANDO_ACEITE||t.situacao==Situacao.ACEITA;}
 private static boolean aberta(EscalaVaga v){return v!=null&&!v.getEvento().isReferencia()&&v.getEvento().getEscala().getStatus()==StatusEscala.FINALIZADA&&inicio(v).atZone(BRASILIA).toInstant().isAfter(Instant.now());}
 private static boolean vigente(Troca t,EscalaVaga v){return ativa(t)&&aberta(v)&&v.getVoluntario()!=null&&v.getVoluntario().getId().equals(t.solicitanteId)&&v.getRespostaVersao()==t.vagaVersao&&inicio(v).equals(t.inicio)&&v.getFuncao().name().equals(t.funcao);}
 private void validar(Troca t,EscalaVaga v,long versao){if(t.versao!=versao||!vigente(t,v))throw mudou();}
 private void validarVinculos(Troca t){for(var par:List.of(Map.entry(t.solicitanteUsuarioId,t.solicitanteId),Map.entry(t.substitutoUsuarioId,t.substitutoId))){var u=usuarios.findByUsuario_IdAndTenant_Id(par.getKey(),TenantContext.get()).orElseThrow(TrocaService::naoEncontrada);if(u.getStatus()!=UsuarioTenant.Status.ATIVO||!par.getValue().equals(u.getPessoaId()))throw new ConflictException("O vínculo pessoal de um participante mudou.");}}
 private Troca carregar(UUID id){var t=em.find(Troca.class,id);if(t==null)throw naoEncontrada();return t;}
 private EscalaVaga bloquearVaga(UUID id){UUID escala=em.createQuery("select v.evento.escala.id from EscalaVaga v where v.id=:id",UUID.class).setParameter("id",id).getResultStream().findFirst().orElseThrow(TrocaService::naoEncontrada);escalas.bloquear(escala).orElseThrow(TrocaService::naoEncontrada);return em.createQuery("select v from EscalaVaga v join fetch v.evento where v.id=:id",EscalaVaga.class).setParameter("id",id).getSingleResult();}
 private void bloquearTenant(){tenants.bloquearParaCotas(TenantContext.get()).orElseThrow(TrocaService::naoEncontrada);}
 private UUID pessoa(){UUID p=portal.pessoaAtual();if(p==null)throw new BadRequestException("Peça ao administrador para vincular sua pessoa.");return p;}
 private static UUID usuario(){return ((AuthenticatedUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).usuarioId();}
 private static LocalDateTime inicio(EscalaVaga v){return v.getEvento().getData().atTime(v.getEvento().getHorario());}
 private void finalizar(Troca t,String acao){t.atualizadaEm=Instant.now();em.flush();registrar(t,acao);}
 private void registrar(Troca t,String acao){audit.registrar(acao,"ESCALA_TROCA",t.id,List.of("situacao"));}
 private static ConflictException mudou(){return new ConflictException("A troca ou vaga mudou. Atualize antes de continuar.");}
 private static ResourceNotFoundException naoEncontrada(){return new ResourceNotFoundException("Troca, vaga ou participante não encontrado.");}
}
