package br.com.servire.api.comunicacao;
import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.tenant.*;
import br.com.servire.api.comunicacao.dto.WhatsappConfigRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CredenciaisWhatsappIntegrationTest extends AbstractIntegrationTest {
    @Autowired ParoquiaWhatsappService service;
    @Autowired ParoquiaWhatsappRepository repository;
    @Autowired TenantRepository tenants;
    @Autowired RotacaoCredenciais rotacao;
    private UUID tenant;
    @BeforeEach void preparar() {
        String id=UUID.randomUUID().toString(); tenant=tenants.saveAndFlush(new Tenant(id,id,"Teste cifra",Tenant.Status.ATIVO)).getId(); TenantContext.set(tenant);
    }
    @AfterEach void limpar() { TenantContext.clear(); }
    @Test void salvaSomenteEnvelopeEConservaTokenQuandoFormularioNaoEnviaOutro() {
        service.salvar(new WhatsappConfigRequest("instancia","valor-secreto",true));
        assertThat(repository.findById(tenant).orElseThrow().getToken()).startsWith("enc:v1:").doesNotContain("valor-secreto");
        assertThat(service.ativa().orElseThrow().getToken()).isEqualTo("valor-secreto");
        service.salvar(new WhatsappConfigRequest("instancia-renomeada",null,true));
        assertThat(service.ativa().orElseThrow().getToken()).isEqualTo("valor-secreto");
        assertThat(service.ativa().orElseThrow().toString()).doesNotContain("valor-secreto");
    }
    @Test void converteLegadoUmaVezAntesDeEnviar() {
        repository.saveAndFlush(new ParoquiaWhatsapp(tenant,"legada","token-antigo",true));
        assertThatThrownBy(() -> service.ativa()).isInstanceOf(IllegalStateException.class);
        rotacao.run(null); String protegido=repository.findById(tenant).orElseThrow().getToken();
        assertThat(protegido).startsWith("enc:v1:").doesNotContain("token-antigo");
        assertThat(service.ativa().orElseThrow().getToken()).isEqualTo("token-antigo");
        rotacao.run(null); assertThat(repository.findById(tenant).orElseThrow().getToken()).isEqualTo(protegido);
    }
}
