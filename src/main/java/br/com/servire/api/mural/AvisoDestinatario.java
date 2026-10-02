package br.com.servire.api.mural;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
@Entity @Table(name="mural_destinatario")
public class AvisoDestinatario {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 @Column(name="aviso_id",nullable=false) public UUID avisoId;
 @Column(name="usuario_id",nullable=false) public UUID usuarioId;

}
