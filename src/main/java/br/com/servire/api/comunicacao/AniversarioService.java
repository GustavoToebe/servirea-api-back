package br.com.servire.api.comunicacao;
import br.com.servire.api.comunicacao.dto.AniversarioDtos.*;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.CriarRequest;
import br.com.servire.api.tenant.*;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.integracao.*;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
/** F19: sem autorização específica não agenda. Nenhuma chamada de envio ocorre neste serviço. */
@Service
public class AniversarioService {
 private final TenantRepository tenants;private final FuncionalidadesPlano plano;private final AcessoParoquia acesso;private final AuditLogService audit;private final ComunicadoService comunicados;private final boolean agendadorAtivo;
 @PersistenceContext private EntityManager em;
 public AniversarioService(TenantRepository tenants,FuncionalidadesPlano plano,AcessoParoquia acesso,AuditLogService audit,ComunicadoService comunicados,@Value("${servire.aniversarios.ativo:false}") boolean ativo){this.tenants=tenants;this.plano=plano;this.acesso=acesso;this.audit=audit;this.comunicados=comunicados;this.agendadorAtivo=ativo;}
 @Transactional(readOnly=true) public List<Config> configuracoes(){return Arrays.stream(TipoEnvio.values()).map(c->{var e=config(c);return new Config(c,e!=null&&e.ativo,e==null?null:e.layoutId,e==null?0:e.versao,agendadorAtivo);}).toList();}
 @Transactional(readOnly=true) public List<OpcaoLayout> layouts(){return em.createQuery("select l from Layout l where l.ativo=true",Layout.class).getResultList().stream().filter(l->l.getTipoLayout()!=TipoLayout.EVENTO).map(l->new OpcaoLayout(l.getId(),l.getNome(),l.getTipoEnvio())).toList();}
 @Transactional public Config configurar(TipoEnvio canal,Configurar req){
  travar();plano.exigir("COMUNICACAO");var e=config(canal);if(req.versao()!=(e==null?0:e.versao))throw new ConflictException("A configuração mudou. Atualize.");
  if(req.ativo()||req.layoutId()!=null){var l=req.layoutId()==null?null:em.find(Layout.class,req.layoutId());if(l==null||!l.isAtivo()||l.getTipoEnvio()!=canal||l.getTipoLayout()==TipoLayout.EVENTO)throw new BadRequestException("Escolha um layout ativo deste canal.");}
  boolean novo=e==null;if(novo){e=new AniversarioConfig();e.versao=1;e.canal=canal;}e.ativo=req.ativo();e.layoutId=req.layoutId();if(novo)em.persist(e);em.flush();audit.registrar("CONFIGURAR","ANIVERSARIO",e.id,List.of("ativo","layoutId"));return new Config(canal,e.ativo,e.layoutId,e.versao,agendadorAtivo);
 }
 @Transactional(readOnly=true) public List<Autorizacao> autorizacoes(UUID pessoa){exigirPessoa(pessoa);return Arrays.stream(TipoEnvio.values()).map(c->{var e=autorizacao(pessoa,c);return new Autorizacao(pessoa,c,e!=null&&e.autorizado,e==null?null:e.fonte,e==null?null:e.registradoEm,e==null?0:e.versao);}).toList();}
 @Transactional public Autorizacao autorizar(UUID pessoa,TipoEnvio canal,Autorizar req){
  travar();exigirPessoa(pessoa);var e=autorizacao(pessoa,canal);if(req.versao()!=(e==null?0:e.versao))throw new ConflictException("A autorização mudou. Atualize.");boolean novo=e==null;if(novo){e=new AniversarioAutorizacao();e.versao=1;e.pessoaId=pessoa;e.canal=canal;}e.autorizado=req.autorizado();e.fonte=req.fonte().trim();e.registradoEm=Instant.now();if(novo)em.persist(e);em.flush();audit.registrar(req.autorizado()?"AUTORIZAR":"REVOGAR","ANIVERSARIO",e.id,List.of("autorizado","fonte"));return new Autorizacao(pessoa,canal,e.autorizado,e.fonte,e.registradoEm,e.versao);
 }
 @Transactional(readOnly=true) public List<UUID> candidatos(LocalDate hoje){
  // 29/02 é observado em 28/02 no ano não bissexto. Sem data de nascimento não entra.
  String dia="day(p.dataNascimento)=:dia";if(hoje.getMonthValue()==2&&hoje.getDayOfMonth()==28&&!hoje.isLeapYear())dia="day(p.dataNascimento) in (28,29)";
  var q=em.createQuery("select a.id from AniversarioAutorizacao a, Pessoa p where a.pessoaId=p.id and a.autorizado=true and p.dataNascimento<=:hoje and exists (select c.id from AniversarioConfig c, Layout l where c.canal=a.canal and c.ativo=true and c.layoutId=l.id and l.ativo=true and l.tipoEnvio=a.canal and l.tipoLayout<>:evento) and month(p.dataNascimento)=:mes and "+dia+" and not exists (select x.id from AniversarioExecucao x where x.pessoaId=a.pessoaId and x.canal=a.canal and x.ano=:ano) order by a.id",UUID.class).setParameter("hoje",hoje).setParameter("evento",TipoLayout.EVENTO).setParameter("mes",hoje.getMonthValue()).setParameter("ano",hoje.getYear());if(dia.contains(":dia"))q.setParameter("dia",hoje.getDayOfMonth());return q.setMaxResults(100).getResultList();
 }
 @Transactional public boolean preparar(UUID id,LocalDate hoje){
  var tenant=travar();if(!acesso.liberada(tenant)||!plano.permitida("COMUNICACAO"))return false;
  var a=em.find(AniversarioAutorizacao.class,id);if(a==null||!a.autorizado)return false;var cfg=config(a.canal);if(cfg==null||!cfg.ativo)return false;
  if(em.createQuery("select count(x) from AniversarioExecucao x where x.pessoaId=:p and x.canal=:c and x.ano=:ano",Long.class).setParameter("p",a.pessoaId).setParameter("c",a.canal).setParameter("ano",hoje.getYear()).getSingleResult()>0)return false;
  var p=em.find(Pessoa.class,a.pessoaId);if(p==null||!aniversario(p.getDataNascimento(),hoje))return false;
  var x=new AniversarioExecucao();x.pessoaId=a.pessoaId;x.canal=a.canal;x.ano=hoje.getYear();x.dia=hoje;x.status="SEM_CONTATO";
  var ds=comunicados.destinos(p,a.canal,EnviarPara.PESSOA,QuaisContatos.PRINCIPAL);
  boolean permitido=a.canal!=TipoEnvio.WHATSAPP||p.getVoluntario()!=null&&p.getVoluntario().isAutorizaWhatsapp();
  if(!ds.isEmpty()&&permitido){var r=comunicados.criar(new CriarRequest(a.canal,cfg.layoutId,null,EnviarPara.PESSOA,QuaisContatos.PRINCIPAL,List.of(a.pessoaId)),List.of());x.comunicadoId=r.id();x.status="NA_FILA";}
  em.persist(x);audit.registrar("AGENDAR","ANIVERSARIO",x.id,List.of("canal","ano","status"));return x.comunicadoId!=null;
 }
 public static boolean aniversario(LocalDate nascimento,LocalDate hoje){if(nascimento==null||nascimento.isAfter(hoje))return false;int dia=nascimento.getDayOfMonth();if(nascimento.getMonthValue()==2&&dia==29&&!hoje.isLeapYear())dia=28;return nascimento.getMonthValue()==hoje.getMonthValue()&&dia==hoje.getDayOfMonth();}
 private Tenant travar(){return tenants.bloquearParaCotas(TenantContext.get()).orElseThrow(()->new ResourceNotFoundException("Paróquia não encontrada."));}
 private Pessoa exigirPessoa(UUID id){var p=em.find(Pessoa.class,id);if(p==null)throw new ResourceNotFoundException("Pessoa não encontrada.");return p;}
 private AniversarioConfig config(TipoEnvio canal){return em.createQuery("select c from AniversarioConfig c where c.canal=:c",AniversarioConfig.class).setParameter("c",canal).getResultStream().findFirst().orElse(null);}
 private AniversarioAutorizacao autorizacao(UUID pessoa,TipoEnvio canal){return em.createQuery("select a from AniversarioAutorizacao a where a.pessoaId=:p and a.canal=:c",AniversarioAutorizacao.class).setParameter("p",pessoa).setParameter("c",canal).getResultStream().findFirst().orElse(null);}
}
