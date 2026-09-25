package br.com.servire.api.integracao;

import br.com.servire.api.integracao.dto.DireitosInstancia;
import br.com.servire.api.tenant.Tenant;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/** Aplica um snapshot de direitos. Versão menor é ignorada; versão igual só confirma. */
final class DireitosAplicacao {

    private DireitosAplicacao() {
    }

    static Resultado aplicar(DireitosLocais locais, DireitosInstancia snapshot, JsonMapper json) {
        if (snapshot.versao() < locais.getVersao()) {
            return new Resultado(false, locais.getVersao());
        }
        int antes = locais.getVersao();
        if (snapshot.versao() > antes) {
            locais.setVersao(snapshot.versao());
            locais.setSituacao(snapshot.situacao());
            locais.setAcessoLiberado(snapshot.acessoLiberado());
            locais.setMotivoBloqueio(snapshot.motivoBloqueio());
            locais.setVigenteAte(snapshot.vigenteAte());
            if (snapshot.plano() != null) {
                locais.setPlanoCodigo(snapshot.plano().codigo());
                locais.setPlanoNome(snapshot.plano().nome());
            }
            locais.setLimites(snapshot.limites() == null ? null : json.writeValueAsString(snapshot.limites()));
            locais.setFuncionalidades(snapshot.funcionalidades() == null
                    ? new String[0]
                    : snapshot.funcionalidades().toArray(String[]::new));
        }
        locais.setConfirmadoEm(Instant.now());
        return new Resultado(snapshot.versao() > antes, locais.getVersao());
    }

    static Tenant.Status statusDe(DireitosInstancia snapshot) {
        if (snapshot == null || snapshot.situacao() == null) {
            return Tenant.Status.ATIVO;
        }
        return switch (snapshot.situacao()) {
            case "TRIAL" -> Tenant.Status.TRIAL;
            case "CANCELADA" -> Tenant.Status.CANCELADO;
            case "BLOQUEADA" -> Tenant.Status.BLOQUEADO;
            default -> snapshot.acessoLiberado() ? Tenant.Status.ATIVO : Tenant.Status.BLOQUEADO;
        };
    }

    record Resultado(boolean aplicado, int versaoAtual) {
    }
}
