package br.com.servire.api.evento;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MensagensDeEventoTest {

    private Evento evento() {
        Evento e = new Evento("Retiro", LocalDateTime.of(2026, 10, 10, 8, 0));
        e.setLocalNome("Chácara");
        return e;
    }

    @Test
    void linhaComTagVaziaSomeELinhaSemTagFica() {
        String texto = MensagensDeEvento.renderizar(MensagensDeEvento.CONFIRMACAO_PADRAO, evento(), "Ana Souza",
                "Paróquia X", LocalDate.of(2026, 10, 1));

        assertThat(texto).startsWith("Olá Ana!");
        assertThat(texto).contains("📅 10/10/2026 às 08:00", "📍 Chácara", "Paróquia X");
        assertThat(texto).doesNotContain("Mapa:", "Dúvidas:", "#");
    }

    @Test
    void quandoFalaHojeAmanhaEDias() {
        assertThat(MensagensDeEvento.quando(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1))).isEqualTo("hoje");
        assertThat(MensagensDeEvento.quando(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2))).isEqualTo("amanhã");
        assertThat(MensagensDeEvento.quando(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4))).isEqualTo("daqui a 3 dias");
    }

    @Test
    void tagDesconhecidaFicaComoEstaParaAPessoaPerceber() {
        String texto = MensagensDeEvento.renderizar("Oi #PESSOA.NOME# #OUTRA.COISA#", evento(), "Ana", null, LocalDate.of(2026, 10, 1));
        assertThat(texto).isEqualTo("Oi Ana #OUTRA.COISA#");
    }
}
