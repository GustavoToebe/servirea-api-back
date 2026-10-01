package br.com.servire.api.minhaconta;

import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.integracao.DireitosLocais;
import br.com.servire.api.integracao.DireitosLocaisRepository;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;

/**
 * Cotas de cadastro, sem contadores duplicados. A trava da paróquia serializa alterações
 * com crescimento e a aplicação dos direitos. Validação após flush inclui responsáveis
 * criados na mesma operação; ultrapassar cancela toda a transação, sem cadastro parcial.
 * Dados anteriores acima de um plano reduzido continuam editáveis, sem permitir crescimento.
 */
@Service
public class CotasService {
    private static final List<String> CODIGOS = List.of("pessoas", "voluntarios", "usuarios");
    private static final Map<String, String> NOMES = Map.of("pessoas", "Pessoas cadastradas", "voluntarios", "Voluntários cadastrados", "usuarios", "Usuários com acesso ativo ou convite");
    private final TenantRepository tenants;
    private final DireitosLocaisRepository direitos;
    private final UsuarioTenantRepository usuarios;
    private final JsonMapper json;
    @PersistenceContext private EntityManager em;

    public CotasService(TenantRepository tenants, DireitosLocaisRepository direitos, UsuarioTenantRepository usuarios, JsonMapper json) {
        this.tenants = tenants; this.direitos = direitos; this.usuarios = usuarios; this.json = json;
    }

    /** Deve ocorrer antes de carregar/mudar pessoa, inscrição ou vínculo de usuário. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Reserva reservar() {
        UUID tenantId = exigirTenant();
        tenants.bloquearParaCotas(tenantId).orElseThrow(() -> new CotaException(HttpStatus.NOT_FOUND, "PAROQUIA_NAO_ENCONTRADA", "Paróquia não encontrada."));
        return new Reserva(tenantId, contagens(tenantId), limites(direitos.findById(tenantId).orElse(null)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validar(Reserva reserva) {
        if (!reserva.tenantId().equals(exigirTenant())) throw new IllegalStateException("Reserva de outra paróquia.");
        em.flush();
        Map<String, Long> atuais = contagens(reserva.tenantId());
        for (String codigo : CODIGOS) {
            Long limite = reserva.limites().get(codigo);
            long usado = atuais.get(codigo);
            if (limite != null && usado > limite && usado > reserva.antes().get(codigo))
                throw new CotaException(HttpStatus.CONFLICT, "COTA_EXCEDIDA", "Limite de " + NOMES.get(codigo).toLowerCase(Locale.ROOT)
                        + " atingido (" + limite + "). Ajuste seu plano ou adicionais antes de ampliar o cadastro.");
        }
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Consumo consumo() {
        UUID id = exigirTenant();
        DireitosLocais local = direitos.findById(id).orElse(null);
        Map<String, Long> limites = limites(local), usados = contagens(id);
        List<Item> itens = CODIGOS.stream().map(codigo -> {
            Long limite = limites.get(codigo); long usado = usados.get(codigo);
            String estado = limite == null ? "SEM_LIMITE_CONFIGURADO" : usado > limite ? "EXCEDIDO" : usado == limite ? "ATINGIDO"
                    : limite > 0 && usado >= (limite / 5) * 4 + ((limite % 5) * 4 + 4) / 5 ? "ATENCAO" : "DISPONIVEL";
            return new Item(codigo, NOMES.get(codigo), usado, limite, limite == null ? null : Math.max(0, limite - usado), estado);
        }).toList();
        return new Consumo(local == null ? null : local.getPlanoNome(), local == null ? null : local.getVersao(),
                local == null ? null : local.getConfirmadoEm(), Instant.now(), itens);
    }

    private Map<String, Long> contagens(UUID tenantId) {
        Object[] pessoas = em.createQuery("select count(p), coalesce(sum(case when p.eVoluntario = true then 1 else 0 end), 0) from Pessoa p", Object[].class).getSingleResult();
        return Map.of("pessoas", ((Number)pessoas[0]).longValue(), "voluntarios", ((Number)pessoas[1]).longValue(),
                "usuarios", usuarios.countByTenant_IdAndStatus(tenantId, UsuarioTenant.Status.ATIVO));
    }

    private Map<String, Long> limites(DireitosLocais local) {
        if (local == null || local.getLimites() == null) return Map.of();
        try {
            var raiz = json.readTree(local.getLimites());
            if (!raiz.isObject()) throw new IllegalArgumentException();
            Map<String, Long> resultado = new HashMap<>();
            for (String codigo : CODIGOS) {
                var valor = raiz.get(codigo);
                if (valor == null) continue;
                if (!valor.isIntegralNumber() || !valor.canConvertToLong() || valor.asLong() < 0) throw new IllegalArgumentException();
                resultado.put(codigo, valor.asLong());
            }
            return Map.copyOf(resultado);
        } catch (RuntimeException ex) {
            throw new CotaException(HttpStatus.SERVICE_UNAVAILABLE, "COTAS_INVALIDAS", "Os limites do plano precisam ser corrigidos. Contate o suporte.");
        }
    }
    private UUID exigirTenant() {
        UUID id = TenantContext.get();
        if (id == null || id.equals(new UUID(0, 0))) throw new CotaException(HttpStatus.FORBIDDEN, "PAROQUIA_NAO_SELECIONADA", "Selecione uma paróquia.");
        return id;
    }
    public record Reserva(UUID tenantId, Map<String, Long> antes, Map<String, Long> limites) {}
    public record Item(String codigo, String nome, long usado, Long limite, Long disponivel, String estado) {}
    public record Consumo(String planoNome, Integer versaoDireitos, Instant direitosConfirmadosEm, Instant consultadoEm, List<Item> itens) {}
}
