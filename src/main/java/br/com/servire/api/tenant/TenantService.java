package br.com.servire.api.tenant;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.tenant.dto.TenantRequest;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Configuração da paróquia do JWT atual ({@code GET}/{@code PUT /tenant}).
 * {@link Tenant} é tabela global, sem {@code @TenantId}: o isolamento é
 * só pelo id em {@link TenantContext} (nunca aceitar id no path).
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    public TenantService(TenantRepository tenantRepository, AuditLogService auditLogService) {
        this.tenantRepository = tenantRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Tenant buscarAtual() {
        return tenantDoContexto();
    }

    @Transactional
    public Tenant atualizar(TenantRequest request) {
        Tenant tenant = tenantDoContexto();
        tenant.setNome(request.nome().trim());
        tenant.setRazaoSocial(opcional(request.razaoSocial()));
        tenant.setCnpj(opcional(request.cnpj()));
        auditLogService.registrar("ATUALIZACAO", "TENANT", tenant.getId(),
                List.of("nome", "razaoSocial", "cnpj"));
        return tenant;
    }

    private Tenant tenantDoContexto() {
        UUID id = TenantContext.get();
        if (id == null) {
            throw new ResourceNotFoundException("Tenant não encontrado.");
        }
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado."));
    }

    private static String opcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
