package br.com.servire.api.estoque;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.util.UUID;
import java.math.BigDecimal;
@Entity @Table(name="estoque_item") public class EstoqueItem {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @TenantId @Column(name="tenant_id",nullable=false) public UUID tenantId;
    @Version public long versao;
    public String nome;
    public String codigo;
    public String tipo;
    public String unidade;
    public String local;
    @Column(name="responsavel_usuario_id")public UUID responsavelUsuarioId;
    @Column(name="atualizado_em") public java.time.Instant atualizadoEm;
    public boolean ativo=true;
    @Column(precision=12,scale=3)public BigDecimal saldo=BigDecimal.ZERO;
}
