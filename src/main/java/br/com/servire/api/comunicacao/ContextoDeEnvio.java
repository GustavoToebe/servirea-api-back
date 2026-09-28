package br.com.servire.api.comunicacao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dados já resolvidos de paróquia, pessoa, responsável e dependentes
 * usados pelo {@link Renderizador} para substituir as tags no conteúdo do layout.
 */
public record ContextoDeEnvio(
        // Paróquia
        String paroquiaNome,
        String paroquiaCidade,
        String paroquiaUf,
        String paroquiaEmail,
        String paroquiaTelefone,
        // Pessoa destinatária
        String pessoaNome,
        String pessoaNumero,
        LocalDate pessoaNascimento,
        String pessoaTipo,
        String pessoaEmail,
        String pessoaTelefone,
        String pessoaMandatoFim,
        // Responsável (nulo quando não há)
        String responsavelNome,
        // Dependentes
        List<String> dependentesNomes
) {

    /**
     * Contexto com dados fictícios para a pré-visualização de layout.
     * A paróquia pode ser sobrescrita com os dados reais do tenant.
     */
    public static ContextoDeEnvio exemplo() {
        return new ContextoDeEnvio(
                "Paróquia São José",
                "São Paulo",
                "SP",
                "secretaria@sao-jose.org.br",
                "(11) 3333-4444",
                "Ana Beatriz Souza",
                "42",
                LocalDate.of(2012, 3, 15),
                "Coroinha",
                "ana.beatriz@email.com",
                "(11) 99999-8888",
                "31/12/2026",
                "Maria Souza",
                List.of("Pedro Souza", "Lucas Souza")
        );
    }

    public static ContextoDeEnvio exemplo(String paroquiaNome, String paroquiaCidade, String paroquiaUf) {
        ContextoDeEnvio base = exemplo();
        return new ContextoDeEnvio(
                paroquiaNome != null ? paroquiaNome : base.paroquiaNome(),
                paroquiaCidade != null ? paroquiaCidade : base.paroquiaCidade(),
                paroquiaUf != null ? paroquiaUf : base.paroquiaUf(),
                base.paroquiaEmail(),
                base.paroquiaTelefone(),
                base.pessoaNome(),
                base.pessoaNumero(),
                base.pessoaNascimento(),
                base.pessoaTipo(),
                base.pessoaEmail(),
                base.pessoaTelefone(),
                base.pessoaMandatoFim(),
                base.responsavelNome(),
                base.dependentesNomes()
        );
    }
}
