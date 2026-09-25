package br.com.servire.api.diocese;

import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.web.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Teto de servidores ativos somados nas paróquias de uma diocese (V034).
 * Paróquia sem diocese, ou diocese sem cota, não é limitada por aqui.
 * Quem já estava ativo continua: a checagem só falha quando o uso sobe.
 */
@Service
public class CotaDioceseService {

    private final TenantRepository tenantRepository;

    public CotaDioceseService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public long uso(UUID dioceseId) {
        if (dioceseId == null) {
            return 0;
        }
        return tenantRepository.somarVoluntariosAtivos(dioceseId);
    }

    @Transactional(readOnly = true)
    public long usoDoTenant(UUID tenantId) {
        if (tenantId == null) {
            return 0;
        }
        return tenantRepository.findById(tenantId)
                .map(Tenant::getDiocese)
                .map(diocese -> uso(diocese.getId()))
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public void exigir(UUID tenantId, long usoAntes) {
        if (tenantId == null) {
            return;
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || tenant.getDiocese() == null) {
            return;
        }
        exigirContra(tenant.getDiocese(), usoAntes);
    }

    @Transactional(readOnly = true)
    public void exigirContra(Diocese diocese, long usoAntes) {
        if (diocese == null || diocese.getCotaVoluntarios() == null) {
            return;
        }
        long uso = uso(diocese.getId());
        if (uso > diocese.getCotaVoluntarios() && uso > usoAntes) {
            throw new ConflictException("A diocese " + diocese.getNome()
                    + " já atingiu a cota de " + diocese.getCotaVoluntarios() + " servidores ativos.");
        }
    }
}
