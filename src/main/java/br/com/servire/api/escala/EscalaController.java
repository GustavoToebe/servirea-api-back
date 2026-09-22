package br.com.servire.api.escala;

import br.com.servire.api.escala.dto.AlocacaoVagaRequest;
import br.com.servire.api.escala.dto.CandidatoResponse;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaResponse;
import br.com.servire.api.escala.dto.EscalaVagaResponse;
import br.com.servire.api.escala.dto.PresencaRequest;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.voluntario.FuncaoEscala;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * <p><b>Permissões (seção 31, Fase 11):</b> leitura exige
 * {@code ESCALA_READ}; escrita (criar/atualizar/finalizar/cancelar/
 * reabrir/excluir/registrar presença) exige {@code ESCALA_WRITE}.</p>
 *
 * <p><b>Controle de faltas (Fase 11, seção 131.5 item 11):</b>
 * {@code PATCH .../vagas/{vagaId}/presenca}, implementado nesta rodada —
 * ver {@link Presenca}/{@link EscalaVaga#getPresenca()}.</p>
 *
 * <p>Picker de candidatos (seção 49): {@code GET /escalas/{eventoId}/candidatos}
 * — ver {@link EscalaService#listarCandidatos}. Alocação pontual:
 * {@code PATCH /escalas/vagas/{vagaId}} — ver {@link EscalaService#alocarVaga}.</p>
 */
@RestController
@RequestMapping("/escalas")
public class EscalaController {

    private final EscalaService escalaService;

    public EscalaController(EscalaService escalaService) {
        this.escalaService = escalaService;
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_READ')")
    @GetMapping
    public List<EscalaResponse> listar(@RequestParam(required = false) TipoEscala tipo,
                                        @RequestParam(required = false) StatusEscala status,
                                        @RequestParam(required = false) Integer ano,
                                        @RequestParam(required = false) Integer mes) {
        return escalaService.buscar(tipo, status, ano, mes).stream().map(EscalaResponse::de).toList();
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_READ')")
    @GetMapping("/{id}")
    public EscalaResponse buscarPorId(@PathVariable UUID id) {
        return EscalaResponse.de(escalaService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_READ')")
    @GetMapping("/{eventoId}/candidatos")
    public List<CandidatoResponse> listarCandidatos(@PathVariable UUID eventoId,
                                                     @RequestParam FuncaoEscala funcao) {
        return escalaService.listarCandidatos(eventoId, funcao);
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_WRITE')")
    @PostMapping
    public ResponseEntity<EscalaResponse> criar(@RequestBody @Valid EscalaRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser usuario) {
        Escala criada = escalaService.criar(request, usuario.usuarioId());
        return ResponseEntity.ok(EscalaResponse.de(criada));
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_WRITE')")
    @PutMapping("/{id}")
    public EscalaResponse atualizar(@PathVariable UUID id, @RequestBody @Valid EscalaRequest request) {
        return EscalaResponse.de(escalaService.atualizar(id, request));
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_WRITE')")
    @PostMapping("/{id}/finalizar")
    public EscalaResponse finalizar(@PathVariable UUID id) {
        return EscalaResponse.de(escalaService.finalizar(id));
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_WRITE')")
    @PostMapping("/{id}/cancelar")
    public EscalaResponse cancelar(@PathVariable UUID id) {
        return EscalaResponse.de(escalaService.cancelar(id));
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_WRITE')")
    @PostMapping("/{id}/reabrir")
    public EscalaResponse reabrir(@PathVariable UUID id) {
        return EscalaResponse.de(escalaService.reabrir(id));
    }

    @PreAuthorize("hasAuthority('PERM_ESCALA_WRITE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        escalaService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    /** Controle de faltas (Fase 11, seção 131.5 item 11) — ver {@link EscalaService#registrarPresenca}. */
    @PreAuthorize("hasAuthority('PERM_ESCALA_WRITE')")
    @PatchMapping("/vagas/{vagaId}/presenca")
    public EscalaVagaResponse registrarPresenca(@PathVariable UUID vagaId, @RequestBody @Valid PresencaRequest request) {
        return EscalaVagaResponse.de(escalaService.registrarPresenca(vagaId, request.presenca()));
    }

    /** Aloca ou desaloca o voluntário desta vaga — complemento do picker (seção 49). */
    @PreAuthorize("hasAuthority('PERM_ESCALA_WRITE')")
    @PatchMapping("/vagas/{vagaId}")
    public EscalaVagaResponse alocarVaga(@PathVariable UUID vagaId, @RequestBody AlocacaoVagaRequest request) {
        return EscalaVagaResponse.de(escalaService.alocarVaga(vagaId, request.voluntarioId()));
    }
}
