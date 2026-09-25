package br.com.servire.api.integracao;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.auth.dto.AccessTokenResponse;
import br.com.servire.api.auth.dto.TenantResumo;
import br.com.servire.api.security.JwtService;
import br.com.servire.api.security.OpaqueTokenGenerator;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SuporteTrocaService {

    private final SuporteCodigoRepository codigos;
    private final TenantRepository tenants;
    private final JwtService jwtService;
    private final AuditLogService auditoria;

    public SuporteTrocaService(SuporteCodigoRepository codigos,
                                TenantRepository tenants,
                                JwtService jwtService,
                                AuditLogService auditoria) {
        this.codigos = codigos;
        this.tenants = tenants;
        this.jwtService = jwtService;
        this.auditoria = auditoria;
    }

    @Transactional
    public AccessTokenResponse trocar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new UnauthorizedException("Código de suporte inválido.");
        }
        SuporteCodigo salvo = codigos.findByHashCodigo(OpaqueTokenGenerator.hash(codigo.trim()))
                .orElseThrow(() -> new UnauthorizedException("Código de suporte inválido."));
        if (salvo.getUsadoEm() != null || salvo.getExpiraEm().isBefore(Instant.now())) {
            throw new UnauthorizedException("Código de suporte inválido.");
        }
        Tenant tenant = tenants.findById(salvo.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("Código de suporte inválido."));
        salvo.setUsadoEm(Instant.now());
        String token = jwtService.gerarTokenSuporte(
                salvo.getId(), tenant.getId(), salvo.getOperadorNome(), salvo.getOperadorEmail(), salvo.getMotivo());
        TenantContext.set(tenant.getId());
        try {
            SuporteSessao.set(new SuporteSessao.Dados(salvo.getOperadorNome(), salvo.getOperadorEmail(), salvo.getMotivo()));
            auditoria.registrar(
                    "Entrada de suporte: " + salvo.getOperadorNome() + " <" + salvo.getOperadorEmail() + "> — " + salvo.getMotivo(),
                    "suporte", salvo.getId(), List.of());
        } finally {
            SuporteSessao.clear();
            TenantContext.clear();
        }
        return new AccessTokenResponse(token, jwtService.suporteTtlSegundos(), TenantResumo.de(tenant));
    }
}
