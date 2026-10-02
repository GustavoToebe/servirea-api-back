package br.com.servire.api.comunicacao.dto;
import br.com.servire.api.comunicacao.TipoEnvio;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public final class AniversarioDtos {
 private AniversarioDtos(){}
 public record Configurar(@NotNull Boolean ativo,UUID layoutId,@NotNull @PositiveOrZero Long versao){}
 public record Autorizar(@NotNull Boolean autorizado,@NotBlank @Size(max=200) String fonte,@NotNull @PositiveOrZero Long versao){}
 public record Config(TipoEnvio canal,boolean ativo,UUID layoutId,long versao,boolean agendadorAtivo){}
 public record Autorizacao(UUID pessoaId,TipoEnvio canal,boolean autorizado,String fonte,Instant registradoEm,long versao){}
 public record OpcaoLayout(UUID id,String nome,TipoEnvio canal){}
}
