package br.com.servire.api.backoffice.dto;

import br.com.servire.api.billing.Periodicidade;
import br.com.servire.api.billing.dto.SituacaoFinanceira;
import br.com.servire.api.pessoa.dto.ContatoEmailResponse;
import br.com.servire.api.pessoa.dto.ContatoTelefoneResponse;
import br.com.servire.api.tenant.Tenant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Paróquia no painel. Os quatro últimos campos vêm do financeiro (V029).
 */
public record ParoquiaAdminResponse(
        UUID id,
        String codigo,
        String slug,
        String nome,
        String razaoSocial,
        String cnpj,
        Tenant.Status status,
        List<ContatoEmailResponse> emails,
        List<ContatoTelefoneResponse> telefones,
        String cep,
        String cidade,
        String uf,
        UUID dioceseId,
        String dioceseNome,
        String bairro,
        String logradouro,
        String numero,
        String complemento,
        String observacoes,
        Instant ultimoPagamentoEm,
        LocalDate vigenciaAte,
        Instant createdAt,
        String planoNome,
        Periodicidade periodicidade,
        long cobrancasVencidas,
        long diasAtraso
) {

    public static ParoquiaAdminResponse de(Tenant t, SituacaoFinanceira financeiro) {
        SituacaoFinanceira f = financeiro == null ? SituacaoFinanceira.VAZIA : financeiro;
        return new ParoquiaAdminResponse(
                t.getId(), t.getCodigo(), t.getSlug(), t.getNome(),
                t.getRazaoSocial(), t.getCnpj(), t.getStatus(),
                t.getEmails().stream()
                        .map(e -> new ContatoEmailResponse(e.getId(), e.getTipo(), e.getEmail(), e.isPrincipal()))
                        .toList(),
                t.getTelefones().stream()
                        .map(tel -> new ContatoTelefoneResponse(tel.getId(), tel.getTipo(), tel.getNumero(), tel.isPrincipal()))
                        .toList(),
                t.getCep(), t.getCidade(), t.getUf(),
                t.getDiocese() == null ? null : t.getDiocese().getId(),
                t.getDiocese() == null ? null : t.getDiocese().getNome(),
                t.getBairro(), t.getLogradouro(), t.getNumero(), t.getComplemento(),
                t.getObservacoes(), t.getUltimoPagamentoEm(), t.getVigenciaAte(),
                t.getCreatedAt(),
                f.planoNome(), f.periodicidade(), f.cobrancasVencidas(), f.diasAtraso());
    }
}
