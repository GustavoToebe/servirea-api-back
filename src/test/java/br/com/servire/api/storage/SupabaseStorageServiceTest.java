package br.com.servire.api.storage;

import br.com.servire.api.web.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Teste unitário de {@link SupabaseStorageService} (débito técnico da
 * Fase 7, seção 107 do plano mestre, adiado até esta rodada —
 * 22/09/2026).
 *
 * <p>Mesma técnica de {@code TurnstileServiceTest} —
 * {@link MockRestServiceServer#bindTo(RestClient.Builder)} — para não
 * depender de um projeto Supabase real nem subir o Spring context. Cobre
 * os três endpoints (upload/URL assinada/exclusão) e as validações de
 * arquivo (content-type/tamanho) que rodam ANTES de qualquer chamada
 * HTTP.</p>
 */
class SupabaseStorageServiceTest {

    private static final String BASE_URL = "https://exemplo.supabase.co";

    private static StorageProperties properties(String baseUrl, String serviceRoleKey) {
        return new StorageProperties("voluntarios-fotos", baseUrl, serviceRoleKey,
                Duration.ofHours(1), 5_242_880L,
                List.of("image/jpeg", "image/png", "image/webp", "image/heic"));
    }

    @Test
    void falhaAltoECedoSeBaseUrlOuServiceRoleKeyAusentes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        SupabaseStorageService service = new SupabaseStorageService(builder, properties("", ""));

        assertThatThrownBy(() -> service.armazenar("qualquer/caminho.jpg", new byte[]{1, 2, 3}, "image/jpeg"))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> service.gerarUrlAssinada("qualquer/caminho.jpg"))
                .isInstanceOf(StorageException.class);
        // excluir() só é best-effort quanto a FALHAS DE REDE (ver javadoc
        // do método) — requireConfigurado() é chamado ANTES do try/catch,
        // então configuração ausente ainda propaga StorageException
        // normalmente, igual aos outros dois métodos.
        assertThatThrownBy(() -> service.excluir("qualquer/caminho.jpg"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void recusaContentTypeNaoPermitido() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        SupabaseStorageService service = new SupabaseStorageService(builder, properties(BASE_URL, "chave-teste"));

        assertThatThrownBy(() -> service.armazenar("x.pdf", new byte[]{1, 2, 3}, "application/pdf"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void recusaArquivoVazio() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        SupabaseStorageService service = new SupabaseStorageService(builder, properties(BASE_URL, "chave-teste"));

        assertThatThrownBy(() -> service.armazenar("x.jpg", new byte[0], "image/jpeg"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void recusaArquivoMaiorQueOLimite() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        StorageProperties props = new StorageProperties("voluntarios-fotos", BASE_URL, "chave-teste",
                Duration.ofHours(1), 10L, List.of("image/jpeg"));
        SupabaseStorageService service = new SupabaseStorageService(builder, props);

        assertThatThrownBy(() -> service.armazenar("x.jpg", new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, "image/jpeg"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void removeEspacoDasPontasDaServiceRoleKey() {
        StorageProperties props = properties(BASE_URL, "  chave-teste  ");
        assertThat(props.serviceRoleKey()).isEqualTo("chave-teste");
    }

    @Test
    void enviaArquivoComHeaderDeUpsertEAutorizacaoBearer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/storage/v1/object/voluntarios-fotos/abc/perfil.jpg"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-upsert", "true"))
                .andExpect(header("Authorization", "Bearer chave-teste"))
                .andExpect(header("apikey", "chave-teste"))
                .andRespond(withSuccess());

        SupabaseStorageService service = new SupabaseStorageService(builder, properties(BASE_URL, "chave-teste"));

        String caminhoSalvo = service.armazenar("abc/perfil.jpg", new byte[]{1, 2, 3}, "image/jpeg");

        assertThat(caminhoSalvo).isEqualTo("abc/perfil.jpg");
        server.verify();
    }

    @Test
    void geraUrlAssinadaAPartirDoSignedURLDaResposta() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/storage/v1/object/sign/voluntarios-fotos/abc/perfil.jpg"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer chave-teste"))
                .andExpect(header("apikey", "chave-teste"))
                .andRespond(withSuccess(
                        "{\"signedURL\": \"/object/sign/voluntarios-fotos/abc/perfil.jpg?token=xyz\"}",
                        MediaType.APPLICATION_JSON));

        SupabaseStorageService service = new SupabaseStorageService(builder, properties(BASE_URL, "chave-teste"));

        String url = service.gerarUrlAssinada("abc/perfil.jpg");

        assertThat(url).isEqualTo(BASE_URL + "/storage/v1/object/sign/voluntarios-fotos/abc/perfil.jpg?token=xyz");
        server.verify();
    }

    @Test
    void lancaStorageExceptionSeUploadFalhar() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/storage/v1/object/voluntarios-fotos/x.jpg"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        SupabaseStorageService service = new SupabaseStorageService(builder, properties(BASE_URL, "chave-teste"));

        assertThatThrownBy(() -> service.armazenar("x.jpg", new byte[]{1, 2, 3}, "image/jpeg"))
                .isInstanceOf(StorageException.class);
        server.verify();
    }

    /**
     * exclusão é deliberadamente best-effort (ver javadoc de
     * {@link SupabaseStorageService#excluir}) — uma falha na chamada HTTP
     * é só logada (warn), nunca propagada ao chamador.
     */
    @Test
    void exclusaoFalhaSilenciosamenteSemPropagarErro() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/storage/v1/object/voluntarios-fotos/x.jpg"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        SupabaseStorageService service = new SupabaseStorageService(builder, properties(BASE_URL, "chave-teste"));

        assertThatCode(() -> service.excluir("x.jpg")).doesNotThrowAnyException();
        server.verify();
    }
}
