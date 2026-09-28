package br.com.servire.api.pessoa;

import br.com.servire.api.pessoa.dto.DuplicidadeRequest;
import br.com.servire.api.pessoa.dto.DuplicidadeResponse;
import br.com.servire.api.pessoa.dto.PessoaBasicoParaDuplicidade;
import br.com.servire.api.pessoa.dto.ResponsavelParaDuplicidade;
import br.com.servire.api.pessoa.dto.TelefoneParaDuplicidade;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class Duplicidades {

    private final PessoaRepository pessoaRepository;

    public Duplicidades(PessoaRepository pessoaRepository) {
        this.pessoaRepository = pessoaRepository;
    }

    public List<DuplicidadeResponse> verificar(DuplicidadeRequest request) {
        List<PessoaBasicoParaDuplicidade> basicos = pessoaRepository.findAllBasico();
        List<TelefoneParaDuplicidade> telefones = pessoaRepository.findAllTelefones();
        List<ResponsavelParaDuplicidade> responsaveis = pessoaRepository.findAllResponsaveis();

        Map<UUID, List<String>> telefonesPorPessoa = telefones.stream()
                .collect(Collectors.groupingBy(
                        TelefoneParaDuplicidade::pessoaId,
                        Collectors.mapping(t -> NomeNormalizado.digitos(t.numero()), Collectors.toList())
                ));

        Map<UUID, List<String>> responsaveisPorPessoa = responsaveis.stream()
                .collect(Collectors.groupingBy(
                        ResponsavelParaDuplicidade::voluntarioId,
                        Collectors.mapping(ResponsavelParaDuplicidade::nomeCompleto, Collectors.toList())
                ));

        String requestCpfDigitos = NomeNormalizado.digitos(request.cpf());
        Set<String> requestTelefonesDigitos = request.telefones() == null ? Set.of() :
                request.telefones().stream()
                        .map(NomeNormalizado::digitos)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
        List<String> requestResponsaveis = request.nomesResponsaveis() == null ? List.of() : request.nomesResponsaveis();

        List<DuplicidadeResponse> resultados = new ArrayList<>();

        for (PessoaBasicoParaDuplicidade p : basicos) {
            if (p.id().equals(request.ignorarId())) {
                continue;
            }

            List<String> motivos = new ArrayList<>();
            boolean temCpf = false;
            boolean temNome = false;
            boolean temNascimento = false;
            boolean temTelefone = false;
            boolean temResponsavel = false;

            // CPF
            String pCpfDigitos = NomeNormalizado.digitos(p.cpf());
            if (!requestCpfDigitos.isEmpty() && !pCpfDigitos.isEmpty() && requestCpfDigitos.equals(pCpfDigitos)) {
                motivos.add("CPF");
                temCpf = true;
            }

            // NOME
            if (NomeNormalizado.parecidos(request.nomeCompleto(), p.nomeCompleto())) {
                motivos.add("NOME");
                temNome = true;
            }

            // NASCIMENTO
            if (request.dataNascimento() != null && p.dataNascimento() != null && request.dataNascimento().equals(p.dataNascimento())) {
                motivos.add("NASCIMENTO");
                temNascimento = true;
            }

            // TELEFONE
            List<String> telefonesPessoa = telefonesPorPessoa.getOrDefault(p.id(), List.of());
            for (String t : telefonesPessoa) {
                if (!t.isEmpty() && requestTelefonesDigitos.contains(t)) {
                    motivos.add("TELEFONE");
                    temTelefone = true;
                    break;
                }
            }

            // RESPONSAVEL
            List<String> responsaveisPessoa = responsaveisPorPessoa.getOrDefault(p.id(), List.of());
            outerResp:
            for (String reqResp : requestResponsaveis) {
                for (String pResp : responsaveisPessoa) {
                    if (NomeNormalizado.parecidos(reqResp, pResp)) {
                        motivos.add("RESPONSAVEL");
                        temResponsavel = true;
                        break outerResp;
                    }
                }
            }

            // Regra: Entra no resultado só se: tem CPF, ou tem NOME, ou tem NASCIMENTO junto com TELEFONE ou RESPONSAVEL
            if (temCpf || temNome || (temNascimento && (temTelefone || temResponsavel))) {
                resultados.add(new DuplicidadeResponse(
                        p.id(),
                        p.sequencial(),
                        p.nomeCompleto(),
                        p.dataNascimento(),
                        motivos,
                        temCpf
                ));
            }
        }

        return resultados.stream()
                .sorted(Comparator.comparing(DuplicidadeResponse::bloqueia).reversed()
                        .thenComparing(r -> r.motivos().size(), Comparator.reverseOrder()))
                .limit(10)
                .collect(Collectors.toList());
    }
}
