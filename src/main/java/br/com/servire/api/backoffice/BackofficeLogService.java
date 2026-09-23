package br.com.servire.api.backoffice;

import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.web.RequestIdFilter;
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
 * Auditoria do painel (seção 111). Mesma transação do chamador
 * ({@code REQUIRED}), mesmo critério do {@code AuditLogService}: a ação
 * e o log são atômicos. Metadados (usuário/IP/requestId) lidos do
 * contexto — fallback silencioso para {@code null} em teste.
 */
@Service
public class BackofficeLogService {

    private final BackofficeLogRepository backofficeLogRepository;

    public BackofficeLogService(BackofficeLogRepository backofficeLogRepository) {
        this.backofficeLogRepository = backofficeLogRepository;
    }

    @Transactional
    public void registrar(String acao, String entidade, UUID entidadeId, UUID tenantAlvoId) {
        BackofficeLog registro = new BackofficeLog(
                usuarioAtualId(), tenantAlvoId, acao, entidade, entidadeId, ipAtual(), requestIdAtual());
        backofficeLogRepository.save(registro);
    }

    @Transactional(readOnly = true)
    public List<BackofficeLog> listar() {
        return backofficeLogRepository.findTop200ByOrderByCreatedAtDesc();
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
            // teste/job sem HTTP
        }
        return null;
    }
}
