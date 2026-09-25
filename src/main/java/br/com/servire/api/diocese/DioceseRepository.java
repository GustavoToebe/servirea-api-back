package br.com.servire.api.diocese;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DioceseRepository extends JpaRepository<Diocese, UUID> {
}
