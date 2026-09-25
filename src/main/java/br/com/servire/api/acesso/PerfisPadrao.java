package br.com.servire.api.acesso;

import java.util.List;
import java.util.UUID;

/**
 * Os três perfis de toda paróquia nova (arquitetura da Central, seção 8.3).
 */
public final class PerfisPadrao {

    public static final String ADMINISTRADOR = "Administrador";
    public static final String SECRETARIO = "Secretário";
    public static final String COORDENADOR = "Coordenador";

    private static final List<String> SECRETARIO_PERMISSOES = List.of(
            "PESSOA", "PESSOA_CRIAR", "PESSOA_ALTERAR", "PESSOA_EXCLUIR", "PESSOA_ATIVAR_INATIVAR",
            "PAROQUIA",
            "INSCRICAO", "INSCRICAO_ALTERAR", "INSCRICAO_APROVAR", "INSCRICAO_REJEITAR",
            "ESCALA", "VAGA");

    private static final List<String> COORDENADOR_PERMISSOES = List.of(
            "ESCALA", "ESCALA_CRIAR", "ESCALA_ALTERAR", "ESCALA_EXCLUIR",
            "ESCALA_FINALIZAR_REABRIR", "ESCALA_CANCELAR",
            "VAGA", "VAGA_ALOCAR", "VAGA_PRESENCA",
            "PESSOA", "INSCRICAO");

    private PerfisPadrao() {
    }

    public static Perfil administrador(UUID tenantId) {
        return new Perfil(tenantId, ADMINISTRADOR, true, true);
    }

    public static Perfil secretario(UUID tenantId) {
        Perfil perfil = new Perfil(tenantId, SECRETARIO, false, false);
        perfil.substituirPermissoes(SECRETARIO_PERMISSOES);
        return perfil;
    }

    public static Perfil coordenador(UUID tenantId) {
        Perfil perfil = new Perfil(tenantId, COORDENADOR, false, false);
        perfil.substituirPermissoes(COORDENADOR_PERMISSOES);
        return perfil;
    }
}
