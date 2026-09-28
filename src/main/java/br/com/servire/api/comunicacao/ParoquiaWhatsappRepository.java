package br.com.servire.api.comunicacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ParoquiaWhatsappRepository extends JpaRepository<ParoquiaWhatsapp, UUID> {
}
