package br.com.servire.api.integracao;

import br.com.servirea.comum.seguranca.HmacAssinatura;

import br.com.servire.api.escala.LayoutsDeFabrica;
import br.com.servire.api.acesso.Perfil;
import br.com.servire.api.acesso.PerfilService;
import br.com.servire.api.auth.EmailSender;
import br.com.servire.api.auth.PasswordResetTokenService;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.integracao.dto.DireitosInstancia;
import br.com.servire.api.integracao.dto.ProvisionarInstanciaRequest;
import br.com.servire.api.security.OpaqueTokenGenerator;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class IntegracaoInstanciaService {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IntegracaoOperacaoRepository operacoes;
    private final DireitosLocaisRepository direitos;
    private final SuporteCodigoRepository suporteCodigos;
    private final TenantRepository tenants;
    private final UsuarioRepository usuarios;
    private final UsuarioTenantRepository vinculos;
    private final VoluntarioRepository voluntarios;
    private final PerfilService perfilService;
    private final PasswordResetTokenService tokens;
    private final EmailSender emailSender;
    private final JsonMapper json;
    private final TransactionTemplate transacao;
    private final AcessoParoquia acessoParoquia;
    private final String frontendBaseUrl;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public IntegracaoInstanciaService(IntegracaoOperacaoRepository operacoes,
                                       DireitosLocaisRepository direitos,
                                       SuporteCodigoRepository suporteCodigos,
                                       TenantRepository tenants,
                                       UsuarioRepository usuarios,
                                       UsuarioTenantRepository vinculos,
                                       VoluntarioRepository voluntarios,
                                       PerfilService perfilService,
                                       PasswordResetTokenService tokens,
                                       EmailSender emailSender,
                                       JsonMapper json,
                                       PlatformTransactionManager transactionManager,
                                       AcessoParoquia acessoParoquia,
                                       @Value("${servire.frontend.base-url}") String frontendBaseUrl,
                                       org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.operacoes = operacoes;
        this.direitos = direitos;
        this.suporteCodigos = suporteCodigos;
        this.tenants = tenants;
        this.usuarios = usuarios;
        this.vinculos = vinculos;
        this.voluntarios = voluntarios;
        this.perfilService = perfilService;
        this.tokens = tokens;
        this.emailSender = emailSender;
        this.json = json;
        this.transacao = new TransactionTemplate(transactionManager);
        this.acessoParoquia = acessoParoquia;
        this.frontendBaseUrl = frontendBaseUrl;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ResultadoProvisionamento provisionar(String idempotencyKey, byte[] corpo, ProvisionarInstanciaRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IntegracaoException(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Idempotency-Key é obrigatória.");
        }
        if (!idempotencyKey.equals(request.contratacaoId().toString())) {
            throw new IntegracaoException(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS",
                    "Idempotency-Key precisa ser o contratacaoId.");
        }
        String hash = HmacAssinatura.sha256Hex(corpo);
        IntegracaoOperacao existente = operacoes.findById(idempotencyKey).orElse(null);
        if (existente != null) {
            return repetir(existente, hash);
        }
        String slug = request.instancia().slug().trim().toLowerCase();
        if (tenants.existsBySlug(slug)) {
            throw new IntegracaoException(HttpStatus.UNPROCESSABLE_ENTITY, "SLUG_EM_USO",
                    "Já existe uma paróquia com este slug.");
        }
        try {
            return transacao.execute(status -> criar(idempotencyKey, hash, slug, request));
        } catch (DataIntegrityViolationException e) {
            IntegracaoOperacao gravada = operacoes.findById(idempotencyKey).orElse(null);
            if (gravada != null) {
                return repetir(gravada, hash);
            }
            throw new IntegracaoException(HttpStatus.UNPROCESSABLE_ENTITY, "SLUG_EM_USO",
                    "Já existe uma paróquia com este slug.");
        }
    }

    public Map<String, Object> consultar(UUID tenantId) {
        DireitosLocais locais = direitos.findById(tenantId)
                .orElseThrow(() -> new IntegracaoException(HttpStatus.NOT_FOUND, "INSTANCIA_NAO_ENCONTRADA",
                        "Instância não encontrada."));
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IntegracaoException(HttpStatus.NOT_FOUND, "INSTANCIA_NAO_ENCONTRADA",
                        "Instância não encontrada."));
        boolean efetivo = acessoParoquia.liberada(locais);
        return Map.of(
                "tenantId", tenant.getId(),
                "contratacaoId", locais.getContratacaoId(),
                "nome", tenant.getNome(),
                "slug", tenant.getSlug(),
                "versaoDireitos", locais.getVersao(),
                "confirmadoEm", locais.getConfirmadoEm(),
                "acessoEfetivo", efetivo,
                "uso", Map.of(
                        "voluntarios", contarVoluntarios(tenantId),
                        "usuarios", vinculos.countByTenant_IdAndStatus(tenantId, UsuarioTenant.Status.ATIVO),
                        "armazenamento_mb", 0));
    }

    @Transactional
    public Map<String, Object> aplicarDireitos(UUID tenantId, DireitosInstancia snapshot) {
        tenants.bloquearParaCotas(tenantId).orElseThrow(() -> new IntegracaoException(HttpStatus.NOT_FOUND,
                "INSTANCIA_NAO_ENCONTRADA", "Instância não encontrada."));
        DireitosLocais locais = direitos.findById(tenantId)
                .orElseThrow(() -> new IntegracaoException(HttpStatus.NOT_FOUND, "INSTANCIA_NAO_ENCONTRADA",
                        "Instância não encontrada."));
        if (snapshot.contratacaoId() != null && !snapshot.contratacaoId().equals(locais.getContratacaoId())) {
            throw new IntegracaoException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLITO",
                    "contratacaoId diferente do gravado nesta instância.");
        }
        boolean atualizaStatus = snapshot.versao() >= locais.getVersao();
        DireitosAplicacao.Resultado resultado = DireitosAplicacao.aplicar(locais, snapshot, json);
        if (atualizaStatus) {
            tenants.findById(tenantId).ifPresent(tenant -> tenant.setStatus(DireitosAplicacao.statusDe(snapshot)));
        }
        return Map.of("aplicado", resultado.aplicado(), "versaoAtual", resultado.versaoAtual());
    }

    @Transactional
    public Map<String, Object> emitirCodigoSuporte(UUID tenantId, String operadorNome, String operadorEmail, String motivo) {
        if (!tenants.existsById(tenantId) || direitos.findById(tenantId).isEmpty()) {
            throw new IntegracaoException(HttpStatus.NOT_FOUND, "INSTANCIA_NAO_ENCONTRADA", "Instância não encontrada.");
        }
        if (operadorNome == null || operadorNome.isBlank() || operadorEmail == null || operadorEmail.isBlank()
                || motivo == null || motivo.isBlank()) {
            throw new BadRequestException("Informe operador e motivo.");
        }
        String codigo = codigoSuporte();
        Instant expira = Instant.now().plus(2, ChronoUnit.MINUTES);
        suporteCodigos.save(new SuporteCodigo(
                tenantId, OpaqueTokenGenerator.hash(codigo), operadorNome.trim(), operadorEmail.trim(), motivo.trim(), expira));
        return Map.of(
                "codigo", codigo,
                "urlAcesso", frontendBaseUrl + "/suporte?codigo=" + codigo,
                "expiraEm", expira);
    }

    private ResultadoProvisionamento criar(String chave, String hash, String slug, ProvisionarInstanciaRequest request) {
        String codigo = codigoLivre();
        Tenant tenant = new Tenant(codigo, slug, request.instancia().nome().trim(),
                DireitosAplicacao.statusDe(request.direitos()));
        tenant.setVigenciaAte(request.direitos().vigenteAte());
        tenant = tenants.saveAndFlush(tenant);

        final UUID tenantId = tenant.getId();
        TenantContext.set(tenantId);
        transacao.execute(status -> {
            jdbcTemplate.update("INSERT INTO public.layout_escala (tenant_id, nome, descricao, tipo, colunas, sistema, ativo, padrao) VALUES (?, ?, ?, 'SEMANAL', ?::jsonb, true, true, true)",
                    tenantId, LayoutsDeFabrica.NOME_SEMANAL, LayoutsDeFabrica.DESCRICAO_SEMANAL, LayoutsDeFabrica.COLUNAS_SEMANAL);
            jdbcTemplate.update("INSERT INTO public.layout_escala (tenant_id, nome, descricao, tipo, colunas, sistema, ativo, padrao) VALUES (?, ?, ?, 'MENSAL', ?::jsonb, true, true, true)",
                    tenantId, LayoutsDeFabrica.NOME_MENSAL, LayoutsDeFabrica.DESCRICAO_MENSAL, LayoutsDeFabrica.COLUNAS_MENSAL);
            return null;
        });
        TenantContext.clear();

        Perfil administrador = perfilService.criarPadrao(tenant.getId());
        String email = request.administrador().email().trim().toLowerCase();
        Usuario usuario = usuarios.findByEmail(email).orElse(null);
        String situacao;
        if (usuario == null) {
            usuario = new Usuario(email, request.administrador().nome().trim());
            usuario.setSenhaHash(null);
            usuario = usuarios.saveAndFlush(usuario);
            situacao = "CONVIDADO";
            String token = tokens.gerarConvite(usuario);
            String link = frontendBaseUrl + "/reset-password?token=" + token;
            String destino = email;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailSender.enviarConvite(destino, link);
                }
            });
        } else {
            situacao = "VINCULADO";
            String destino = email;
            String nome = tenant.getNome();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailSender.enviarAvisoAcesso(destino, nome);
                }
            });
        }
        UsuarioTenant vinculo = new UsuarioTenant(
                usuario, tenant, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO);
        vinculo.setPerfil(administrador);
        vinculos.saveAndFlush(vinculo);

        DireitosLocais locais = new DireitosLocais(tenant.getId(), request.contratacaoId());
        DireitosAplicacao.aplicar(locais, request.direitos(), json);
        direitos.save(locais);

        String resposta = json.writeValueAsString(Map.of(
                "contratacaoId", request.contratacaoId(),
                "tenantId", tenant.getId(),
                "administrador", Map.of("usuarioId", usuario.getId(), "situacao", situacao),
                "versaoDireitosAplicada", locais.getVersao(),
                "criadoEm", Instant.now()));
        operacoes.saveAndFlush(new IntegracaoOperacao(chave, "PROVISIONAR", hash, 201, resposta));
        return new ResultadoProvisionamento(201, false, resposta);
    }

    private ResultadoProvisionamento repetir(IntegracaoOperacao operacao, String hash) {
        if (!operacao.getHashCorpo().equals(hash)) {
            throw new IntegracaoException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLITO",
                    "Idempotency-Key já foi usada com outro corpo.");
        }
        return new ResultadoProvisionamento(200, true, operacao.getResposta());
    }

    private long contarVoluntarios(UUID tenantId) {
        TenantContext.set(tenantId);
        try {
            Long total = transacao.execute(status -> voluntarios.count());
            return total == null ? 0 : total;
        } finally {
            TenantContext.clear();
        }
    }

    private String codigoLivre() {
        for (int i = 0; i < 5; i++) {
            String codigo = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            if (!tenants.existsByCodigo(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de paróquia.");
    }

    private static String codigoSuporte() {
        StringBuilder sb = new StringBuilder();
        for (int grupo = 0; grupo < 3; grupo++) {
            if (grupo > 0) {
                sb.append('-');
            }
            for (int i = 0; i < 4; i++) {
                sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
            }
        }
        return sb.toString();
    }

    public record ResultadoProvisionamento(int status, boolean replay, String json) {
    }
}
