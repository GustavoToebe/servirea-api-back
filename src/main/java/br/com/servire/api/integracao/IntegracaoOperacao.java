package br.com.servire.api.integracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "integracao_operacao")
public class IntegracaoOperacao {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "hash_corpo", nullable = false)
    private String hashCorpo;

    @Column(name = "status_http", nullable = false)
    private int statusHttp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String resposta;

    protected IntegracaoOperacao() {
    }

    public IntegracaoOperacao(String idempotencyKey, String tipo, String hashCorpo, int statusHttp, String resposta) {
        this.idempotencyKey = idempotencyKey;
        this.tipo = tipo;
        this.hashCorpo = hashCorpo;
        this.statusHttp = statusHttp;
        this.resposta = resposta;
    }

    public String getHashCorpo() {
        return hashCorpo;
    }

    public int getStatusHttp() {
        return statusHttp;
    }

    public String getResposta() {
        return resposta;
    }
}
