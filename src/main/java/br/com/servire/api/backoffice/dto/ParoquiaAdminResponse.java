package br.com.servire.api.backoffice.dto;

import br.com.servire.api.billing.Periodicidade;
import br.com.servire.api.billing.dto.SituacaoFinanceira;
import br.com.servire.api.tenant.Tenant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Paróquia no painel. Os quatro últimos campos vêm do financeiro (V029):
 * plano da assinatura ativa e atraso — {@code null}/0 quando não há.
 */
public record ParoquiaAdminResponse(
        UUID id,
        String codigo,
        String slug,
        String nome,
        String razaoSocial,
        String cnpj,
        Tenant.Status status,
        String email,
        String telefone,
        String cep,
        String cidade,
        String uf,
        String bairro,
        String logradouro,
        String numero,
        String complemento,
        String observacoes,
        Instant ultimoPagamentoEm,
        LocalDate vigenciaAte,
        String tipoEmail,
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
                t.getEmail(), t.getTelefone(), t.getCep(), t.getCidade(), t.getUf(),
                t.getBairro(), t.getLogradouro(), t.getNumero(), t.getComplemento(),
                t.getObservacoes(), t.getUltimoPagamentoEm(), t.getVigenciaAte(),
                t.getTipoEmail(), t.getCreatedAt(),
                f.planoNome(), f.periodicidade(), f.cobrancasVencidas(), f.diasAtraso());
    }
}
