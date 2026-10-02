package br.com.servire.api.relatorios.dto;
import br.com.servire.api.escala.*;
import br.com.servire.api.voluntario.FuncaoEscala;
import java.time.*;
import java.util.*;
public final class ParticipacaoDtos {
 private ParticipacaoDtos(){}
 public record Linha(UUID vagaId,String escala,String celebracao,LocalDate data,LocalTime horario,String pessoa,FuncaoEscala funcao,Presenca presenca,RespostaParticipacao resposta){}
 public record Pagina(List<Linha> itens,long total,int pagina,int tamanho){}
}
