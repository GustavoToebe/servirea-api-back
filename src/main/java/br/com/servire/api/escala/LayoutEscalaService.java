package br.com.servire.api.escala;

import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LayoutEscalaService {
    private final LayoutEscalaRepository repository;

    public LayoutEscalaService(LayoutEscalaRepository repository) {
        this.repository = repository;
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
        LayoutEscala layout = new LayoutEscala(dto.nome(), dto.tipo(), dto.colunas(), false);
        return repository.save(layout);
    }

    @Transactional
    public LayoutEscala atualizar(UUID id, LayoutEscalaDto dto) {
        LayoutEscala layout = buscar(id);
        layout.setNome(dto.nome());
        layout.setTipo(dto.tipo());
        layout.setColunas(dto.colunas());
        if (!dto.ativo() && layout.isAtivo()) {
            if (repository.countByTipoAndAtivoTrue(layout.getTipo()) <= 1) {
                throw new BadRequestException("Não é possível desativar o último layout ativo deste tipo.");
            }
        }
        layout.setAtivo(dto.ativo());
        return repository.save(layout);
    }

    @Transactional
    public void excluir(UUID id) {
        LayoutEscala layout = buscar(id);
        if (layout.isSistema()) {
            throw new BadRequestException("Não é possível excluir um layout de sistema.");
        }
        if (layout.isAtivo()) {
            if (repository.countByTipoAndAtivoTrue(layout.getTipo()) <= 1) {
                throw new BadRequestException("Não é possível excluir o último layout ativo deste tipo.");
            }
        }
        repository.delete(layout);
    }
}
