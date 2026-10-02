package br.com.servire.api.checkin;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Entidades do check-in por encontro (F15). O token em texto nunca é gravado: só o hash SHA-256. */
public final class Checkin {
    private Checkin() {}

    @Entity(name = "CheckinSessao")
    @Table(name = "checkin_sessao")
    public static class Sessao {
        @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
        @TenantId @Column(name = "tenant_id", nullable = false) public UUID tenantId;
        @Column(name = "evento_id", nullable = false) public UUID eventoId;
        @Column(name = "token_hash", nullable = false, length = 64) public String tokenHash;
        @Column(name = "criado_por") public UUID criadoPor;
        @Column(name = "criado_em", nullable = false) public Instant criadoEm;
        @Column(name = "expira_em", nullable = false) public Instant expiraEm;
        @Column(name = "revogado_em") public Instant revogadoEm;

        public boolean ativa(Instant agora) {
            return revogadoEm == null && expiraEm.isAfter(agora);
        }
    }

    @Entity(name = "CheckinRegistro")
    @Table(name = "checkin_registro")
    public static class Registro {
        @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
        @TenantId @Column(name = "tenant_id", nullable = false) public UUID tenantId;
        @Column(name = "sessao_id", nullable = false) public UUID sessaoId;
        @Column(name = "vaga_id", nullable = false) public UUID vagaId;
        @Column(name = "usuario_id") public UUID usuarioId;
        @Column(name = "registrado_em", nullable = false) public Instant registradoEm;
    }
}
