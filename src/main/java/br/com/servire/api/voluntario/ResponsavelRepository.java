package br.com.servire.api.voluntario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Desde a correção do bug de {@code @TenantId} ausente (ver javadoc de
 * {@link Responsavel}), {@code responsaveis} já é tenant-aware por conta
 * própria — o Hibernate filtra automaticamente por
 * {@link br.com.servire.api.tenant.TenantContext} em qualquer consulta,
 * inclusive {@link #findById}. Mesmo assim, este repositório continua só
 * devendo ser acessado a partir de {@link VoluntarioService}, nunca
 * diretamente de um controller — a validação de negócio (responsável
 * principal único, seção 38) vive lá, não aqui.
 */
public interface ResponsavelRepository extends JpaRepository<Responsavel, UUID> {

    List<Responsavel> findByVoluntario_Id(UUID voluntarioId);
}
