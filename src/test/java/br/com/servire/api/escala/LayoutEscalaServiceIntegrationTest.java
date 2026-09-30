package br.com.servire.api.escala;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.escala.dto.EscalaEventoRequest;
import br.com.servire.api.escala.dto.EscalaRequest;
import br.com.servire.api.escala.dto.EscalaVagaRequest;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras de {@link LayoutEscalaService} (30/09/2026): ativo/inativo, um padrão por modelo, nome único e exclusão.
 * Cada teste cria a sua paróquia descartável; sem rollback automático.
 */
class LayoutEscalaServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private LayoutEscalaService service;
    @Autowired private LayoutEscalaRepository layouts;
    @Autowired private EscalaService escalaService;
    @Autowired private TenantRepository tenantRepository;

    @BeforeEach
    void definirTenant() {
        String sufixo = UUID.randomUUID().toString();
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("LAYOUT-SVC-" + sufixo, "layout-svc-" + sufixo,
                "Paróquia de teste (layout)", Tenant.Status.ATIVO));
        TenantContext.set(tenant.getId());
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    private static List<ColunaEscalaDto> vaga() {
        return List.of(new ColunaEscalaDto(1, FuncaoEscala.CRUZ, 1, "Cruz"));
    }

    /** Layout de sistema só nasce por provisionamento/migration; aqui vai direto no repositório. */
    private LayoutEscala sistema(TipoEscala tipo, String nome) {
        return layouts.saveAndFlush(new LayoutEscala(nome, tipo, vaga(), true));
    }

    private LayoutEscalaDto dto(String nome, TipoEscala tipo, Boolean ativo, Boolean padrao) {
        return new LayoutEscalaDto(null, nome, tipo, vaga(), ativo, false, "descrição", padrao);
    }

    private Escala escalaCom(UUID layoutId) {
        return escalaService.criar(new EscalaRequest("Escala", TipoEscala.SEMANAL, 2026, 11, null, layoutId,
                layoutId == null ? null : vaga(), null,
                List.of(new EscalaEventoRequest(LocalDate.of(2026, 11, 2), LocalTime.of(19, 0), "Missa",
                        List.of(new EscalaVagaRequest(FuncaoEscala.CRUZ, 1, null))))), null);
    }

    @Test
    void criarSemAtivoNemPadraoNasceAtivoESemPadrao() {
        LayoutEscala l = service.criar(dto("Matriz", TipoEscala.MENSAL, null, null));
        assertThat(l.isAtivo()).isTrue();
        assertThat(l.isPadrao()).isFalse();
        assertThat(l.getDescricao()).isEqualTo("descrição");
    }

    @Test
    void nomeRepetidoNoMesmoModeloDaConflito() {
        service.criar(dto("Matriz", TipoEscala.MENSAL, null, null));
        assertThatThrownBy(() -> service.criar(dto("matriz", TipoEscala.MENSAL, null, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(service.criar(dto("Matriz", TipoEscala.SEMANAL, null, null)).getId()).isNotNull();
    }

    @Test
    void marcarPadraoDesmarcaOAnteriorDoMesmoModelo() {
        LayoutEscala a = service.criar(dto("A", TipoEscala.MENSAL, true, true));
        LayoutEscala b = service.criar(dto("B", TipoEscala.MENSAL, true, true));
        assertThat(layouts.findById(a.getId()).orElseThrow().isPadrao()).isFalse();
        assertThat(layouts.findById(b.getId()).orElseThrow().isPadrao()).isTrue();
        LayoutEscala s = service.criar(dto("S", TipoEscala.SEMANAL, true, true));
        assertThat(layouts.findById(b.getId()).orElseThrow().isPadrao()).isTrue();
        assertThat(s.isPadrao()).isTrue();
    }

    @Test
    void layoutInativoNaoPodeSerPadrao() {
        assertThatThrownBy(() -> service.criar(dto("A", TipoEscala.MENSAL, false, true)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void inativarOPadraoTiraAMarcaDePadrao() {
        sistema(TipoEscala.MENSAL, "Padrão Mensal");
        LayoutEscala a = service.criar(dto("A", TipoEscala.MENSAL, true, true));
        LayoutEscala inativo = service.atualizar(a.getId(), dto("A", TipoEscala.MENSAL, false, null));
        assertThat(inativo.isAtivo()).isFalse();
        assertThat(inativo.isPadrao()).isFalse();
    }

    @Test
    void naoInativaNemExcluiOUltimoLayoutAtivoDoModelo() {
        LayoutEscala unico = service.criar(dto("Único", TipoEscala.MENSAL, null, null));
        assertThatThrownBy(() -> service.atualizar(unico.getId(), dto("Único", TipoEscala.MENSAL, false, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.excluir(unico.getId())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void salvarSemMandarAtivoNaoMudaOEstado() {
        service.criar(dto("Outro", TipoEscala.MENSAL, null, null));
        LayoutEscala a = service.criar(dto("A", TipoEscala.MENSAL, false, null));
        // O editor antigo mandava só nome e tipo: "ativo" ausente não pode reativar nem desativar.
        LayoutEscala salvo = service.atualizar(a.getId(), dto("A renomeado", TipoEscala.MENSAL, null, null));
        assertThat(salvo.isAtivo()).isFalse();
        assertThat(salvo.getNome()).isEqualTo("A renomeado");
    }

    @Test
    void layoutDeSistemaNaoSeExcluiNemMudaDeModelo() {
        LayoutEscala s = sistema(TipoEscala.MENSAL, "Padrão Mensal");
        assertThatThrownBy(() -> service.excluir(s.getId())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.atualizar(s.getId(),
                new LayoutEscalaDto(s.getId(), "Padrão Mensal", TipoEscala.SEMANAL, vaga(), null, true, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void layoutJaUsadoEmEscalaNaoSeExcluiMasSeInativa() {
        service.criar(dto("Outro", TipoEscala.SEMANAL, null, null));
        LayoutEscala usado = service.criar(dto("Usado", TipoEscala.SEMANAL, null, null));
        escalaCom(usado.getId());

        assertThatThrownBy(() -> service.excluir(usado.getId())).isInstanceOf(ConflictException.class);
        assertThat(service.atualizar(usado.getId(), dto("Usado", TipoEscala.SEMANAL, false, null)).isAtivo()).isFalse();
    }

    @Test
    void escalaNovaSemLayoutUsaOPadraoMarcadoAntesDoDeSistema() {
        sistema(TipoEscala.SEMANAL, "Padrão Semanal");
        LayoutEscala meu = service.criar(dto("Meu padrão", TipoEscala.SEMANAL, true, true));
        assertThat(escalaCom(null).getLayoutId()).isEqualTo(meu.getId());
    }
}
