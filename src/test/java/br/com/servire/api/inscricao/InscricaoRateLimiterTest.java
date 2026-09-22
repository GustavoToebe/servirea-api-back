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
    void tentativaForaDaJanelaDeslizanteNaoContaMaisParaOLimite() throws InterruptedException {
        // Bug de teste encontrado no mvn clean verify real de 22/09/2026:
        // a versão original usava Duration.ofNanos(1) apostando que
        // qualquer intervalo real entre duas chamadas já excederia a
        // janela — mas isso depende da resolução do relógio da máquina, e
        // falhou na máquina Windows do usuário (duas chamadas de
        // Instant.now() em sequência podem "empatar" dependendo da
        // resolução do relógio do sistema operacional). Como
        // InscricaoRateLimiter não tem um Clock injetável (e criar um só
        // para este teste não se justifica), a correção é usar uma janela
        // pequena, porém real, combinada com um Thread.sleep que garante
        // determinística e explicitamente que ela já passou.
        InscricaoRateLimiter limiter = new InscricaoRateLimiter(
                new RateLimitProperties(new RateLimitProperties.InscricaoPublica(1, Duration.ofMillis(50))));

        String ip = "192.0.2.55";
        assertThatCode(() -> limiter.registrarTentativa(ip)).doesNotThrowAnyException();
        Thread.sleep(60);
        // Passados os 60ms (> janela de 50ms configurada), a tentativa
        // anterior é descartada antes da checagem do limite.
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
