package br.com.servire.api.integracao;

import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Módulo vem do handler resolvido, não do texto da URL; leituras preservam o histórico no downgrade. */
@Configuration
public class FuncionalidadesPlanoWeb implements WebMvcConfigurer {
    private final FuncionalidadesPlano funcionalidades;
    public FuncionalidadesPlanoWeb(FuncionalidadesPlano funcionalidades) {this.funcionalidades=funcionalidades;}
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if(!(handler instanceof HandlerMethod method) || java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) return true;
                String tipo=method.getBeanType().getName();String codigo=null;
                if(tipo.startsWith("br.com.servire.api.escala.") || tipo.endsWith("DisponibilidadeVoluntarioController")) codigo="ESCALAS";
                else if(tipo.startsWith("br.com.servire.api.evento.")) codigo="EVENTOS";
                else if(tipo.startsWith("br.com.servire.api.pastoral.")) codigo="PASTORAIS";
                else if(tipo.startsWith("br.com.servire.api.mural.")) codigo="MURAL";
                else if(tipo.startsWith("br.com.servire.api.tarefas.")) codigo="TAREFAS";
                else if(tipo.startsWith("br.com.servire.api.financeiro.")) codigo="FINANCEIRO";
                else if(tipo.startsWith("br.com.servire.api.comunicacao.")) codigo="COMUNICACAO";
                else if(tipo.startsWith("br.com.servire.api.pessoa.importacao.")) codigo="IMPORTACAO_PESSOAS";
                else if(tipo.endsWith("InscricaoController") && !tipo.endsWith("PublicInscricaoController")) codigo="INSCRICAO_PUBLICA";
                if(codigo!=null) funcionalidades.exigir(codigo);
                return true;
            }
        });
    }
}
