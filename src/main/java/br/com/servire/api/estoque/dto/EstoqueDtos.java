package br.com.servire.api.estoque.dto;
import jakarta.validation.constraints.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
public final class EstoqueDtos {
    private EstoqueDtos() {
    }
    public record Salvar(@PositiveOrZero long versao,@NotBlank @Size(max=160)String nome,@NotBlank @Size(max=60)String codigo,@NotBlank String tipo,@NotBlank @Size(max=30)String unidade,@Size(max=200)String local,UUID responsavelUsuarioId,boolean ativo) {
    }
    public record Movimentar(@PositiveOrZero long versao,@NotNull UUID chave,@NotBlank String tipo,@NotNull @DecimalMin("0") @Digits(integer=9,fraction=3)BigDecimal quantidade,@NotBlank @Size(max=500)String motivo,UUID responsavelUsuarioId) {
    }
    public record Item(UUID id,long versao,String nome,String codigo,String tipo,String unidade,String local,UUID responsavelUsuarioId,boolean ativo,BigDecimal saldo,String responsavelNome) {
    }
    public record Movimento(UUID id,UUID itemId,UUID chave,String tipo,BigDecimal quantidade,BigDecimal saldoAntes,BigDecimal saldoDepois,String motivo,UUID responsavelUsuarioId,UUID registradoPor,Instant registradoEm,String responsavelNome,String registradoPorNome) {
    }
    public record Pagina<T>(List<T> itens,long total,int pagina,int tamanho) {
    }
    public record Responsavel(UUID id,String nome) {
    }
}
