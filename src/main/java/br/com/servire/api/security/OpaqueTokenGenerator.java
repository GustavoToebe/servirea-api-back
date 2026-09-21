package br.com.servire.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Geração e hash de tokens opacos de alta entropia — usado por
 * {@code RefreshToken} (seção 35/36) e {@code PasswordResetToken}
 * (seção 32), que compartilham exatamente o mesmo requisito: um valor
 * aleatório entregue uma vez ao cliente, do qual só o hash fica no banco.
 *
 * <p><b>Por que SHA-256, e não bcrypt/argon2 (o algoritmo usado para
 * senha):</b> bcrypt/argon2 são deliberadamente lentos e incluem um salt
 * aleatório embutido — duas execuções sobre o MESMO valor produzem hashes
 * DIFERENTES, o que é essencial para senha (baixa entropia, precisa
 * resistir a ataque de força bruta offline) mas inviabiliza a busca no
 * banco por {@code token_hash} (não dá pra fazer
 * {@code WHERE token_hash = :hash} com um hash não determinístico sem
 * carregar e comparar token por token). Um token opaco gerado aqui já
 * nasce com alta entropia (256 bits aleatórios via {@link SecureRandom}) —
 * um hash rápido e determinístico (SHA-256) é seguro nesse caso porque não
 * há o que "forçar por dicionário": só resta forçar o espaço de 2^256
 * valores possíveis, inviável na prática. SHA-256 determinístico permite o
 * {@code UNIQUE (token_hash)}/busca indexada que as migrations V019/V022
 * já previam.</p>
 */
public final class OpaqueTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits de entropia

    private OpaqueTokenGenerator() {
    }

    public static String gerar() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String tokenBruto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(tokenBruto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em qualquer JVM compatível com o padrão
            // (JCA) — nunca deveria realmente acontecer.
            throw new IllegalStateException("SHA-256 não disponível na JVM", e);
        }
    }
}
