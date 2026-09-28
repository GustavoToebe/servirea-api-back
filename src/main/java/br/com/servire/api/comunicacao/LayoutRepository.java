package br.com.servire.api.comunicacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface LayoutRepository extends JpaRepository<Layout, UUID>, JpaSpecificationExecutor<Layout> {

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    boolean existsByNomeIgnoreCase(String nome);
}
