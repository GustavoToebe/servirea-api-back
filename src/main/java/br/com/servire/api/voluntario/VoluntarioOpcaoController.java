package br.com.servire.api.voluntario;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/voluntarios/opcoes")public class VoluntarioOpcaoController {
    private final VoluntarioOpcaoService service;
    public VoluntarioOpcaoController(VoluntarioOpcaoService service) {
        this.service=service;
    }
    @GetMapping @PreAuthorize("hasAnyAuthority('PERM_PESSOA','PERM_ESCALA','PERM_VAGA')")public VoluntarioOpcaoService.Pagina buscar(@RequestParam(defaultValue="")String nome,@RequestParam(required=false)TipoVoluntario tipo,@RequestParam(defaultValue="0")int pagina) {
        return service.buscar(nome,tipo,pagina);
    }
    @PostMapping("/ids") @PreAuthorize("hasAnyAuthority('PERM_PESSOA','PERM_ESCALA','PERM_VAGA')")public List<VoluntarioOpcaoService.Opcao> resolver(@Valid @RequestBody VoluntarioOpcaoService.Resolver r) {
        return service.resolver(r.ids());
    }
}
