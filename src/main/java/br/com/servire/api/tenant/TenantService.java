package br.com.servire.api.tenant;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.pessoa.Contatos;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.tenant.dto.TenantRequest;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Configuração da paróquia do JWT atual ({@code GET}/{@code PUT /tenant}).
 * {@link Tenant} é tabela global, sem {@code @TenantId}: o isolamento é
 * só pelo id em {@link TenantContext}.
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    @PersistenceContext
    private EntityManager entityManager;

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
        substituirContatos(tenant, request.emails(), request.telefones());
        auditLogService.registrar("ATUALIZACAO", "TENANT", tenant.getId(),
                List.of("nome", "razaoSocial", "cnpj", "contatos"));
        return tenant;
    }

    private void substituirContatos(Tenant tenant, List<ContatoEmailRequest> emails,
                                    List<ContatoTelefoneRequest> telefones) {
        Contatos.exigirUmPrincipalEmail(emails);
        Contatos.exigirUmPrincipalTelefone(telefones);
        tenant.getEmails().clear();
        tenant.getTelefones().clear();
        entityManager.flush();
        if (emails != null) {
            for (ContatoEmailRequest e : emails) {
                TenantEmail linha = new TenantEmail(e.tipo().trim(), e.email().trim(), e.principal());
                linha.setTenant(tenant);
                tenant.getEmails().add(linha);
            }
        }
        if (telefones != null) {
            for (ContatoTelefoneRequest t : telefones) {
                TenantTelefone linha = new TenantTelefone(t.tipo().trim(), t.numero().trim(), t.principal());
                linha.setTenant(tenant);
                tenant.getTelefones().add(linha);
            }
        }
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
