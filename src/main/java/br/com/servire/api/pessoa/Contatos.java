package br.com.servire.api.pessoa;

import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.web.BadRequestException;

import java.util.List;

/** Regras comuns das listas 1:N de e-mail/telefone (um principal se a lista não é vazia). */
public final class Contatos {

    private Contatos() {
    }

    public static void exigirUmPrincipalEmail(List<ContatoEmailRequest> emails) {
        if (emails == null || emails.isEmpty()) {
            return;
        }
        long principais = emails.stream().filter(ContatoEmailRequest::principal).count();
        if (principais != 1) {
            throw new BadRequestException(
                    "Deve existir exatamente um e-mail principal quando a lista não é vazia — recebido: "
                            + principais + ".");
        }
    }

    public static void exigirUmPrincipalTelefone(List<ContatoTelefoneRequest> telefones) {
        if (telefones == null || telefones.isEmpty()) {
            return;
        }
        long principais = telefones.stream().filter(ContatoTelefoneRequest::principal).count();
        if (principais != 1) {
            throw new BadRequestException(
                    "Deve existir exatamente um telefone principal quando a lista não é vazia — recebido: "
                            + principais + ".");
        }
    }
}
