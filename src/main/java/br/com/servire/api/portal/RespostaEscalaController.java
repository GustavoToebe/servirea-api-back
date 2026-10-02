package br.com.servire.api.portal;

import br.com.servire.api.portal.dto.RespostaEscalaDtos.*;
import br.com.servire.api.escala.RespostaParticipacao;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

@RestController
public class RespostaEscalaController {
    private final RespostaEscalaService service;
    public RespostaEscalaController(RespostaEscalaService service){this.service=service;}
    @PutMapping("/portal/vagas/{id}/resposta")
    @PreAuthorize("hasAuthority('PERM_PORTAL_VOLUNTARIO') and hasAuthority('PERM_PORTAL_RESPONDER')")
    public Atual responder(@PathVariable UUID id,@Valid @RequestBody Responder req){return service.responder(id,req);}
    @GetMapping("/portal/vagas/{id}/respostas")
    @PreAuthorize("hasAuthority('PERM_PORTAL_VOLUNTARIO')")
    public Pagina<Historico> historico(@PathVariable UUID id,@RequestParam(defaultValue="0") int pagina){return service.historico(id,pagina);}
    @GetMapping("/escalas/{id}/respostas")
    @PreAuthorize("hasAuthority('PERM_VAGA_RESPOSTA_LER')")
    public Pagina<Item> coordenacao(@PathVariable UUID id,@RequestParam(defaultValue="0") int pagina,@RequestParam(required=false) RespostaParticipacao resposta){return service.consultarCoordenacao(id,pagina,resposta);}
}
