package br.com.servire.api.tarefas.dto;
import br.com.servire.api.tarefas.Tarefa;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class TarefaDtos {
 private TarefaDtos() { }
 public record Salvar(@NotBlank @Size(max=160) String titulo, @NotBlank @Size(max=4000) String descricao,
    @NotNull Tarefa.Status status, LocalDate prazo, @Size(max=120) String equipe, @PositiveOrZero Long versao, UUID responsavelUsuarioId) { }
 public record Resposta(UUID id,String titulo,String descricao,Tarefa.Status status,LocalDate prazo,String equipe,
    long versao,Instant criadoEm,Instant atualizadoEm,UUID responsavelUsuarioId,String responsavelNome) {
    public static Resposta de(Tarefa e) {return new Resposta(e.id,e.titulo,e.descricao,e.status,e.prazo,e.equipe,e.versao,e.criadoEm,e.atualizadoEm,e.responsavelUsuarioId,null);}
 }
 public record Responsavel(UUID id,String nome) { }
 public record Pagina(List<Resposta> itens,long total,int pagina,int tamanho) { }
}
