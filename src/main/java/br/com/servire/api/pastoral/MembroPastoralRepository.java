package br.com.servire.api.pastoral;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.*;
public interface MembroPastoralRepository extends JpaRepository<MembroPastoral,UUID> {
 Optional<MembroPastoral> findByEquipeIdAndPessoaId(UUID equipe,UUID pessoa);
 Page<MembroPastoral> findByEquipeId(UUID equipe,Pageable page);
}
