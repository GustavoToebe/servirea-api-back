package br.com.servire.api.mural;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface AvisoRepository extends JpaRepository<Aviso,UUID>, JpaSpecificationExecutor<Aviso> { }
