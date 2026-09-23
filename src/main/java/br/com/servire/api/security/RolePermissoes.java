package br.com.servire.api.security;

import br.com.servire.api.auth.UsuarioTenant;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Mapeamento {@code role -> permissions} (seção 31 do plano mestre) —
 * {@link Permissao#values()} nunca é checada direto contra uma
 * {@link UsuarioTenant.Role} num controller; sempre passa por aqui.
 *
 * <p>Critério adotado para as três roles iniciais (seção 31 não detalha o
 * mapeamento exato, só a lista de permissões — este é o critério de bom
 * senso adotado, documentado para poder ser revisto):</p>
 * <ul>
 *   <li>{@code ADMIN} — todas as permissões de negócio da paróquia,
 *   incluindo {@code CONFIG_WRITE}. NÃO inclui {@code BACKOFFICE}
 *   (seção 111: o padre não acessa o painel do dono da plataforma).</li>
 *   <li>{@code COORDENADOR} — todas as permissões de leitura/escrita de
 *   voluntários, escalas e inscrições (o dia a dia operacional da
 *   paróquia), MENOS {@code CONFIG_WRITE} (reservada a quem administra o
 *   tenant).</li>
 *   <li>{@code VISUALIZADOR} — só as permissões {@code *_READ} (consulta,
 *   nunca grava nada) — nem {@code INSCRICAO_APPROVE}, que é uma ação de
 *   escrita (aprova/rejeita), mesmo não estando nomeada "WRITE".</li>
 * </ul>
 */
public final class RolePermissoes {

    private static final Map<UsuarioTenant.Role, Set<Permissao>> MAPA = new EnumMap<>(UsuarioTenant.Role.class);

    static {
        // ADMIN da paróquia tem todas as permissões de negócio, MENOS
        // BACKOFFICE (seção 111: operador do SaaS não mistura com o admin
        // da paróquia). EnumSet.allOf puxaria BACKOFFICE e o padre
        // acessaria /admin/** — por isso a lista é explícita.
        MAPA.put(UsuarioTenant.Role.ADMIN, EnumSet.of(
                Permissao.VOLUNTARIO_READ, Permissao.VOLUNTARIO_WRITE,
                Permissao.ESCALA_READ, Permissao.ESCALA_WRITE,
                Permissao.INSCRICAO_READ, Permissao.INSCRICAO_APPROVE,
                Permissao.CONFIG_WRITE));
        MAPA.put(UsuarioTenant.Role.COORDENADOR, EnumSet.of(
                Permissao.VOLUNTARIO_READ, Permissao.VOLUNTARIO_WRITE,
                Permissao.ESCALA_READ, Permissao.ESCALA_WRITE,
                Permissao.INSCRICAO_READ, Permissao.INSCRICAO_APPROVE));
        MAPA.put(UsuarioTenant.Role.VISUALIZADOR, EnumSet.of(
                Permissao.VOLUNTARIO_READ, Permissao.ESCALA_READ, Permissao.INSCRICAO_READ));
    }

    private RolePermissoes() {
    }

    /** @return as permissões concedidas à role informada — nunca {@code null} (conjunto vazio na pior hipótese). */
    public static Set<Permissao> de(UsuarioTenant.Role role) {
        return MAPA.getOrDefault(role, Set.of());
    }
}
