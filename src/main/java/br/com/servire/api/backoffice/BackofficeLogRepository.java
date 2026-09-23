package br.com.servire.api.backoffice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BackofficeLogRepository extends JpaRepository<BackofficeLog, UUID> {

    List<BackofficeLog> findTop200ByOrderByCreatedAtDesc();
}
