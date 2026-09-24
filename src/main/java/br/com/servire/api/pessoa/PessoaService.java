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
import java.util.List;
import java.util.UUID;

/**
 * Cadastro pessoa-primeiro. Papel é imutável. Relação só liga
 * RESPONSAVEL ↔ VOLUNTARIO; o voluntário exige exatamente um responsável
 * principal (seção 38).
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
            if (papel != null) {
                predicados.add(cb.equal(root.get("papel"), papel));
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
        validarRequest(request);
        Pessoa pessoa = new Pessoa(request.papel(), request.nomeCompleto().trim());
        aplicarIdentidade(pessoa, request);
        substituirContatos(pessoa, request.emails(), request.telefones());
        if (request.papel() == PessoaPapel.VOLUNTARIO) {
            aplicarPerfilVoluntario(pessoa, request.voluntario());
            substituirRelacoesDoVoluntario(pessoa, request.relacoes());
        } else {
            substituirRelacoesDoResponsavel(pessoa, request.relacoes());
        }
        pessoa = pessoaRepository.save(pessoa);
        auditLogService.registrar("CRIACAO", "PESSOA", pessoa.getId(), null);
        return buscarPorId(pessoa.getId());
    }

    @Transactional
    public Pessoa atualizar(UUID id, PessoaRequest request) {
        Pessoa pessoa = buscarPorId(id);
        if (pessoa.getPapel() != request.papel()) {
            throw new BadRequestException("O papel da pessoa não pode ser alterado.");
        }
        validarRequest(request);
        aplicarIdentidade(pessoa, request);
        substituirContatos(pessoa, request.emails(), request.telefones());
        if (pessoa.getPapel() == PessoaPapel.VOLUNTARIO) {
            aplicarPerfilVoluntario(pessoa, request.voluntario());
            substituirRelacoesDoVoluntario(pessoa, request.relacoes());
        } else {
            substituirRelacoesDoResponsavel(pessoa, request.relacoes());
        }
        pessoaRepository.save(pessoa);
        auditLogService.registrar("ATUALIZACAO", "PESSOA", id, List.of("identidade", "contatos", "relacoes"));
        return buscarPorId(id);
    }

    private void validarRequest(PessoaRequest request) {
        Contatos.exigirUmPrincipalEmail(request.emails());
        Contatos.exigirUmPrincipalTelefone(request.telefones());
        if (request.papel() == PessoaPapel.VOLUNTARIO) {
            if (request.voluntario() == null) {
                throw new BadRequestException("O bloco de voluntário é obrigatório quando o papel é VOLUNTARIO.");
            }
            List<RelacaoRequest> relacoes = request.relacoes() == null ? List.of() : request.relacoes();
            long principais = relacoes.stream().filter(RelacaoRequest::principal).count();
            if (principais != 1) {
                throw new BadRequestException(
                        "Deve existir exatamente um responsável principal (seção 38) — recebido: "
                                + principais + ".");
            }
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
        for (RelacaoRequest r : requests) {
            Pessoa responsavel = buscarPorId(r.pessoaId());
            if (responsavel.getPapel() != PessoaPapel.RESPONSAVEL) {
                throw new BadRequestException("A relação do voluntário precisa apontar para um RESPONSAVEL.");
            }
            if (responsavel.getId().equals(voluntario.getId())) {
                throw new BadRequestException("Uma pessoa não pode se relacionar consigo mesma.");
            }
            PessoaRelacao relacao = new PessoaRelacao(responsavel, voluntario, r.parentesco().trim(),
                    opcional(r.parentescoInverso()), r.principal());
            voluntario.getResponsaveis().add(relacao);
        }
    }

    private void substituirRelacoesDoResponsavel(Pessoa responsavel, List<RelacaoRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            responsavel.getDependentes().clear();
            entityManager.flush();
            return;
        }
        responsavel.getDependentes().clear();
        entityManager.flush();
        for (RelacaoRequest r : requests) {
            Pessoa voluntario = buscarPorId(r.pessoaId());
            if (voluntario.getPapel() != PessoaPapel.VOLUNTARIO) {
                throw new BadRequestException("A relação do responsável precisa apontar para um VOLUNTARIO.");
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
