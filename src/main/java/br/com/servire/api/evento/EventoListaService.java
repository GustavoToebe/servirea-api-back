package br.com.servire.api.evento;

import br.com.servire.api.evento.EventoDtos.EventoResumo;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.web.PaginaLista;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

/** Página consistente: consultas em lote; assinatura externa após liberar a conexão do banco. */
@Service
public class EventoListaService {
    @PersistenceContext private EntityManager em;
    private final EventoService eventos;
    private final StorageService storage;
    private final TransactionTemplate leitura;
    public EventoListaService(EventoService eventos, StorageService storage, PlatformTransactionManager manager) {
        this.eventos=eventos;this.storage=storage;
        leitura=new TransactionTemplate(manager);leitura.setReadOnly(true);
        leitura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    private record Linha(EventoResumo resumo,String caminho) {}
    private record Snapshot(List<Linha> linhas,long total) {}

    public PaginaLista<EventoResumo> pagina(int pagina,int tamanho) {
        var pedido=PaginaLista.pedido(pagina,tamanho,Sort.unsorted());
        Snapshot dados=Objects.requireNonNull(leitura.execute(status -> carregar(pedido.getPageNumber(),pedido.getPageSize())));
        var itens=dados.linhas().stream().map(l -> {
            var e=l.resumo();
            return new EventoResumo(e.id(),e.titulo(),e.inicio(),e.termino(),e.localNome(),e.situacao(),e.inscritos(),e.vagas(),
                    l.caminho()==null ? null : storage.gerarUrlAssinada(l.caminho()));
        }).toList();
        return new PaginaLista<>(itens,pagina,tamanho,dados.total(),(int)((dados.total()+tamanho-1)/tamanho));
    }
    private Snapshot carregar(int pagina,int tamanho) {
        LocalDateTime agora=eventos.agora();
        var cb=em.getCriteriaBuilder();var consulta=cb.createQuery(Evento.class);var e=consulta.from(Evento.class);
        // Rascunhos sempre permanecem próximos, como na regra vigente da tela.
        var proximo=cb.or(cb.equal(e.get("situacao"),Evento.Situacao.RASCUNHO),
            cb.and(cb.equal(e.get("situacao"),Evento.Situacao.PUBLICADO),
                cb.greaterThan(cb.<LocalDateTime>coalesce(e.get("termino"),e.get("inicio")),agora)));
        var grupo=cb.<Integer>selectCase().when(proximo,0).otherwise(1);
        var inicioProximo=cb.<LocalDateTime>selectCase().when(proximo,e.<LocalDateTime>get("inicio")).otherwise(cb.nullLiteral(LocalDateTime.class));
        var inicioPassado=cb.<LocalDateTime>selectCase().when(cb.not(proximo),e.<LocalDateTime>get("inicio")).otherwise(cb.nullLiteral(LocalDateTime.class));
        consulta.select(e).orderBy(cb.asc(grupo),cb.asc(inicioProximo),cb.desc(inicioPassado),cb.asc(e.get("id")));
        var lista=em.createQuery(consulta).setFirstResult(pagina*tamanho).setMaxResults(tamanho).getResultList();
        var contagem=cb.createQuery(Long.class);contagem.select(cb.count(contagem.from(Evento.class)));
        long total=em.createQuery(contagem).getSingleResult();
        if(lista.isEmpty())return new Snapshot(List.of(),total);
        var ids=lista.stream().map(Evento::getId).toList();
        Map<UUID,Long> inscritos=new HashMap<>();
        for(var linha:em.createQuery("select i.eventoId, count(i) from EventoInscricao i where i.eventoId in :ids group by i.eventoId",Object[].class)
            .setParameter("ids",ids).getResultList()) inscritos.put((UUID)linha[0],(Long)linha[1]);
        Map<UUID,String> capas=new HashMap<>();
        for(var foto:em.createQuery("select f from EventoFoto f where f.eventoId in :ids order by f.capa desc, f.createdAt asc, f.id asc",EventoFoto.class)
            .setParameter("ids",ids).getResultList())capas.putIfAbsent(foto.getEventoId(),foto.getCaminho());
        return new Snapshot(lista.stream().map(item -> new Linha(new EventoResumo(item.getId(),item.getTitulo(),item.getInicio(),item.getTermino(),
            item.getLocalNome(),EventoService.situacao(item,agora),inscritos.getOrDefault(item.getId(),0L),item.getVagas(),null),capas.get(item.getId()))).toList(),total);
    }
}
