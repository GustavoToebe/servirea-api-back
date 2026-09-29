package br.com.servire.api.storage;

import br.com.servire.api.web.BadRequestException;

/**
 * Extensão do objeto no storage. Sai só do Content-Type já aceito,
 * nunca do nome enviado pelo navegador (um {@code foto.png/../../outro}
 * virava parte da chave).
 */
public final class ExtensaoDeFoto {

    private ExtensaoDeFoto() {
    }

    public static String de(String contentType) {
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            default -> throw new BadRequestException(
                    "Tipo de arquivo não permitido. Tipos aceitos: image/jpeg, image/png, image/webp, image/heic.");
        };
    }
}
