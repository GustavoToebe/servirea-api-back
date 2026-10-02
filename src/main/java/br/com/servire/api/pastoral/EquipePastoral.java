package br.com.servire.api.pastoral;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
@Entity @Table(name="pastoral_equipe")
public class EquipePastoral {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Column(nullable=false,length=120) public String nome;
 @Column(length=1000) public String descricao;
 public boolean ativo=true;
 @Version public long versao;
}
