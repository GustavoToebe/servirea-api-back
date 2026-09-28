package br.com.servire.api.pessoa.dto;

import java.util.UUID;

/** Duas pessoas com algum responsável em comum. */
public record ParDeIrmaos(UUID pessoaId, UUID irmaoId) {
}
