package br.com.servire.api.checkin;

import br.com.servire.api.checkin.CheckinDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Check-in por encontro (F15): gestão pela coordenação e registro pela própria pessoa escalada. */
@RestController
public class CheckinController {
    private final CheckinService service;

    public CheckinController(CheckinService service) {
        this.service = service;
    }

    @PostMapping("/escalas/eventos/{id}/checkin")
    @PreAuthorize("hasAuthority('PERM_CHECKIN_GERENCIAR')")
    public Aberto abrir(@PathVariable UUID id, @Valid @RequestBody Abrir req) {
        return service.abrir(id, req.minutos());
    }

    @DeleteMapping("/escalas/eventos/{id}/checkin")
    @PreAuthorize("hasAuthority('PERM_CHECKIN_GERENCIAR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void encerrar(@PathVariable UUID id) {
        service.encerrar(id);
    }

    @GetMapping("/escalas/eventos/{id}/checkin")
    @PreAuthorize("hasAuthority('PERM_CHECKIN')")
    public Estado estado(@PathVariable UUID id) {
        return service.estado(id);
    }

    @PostMapping("/portal/checkin")
    @PreAuthorize("hasAuthority('PERM_PORTAL_VOLUNTARIO')")
    public Resultado registrar(@Valid @RequestBody Registrar req) {
        return service.registrar(req.token());
    }
}
