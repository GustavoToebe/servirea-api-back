package br.com.servire.api.pessoa;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.pessoa.dto.RelacaoRequest;
import br.com.servire.api.pessoa.dto.VoluntarioPerfilRequest;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro pessoa-primeiro. Papéis não são exclusivos e podem ser
 * acrescentados (nunca removidos). Relação responsável↔voluntário é
 * opcional — adulto/ministro entra sem responsável.
 */
@Service
public class PessoaService {

    private final PessoaRepository pessoaRepository;
    private final AuditLogService auditLogService;

    @PersistenceContext
    private EntityManager entityManager;

    public PessoaService(PessoaRepository pessoaRepository, AuditLogService auditLogService) {
        this.pessoaRepository = pessoaRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Pessoa> buscar(PessoaPapel papel, String nome) {
        List<Pessoa> pessoas = pessoaRepository.findAll(filtro(papel, nome), Sort.by(Sort.Direction.ASC, "nomeCompleto"));
        pessoas.forEach(p -> {
            p.getEmails().size();
            p.getTelefones().size();
        });
        return pessoas;
    }

    private static Specification<Pessoa> filtro(PessoaPapel papel, String nome) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (papel == PessoaPapel.VOLUNTARIO) {
                predicados.add(cb.isTrue(root.get("eVoluntario")));
            } else if (papel == PessoaPapel.RESPONSAVEL) {
                predicados.add(cb.isTrue(root.get("eResponsavel")));
            }
            if (nome != null && !nome.isBlank()) {
                predicados.add(cb.like(cb.lower(root.get("nomeCompleto")), "%" + nome.trim().toLowerCase() + "%"));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public Pessoa buscarPorId(UUID id) {
        return pessoaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));
    }

    @Transactional
    public Pessoa criar(PessoaRequest request) {
        Set<PessoaPapel> papeis = papeisDe(request);
        validarRequest(request, papeis);
        Pessoa pessoa = new Pessoa(papeis, request.nomeCompleto().trim());
        aplicarIdentidade(pessoa, request);
        substituirContatos(pessoa, request.emails(), request.telefones());
        if (pessoa.isVoluntario()) {
            aplicarPerfilVoluntario(pessoa, request.voluntario());
            substituirRelacoesDoVoluntario(pessoa, request.relacoes());
        }
        if (pessoa.isResponsavel()) {
            substituirRelacoesDoResponsavel(pessoa, request.relacoes(), pessoa.isVoluntario());
        }
        pessoa = pessoaRepository.save(pessoa);
        auditLogService.registrar("CRIACAO", "PESSOA", pessoa.getId(), null);
        return buscarPorId(pessoa.getId());
    }

    @Transactional
    public Pessoa atualizar(UUID id, PessoaRequest request) {
        Pessoa pessoa = buscarPorId(id);
        Set<PessoaPapel> novos = papeisDe(request);
        if (pessoa.isVoluntario() && !novos.contains(PessoaPapel.VOLUNTARIO)) {
            throw new BadRequestException("Não é possível remover o papel VOLUNTARIO.");
        }
        if (pessoa.isResponsavel() && !novos.contains(PessoaPapel.RESPONSAVEL)) {
            throw new BadRequestException("Não é possível remover o papel RESPONSAVEL.");
        }
        validarRequest(request, novos);
        pessoa.setPapeis(novos);
        aplicarIdentidade(pessoa, request);
        substituirContatos(pessoa, request.emails(), request.telefones());
        if (pessoa.isVoluntario()) {
            aplicarPerfilVoluntario(pessoa, request.voluntario());
            substituirRelacoesDoVoluntario(pessoa, request.relacoes());
        }
        if (pessoa.isResponsavel()) {
            substituirRelacoesDoResponsavel(pessoa, request.relacoes(), pessoa.isVoluntario());
        }
        pessoaRepository.save(pessoa);
        auditLogService.registrar("ATUALIZACAO", "PESSOA", id, List.of("identidade", "contatos", "relacoes", "papeis"));
        return buscarPorId(id);
    }

    private static Set<PessoaPapel> papeisDe(PessoaRequest request) {
        if (request.papeis() == null || request.papeis().isEmpty()) {
            throw new BadRequestException("Informe ao menos um papel (VOLUNTARIO e/ou RESPONSAVEL).");
        }
        return EnumSet.copyOf(request.papeis());
    }

    private void validarRequest(PessoaRequest request, Set<PessoaPapel> papeis) {
        Contatos.exigirUmPrincipalEmail(request.emails());
        Contatos.exigirUmPrincipalTelefone(request.telefones());
        if (papeis.contains(PessoaPapel.VOLUNTARIO) && request.voluntario() == null) {
            throw new BadRequestException("O bloco de voluntário é obrigatório quando o papel inclui VOLUNTARIO.");
        }
        validarPrincipaisDasRelacoes(request.relacoes());
    }

    /**
     * Relação é opcional. Se vier alguma, no máximo um principal — e se
     * houver mais de uma, exige exatamente um principal (contato da família).
     */
    private static void validarPrincipaisDasRelacoes(List<RelacaoRequest> relacoes) {
        List<RelacaoRequest> lista = relacoes == null ? List.of() : relacoes;
        if (lista.isEmpty()) {
            return;
        }
        long principais = lista.stream().filter(RelacaoRequest::principal).count();
        if (principais > 1) {
            throw new BadRequestException(
                    "Deve existir no máximo um responsável principal — recebido: " + principais + ".");
        }
        if (lista.size() > 1 && principais != 1) {
            throw new BadRequestException(
                    "Quando há mais de uma relação, deve existir exatamente um responsável principal — recebido: "
                            + principais + ".");
        }
    }

    private void aplicarIdentidade(Pessoa pessoa, PessoaRequest request) {
        pessoa.setNomeCompleto(request.nomeCompleto().trim());
        pessoa.setDataNascimento(request.dataNascimento());
        pessoa.setSexo(opcional(request.sexo()));
        pessoa.setCpf(opcional(request.cpf()));
        pessoa.setRg(opcional(request.rg()));
        pessoa.setCep(opcional(request.cep()));
        pessoa.setCidade(opcional(request.cidade()));
        pessoa.setUf(opcional(request.uf()));
        pessoa.setLogradouro(opcional(request.logradouro()));
        pessoa.setNumero(opcional(request.numero()));
        pessoa.setComplemento(opcional(request.complemento()));
        pessoa.setBairro(opcional(request.bairro()));
        pessoa.setObservacoes(opcional(request.observacoes()));
    }

    private void aplicarPerfilVoluntario(Pessoa pessoa, VoluntarioPerfilRequest perfil) {
        Voluntario voluntario = pessoa.getVoluntario();
        if (voluntario == null) {
            voluntario = new Voluntario();
            pessoa.setVoluntario(voluntario);
        }
        voluntario.setTipo(perfil.tipo());
        voluntario.setAtivo(perfil.ativo());
        voluntario.setEtapaCatequese(opcional(perfil.etapaCatequese()));
        voluntario.setEucaristiaAno(opcional(perfil.eucaristiaAno()));
        voluntario.setCrismaAno(opcional(perfil.crismaAno()));
        voluntario.setHorarioEstudo(perfil.horarioEstudo());
        voluntario.setAutorizaWhatsapp(perfil.autorizaWhatsapp());
        List<FuncaoEscala> funcoes = perfil.funcoesHabilitadas();
        voluntario.setFuncoesHabilitadas(funcoes == null ? new FuncaoEscala[0] : funcoes.toArray(new FuncaoEscala[0]));
    }

    private void substituirContatos(Pessoa pessoa, List<ContatoEmailRequest> emails,
                                    List<ContatoTelefoneRequest> telefones) {
        pessoa.getEmails().clear();
        pessoa.getTelefones().clear();
        entityManager.flush();
        if (emails != null) {
            for (ContatoEmailRequest e : emails) {
                PessoaEmail linha = new PessoaEmail(e.tipo().trim(), e.email().trim(), e.principal());
                linha.setPessoa(pessoa);
                pessoa.getEmails().add(linha);
            }
        }
        if (telefones != null) {
            for (ContatoTelefoneRequest t : telefones) {
                PessoaTelefone linha = new PessoaTelefone(t.tipo().trim(), t.numero().trim(), t.principal());
                linha.setPessoa(pessoa);
                pessoa.getTelefones().add(linha);
            }
        }
    }

    private void substituirRelacoesDoVoluntario(Pessoa voluntario, List<RelacaoRequest> requests) {
        voluntario.getResponsaveis().clear();
        entityManager.flush();
        if (requests == null) {
            return;
        }
        for (RelacaoRequest r : requests) {
            Pessoa responsavel = buscarPorId(r.pessoaId());
            if (!responsavel.isResponsavel()) {
                throw new BadRequestException("A relação do voluntário precisa apontar para alguém com papel RESPONSAVEL.");
            }
            if (responsavel.getId().equals(voluntario.getId())) {
                throw new BadRequestException("Uma pessoa não pode se relacionar consigo mesma.");
            }
            PessoaRelacao relacao = new PessoaRelacao(responsavel, voluntario, r.parentesco().trim(),
                    opcional(r.parentescoInverso()), r.principal());
            voluntario.getResponsaveis().add(relacao);
        }
    }

    /**
     * @param relacoesJaAplicadasNoVoluntario quando a pessoa é os dois papéis,
     *        as relações do request são as do lado voluntário; o lado
     *        responsável não é substituído pelo mesmo payload.
     */
    private void substituirRelacoesDoResponsavel(Pessoa responsavel, List<RelacaoRequest> requests,
                                                 boolean relacoesJaAplicadasNoVoluntario) {
        if (relacoesJaAplicadasNoVoluntario) {
            return;
        }
        responsavel.getDependentes().clear();
        entityManager.flush();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (RelacaoRequest r : requests) {
            Pessoa voluntario = buscarPorId(r.pessoaId());
            if (!voluntario.isVoluntario()) {
                throw new BadRequestException("A relação do responsável precisa apontar para alguém com papel VOLUNTARIO.");
            }
            PessoaRelacao relacao = new PessoaRelacao(responsavel, voluntario, r.parentesco().trim(),
                    opcional(r.parentescoInverso()), r.principal());
            responsavel.getDependentes().add(relacao);
        }
    }

    private static String opcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
