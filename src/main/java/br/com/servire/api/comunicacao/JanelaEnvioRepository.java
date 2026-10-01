package br.com.servire.api.comunicacao;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface JanelaEnvioRepository extends JpaRepository<JanelaEnvio,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from JanelaEnvio j where j.id=:id")
    Optional<JanelaEnvio> buscarParaAlterar(@Param("id") String id);
}
