package br.com.servire.api.comunicacao;

import br.com.servire.api.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mensagens de WhatsApp que outros módulos geram (ex.: confirmação e lembrete de evento) entram na mesma fila
 * dos comunicados: mesmo intervalo entre mensagens, modo {@code log}, novas tentativas e histórico na tela
 * Comunicados. Quem chama já montou o texto e já conferiu a autorização de WhatsApp da pessoa.
 */
@Service
public class EnvioAvulso {

    /** Uma mensagem pronta para um número. */
    public record Mensagem(UUID pessoaId, String nome, String telefone, String texto) {
    }

    private final ComunicadoRepository comunicados;
    private final ComunicadoDestinatarioRepository destinatarios;

    public EnvioAvulso(ComunicadoRepository comunicados, ComunicadoDestinatarioRepository destinatarios) {
        this.comunicados = comunicados;
        this.destinatarios = destinatarios;
    }

    /**
     * Enfileira as mensagens num comunicado com o nome {@code origem}. Devolve o id do destinatário de cada
     * mensagem, na mesma ordem, para quem chamou acompanhar a situação do envio.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> enfileirarWhatsapp(String origem, List<Mensagem> mensagens) {
        if (mensagens.isEmpty()) return List.of();
        Comunicado comunicado = comunicados.save(new Comunicado(TipoEnvio.WHATSAPP, origem, usuarioAtual()));
        List<ComunicadoDestinatario> linhas = new ArrayList<>();
        for (Mensagem m : mensagens) {
            linhas.add(new ComunicadoDestinatario(comunicado.getId(), m.pessoaId(), limitar(m.nome(), 200),
                    m.telefone().trim(), null, m.texto()));
        }
        destinatarios.saveAll(linhas);
        comunicado.setTotal(linhas.size());
        return linhas.stream().map(ComunicadoDestinatario::getId).toList();
    }

    /** Situação de envio de cada destinatário (os que não existem mais ficam de fora). */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, StatusEnvio> situacoes(java.util.Collection<UUID> ids) {
        java.util.Map<UUID, StatusEnvio> mapa = new java.util.HashMap<>();
        if (ids.isEmpty()) return mapa;
        destinatarios.findAllById(ids).forEach(d -> mapa.put(d.getId(), d.getStatus()));
        return mapa;
    }

    private static String limitar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static UUID usuarioAtual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthenticatedUser u ? u.usuarioId() : null;
    }
}
