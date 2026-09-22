package br.com.servire.api.voluntario;

import br.com.servire.api.voluntario.dto.DisponibilidadeVoluntarioRequest;
import br.com.servire.api.voluntario.dto.DisponibilidadeVoluntarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Disponibilidade do voluntário (Fase 11, seção 131.5 item 12 do plano
 * mestre) — sub-recurso de voluntário, mesmas permissões de
 * {@code VOLUNTARIO_READ}/{@code VOLUNTARIO_WRITE} usadas pelo resto do
 * módulo (não existe uma permissão conceitual própria para isto na seção
 * 31 do plano mestre, e criar uma só para este sub-recurso seria
 * over-engineering).
 */
@RestController
@RequestMapping("/voluntarios/{voluntarioId}/disponibilidades")
public class DisponibilidadeVoluntarioController {

    private final DisponibilidadeVoluntarioService disponibilidadeService;

    public DisponibilidadeVoluntarioController(DisponibilidadeVoluntarioService disponibilidadeService) {
        this.disponibilidadeService = disponibilidadeService;
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_READ')")
    @GetMapping
    public List<DisponibilidadeVoluntarioResponse> listar(@PathVariable UUID voluntarioId) {
        return disponibilidadeService.listar(voluntarioId).stream().map(DisponibilidadeVoluntarioResponse::de).toList();
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")
    @PostMapping
    public ResponseEntity<DisponibilidadeVoluntarioResponse> criar(@PathVariable UUID voluntarioId,
                                                                    @RequestBody @Valid DisponibilidadeVoluntarioRequest request) {
        DisponibilidadeVoluntario criada = disponibilidadeService.criar(voluntarioId, request);
        return ResponseEntity.ok(DisponibilidadeVoluntarioResponse.de(criada));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID voluntarioId, @PathVariable UUID id) {
        disponibilidadeService.excluir(voluntarioId, id);
        return ResponseEntity.noContent().build();
    }
}
