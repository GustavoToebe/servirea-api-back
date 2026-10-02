package br.com.servire.api.storage;

import br.com.servire.api.storage.ArquivoPendencia.Tipo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ciclo de vida durável dos arquivos no Storage (T13). O HTTP do provedor nunca decide sozinho o destino de um arquivo:
 * antes de enviar fica registrado um UPLOAD (em transação própria, para sobreviver a um rollback do negócio); ao confirmar
 * a referência no banco o registro some; um arquivo que deixa de ser referenciado vira REMOCAO na mesma transação do negócio.
 * O processamento tenta de novo com espera crescente e confere se o caminho ainda está em uso antes de apagar qualquer coisa.
 */
@Service
public class ArquivoCicloService {
    private static final Logger log = LoggerFactory.getLogger(ArquivoCicloService.class);
    static final Duration CARENCIA_UPLOAD = Duration.ofHours(6);
    static final Duration PRIMEIRA_ESPERA = Duration.ofMinutes(2);
    static final Duration ESPERA_MAXIMA = Duration.ofHours(6);
    static final int LOTE = 10;
    static final int ERRO_MAX = 200;

    @PersistenceContext
    private EntityManager em;
    private final StorageService storage;
    private final TransactionTemplate propria;

    public ArquivoCicloService(StorageService storage, PlatformTransactionManager tm) {
        this.storage = storage;
        this.propria = new TransactionTemplate(tm);
        this.propria.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Antes do upload. Transação própria: sobrevive mesmo que a operação de negócio seja revertida depois. */
    public void registrarUpload(String caminho) {
        propria.executeWithoutResult(tx -> inserir(caminho, Tipo.UPLOAD, Instant.now().plus(CARENCIA_UPLOAD)));
    }

    /** Dentro da transação do negócio, depois de gravar a referência: após o commit, o UPLOAD deixa de ser pendência. */
    public void aoConfirmar(String caminho) {
        depoisDoCommit(() -> propria.executeWithoutResult(tx -> remover(caminho, Tipo.UPLOAD)));
    }

    /**
     * A gravação da referência falhou depois do upload (cota, conflito, erro). Remove o arquivo já e encerra a pendência; se o
     * provedor falhar, a pendência continua e o job limpa depois da carência. Nunca lança: não pode mascarar o erro original.
     */
    public void descartarUpload(String caminho) {
        try {
            if (!emUso(caminho) && storage.excluirConfirmando(caminho)) {
                propria.executeWithoutResult(tx -> remover(caminho, Tipo.UPLOAD));
            }
        } catch (RuntimeException e) {
            log.warn("Descarte imediato de upload adiado para o job: {}", e.getClass().getSimpleName());
        }
    }

    /** Dentro da transação do negócio: o arquivo deixou de ser referenciado. Só vale se a transação confirmar. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void agendarRemocao(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            return;
        }
        inserir(caminho, Tipo.REMOCAO, Instant.now().plus(PRIMEIRA_ESPERA));
        depoisDoCommit(() -> tentarAgora(caminho));
    }

    private void tentarAgora(String caminho) {
        try {
            if (!emUso(caminho) && storage.excluirConfirmando(caminho)) {
                propria.executeWithoutResult(tx -> remover(caminho, Tipo.REMOCAO));
            }
        } catch (RuntimeException e) {
            log.warn("Remoção imediata de arquivo adiada para o job: {}", e.getClass().getSimpleName());
        }
    }

    private void inserir(String caminho, Tipo tipo, Instant proxima) {
        boolean existe = em.createQuery("select count(p) from ArquivoPendencia p where p.caminho=:c and p.tipo=:t", Long.class)
                .setParameter("c", caminho).setParameter("t", tipo).getSingleResult() > 0;
        if (existe) {
            return;
        }
        ArquivoPendencia p = new ArquivoPendencia();
        p.caminho = caminho;
        p.tipo = tipo;
        p.criadoEm = Instant.now();
        p.proximaTentativa = proxima;
        em.persist(p);
        em.flush();
    }

    private void remover(String caminho, Tipo tipo) {
        em.createQuery("delete from ArquivoPendencia p where p.caminho=:c and p.tipo=:t")
                .setParameter("c", caminho).setParameter("t", tipo).executeUpdate();
    }

    private static void depoisDoCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        acao.run();
                    } catch (RuntimeException e) {
                        log.warn("Pós-commit de arquivo falhou e ficará para o job: {}", e.getClass().getSimpleName());
                    }
                }
            });
        } else {
            acao.run();
        }
    }

    /** Verdadeiro se alguma referência atual (voluntário, inscrição ou foto de evento) usa o caminho. */
    boolean emUso(String caminho) {
        return em.createQuery("select count(v) from Voluntario v where v.fotoPath=:c", Long.class).setParameter("c", caminho).getSingleResult() > 0
                || em.createQuery("select count(i) from Inscricao i where i.fotoPath=:c", Long.class).setParameter("c", caminho).getSingleResult() > 0
                || em.createQuery("select count(f) from EventoFoto f where f.caminho=:c", Long.class).setParameter("c", caminho).getSingleResult() > 0;
    }

    // ---- Processamento (uma paróquia por vez; o chamador define o TenantContext)

    private record Item(UUID id, String caminho, Tipo tipo, Instant criadoEm, int tentativas) {}

    /** Reserva e resolve até {@value #LOTE} pendências vencidas da paróquia atual. Devolve quantas foram resolvidas. */
    public int processarLote() {
        UUID dono = UUID.randomUUID();
        List<Item> itens = propria.execute(tx -> reservar(dono));
        int resolvidas = 0;
        for (Item item : itens == null ? List.<Item>of() : itens) {
            Resultado r = decidir(item);
            propria.executeWithoutResult(tx -> concluir(item, dono, r));
            if (r == Resultado.RESOLVIDA) {
                resolvidas++;
            }
        }
        return resolvidas;
    }

    private enum Resultado { RESOLVIDA, ESPERAR, FALHA }

    private List<Item> reservar(UUID dono) {
        Instant agora = Instant.now();
        List<ArquivoPendencia> candidatas = em.createQuery("select p from ArquivoPendencia p where p.proximaTentativa<=:agora and (p.reservaAte is null or p.reservaAte<:agora) order by p.proximaTentativa",
                ArquivoPendencia.class).setParameter("agora", agora).setMaxResults(LOTE).getResultList();
        List<Item> itens = new ArrayList<>();
        for (ArquivoPendencia p : candidatas) {
            int n = em.createQuery("update ArquivoPendencia p set p.reservadoPor=:dono, p.reservaAte=:ate where p.id=:id and (p.reservaAte is null or p.reservaAte<:agora)")
                    .setParameter("dono", dono).setParameter("ate", agora.plusSeconds(120)).setParameter("id", p.id).setParameter("agora", agora).executeUpdate();
            if (n == 1) {
                itens.add(new Item(p.id, p.caminho, p.tipo, p.criadoEm, p.tentativas));
            }
        }
        return itens;
    }

    private Resultado decidir(Item item) {
        try {
            if (emUso(item.caminho())) {
                return Resultado.RESOLVIDA; // referência ativa: nada a apagar, a pendência só some
            }
            if (item.tipo() == Tipo.UPLOAD && item.criadoEm().plus(CARENCIA_UPLOAD).isAfter(Instant.now())) {
                return Resultado.ESPERAR; // ainda dentro da carência: a operação de negócio pode estar em andamento
            }
            return storage.excluirConfirmando(item.caminho()) ? Resultado.RESOLVIDA : Resultado.FALHA;
        } catch (RuntimeException e) {
            return Resultado.FALHA;
        }
    }

    private void concluir(Item item, UUID dono, Resultado r) {
        ArquivoPendencia p = em.find(ArquivoPendencia.class, item.id());
        if (p == null || !dono.equals(p.reservadoPor)) {
            return;
        }
        if (r == Resultado.RESOLVIDA) {
            em.remove(p);
            return;
        }
        p.reservadoPor = null;
        p.reservaAte = null;
        if (r == Resultado.ESPERAR) {
            p.proximaTentativa = item.criadoEm().plus(CARENCIA_UPLOAD);
            return;
        }
        p.tentativas = item.tentativas() + 1;
        p.erro = "Falha ao remover do armazenamento.";
        long minutos = Math.min(ESPERA_MAXIMA.toMinutes(), 5L << Math.min(p.tentativas, 10));
        p.proximaTentativa = Instant.now().plus(Duration.ofMinutes(minutos));
    }
}
