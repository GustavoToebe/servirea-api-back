package br.com.servire.api.voluntario;

import br.com.servire.api.voluntario.dto.VoluntarioRequest;
import br.com.servire.api.voluntario.dto.VoluntarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
 * Endpoints de voluntários (seção 106 do plano mestre, Fase 6) —
 * substituem o acesso direto do Angular à tabela {@code voluntarios} via
 * Supabase JS (seção 83: "RPCs de negócio e regras → migrar para Java").
 * Requer autenticação (não está em {@code ROTAS_PUBLICAS} de
 * {@code SecurityConfig}, então cai na regra padrão
 * {@code anyRequest().authenticated()}).
 *
 * <p><b>Deliberadamente fora do escopo desta primeira versão</b> (a
 * documentar em "Próximos passos" até serem implementados):</p>
 * <ul>
 *   <li>Upload real de foto e geração de signed URL — depende do módulo
 *   de Storage (Fase 7, seção 107); por enquanto {@code fotoPath} só é
 *   lido, nunca escrito por aqui.</li>
 *   <li>Filtro de listagem por {@code funcoes_habilitadas} (o "picker de
 *   candidatos" da seção 49) — só faz sentido junto do módulo de escalas
 *   (Fase 9, seção 109).</li>
 *   <li>{@code GET .../commitments} (histórico de compromissos do
 *   voluntário, view {@code vw_voluntario_compromissos}) — depende de
 *   {@code escalas}/{@code escala_eventos}/{@code escala_vagas}, que só
 *   existem no banco, ainda sem entidade JPA (Fase 9).</li>
 * </ul>
 */
@RestController
@RequestMapping("/voluntarios")
public class VoluntarioController {

    private final VoluntarioService voluntarioService;

    public VoluntarioController(VoluntarioService voluntarioService) {
        this.voluntarioService = voluntarioService;
    }

    @GetMapping
    public List<VoluntarioResponse> listar(@RequestParam(required = false) Boolean ativo,
                                            @RequestParam(required = false) TipoVoluntario tipo,
                                            @RequestParam(required = false) String nome) {
        return voluntarioService.buscar(ativo, tipo, nome).stream().map(VoluntarioResponse::de).toList();
    }

    @GetMapping("/count")
    public long contar(@RequestParam boolean ativo) {
        return voluntarioService.contarPorAtivo(ativo);
    }

    @GetMapping("/{id}")
    public VoluntarioResponse buscarPorId(@PathVariable UUID id) {
        return VoluntarioResponse.de(voluntarioService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<VoluntarioResponse> criar(@RequestBody @Valid VoluntarioRequest request) {
        Voluntario criado = voluntarioService.criar(request);
        return ResponseEntity.ok(VoluntarioResponse.de(criado));
    }

    @PutMapping("/{id}")
    public VoluntarioResponse atualizar(@PathVariable UUID id, @RequestBody @Valid VoluntarioRequest request) {
        return VoluntarioResponse.de(voluntarioService.atualizar(id, request));
    }

    @PatchMapping("/{id}/ativo")
    public VoluntarioResponse alterarAtivo(@PathVariable UUID id, @RequestParam boolean ativo) {
        return VoluntarioResponse.de(voluntarioService.setAtivo(id, ativo));
    }
}
