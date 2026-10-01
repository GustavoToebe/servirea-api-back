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
        List<String> dependentesNomes,
        // Evento (opcional)
        String eventoTitulo,
        String eventoData,
        String eventoHora,
        String eventoQuando,
        String eventoLocal,
        String eventoEndereco,
        String eventoMapa,
        String eventoResponsavel,
        String eventoTelefone
) {

    public ContextoDeEnvio(
            String paroquiaNome, String paroquiaCidade, String paroquiaUf, String paroquiaEmail, String paroquiaTelefone,
            String pessoaNome, String pessoaNumero, LocalDate pessoaNascimento, String pessoaTipo, String pessoaEmail,
            String pessoaTelefone, String pessoaMandatoFim, String responsavelNome, List<String> dependentesNomes) {
        this(paroquiaNome, paroquiaCidade, paroquiaUf, paroquiaEmail, paroquiaTelefone,
                pessoaNome, pessoaNumero, pessoaNascimento, pessoaTipo, pessoaEmail,
                pessoaTelefone, pessoaMandatoFim, responsavelNome, dependentesNomes,
                null, null, null, null, null, null, null, null, null);
    }

    /** Mesmo contexto, com os dados do evento preenchidos (layouts do tipo EVENTO). */
    public ContextoDeEnvio comEvento(String titulo, String data, String hora, String quando, String local,
                                     String endereco, String mapa, String responsavel, String telefone) {
        return new ContextoDeEnvio(paroquiaNome, paroquiaCidade, paroquiaUf, paroquiaEmail, paroquiaTelefone,
                pessoaNome, pessoaNumero, pessoaNascimento, pessoaTipo, pessoaEmail, pessoaTelefone, pessoaMandatoFim,
                responsavelNome, dependentesNomes,
                titulo, data, hora, quando, local, endereco, mapa, responsavel, telefone);
    }

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
                List.of("Pedro Souza", "Lucas Souza"),
                "Encontro Geral de Formação",
                "15/10/2026",
                "19:30",
                "amanhã",
                "Salão Paroquial",
                "Rua das Flores, 123 - Centro",
                "https://maps.app.goo.gl/exemplo",
                "Carlos Silva",
                "(11) 98888-7777"
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
                base.dependentesNomes(),
                base.eventoTitulo(),
                base.eventoData(),
                base.eventoHora(),
                base.eventoQuando(),
                base.eventoLocal(),
                base.eventoEndereco(),
                base.eventoMapa(),
                base.eventoResponsavel(),
                base.eventoTelefone()
        );
    }
}
