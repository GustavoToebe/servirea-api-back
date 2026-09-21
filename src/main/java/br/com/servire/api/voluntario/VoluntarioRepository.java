package br.com.servire.api.voluntario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repositório tenant-aware: toda query gerada aqui recebe automaticamente
 * o filtro {@code WHERE tenant_id = ?} do Hibernate via {@code @TenantId}
 * em {@link Voluntario} - inclusive {@link #findById}, que é exatamente o
 * cenário do teste crítico de isolamento (seção 78: buscar por ID de
 * outro tenant não deve retornar nada), seção 79/80 do plano mestre.
 */
public interface VoluntarioRepository extends JpaRepository<Voluntario, UUID> {
}
