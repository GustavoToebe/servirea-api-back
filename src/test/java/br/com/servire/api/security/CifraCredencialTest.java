package br.com.servire.api.security;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CifraCredencialTest {
    private static final String K="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    @Test void cifragemAleatoriaPreservaValorSemRevelarSegredo() {
        var c=new CifraCredencial("v1:"+K,"v1"); var tenant=UUID.randomUUID();
        String a=c.cifrar(tenant,"token-secreto"); String b=c.cifrar(tenant,"token-secreto");
        assertThat(a).isNotEqualTo(b).doesNotContain("token-secreto");
        assertThat(c.abrir(tenant,a)).isEqualTo("token-secreto");
    }
    @Test void trocaDeParoquiaOuConteudoAdulteradoRecusados() {
        var c=new CifraCredencial("v1:"+K,"v1"); var tenant=UUID.randomUUID(); String a=c.cifrar(tenant,"segredo");
        assertThatThrownBy(() -> c.abrir(UUID.randomUUID(),a)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("segredo");
        String[] p=a.split(":",5); byte[] bytes=Base64.getDecoder().decode(p[4]); bytes[0]^=1;
        String alterada=String.join(":",Arrays.copyOf(p,4))+":"+Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> c.abrir(tenant,alterada)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rotacaoAceitaChaveAnteriorEGravaComAtual() {
        var tenant=UUID.randomUUID(); var antiga=new CifraCredencial("v1:"+K,"v1");
        String token=antiga.cifrar(tenant,"valor");
        var nova=new CifraCredencial("v1:"+K+",v2:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","v2");
        assertThat(nova.precisaRotacionar(token)).isTrue();
        String rotacionado=nova.cifrar(tenant,nova.abrir(tenant,token));
        assertThat(rotacionado).startsWith("enc:v1:v2:"); assertThat(nova.abrir(tenant,rotacionado)).isEqualTo("valor");
    }
    @Test void configuracaoInvalidaNaoVazaChaveETextoLegadoNaoPodeSerEnviado() {
        assertThatThrownBy(() -> new CifraCredencial("v1:segredo-invalido","v1")).hasMessageNotContaining("segredo-invalido");
        var c=new CifraCredencial("v1:"+K,"v1");
        assertThatThrownBy(() -> c.abrir(UUID.randomUUID(),"token-legado")).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("token-legado");
    }
}
