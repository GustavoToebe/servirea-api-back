package br.com.servire.api.escala;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class IndisponibilidadeMesService {
 private final TenantRepository tenants;@PersistenceContext private EntityManager em;
 public IndisponibilidadeMesService(TenantRepository tenants){this.tenants=tenants;}
 @Transactional(readOnly=true) public long versao(int ano,int mes){return em.createQuery("select m.versao from IndisponibilidadeMes m where m.ano=:ano and m.mes=:mes",Long.class).setParameter("ano",ano).setParameter("mes",mes).getResultStream().findFirst().orElse(0L);}
 @Transactional public void avancar(int ano,int mes,Long esperada){
  tenants.bloquearParaCotas(TenantContext.get()).orElseThrow(()->new ResourceNotFoundException("Paróquia não encontrada."));
  var m=em.createQuery("select m from IndisponibilidadeMes m where m.ano=:ano and m.mes=:mes",IndisponibilidadeMes.class).setParameter("ano",ano).setParameter("mes",mes).getResultStream().findFirst().orElse(null);
  long atual=m==null?0:m.versao;if(esperada==null||esperada!=atual)throw new ConflictException("O mês mudou. Recarregue antes de salvar; suas alterações não foram aplicadas.");
  if(m==null){m=new IndisponibilidadeMes();m.ano=ano;m.mes=mes;em.persist(m);}m.versao=atual+1;em.flush();
 }
}
