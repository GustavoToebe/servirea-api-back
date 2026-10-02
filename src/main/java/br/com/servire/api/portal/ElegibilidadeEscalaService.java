package br.com.servire.api.portal;
import br.com.servire.api.escala.*;
import br.com.servire.api.voluntario.*;
import br.com.servire.api.web.ConflictException;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
/** Regras comuns a candidaturas e substituições, sempre dentro da transação do chamador. */
@Service
public class ElegibilidadeEscalaService {
 private final VoluntarioRepository voluntarios;
 @PersistenceContext private EntityManager em;
 public ElegibilidadeEscalaService(VoluntarioRepository voluntarios){this.voluntarios=voluntarios;}
 Voluntario validar(EscalaVaga vaga,UUID pessoa){
  var ctx=contexto(pessoa,vaga.getEvento().getData(),vaga.getEvento().getData());
  if(ctx.voluntario()!=null)em.lock(ctx.voluntario(),LockModeType.PESSIMISTIC_WRITE);
  String motivo=impedimento(vaga,ctx);if(motivo!=null)throw new ConflictException(motivo);
  return ctx.voluntario();
 }
    /** Quatro consultas por pessoa/período, reaproveitadas para as 30 vagas da página. */
    Contexto contexto(UUID pessoa,LocalDate de,LocalDate ate){
        var vol=voluntarios.findById(pessoa).orElse(null);
        var disponiveis=em.createQuery("select d from DisponibilidadeVoluntario d where d.voluntario.id=:pessoa",DisponibilidadeVoluntario.class).setParameter("pessoa",pessoa).getResultList();
        var bloqueios=em.createQuery("select i from Indisponibilidade i where i.voluntarioId=:pessoa and i.data between :de and :ate",Indisponibilidade.class).setParameter("pessoa",pessoa).setParameter("de",de).setParameter("ate",ate).getResultList();
        var ocupados=em.createQuery("select e.data,e.horario,e.id from EscalaVaga v join v.evento e where v.voluntario.id=:pessoa and e.referencia=false and e.escala.status=:status and e.data between :de and :ate",Object[].class)
                .setParameter("pessoa",pessoa).setParameter("status",StatusEscala.FINALIZADA).setParameter("de",de).setParameter("ate",ate).getResultList();
        return new Contexto(vol,disponiveis,bloqueios,ocupados);
    }
    String impedimento(EscalaVaga vaga,Contexto ctx){
        var vol=ctx.voluntario();if(vol==null||!vol.isAtivo())return "É necessário um voluntário ativo.";
        if(vol.getFuncoesHabilitadas()==null||!Arrays.asList(vol.getFuncoesHabilitadas()).contains(vaga.getFuncao()))return "Sua pessoa não está habilitada para esta função.";
        var e=vaga.getEvento();var periodo=periodo(e.getHorario());
        if(!ctx.disponiveis().isEmpty()&&ctx.disponiveis().stream().noneMatch(d->d.getPeriodo()==periodo&&(e.getData().equals(d.getData())||e.getData().getDayOfWeek()==d.getDiaSemana())))return "Horário fora da disponibilidade cadastrada.";
        if(ctx.bloqueios().stream().anyMatch(i->i.getData().equals(e.getData())&&(i.getPeriodo()==null||i.getPeriodo()==periodo)))return "Você está indisponível neste período.";
        if(ctx.ocupados().stream().anyMatch(v->v[2].equals(e.getId())||v[0].equals(e.getData())&&v[1].equals(e.getHorario())))return "Você já está alocado nesta celebração ou em outra no mesmo horário.";
        return null;
    }
    record Contexto(Voluntario voluntario,List<DisponibilidadeVoluntario> disponiveis,List<Indisponibilidade> bloqueios,List<Object[]> ocupados) {}
 private static Periodo periodo(LocalTime h){return h.isBefore(LocalTime.NOON)?Periodo.MANHA:h.isBefore(LocalTime.of(18,0))?Periodo.TARDE:Periodo.NOITE;}
}
