package br.com.servire.api.diocese.dto;

import br.com.servire.api.diocese.Diocese;

import java.util.UUID;

public record DioceseResponse(UUID id, String nome, String uf) {

    public static DioceseResponse de(Diocese diocese) {
        return new DioceseResponse(diocese.getId(), diocese.getNome(), diocese.getUf());
    }
}
