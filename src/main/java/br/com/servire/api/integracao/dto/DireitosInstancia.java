package br.com.servire.api.integracao.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DireitosInstancia(
        UUID contratacaoId,
        UUID clienteId,
        String produto,
        UUID tenantId,
        int versao,
        String situacao,
        boolean acessoLiberado,
        String motivoBloqueio,
        LocalDate vigenteAte,
        Plano plano,
        Map<String, Integer> limites,
        List<String> funcionalidades,
        Instant geradoEm
) {
    public record Plano(String codigo, String nome) {
    }
}
