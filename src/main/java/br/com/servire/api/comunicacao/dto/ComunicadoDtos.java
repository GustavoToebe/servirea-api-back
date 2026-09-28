package br.com.servire.api.comunicacao.dto;

import br.com.servire.api.comunicacao.Comunicado;
import br.com.servire.api.comunicacao.ComunicadoAnexo;
import br.com.servire.api.comunicacao.ComunicadoDestinatario;
import br.com.servire.api.comunicacao.EnviarPara;
import br.com.servire.api.comunicacao.QuaisContatos;
import br.com.servire.api.comunicacao.StatusComunicado;
import br.com.servire.api.comunicacao.StatusEnvio;
import br.com.servire.api.comunicacao.TipoEnvio;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contratos HTTP do comunicado (PLANO-005). */
public final class ComunicadoDtos {

    private ComunicadoDtos() {
    }

    public record DestinatariosRequest(
            @NotNull TipoEnvio canal,
            @NotEmpty @Size(max = 2000) List<UUID> pessoaIds,
            @NotNull EnviarPara enviarPara,
            @NotNull QuaisContatos contatos) {
    }

    /** {@code deQuem}: PESSOA ou RESPONSAVEL. */
    public record Destino(String nome, String endereco, String deQuem) {
    }

    /** {@code autorizaWhatsapp}: nulo quando a pessoa não é voluntária. */
    public record DestinatarioPrevia(UUID pessoaId, String nome, List<Destino> destinos, Boolean autorizaWhatsapp) {
    }

    public record PreVisualizarRequest(
            @NotNull UUID layoutId,
            @Size(max = 200) String assunto,
            @NotNull UUID pessoaId,
            @NotNull EnviarPara enviarPara) {
    }

    public record PreVisualizacao(String de, String para, String assunto, String conteudo) {
    }

    public record CriarRequest(
            @NotNull TipoEnvio canal,
            @NotNull UUID layoutId,
            @Size(max = 200) String assunto,
            @NotNull EnviarPara enviarPara,
            @NotNull QuaisContatos contatos,
            @NotEmpty @Size(max = 2000) List<UUID> pessoaIds) {
    }

    public record Criado(UUID id, int total) {
    }

    public record Resumo(UUID id, TipoEnvio canal, String layoutNome, String assunto, EnviarPara enviarPara,
                         StatusComunicado status, int total, int enviados, int falhas,
                         Instant createdAt, Instant concluidoEm) {
        public static Resumo de(Comunicado c) {
            return new Resumo(c.getId(), c.getCanal(), c.getLayoutNome(), c.getAssunto(), c.getEnviarPara(),
                    c.getStatus(), c.getTotal(), c.getEnviados(), c.getFalhas(), c.getCreatedAt(), c.getConcluidoEm());
        }
    }

    /** Sem o conteúdo de cada mensagem. */
    public record DestinatarioLinha(UUID id, String nome, String destino, StatusEnvio status, String erro, Instant enviadoEm) {
        public static DestinatarioLinha de(ComunicadoDestinatario d) {
            return new DestinatarioLinha(d.getId(), d.getNome(), d.getDestino(), d.getStatus(), d.getErro(), d.getEnviadoEm());
        }
    }

    public record AnexoLinha(UUID id, String nome, int tamanho) {
        public static AnexoLinha de(ComunicadoAnexo a) {
            return new AnexoLinha(a.getId(), a.getNome(), a.getTamanho());
        }
    }

    public record Detalhe(Resumo comunicado, List<DestinatarioLinha> destinatarios, List<AnexoLinha> anexos) {
    }
}
