package br.com.servire.api.escala;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.Item;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.MesRequest;
import br.com.servire.api.escala.dto.IndisponibilidadeDtos.MesResponse;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.Pessoas;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.Periodo;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.web.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndisponibilidadeIntegrationTest extends AbstractIntegrationTest {

    @Autowired private IndisponibilidadeService service;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PessoaRepository pessoaRepository;

    private UUID tenantId;
    private UUID outroTenantId;
    private Voluntario ana;
    private Voluntario bruno;

    @BeforeEach
    void montar() {
        String s = UUID.randomUUID().toString();
        tenantId = tenantRepository.saveAndFlush(new Tenant("IN-" + s.substring(0, 8), "in-" + s, "Paróquia Indisp", Tenant.Status.ATIVO)).getId();
        outroTenantId = tenantRepository.saveAndFlush(new Tenant("IO-" + s.substring(0, 8), "io-" + s, "Outra", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenantId);
        ana = Pessoas.persistirVoluntario(pessoaRepository, "Ana Indisp");
        bruno = Pessoas.persistirVoluntario(pessoaRepository, "Bruno Indisp");
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    @Test
    void gravaLeESubstituiOMes() {
        service.salvar(2026, 10, new MesRequest(List.of(
                new Item(ana.getId(), LocalDate.of(2026, 10, 3), null, "viagem"),
                new Item(ana.getId(), LocalDate.of(2026, 10, 4), Periodo.MANHA, null)), List.of(bruno.getId()), service.buscar(2026,10).versao()));

        MesResponse lido = service.buscar(2026, 10);
        assertThat(lido.itens()).hasSize(2);
        assertThat(lido.itens().getFirst().observacao()).isEqualTo("viagem");
        assertThat(lido.semRestricao()).containsExactly(bruno.getId());

        service.salvar(2026, 10, new MesRequest(List.of(new Item(bruno.getId(), LocalDate.of(2026, 10, 17), null, null)), List.of(), service.buscar(2026,10).versao()));
        lido = service.buscar(2026, 10);
        assertThat(lido.itens()).extracting(Item::voluntarioId).containsExactly(bruno.getId());
        assertThat(lido.semRestricao()).isEmpty();
        assertThat(service.buscar(2026, 11).itens()).isEmpty();
    }

    @Test
    void dataDeOutroMesDa400() {
        assertThatThrownBy(() -> service.salvar(2026, 10, new MesRequest(
                List.of(new Item(ana.getId(), LocalDate.of(2026, 11, 1), null, null)), List.of(), service.buscar(2026,10).versao())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("não é do mês");
    }

    @Test
    void restricaoESemRestricaoJuntosDa400() {
        assertThatThrownBy(() -> service.salvar(2026, 10, new MesRequest(
                List.of(new Item(ana.getId(), LocalDate.of(2026, 10, 3), null, null)), List.of(ana.getId()), service.buscar(2026,10).versao())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("sem restrição");
    }

    @Test
    void outraParoquiaNaoVe() {
        service.salvar(2026, 10, new MesRequest(List.of(new Item(ana.getId(), LocalDate.of(2026, 10, 3), null, null)), List.of(), service.buscar(2026,10).versao()));
        TenantContext.set(outroTenantId);
        assertThat(service.buscar(2026, 10).itens()).isEmpty();
        assertThatThrownBy(() -> service.salvar(2026, 10, new MesRequest(
                List.of(new Item(ana.getId(), LocalDate.of(2026, 10, 3), null, null)), List.of(), service.buscar(2026,10).versao())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("não é desta paróquia");
    }
}
