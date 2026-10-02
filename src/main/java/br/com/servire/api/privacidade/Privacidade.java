package br.com.servire.api.privacidade;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Entidades de privacidade (F09): histórico de consentimentos, política de retenção e execuções. */
public final class Privacidade {
    private Privacidade() {}

    public enum TipoConsentimento { WHATSAPP, ANIVERSARIO_EMAIL, ANIVERSARIO_WHATSAPP }

    /** Registro imutável de uma concessão ou revogação. O estado vigente continua nas tabelas de origem. */
    @Entity(name = "Consentimento")
    @Table(name = "consentimento_historico")
    public static class Consentimento {
        @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
        @TenantId @Column(name = "tenant_id", nullable = false) public UUID tenantId;
        @Column(name = "pessoa_id", nullable = false) public UUID pessoaId;
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) public TipoConsentimento tipo;
        @Column(nullable = false) public boolean concedido;
        @Column(nullable = false, length = 200) public String fonte;
        @Column(name = "registrado_por") public UUID registradoPor;
        @Column(name = "registrado_em", nullable = false) public Instant registradoEm;
    }

    @Entity(name = "Politica")
    @Table(name = "retencao_politica")
    public static class Politica {
        @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
        @TenantId @Column(name = "tenant_id", nullable = false) public UUID tenantId;
        @Column(name = "comunicados_dias") public Integer comunicadosDias;
        @Version public long versao;
    }

    @Entity(name = "Execucao")
    @Table(name = "retencao_execucao")
    public static class Execucao {
        @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
        @TenantId @Column(name = "tenant_id", nullable = false) public UUID tenantId;
        @Column(name = "executado_em", nullable = false) public Instant executadoEm;
        @Column(name = "executado_por") public UUID executadoPor;
        @Column(nullable = false) public Instant corte;
        @Column(name = "comunicados_anonimizados", nullable = false) public int comunicadosAnonimizados;
    }
}
