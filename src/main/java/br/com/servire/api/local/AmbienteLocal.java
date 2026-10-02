package br.com.servire.api.local;

import br.com.servire.api.acesso.Perfil;
import br.com.servire.api.acesso.PerfilService;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.escala.LayoutsDeFabrica;
import br.com.servire.api.integracao.DireitosLocais;
import br.com.servire.api.integracao.DireitosLocaisRepository;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Paróquia de teste no profile {@code dev}, e só se o JDBC aponta para esta máquina.
 * O mesmo id de paróquia e de contratação está no seed da Central, para o
 * "Acessar aplicativo" abrir esta base. Não roda em produção.
 */
@Component
@Profile("dev")
public class AmbienteLocal implements ApplicationRunner {

    /** Igual ao seed da Central. Não mudar um sem o outro. */
    public static final UUID TENANT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final UUID CONTRATACAO_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    public static final String SLUG = "paroquia-teste";
    public static final String EMAIL = "paroquia@teste.local";
    public static final String SENHA = "12345678";

    private static final Logger log = LoggerFactory.getLogger(AmbienteLocal.class);

    private final TenantRepository tenants;
    private final UsuarioRepository usuarios;
    private final UsuarioTenantRepository vinculos;
    private final PerfilService perfis;
    private final DireitosLocaisRepository direitos;
    private final PessoaRepository pessoas;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final String url;

    public AmbienteLocal(TenantRepository tenants, UsuarioRepository usuarios, UsuarioTenantRepository vinculos,
                         PerfilService perfis, DireitosLocaisRepository direitos, PessoaRepository pessoas,
                         PasswordEncoder passwordEncoder, JdbcTemplate jdbc, PlatformTransactionManager transacoes,
                         @Value("${spring.datasource.url:}") String url) {
        this.tenants = tenants;
        this.usuarios = usuarios;
        this.vinculos = vinculos;
        this.perfis = perfis;
        this.direitos = direitos;
        this.pessoas = pessoas;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(transacoes);
        this.url = url;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bancoNestaMaquina(url) || tenants.existsBySlug(SLUG)) {
            return;
        }
        transacao.executeWithoutResult(status -> gravarParoquia());
        TenantContext.set(TENANT_ID);
        try {
            transacao.executeWithoutResult(status -> gravarPessoas());
        } finally {
            TenantContext.clear();
        }
        log.info("Ambiente local do Servire: paróquia {}, login {} / {}", SLUG, EMAIL, SENHA);
    }

    private void gravarParoquia() {
        jdbc.update("""
                INSERT INTO public.tenant (id, codigo, slug, nome, status, cidade, uf)
                VALUES (?, 'PAROQUIA-TESTE', ?, 'Paróquia de Teste', 'ATIVO', 'São Paulo', 'SP')
                """, TENANT_ID, SLUG);
        Tenant tenant = tenants.findById(TENANT_ID).orElseThrow();

        Usuario usuario = new Usuario(EMAIL, "Administrador da paróquia");
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario = usuarios.saveAndFlush(usuario);
        Perfil perfil = perfis.criarPadrao(TENANT_ID);
        UsuarioTenant vinculo = new UsuarioTenant(usuario, tenant, UsuarioTenant.Role.ADMIN, UsuarioTenant.Status.ATIVO);
        vinculo.setPerfil(perfil);
        vinculos.save(vinculo);

        DireitosLocais local = new DireitosLocais(TENANT_ID, CONTRATACAO_ID);
        local.setVersao(1);
        local.setSituacao("ATIVA");
        local.setAcessoLiberado(true);
        local.setPlanoCodigo("PAROQUIA");
        local.setPlanoNome("Paróquia");
        local.setFuncionalidades(br.com.servire.api.integracao.FuncionalidadesPlano.CODIGOS.toArray(String[]::new));
        local.setConfirmadoEm(Instant.now());
        direitos.save(local);
        jdbc.update("""
                INSERT INTO public.layout_escala (tenant_id, nome, descricao, tipo, colunas, sistema, ativo, padrao)
                VALUES (?, ?, ?, 'SEMANAL', ?::jsonb, true, true, true)
                """, TENANT_ID, LayoutsDeFabrica.NOME_SEMANAL, LayoutsDeFabrica.DESCRICAO_SEMANAL, LayoutsDeFabrica.COLUNAS_SEMANAL);
        jdbc.update("""
                INSERT INTO public.layout_escala (tenant_id, nome, descricao, tipo, colunas, sistema, ativo, padrao)
                VALUES (?, ?, ?, 'MENSAL', ?::jsonb, true, true, true)
                """, TENANT_ID, LayoutsDeFabrica.NOME_MENSAL, LayoutsDeFabrica.DESCRICAO_MENSAL, LayoutsDeFabrica.COLUNAS_MENSAL);
    }

    /** Transação nova, aberta depois do TenantContext: o Hibernate grava o tenant da pessoa. */
    private void gravarPessoas() {
        for (String nome : List.of("Ana Souza", "Bruno Lima", "Carla Dias")) {
            Pessoa pessoa = new Pessoa(Set.of(PessoaPapel.VOLUNTARIO), nome);
            Voluntario voluntario = new Voluntario();
            voluntario.setTipo(TipoVoluntario.COROINHA);
            pessoa.setVoluntario(voluntario);
            pessoas.save(pessoa);
        }
    }

    /**
     * O host do JDBC precisa ser esta máquina. Olha o host de verdade, e não se o texto contém "localhost":
     * {@code ...supabase.com/postgres?app=localhost} ou {@code localhost.exemplo.com} não contam.
     */
    static boolean bancoNestaMaquina(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            return false;
        }
        try {
            String host = java.net.URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
