package br.com.servire.api.escala;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EscalaRepository extends JpaRepository<Escala, UUID> {

    @Query("""
            SELECT e FROM Escala e
            WHERE (:tipo IS NULL OR e.tipo = :tipo)
              AND (:status IS NULL OR e.status = :status)
              AND (:ano IS NULL OR e.ano = :ano)
              AND (:mes IS NULL OR e.mes = :mes)
            ORDER BY e.ano DESC NULLS LAST, e.mes DESC NULLS LAST, e.titulo ASC
            """)
    List<Escala> buscar(@Param("tipo") TipoEscala tipo, @Param("status") StatusEscala status,
                         @Param("ano") Integer ano, @Param("mes") Integer mes);
}
