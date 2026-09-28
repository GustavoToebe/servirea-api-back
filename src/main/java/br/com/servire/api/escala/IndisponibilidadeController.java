package br.com.servire.api.escala;

import br.com.servire.api.escala.dto.IndisponibilidadeDtos.ApoioEscala;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.MesRequest;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.MesResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Indisponibilidades do mês e apoio à montagem da mensal (PLANO-007). */
@RestController
@RequestMapping("/escalas")
public class IndisponibilidadeController {

    private final IndisponibilidadeService service;

    public IndisponibilidadeController(IndisponibilidadeService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA')")
    @GetMapping("/indisponibilidades")
    public MesResponse buscar(@RequestParam int ano, @RequestParam int mes) {
        return service.buscar(ano, mes);
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_ALTERAR')")
    @PutMapping("/indisponibilidades")
    public MesResponse salvar(@RequestParam int ano, @RequestParam int mes, @RequestBody @Valid MesRequest request) {
        return service.salvar(ano, mes, request);
    }

    @PreAuthorize("hasAnyAuthority('PERM_ESCALA','PERM_VAGA')")
    @GetMapping("/{id}/apoio")
    public ApoioEscala apoio(@PathVariable UUID id) {
        return service.apoio(id);
    }
}
