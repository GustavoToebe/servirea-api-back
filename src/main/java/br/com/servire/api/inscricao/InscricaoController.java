package br.com.servire.api.inscricao;

import br.com.servire.api.inscricao.dto.InscricaoAtualizarRequest;
import br.com.servire.api.inscricao.dto.InscricaoRejeitarRequest;
import br.com.servire.api.inscricao.dto.InscricaoResponse;
import br.com.servire.api.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * Fila de aprovação de inscrições (Fase 8, seção 44/108 do plano mestre) —
 * requer autenticação (não está em {@code ROTAS_PUBLICAS}).
 *
 * <p>Não há uma checagem de {@code role} aqui além de "autenticado no
 * tenant" — a mesma simplificação já documentada/aceita para o resto da
 * aplicação nesta rodada (roles/permissões finas em
 * {@code authorizeHttpRequests} continuam na lista de "próximos passos",
 * decisão explícita do usuário de priorizar velocidade sobre isso por
 * enquanto).</p>
 */
@RestController
@RequestMapping("/inscricoes")
public class InscricaoController {

    private final InscricaoService inscricaoService;

    public InscricaoController(InscricaoService inscricaoService) {
        this.inscricaoService = inscricaoService;
    }

    @GetMapping
    public List<InscricaoResponse> listar(@RequestParam(required = false) StatusInscricao status) {
        return inscricaoService.buscar(status).stream().map(InscricaoResponse::de).toList();
    }

    @GetMapping("/{id}")
    public InscricaoResponse buscarPorId(@PathVariable UUID id) {
        return InscricaoResponse.de(inscricaoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public InscricaoResponse atualizar(@PathVariable UUID id, @RequestBody @Valid InscricaoAtualizarRequest request) {
        return InscricaoResponse.de(inscricaoService.atualizarPendente(id, request));
    }

    @PostMapping("/{id}/aprovar")
    public InscricaoResponse aprovar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return InscricaoResponse.de(inscricaoService.aprovar(id, usuario.usuarioId()));
    }

    @PostMapping("/{id}/rejeitar")
    public InscricaoResponse rejeitar(@PathVariable UUID id, @RequestBody @Valid InscricaoRejeitarRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser usuario) {
        return InscricaoResponse.de(inscricaoService.rejeitar(id, request.motivo(), usuario.usuarioId()));
    }
}
