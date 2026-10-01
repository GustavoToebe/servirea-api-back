package br.com.servire.api.evento;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Corpo e respostas da API de eventos. */
public final class EventoDtos {

    private EventoDtos() {
    }

    public record EventoRequest(
            @NotBlank(message = "Informe o título.") @Size(max = 150, message = "Título com no máximo 150 letras.") String titulo,
            @Size(max = 5000, message = "Descrição com no máximo 5000 letras.") String descricao,
            @NotNull(message = "Informe a data e a hora de início.") LocalDateTime inicio,
            LocalDateTime termino,
            @Size(max = 150) String localNome,
            String cep,
            @Size(max = 200) String logradouro,
            @Size(max = 20) String numero,
            @Size(max = 100) String complemento,
            @Size(max = 100) String bairro,
            @Size(max = 100) String cidade,
            String uf,
            @Size(max = 500, message = "Link do mapa muito longo.") String mapaUrl,
            @Min(value = 1, message = "Vagas: deixe em branco para ilimitado ou informe 1 ou mais.") Integer vagas,
            @Size(max = 150) String responsavelNome,
            String responsavelTelefone,
            @Min(value = 0, message = "Lembrete: de 0 a 30 dias.") @Max(value = 30, message = "Lembrete: de 0 a 30 dias.") Integer lembreteDias,
            @Size(max = 2000, message = "Mensagem de confirmação muito longa.") String mensagemConfirmacao,
            @Size(max = 2000, message = "Mensagem de lembrete muito longa.") String mensagemLembrete) {
    }

    /** Situação na tela: ENCERRADO quando um publicado já aconteceu. */
    public enum SituacaoTela { RASCUNHO, PUBLICADO, ENCERRADO, CANCELADO }

    public record EventoResumo(UUID id, String titulo, LocalDateTime inicio, LocalDateTime termino, String localNome,
                               SituacaoTela situacao, long inscritos, Integer vagas, String capaUrl) {
    }

    public record FotoResponse(UUID id, String url, boolean capa) {
    }

    /** Situação de cada mensagem para o inscrito. */
    public enum SituacaoMensagem { PENDENTE, ENVIADA, FALHOU, SEM_AUTORIZACAO, SEM_TELEFONE }

    public record InscritoResponse(UUID id, UUID pessoaId, String nome, String telefone,
                                   SituacaoMensagem confirmacao, SituacaoMensagem lembrete) {
    }

    public record EventoDetalhe(UUID id, String titulo, String descricao, LocalDateTime inicio, LocalDateTime termino,
                                String localNome, String cep, String logradouro, String numero, String complemento,
                                String bairro, String cidade, String uf, String mapaUrl, Integer vagas,
                                String responsavelNome, String responsavelTelefone, int lembreteDias,
                                String mensagemConfirmacao, String mensagemLembrete, SituacaoTela situacao,
                                List<FotoResponse> fotos, List<InscritoResponse> inscritos, Map<String, String> tags) {
    }

    public record InscreverRequest(@NotNull(message = "Escolha a pessoa.") UUID pessoaId) {
    }

    public record CancelarRequest(boolean avisarInscritos) {
    }
}
