package br.com.servire.api.liturgia.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public final class LiturgiaDtos {
    private LiturgiaDtos() {
    }
    public record ReferenciaSalvar(@PositiveOrZero long versao,@NotBlank @Size(max=160) String titulo,@NotBlank @Size(max=200) String fonte,@Size(max=1000) String url,@Size(max=2000) String observacao,boolean ativo) {
    }
    public record Referencia(UUID id,long versao,String titulo,String fonte,String url,String observacao,boolean ativo) {
    }
    public record Passo(@NotBlank @Size(max=160) String titulo,UUID referenciaId,@Size(max=2000) String observacao) {
    }
    public record RoteiroSalvar(@PositiveOrZero long versao,@NotBlank @Size(max=160) String titulo,@NotBlank @Size(max=200) String celebracao,@NotNull @Size(min=1,max=50) List<@NotNull @Valid Passo> passos,boolean ativo) {
    }
    public record Roteiro(UUID id,long versao,String titulo,String celebracao,List<Passo> passos,boolean ativo) {
    }
    public record Pagina<T>(List<T> itens,long total,int pagina,int tamanho) {
    }
}
