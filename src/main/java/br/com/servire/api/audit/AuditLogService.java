package br.com.servire.api.audit;

import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Trilha de auditoria (Fase 11 do plano mestre, seção 59) — chamada
 * explicitamente pelos serviços de negócio ({@code VoluntarioService},
 * {@code EscalaService}, {@code InscricaoService}) depois de uma mudança
 * relevante, nunca via AOP/interceptador genérico (mais simples de ler e
 * de garantir que o texto de {@code acao}/{@code entidade} faz sentido de
 * negócio para cada caso, em vez de inferir de reflection).
 *
 * <p><b>Por que ler tenant/usuário/IP/requestId aqui dentro, em vez de
 * receber tudo por parâmetro</b> (diferente do padrão já usado em
 * {@code TurnstileService.validar(token, ipRemetente)}, que recebe o IP
 * explicitamente do controller): teria exigido acrescentar parâmetros de
 * IP/requestId em toda a cadeia de chamadas de
 * {@code VoluntarioService}/{@code EscalaService}/{@code InscricaoService}
 * (métodos hoje sem nenhum acesso a {@code HttpServletRequest}), uma
 * mudança bem mais invasiva para um dado "nice to have" de auditoria.
 * Em vez disso, {@link #registrar} lê:</p>
 * <ul>
 *   <li>{@link TenantContext#get()} — já garantido setado por
 *   {@code JwtAuthenticationFilter}/{@code InscricaoService.criarPublica}
 *   em toda requisição que chega a um serviço de negócio (mesma garantia
 *   de que o {@code @TenantId} da entidade vai resolver certo).</li>
 *   <li>{@code SecurityContextHolder} — o {@link AuthenticatedUser}
 *   atual, quando existe (rotas públicas não têm um).</li>
 *   <li>{@code MDC.get(RequestIdFilter.MDC_KEY)} — o mesmo requestId já
 *   usado nos logs estruturados e no header {@code X-Request-Id}
 *   (nenhuma leitura nova, só reaproveita o que {@code RequestIdFilter}
 *   já coloca no MDC).</li>
 *   <li>{@code RequestContextHolder} — o {@code HttpServletRequest} da
 *   requisição atual, só para {@code getRemoteAddr()}. Mesma simplificação
 *   já aceita em {@code AuthController.ip(request)} (comentário lá:
 *   "não considera reverse proxy/X-Forwarded-For ainda") — não vale a pena
 *   resolver isso aqui antes de ser resolvido lá.</li>
 * </ul>
 * <p>Todos os quatro têm fallback silencioso para {@code null}/log de aviso
 * se ausentes — auditoria nunca deve derrubar a operação de negócio por
 * falta de metadado incidental.</p>
 *
 * <p><b>Atomicidade:</b> {@link #registrar} roda na MESMA transação do
 * chamador (propagação padrão {@code REQUIRED}) — de propósito: garante
 * que a mudança de negócio e o registro de auditoria são atômicos (nunca
 * existe um sem o outro). O único jeito de {@code save} falhar aqui seria
 * um bug de programação (todos os valores gravados vêm do próprio contexto
 * interno confiável, nunca de entrada do usuário sem validação prévia), não
 * uma condição esperada de operação — então deixar essa falha (rara)
 * desfazer a transação inteira é aceitável, em vez de mascará-la com
 * {@code REQUIRES_NEW} + try/catch silencioso.</p>
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void registrar(String acao, String entidade, UUID entidadeId, List<String> changedFields) {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            // Não deveria ocorrer — toda ação de negócio que chega até aqui
            // já rodou com TenantContext setado (mesma garantia usada pelo
            // @TenantId das outras entidades). Preferir logar e seguir a
            // nunca travar a operação de negócio por causa da auditoria.
            log.warn("registrar() chamado sem TenantContext definido (acao={}, entidade={}, entidadeId={}) — auditoria ignorada.",
                    acao, entidade, entidadeId);
            return;
        }
        String[] campos = (changedFields == null || changedFields.isEmpty()) ? null : changedFields.toArray(new String[0]);
        AuditLog registro = new AuditLog(usuarioAtualId(), acao, entidade, entidadeId, campos, ipAtual(), requestIdAtual());
        auditLogRepository.save(registro);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> listar(String entidade, UUID entidadeId) {
        if ((entidade == null) != (entidadeId == null)) {
            throw new BadRequestException("Informe entidade e entidadeId juntos, ou nenhum dos dois.");
        }
        if (entidade != null) {
            return auditLogRepository.findByEntidadeAndEntidadeIdOrderByCreatedAtDesc(entidade, entidadeId);
        }
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc();
    }

    private UUID usuarioAtualId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser usuario) {
            return usuario.usuarioId();
        }
        return null;
    }

    private String requestIdAtual() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }

    private String ipAtual() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                return attributes.getRequest().getRemoteAddr();
            }
        } catch (IllegalStateException e) {
            // Sem requisição HTTP ativa na thread atual (ex.: chamado a
            // partir de um teste/job) — ip fica null, nunca quebra a
            // gravação de auditoria por causa disso.
        }
        return null;
    }
}
