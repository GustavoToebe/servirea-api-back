package br.com.servire.api.pessoa.dto;

import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PessoaResponse(
        UUID id,
        Set<PessoaPapel> papeis,
        String nomeCompleto,
        LocalDate dataNascimento,
        String sexo,
        String cpf,
        String rg,
        List<ContatoEmailResponse> emails,
        List<ContatoTelefoneResponse> telefones,
        List<RelacaoResponse> responsaveis,
        List<RelacaoResponse> dependentes,
        String cep,
        String cidade,
        String uf,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String observacoes,
        VoluntarioPerfilResponse voluntario) {

    public static PessoaResponse de(Pessoa p) {
        List<RelacaoResponse> responsaveis = p.isVoluntario()
                ? p.getResponsaveis().stream().map(RelacaoResponse::doVoluntario).toList()
                : List.of();
        List<RelacaoResponse> dependentes = p.isResponsavel()
                ? p.getDependentes().stream().map(RelacaoResponse::doResponsavel).toList()
                : List.of();
        return new PessoaResponse(
                p.getId(),
                p.getPapeis(),
                p.getNomeCompleto(),
                p.getDataNascimento(),
                p.getSexo(),
                p.getCpf(),
                p.getRg(),
                p.getEmails().stream()
                        .map(e -> new ContatoEmailResponse(e.getId(), e.getTipo(), e.getEmail(), e.isPrincipal()))
                        .toList(),
                p.getTelefones().stream()
                        .map(t -> new ContatoTelefoneResponse(t.getId(), t.getTipo(), t.getNumero(), t.isPrincipal()))
                        .toList(),
                responsaveis,
                dependentes,
                p.getCep(),
                p.getCidade(),
                p.getUf(),
                p.getLogradouro(),
                p.getNumero(),
                p.getComplemento(),
                p.getBairro(),
                p.getObservacoes(),
                VoluntarioPerfilResponse.de(p.getVoluntario()));
    }
}
