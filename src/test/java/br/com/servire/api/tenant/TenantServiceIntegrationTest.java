package br.com.servire.api.tenant;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.tenant.dto.TenantRequest;
import br.com.servire.api.web.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code GET}/{@code PUT /tenant} — isolamento pelo {@link TenantContext}
 * (tabela global, sem {@code @TenantId}).
 */
class TenantServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @AfterEach
    void limparTenantContext() {
        TenantContext.clear();
    }

    @Test
    void semContextoLancaResourceNotFoundException() {
        assertThatThrownBy(() -> tenantService.buscarAtual())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tenantInexistenteLancaResourceNotFoundException() {
        TenantContext.set(UUID.randomUUID());

        assertThatThrownBy(() -> tenantService.buscarAtual())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void buscarDevolveOTenantDoContexto() {
        Tenant tenant = salvar("A");
        TenantContext.set(tenant.getId());

        Tenant atual = tenantService.buscarAtual();

        assertThat(atual.getId()).isEqualTo(tenant.getId());
        assertThat(atual.getNome()).isEqualTo(tenant.getNome());
        assertThat(atual.getCodigo()).isEqualTo(tenant.getCodigo());
    }

    @Test
    void atualizarMudaNomeRazaoECnpjSemTocarSlugNemStatus() {
        Tenant tenant = salvar("B");
        String slug = tenant.getSlug();
        String codigo = tenant.getCodigo();
        TenantContext.set(tenant.getId());

        Tenant atualizado = tenantService.atualizar(new TenantRequest(
                " Paróquia Nova ", " Associação ", " 12.345.678/0001-90 "));

        assertThat(atualizado.getNome()).isEqualTo("Paróquia Nova");
        assertThat(atualizado.getRazaoSocial()).isEqualTo("Associação");
        assertThat(atualizado.getCnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(atualizado.getSlug()).isEqualTo(slug);
        assertThat(atualizado.getCodigo()).isEqualTo(codigo);
        assertThat(atualizado.getStatus()).isEqualTo(Tenant.Status.ATIVO);
        Tenant persistido = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persistido.getNome()).isEqualTo("Paróquia Nova");
        assertThat(persistido.getSlug()).isEqualTo(slug);
    }

    @Test
    void atualizarNaoVazaParaOutroTenant() {
        Tenant tenantA = salvar("C");
        Tenant tenantB = salvar("D");
        String nomeB = tenantB.getNome();
        TenantContext.set(tenantA.getId());

        tenantService.atualizar(new TenantRequest("Só o A", null, null));

        TenantContext.set(tenantB.getId());
        assertThat(tenantService.buscarAtual().getNome()).isEqualTo(nomeB);
        assertThat(tenantRepository.findById(tenantA.getId()).orElseThrow().getNome()).isEqualTo("Só o A");
    }

    private Tenant salvar(String sufixoUnico) {
        String sufixo = sufixoUnico + "-" + UUID.randomUUID();
        return tenantRepository.saveAndFlush(new Tenant(
                "TENANT-CFG-" + sufixo, "tenant-cfg-" + sufixo,
                "Paróquia " + sufixoUnico, Tenant.Status.ATIVO));
    }
}
