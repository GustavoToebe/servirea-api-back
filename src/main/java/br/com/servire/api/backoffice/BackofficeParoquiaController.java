package br.com.servire.api.backoffice;

import br.com.servire.api.backoffice.dto.AtualizarParoquiaRequest;
import br.com.servire.api.backoffice.dto.CriarParoquiaRequest;
import br.com.servire.api.backoffice.dto.DashboardResponse;
import br.com.servire.api.backoffice.dto.FiltroParoquia;
import br.com.servire.api.backoffice.dto.ParoquiaAdminResponse;
import br.com.servire.api.backoffice.dto.SuporteTokenResponse;
import br.com.servire.api.billing.BillingService;
import br.com.servire.api.billing.dto.SituacaoFinanceira;
import br.com.servire.api.security.SecurityProperties;
import br.com.servire.api.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API do painel (seção 111). Toda rota exige {@code PERM_BACKOFFICE}
 * (JWT {@code purpose=backoffice}). "Entrar em suporte" devolve um
 * access token da paróquia e grava o refresh no cookie {@code /auth}
 * para o app da paróquia renovar.
 *
 * <p>O antigo {@code POST /paroquias/{id}/marcar-pago} saiu (V029): o
 * pagamento agora é registrado por cobrança em
 * {@code FinanceiroParoquiaController}.</p>
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('PERM_BACKOFFICE')")
public class BackofficeParoquiaController {

    private static final String PARISH_REFRESH_COOKIE = "servire_refresh_token";

    private final BackofficeParoquiaService backofficeParoquiaService;
    private final SecurityProperties properties;
    private final BillingService billingService;

    public BackofficeParoquiaController(BackofficeParoquiaService backofficeParoquiaService,
                                         SecurityProperties properties,
                                         BillingService billingService) {
        this.backofficeParoquiaService = backofficeParoquiaService;
        this.properties = properties;
        this.billingService = billingService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return backofficeParoquiaService.dashboard();
    }

    @GetMapping("/paroquias")
    public List<ParoquiaAdminResponse> listar(
            @RequestParam(required = false) Tenant.Status status,
            @RequestParam(required = false) SituacaoParoquia situacao,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String tipoEmail,
            @RequestParam(required = false) LocalDate contratadoDe,
            @RequestParam(required = false) LocalDate contratadoAte,
            @RequestParam(required = false) LocalDate vigenciaDe,
            @RequestParam(required = false) LocalDate vigenciaAte,
            @RequestParam(required = false) Boolean emAtraso) {
        List<Tenant> tenants = backofficeParoquiaService.listar(new FiltroParoquia(
                status, situacao, nome, cnpj, email, tipoEmail,
                contratadoDe, contratadoAte, vigenciaDe, vigenciaAte, emAtraso));
        Map<UUID, SituacaoFinanceira> financeiro = billingService.situacaoPorTenant(
                tenants.stream().map(Tenant::getId).toList());
        return tenants.stream().map(t -> ParoquiaAdminResponse.de(t, financeiro.get(t.getId()))).toList();
    }

    @PostMapping("/paroquias")
    public ParoquiaAdminResponse criar(@RequestBody @Valid CriarParoquiaRequest request) {
        return resposta(backofficeParoquiaService.criar(request));
    }

    @GetMapping("/paroquias/{id}")
    public ParoquiaAdminResponse buscar(@PathVariable UUID id) {
        return resposta(backofficeParoquiaService.buscar(id));
    }

    @PutMapping("/paroquias/{id}")
    public ParoquiaAdminResponse atualizar(@PathVariable UUID id,
                                            @RequestBody @Valid AtualizarParoquiaRequest request) {
        return resposta(backofficeParoquiaService.atualizar(id, request));
    }

    @PostMapping("/paroquias/{id}/bloquear")
    public ParoquiaAdminResponse bloquear(@PathVariable UUID id) {
        return resposta(backofficeParoquiaService.bloquear(id));
    }

    @PostMapping("/paroquias/{id}/desbloquear")
    public ParoquiaAdminResponse desbloquear(@PathVariable UUID id) {
        return resposta(backofficeParoquiaService.desbloquear(id));
    }

    @PostMapping("/paroquias/{id}/suporte")
    public ResponseEntity<SuporteTokenResponse> entrarEmSuporte(@PathVariable UUID id,
                                                                 HttpServletRequest httpRequest,
                                                                 HttpServletResponse httpResponse) {
        BackofficeParoquiaService.SessaoSuporte sessao = backofficeParoquiaService.entrarEmSuporte(
                id, httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        ResponseCookie cookie = ResponseCookie.from(PARISH_REFRESH_COOKIE, sessao.refreshTokenBruto())
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/auth")
                .maxAge(properties.refreshTokenTtl())
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(new SuporteTokenResponse(
                sessao.accessToken(), sessao.expiresInSeconds(), sessao.tenantAtual(), true));
    }

    private ParoquiaAdminResponse resposta(Tenant tenant) {
        return ParoquiaAdminResponse.de(tenant, billingService.situacaoPorTenant(List.of(tenant.getId())).get(tenant.getId()));
    }
}
