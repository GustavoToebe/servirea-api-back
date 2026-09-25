package br.com.servire.api.acesso;

import java.util.List;

/**
 * Catálogo seção → módulo → ações da tela de perfil (Central, seção 8.3).
 * O código do módulo é o acesso/visualizar. Ação solta não existe sem o módulo.
 */
public final class CatalogoPermissao {

    public record Acao(String codigo, String nome) {
    }

    public record Modulo(String codigo, String nome, List<Acao> acoes) {
    }

    public record Secao(String nome, List<Modulo> modulos) {
    }

    public static final List<Secao> SECOES = List.of(
            new Secao("Cadastro", List.of(
                    new Modulo("PERFIL", "Perfil do usuário", List.of(
                            new Acao("PERFIL_CRIAR", "Criar"),
                            new Acao("PERFIL_ALTERAR", "Alterar"))),
                    new Modulo("USUARIO", "Usuário", List.of(
                            new Acao("USUARIO_CRIAR", "Criar"),
                            new Acao("USUARIO_ALTERAR", "Alterar"),
                            new Acao("USUARIO_REENVIAR_CONVITE", "Reenviar convite"))),
                    new Modulo("PAROQUIA", "Paróquia", List.of(
                            new Acao("PAROQUIA_ALTERAR", "Alterar"))),
                    new Modulo("PESSOA", "Pessoas", List.of(
                            new Acao("PESSOA_CRIAR", "Criar"),
                            new Acao("PESSOA_ALTERAR", "Alterar"),
                            new Acao("PESSOA_EXCLUIR", "Excluir"),
                            new Acao("PESSOA_ATIVAR_INATIVAR", "Ativar/Inativar"))))),
            new Secao("Escalas", List.of(
                    new Modulo("ESCALA", "Escala", List.of(
                            new Acao("ESCALA_CRIAR", "Criar"),
                            new Acao("ESCALA_ALTERAR", "Alterar"),
                            new Acao("ESCALA_EXCLUIR", "Excluir"),
                            new Acao("ESCALA_FINALIZAR_REABRIR", "Finalizar/Reabrir"),
                            new Acao("ESCALA_CANCELAR", "Cancelar"))),
                    new Modulo("VAGA", "Vagas", List.of(
                            new Acao("VAGA_ALOCAR", "Alocar voluntário"),
                            new Acao("VAGA_PRESENCA", "Registrar presença"))))),
            new Secao("Inscrições", List.of(
                    new Modulo("INSCRICAO", "Inscrição", List.of(
                            new Acao("INSCRICAO_ALTERAR", "Alterar"),
                            new Acao("INSCRICAO_APROVAR", "Aprovar"),
                            new Acao("INSCRICAO_REJEITAR", "Rejeitar"))))),
            new Secao("Relatórios", List.of(
                    new Modulo("AUDITORIA", "Auditoria", List.of())))
    );

    private CatalogoPermissao() {
    }
}
