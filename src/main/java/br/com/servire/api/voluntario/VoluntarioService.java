package br.com.servire.api.voluntario;

import br.com.servire.api.voluntario.dto.ResponsavelRequest;
import br.com.servire.api.voluntario.dto.VoluntarioRequest;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Regras de negócio de voluntário/responsável (seção 37/38/39/106 do
 * plano mestre).
 *
 * <p>{@link #criar} e {@link #atualizar} são {@code @Transactional}
 * porque voluntário + responsáveis precisam ser consistentes juntos
 * (seção 39) — se a validação de responsável principal falhar depois de
 * já ter mexido no voluntário, a transação inteira desfaz. O arquivo
 * físico da foto (Storage, Fase 7) não participa dessa transação SQL,
 * como já avisa a seção 39 — não é responsabilidade desta classe ainda.</p>
 */
@Service
public class VoluntarioService {

    private final VoluntarioRepository voluntarioRepository;

    public VoluntarioService(VoluntarioRepository voluntarioRepository) {
        this.voluntarioRepository = voluntarioRepository;
    }

    @Transactional(readOnly = true)
    public List<Voluntario> buscar(Boolean ativo, TipoVoluntario tipo, String nome) {
        return voluntarioRepository.buscar(ativo, tipo, nome);
    }

    @Transactional(readOnly = true)
    public long contarPorAtivo(boolean ativo) {
        return voluntarioRepository.countByAtivo(ativo);
    }

    @Transactional(readOnly = true)
    public Voluntario buscarPorId(UUID id) {
        return voluntarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voluntário não encontrado."));
    }

    @Transactional
    public Voluntario criar(VoluntarioRequest request) {
        Voluntario voluntario = new Voluntario(request.nomeCompleto());
        aplicarCampos(voluntario, request);
        substituirResponsaveis(voluntario, request.responsaveis());
        return voluntarioRepository.save(voluntario);
    }

    @Transactional
    public Voluntario atualizar(UUID id, VoluntarioRequest request) {
        Voluntario voluntario = buscarPorId(id);
        aplicarCampos(voluntario, request);
        substituirResponsaveis(voluntario, request.responsaveis());
        return voluntarioRepository.save(voluntario);
    }

    @Transactional
    public Voluntario setAtivo(UUID id, boolean ativo) {
        Voluntario voluntario = buscarPorId(id);
        voluntario.setAtivo(ativo);
        return voluntario;
    }

    private void aplicarCampos(Voluntario voluntario, VoluntarioRequest request) {
        voluntario.setNomeCompleto(request.nomeCompleto());
        voluntario.setDataNascimento(request.dataNascimento());
        voluntario.setTipo(request.tipo());
        voluntario.setAtivo(request.ativo());
        voluntario.setEtapaCatequese(request.etapaCatequese());
        voluntario.setEucaristiaAno(request.eucaristiaAno());
        voluntario.setCrismaAno(request.crismaAno());
        voluntario.setRua(request.rua());
        voluntario.setNumero(request.numero());
        voluntario.setBairro(request.bairro());
        voluntario.setTelefone(request.telefone());
        voluntario.setCelular(request.celular());
        voluntario.setEmail(request.email());
        voluntario.setHorarioEstudo(request.horarioEstudo());
        voluntario.setObservacoes(request.observacoes());
        voluntario.setAutorizaWhatsapp(request.autorizaWhatsapp());
        List<FuncaoEscala> funcoes = request.funcoesHabilitadas();
        voluntario.setFuncoesHabilitadas(funcoes == null ? new FuncaoEscala[0] : funcoes.toArray(new FuncaoEscala[0]));
    }

    /**
     * Substitui TODOS os responsáveis do voluntário pela lista recebida —
     * mesmo padrão "apaga tudo e reinsere" do Angular atual
     * ({@code replaceResponsaveis}). {@code clear()} seguido de
     * reinserção na mesma coleção gerenciada pelo Hibernate (em vez de
     * criar uma lista nova) é o que faz o {@code orphanRemoval = true}
     * de {@link Voluntario#getResponsaveis()} de fato deletar os antigos
     * no flush.
     */
    private void substituirResponsaveis(Voluntario voluntario, List<ResponsavelRequest> requests) {
        long principais = requests.stream().filter(ResponsavelRequest::principal).count();
        if (principais != 1) {
            throw new BadRequestException(
                    "Deve existir exatamente um responsável principal (seção 38 do plano mestre) — recebido: " + principais + ".");
        }
        voluntario.getResponsaveis().clear();
        for (ResponsavelRequest r : requests) {
            Responsavel responsavel = new Responsavel(r.parentesco(), r.nome(), r.telefone(), r.celular(), r.email(), r.principal());
            responsavel.setVoluntario(voluntario);
            voluntario.getResponsaveis().add(responsavel);
        }
    }
}
