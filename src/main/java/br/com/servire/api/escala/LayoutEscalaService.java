package br.com.servire.api.escala;

import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Layouts de escala da paróquia.
 *
 * <p>Regras (30/09/2026): layout de sistema nunca é excluído; layout <b>inativo</b> continua na lista de layouts mas não é
 * oferecido na montagem da escala; sempre sobra ao menos um layout ativo de cada modelo; há no máximo um layout
 * <b>padrão</b> por modelo e ele precisa estar ativo; layout que já foi usado por uma escala só se inativa, não se exclui.
 */
@Service
public class LayoutEscalaService {
    private final LayoutEscalaRepository repository;
    private final EscalaRepository escalaRepository;

    public LayoutEscalaService(LayoutEscalaRepository repository, EscalaRepository escalaRepository) {
        this.repository = repository;
        this.escalaRepository = escalaRepository;
    }

    @Transactional(readOnly = true)
    public List<LayoutEscala> listar() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public LayoutEscala buscar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Layout não encontrado"));
    }

    @Transactional
    public LayoutEscala criar(LayoutEscalaDto dto) {
        String nome = nomeValido(dto);
        if (repository.existsByTipoAndNomeIgnoreCase(dto.tipo(), nome)) {
            throw new ConflictException("Já existe um layout com esse nome neste modelo.");
        }
        boolean ativo = dto.ativo() == null || dto.ativo();
        boolean padrao = Boolean.TRUE.equals(dto.padrao());
        if (padrao && !ativo) {
            throw new BadRequestException("Um layout inativo não pode ser o padrão.");
        }
        LayoutEscala layout = new LayoutEscala(nome, dto.tipo(), dto.colunas(), false);
        layout.setDescricao(descricaoLimpa(dto.descricao()));
        layout.setAtivo(ativo);
        if (padrao) {
            desmarcarPadrao(dto.tipo());
            layout.setPadrao(true);
        }
        return repository.save(layout);
    }

    @Transactional
    public LayoutEscala atualizar(UUID id, LayoutEscalaDto dto) {
        LayoutEscala layout = buscar(id);
        String nome = nomeValido(dto);
        boolean tipoMudou = dto.tipo() != layout.getTipo();
        if (tipoMudou && layout.isSistema()) {
            throw new BadRequestException("O modelo (semanal/mensal) de um layout de sistema não pode ser alterado.");
        }
        if (repository.existsByTipoAndNomeIgnoreCaseAndIdNot(dto.tipo(), nome, id)) {
            throw new ConflictException("Já existe um layout com esse nome neste modelo.");
        }
        boolean ativo = dto.ativo() == null ? layout.isAtivo() : dto.ativo();

        // Sai do conjunto de ativos do modelo antigo se for inativado ou mudar de modelo.
        if (layout.isAtivo() && (!ativo || tipoMudou) && repository.countByTipoAndAtivoTrue(layout.getTipo()) <= 1) {
            throw new BadRequestException("Não é possível inativar nem mudar o último layout ativo deste modelo.");
        }

        boolean quer = dto.padrao() == null ? layout.isPadrao() && !tipoMudou : dto.padrao();
        if (quer && !ativo) {
            if (Boolean.TRUE.equals(dto.padrao())) {
                throw new BadRequestException("Um layout inativo não pode ser o padrão.");
            }
            quer = false; // inativou o padrão sem pedir: ele deixa de ser padrão (cai no layout de sistema).
        }

        layout.setNome(nome);
        layout.setDescricao(descricaoLimpa(dto.descricao()));
        layout.setTipo(dto.tipo());
        layout.setColunas(dto.colunas());
        layout.setAtivo(ativo);
        if (quer && !layout.isPadrao()) {
            desmarcarPadrao(dto.tipo());
        }
        layout.setPadrao(quer);
        return repository.save(layout);
    }

    @Transactional
    public void excluir(UUID id) {
        LayoutEscala layout = buscar(id);
        if (layout.isSistema()) {
            throw new BadRequestException("Não é possível excluir um layout de sistema.");
        }
        if (escalaRepository.existsByLayoutId(id)) {
            throw new ConflictException("Este layout já foi usado em escalas. Inative-o para que deixe de aparecer.");
        }
        if (layout.isAtivo() && repository.countByTipoAndAtivoTrue(layout.getTipo()) <= 1) {
            throw new BadRequestException("Não é possível excluir o último layout ativo deste modelo.");
        }
        repository.delete(layout);
    }

    /** Tira a marca de padrão dos outros e grava antes: o índice único parcial não admite dois ao mesmo tempo. */
    private void desmarcarPadrao(TipoEscala tipo) {
        List<LayoutEscala> atuais = repository.findByTipoAndPadraoTrue(tipo);
        if (atuais.isEmpty()) return;
        atuais.forEach(l -> l.setPadrao(false));
        repository.saveAllAndFlush(atuais);
    }

    private static String nomeValido(LayoutEscalaDto dto) {
        if (dto.nome() == null || dto.nome().isBlank()) {
            throw new BadRequestException("Informe o nome do layout.");
        }
        if (dto.tipo() == null) {
            throw new BadRequestException("Informe o modelo do layout (semanal ou mensal).");
        }
        return dto.nome().trim();
    }

    private static String descricaoLimpa(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.trim();
    }
}
