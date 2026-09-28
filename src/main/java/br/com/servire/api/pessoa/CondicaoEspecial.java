package br.com.servire.api.pessoa;

import br.com.servire.api.web.BadRequestException;
import java.util.Arrays;

public enum CondicaoEspecial {
    SINDROME_DOWN,
    TEA,
    TDAH,
    ANSIEDADE,
    DEPRESSAO,
    BORDERLINE,
    OUTRA;

    public static void validar(CondicaoEspecial[] condicoes, Integer[] nivelRef, String[] outraRef) {
        boolean temTea = false;
        boolean temOutra = false;

        if (condicoes != null) {
            temTea = Arrays.asList(condicoes).contains(TEA);
            temOutra = Arrays.asList(condicoes).contains(OUTRA);
        }

        if (!temTea) {
            nivelRef[0] = null;
        }

        if (temOutra) {
            if (outraRef[0] == null || outraRef[0].trim().isEmpty()) {
                throw new BadRequestException("Descreva a outra condição.");
            }
            outraRef[0] = outraRef[0].trim();
        } else {
            outraRef[0] = null;
        }
    }
}
