package br.com.servire.api.comunicacao;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ComunicadoDestinatarioRepository extends JpaRepository<ComunicadoDestinatario, UUID> {

    List<ComunicadoDestinatario> findByComunicadoIdOrderByNome(UUID comunicadoId);

    List<ComunicadoDestinatario> findByComunicadoIdAndStatus(UUID comunicadoId, StatusEnvio status);

    long countByComunicadoIdAndStatus(UUID comunicadoId, StatusEnvio status);

    /** Próximos da fila: pendentes dos comunicados não concluídos, mais antigos primeiro. */
    @Query("""
            select d from ComunicadoDestinatario d, Comunicado c
            where c.id = d.comunicadoId and d.status = br.com.servire.api.comunicacao.StatusEnvio.PENDENTE
              and c.status in :situacoes
            order by c.createdAt, d.nome
            """)
    List<ComunicadoDestinatario> proximosDaFila(Collection<StatusComunicado> situacoes, Pageable pagina);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ComunicadoDestinatario d where d.id=:id")
    java.util.Optional<ComunicadoDestinatario> buscarParaAlterar(@org.springframework.data.repository.query.Param("id") UUID id);
    @Query("""
        select d.id from ComunicadoDestinatario d, Comunicado c
        where c.id=d.comunicadoId and d.status=:status and c.status in :situacoes and c.canal=:canal
          and d.proximaTentativa<=:agora and (d.reservaAte is null or d.reservaAte<=:agora)
        order by d.proximaTentativa,c.createdAt,d.id
        """)
    List<UUID> candidatos(@org.springframework.data.repository.query.Param("status") StatusEnvio status,
        @org.springframework.data.repository.query.Param("situacoes") Collection<StatusComunicado> situacoes,
        @org.springframework.data.repository.query.Param("canal") TipoEnvio canal,
        @org.springframework.data.repository.query.Param("agora") java.time.Instant agora, Pageable pagina);
    @Query("select d.comunicadoId from ComunicadoDestinatario d where d.id=:id")
    java.util.Optional<UUID> comunicadoDoDestinatario(@org.springframework.data.repository.query.Param("id") UUID id);
}
