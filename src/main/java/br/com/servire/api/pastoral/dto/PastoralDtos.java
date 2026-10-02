package br.com.servire.api.pastoral.dto;
import br.com.servire.api.pastoral.*;
import jakarta.validation.constraints.*;
import java.util.*;
public final class PastoralDtos {
 private PastoralDtos() { }
 public record SalvarEquipe(@NotBlank @Size(max=120) String nome,@Size(max=1000) String descricao,@NotNull Boolean ativo,@PositiveOrZero Long versao) { }
 public record SalvarMembro(@NotNull UUID pessoaId,@NotNull MembroPastoral.Papel papel,@NotNull Boolean ativo,@PositiveOrZero Long versao) { }
 public record Equipe(UUID id,String nome,String descricao,boolean ativo,long versao) {public static Equipe de(EquipePastoral e){return new Equipe(e.id,e.nome,e.descricao,e.ativo,e.versao);}}
 public record Membro(UUID id,UUID pessoaId,String nome,MembroPastoral.Papel papel,boolean ativo,long versao) { }
 public record PessoaOpcao(UUID id,String nome) { }
 public record Pagina<T>(List<T> itens,long total,int pagina,int tamanho) { }
}
