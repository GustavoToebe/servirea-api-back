package br.com.servire.api.inscricao;

import br.com.servire.api.web.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste unitário de {@link InscricaoRateLimiter} (débito técnico da Fase
 * 8, seção 45 do plano mestre, adiado até esta rodada — 22/09/2026).
 *
 * <p>Deliberadamente NÃO estende {@code AbstractIntegrationTest}: esta
 * classe não toca banco nem Spring context, só memória — um teste
 * unitário puro é mais rápido e suficiente, já que toda a lógica vive
 * num {@code Map} em memória (ver javadoc de {@link InscricaoRateLimiter}
 * sobre a limitação de só funcionar corretamente numa única instância).</p>
 */
class InscricaoRateLimiterTest {

    @Test
    void permiteAteOLimiteConfiguradoPorIpENegaAPartirDaí() {
        InscricaoRateLimiter limiter = new InscricaoRateLimiter(
                new RateLimitProperties(new RateLimitProperties.InscricaoPublica(3, Duration.ofHours(1))));

        String ip = "203.0.113.10";
        assertThatCode(() -> limiter.registrarTentativa(ip)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.registrarTentativa(ip)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.registrarTentativa(ip)).doesNotThrowAnyException();

        // 4ª tentativa do MESMO IP dentro da janela: barrada.
        assertThatThrownBy(() -> limiter.registrarTentativa(ip))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void ipsDiferentesTemContadoresIndependentes() {
        InscricaoRateLimiter limiter = new InscricaoRateLimiter(
                new RateLimitProperties(new RateLimitProperties.InscricaoPublica(1, Duration.ofHours(1))));

        assertThatCode(() -> limiter.registrarTentativa("198.51.100.1")).doesNotThrowAnyException();
        // IP diferente: não deve ser afetado pelo contador do primeiro.
        assertThatCode(() -> limiter.registrarTentativa("198.51.100.2")).doesNotThrowAnyException();
        // De volta ao primeiro IP, já no limite: barrado.
        assertThatThrownBy(() -> limiter.registrarTentativa("198.51.100.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void tentativaForaDaJanelaDeslizanteNaoContaMaisParaOLimite() {
        // Janela de 1 nanossegundo: qualquer intervalo real entre duas
        // chamadas já é "fora da janela", simulando o descarte de
        // tentativas antigas sem precisar de Thread.sleep/mock de relógio.
        InscricaoRateLimiter limiter = new InscricaoRateLimiter(
                new RateLimitProperties(new RateLimitProperties.InscricaoPublica(1, Duration.ofNanos(1))));

        String ip = "192.0.2.55";
        assertThatCode(() -> limiter.registrarTentativa(ip)).doesNotThrowAnyException();
        // Mesmo já tendo usado a única "vaga" da janela, o tempo decorrido
        // entre as duas chamadas (mesmo que só microssegundos) já excede a
        // janela de 1ns configurada, então a tentativa anterior é
        // descartada antes da checagem do limite.
        assertThatCode(() -> limiter.registrarTentativa(ip)).doesNotThrowAnyException();
    }

    @Test
    void ipNuloOuEmBrancoUsaChaveDesconhecidoSemLancarErro() {
        InscricaoRateLimiter limiter = new InscricaoRateLimiter(
                new RateLimitProperties(new RateLimitProperties.InscricaoPublica(2, Duration.ofHours(1))));

        assertThatCode(() -> limiter.registrarTentativa(null)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.registrarTentativa("  ")).doesNotThrowAnyException();
        // Ambas contam para a mesma chave "desconhecido" — terceira estoura o limite de 2.
        assertThatThrownBy(() -> limiter.registrarTentativa(""))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
