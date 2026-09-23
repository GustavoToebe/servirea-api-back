package br.com.servire.api.backoffice;

import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria (ou promove) o operador {@code suporte@...} em dev, lendo e-mail e
 * senha de {@code application-dev-local.yml} (gitignorado). Sem senha no
 * git. Profile {@code test} não ativa isto — cada teste cria o seu.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "servire.backoffice", name = {"operador-email", "operador-senha"})
public class DevOperadorSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevOperadorSeed.class);

    private final BackofficeProperties properties;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DevOperadorSeed(BackofficeProperties properties,
                            UsuarioRepository usuarioRepository,
                            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.operadorEmail().trim();
        Usuario usuario = usuarioRepository.findByEmail(email).orElseGet(() -> new Usuario(email, "Operador Servire"));
        usuario.setOperadorSaas(true);
        usuario.setAtivo(true);
        usuario.setSenhaHash(passwordEncoder.encode(properties.operadorSenha()));
        usuarioRepository.save(usuario);
        log.info("Operador de backoffice pronto: {}", email);
    }
}
