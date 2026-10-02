package br.com.servire.api.pastoral;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface EquipePastoralRepository extends JpaRepository<EquipePastoral,UUID>,JpaSpecificationExecutor<EquipePastoral> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from EquipePastoral e where e.id=:id") Optional<EquipePastoral> bloquear(@Param("id") UUID id);
}
