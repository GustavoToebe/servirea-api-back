package br.com.servire.api.site;
import org.springframework.stereotype.Component;
import br.com.servire.api.web.TooManyRequestsException;
import java.time.Instant;
import java.util.*;
/** Proteção local limitada a 10.000 origens; contagem não armazena conteúdo ou caminhos. */
@Component
public class SitePublicoLimiter {
 private final Map<String,Janela> janelas=new HashMap<>();
 public synchronized void consultar(String ip){long agora=Instant.now().getEpochSecond();var j=janelas.get(ip);if(j==null||agora-j.inicio>=60){if(janelas.size()>=10000){janelas.entrySet().removeIf(e->agora-e.getValue().inicio>=60);if(janelas.size()>=10000)throw new TooManyRequestsException("Tente novamente em instantes.");}j=new Janela(agora);janelas.put(ip,j);}if(++j.total>120)throw new TooManyRequestsException("Tente novamente em instantes.");}
 private static class Janela {final long inicio;int total;Janela(long inicio){this.inicio=inicio;}}
}
