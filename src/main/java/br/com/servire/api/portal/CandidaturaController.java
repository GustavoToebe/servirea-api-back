package br.com.servire.api.portal;

import br.com.servire.api.portal.dto.CandidaturaDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import java.util.UUID;

@RestController
public class CandidaturaController {
    private final CandidaturaService service;
    public CandidaturaController(CandidaturaService service){this.service=service;}
    @GetMapping("/portal/vagas-abertas") @PreAuthorize("hasAuthority('PERM_PORTAL_VOLUNTARIO')")
    public Pagina<Vaga> vagas(@RequestParam LocalDate de,@RequestParam LocalDate ate,@RequestParam(defaultValue="0") int pagina){return service.vagas(de,ate,pagina);}
    @GetMapping("/portal/candidaturas") @PreAuthorize("hasAuthority('PERM_PORTAL_VOLUNTARIO')")
    public Pagina<Pedido> minhas(@RequestParam(defaultValue="0") int pagina){return service.minhas(pagina);}
    @PostMapping("/portal/vagas/{id}/candidaturas") @PreAuthorize("hasAuthority('PERM_PORTAL_CANDIDATAR')")
    public Pedido candidatar(@PathVariable UUID id,@Valid @RequestBody Versao req){return service.candidatar(id,req);}
    @PutMapping("/portal/candidaturas/{id}/desistencia") @PreAuthorize("hasAuthority('PERM_PORTAL_CANDIDATAR')")
    public Pedido desistir(@PathVariable UUID id,@Valid @RequestBody Versao req){return service.desistir(id,req);}
    @GetMapping("/escalas/{id}/candidaturas") @PreAuthorize("hasAuthority('PERM_VAGA_CANDIDATURA_LER')")
    public Pagina<Pedido> coordenacao(@PathVariable UUID id,@RequestParam(defaultValue="0") int pagina){return service.coordenacao(id,pagina);}
    @PutMapping("/escalas/candidaturas/{id}/decisao") @PreAuthorize("hasAuthority('PERM_VAGA_CANDIDATURA_DECIDIR')")
    public Pedido decidir(@PathVariable UUID id,@Valid @RequestBody Decisao req){return service.decidir(id,req);}
}
