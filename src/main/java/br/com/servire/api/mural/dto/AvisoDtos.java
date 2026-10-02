package br.com.servire.api.mural.dto;
import br.com.servire.api.mural.Aviso;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class AvisoDtos {
 private AvisoDtos() { }
 public record Salvar(@NotBlank @Size(max=160) String titulo, @NotBlank @Size(max=4000) String descricao,
    @NotNull Aviso.Status status, LocalDate prazo,  @PositiveOrZero Long versao,Aviso.Publico publico,@Size(max=100) List<@NotNull UUID> destinatarios) {
 public Salvar(String t,String d,Aviso.Status s,LocalDate p,Long v){this(t,d,s,p,v,Aviso.Publico.TODOS,List.of());}
 }
 public record Resposta(UUID id,String titulo,String descricao,Aviso.Status status,LocalDate prazo,
    long versao,Instant criadoEm,Instant atualizadoEm,Aviso.Publico publico,List<UUID> destinatarios,boolean lido,long leituras) {
    public static Resposta de(Aviso e) {return new Resposta(e.id,e.titulo,e.descricao,e.status,e.prazo,e.versao,e.criadoEm,e.atualizadoEm,e.publico,List.of(),false,0);}
 }
 public record Confirmar(@NotNull @PositiveOrZero Long versao) { }
 public record Conta(UUID id,String nome) { }
 public record Pagina(List<Resposta> itens,long total,int pagina,int tamanho) { }
}
