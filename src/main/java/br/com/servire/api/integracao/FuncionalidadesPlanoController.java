package br.com.servire.api.integracao;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
@RestController @RequestMapping("/funcionalidades-plano")
public class FuncionalidadesPlanoController {
    private final FuncionalidadesPlano funcionalidades;
    public FuncionalidadesPlanoController(FuncionalidadesPlano funcionalidades) {this.funcionalidades=funcionalidades;}
    @GetMapping @PreAuthorize("isAuthenticated()")
    public List<String> liberadas() {return funcionalidades.liberadas();}
}
