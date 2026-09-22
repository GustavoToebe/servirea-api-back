package br.com.servire.api.voluntario;

import br.com.servire.api.voluntario.dto.CompromissoResponse;
import br.com.servire.api.voluntario.dto.FotoUrlResponse;
import br.com.servire.api.voluntario.dto.VoluntarioRequest;
import br.com.servire.api.voluntario.dto.VoluntarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de voluntários (seção 106 do plano mestre, Fase 6) —
 * substituem o acesso direto do Angular à tabela {@code voluntarios} via
 * Supabase JS (seção 83: "RPCs de negócio e regras → migrar para Java").
 * Requer autenticação (não está em {@code ROTAS_PUBLICAS} de
 * {@code SecurityConfig}, então cai na regra padrão
 * {@code anyRequest().authenticated()}).
 *
 * <p>{@code POST .../foto} e {@code GET .../foto-url} foram somados na
 * Fase 7 (Storage, seção 107) — o resto do módulo é da Fase 6.</p>
 *
 * <p><b>Permissões (seção 31, Fase 11):</b> leitura exige
 * {@code VOLUNTARIO_READ}; escrita (criar/atualizar/ativar-desativar/foto)
 * exige {@code VOLUNTARIO_WRITE} — ver {@link br.com.servire.api.security.RolePermissoes}.</p>
 *
 * <p>{@code GET .../commitments} lista os compromissos do voluntário
 * (seção 9.3 / view {@code vw_voluntario_compromissos}), via JPA.</p>
 */
@RestController
@RequestMapping("/voluntarios")
public class VoluntarioController {

    private final VoluntarioService voluntarioService;

    public VoluntarioController(VoluntarioService voluntarioService) {
        this.voluntarioService = voluntarioService;
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_READ')")
    @GetMapping
    public List<VoluntarioResponse> listar(@RequestParam(required = false) Boolean ativo,
                                            @RequestParam(required = false) TipoVoluntario tipo,
                                            @RequestParam(required = false) String nome) {
        return voluntarioService.buscar(ativo, tipo, nome).stream().map(VoluntarioResponse::de).toList();
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_READ')")
    @GetMapping("/count")
    public long contar(@RequestParam boolean ativo) {
        return voluntarioService.contarPorAtivo(ativo);
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_READ')")
    @GetMapping("/{id}")
    public VoluntarioResponse buscarPorId(@PathVariable UUID id) {
        return VoluntarioResponse.de(voluntarioService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")
    @PostMapping
    public ResponseEntity<VoluntarioResponse> criar(@RequestBody @Valid VoluntarioRequest request) {
        Voluntario criado = voluntarioService.criar(request);
        return ResponseEntity.ok(VoluntarioResponse.de(criado));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")
    @PutMapping("/{id}")
    public VoluntarioResponse atualizar(@PathVariable UUID id, @RequestBody @Valid VoluntarioRequest request) {
        return VoluntarioResponse.de(voluntarioService.atualizar(id, request));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")
    @PatchMapping("/{id}/ativo")
    public VoluntarioResponse alterarAtivo(@PathVariable UUID id, @RequestParam boolean ativo) {
        return VoluntarioResponse.de(voluntarioService.setAtivo(id, ativo));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")
    @PostMapping("/{id}/foto")
    public VoluntarioResponse enviarFoto(@PathVariable UUID id, @RequestParam("foto") MultipartFile foto) {
        return VoluntarioResponse.de(voluntarioService.definirFoto(id, foto));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_READ')")
    @GetMapping("/{id}/foto-url")
    public FotoUrlResponse obterUrlFoto(@PathVariable UUID id) {
        return new FotoUrlResponse(voluntarioService.obterUrlFoto(id));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_READ')")
    @GetMapping("/{id}/commitments")
    public List<CompromissoResponse> listarCompromissos(@PathVariable UUID id) {
        return voluntarioService.listarCompromissos(id);
    }
}
