package br.com.servire.api.relatorios;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
@RestController @RequestMapping("/indicadores/participacao") public class IndicadoresController {
    private final IndicadoresService service;
    public IndicadoresController(IndicadoresService service) {
        this.service=service;
    }
    @GetMapping @PreAuthorize("hasAuthority('PERM_INDICADORES') and hasAuthority('PERM_ESCALA')")public IndicadoresService.Pagina consultar(@RequestParam LocalDate de,@RequestParam LocalDate ate,@RequestParam(defaultValue="")String busca,@RequestParam(defaultValue="0")int pagina) {
        return service.consultar(de,ate,busca,pagina);
    }
}
