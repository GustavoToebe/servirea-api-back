package br.com.servire.api.pessoa.importacao.dto;
import java.util.*;
public final class ImportacaoPessoaDtos {
    private ImportacaoPessoaDtos() { }
    public record Linha(int linha,String nome,String erro,String papel,String cpf,String email,String telefone) {
        public Linha(int linha,String nome,String erro) {this(linha,nome,erro,null,null,null,null);}
        public Linha comErro(String mensagem) {return new Linha(linha,nome,mensagem,papel,cpf,email,telefone);}
    }
    public record Previa(String hash,int quantidade,boolean podeConfirmar,List<Linha> linhas) { }
    public record Resultado(UUID id,int quantidade,boolean repetida) { }
    public record Aba(int indice,String nome) { }
    public record Coluna(int indice,String titulo) { }
    public record Estrutura(List<Aba> abas,int aba,int linhaCabecalho,List<Coluna> colunas,Map<String,Integer> sugestao) { }
    /** null procura pelo cabeçalho; -1 ignora campo opcional. Índices começam em zero. */
    public record Opcoes(Integer aba,Integer nome,Integer papel,Integer cpf,Integer email,Integer telefone) {
        public Opcoes {if(aba==null) aba=0;}
        public static Opcoes padrao() {return new Opcoes(0,null,null,null,null,null);}
    }
}
