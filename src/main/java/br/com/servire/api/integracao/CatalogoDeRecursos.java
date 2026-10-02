package br.com.servire.api.integracao;

import java.util.List;

/**
 * Limites e funcionalidades que o Servirea entende nos direitos vindos da
 * Central (contrato v1, seção 5.5). A Central usa esta lista como sugestão
 * ao cadastrar recursos, para o código nunca divergir do que o app lê em
 * {@code limites}/{@code funcionalidades} (26/09/2026).
 *
     * <p>{@code aplicado} diz se o app já faz valer o recurso; só marcar quando
     * todos os fluxos de crescimento correspondentes estiverem protegidos.
 */
public final class CatalogoDeRecursos {

    public enum Tipo {
        LIMITE,
        FUNCIONALIDADE
    }

    public record RecursoDoApp(String codigo, String nome, Tipo tipo, String unidade, boolean aplicado) {
    }

    public static final List<RecursoDoApp> RECURSOS = List.of(
            new RecursoDoApp("pessoas", "Pessoas cadastradas", Tipo.LIMITE, "pessoa", true),
            new RecursoDoApp("voluntarios", "Voluntários cadastrados", Tipo.LIMITE, "pessoa", true),
            new RecursoDoApp("usuarios", "Usuários com acesso ativo ou convite", Tipo.LIMITE, "usuário", true),
            new RecursoDoApp("armazenamento_mb", "Armazenamento de arquivos vinculados", Tipo.LIMITE, "MB", true),
            new RecursoDoApp("emails_mes", "E-mails da fila por mês", Tipo.LIMITE, "mensagem/mês", true),
            new RecursoDoApp("whatsapp_mes", "WhatsApp da fila por mês", Tipo.LIMITE, "mensagem/mês", true),
            new RecursoDoApp("importacoes_mes", "Lotes CSV de pessoas por mês", Tipo.LIMITE, "lote/mês", true),
            new RecursoDoApp("PASTORAIS","Pastorais e equipes",Tipo.FUNCIONALIDADE,null,true),
            new RecursoDoApp("CALENDARIO","Calendário privado",Tipo.FUNCIONALIDADE,null,true),
            new RecursoDoApp("PORTAL_VOLUNTARIO","Portal de consulta do voluntário",Tipo.FUNCIONALIDADE,null,true),
            new RecursoDoApp("MURAL", "Mural de avisos", Tipo.FUNCIONALIDADE, null, true),
            new RecursoDoApp("TAREFAS", "Tarefas e solicitações", Tipo.FUNCIONALIDADE, null, true),
            new RecursoDoApp("EVENTOS", "Eventos", Tipo.FUNCIONALIDADE, null, true),
            new RecursoDoApp("FINANCEIRO", "Financeiro paroquial", Tipo.FUNCIONALIDADE, null, true),
            new RecursoDoApp("COMUNICACAO", "Comunicação e WhatsApp", Tipo.FUNCIONALIDADE, null, true),
            new RecursoDoApp("IMPORTACAO_PESSOAS", "Importação CSV de pessoas", Tipo.FUNCIONALIDADE, null, true),
            new RecursoDoApp("ESCALAS", "Escalas", Tipo.FUNCIONALIDADE, null, true),
            new RecursoDoApp("INSCRICAO_PUBLICA", "Inscrição pública", Tipo.FUNCIONALIDADE, null, true));

    private CatalogoDeRecursos() {
    }
}
