package br.com.servire.api.backoffice;

import br.com.servire.api.backoffice.dto.BackofficeLogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/logs")
public class BackofficeLogController {

    private final BackofficeLogService backofficeLogService;

    public BackofficeLogController(BackofficeLogService backofficeLogService) {
        this.backofficeLogService = backofficeLogService;
    }

    @PreAuthorize("hasAuthority('PERM_BACKOFFICE')")
    @GetMapping
    public List<BackofficeLogResponse> listar() {
        return backofficeLogService.listar().stream().map(BackofficeLogResponse::de).toList();
    }
}
