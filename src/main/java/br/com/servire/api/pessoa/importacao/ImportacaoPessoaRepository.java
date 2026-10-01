package br.com.servire.api.pessoa.importacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ImportacaoPessoaRepository extends JpaRepository<ImportacaoPessoa,UUID> {
    Optional<ImportacaoPessoa> findByChave(UUID chave);
}
