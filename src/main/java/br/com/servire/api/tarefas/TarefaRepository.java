package br.com.servire.api.tarefas;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface TarefaRepository extends JpaRepository<Tarefa,UUID>, JpaSpecificationExecutor<Tarefa> { }
