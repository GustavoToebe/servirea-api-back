package br.com.servire.api.comunicacao;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.comunicacao.dto.WhatsappConfigRequest;
import br.com.servire.api.comunicacao.dto.WhatsappConfigResponse;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Configuração do WhatsApp da paróquia atual (PLANO-005). */
@Service
public class ParoquiaWhatsappService {

    static final String TEXTO_TESTE = "Teste do Servirea: o WhatsApp da paróquia está conectado. ✅";

    private final ParoquiaWhatsappRepository repository;
    private final WhatsappSender whatsappSender;
    private final AuditLogService auditLogService;

    public ParoquiaWhatsappService(ParoquiaWhatsappRepository repository, WhatsappSender whatsappSender,
                                   AuditLogService auditLogService) {
        this.repository = repository;
        this.whatsappSender = whatsappSender;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public WhatsappConfigResponse buscar() {
        return atual().map(c -> new WhatsappConfigResponse(c.getInstancia(), c.isAtivo(), true))
                .orElse(new WhatsappConfigResponse("", false, false));
    }

    /** Configuração ativa da paróquia atual, para o envio. */
    @Transactional(readOnly = true)
    public Optional<ParoquiaWhatsapp> ativa() {
        return atual().filter(ParoquiaWhatsapp::isAtivo);
    }

    @Transactional
    public WhatsappConfigResponse salvar(WhatsappConfigRequest req) {
        UUID tenantId = TenantContext.get();
        String instancia = req.instancia().trim();
        Optional<ParoquiaWhatsapp> existente = repository.findById(tenantId);
        List<String> campos = new ArrayList<>();
        if (existente.isEmpty()) {
            if (req.token() == null || req.token().isBlank()) {
                throw new BadRequestException("Informe o token da instância.");
            }
            repository.save(new ParoquiaWhatsapp(tenantId, instancia, req.token().trim(), req.ativo()));
            campos.addAll(List.of("instancia", "token", "ativo"));
        } else {
            ParoquiaWhatsapp c = existente.get();
            if (!c.getInstancia().equals(instancia)) campos.add("instancia");
            if (req.token() != null && !req.token().isBlank()) campos.add("token");
            if (c.isAtivo() != req.ativo()) campos.add("ativo");
            c.atualizar(instancia, req.token() == null ? null : req.token().trim(), req.ativo());
        }
        auditLogService.registrar("ALTERAR", "paroquia_whatsapp", tenantId, campos);
        return buscar();
    }

    /** Envia a mensagem de teste na hora, mesmo com a configuração inativa. */
    @Transactional(readOnly = true)
    public void testar(String telefone) {
        ParoquiaWhatsapp c = atual().orElseThrow(() -> new BadRequestException("Configure o WhatsApp da paróquia antes de testar."));
        whatsappSender.enviarTexto(c.getInstancia(), c.getToken(), telefone, TEXTO_TESTE);
    }

    private Optional<ParoquiaWhatsapp> atual() {
        UUID tenantId = TenantContext.get();
        return tenantId == null ? Optional.empty() : repository.findById(tenantId);
    }
}
