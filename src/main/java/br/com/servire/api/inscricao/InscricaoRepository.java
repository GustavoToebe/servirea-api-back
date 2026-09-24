package br.com.servire.api.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * Sem {@code @EntityGraph}: e-mails, telefones e responsáveis (com os
 * contatos deles) são bags — o grafo com todas fazia o Hibernate falhar em
 * "Could not generate fetch" e o {@code GET /inscricoes} voltava 500 sempre.
 * {@code InscricaoService} inicializa as coleções dentro da transação.
 */
public interface InscricaoRepository extends JpaRepository<Inscricao, UUID>, JpaSpecificationExecutor<Inscricao> {
}
