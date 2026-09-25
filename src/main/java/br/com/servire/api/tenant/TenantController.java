package br.com.servire.api.tenant;

import br.com.servire.api.tenant.dto.TenantRequest;
import br.com.servire.api.tenant.dto.TenantResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuração da paróquia do access token atual. Exige
 * {@code CONFIG_WRITE} (seção 31) — só {@code ADMIN} tem essa permissão
 * ({@link br.com.servire.api.security.RolePermissoes}).
 */
@RestController
@RequestMapping("/tenant")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PreAuthorize("hasAuthority('PERM_PAROQUIA')")
    @GetMapping
    public TenantResponse buscar() {
        return TenantResponse.de(tenantService.buscarAtual());
    }

    @PreAuthorize("hasAuthority('PERM_PAROQUIA_ALTERAR')")
    @PutMapping
    public TenantResponse atualizar(@RequestBody @Valid TenantRequest request) {
        return TenantResponse.de(tenantService.atualizar(request));
    }
}
