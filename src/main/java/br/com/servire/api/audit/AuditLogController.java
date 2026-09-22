package br.com.servire.api.audit;

import br.com.servire.api.audit.dto.AuditLogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Consulta da trilha de auditoria (Fase 11, seção 59 do plano mestre).
 *
 * <p>Restrito a {@code ADMIN} via {@code hasRole(...)} direto — não a uma
 * das permissões conceituais de {@link br.com.servire.api.security.Permissao}
 * (seção 31 não define uma permissão própria de auditoria; criar uma só
 * para este único endpoint seria over-engineering). Auditoria é, por
 * natureza, uma preocupação administrativa transversal (toca voluntários,
 * escalas E inscrições ao mesmo tempo), então amarrar à role em vez de a
 * uma permissão de um módulo específico é a escolha mais direta.</p>
 */
@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<AuditLogResponse> listar(@RequestParam(required = false) String entidade,
                                          @RequestParam(required = false) UUID entidadeId) {
        return auditLogService.listar(entidade, entidadeId).stream().map(AuditLogResponse::de).toList();
    }
}
