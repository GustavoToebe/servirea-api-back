package br.com.servire.api.mural.dto;
import br.com.servire.api.mural.Aviso;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class AvisoDtos {
 private AvisoDtos() { }
 public record Salvar(@NotBlank @Size(max=160) String titulo, @NotBlank @Size(max=4000) String descricao,
    @NotNull Aviso.Status status, LocalDate prazo,  @PositiveOrZero Long versao) { }
 public record Resposta(UUID id,String titulo,String descricao,Aviso.Status status,LocalDate prazo,
    long versao,Instant criadoEm,Instant atualizadoEm) {
    public static Resposta de(Aviso e) {return new Resposta(e.id,e.titulo,e.descricao,e.status,e.prazo,e.versao,e.criadoEm,e.atualizadoEm);}
 }
 public record Pagina(List<Resposta> itens,long total,int pagina,int tamanho) { }
}
