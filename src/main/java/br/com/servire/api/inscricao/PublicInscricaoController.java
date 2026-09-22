package br.com.servire.api.inscricao;

import br.com.servire.api.inscricao.dto.InscricaoPublicaRequest;
import br.com.servire.api.inscricao.dto.InscricaoResponse;
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
        Inscricao criada = inscricaoService.criarPublica(tenantSlug, dados, foto, ipRemetente(request));
        return ResponseEntity.ok(InscricaoResponse.de(criada));
    }

    /**
     * Diferente de {@code AuthController.ip} (que só usa o IP para um
     * campo informativo de auditoria), aqui o IP alimenta diretamente o
     * rate limit (seção 45) — por isso lemos {@code X-Forwarded-For}
     * primeiro (o valor que um reverse proxy real, Nginx/Caddy, seção
     * 11/89, adiciona), caindo para {@link HttpServletRequest#getRemoteAddr()}
     * só se o header não vier.
     *
     * <p><b>Ressalva:</b> {@code X-Forwarded-For} só é confiável se o
     * reverse proxy do deploy REESCREVER (não simplesmente repassar) esse
     * header antes de chegar aqui — do contrário, um cliente malicioso
     * pode forjar o header e escolher artificialmente sob qual "IP" cada
     * tentativa é contabilizada, esvaziando o rate limit. Não foi possível
     * confirmar a configuração exata do Nginx/Caddy do VPS de produção
     * neste ambiente de pesquisa — validar isso faz parte do checklist de
     * deploy (seção 11/89 do plano mestre).</p>
     */
    private String ipRemetente(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
