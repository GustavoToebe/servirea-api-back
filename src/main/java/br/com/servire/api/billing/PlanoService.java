package br.com.servire.api.billing;

import br.com.servire.api.backoffice.BackofficeLogService;
import br.com.servire.api.billing.dto.NovoPrecoRequest;
import br.com.servire.api.billing.dto.PlanoRequest;
import br.com.servire.api.billing.dto.PlanoResponse;
import br.com.servire.api.billing.dto.PrecoResponse;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Catálogo de planos e preços (seção 63). Preço nunca é editado: reajuste
 * é linha nova com {@code vigenteDesde} maior — contratos antigos ficam
 * com o valor copiado na assinatura.
 */
@Service
public class PlanoService {

    private final PlanoRepository planoRepository;
    private final PrecoPlanoRepository precoPlanoRepository;
    private final BackofficeLogService backofficeLogService;
    private final Clock clock;

    public PlanoService(PlanoRepository planoRepository,
                        PrecoPlanoRepository precoPlanoRepository,
                        BackofficeLogService backofficeLogService,
                        Clock clock) {
        this.planoRepository = planoRepository;
        this.precoPlanoRepository = precoPlanoRepository;
        this.backofficeLogService = backofficeLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlanoResponse> listar() {
        return planoRepository.findAllByOrderByNomeAsc().stream().map(this::resposta).toList();
    }

    @Transactional
    public PlanoResponse criar(PlanoRequest request) {
        if (request.codigo() == null || request.codigo().isBlank()) {
            throw new BadRequestException("Informe o código do plano.");
        }
        String codigo = request.codigo().trim().toUpperCase();
        if (planoRepository.existsByCodigo(codigo)) {
            throw new ConflictException("Já existe um plano com este código.");
        }
        Plano plano = new Plano(codigo, request.nome().trim());
        plano.setLimiteVoluntarios(request.limiteVoluntarios());
        if (request.ativo() != null) {
            plano.setAtivo(request.ativo());
        }
        plano = planoRepository.saveAndFlush(plano);
        backofficeLogService.registrar("PLANO_CRIAR", "PLANO", plano.getId(), null);
        return resposta(plano);
    }

    @Transactional
    public PlanoResponse atualizar(UUID id, PlanoRequest request) {
        Plano plano = buscar(id);
        plano.setNome(request.nome().trim());
        plano.setLimiteVoluntarios(request.limiteVoluntarios());
        if (request.ativo() != null) {
            plano.setAtivo(request.ativo());
        }
        backofficeLogService.registrar("PLANO_ATUALIZAR", "PLANO", plano.getId(), null);
        return resposta(plano);
    }

    @Transactional
    public PlanoResponse adicionarPreco(UUID planoId, NovoPrecoRequest request) {
        Plano plano = buscar(planoId);
        if (precoPlanoRepository.existsByPlanoIdAndPeriodicidadeAndVigenteDesde(
                planoId, request.periodicidade(), request.vigenteDesde())) {
            throw new ConflictException("Já existe preço deste plano para esta periodicidade nesta data.");
        }
        PrecoPlano preco = precoPlanoRepository.saveAndFlush(new PrecoPlano(
                planoId, request.periodicidade(), request.valor(), request.vigenteDesde()));
        backofficeLogService.registrar("PRECO_CRIAR", "PRECO_PLANO", preco.getId(), null);
        return resposta(plano);
    }

    @Transactional(readOnly = true)
    public Plano buscar(UUID id) {
        return planoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado."));
    }

    /** Preço vigente numa data; vazio = plano ainda sem preço para essa periodicidade. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> precoVigente(UUID planoId, Periodicidade periodicidade, LocalDate data) {
        return precoPlanoRepository
                .findFirstByPlanoIdAndPeriodicidadeAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        planoId, periodicidade, data)
                .map(PrecoPlano::getValor);
    }

    private PlanoResponse resposta(Plano plano) {
        LocalDate hoje = LocalDate.now(clock);
        List<PrecoResponse> precos = precoPlanoRepository.findByPlanoIdOrderByVigenteDesdeDesc(plano.getId())
                .stream().map(PrecoResponse::de).toList();
        return PlanoResponse.de(plano,
                precoVigente(plano.getId(), Periodicidade.MENSAL, hoje).orElse(null),
                precoVigente(plano.getId(), Periodicidade.ANUAL, hoje).orElse(null),
                precos);
    }
}
