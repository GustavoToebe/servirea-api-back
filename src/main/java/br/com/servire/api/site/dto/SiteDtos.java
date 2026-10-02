package br.com.servire.api.site.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.time.Instant;
public final class SiteDtos {
 private SiteDtos(){}
 public enum Tipo {AVISO,EVENTO}
 public record Bloco(@NotNull Tipo tipo,@NotBlank @Size(max=160) String titulo,@NotBlank @Size(max=4000) String texto,@Size(max=100) String quando,@Size(max=200) String local){}
 public record Dados(@NotBlank @Size(max=160) String titulo,@NotBlank @Size(max=4000) String apresentacao,@Size(max=500) String endereco,@Size(max=1000) String horarios,@Size(max=200) String contato,@NotNull @Size(max=30) List<@NotNull @Valid Bloco> blocos){}
 public record Salvar(@NotNull @PositiveOrZero Long versao,@NotNull @Valid Dados dados){}
 public record Publicar(@NotNull @PositiveOrZero Long versao,@NotNull Boolean confirmar){}
 public record Revisao(@NotNull @PositiveOrZero Long versao){}
 public record Estado(long versao,String slug,Dados rascunho,Dados publicado,Instant publicadoEm){}
}
