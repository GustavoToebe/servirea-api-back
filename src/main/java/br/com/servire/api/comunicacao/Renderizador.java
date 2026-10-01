package br.com.servire.api.comunicacao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitui tags {@code #X.Y#} no conteúdo do layout pelos dados do
 * {@link ContextoDeEnvio}. E-mail: escapa o valor em HTML antes de inserir
 * (o conteúdo do layout é HTML do usuário, o dado da pessoa não pode injetar
 * HTML). WhatsApp: valor puro. Tag desconhecida fica como está.
 */
public final class Renderizador {

    private static final Pattern TAG_PATTERN = Pattern.compile("#[A-Z_]+\\.[A-Z_]+#");

    private Renderizador() {
    }

    /**
     * Renderiza o conteúdo substituindo todas as tags conhecidas.
     *
     * @param conteudo conteúdo do layout (HTML para e-mail, texto para WhatsApp)
     * @param envio    tipo de envio (define se o valor é escapado em HTML)
     * @param ctx      dados da pessoa destinatária e da paróquia
     * @return conteúdo com tags substituídas
     */
    public static String renderizar(String conteudo, TipoEnvio envio, ContextoDeEnvio ctx) {
        if (conteudo == null) return "";
        Map<String, CatalogoDeTags.Tag> catalogo = CatalogoDeTags.porCodigo();
        Matcher m = TAG_PATTERN.matcher(conteudo);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String codigo = m.group();
            CatalogoDeTags.Tag tag = catalogo.get(codigo);
            if (tag == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(codigo));
            } else {
                String valor = tag.valor().apply(ctx);
                if (valor == null) valor = "";
                if (envio == TipoEnvio.EMAIL) {
                    valor = escaparHtml(valor);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(valor));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Como {@link #renderizar}, mas a linha que tem tag e cujas tags ficaram todas vazias some da mensagem
     * (ex.: "Mapa: #EVENTO.MAPA#" num evento sem mapa). Pensado para texto de WhatsApp, que é uma tag por linha.
     */
    public static String renderizarSemLinhasVazias(String conteudo, TipoEnvio envio, ContextoDeEnvio ctx) {
        if (conteudo == null) return "";
        Map<String, CatalogoDeTags.Tag> catalogo = CatalogoDeTags.porCodigo();
        List<String> linhas = new ArrayList<>();
        for (String linha : conteudo.split("\n", -1)) {
            Matcher m = TAG_PATTERN.matcher(linha);
            boolean temTag = false;
            boolean algumaCheia = false;
            while (m.find()) {
                CatalogoDeTags.Tag tag = catalogo.get(m.group());
                if (tag == null) continue;
                temTag = true;
                String valor = tag.valor().apply(ctx);
                if (valor != null && !valor.isBlank()) algumaCheia = true;
            }
            if (temTag && !algumaCheia) continue;
            linhas.add(renderizar(linha, envio, ctx).stripTrailing());
        }
        return String.join("\n", linhas).strip();
    }

    /**
     * Tags presentes no conteúdo (regex {@code #[A-Z_]+.[A-Z_]+#}) que não
     * existem para o tipo de layout informado.
     */
    public static List<String> tagsDesconhecidas(String conteudo, TipoLayout tipoLayout) {
        if (conteudo == null) return List.of();
        List<String> disponíveis = CatalogoDeTags.tagsDo(tipoLayout)
                .stream().map(CatalogoDeTags.Tag::codigo).toList();
        List<String> desconhecidas = new ArrayList<>();
        Matcher m = TAG_PATTERN.matcher(conteudo);
        while (m.find()) {
            String codigo = m.group();
            if (!disponíveis.contains(codigo) && !desconhecidas.contains(codigo)) {
                desconhecidas.add(codigo);
            }
        }
        return desconhecidas;
    }

    private static String escaparHtml(String valor) {
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
