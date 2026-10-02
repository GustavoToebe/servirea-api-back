package br.com.servire.api.auth;

import br.com.servire.api.security.AuthenticatedUser;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/me/mfa")
public class MfaController {
    private final MfaService mfa;
    private final LimiteLogin limite;
    public MfaController(MfaService mfa, LimiteLogin limite) {this.mfa = mfa; this.limite = limite;}
    @GetMapping
    @PreAuthorize("isAuthenticated() and !principal.suporte()")
    public MfaService.Status status(@AuthenticationPrincipal AuthenticatedUser op, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return mfa.status(op.usuarioId());
    }
    @PostMapping("/preparar")
    @PreAuthorize("isAuthenticated() and !principal.suporte()")
    public MfaService.Preparacao preparar(@AuthenticationPrincipal AuthenticatedUser op, @Valid @RequestBody Senha body,
                                         HttpServletRequest req, HttpServletResponse res) {
        limitar(op, req, res); return mfa.preparar(op.usuarioId(), body.senha(), req.getRemoteAddr());
    }
    @PostMapping("/ativar")
    @PreAuthorize("isAuthenticated() and !principal.suporte()")
    public MfaService.Recuperacao ativar(@AuthenticationPrincipal AuthenticatedUser op, @Valid @RequestBody Confirmacao body,
                                        HttpServletRequest req, HttpServletResponse res) {
        limitar(op, req, res); return mfa.ativar(op.usuarioId(), body.senha(), body.codigo(), req.getRemoteAddr());
    }
    @PostMapping("/desativar")
    @PreAuthorize("isAuthenticated() and !principal.suporte()")
    public ResponseEntity<Void> desativar(@AuthenticationPrincipal AuthenticatedUser op, @Valid @RequestBody Confirmacao body,
                                         HttpServletRequest req, HttpServletResponse res) {
        limitar(op, req, res); mfa.desativar(op.usuarioId(), body.senha(), body.codigo(), req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/recuperacao")
    @PreAuthorize("isAuthenticated() and !principal.suporte()")
    public MfaService.Recuperacao recuperar(@AuthenticationPrincipal AuthenticatedUser op,@Valid @RequestBody Confirmacao body,HttpServletRequest req,HttpServletResponse res) {
        limitar(op,req,res);return mfa.renovarRecuperacao(op.usuarioId(),body.senha(),body.codigo(),req.getRemoteAddr());
    }
    private void limitar(AuthenticatedUser op, HttpServletRequest req, HttpServletResponse res) {
        res.setHeader("Cache-Control", "no-store");
        try {limite.registrar(req.getRemoteAddr(), op.usuarioId().toString());}
        catch (LimiteLoginException ex) {res.setHeader("Retry-After", Long.toString(ex.segundos())); throw ex;}
    }
    public record Senha(@NotBlank @Size(max = 72) String senha) {}
    public record Confirmacao(@NotBlank @Size(max = 72) String senha, @NotBlank @Size(max = 64) String codigo) {}
}
