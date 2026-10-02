package br.com.servire.api.monitoramento;
import io.micrometer.core.instrument.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Exportação restrita: agregados operacionais, sem URI, paróquia, usuário ou conteúdo. */ @RestController public class MetricasController {
    private final MeterRegistry registry;
    public MetricasController(MeterRegistry registry) {
        this.registry=registry;
    }
    @GetMapping(value="/monitoramento/metrics",produces="text/plain; version=0.0.4; charset=utf-8") @PreAuthorize("hasRole('MONITORAMENTO')")
    public ResponseEntity<String> metricas() {
        StringBuilder b=new StringBuilder();
        timers(b,"http.server.requests","ecossistema_http_servidor");
        timers(b,"http.client.requests","ecossistema_http_cliente");
        Map<String,String> gauges=Map.of("jvm.memory.used","ecossistema_jvm_memoria_bytes","process.uptime","ecossistema_process_uptime_segundos","process.cpu.usage","ecossistema_process_cpu_fracao","hikaricp.connections.active","ecossistema_banco_conexoes_ativas","hikaricp.connections.idle","ecossistema_banco_conexoes_ociosas","hikaricp.connections.pending","ecossistema_banco_conexoes_pendentes","hikaricp.connections.max","ecossistema_banco_conexoes_maximas");
        gauges.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e-> {
            var gs=registry.find(e.getKey()).gauges();if(!gs.isEmpty()) {
                double v=gs.stream().mapToDouble(Gauge::value).sum();if(Double.isFinite(v))b.append("# TYPE ").append(e.getValue()).append(" gauge\n").append(e.getValue()).append(' ').append(v).append('\n');
            }
        }
        );
        fila(b);
        return ResponseEntity.ok().header("Cache-Control","no-store").body(b.toString());
    }
    /** Fila de comunicados: contadores por canal/resultado e reservas em curso, sem dado de negócio. */
    private void fila(StringBuilder b) {
        final char fim=(char)10;
        var grupos=new TreeMap<String,Double>();
        for(var c:registry.find("fila.envios").counters()) {
            String canal=c.getId().getTag("canal"),resultado=c.getId().getTag("resultado");
            if(!Set.of("EMAIL","WHATSAPP").contains(canal)||!Set.of("enviado","falha_tentativa","falha_definitiva","aguardando_cota").contains(resultado))continue;
            grupos.merge("{canal=\""+canal+"\",resultado=\""+resultado+"\"}",c.count(),Double::sum);
        }
        b.append("# TYPE ecossistema_fila_envios_total counter").append(fim);
        grupos.forEach((k,v)->b.append("ecossistema_fila_envios_total").append(k).append(' ').append((long)(double)v).append(fim));
        var gs=registry.find("fila.reservas.em.curso").gauges();
        if(!gs.isEmpty()) {
            double v=gs.stream().mapToDouble(Gauge::value).sum();
            if(Double.isFinite(v))b.append("# TYPE ecossistema_fila_reservas_em_curso gauge").append(fim).append("ecossistema_fila_reservas_em_curso ").append((long)v).append(fim);
        }
    }
    private void timers(StringBuilder b,String origem,String nome) {
        var grupos=new TreeMap<String,double[]>();
        for(var t:registry.find(origem).timers()) {
            String metodo=t.getId().getTag("method"),status=t.getId().getTag("status");
            if(metodo==null||!Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS").contains(metodo))metodo="OUTRO";
            String classe=status!=null&&status.matches("[1-5][0-9]{2}")?status.charAt(0)+"xx":"desconhecido";
            String chave="{metodo=\""+metodo+"\",status=\""+classe+"\"}";
            double[] v=grupos.computeIfAbsent(chave,k->new double[2]);
            v[0]+=t.count();
            v[1]+=t.totalTime(TimeUnit.SECONDS);
        }
        b.append("# TYPE ").append(nome).append("_duracao_seconds summary\n");
        grupos.forEach((k,v)-> {
            b.append(nome).append("_duracao_seconds_count").append(k).append(' ').append((long)v[0]).append('\n');b.append(nome).append("_duracao_seconds_sum").append(k).append(' ').append(v[1]).append('\n');
        }
        );
    }
}
