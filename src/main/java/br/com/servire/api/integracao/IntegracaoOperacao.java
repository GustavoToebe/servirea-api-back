package br.com.servire.api.integracao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Uma operação idempotente já executada. {@link Persistable} com
 * {@code isNew} verdadeiro até gravar: com a chave preenchida à mão, o
 * {@code save} do Spring Data faria {@code merge} (UPDATE) e uma segunda
 * gravação da mesma chave passaria em silêncio em vez de esbarrar na PK
 * (25/09/2026).
 */
@Entity
@Table(name = "integracao_operacao")
public class IntegracaoOperacao implements Persistable<String> {

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

    @Transient
    private boolean nova = true;

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

    @Override
    public String getId() {
        return idempotencyKey;
    }

    @Override
    public boolean isNew() {
        return nova;
    }

    @PostLoad
    @PostPersist
    void marcarGravada() {
        nova = false;
    }
}
