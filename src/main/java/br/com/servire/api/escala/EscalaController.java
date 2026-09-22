package br.com.servire.api.escala;

import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaResponse;
import br.com.servire.api.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de escalas (Fase 9, seção 46/47/109 do plano mestre) —
 * autenticado, mesma regra das demais rotas de negócio.
 *
 * <p><b>Deliberadamente fora do escopo desta versão</b> (a documentar em
 * "Próximos passos"): controle de faltas e disponibilidade do voluntário
 * (itens 11/12 referenciados pela seção 109) — não foram desenhados a
 * tempo nesta rodada conjunta das Fases 7/8/9; ficam para uma próxima
 * iteração, com entidades/campos próprios a definir depois.</p>
 */
@RestController
@RequestMapping("/escalas")
public class EscalaController {

    private final EscalaService escalaService;

    public EscalaController(EscalaService escalaService) {
        this.escalaService = escalaService;
    }

    @GetMapping
    public List<EscalaResponse> listar(@RequestParam(required = false) TipoEscala tipo,
                                        @RequestParam(required = false) StatusEscala status,
                                        @RequestParam(required = false) Integer ano,
                                        @RequestParam(required = false) Integer mes) {
        return escalaService.buscar(tipo, status, ano, mes).stream().map(EscalaResponse::de).toList();
    }

    @GetMapping("/{id}")
    public EscalaResponse buscarPorId(@PathVariable UUID id) {
        return EscalaResponse.de(escalaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<EscalaResponse> criar(@RequestBody @Valid EscalaRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser usuario) {
        Escala criada = escalaService.criar(request, usuario.usuarioId());
        return ResponseEntity.ok(EscalaResponse.de(criada));
    }

    @PutMapping("/{id}")
    public EscalaResponse atualizar(@PathVariable UUID id, @RequestBody @Valid EscalaRequest request) {
        return EscalaResponse.de(escalaService.atualizar(id, request));
    }

    @PostMapping("/{id}/finalizar")
    public EscalaResponse finalizar(@PathVariable UUID id) {
        return EscalaResponse.de(escalaService.finalizar(id));
    }

    @PostMapping("/{id}/cancelar")
    public EscalaResponse cancelar(@PathVariable UUID id) {
        return EscalaResponse.de(escalaService.cancelar(id));
    }

    @PostMapping("/{id}/reabrir")
    public EscalaResponse reabrir(@PathVariable UUID id) {
        return EscalaResponse.de(escalaService.reabrir(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        escalaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
