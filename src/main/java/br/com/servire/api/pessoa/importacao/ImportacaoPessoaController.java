package br.com.servire.api.pessoa.importacao;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import br.com.servire.api.pessoa.importacao.dto.ImportacaoPessoaDtos.*;
@RestController @RequestMapping("/pessoas/importacoes")
public class ImportacaoPessoaController {
    private final ImportacaoPessoaService service;
    public ImportacaoPessoaController(ImportacaoPessoaService service) {this.service=service;}
    @PostMapping(value="/previa",consumes="multipart/form-data")
    @PreAuthorize("hasAuthority('PERM_PESSOA') and hasAuthority('PERM_PESSOA_CRIAR')")
    public Previa previa(@RequestPart MultipartFile arquivo) {return service.previa(arquivo);}
    @PostMapping(value="/confirmar",consumes="multipart/form-data")
    @PreAuthorize("hasAuthority('PERM_PESSOA') and hasAuthority('PERM_PESSOA_CRIAR')")
    public Resultado confirmar(@RequestPart MultipartFile arquivo,@RequestParam UUID chave,@RequestParam String hash) {
        if(!hash.matches("[a-f0-9]{64}")) throw new br.com.servire.api.web.BadRequestException("Identificador do arquivo inválido.");
        return service.confirmar(arquivo,chave,hash);
    }
}
