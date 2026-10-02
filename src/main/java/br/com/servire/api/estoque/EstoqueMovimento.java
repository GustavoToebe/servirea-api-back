package br.com.servire.api.estoque;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="estoque_movimento") public class EstoqueMovimento {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
    @Column(name="item_id") public UUID itemId;
    public UUID chave;
    public String tipo;
    public String motivo;
    @Column(name="responsavel_usuario_id")public UUID responsavelUsuarioId;
    @Column(name="registrado_por")public UUID registradoPor;
    @Column(name="registrado_em")public Instant registradoEm;
    @Column(precision=12,scale=3)public BigDecimal quantidade;
    @Column(name="saldo_antes",precision=12,scale=3)public BigDecimal saldoAntes;
    @Column(name="saldo_depois",precision=12,scale=3)public BigDecimal saldoDepois;
}
