package br.com.servire.api.pessoa;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class NomeNormalizado {
    
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHA_SPACE = Pattern.compile("[^a-z\\s]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final List<String> PARTICULAS = List.of("de", "da", "do", "das", "dos", "e");

    private NomeNormalizado() {}

    public static String normalizar(String nome) {
        if (nome == null || nome.isBlank()) return "";
        String s = Normalizer.normalize(nome, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");
        s = s.toLowerCase();
        s = NON_ALPHA_SPACE.matcher(s).replaceAll(" ");
        s = MULTIPLE_SPACES.matcher(s).replaceAll(" ");
        return Arrays.stream(s.split(" "))
                .filter(p -> !p.isBlank() && !PARTICULAS.contains(p))
                .collect(Collectors.joining(" "))
                .trim();
    }

    public static List<String> tokens(String nome) {
        String n = normalizar(nome);
        if (n.isEmpty()) return List.of();
        return Arrays.asList(n.split(" "));
    }

    public static boolean parecidos(String a, String b) {
        if (a == null || b == null) return false;
        String nA = normalizar(a);
        String nB = normalizar(b);
        if (nA.isEmpty() || nB.isEmpty()) return false;
        
        if (nA.equals(nB)) return true;
        
        List<String> tokensA = tokens(a);
        List<String> tokensB = tokens(b);
        
        if (tokensA.size() >= 2 && tokensB.size() >= 2) {
            if (tokensA.get(0).equals(tokensB.get(0)) && 
                tokensA.get(tokensA.size() - 1).equals(tokensB.get(tokensB.size() - 1))) {
                return true;
            }
        }
        
        int dist = levenshtein(nA, nB);
        int maxLen = Math.max(nA.length(), nB.length());
        double sim = 1.0 - ((double) dist / maxLen);
        if (sim >= 0.85) return true;
        
        return false;
    }
    
    private static int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++)
            costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    public static String digitos(String valor) {
        if (valor == null) return "";
        return valor.replaceAll("\\D", "");
    }
}
