package br.com.servire.api.storage;

import br.com.servire.api.web.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtensaoDeFotoTest {

    @Test
    void usaSoOTipoAceito() {
        assertThat(ExtensaoDeFoto.de("image/jpeg")).isEqualTo(".jpg");
        assertThat(ExtensaoDeFoto.de("image/png")).isEqualTo(".png");
        assertThat(ExtensaoDeFoto.de("image/webp")).isEqualTo(".webp");
        assertThat(ExtensaoDeFoto.de("image/heic")).isEqualTo(".heic");
    }

    @Test
    void recusaTipoForaDaLista() {
        assertThatThrownBy(() -> ExtensaoDeFoto.de("image/svg+xml"))
                .isInstanceOf(BadRequestException.class);
    }
}
