package br.com.servire.api.comunicacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Padrão em dev/test: só loga a instância e o telefone mascarado, nunca o texto. */
@Component
@ConditionalOnProperty(prefix = "servire.whatsapp", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingWhatsappSender implements WhatsappSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingWhatsappSender.class);

    @Override
    public void enviarTexto(String instancia, String token, String telefone, String texto) {
        log.debug("[STUB] WhatsApp pela instância {} para {}", instancia, mascarar(telefone));
    }

    static String mascarar(String telefone) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        return digitos.length() <= 4 ? "****" : "****" + digitos.substring(digitos.length() - 4);
    }
}
