package br.com.servire.api.monitoramento;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import org.springframework.mock.web.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.security.core.context.SecurityContextHolder;
class MetricasTest {
    private final String segredo="teste_exclusivo_de_metricas_1234567890";
    @AfterEach void limpar() {
        SecurityContextHolder.clearContext();
    }
    @Test void coletaAgregaRotasSemDadosPessoais() {
        var r=new SimpleMeterRegistry();
        r.timer("http.server.requests","method","GET","status","200","uri","/pessoas/segredo").record(100,TimeUnit.MILLISECONDS);
        r.timer("http.server.requests","method","GET","status","201","uri","/outra").record(200,TimeUnit.MILLISECONDS);
        String s=new MetricasController(r).metricas().getBody();
        assertThat(s).contains("_count{metodo=\"GET\",status=\"2xx\"} 2","_sum").doesNotContain("pessoas","segredo","outra","uri=");
    }
    @Test void exportaFilaPorCanalEResultadoSemDadosDeNegocio() {
        var r=new SimpleMeterRegistry();
        r.counter("fila.envios","canal","EMAIL","resultado","enviado").increment(3);
        r.counter("fila.envios","canal","WHATSAPP","resultado","falha_tentativa").increment(2);
        r.counter("fila.envios","canal","WHATSAPP","resultado","aguardando_cota").increment();
        r.counter("fila.envios","canal","pessoa@x.com","resultado","enviado").increment(9);
        r.counter("fila.envios","canal","EMAIL","resultado","conteudo secreto").increment(9);
        java.util.concurrent.atomic.AtomicInteger emCurso=new java.util.concurrent.atomic.AtomicInteger(2);
        r.gauge("fila.reservas.em.curso",emCurso);
        String s=new MetricasController(r).metricas().getBody();
        assertThat(s).contains("ecossistema_fila_envios_total{canal=\"EMAIL\",resultado=\"enviado\"} 3",
            "ecossistema_fila_envios_total{canal=\"WHATSAPP\",resultado=\"falha_tentativa\"} 2",
            "ecossistema_fila_envios_total{canal=\"WHATSAPP\",resultado=\"aguardando_cota\"} 1",
            "ecossistema_fila_reservas_em_curso 2").doesNotContain("pessoa@x.com","secreto");
    }
    @Test void filtroAceitaSomenteColetaERestauraContexto()throws Exception {
        var req=new MockHttpServletRequest("GET","/monitoramento/metrics");
        req.setServletPath("/monitoramento/metrics");
        req.addHeader("Authorization","Monitoring "+segredo);
        var res=new MockHttpServletResponse();
        new MonitoramentoFilter(segredo).doFilter(req,res,(a,b)->assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MONITORAMENTO"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void semConfiguracaoOuComTokenErradoNaoColeta()throws Exception {
        for(String token:new String[] {
            "",segredo
        }
        ) {
            var req=new MockHttpServletRequest("GET","/monitoramento/metrics");
            req.setServletPath("/monitoramento/metrics");
            req.addHeader("Authorization","Bearer qualquer");
            var res=new MockHttpServletResponse();
            new MonitoramentoFilter(token).doFilter(req,res,(a,b)-> {
                throw new AssertionError("não deve encaminhar");
            }
            );
            assertThat(res.getStatus()).isEqualTo(401);
        }
    }
    @Test void tokenDeColetaNaoAutenticaNegocio()throws Exception {
        var req=new MockHttpServletRequest("GET","/clientes");
        req.setServletPath("/clientes");
        req.addHeader("Authorization","Monitoring "+segredo);
        new MonitoramentoFilter(segredo).doFilter(req,new MockHttpServletResponse(),(a,b)->assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }
    @Test void postNaoEhColeta()throws Exception {
        var req=new MockHttpServletRequest("POST","/monitoramento/metrics");
        req.setServletPath("/monitoramento/metrics");
        req.addHeader("Authorization","Monitoring "+segredo);
        var res=new MockHttpServletResponse();
        new MonitoramentoFilter(segredo).doFilter(req,res,(a,b)-> {
            throw new AssertionError();
        }
        );
        assertThat(res.getStatus()).isEqualTo(401);
    }
}
