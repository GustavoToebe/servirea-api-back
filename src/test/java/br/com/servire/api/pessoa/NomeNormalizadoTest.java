package br.com.servire.api.pessoa;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NomeNormalizadoTest {

    @Test
    void testNormalizar() {
        assertEquals("jose silva", NomeNormalizado.normalizar("José da Silva"));
        assertEquals("ana paula souza", NomeNormalizado.normalizar("Ana Paula Souza"));
    }

    @Test
    void testParecidos() {
        assertTrue(NomeNormalizado.parecidos("José da Silva", "Jose Silva"));
        assertTrue(NomeNormalizado.parecidos("Ana Paula Souza", "Ana Souza"));
        assertFalse(NomeNormalizado.parecidos("Ana Souza", "Bruno Souza"));
        assertTrue(NomeNormalizado.parecidos("Maria Aparecida", "Maria Aparecyda"));
    }

    @Test
    void testDigitos() {
        assertEquals("45999998888", NomeNormalizado.digitos("(45) 99999-8888"));
        assertEquals("", NomeNormalizado.digitos(null));
    }
}
