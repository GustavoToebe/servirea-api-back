package br.com.servire.api.inscricao;

import br.com.servire.api.inscricao.dto.InscricaoAtualizarRequest;
import br.com.servire.api.inscricao.dto.InscricaoRejeitarRequest;
import br.com.servire.api.inscricao.dto.InscricaoResponse;
import br.com.servire.api.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * <p><b>Permissões (seção 31, Fase 11):</b> leitura exige
 * {@code INSCRICAO_READ}; editar/aprovar/rejeitar exigem
 * {@code INSCRICAO_APPROVE} — a seção 31 não define uma
 * {@code INSCRICAO_WRITE} própria, então editar os dados de uma inscrição
 * ainda pendente (que só um COORDENADOR/ADMIN faz, tipicamente já revisando
 * para aprovar) também usa {@code INSCRICAO_APPROVE} (decisão explícita,
 * documentada aqui por não estar óbvia na lista original da seção 31).</p>
 */
@RestController
@RequestMapping("/inscricoes")
public class InscricaoController {

    private final InscricaoService inscricaoService;
    private final br.com.servire.api.pessoa.Duplicidades duplicidades;

    public InscricaoController(InscricaoService inscricaoService, br.com.servire.api.pessoa.Duplicidades duplicidades) {
        this.inscricaoService = inscricaoService;
        this.duplicidades = duplicidades;
    }

    @PreAuthorize("hasAuthority('PERM_INSCRICAO')")
    @GetMapping
    public List<InscricaoResponse> listar(@RequestParam(required = false) StatusInscricao status) {
        return inscricaoService.buscar(status).stream().map(InscricaoResponse::deAutorizada).toList();
    }

    @PreAuthorize("hasAuthority('PERM_INSCRICAO')")
    @GetMapping("/{id}")
    public InscricaoResponse buscarPorId(@PathVariable UUID id) {
        return InscricaoResponse.deAutorizada(inscricaoService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('PERM_INSCRICAO')")
    @GetMapping("/{id}/duplicidades")
    public List<br.com.servire.api.pessoa.dto.DuplicidadeResponse> verificarDuplicidades(@PathVariable UUID id) {
        var inscricao = inscricaoService.buscarPorId(id);
        var req = new br.com.servire.api.pessoa.dto.DuplicidadeRequest(
                null,
                inscricao.getNomeCompleto(),
                inscricao.getCpf(),
                inscricao.getDataNascimento(),
                inscricao.getTelefones().stream().map(br.com.servire.api.inscricao.InscricaoTelefone::getNumero).toList(),
                inscricao.getResponsaveis().stream().map(br.com.servire.api.inscricao.InscricaoResponsavel::getNome).toList()
        );
        return duplicidades.verificar(req);
    }

    @PreAuthorize("hasAuthority('PERM_INSCRICAO_ALTERAR')")
    @PutMapping("/{id}")
    public InscricaoResponse atualizar(@PathVariable UUID id, @RequestBody @Valid InscricaoAtualizarRequest request) {
        return InscricaoResponse.deAutorizada(inscricaoService.atualizarPendente(id, request));
    }

    @PreAuthorize("hasAuthority('PERM_INSCRICAO_APROVAR')")
    @PostMapping("/{id}/aprovar")
    public InscricaoResponse aprovar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser usuario) {
        return InscricaoResponse.deAutorizada(inscricaoService.aprovar(id, usuario.usuarioId()));
    }

    @PreAuthorize("hasAuthority('PERM_INSCRICAO_REJEITAR')")
    @PostMapping("/{id}/rejeitar")
    public InscricaoResponse rejeitar(@PathVariable UUID id, @RequestBody @Valid InscricaoRejeitarRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser usuario) {
        return InscricaoResponse.deAutorizada(inscricaoService.rejeitar(id, request.motivo(), usuario.usuarioId()));
    }
}
