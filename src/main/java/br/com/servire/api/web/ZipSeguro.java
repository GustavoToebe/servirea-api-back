package br.com.servire.api.web;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Recusa zip bomb em DOCX/XLSX sem descompactar além do teto.
 * Lê o diretório central (não infla o que o cabeçalho já declara grande)
 * e, no que passa, infla no máximo o tamanho declarado — se o fluxo
 * renderia mais, para. Um zip dentro de outro é aceito uma vez
 * (planilha embutida no Word); o terceiro nível é recusado.
 */
public final class ZipSeguro {

    static final int MAX_ENTRADAS = 128;
    static final long MAX_POR_ENTRADA = 16L * 1024 * 1024;
    static final long MAX_TOTAL = 32L * 1024 * 1024;
    static final int MAX_RAZAO = 100;
    static final int MAX_PROFUNDIDADE = 2;

    private static final String LIMITE = "Este anexo compactado passa do limite seguro.";
    private static final String INVALIDO = "Anexo compactado inválido.";

    private ZipSeguro() {
    }

    public static boolean pareceZip(byte[] dados) {
        return dados != null && dados.length >= 4
                && dados[0] == 'P' && dados[1] == 'K' && dados[2] == 3 && dados[3] == 4;
    }

    public static void verificar(byte[] dados) {
        if (!pareceZip(dados)) {
            throw new BadRequestException(INVALIDO);
        }
        verificar(dados, 0);
    }

    private static void verificar(byte[] dados, int profundidade) {
        int eocd = indiceEocd(dados);
        int entradas = u16(dados, eocd + 10);
        long tamanhoCentral = u32(dados, eocd + 12);
        long inicioCentral = u32(dados, eocd + 16);
        if (entradas == 0xFFFF || tamanhoCentral == 0xFFFFFFFFL || inicioCentral == 0xFFFFFFFFL) {
            throw new BadRequestException(LIMITE);
        }
        if (entradas > MAX_ENTRADAS) {
            throw new BadRequestException(LIMITE);
        }
        if (inicioCentral > dados.length || tamanhoCentral > dados.length - inicioCentral) {
            throw new BadRequestException(INVALIDO);
        }

        int pos = (int) inicioCentral;
        long total = 0;
        for (int n = 0; n < entradas; n++) {
            if (pos + 46 > dados.length || u32(dados, pos) != 0x02014b50L) {
                throw new BadRequestException(INVALIDO);
            }
            int flags = u16(dados, pos + 8);
            int metodo = u16(dados, pos + 10);
            long compactado = u32(dados, pos + 20);
            long descompactado = u32(dados, pos + 24);
            int nome = u16(dados, pos + 28);
            int extra = u16(dados, pos + 30);
            int comentario = u16(dados, pos + 32);
            long offsetLocal = u32(dados, pos + 42);
            if ((flags & 1) != 0 || compactado == 0xFFFFFFFFL || descompactado == 0xFFFFFFFFL || offsetLocal == 0xFFFFFFFFL) {
                throw new BadRequestException(LIMITE);
            }
            if (descompactado > MAX_POR_ENTRADA || compactado > dados.length) {
                throw new BadRequestException(LIMITE);
            }
            total += descompactado;
            if (total > MAX_TOTAL) {
                throw new BadRequestException(LIMITE);
            }
            if (compactado > 0 && descompactado > compactado * (long) MAX_RAZAO) {
                throw new BadRequestException(LIMITE);
            }
            if (descompactado > 0 && (metodo == 0 || metodo == 8)) {
                byte[] interno = extrair(dados, (int) offsetLocal, compactado, descompactado, metodo);
                if (pareceZip(interno)) {
                    if (profundidade + 1 >= MAX_PROFUNDIDADE) {
                        throw new BadRequestException(LIMITE);
                    }
                    verificar(interno, profundidade + 1);
                }
            } else if (descompactado > 0) {
                throw new BadRequestException(INVALIDO);
            }
            int passo = 46 + nome + extra + comentario;
            if (pos + passo > dados.length) {
                throw new BadRequestException(INVALIDO);
            }
            pos += passo;
        }
    }

    /** Infla no máximo {@code descompactado} bytes. Se sobrar fluxo, é bomba. */
    private static byte[] extrair(byte[] dados, int offsetLocal, long compactado, long descompactado, int metodo) {
        if (offsetLocal < 0 || offsetLocal + 30 > dados.length || u32(dados, offsetLocal) != 0x04034b50L) {
            throw new BadRequestException(INVALIDO);
        }
        int nome = u16(dados, offsetLocal + 26);
        int extra = u16(dados, offsetLocal + 28);
        long inicio = (long) offsetLocal + 30 + nome + extra;
        if (inicio > dados.length || compactado > dados.length - inicio) {
            throw new BadRequestException(INVALIDO);
        }
        int tamanho = (int) compactado;
        if (metodo == 0) {
            if (compactado != descompactado) {
                throw new BadRequestException(INVALIDO);
            }
            byte[] cru = new byte[tamanho];
            System.arraycopy(dados, (int) inicio, cru, 0, tamanho);
            return cru;
        }
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(dados, (int) inicio, tamanho);
            byte[] saida = new byte[(int) descompactado];
            int lido = inflater.inflate(saida);
            if (lido != descompactado || !inflater.finished()) {
                throw new BadRequestException(LIMITE);
            }
            return saida;
        } catch (DataFormatException e) {
            throw new BadRequestException(INVALIDO);
        } finally {
            inflater.end();
        }
    }

    private static int indiceEocd(byte[] dados) {
        int minimo = Math.max(0, dados.length - 22 - 65535);
        for (int i = dados.length - 22; i >= minimo; i--) {
            if (u32(dados, i) == 0x06054b50L) {
                return i;
            }
        }
        throw new BadRequestException(INVALIDO);
    }

    private static int u16(byte[] dados, int i) {
        if (i < 0 || i + 1 >= dados.length) {
            throw new BadRequestException(INVALIDO);
        }
        return (dados[i] & 0xff) | ((dados[i + 1] & 0xff) << 8);
    }

    private static long u32(byte[] dados, int i) {
        if (i < 0 || i + 3 >= dados.length) {
            throw new BadRequestException(INVALIDO);
        }
        return (dados[i] & 0xffL)
                | ((dados[i + 1] & 0xffL) << 8)
                | ((dados[i + 2] & 0xffL) << 16)
                | ((dados[i + 3] & 0xffL) << 24);
    }
}
