package br.com.servire.api.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InscricaoRepository extends JpaRepository<Inscricao, UUID> {

    @Query("""
            SELECT i FROM Inscricao i
            WHERE (:status IS NULL OR i.status = :status)
            ORDER BY i.createdAt DESC
            """)
    List<Inscricao> buscar(@Param("status") StatusInscricao status);
}
