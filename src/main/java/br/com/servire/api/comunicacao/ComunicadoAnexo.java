package br.com.servire.api.comunicacao;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/** Anexo do e-mail. O conteúdo vira NULL quando o comunicado conclui; nome e tamanho ficam no histórico. */
@Entity
@Table(name = "comunicado_anexo")
public class ComunicadoAnexo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "comunicado_id", nullable = false)
    private UUID comunicadoId;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(nullable = false)
    private int tamanho;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "bytea")
    private byte[] conteudo;

    protected ComunicadoAnexo() {
    }

    public ComunicadoAnexo(UUID comunicadoId, String nome, String tipo, byte[] conteudo) {
        this.comunicadoId = comunicadoId;
        this.nome = nome;
        this.tipo = tipo;
        this.tamanho = conteudo.length;
        this.conteudo = conteudo;
    }

    public UUID getId() { return id; }
    public UUID getComunicadoId() { return comunicadoId; }
    public String getNome() { return nome; }
    public String getTipo() { return tipo; }
    public int getTamanho() { return tamanho; }
    public byte[] getConteudo() { return conteudo; }

    void apagarConteudo() {
        this.conteudo = null;
    }
}
