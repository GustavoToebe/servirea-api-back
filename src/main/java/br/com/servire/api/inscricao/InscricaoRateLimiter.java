package br.com.servire.api.inscricao;

import br.com.servire.api.web.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit por IP, janela deslizante, em memória (Fase 8, seção 45 do
 * plano mestre) — protege {@code POST /public/{slug}/inscricoes} contra
 * abuso/spam antes mesmo de chamar o Turnstile.
 *
 * <p><b>Ressalva deliberada (seção 45 só lista Bucket4j como UMA opção
 * possível, e desaconselha introduzir Redis nesta fase):</b> este limitador
 * guarda estado só na memória desta instância da aplicação. Funciona
 * corretamente com uma única instância (o cenário atual do VPS, seção
 * 1.2/11); se um dia a aplicação escalar horizontalmente (múltiplas
 * instâncias atrás de um load balancer), cada instância teria seu próprio
 * contador — um atacante distribuído entre instâncias efetivamente
 * multiplicaria o limite. Documentado aqui e no README/plano mestre como
 * limitação conhecida, não um bug.</p>
 *
 * <p>Sem faxina automática de IPs antigos — o mapa cresce um pouco por IP
 * distinto visto, mas cada entrada só guarda os timestamps dentro da
 * janela configurada (entradas mais velhas são descartadas a cada nova
 * tentativa do mesmo IP). Para o volume esperado de um formulário de
 * inscrição paroquial, isso não é um problema prático.</p>
 */
@Component
public class InscricaoRateLimiter {

    private final Map<String, Deque<Instant>> tentativasPorIp = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    public InscricaoRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Registra uma tentativa do IP informado e lança
     * {@link TooManyRequestsException} se o limite da janela já tiver sido
     * atingido — chamar ANTES de qualquer trabalho custoso (Turnstile,
     * banco), para que o rate limit seja de fato a primeira barreira.
     */
    public void registrarTentativa(String ip) {
        String chave = (ip == null || ip.isBlank()) ? "desconhecido" : ip;
        Duration janela = properties.inscricaoPublica().janela();
        int maxTentativas = properties.inscricaoPublica().maxTentativas();
        Instant agora = Instant.now();

        Deque<Instant> tentativas = tentativasPorIp.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (tentativas) {
            while (!tentativas.isEmpty() && Duration.between(tentativas.peekFirst(), agora).compareTo(janela) > 0) {
                tentativas.pollFirst();
            }
            if (tentativas.size() >= maxTentativas) {
                throw new TooManyRequestsException(
                        "Muitas tentativas de inscrição a partir deste endereço. Tente novamente mais tarde.");
            }
            tentativas.addLast(agora);
        }
    }
}
