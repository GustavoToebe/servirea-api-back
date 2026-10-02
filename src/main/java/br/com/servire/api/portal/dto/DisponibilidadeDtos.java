package br.com.servire.api.portal.dto;
import br.com.servire.api.voluntario.Periodo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public final class DisponibilidadeDtos {
 private DisponibilidadeDtos(){}
 public record Bloqueio(@NotNull LocalDate data,Periodo periodo){}
 public record Salvar(@NotNull @PositiveOrZero Long versao,@NotNull Boolean semRestricao,@NotNull @Size(max=93) List<@NotNull @Valid Bloqueio> itens){}
 public record Resposta(int ano,int mes,long versao,boolean semRestricao,List<Bloqueio> itens){}
}
