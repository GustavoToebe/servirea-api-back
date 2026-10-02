package br.com.servire.api.escala;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
/** Versão compartilhada por coordenação e portal, incrementada sob lock da paróquia (V065). */
@Entity @Table(name="indisponibilidade_mes")
public class IndisponibilidadeMes {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
 public int ano;public int mes;public long versao;
}
