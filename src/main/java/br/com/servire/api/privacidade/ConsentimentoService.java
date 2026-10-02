package br.com.servire.api.privacidade;

import br.com.servire.api.privacidade.PrivacidadeDtos.ConsentimentoItem;
import br.com.servire.api.privacidade.PrivacidadeDtos.PaginaConsentimentos;
import br.com.servire.api.privacidade.Privacidade.Consentimento;
import br.com.servire.api.privacidade.Privacidade.TipoConsentimento;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Histórico de consentimentos (F09). Cada mudança de autorização grava uma linha imutável com a fonte e quem
 * registrou; o estado vigente continua no cadastro. Não guarda texto livre da pessoa nem contato.
 */
@Service
public class ConsentimentoService {
    private static final int PAGINA = 30;

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void registrar(UUID pessoaId, TipoConsentimento tipo, boolean concedido, String fonte) {
        Consentimento c = new Consentimento();
        c.pessoaId = pessoaId;
        c.tipo = tipo;
        c.concedido = concedido;
        c.fonte = fonte == null || fonte.isBlank() ? "Não informada" : fonte.trim().substring(0, Math.min(200, fonte.trim().length()));
        c.registradoPor = usuarioAtual();
        c.registradoEm = Instant.now();
        em.persist(c);
    }

    @Transactional(readOnly = true)
    public PaginaConsentimentos historico(UUID pessoaId, int pagina) {
        if (pagina < 0 || pagina > 100000) {
            throw new BadRequestException("Página inválida.");
        }
        if (em.createQuery("select count(p) from Pessoa p where p.id=:id", Long.class).setParameter("id", pessoaId).getSingleResult() == 0) {
            throw new ResourceNotFoundException("Pessoa não encontrada.");
        }
        long total = em.createQuery("select count(c) from Consentimento c where c.pessoaId=:id", Long.class)
                .setParameter("id", pessoaId).getSingleResult();
        var itens = em.createQuery("select c from Consentimento c where c.pessoaId=:id order by c.registradoEm desc, c.id desc", Consentimento.class)
                .setParameter("id", pessoaId).setFirstResult(pagina * PAGINA).setMaxResults(PAGINA).getResultList().stream()
                .map(c -> new ConsentimentoItem(c.tipo, c.concedido, c.fonte, c.registradoEm)).toList();
        return new PaginaConsentimentos(itens, total, pagina, PAGINA);
    }

    private static UUID usuarioAtual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthenticatedUser u ? u.usuarioId() : null;
    }
}
