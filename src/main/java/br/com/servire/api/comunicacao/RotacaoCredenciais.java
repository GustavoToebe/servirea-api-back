package br.com.servire.api.comunicacao;

import br.com.servire.api.security.CifraCredencial;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;

/** Na subida, protege tokens legados e recifra versões antigas antes do primeiro ciclo da fila. */
@Component
public class RotacaoCredenciais implements ApplicationRunner {
    private final ParoquiaWhatsappRepository repository;
    private final CifraCredencial cifra;
    private final TransactionTemplate tx;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    public RotacaoCredenciais(ParoquiaWhatsappRepository repository,CifraCredencial cifra,PlatformTransactionManager tm) {
        this.repository=repository; this.cifra=cifra; this.tx=new TransactionTemplate(tm);
    }
    @Override public void run(ApplicationArguments args) {
        if (!cifra.configurada()) {
            if (repository.count()>0) throw new IllegalStateException("Existem credenciais WhatsApp: configure a chave de cifra antes de iniciar.");
            return;
        }
        int pagina=0;
        while (true) {
            int atual=pagina++;
            Boolean ultima=tx.execute(status -> {
                var lote=repository.findAll(PageRequest.of(atual,100,org.springframework.data.domain.Sort.by("tenantId")));
                for (var c : lote) {
                    em.refresh(c,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                    if (cifra.precisaRotacionar(c.getToken())) {
                    String claro=c.getToken().startsWith("enc:") ? cifra.abrir(c.getTenantId(),c.getToken()) : c.getToken();
                    c.atualizar(c.getInstancia(),cifra.cifrar(c.getTenantId(),claro),c.isAtivo());
                }
                }
                repository.flush(); return lote.isLast();
            });
            if (Boolean.TRUE.equals(ultima)) break;
        }
    }
}
