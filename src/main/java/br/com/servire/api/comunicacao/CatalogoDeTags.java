package br.com.servire.api.comunicacao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Catálogo fixo de tags disponíveis para cada tipo de layout.
 * A única fonte de verdade sobre quais tags existem e como são preenchidas.
 * O {@link Renderizador} usa este catálogo para substituição e validação.
 */
public final class CatalogoDeTags {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Tag(String codigo, String descricao, Set<TipoLayout> tipos, Function<ContextoDeEnvio, String> valor) {
    }

    private static final List<Tag> TAGS;

    static {
        List<Tag> tags = new ArrayList<>();
        Set<TipoLayout> todos = Set.of(TipoLayout.values());
        Set<TipoLayout> semResponsavel = Set.of(TipoLayout.TODOS, TipoLayout.COROINHA, TipoLayout.ACOLITO,
                TipoLayout.COROINHA_ACOLITO, TipoLayout.MINISTRO);
        Set<TipoLayout> comIdade = Set.of(TipoLayout.TODOS, TipoLayout.COROINHA, TipoLayout.ACOLITO,
                TipoLayout.COROINHA_ACOLITO, TipoLayout.MINISTRO);
        Set<TipoLayout> apenasMinistro = Set.of(TipoLayout.MINISTRO);
        Set<TipoLayout> comDependentes = Set.of(TipoLayout.RESPONSAVEL, TipoLayout.TODOS);

        // Tags disponíveis para TODOS os tipos
        tags.add(new Tag("#PAROQUIA.NOME#", "Será trocado pelo nome da paróquia", todos,
                ctx -> ctx.paroquiaNome()));
        tags.add(new Tag("#PAROQUIA.CIDADE#", "Será trocado pela cidade da paróquia", todos,
                ctx -> ctx.paroquiaCidade()));
        tags.add(new Tag("#PAROQUIA.EMAIL#", "Será trocado pelo e-mail principal da paróquia", todos,
                ctx -> ctx.paroquiaEmail()));
        tags.add(new Tag("#PAROQUIA.TELEFONE#", "Será trocado pelo telefone principal da paróquia", todos,
                ctx -> ctx.paroquiaTelefone()));
        tags.add(new Tag("#DATA.HOJE#", "Será trocado pela data de hoje (DD/MM/AAAA)", todos,
                ctx -> LocalDate.now().format(FORMATO_DATA)));
        tags.add(new Tag("#PESSOA.NOME#", "Será trocado pelo nome completo da pessoa", todos,
                ctx -> ctx.pessoaNome()));
        tags.add(new Tag("#PESSOA.PRIMEIRO_NOME#", "Será trocado pelo primeiro nome da pessoa", todos,
                ctx -> primeiroNome(ctx.pessoaNome())));
        tags.add(new Tag("#PESSOA.NUMERO#", "Será trocado pelo número/sequencial da pessoa na paróquia", todos,
                ctx -> ctx.pessoaNumero()));
        tags.add(new Tag("#PESSOA.EMAIL#", "Será trocado pelo e-mail principal da pessoa", todos,
                ctx -> ctx.pessoaEmail()));
        tags.add(new Tag("#PESSOA.TELEFONE#", "Será trocado pelo telefone principal da pessoa", todos,
                ctx -> ctx.pessoaTelefone()));

        // Tags para Coroinha, Acólito, Coroinha/acólito, Ministro e Todos (com idade/nascimento)
        tags.add(new Tag("#PESSOA.IDADE#", "Será trocado pela idade atual da pessoa", comIdade,
                ctx -> ctx.pessoaNascimento() != null ? String.valueOf(calcularIdade(ctx.pessoaNascimento())) : ""));
        tags.add(new Tag("#PESSOA.NASCIMENTO#", "Será trocado pela data de nascimento da pessoa (DD/MM/AAAA)", comIdade,
                ctx -> ctx.pessoaNascimento() != null ? ctx.pessoaNascimento().format(FORMATO_DATA) : ""));
        tags.add(new Tag("#PESSOA.TIPO#", "Será trocado pelo tipo do voluntário (ex.: Coroinha, Acólito)", comIdade,
                ctx -> ctx.pessoaTipo()));
        tags.add(new Tag("#RESPONSAVEL.NOME#", "Será trocado pelo nome completo do responsável", comIdade,
                ctx -> ctx.responsavelNome() != null ? ctx.responsavelNome() : ""));
        tags.add(new Tag("#RESPONSAVEL.PRIMEIRO_NOME#", "Será trocado pelo primeiro nome do responsável", comIdade,
                ctx -> ctx.responsavelNome() != null ? primeiroNome(ctx.responsavelNome()) : ""));

        // Tag exclusiva de Ministro
        tags.add(new Tag("#PESSOA.MANDATO_FIM#", "Será trocado pela data de fim do mandato do ministro", apenasMinistro,
                ctx -> ctx.pessoaMandatoFim() != null ? ctx.pessoaMandatoFim() : ""));

        // Tag de dependentes (Responsável e Todos)
        tags.add(new Tag("#DEPENDENTES.NOMES#",
                "Será trocado pelos nomes dos dependentes separados por vírgula e \"e\" (ex.: Ana, Bruno e Carla)",
                comDependentes,
                ctx -> nomesLista(ctx.dependentesNomes())));

        // Tags exclusivas de Evento
        Set<TipoLayout> apenasEvento = Set.of(TipoLayout.EVENTO);
        tags.add(new Tag("#EVENTO.TITULO#", "Será trocado pelo título do evento", apenasEvento,
                ctx -> ctx.eventoTitulo() != null ? ctx.eventoTitulo() : ""));
        tags.add(new Tag("#EVENTO.DATA#", "Será trocado pela data de início (DD/MM/AAAA)", apenasEvento,
                ctx -> ctx.eventoData() != null ? ctx.eventoData() : ""));
        tags.add(new Tag("#EVENTO.HORA#", "Será trocado pela hora de início (HH:mm)", apenasEvento,
                ctx -> ctx.eventoHora() != null ? ctx.eventoHora() : ""));
        tags.add(new Tag("#EVENTO.QUANDO#", "Será trocado por \"hoje\", \"amanhã\" ou \"daqui a N dias\" (lembrete)", apenasEvento,
                ctx -> ctx.eventoQuando() != null ? ctx.eventoQuando() : ""));
        tags.add(new Tag("#EVENTO.LOCAL#", "Será trocado pelo nome do local", apenasEvento,
                ctx -> ctx.eventoLocal() != null ? ctx.eventoLocal() : ""));
        tags.add(new Tag("#EVENTO.ENDERECO#", "Será trocado pelo endereço completo", apenasEvento,
                ctx -> ctx.eventoEndereco() != null ? ctx.eventoEndereco() : ""));
        tags.add(new Tag("#EVENTO.MAPA#", "Será trocado pelo link do mapa", apenasEvento,
                ctx -> ctx.eventoMapa() != null ? ctx.eventoMapa() : ""));
        tags.add(new Tag("#EVENTO.RESPONSAVEL#", "Será trocado pelo responsável pelo evento", apenasEvento,
                ctx -> ctx.eventoResponsavel() != null ? ctx.eventoResponsavel() : ""));
        tags.add(new Tag("#EVENTO.TELEFONE#", "Será trocado pelo telefone do responsável", apenasEvento,
                ctx -> ctx.eventoTelefone() != null ? ctx.eventoTelefone() : ""));

        TAGS = List.copyOf(tags);
    }

    private CatalogoDeTags() {
    }

    /** Todas as tags do catálogo. */
    public static List<Tag> todas() {
        return TAGS;
    }

    /** Tags disponíveis para um tipo de layout, na ordem do catálogo. */
    public static List<Tag> tagsDo(TipoLayout tipo) {
        return TAGS.stream().filter(t -> t.tipos().contains(tipo)).toList();
    }

    /** Mapa código → tag para lookup O(1). */
    public static Map<String, Tag> porCodigo() {
        Map<String, Tag> mapa = new LinkedHashMap<>();
        for (Tag t : TAGS) {
            mapa.put(t.codigo(), t);
        }
        return mapa;
    }

    private static String primeiroNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) return "";
        return nomeCompleto.split("\\s+")[0];
    }

    private static int calcularIdade(LocalDate nascimento) {
        return (int) java.time.temporal.ChronoUnit.YEARS.between(nascimento, LocalDate.now());
    }

    private static String nomesLista(List<String> nomes) {
        if (nomes == null || nomes.isEmpty()) return "";
        if (nomes.size() == 1) return nomes.get(0);
        if (nomes.size() == 2) return nomes.get(0) + " e " + nomes.get(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nomes.size() - 1; i++) {
            if (i > 0) sb.append(", ");
            sb.append(nomes.get(i));
        }
        sb.append(" e ").append(nomes.get(nomes.size() - 1));
        return sb.toString();
    }
}
