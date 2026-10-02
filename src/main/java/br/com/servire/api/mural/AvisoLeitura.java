package br.com.servire.api.mural;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
@Entity @Table(name="mural_leitura")
public class AvisoLeitura {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Column(name="aviso_id",nullable=false) public UUID avisoId;
 @Column(name="usuario_id",nullable=false) public UUID usuarioId;
 @Column(name="versao_aviso",nullable=false) public long versaoAviso;
 @Column(name="lido_em",nullable=false) public java.time.Instant lidoEm;
}
