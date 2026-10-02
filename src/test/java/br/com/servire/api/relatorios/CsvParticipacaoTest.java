package br.com.servire.api.relatorios;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CsvParticipacaoTest {
 @ParameterizedTest @ValueSource(strings={"=1+1","+cmd","-2+1","@SOMA(1)","  =1","\t@x","\r\n+x","\uFEFF=x"}) void neutralizaFormulas(String s){assertThat(ParticipacaoService.celula(s)).startsWith("\"'");}
 @Test void escapaAspasESeparadores(){assertThat(ParticipacaoService.celula("Nome;\"A\"\nB")).isEqualTo("\"Nome;\"\"A\"\"\nB\"");}
}
