package br.com.servire.api.integracao;
import br.com.servire.api.tenant.*;
import br.com.servire.api.minhaconta.CotasService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import java.util.*;
/** Só a integração HMAC usa tenant explícito. O contexto precede a abertura da sessão Hibernate. */
@Service
public class ConsumoInstanciaService {
 private final TenantRepository tenants;private final DireitosLocaisRepository direitos;
 private final CotasService cotas;private final FuncionalidadesPlano funcionalidades;private final TransactionTemplate leitura;
 public ConsumoInstanciaService(TenantRepository tenants,DireitosLocaisRepository direitos,CotasService cotas,FuncionalidadesPlano funcionalidades,PlatformTransactionManager tm) {
  this.tenants=tenants;this.direitos=direitos;this.cotas=cotas;this.funcionalidades=funcionalidades;
  leitura=new TransactionTemplate(tm);leitura.setReadOnly(true);leitura.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);leitura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
 }
 public Resposta consultar(UUID tenant) {
  if(!tenants.existsById(tenant)) throw new IntegracaoException(HttpStatus.NOT_FOUND,"INSTANCIA_NAO_ENCONTRADA","Instância não encontrada.");
  UUID anterior=TenantContext.get();TenantContext.set(tenant);
  try {return leitura.execute(tx->{
   var d=direitos.findById(tenant).orElseThrow(()->new IntegracaoException(HttpStatus.NOT_FOUND,"INSTANCIA_NAO_ENCONTRADA","Instância não encontrada."));
   return new Resposta(1,tenant,d.getContratacaoId(),cotas.consumo(),funcionalidades.liberadas());
  });} finally {if(anterior==null) TenantContext.clear();else TenantContext.set(anterior);}
 }
 public record Resposta(int versaoContrato,UUID tenantId,UUID contratacaoId,CotasService.Consumo consumo,List<String> funcionalidades) { }
}
