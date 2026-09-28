package br.com.servire.api.escala;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.escala.dto.EscalaEventoRequest;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaVagaRequest;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.ApoioVoluntario;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.Item;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.MesRequest;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.Situacao;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaService;
import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.pessoa.dto.RelacaoRequest;
import br.com.servire.api.pessoa.dto.VoluntarioPerfilRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ApoioEscalaIntegrationTest extends AbstractIntegrationTest {

    @Autowired private IndisponibilidadeService service;
    @Autowired private EscalaService escalaService;
    @Autowired private PessoaService pessoaService;
    @Autowired private TenantRepository tenantRepository;

    @BeforeEach
    void montar() {
        String s = UUID.randomUUID().toString();
        UUID tenantId = tenantRepository.saveAndFlush(new Tenant("AP-" + s.substring(0, 8), "ap-" + s, "Paróquia Apoio", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenantId);
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    private Pessoa responsavel(String nome) {
        return pessoaService.criar(new PessoaRequest(Set.of(PessoaPapel.RESPONSAVEL), nome, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    private Pessoa filho(String nome, Pessoa responsavel) {
        return pessoaService.criar(new PessoaRequest(Set.of(PessoaPapel.VOLUNTARIO), nome, null, null, null, null, List.of(), List.of(),
                List.of(new RelacaoRequest(responsavel.getId(), "Mãe", "Filho(a)", true)), List.of(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                new VoluntarioPerfilRequest(TipoVoluntario.COROINHA, true, null, null, null, null, false, List.of(), null, null)));
    }

    @Test
    void irmaosPeloResponsavelEmComumESituacaoDoMes() {
        Pessoa maria = responsavel("Maria");
        Pessoa joana = responsavel("Joana");
        Pessoa ana = filho("Ana", maria);
        Pessoa bruno = filho("Bruno", maria);
        Pessoa caio = filho("Caio", joana);
        Pessoa unico = filho("Único", responsavel("Rosa"));

        service.salvar(2026, 10, new MesRequest(List.of(new Item(ana.getId(), LocalDate.of(2026, 10, 3), null, null)), List.of(bruno.getId())));
        Escala escala = escalaService.criar(new EscalaRequest("Mensal Outubro", TipoEscala.MENSAL, 2026, 10, null, null,
                List.of(new EscalaEventoRequest(LocalDate.of(2026, 10, 3), LocalTime.of(19, 0), "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.MISSAL, 1, null))))), null);

        Map<UUID, ApoioVoluntario> apoio = service.apoio(escala.getId()).voluntarios().stream()
                .collect(Collectors.toMap(ApoioVoluntario::voluntarioId, Function.identity()));

        assertThat(apoio.get(ana.getId()).irmaos()).containsExactly(bruno.getId());
        assertThat(apoio.get(bruno.getId()).irmaos()).containsExactly(ana.getId());
        assertThat(apoio.get(caio.getId()).irmaos()).isEmpty();
        assertThat(apoio.get(unico.getId()).irmaos()).isEmpty();

        assertThat(apoio.get(ana.getId()).situacao()).isEqualTo(Situacao.COM_RESTRICAO);
        assertThat(apoio.get(ana.getId()).indisponiveis()).extracting(i -> i.data()).containsExactly(LocalDate.of(2026, 10, 3));
        assertThat(apoio.get(bruno.getId()).situacao()).isEqualTo(Situacao.SEM_RESTRICAO);
        assertThat(apoio.get(caio.getId()).situacao()).isEqualTo(Situacao.PENDENTE);
        assertThat(apoio).doesNotContainKey(maria.getId());
    }
}
