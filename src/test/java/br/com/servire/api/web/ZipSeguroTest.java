package br.com.servire.api.web;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipSeguroTest {

    @Test
    void aceitaZipSimplesEUmNivelEmbutido() throws IOException {
        byte[] texto = zip("a.txt", "olá".getBytes(StandardCharsets.UTF_8));
        byte[] comPlanilha = zip("folha.xlsx", texto);

        assertThatCode(() -> ZipSeguro.verificar(texto)).doesNotThrowAnyException();
        assertThatCode(() -> ZipSeguro.verificar(comPlanilha)).doesNotThrowAnyException();
    }

    @Test
    void recusaTerceiroNivel() throws IOException {
        byte[] interno = zip("a.txt", "x".getBytes(StandardCharsets.UTF_8));
        byte[] meio = zip("meio.zip", interno);
        byte[] fora = zip("fora.zip", meio);

        assertThatThrownBy(() -> ZipSeguro.verificar(fora))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void recusaTamanhoDeclaradoAcimaDoTetoSemInflar() throws IOException {
        byte[] zip = zip("a.txt", "pequeno".getBytes(StandardCharsets.UTF_8));
        definirDescompactado(zip, ZipSeguro.MAX_POR_ENTRADA + 1);

        assertThatThrownBy(() -> ZipSeguro.verificar(zip))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void recusaRazaoDeCompactacaoAbsurda() throws IOException {
        byte[] zip = zip("a.txt", "pequeno".getBytes(StandardCharsets.UTF_8));
        long compactado = lerCompactado(zip);
        definirDescompactado(zip, compactado * (ZipSeguro.MAX_RAZAO + 1));

        assertThatThrownBy(() -> ZipSeguro.verificar(zip))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void recusaEntradasDemais() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < ZipSeguro.MAX_ENTRADAS + 1; i++) {
                zip.putNextEntry(new ZipEntry("a" + i + ".txt"));
                zip.write('x');
                zip.closeEntry();
            }
        }

        assertThatThrownBy(() -> ZipSeguro.verificar(out.toByteArray()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void recusaOQueNaoEZip() {
        assertThatThrownBy(() -> ZipSeguro.verificar("%PDF".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inválido");
    }

    private static byte[] zip(String nome, byte[] conteudo) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(nome));
            zip.write(conteudo);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static int indiceCentral(byte[] zip) {
        for (int i = 0; i < zip.length - 4; i++) {
            if (zip[i] == 0x50 && zip[i + 1] == 0x4b && zip[i + 2] == 1 && zip[i + 3] == 2) {
                return i;
            }
        }
        throw new AssertionError("sem diretório central");
    }

    private static long lerCompactado(byte[] zip) {
        int i = indiceCentral(zip);
        return (zip[i + 20] & 0xffL) | ((zip[i + 21] & 0xffL) << 8)
                | ((zip[i + 22] & 0xffL) << 16) | ((zip[i + 23] & 0xffL) << 24);
    }

    private static void definirDescompactado(byte[] zip, long valor) {
        int i = indiceCentral(zip) + 24;
        zip[i] = (byte) valor;
        zip[i + 1] = (byte) (valor >> 8);
        zip[i + 2] = (byte) (valor >> 16);
        zip[i + 3] = (byte) (valor >> 24);
    }
}
