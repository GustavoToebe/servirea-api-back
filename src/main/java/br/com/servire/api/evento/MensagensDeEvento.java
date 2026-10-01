package br.com.servire.api.evento;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textos de WhatsApp dos eventos. Modelo inspirado no aviso de consulta: saudação, data e hora, local,
 * mapa e contato. Cada evento guarda o seu texto (editável); linha cuja tag ficou vazia (ex.: sem mapa)
 * some da mensagem.
 */
public final class MensagensDeEvento {

    public static final String CONFIRMACAO_PADRAO = """
            Olá #PESSOA.NOME#!
            Sua inscrição no evento *#EVENTO.TITULO#* está confirmada.
            📅 #EVENTO.DATA# às #EVENTO.HORA#
            📍 #EVENTO.LOCAL#
            #EVENTO.ENDERECO#
            Mapa: #EVENTO.MAPA#
            Dúvidas: #EVENTO.RESPONSAVEL# #EVENTO.TELEFONE#
            #PAROQUIA.NOME#""";

    public static final String LEMBRETE_PADRAO = """
            Olá #PESSOA.NOME#! Lembrete: o evento *#EVENTO.TITULO#* é #EVENTO.QUANDO# (#EVENTO.DATA# às #EVENTO.HORA#).
            📍 #EVENTO.LOCAL#
            #EVENTO.ENDERECO#
            Mapa: #EVENTO.MAPA#
            Dúvidas: #EVENTO.RESPONSAVEL# #EVENTO.TELEFONE#
            #PAROQUIA.NOME#""";

    public static final String CANCELAMENTO = """
            Olá #PESSOA.NOME#. O evento *#EVENTO.TITULO#* de #EVENTO.DATA# foi cancelado.
            Dúvidas: #EVENTO.RESPONSAVEL# #EVENTO.TELEFONE#
            #PAROQUIA.NOME#""";

    /** Tags aceitas, com a descrição que a tela mostra. */
    public static final Map<String, String> TAGS = tags();

    private static final Pattern TAG = Pattern.compile("#[A-Z]+\\.[A-Z]+#");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private MensagensDeEvento() {
    }

    private static Map<String, String> tags() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("#PESSOA.NOME#", "Nome da pessoa inscrita");
        t.put("#EVENTO.TITULO#", "Título do evento");
        t.put("#EVENTO.DATA#", "Data de início (dd/mm/aaaa)");
        t.put("#EVENTO.HORA#", "Hora de início");
        t.put("#EVENTO.QUANDO#", "\"hoje\", \"amanhã\" ou \"daqui a N dias\" (lembrete)");
        t.put("#EVENTO.LOCAL#", "Nome do local");
        t.put("#EVENTO.ENDERECO#", "Endereço completo");
        t.put("#EVENTO.MAPA#", "Link do mapa");
        t.put("#EVENTO.RESPONSAVEL#", "Responsável pelo evento");
        t.put("#EVENTO.TELEFONE#", "Telefone do responsável");
        t.put("#PAROQUIA.NOME#", "Nome da paróquia");
        return java.util.Collections.unmodifiableMap(t);
    }

    /** Monta o texto para uma pessoa. {@code hoje} é a data de Brasília (para o "amanhã"). */
    public static String renderizar(String modelo, Evento e, String nomePessoa, String nomeParoquia, LocalDate hoje) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("#PESSOA.NOME#", primeiroNome(nomePessoa));
        valores.put("#EVENTO.TITULO#", e.getTitulo());
        valores.put("#EVENTO.DATA#", e.getInicio().format(DATA));
        valores.put("#EVENTO.HORA#", e.getInicio().format(HORA));
        valores.put("#EVENTO.QUANDO#", quando(hoje, e.getInicio().toLocalDate()));
        valores.put("#EVENTO.LOCAL#", e.getLocalNome());
        valores.put("#EVENTO.ENDERECO#", endereco(e));
        valores.put("#EVENTO.MAPA#", e.getMapaUrl());
        valores.put("#EVENTO.RESPONSAVEL#", e.getResponsavelNome());
        valores.put("#EVENTO.TELEFONE#", e.getResponsavelTelefone());
        valores.put("#PAROQUIA.NOME#", nomeParoquia);

        List<String> linhas = new ArrayList<>();
        for (String linha : modelo.split("\n", -1)) {
            Matcher m = TAG.matcher(linha);
            boolean temTag = false;
            boolean algumaCheia = false;
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String valor = valores.get(m.group());
                if (valor == null && !valores.containsKey(m.group())) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                    continue;
                }
                temTag = true;
                if (valor != null && !valor.isBlank()) algumaCheia = true;
                m.appendReplacement(sb, Matcher.quoteReplacement(valor == null ? "" : valor.trim()));
            }
            m.appendTail(sb);
            if (temTag && !algumaCheia) continue;
            linhas.add(sb.toString().replaceAll("[ \\t]+$", ""));
        }
        return String.join("\n", linhas).trim();
    }

    static String quando(LocalDate hoje, LocalDate dia) {
        long dias = ChronoUnit.DAYS.between(hoje, dia);
        if (dias <= 0) return "hoje";
        if (dias == 1) return "amanhã";
        return "daqui a " + dias + " dias";
    }

    static String endereco(Evento e) {
        StringBuilder sb = new StringBuilder();
        if (vazio(e.getLogradouro())) return null;
        sb.append(e.getLogradouro().trim());
        if (!vazio(e.getNumero())) sb.append(", ").append(e.getNumero().trim());
        if (!vazio(e.getComplemento())) sb.append(" – ").append(e.getComplemento().trim());
        if (!vazio(e.getBairro())) sb.append(" – ").append(e.getBairro().trim());
        if (!vazio(e.getCidade())) {
            sb.append(" – ").append(e.getCidade().trim());
            if (!vazio(e.getUf())) sb.append("/").append(e.getUf().trim());
        }
        return sb.toString();
    }

    private static String primeiroNome(String nome) {
        if (vazio(nome)) return "";
        return nome.trim().split("\\s+")[0];
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
