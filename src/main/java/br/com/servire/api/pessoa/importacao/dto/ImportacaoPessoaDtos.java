package br.com.servire.api.pessoa.importacao.dto;
import java.util.*;
public final class ImportacaoPessoaDtos {
    private ImportacaoPessoaDtos() { }
    public record Linha(int linha,String nome,String erro) { }
    public record Previa(String hash,int quantidade,boolean podeConfirmar,List<Linha> linhas) { }
    public record Resultado(UUID id,int quantidade,boolean repetida) { }
}
