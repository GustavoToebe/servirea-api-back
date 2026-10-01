package br.com.servire.api.pessoa;

import br.com.servire.api.pessoa.dto.AniversarianteResponse;
import br.com.servire.api.web.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Aniversariantes do mês para o Início. Sem mês informado vale o mês corrente no fuso de
 * Brasília: o servidor roda em UTC e, perto da virada do mês, o mês dele já seria outro.
 */
@Service
public class Aniversariantes {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private final PessoaRepository pessoas;
    private final Clock clock;

    @Autowired
    public Aniversariantes(PessoaRepository pessoas) {
        this(pessoas, Clock.system(BRASILIA));
    }

    Aniversariantes(PessoaRepository pessoas, Clock clock) {
        this.pessoas = pessoas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AniversarianteResponse> doMes(Integer mes) {
        int alvo = mes == null ? LocalDate.now(clock.withZone(BRASILIA)).getMonthValue() : mes;
        if (alvo < 1 || alvo > 12) {
            throw new BadRequestException("Mês inválido: use de 1 a 12.");
        }
        return pessoas.aniversariantesDoMes(alvo);
    }
}
