package br.com.servire.api.pessoa;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.pessoa.dto.DuplicidadeRequest;
import br.com.servire.api.pessoa.dto.DuplicidadeResponse;
import br.com.servire.api.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicidadesIntegrationTest extends AbstractIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private br.com.servire.api.tenant.TenantRepository tenantRepository;
    
    private java.util.UUID tenantId;

    @org.junit.jupiter.api.BeforeEach
    void definirTenant() {
        String sufixo = java.util.UUID.randomUUID().toString();
        br.com.servire.api.tenant.Tenant tenant = tenantRepository.saveAndFlush(new br.com.servire.api.tenant.Tenant(
                "TEST-" + sufixo, "test-" + sufixo,
                "Tenant de Teste", br.com.servire.api.tenant.Tenant.Status.ATIVO));
        this.tenantId = tenant.getId();
        br.com.servire.api.tenant.TenantContext.set(this.tenantId);
    }

    @org.junit.jupiter.api.AfterEach
    void limparTenantContext() {
        br.com.servire.api.tenant.TenantContext.clear();
    }


    @Autowired
    private Duplicidades duplicidades;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ignorarIdExcluiAPropriaPessoa() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa (id, tenant_id, nome_completo, e_voluntario, e_responsavel) VALUES (?, ?, 'Mesmo Nome', true, false)", id, tenantId);

        var req = new DuplicidadeRequest(id, "Mesmo Nome", null, null, List.of(), List.of());
        var res = duplicidades.verificar(req);
        
        assertThat(res).isEmpty();
    }

    @Test
    void cpfIgualBloqueiaENomeParecidoApareceSemBloquear() {
        UUID p1 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa (id, tenant_id, nome_completo, cpf, e_voluntario, e_responsavel) VALUES (?, ?, 'João Silva', '12345678909', true, false)", p1, tenantId);

        UUID p2 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa (id, tenant_id, nome_completo, cpf, e_voluntario, e_responsavel) VALUES (?, ?, 'Maria Silva', '04220011030', true, false)", p2, tenantId);

        var req = new DuplicidadeRequest(null, "Maria Sylva", "123.456.789-09", null, List.of(), List.of());
        var res = duplicidades.verificar(req);
        
        assertThat(res).hasSize(2);
        assertThat(res.get(0).bloqueia()).isTrue();
        assertThat(res.get(0).motivos()).contains("CPF");
        assertThat(res.get(0).nomeCompleto()).isEqualTo("João Silva");

        assertThat(res.get(1).bloqueia()).isFalse();
        assertThat(res.get(1).motivos()).contains("NOME");
        assertThat(res.get(1).nomeCompleto()).isEqualTo("Maria Silva");
    }

    @Test
    void irmaoNaoAparece() {
        UUID p1 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa (id, tenant_id, nome_completo, data_nascimento, e_voluntario, e_responsavel) VALUES (?, ?, 'Irmão Mais Velho', '2010-01-01', true, false)", p1, tenantId);
        UUID resp1 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa (id, tenant_id, nome_completo, e_voluntario, e_responsavel) VALUES (?, ?, 'Pai', true, false)", resp1, tenantId);
        
        UUID relId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa_relacao (id, tenant_id, voluntario_id, responsavel_id, parentesco, parentesco_inverso, principal, created_at) VALUES (?, ?, ?, ?, 'Pai', 'Filho', true, now())", relId, tenantId, p1, resp1);

        UUID telId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa_telefone (id, tenant_id, pessoa_id, tipo, numero, principal, created_at) VALUES (?, ?, ?, 'Celular', '11999999999', true, now())", telId, tenantId, p1);

        var req = new DuplicidadeRequest(null, "Irmão Mais Novo", null, LocalDate.of(2015, 1, 1), List.of("11999999999"), List.of("Pai"));
        var res = duplicidades.verificar(req);
        
        assertThat(res).isEmpty(); // Irmão tem mesmo telefone e responsavel mas nome e nascimento diferentes -> nao entra
    }

    @Test
    void mesmoNascimentoETelefoneAparece() {
        UUID p1 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa (id, tenant_id, nome_completo, data_nascimento, e_voluntario, e_responsavel) VALUES (?, ?, 'Gêmeo Um', '2010-01-01', true, false)", p1, tenantId);
        
        UUID telId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa_telefone (id, tenant_id, pessoa_id, tipo, numero, principal, created_at) VALUES (?, ?, ?, 'Celular', '11999999999', true, now())", telId, tenantId, p1);

        var req = new DuplicidadeRequest(null, "Gêmeo Dois", null, LocalDate.of(2010, 1, 1), List.of("11999999999"), List.of());
        var res = duplicidades.verificar(req);
        
        assertThat(res).hasSize(1);
        assertThat(res.get(0).motivos()).containsExactlyInAnyOrder("NASCIMENTO", "TELEFONE");
    }

    @Test
    void outraParoquiaComMesmoCpfNaoAparece() {
        br.com.servire.api.tenant.Tenant t = tenantRepository.saveAndFlush(new br.com.servire.api.tenant.Tenant("outro-tenant", "outro-tenant", "Outro", br.com.servire.api.tenant.Tenant.Status.ATIVO));
        UUID outroTenant = t.getId();
        
        UUID p1 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pessoa (id, tenant_id, nome_completo, cpf, e_voluntario, e_responsavel) VALUES (?, ?, 'Forasteiro', '12345678909', true, false)", p1, outroTenant);

        var req = new DuplicidadeRequest(null, "Forasteiro", "12345678909", null, List.of(), List.of());
        var res = duplicidades.verificar(req);
        
        assertThat(res).isEmpty();
    }
}
