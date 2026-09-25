package br.com.servire.api.diocese;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Diocese como agrupamento informativo da paróquia (V037, 25/09/2026).
 * Não há cadastro próprio: a paróquia escolhe uma diocese já usada ou digita
 * um nome novo no {@code PUT /tenant}, e {@link #resolver(String)} reaproveita
 * ou cria.
 */
@Service
public class DioceseService {

    private final DioceseRepository dioceseRepository;

    public DioceseService(DioceseRepository dioceseRepository) {
        this.dioceseRepository = dioceseRepository;
    }

    @Transactional(readOnly = true)
    public List<Diocese> listarEmUso() {
        return dioceseRepository.listarEmUso();
    }

    /**
     * Nome vazio = sem diocese. Senão devolve a diocese com esse nome (sem
     * diferenciar maiúsculas e ignorando espaços repetidos), criando se não
     * existir. Roda na transação de quem chama.
     */
    @Transactional
    public Diocese resolver(String nome) {
        String normalizado = normalizar(nome);
        if (normalizado == null) {
            return null;
        }
        return dioceseRepository.findFirstByNomeIgnoreCase(normalizado).orElseGet(() -> {
            dioceseRepository.inserirSeNaoExiste(normalizado);
            return dioceseRepository.findFirstByNomeIgnoreCase(normalizado).orElseThrow();
        });
    }

    static String normalizar(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        return nome.trim().replaceAll("\\s+", " ");
    }
}
