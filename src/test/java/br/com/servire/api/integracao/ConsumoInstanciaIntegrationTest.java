package br.com.servire.api.integracao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.mural.*;
import br.com.servire.api.tenant.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

class ConsumoInstanciaIntegrationTest extends AbstractIntegrationTest {
  @Autowired ConsumoInstanciaService consumo;
  @Autowired TenantRepository tenants;
  @Autowired DireitosLocaisRepository direitos;
  @Autowired br.com.servire.api.pessoa.PessoaRepository pessoas;
  UUID a, b;

  @BeforeEach
  void preparar() {
    doCallRealMethod().when(funcionalidadesPlano).liberadas();
    a = novo("MURAL");
    b = novo("TAREFAS");
  }

  UUID novo(String codigo) {
    String slug = UUID.randomUUID().toString();
    UUID id = tenants.saveAndFlush(new Tenant(slug, slug, "Consumo", Tenant.Status.ATIVO)).getId();
    var d = new DireitosLocais(id, UUID.randomUUID());
    d.setSituacao("ATIVA");
    d.setFuncionalidades(new String[] {codigo});
    d.setConfirmadoEm(Instant.now());
    direitos.saveAndFlush(d);
    return id;
  }

  @AfterEach
  void limpar() {
    TenantContext.clear();
  }

  @Test
  void abreContextoAntesDaSessaoERestauraTenantOriginal() {
    TenantContext.set(a);
    pessoas.saveAndFlush(
        new br.com.servire.api.pessoa.Pessoa(
            br.com.servire.api.pessoa.PessoaPapel.RESPONSAVEL, "Fixture A"));
    var resp = consumo.consultar(b);
    assertThat(resp.tenantId()).isEqualTo(b);
    assertThat(resp.funcionalidades()).containsExactly("TAREFAS");
    assertThat(resp.consumo().itens()).hasSize(7);
    assertThat(
            resp.consumo().itens().stream()
                .filter(i -> i.codigo().equals("pessoas"))
                .findFirst()
                .orElseThrow()
                .usado())
        .isZero();
    assertThat(
            consumo.consultar(a).consumo().itens().stream()
                .filter(i -> i.codigo().equals("pessoas"))
                .findFirst()
                .orElseThrow()
                .usado())
        .isEqualTo(1);
    assertThat(TenantContext.get()).isEqualTo(a);
    assertThat(consumo.consultar(a).funcionalidades()).containsExactly("MURAL");
  }

  @Test
  void semContextoNaoDeixaTenantNaThreadENaoPermiteInstanciaInexistente() {
    TenantContext.clear();
    assertThat(consumo.consultar(a).versaoContrato()).isEqualTo(1);
    assertThat(TenantContext.get()).isNull();
    assertThatThrownBy(() -> consumo.consultar(UUID.randomUUID()))
        .isInstanceOf(IntegracaoException.class);
    assertThat(TenantContext.get()).isNull();
  }
}
