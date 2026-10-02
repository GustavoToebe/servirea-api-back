package br.com.servire.api.mural;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.*;
import java.util.UUID;
/** Registro paroquial simples; versão impede sobrescrita de edição concorrente. */
@Entity @Table(name="mural_aviso")
public class Aviso {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Version public long versao;
 @Column(nullable=false,length=160) public String titulo;
 @Column(nullable=false,length=4000) public String descricao;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) public Status status;
 public LocalDate prazo;

 @Column(name="criado_em",nullable=false) public Instant criadoEm;
 @Column(name="atualizado_em",nullable=false) public Instant atualizadoEm;
 public enum Status { PUBLICADO, ARQUIVADO }
}
