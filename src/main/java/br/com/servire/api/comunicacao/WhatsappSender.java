package br.com.servire.api.comunicacao;

/** Envio de texto pelo WhatsApp da paróquia (instância e token dela). */
public interface WhatsappSender {

    void enviarTexto(String instancia, String token, String telefone, String texto);
}
