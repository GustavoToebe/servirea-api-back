package br.com.servire.api.calendario;
import br.com.servire.api.portal.PortalService.Compromisso;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.List;
/** RFC 5545: CRLF, escape de TEXT e dobra de linha por octetos UTF-8, sem cortar caracteres. */
public final class Icalendar {
 private static final DateTimeFormatter UTC=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
 private static final ZoneId BRASILIA=ZoneId.of("America/Sao_Paulo");
 private Icalendar() { }
 public static String gerar(List<Compromisso> itens){StringBuilder b=new StringBuilder();
  for(String s:List.of("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Servirea//Compromissos pessoais//PT-BR","CALSCALE:GREGORIAN")) linha(b,s);
  for(var i:itens){linha(b,"BEGIN:VEVENT");linha(b,"UID:"+i.id()+"@servirea");linha(b,"DTSTAMP:"+UTC.format(Instant.now()));linha(b,"DTSTART:"+UTC.format(i.inicio().atZone(BRASILIA)));linha(b,"DTEND:"+UTC.format(i.termino().atZone(BRASILIA)));linha(b,"SUMMARY:"+texto(i.titulo()));if(i.local()!=null) linha(b,"LOCATION:"+texto(i.local()));if(i.funcao()!=null) linha(b,"DESCRIPTION:"+texto("Função: "+i.funcao()));linha(b,"CLASS:PRIVATE");linha(b,"END:VEVENT");}
  linha(b,"END:VCALENDAR");return b.toString();
 }
 static String texto(String s){return s.replace("\\","\\\\").replace("\r\n","\n").replace("\r","\n").replace("\n","\\n").replace(";","\\;").replace(",","\\,").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]","");}
 static void linha(StringBuilder b,String linha){int octetos=0;for(int p=0;p<linha.length();){int cp=linha.codePointAt(p);String c=new String(Character.toChars(cp));int tamanho=c.getBytes(StandardCharsets.UTF_8).length;if(octetos+tamanho>75){b.append("\r\n ");octetos=1;}b.append(c);octetos+=tamanho;p+=Character.charCount(cp);}b.append("\r\n");}
}
