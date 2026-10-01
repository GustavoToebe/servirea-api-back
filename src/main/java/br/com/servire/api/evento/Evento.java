package br.com.servire.api.evento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Evento da paróquia. {@code inicio}/{@code termino} são o horário de Brasília, como a pessoa digitou. */
@Entity
@Table(name = "evento")
public class Evento {

    public enum Situacao { RASCUNHO, PUBLICADO, CANCELADO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 150)
    private String titulo;
    private String descricao;
    @Column(nullable = false)
    private LocalDateTime inicio;
    private LocalDateTime termino;
    @Column(name = "local_nome", length = 150)
    private String localNome;
    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String uf;
    @Column(name = "mapa_url", length = 500)
    private String mapaUrl;
    private Integer vagas;
    @Column(name = "responsavel_nome", length = 150)
    private String responsavelNome;
    @Column(name = "responsavel_telefone", length = 20)
    private String responsavelTelefone;
    @Column(name = "lembrete_dias", nullable = false)
    private int lembreteDias = 1;
    @Column(name = "mensagem_confirmacao", nullable = false)
    private String mensagemConfirmacao;
    @Column(name = "mensagem_lembrete", nullable = false)
    private String mensagemLembrete;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Situacao situacao = Situacao.RASCUNHO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Evento() {
    }

    public Evento(String titulo, LocalDateTime inicio) {
        this.titulo = titulo;
        this.inicio = inicio;
        this.mensagemConfirmacao = MensagensDeEvento.CONFIRMACAO_PADRAO;
        this.mensagemLembrete = MensagensDeEvento.LEMBRETE_PADRAO;
    }

    @PreUpdate
    void atualizar() {
        updatedAt = Instant.now();
    }

    /** Já começou (ou terminou) no horário de Brasília: não aceita inscrição nem lembrete. */
    public boolean jaAconteceu(LocalDateTime agora) {
        return !(termino != null ? termino : inicio).isAfter(agora);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public LocalDateTime getInicio() { return inicio; }
    public void setInicio(LocalDateTime inicio) { this.inicio = inicio; }
    public LocalDateTime getTermino() { return termino; }
    public void setTermino(LocalDateTime termino) { this.termino = termino; }
    public String getLocalNome() { return localNome; }
    public void setLocalNome(String localNome) { this.localNome = localNome; }
    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }
    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }
    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
    public String getMapaUrl() { return mapaUrl; }
    public void setMapaUrl(String mapaUrl) { this.mapaUrl = mapaUrl; }
    public Integer getVagas() { return vagas; }
    public void setVagas(Integer vagas) { this.vagas = vagas; }
    public String getResponsavelNome() { return responsavelNome; }
    public void setResponsavelNome(String responsavelNome) { this.responsavelNome = responsavelNome; }
    public String getResponsavelTelefone() { return responsavelTelefone; }
    public void setResponsavelTelefone(String responsavelTelefone) { this.responsavelTelefone = responsavelTelefone; }
    public int getLembreteDias() { return lembreteDias; }
    public void setLembreteDias(int lembreteDias) { this.lembreteDias = lembreteDias; }
    public String getMensagemConfirmacao() { return mensagemConfirmacao; }
    public void setMensagemConfirmacao(String mensagemConfirmacao) { this.mensagemConfirmacao = mensagemConfirmacao; }
    public String getMensagemLembrete() { return mensagemLembrete; }
    public void setMensagemLembrete(String mensagemLembrete) { this.mensagemLembrete = mensagemLembrete; }
    public Situacao getSituacao() { return situacao; }
    public void setSituacao(Situacao situacao) { this.situacao = situacao; }
    public Instant getCreatedAt() { return createdAt; }
}
