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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Troca o código de uso único da Central pelo JWT de suporte do app.
 *
 * <p>Sem {@code @Transactional} no método de propósito (25/09/2026): o
 * Hibernate fixa o tenant quando a sessão abre, e a auditoria precisa ser
 * gravada na paróquia do código. Com a transação aberta antes do
 * {@code TenantContext.set}, o {@code audit_log} ia com o tenant sentinela,
 * a FK falhava e toda entrada em suporte dava 500. O código é consumido
 * numa transação sem tenant; a auditoria roda numa segunda, aberta depois
 * do {@code TenantContext.set} (mesma regra de
 * {@code InscricaoService.criarPublica}).</p>
 */
@Service
public class SuporteTrocaService {

    private final SuporteCodigoRepository codigos;
    private final TenantRepository tenants;
    private final JwtService jwtService;
    private final AuditLogService auditoria;
    private final TransactionTemplate transacao;

    public SuporteTrocaService(SuporteCodigoRepository codigos,
                                TenantRepository tenants,
                                JwtService jwtService,
                                AuditLogService auditoria,
                                PlatformTransactionManager transactionManager) {
        this.codigos = codigos;
        this.tenants = tenants;
        this.jwtService = jwtService;
        this.auditoria = auditoria;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public AccessTokenResponse trocar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new UnauthorizedException("Código de suporte inválido.");
        }
        SuporteCodigo salvo = transacao.execute(status -> consumir(codigo.trim()));
        Tenant tenant = tenants.findById(salvo.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("Código de suporte inválido."));
        String token = jwtService.gerarTokenSuporte(
                salvo.getId(), tenant.getId(), salvo.getOperadorNome(), salvo.getOperadorEmail(), salvo.getMotivo());
        TenantContext.set(tenant.getId());
        try {
            SuporteSessao.set(new SuporteSessao.Dados(salvo.getOperadorNome(), salvo.getOperadorEmail(), salvo.getMotivo()));
            transacao.executeWithoutResult(status -> auditoria.registrar(
                    "Entrada de suporte: " + salvo.getOperadorNome() + " <" + salvo.getOperadorEmail() + "> — "
                            + salvo.getMotivo(),
                    "suporte", salvo.getId(), List.of()));
        } finally {
            SuporteSessao.clear();
            TenantContext.clear();
        }
        return new AccessTokenResponse(token, jwtService.suporteTtlSegundos(), TenantResumo.de(tenant));
    }

    private SuporteCodigo consumir(String codigo) {
        SuporteCodigo salvo = codigos.findByHashCodigo(OpaqueTokenGenerator.hash(codigo))
                .orElseThrow(() -> new UnauthorizedException("Código de suporte inválido."));
        Instant agora = Instant.now();
        if (salvo.getExpiraEm().isBefore(agora) || codigos.marcarUsado(salvo.getId(), agora) != 1) {
            throw new UnauthorizedException("Código de suporte inválido.");
        }
        return salvo;
    }
}
