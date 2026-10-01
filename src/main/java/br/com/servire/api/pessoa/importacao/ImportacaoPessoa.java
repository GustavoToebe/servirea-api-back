package br.com.servire.api.pessoa.importacao;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="importacao_pessoa")
public class ImportacaoPessoa {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @TenantId @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(nullable=false) private UUID chave;
    @Column(name="hash_arquivo",nullable=false,length=64) private String hashArquivo;
    @Column(nullable=false) private int quantidade;
    @Column(nullable=false) private LocalDate competencia;
    @Column(name="criado_em",nullable=false) private Instant criadoEm=Instant.now();
    protected ImportacaoPessoa() { }
    ImportacaoPessoa(UUID chave,String hash,int quantidade,LocalDate competencia) {
        this.chave=chave;this.hashArquivo=hash;this.quantidade=quantidade;this.competencia=competencia;
    }
    public UUID getId() {return id;}
    public String getHashArquivo() {return hashArquivo;}
    public int getQuantidade() {return quantidade;}
}
