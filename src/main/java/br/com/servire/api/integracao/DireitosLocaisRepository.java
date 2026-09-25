package br.com.servire.api.integracao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DireitosLocaisRepository extends JpaRepository<DireitosLocais, UUID> {

    List<DireitosLocais> findAll();
}
