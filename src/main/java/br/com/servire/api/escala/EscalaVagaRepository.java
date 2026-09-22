package br.com.servire.api.escala;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Acesso direto a {@link EscalaVaga} (Fase 11) — até então a vaga só era
 * lida/gravada indireta, como parte do agregado {@code Escala} (cascade a
 * partir de {@link EscalaRepository}). O controle de faltas (seção 131.5
 * item 11) precisa achar/gravar UMA vaga específica sem carregar a escala
 * inteira, daí este repositório dedicado.
 */
public interface EscalaVagaRepository extends JpaRepository<EscalaVaga, UUID> {
}
