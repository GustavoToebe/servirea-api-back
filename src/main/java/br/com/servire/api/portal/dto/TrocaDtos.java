package br.com.servire.api.portal.dto;
import br.com.servire.api.portal.Troca.Situacao;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class TrocaDtos {
 private TrocaDtos(){}
 public record Solicitar(@NotNull UUID substitutoId,@NotNull @Min(0) Long versao){}
 public record Versao(@NotNull @Min(0) Long versao){}
 public record Decisao(@NotNull Boolean aprovar,@NotNull @Min(0) Long versao){}
 public record Substituto(UUID id,String nome){}
 public record Pedido(UUID id,UUID vagaId,String celebracao,LocalDateTime inicio,String funcao,Situacao situacao,long versao,
     Instant criadaEm,Instant atualizadaEm,String solicitanteNome,String substitutoNome,boolean solicitante,boolean substituto,boolean vigente){}
 public record Pagina<T>(List<T> itens,long total,int pagina,int tamanho){}
}
