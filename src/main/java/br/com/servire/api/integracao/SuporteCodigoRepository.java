package br.com.servire.api.integracao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

public interface SuporteCodigoRepository extends JpaRepository<SuporteCodigo, UUID> {

    Optional<SuporteCodigo> findByHashCodigo(String hashCodigo);

    /** Uso único atômico: 0 = outra troca chegou antes. */
    @Modifying
    @Query("update SuporteCodigo c set c.usadoEm = :agora where c.id = :id and c.usadoEm is null")
    int marcarUsado(UUID id, Instant agora);
}
