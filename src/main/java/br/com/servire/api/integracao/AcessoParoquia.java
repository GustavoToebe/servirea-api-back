package br.com.servire.api.integracao;

import br.com.servire.api.tenant.Tenant;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * A regra única de "esta paróquia pode ser usada agora", usada pelo login
 * ({@code AuthService}) e pelo Kill Switch de cada requisição
 * ({@code JwtAuthenticationFilter}). Até 25/09/2026 o login olhava só
 * {@code tenant.status}: paróquia sem confirmação da Central há mais de 72h
 * aparecia na lista, fazia login e depois toda requisição dava 401.
 *
 * <ul>
 *   <li>Sem linha em {@code direitos_locais} (paróquia anterior à Central):
 *   vale {@code tenant.status} ATIVO/TRIAL.</li>
 *   <li>Com a linha: {@code acesso_liberado} da Central e confirmação há
 *   no máximo a tolerância técnica (padrão 72h).</li>
 * </ul>
 */
@Component
public class AcessoParoquia {

    private final DireitosLocaisRepository direitos;
    private final IntegracaoProperties properties;

    public AcessoParoquia(DireitosLocaisRepository direitos, IntegracaoProperties properties) {
        this.direitos = direitos;
        this.properties = properties;
    }

    public boolean liberada(Tenant tenant) {
        Optional<DireitosLocais> locais = direitos.findById(tenant.getId());
        if (locais.isEmpty()) {
            return tenant.getStatus() == Tenant.Status.ATIVO || tenant.getStatus() == Tenant.Status.TRIAL;
        }
        return liberada(locais.get());
    }

    boolean liberada(DireitosLocais locais) {
        return locais.isAcessoLiberado() && !toleranciaEsgotada(locais);
    }

    boolean toleranciaEsgotada(DireitosLocais locais) {
        return Instant.now().isAfter(locais.getConfirmadoEm().plus(properties.tolerancia(), ChronoUnit.HOURS));
    }
}
