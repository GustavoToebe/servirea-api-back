package br.com.servire.api.site;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
import java.time.Instant;
@Entity @Table(name="site_paroquia")
public class SiteParoquia {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Version public long versao;
 @Column(nullable=false,columnDefinition="text") public String rascunho;
 @Column(columnDefinition="text") public String publicado;
 @Column(name="publicado_em") public Instant publicadoEm;
}
