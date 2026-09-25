package br.com.servire.api.acesso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "perfil_permissao")
public class PerfilPermissao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_id")
    private Perfil perfil;

    @Column(nullable = false)
    private String permissao;

    protected PerfilPermissao() {
    }

    public PerfilPermissao(Perfil perfil, String permissao) {
        this.perfil = perfil;
        this.permissao = permissao;
    }

    public String getPermissao() {
        return permissao;
    }
}
