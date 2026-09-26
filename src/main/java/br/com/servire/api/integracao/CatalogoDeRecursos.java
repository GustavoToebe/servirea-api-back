package br.com.servire.api.integracao;

import java.util.List;

/**
 * Limites e funcionalidades que o Servire entende nos direitos vindos da
 * Central (contrato v1, seção 5.5). A Central usa esta lista como sugestão
 * ao cadastrar recursos, para o código nunca divergir do que o app lê em
 * {@code limites}/{@code funcionalidades} (26/09/2026).
 *
 * <p>{@code aplicado} diz se o app já faz valer o recurso. Hoje nenhum é
 * aplicado ("limites modelados agora e aplicados depois"): ao passar a
 * aplicar um, trocar para {@code true} aqui no mesmo commit.
 */
public final class CatalogoDeRecursos {

    public enum Tipo {
        LIMITE,
        FUNCIONALIDADE
    }

    public record RecursoDoApp(String codigo, String nome, Tipo tipo, String unidade, boolean aplicado) {
    }

    public static final List<RecursoDoApp> RECURSOS = List.of(
            new RecursoDoApp("voluntarios", "Voluntários", Tipo.LIMITE, "pessoa", false),
            new RecursoDoApp("usuarios", "Usuários", Tipo.LIMITE, "usuário", false),
            new RecursoDoApp("armazenamento_mb", "Armazenamento", Tipo.LIMITE, "MB", false),
            new RecursoDoApp("ESCALAS", "Escalas", Tipo.FUNCIONALIDADE, null, false),
            new RecursoDoApp("INSCRICAO_PUBLICA", "Inscrição pública", Tipo.FUNCIONALIDADE, null, false));

    private CatalogoDeRecursos() {
    }
}
