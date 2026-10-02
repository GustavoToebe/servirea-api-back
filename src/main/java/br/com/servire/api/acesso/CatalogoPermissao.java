package br.com.servire.api.acesso;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                            new Acao("PESSOA_CUIDADOS_LER", "Consultar cuidado e acolhimento"),
                            new Acao("PESSOA_CUIDADOS_ALTERAR", "Alterar cuidado e acolhimento"),
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
            new Secao("Comunicação", List.of(
                    new Modulo("LAYOUT", "Layouts", List.of(
                            new Acao("LAYOUT_CRIAR", "Criar"),
                            new Acao("LAYOUT_ALTERAR", "Alterar"),
                            new Acao("LAYOUT_EXCLUIR", "Excluir"))),
                    new Modulo("COMUNICADO", "Comunicados", List.of(
                            new Acao("COMUNICADO_ENVIAR", "Enviar"))))),
            new Secao("Eventos", List.of(
                    new Modulo("EVENTO", "Eventos", List.of(
                            new Acao("EVENTO_CRIAR", "Criar"),
                            new Acao("EVENTO_ALTERAR", "Alterar (dados e fotos)"),
                            new Acao("EVENTO_INSCREVER", "Inscrever e remover pessoas"),
                            new Acao("EVENTO_CANCELAR", "Cancelar"))))),
            new Secao("Financeiro", List.of(
                    new Modulo("FINANCEIRO", "Financeiro paroquial", List.of(
                            new Acao("FINANCEIRO_CRIAR", "Criar lançamento"),
                            new Acao("FINANCEIRO_ALTERAR", "Editar e cancelar lançamento"),
                            new Acao("FINANCEIRO_BAIXAR", "Baixar e estornar"),
                            new Acao("FINANCEIRO_CONFIGURAR", "Gerenciar contas e categorias"))))),
            new Secao("Organização", List.of(
                    new Modulo("MURAL", "Mural de avisos", List.of(new Acao("MURAL_CRIAR", "Criar aviso"),new Acao("MURAL_ALTERAR", "Editar e arquivar aviso"))),
                    new Modulo("TAREFA", "Tarefas e solicitações", List.of(new Acao("TAREFA_CRIAR", "Criar tarefa"),new Acao("TAREFA_ALTERAR", "Editar e atualizar status"))))),
            new Secao("Relatórios", List.of(
                    new Modulo("AUDITORIA", "Auditoria", List.of())))
    );

    private static final Set<String> CODIGOS;
    private static final Map<String, String> MODULO_DA_ACAO;

    static {
        Set<String> codigos = new LinkedHashSet<>();
        Map<String, String> moduloDaAcao = new LinkedHashMap<>();
        for (Secao secao : SECOES) {
            for (Modulo modulo : secao.modulos()) {
                codigos.add(modulo.codigo());
                for (Acao acao : modulo.acoes()) {
                    codigos.add(acao.codigo());
                    moduloDaAcao.put(acao.codigo(), modulo.codigo());
                }
            }
        }
        CODIGOS = Collections.unmodifiableSet(codigos);
        MODULO_DA_ACAO = Collections.unmodifiableMap(moduloDaAcao);
    }

    private CatalogoPermissao() {
    }

    /**
     * Todos os códigos do catálogo. É a lista que o acesso total libera e a
     * única aceita num perfil: o {@code @PreAuthorize} de cada endpoint usa
     * {@code PERM_<código>} (25/09/2026 — antes os endpoints ainda pediam
     * as permissões antigas e a matriz não valia para Pessoas/Escalas/
     * Inscrições).
     */
    public static Set<String> codigos() {
        return CODIGOS;
    }

    /** Módulo de uma ação ({@code ESCALA_CRIAR} → {@code ESCALA}); {@code null} se o código já é módulo. */
    public static String moduloDe(String codigo) {
        return MODULO_DA_ACAO.get(codigo);
    }
}
