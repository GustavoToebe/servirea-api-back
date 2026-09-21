package br.com.servire.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o handler isoladamente (sem subir o Spring context) — mais rápido
 * e suficiente para garantir o contrato de {@link ApiError}: nunca vazar
 * detalhe interno para o cliente em erro inesperado, e devolver o status
 * correto para cada subclasse de {@link ApiException}.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFoundExceptionViraHttp404ComAMensagemDaExcecao() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/voluntarios/algum-id");
        ResponseEntity<ApiError> response = handler.handleApiException(
                new ResourceNotFoundException("Voluntário não encontrado"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Voluntário não encontrado");
        assertThat(response.getBody().path()).isEqualTo("/voluntarios/algum-id");
    }

    @Test
    void conflictExceptionViraHttp409() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inscricoes/x/aprovar");
        ResponseEntity<ApiError> response = handler.handleApiException(
                new ConflictException("Somente inscrições pendentes podem ser aprovadas"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void excecaoNaoMapeadaViraHttp500ComMensagemGenericaSemVazarDetalheInterno() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/qualquer");
        RuntimeException erroInterno = new RuntimeException(
                "ERROR: relation \"public.voluntarios\" does not exist — detalhe interno do driver JDBC");

        ResponseEntity<ApiError> response = handler.handleUnexpected(erroInterno, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("relation");
        assertThat(response.getBody().message()).doesNotContain("voluntarios");
        assertThat(response.getBody().message()).doesNotContain("JDBC");
    }
}
