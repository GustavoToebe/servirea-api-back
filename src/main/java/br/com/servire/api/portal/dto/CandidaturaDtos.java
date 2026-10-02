package br.com.servire.api.portal.dto;

import br.com.servire.api.portal.Candidatura.Situacao;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class CandidaturaDtos {
    private CandidaturaDtos() {}
    public record Versao(@NotNull @Min(0) Long versao) {}
    public record Decisao(@NotNull Boolean aprovar,@NotNull @Min(0) Long versao) {}
    public record Vaga(UUID vagaId,String celebracao,LocalDateTime inicio,String funcao,long versao,boolean elegivel,String impedimento) {}
    public record Pedido(UUID id,UUID vagaId,String celebracao,LocalDateTime inicio,String funcao,
            Situacao situacao,long versao,Instant criadaEm,Instant atualizadaEm,String pessoaNome,boolean vigente) {}
    public record Pagina<T>(List<T> itens,long total,int pagina,int tamanho) {}
}
