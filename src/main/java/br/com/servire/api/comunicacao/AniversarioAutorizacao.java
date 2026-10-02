package br.com.servire.api.comunicacao;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="aniversario_autorizacao")
public class AniversarioAutorizacao {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public TipoEnvio canal;
 @Version public long versao; @Column(name="pessoa_id") public UUID pessoaId; public boolean autorizado; @Column(nullable=false,length=200) public String fonte; @Column(name="registrado_em",nullable=false) public Instant registradoEm;
}
