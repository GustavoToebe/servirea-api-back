package br.com.servire.api.pessoa;

import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.pessoa.dto.PessoaResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/pessoas")
public class PessoaController {

    private final PessoaService pessoaService;

    public PessoaController(PessoaService pessoaService) {
        this.pessoaService = pessoaService;
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_READ')")
    @GetMapping
    public List<PessoaResponse> listar(@RequestParam(required = false) PessoaPapel papel,
                                       @RequestParam(required = false) String nome) {
        return pessoaService.buscar(papel, nome).stream().map(PessoaResponse::de).toList();
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_READ')")
    @GetMapping("/{id}")
    public PessoaResponse buscarPorId(@PathVariable UUID id) {
        return PessoaResponse.de(pessoaService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")
    @PostMapping
    public ResponseEntity<PessoaResponse> criar(@RequestBody @Valid PessoaRequest request) {
        return ResponseEntity.ok(PessoaResponse.de(pessoaService.criar(request)));
    }

    @PreAuthorize("hasAuthority('PERM_VOLUNTARIO_WRITE')")
    @PutMapping("/{id}")
    public PessoaResponse atualizar(@PathVariable UUID id, @RequestBody @Valid PessoaRequest request) {
        return PessoaResponse.de(pessoaService.atualizar(id, request));
    }
}
