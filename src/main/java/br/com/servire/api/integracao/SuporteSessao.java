package br.com.servire.api.integracao;

/**
 * Operador da Central na requisição atual. O filtro JWT preenche e limpa
 * na mesma thread; a auditoria lê daqui para gravar nome, e-mail e motivo.
 */
public final class SuporteSessao {

    private static final ThreadLocal<Dados> ATUAL = new ThreadLocal<>();

    private SuporteSessao() {
    }

    public record Dados(String nome, String email, String motivo) {
    }

    public static void set(Dados dados) {
        ATUAL.set(dados);
    }

    public static Dados atual() {
        return ATUAL.get();
    }

    public static void clear() {
        ATUAL.remove();
    }
}
