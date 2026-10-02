package br.com.servire.api.integracao;

import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Lista explícita da Central. Ausência, inclusive de snapshot, nunca libera uma função. */
@Service
public class FuncionalidadesPlano {
    public static final List<String> CODIGOS=List.of("ESCALAS","INSCRICAO_PUBLICA","EVENTOS","FINANCEIRO","COMUNICACAO","IMPORTACAO_PESSOAS","MURAL","TAREFAS","PASTORAIS","PORTAL_VOLUNTARIO","CALENDARIO");
    private final DireitosLocaisRepository direitos;
    public FuncionalidadesPlano(DireitosLocaisRepository direitos) {this.direitos=direitos;}
    @Transactional(readOnly=true)
    public List<String> liberadas() {
        UUID tenant=TenantContext.get();
        if(tenant==null || tenant.equals(new UUID(0,0))) return List.of();
        var local=direitos.findById(tenant).orElse(null);
        if(local==null) return List.of();
        var presentes=new HashSet<>(Arrays.asList(local.getFuncionalidades()));
        return CODIGOS.stream().filter(presentes::contains).toList();
    }
    public boolean permitida(String codigo) {return liberadas().contains(codigo);}
    public void exigir(String codigo) {
        if(!permitida(codigo)) throw new NaoContratada();
    }
    public static final class NaoContratada extends ApiException {
        NaoContratada() {super(HttpStatus.FORBIDDEN,"Esta funcionalidade não está incluída no plano. Consulte Minha conta ou o administrador.");}
        @Override public String getCodigo() {return "FUNCIONALIDADE_NAO_CONTRATADA";}
    }
}
