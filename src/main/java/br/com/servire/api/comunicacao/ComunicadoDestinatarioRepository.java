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
}
