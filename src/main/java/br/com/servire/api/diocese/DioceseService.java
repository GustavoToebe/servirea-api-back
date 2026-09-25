package br.com.servire.api.diocese;

import br.com.servire.api.backoffice.BackofficeLogService;
import br.com.servire.api.diocese.dto.DioceseRequest;
import br.com.servire.api.diocese.dto.DioceseResponse;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Catálogo de dioceses no painel. Tabela global, como {@code plano}. */
@Service
public class DioceseService {

    private final DioceseRepository dioceseRepository;
    private final CotaDioceseService cotaDioceseService;
    private final BackofficeLogService backofficeLogService;

    public DioceseService(DioceseRepository dioceseRepository,
                          CotaDioceseService cotaDioceseService,
                          BackofficeLogService backofficeLogService) {
        this.dioceseRepository = dioceseRepository;
        this.cotaDioceseService = cotaDioceseService;
        this.backofficeLogService = backofficeLogService;
    }

    @Transactional(readOnly = true)
    public List<DioceseResponse> listar() {
        return dioceseRepository.findAll(Sort.by("nome")).stream()
                .map(d -> DioceseResponse.de(d, cotaDioceseService.uso(d.getId())))
                .toList();
    }

    @Transactional
    public DioceseResponse criar(DioceseRequest request) {
        Diocese diocese = new Diocese(request.nome().trim());
        aplicar(diocese, request);
        try {
            diocese = dioceseRepository.saveAndFlush(diocese);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe uma diocese com este nome.");
        }
        backofficeLogService.registrar("CRIAR", "DIOCESE", diocese.getId(), null);
        return DioceseResponse.de(diocese, 0);
    }

    @Transactional
    public DioceseResponse atualizar(UUID id, DioceseRequest request) {
        Diocese diocese = dioceseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diocese não encontrada."));
        aplicar(diocese, request);
        try {
            dioceseRepository.saveAndFlush(diocese);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe uma diocese com este nome.");
        }
        backofficeLogService.registrar("ATUALIZAR", "DIOCESE", diocese.getId(), null);
        return DioceseResponse.de(diocese, cotaDioceseService.uso(diocese.getId()));
    }

    private static void aplicar(Diocese diocese, DioceseRequest request) {
        diocese.setNome(request.nome().trim());
        String uf = request.uf();
        diocese.setUf(uf == null || uf.isBlank() ? null : uf.trim().toUpperCase());
        diocese.setCotaVoluntarios(request.cotaVoluntarios());
    }
}
