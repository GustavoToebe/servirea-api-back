package br.com.servire.api.pessoa;

import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.pessoa.dto.PessoaResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pessoas")
public class PessoaController {

    private final PessoaService pessoaService;
    private final CadastroComFotoService cadastroComFoto;
    private final Duplicidades duplicidades;
    private final Aniversariantes aniversariantes;

    public PessoaController(PessoaService pessoaService, CadastroComFotoService cadastroComFoto, Duplicidades duplicidades,
                            Aniversariantes aniversariantes) {
        this.pessoaService = pessoaService;
        this.cadastroComFoto = cadastroComFoto;
        this.duplicidades = duplicidades;
        this.aniversariantes = aniversariantes;
    }

    /** Cartão do Início. {@code mes} 1–12; sem ele, o mês corrente no fuso de Brasília. */
    @PreAuthorize("hasAuthority('PERM_PESSOA')")
    @GetMapping("/aniversariantes")
    public List<br.com.servire.api.pessoa.dto.AniversarianteResponse> aniversariantes(@RequestParam(required = false) Integer mes) {
        return aniversariantes.doMes(mes);
    }

    @PreAuthorize("hasAuthority('PERM_PESSOA')")
    @GetMapping
    public List<PessoaResponse> listar(@RequestParam(required = false) PessoaPapel papel,
                                       @RequestParam(required = false) String nome) {
        return pessoaService.buscar(papel, nome).stream().map(PessoaResponse::de).toList();
    }

    @PreAuthorize("hasAuthority('PERM_PESSOA')")
    @GetMapping("/{id}")
    public PessoaResponse buscarPorId(@PathVariable UUID id) {
        return PessoaResponse.de(pessoaService.buscarPorId(id));
    }

    @PreAuthorize("hasAuthority('PERM_PESSOA')")
    @PostMapping("/duplicidades")
    public List<br.com.servire.api.pessoa.dto.DuplicidadeResponse> verificarDuplicidades(@RequestBody @Valid br.com.servire.api.pessoa.dto.DuplicidadeRequest request) {
        return duplicidades.verificar(request);
    }

    @PreAuthorize("hasAuthority('PERM_PESSOA_CRIAR')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PessoaResponse> criar(@RequestBody @Valid PessoaRequest request) {
        return ResponseEntity.ok(PessoaResponse.de(pessoaService.criar(request)));
    }

    /** Ficha ({@code dados}, JSON) e foto opcional juntas: se a foto falhar, nada é gravado. */
    @PreAuthorize("hasAuthority('PERM_PESSOA_CRIAR')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PessoaResponse> criarComFoto(@RequestPart("dados") @Valid PessoaRequest request,
                                                       @RequestPart(value = "foto", required = false) MultipartFile foto) {
        return ResponseEntity.ok(PessoaResponse.de(cadastroComFoto.criar(request, foto)));
    }

    @PreAuthorize("hasAuthority('PERM_PESSOA_ALTERAR')")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PessoaResponse atualizar(@PathVariable UUID id, @RequestBody @Valid PessoaRequest request) {
        return PessoaResponse.de(pessoaService.atualizar(id, request));
    }

    @PreAuthorize("hasAuthority('PERM_PESSOA_ALTERAR')")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PessoaResponse atualizarComFoto(@PathVariable UUID id,
                                           @RequestPart("dados") @Valid PessoaRequest request,
                                           @RequestPart(value = "foto", required = false) MultipartFile foto) {
        return PessoaResponse.de(cadastroComFoto.atualizar(id, request, foto));
    }
}
