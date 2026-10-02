package br.com.servire.api.portal;
import br.com.servire.api.auth.*;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.*;
import br.com.servire.api.escala.StatusEscala;
import br.com.servire.api.evento.Evento;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
/** Consulta apenas a pessoa vinculada à conta; não dá acesso a fichas, outros voluntários ou rascunhos. */
@Service
public class PortalService {
 private final UsuarioTenantRepository vinculos;private final br.com.servire.api.integracao.FuncionalidadesPlano plano;
 @PersistenceContext private EntityManager em;
 public PortalService(UsuarioTenantRepository vinculos,br.com.servire.api.integracao.FuncionalidadesPlano plano){this.vinculos=vinculos;this.plano=plano;}
 public UUID pessoaAtual(){
  var a=SecurityContextHolder.getContext().getAuthentication();
  if(a==null||!(a.getPrincipal() instanceof AuthenticatedUser u)||u.suporte()||!Objects.equals(u.tenantId(),TenantContext.get())) throw new BadRequestException("Este acesso precisa de uma conta pessoal vinculada.");
  var v=vinculos.findByUsuario_IdAndTenant_Id(u.usuarioId(),u.tenantId()).orElseThrow(()->new ResourceNotFoundException("Vínculo não encontrado."));
  if(v.getStatus()!=UsuarioTenant.Status.ATIVO||v.getPessoaId()==null) return null;
  return v.getPessoaId();
 }
 @Transactional(readOnly=true) public Resposta consultar(LocalDate de,LocalDate ate){plano.exigir("PORTAL_VOLUNTARIO");UUID pessoa=pessoaAtual();return new Resposta(pessoa!=null,pessoa==null?List.of():compromissos(pessoa,de,ate));}
 public UUID pessoaAutorizada(UUID dependente,boolean responder){
  UUID atual=pessoaAtual();if(atual==null)throw new ResourceNotFoundException("Pessoa vinculada não encontrada.");
  if(dependente==null||dependente.equals(atual))return atual;
  var a=SecurityContextHolder.getContext().getAuthentication();
  if(a.getAuthorities().stream().noneMatch(p->p.getAuthority().equals("PERM_PORTAL_DEPENDENTES")))throw new ForbiddenException("Sem permissão para dependentes.");
  var relacoes=em.createQuery("select r from PessoaRelacao r where r.responsavel.id=:r and r.voluntario.id=:d and r.portalConsulta=true",br.com.servire.api.pessoa.PessoaRelacao.class).setParameter("r",atual).setParameter("d",dependente).getResultList();
  if(relacoes.isEmpty()||(responder&&!relacoes.getFirst().isPortalResposta()))throw new ResourceNotFoundException("Dependente não autorizado.");
  return dependente;
 }
 @Transactional(readOnly=true) public Resposta consultarDependente(UUID dependente,LocalDate de,LocalDate ate){plano.exigir("PORTAL_VOLUNTARIO");return new Resposta(true,compromissos(pessoaAutorizada(dependente,false),de,ate));}
 public List<Compromisso> compromissos(UUID pessoa,LocalDate de,LocalDate ate){
  if(de==null||ate==null||ate.isBefore(de)||java.time.temporal.ChronoUnit.DAYS.between(de,ate)>366) throw new BadRequestException("Informe um período de até 366 dias.");
  List<Compromisso> itens=new ArrayList<>();
  var vagas=em.createQuery("select v.id,e.data,e.horario,e.celebracao,v.funcao,v.resposta,v.respostaVersao,v.respostaEm from EscalaVaga v join v.evento e join e.escala s where v.voluntario.id=:pessoa and s.status=:status and e.referencia=false and e.data between :de and :ate",Object[].class)
   .setParameter("pessoa",pessoa).setParameter("status",StatusEscala.FINALIZADA).setParameter("de",de).setParameter("ate",ate).setMaxResults(501).getResultList();
  for(var v:vagas){var inicio=((LocalDate)v[1]).atTime((LocalTime)v[2]);itens.add(new Compromisso("escala-"+v[0],"ESCALA",(String)v[3],inicio,inicio.plusHours(1),null,String.valueOf(v[4]),(UUID)v[0],(br.com.servire.api.escala.RespostaParticipacao)v[5],((Number)v[6]).longValue(),(Instant)v[7],inicio.atZone(ZoneId.of("America/Sao_Paulo")).toInstant()));}
  var eventos=em.createQuery("select e.id,e.titulo,e.inicio,e.termino,e.localNome from Evento e, EventoInscricao i where i.eventoId=e.id and i.pessoa.id=:pessoa and e.situacao=:status and e.inicio>=:de and e.inicio<:ate",Object[].class)
   .setParameter("pessoa",pessoa).setParameter("status",Evento.Situacao.PUBLICADO).setParameter("de",de.atStartOfDay()).setParameter("ate",ate.plusDays(1).atStartOfDay()).setMaxResults(501).getResultList();
  for(var e:eventos){var inicio=(LocalDateTime)e[2];itens.add(new Compromisso("evento-"+e[0],"EVENTO",(String)e[1],inicio,e[3]!=null?(LocalDateTime)e[3]:inicio.plusHours(1),(String)e[4],null,null,null,null,null,null));}
  if(itens.size()>500) throw new BadRequestException("Há muitos compromissos. Reduza o período.");
  itens.sort(Comparator.comparing(Compromisso::inicio).thenComparing(Compromisso::id));return List.copyOf(itens);
 }
 public record Compromisso(String id,String tipo,String titulo,LocalDateTime inicio,LocalDateTime termino,String local,String funcao,UUID vagaId,br.com.servire.api.escala.RespostaParticipacao resposta,Long versao,Instant respondidoEm,Instant prazoResposta) { }
 public record Resposta(boolean vinculado,List<Compromisso> compromissos) { }
}
