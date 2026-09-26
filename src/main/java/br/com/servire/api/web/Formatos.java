package br.com.servire.api.web;

import java.util.Locale;
import java.util.Set;

/**
 * Validação e formato único de documentos e contatos (26/09/2026, pedido
 * depois do primeiro teste de telas: CPF "101175" salvava sem reclamar).
 *
 * <p>Cada método aceita o valor com ou sem pontuação, devolve {@code null}
 * para vazio e grava sempre no mesmo formato ({@code 123.456.789-09},
 * {@code (45) 99999-8888}, {@code 85800-000}): a tela mostra o que está no
 * banco sem formatar de novo, e o mesmo CPF digitado de dois jeitos é o
 * mesmo texto. Valor inválido vira {@link BadRequestException} com a
 * mensagem para o usuário. O front aplica as mesmas regras antes de enviar
 * ({@code shared/utils/formatos.ts}); esta classe é a que vale.</p>
 *
 * <p>Dado antigo, gravado antes desta regra, só é conferido quando a ficha é
 * salva de novo.</p>
 */
public final class Formatos {

    /** Exige domínio com ponto ({@code @Email} sozinho aceita {@code ana@servire}). */
    public static final String EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";

    private static final Set<String> UFS = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private Formatos() {
    }

    /** CPF com dígitos verificadores; recusa os onze dígitos iguais. */
    public static String cpf(String valor) {
        String d = digitos(valor);
        if (d == null) {
            return null;
        }
        if (d.length() != 11 || todosIguais(d) || !dvCpfConfere(d)) {
            throw new BadRequestException("CPF inválido.");
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    /**
     * CNPJ numérico ou alfanumérico (Receita Federal, a partir de julho de
     * 2026): as 12 primeiras posições aceitam 0-9 e A-Z, os dois dígitos
     * verificadores são numéricos e cada caractere vale o código ASCII menos 48.
     */
    public static String cnpj(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String c = valor.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-Z]", "");
        if (c.length() != 14 || !c.matches("[0-9A-Z]{12}[0-9]{2}") || todosIguais(c) || !dvCnpjConfere(c)) {
            throw new BadRequestException("CNPJ inválido.");
        }
        return c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8) + "/"
                + c.substring(8, 12) + "-" + c.substring(12);
    }

    /**
     * RG não tem regra nacional (cada estado tem o seu formato e nem todos têm
     * dígito verificador): só números e X, de 5 a 14 caracteres. Com 9, usa a
     * máscara mais comum ({@code 12.345.678-9}).
     */
    public static String rg(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String r = valor.toUpperCase(Locale.ROOT).replaceAll("[^0-9X]", "");
        if (r.length() < 5 || r.length() > 14 || r.indexOf('X') >= 0 && r.indexOf('X') != r.length() - 1) {
            throw new BadRequestException("RG inválido.");
        }
        if (r.length() == 9) {
            return r.substring(0, 2) + "." + r.substring(2, 5) + "." + r.substring(5, 8) + "-" + r.substring(8);
        }
        return r;
    }

    public static String cep(String valor) {
        String d = digitos(valor);
        if (d == null) {
            return null;
        }
        if (d.length() != 8) {
            throw new BadRequestException("CEP inválido.");
        }
        return d.substring(0, 5) + "-" + d.substring(5);
    }

    /** Sigla de um dos 27 estados, em maiúsculas. */
    public static String uf(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String uf = valor.trim().toUpperCase(Locale.ROOT);
        if (!UFS.contains(uf)) {
            throw new BadRequestException("UF inválida.");
        }
        return uf;
    }

    /**
     * Telefone brasileiro com DDD: fixo (10 dígitos) ou celular (11, começando
     * por 9). Aceita o +55 na frente e o descarta.
     */
    public static String telefone(String valor) {
        String d = digitos(valor);
        if (d == null) {
            return null;
        }
        if ((d.length() == 12 || d.length() == 13) && d.startsWith("55")) {
            d = d.substring(2);
        }
        boolean ddd = d.length() >= 2 && d.charAt(0) != '0' && d.charAt(1) != '0';
        boolean fixo = d.length() == 10 && d.charAt(2) >= '2' && d.charAt(2) <= '8';
        boolean celular = d.length() == 11 && d.charAt(2) == '9';
        if (!ddd || !(fixo || celular)) {
            throw new BadRequestException("Telefone inválido. Informe o DDD e o número.");
        }
        int corte = d.length() - 4;
        return "(" + d.substring(0, 2) + ") " + d.substring(2, corte) + "-" + d.substring(corte);
    }

    /** Masculino, Feminino ou Outro; aceita as iniciais e qualquer caixa. */
    public static String sexo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "m", "masculino" -> "Masculino";
            case "f", "feminino" -> "Feminino";
            case "o", "outro" -> "Outro";
            default -> throw new BadRequestException("Sexo inválido. Use Masculino, Feminino ou Outro.");
        };
    }

    private static String digitos(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.replaceAll("\\D", "");
    }

    private static boolean todosIguais(String s) {
        return s.chars().allMatch(c -> c == s.charAt(0));
    }

    private static boolean dvCpfConfere(String d) {
        return dvCpf(d, 9) == d.charAt(9) - '0' && dvCpf(d, 10) == d.charAt(10) - '0';
    }

    private static int dvCpf(String d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (d.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean dvCnpjConfere(String c) {
        return dvCnpj(c, 12) == c.charAt(12) - '0' && dvCnpj(c, 13) == c.charAt(13) - '0';
    }

    private static int dvCnpj(String c, int tamanho) {
        int soma = 0;
        int peso = tamanho - 7;
        for (int i = 0; i < tamanho; i++) {
            soma += (c.charAt(i) - '0') * peso;
            peso = peso == 2 ? 9 : peso - 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
