package br.com.servire.api.pessoa.dto;

import java.util.UUID;

/** Cartão "Aniversariantes" do Início: só dia e nome (sem ano, idade ou contato). */
public record AniversarianteResponse(UUID id, String nome, Integer dia) {
}
