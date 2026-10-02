package br.com.servire.api.comunicacao;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="aniversario_execucao")
public class AniversarioExecucao {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public TipoEnvio canal;
 @Column(name="pessoa_id") public UUID pessoaId; public int ano; public LocalDate dia; @Column(name="comunicado_id") public UUID comunicadoId; @Column(nullable=false,length=24) public String status;
}
