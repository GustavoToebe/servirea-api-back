package br.com.servire.api.liturgia;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
@Entity @Table(name="liturgia_referencia")
public class ReferenciaLiturgica {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
    @Version public long versao;
    public String titulo;
    public String fonte;
    public String url;
    public String observacao;
    public boolean ativo=true;
}
