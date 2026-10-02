package br.com.servire.api.comunicacao;
import br.com.servire.api.tenant.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import java.time.*;
/** Uma página de paróquias por rodada; transação separada por destinatário, fila preexistente faz o envio. */
@Component
public class AniversarioJob {
 private final TenantRepository tenants;private final AniversarioService service;private final boolean ativo;private int pagina;
 public AniversarioJob(TenantRepository tenants,AniversarioService service,@Value("${servire.aniversarios.ativo:false}") boolean ativo){this.tenants=tenants;this.service=service;this.ativo=ativo;}
 @Scheduled(fixedDelay=60000) public void executar(){if(!ativo)return;var hoje=LocalDate.now(ZoneId.of("America/Sao_Paulo"));if(LocalTime.now(ZoneId.of("America/Sao_Paulo")).isBefore(LocalTime.of(8,0)))return;var lote=tenants.loteDaFila(PageRequest.of(pagina,20));pagina=lote.size()<20?0:pagina+1;for(var t:lote){TenantContext.set(t.getId());try{for(var id:service.candidatos(hoje)){try{service.preparar(id,hoje);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(AniversarioJob.class).warn("Não foi possível preparar aniversário; paróquia={}, registro={}",t.getId(),id);}}}finally{TenantContext.clear();}}}
}
