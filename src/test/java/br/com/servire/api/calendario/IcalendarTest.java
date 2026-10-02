package br.com.servire.api.calendario;
import org.junit.jupiter.api.Test;
import br.com.servire.api.portal.PortalService.Compromisso;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class IcalendarTest {
 @Test void dobraUtf8SemCortarCaracteresEUsaCRLF(){StringBuilder b=new StringBuilder();String original="SUMMARY:"+"ç😀".repeat(70);Icalendar.linha(b,original);for(String linha:b.toString().split("\r\n"))assertThat(linha.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);assertThat(b.toString().replace("\r\n ","").stripTrailing()).isEqualTo(original);}
 @Test void textoNaoInjetaLinhasIcsEHorarioEhUTC(){var inicio=LocalDateTime.of(2026,10,2,19,0);String s=Icalendar.gerar(List.of(new Compromisso("a","ESCALA","Missa; teste,\r\nBEGIN:VEVENT",inicio,inicio.plusHours(1),null,null,null,null,null,null,null)));assertThat(s).contains("DTSTART:20261002T220000Z","SUMMARY:Missa\\; teste\\,\\nBEGIN:VEVENT");assertThat(s.split("BEGIN:VEVENT").length).isEqualTo(3);assertThat(s).doesNotContain("\r\nBEGIN:VEVENT\r\nBEGIN:VEVENT");}
}
