package br.com.servire.api.comunicacao;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="aniversario_config")
public class AniversarioConfig {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public TipoEnvio canal;
 @Version public long versao; public boolean ativo; @Column(name="layout_id") public UUID layoutId;
}
