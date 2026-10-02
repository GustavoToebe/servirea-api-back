package br.com.servire.api.calendario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CalendarioRepository extends JpaRepository<CalendarioAssinatura,UUID> {
 Optional<CalendarioAssinatura> findByTokenHash(String hash);
 Optional<CalendarioAssinatura> findByTenantIdAndUsuarioId(UUID tenant,UUID usuario);
}
