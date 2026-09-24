package br.com.servire.api.voluntario;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.dto.DisponibilidadeVoluntarioRequest;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de {@link DisponibilidadeVoluntarioService} (Fase 11 do plano
 * mestre, seção 131.5 item 12) — débito de teste pago numa rodada
 * posterior à implementação (22/09/2026 à tarde), depois que o segundo
 * {@code mvn clean verify} da Fase 11 confirmou {@code BUILD SUCCESS} sem
 * nenhum teste exercitar este serviço diretamente (ver README/plano
 * mestre). Mesmo padrão de tenant descartável por teste já usado em
 * {@code EscalaServiceIntegrationTest}/{@code VoluntarioServiceIntegrationTest}.
 */
class DisponibilidadeVoluntarioServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DisponibilidadeVoluntarioService disponibilidadeService;

    @Autowired
    private DisponibilidadeVoluntarioRepository disponibilidadeRepository;

    @Autowired
    private VoluntarioRepository voluntarioRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @BeforeEach
    void definirTenant() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant(
                "TENANT-DISPONIBILIDADE-TESTE-" + sufixo, "tenant-disponibilidade-teste-" + sufixo,
                "Paróquia de teste (disponibilidade)", Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void criarComDiaSemanaGravaDisponibilidadeRecorrente() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Disponível");
        DisponibilidadeVoluntarioRequest request =
                new DisponibilidadeVoluntarioRequest(DayOfWeek.SATURDAY, null, Periodo.TARDE, "prefere sábado à tarde");

        DisponibilidadeVoluntario criada = disponibilidadeService.criar(voluntario.getId(), request);

        assertThat(criada.getId()).isNotNull();
        assertThat(criada.getDiaSemana()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(criada.getData()).isNull();
        assertThat(criada.getPeriodo()).isEqualTo(Periodo.TARDE);
        assertThat(disponibilidadeRepository.findByVoluntario_IdOrderByDiaSemanaAscDataAscPeriodoAsc(voluntario.getId()))
                .hasSize(1);
    }

    @Test
    void criarComDataGravaDisponibilidadePontual() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Pontual");
        DisponibilidadeVoluntarioRequest request =
                new DisponibilidadeVoluntarioRequest(null, LocalDate.of(2026, 12, 25), Periodo.MANHA, "só no Natal");

        DisponibilidadeVoluntario criada = disponibilidadeService.criar(voluntario.getId(), request);

        assertThat(criada.getDiaSemana()).isNull();
        assertThat(criada.getData()).isEqualTo(LocalDate.of(2026, 12, 25));
    }

    /**
     * A mesma regra "exatamente um entre diaSemana/data" é checada duas
     * vezes (serviço e construtor da entidade — ver javadoc de
     * {@link DisponibilidadeVoluntario}); este teste cobre a checagem do
     * serviço, que é a que realmente decide a mensagem que o cliente HTTP
     * recebe.
     */
    @Test
    void criarComAmbosDiaSemanaEDataLancaBadRequestException() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Inválido");
        DisponibilidadeVoluntarioRequest request =
                new DisponibilidadeVoluntarioRequest(DayOfWeek.MONDAY, LocalDate.of(2026, 12, 25), Periodo.MANHA, null);

        assertThatThrownBy(() -> disponibilidadeService.criar(voluntario.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Informe exatamente um");
    }

    @Test
    void criarComNenhumDiaSemanaNemDataLancaBadRequestException() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Sem Dia");
        DisponibilidadeVoluntarioRequest request = new DisponibilidadeVoluntarioRequest(null, null, Periodo.MANHA, null);

        assertThatThrownBy(() -> disponibilidadeService.criar(voluntario.getId(), request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void criarComVoluntarioInexistenteLancaResourceNotFoundException() {
        DisponibilidadeVoluntarioRequest request =
                new DisponibilidadeVoluntarioRequest(DayOfWeek.FRIDAY, null, Periodo.NOITE, null);

        assertThatThrownBy(() -> disponibilidadeService.criar(UUID.randomUUID(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * Confirma o índice único parcial {@code ux_disponibilidade_recorrente}
     * (V025) — mesmo dia da semana + período duas vezes para o mesmo
     * voluntário é rejeitado no banco, traduzido para {@code ConflictException}
     * pelo serviço.
     */
    @Test
    void criarDuplicataDeDiaSemanaEPeriodoLancaConflictException() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Duplicado");
        DisponibilidadeVoluntarioRequest request =
                new DisponibilidadeVoluntarioRequest(DayOfWeek.WEDNESDAY, null, Periodo.NOITE, null);
        disponibilidadeService.criar(voluntario.getId(), request);

        assertThatThrownBy(() -> disponibilidadeService.criar(voluntario.getId(), request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listarRetornaSomenteDisponibilidadesDoVoluntarioInformado() {
        Voluntario voluntario1 = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Um");
        Voluntario voluntario2 = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Dois");
        disponibilidadeService.criar(voluntario1.getId(),
                new DisponibilidadeVoluntarioRequest(DayOfWeek.TUESDAY, null, Periodo.MANHA, null));
        disponibilidadeService.criar(voluntario2.getId(),
                new DisponibilidadeVoluntarioRequest(DayOfWeek.TUESDAY, null, Periodo.MANHA, null));

        assertThat(disponibilidadeService.listar(voluntario1.getId())).hasSize(1);
    }

    @Test
    void excluirRemoveADisponibilidade() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário a Excluir");
        DisponibilidadeVoluntario criada = disponibilidadeService.criar(voluntario.getId(),
                new DisponibilidadeVoluntarioRequest(DayOfWeek.THURSDAY, null, Periodo.TARDE, null));

        disponibilidadeService.excluir(voluntario.getId(), criada.getId());

        assertThat(disponibilidadeRepository.findById(criada.getId())).isEmpty();
    }

    /**
     * Nunca revelar (nem por um 403 diferente de um 404) que uma
     * disponibilidade existe para OUTRO voluntário — mesma filosofia já
     * documentada no javadoc de
     * {@link DisponibilidadeVoluntarioService#excluir}.
     */
    @Test
    void excluirDisponibilidadeDeOutroVoluntarioLancaResourceNotFoundException() {
        Voluntario dono = Pessoas.persistirVoluntario(pessoaRepository, "Dono da Disponibilidade");
        Voluntario outro = Pessoas.persistirVoluntario(pessoaRepository, "Outro Voluntário");
        DisponibilidadeVoluntario criada = disponibilidadeService.criar(dono.getId(),
                new DisponibilidadeVoluntarioRequest(DayOfWeek.FRIDAY, null, Periodo.MANHA, null));

        assertThatThrownBy(() -> disponibilidadeService.excluir(outro.getId(), criada.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void excluirDisponibilidadeInexistenteLancaResourceNotFoundException() {
        Voluntario voluntario = Pessoas.persistirVoluntario(pessoaRepository, "Voluntário Qualquer");

        assertThatThrownBy(() -> disponibilidadeService.excluir(voluntario.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
