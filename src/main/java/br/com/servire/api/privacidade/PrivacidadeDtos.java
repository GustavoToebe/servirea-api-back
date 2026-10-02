package br.com.servire.api.privacidade;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class PrivacidadeDtos {
    private PrivacidadeDtos() {}

    public record ConsentimentoItem(Privacidade.TipoConsentimento tipo, boolean concedido, String fonte, Instant registradoEm) {}

    public record PaginaConsentimentos(List<ConsentimentoItem> itens, long total, int pagina, int tamanho) {}

    public record ExecucaoItem(Instant executadoEm, Instant corte, int comunicadosAnonimizados) {}

    public record Retencao(Integer comunicadosDias, long versao, long elegiveis, List<ExecucaoItem> execucoes) {}

    public record AtualizarRetencao(@Min(30) @Max(3650) Integer comunicadosDias, @NotNull @Min(0) Long versao) {}

    public record ExecutarRetencao(@NotNull @Min(0) Long versao) {}

    public record ResultadoRetencao(Instant corte, int comunicadosAnonimizados) {}

    // ---- Exportação autorizada dos dados de uma pessoa

    public record ContatoExport(String tipo, String valor, boolean principal) {}

    public record RelacaoExport(String pessoa, String parentesco, boolean principal) {}

    public record VoluntarioExport(String tipo, boolean ativo, List<String> funcoes, boolean autorizaWhatsapp) {}

    public record ParticipacaoExport(LocalDate data, LocalTime horario, String celebracao, String funcao, String presenca, String resposta) {}

    public record ComunicacaoExport(String canal, String origem, String assunto, String situacao, Instant enviadoEm) {}

    public record CuidadosExport(List<String> condicoes, Integer nivelSuporteTea, String condicaoOutra, String cuidados) {}

    public record Exportacao(Instant geradaEm, UUID pessoaId, Long sequencial, String nome, List<String> papeis,
                             LocalDate dataNascimento, String sexo, String cpf, String rg, String endereco,
                             String observacoes, List<ContatoExport> emails, List<ContatoExport> telefones,
                             List<RelacaoExport> responsaveis, List<RelacaoExport> dependentes, VoluntarioExport voluntario,
                             List<ParticipacaoExport> participacoes, List<ConsentimentoItem> consentimentos,
                             List<ComunicacaoExport> comunicacoes, CuidadosExport cuidados) {}
}
