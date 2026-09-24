package br.com.servire.api.inscricao;

import br.com.servire.api.inscricao.dto.InscricaoPublicaRequest;
import br.com.servire.api.inscricao.dto.InscricaoResponse;
import br.com.servire.api.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Formulário público de inscrição (Fase 8, seção 21/44/108 do plano
 * mestre) — {@code /public/**} já é {@code permitAll} e isento de CSRF em
 * {@code SecurityConfig} (confirmado antes de escrever esta classe, sem
 * necessidade de mudar {@code SecurityConfig}).
 *
 * <p>{@code multipart/form-data} com duas partes: {@code dados} (JSON de
 * {@link InscricaoPublicaRequest}) e, opcionalmente, {@code foto}
 * (imagem). Ver {@code StorageProperties} para os tipos/tamanho aceitos.</p>
 */
@RestController
@RequestMapping("/public/{tenantSlug}/inscricoes")
public class PublicInscricaoController {

    private final InscricaoService inscricaoService;

    public PublicInscricaoController(InscricaoService inscricaoService) {
        this.inscricaoService = inscricaoService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<InscricaoResponse> criar(@PathVariable String tenantSlug,
                                                     @RequestPart("dados") @Valid InscricaoPublicaRequest dados,
                                                     @RequestPart(value = "foto", required = false) MultipartFile foto,
                                                     HttpServletRequest request) {
        Inscricao criada = inscricaoService.criarPublica(tenantSlug, dados, foto, ClientIp.de(request));
        return ResponseEntity.ok(InscricaoResponse.de(criada));
    }
}
