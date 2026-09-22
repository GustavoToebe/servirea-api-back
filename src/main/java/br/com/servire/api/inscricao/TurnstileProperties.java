package br.com.servire.api.inscricao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do Cloudflare Turnstile (Fase 8, seção 44 do plano mestre)
 * — verificação anti-robô do formulário público de inscrição.
 *
 * <p>{@code secretKey} sem valor padrão de propósito (mesma regra de
 * {@code JWT_SECRET}/{@code SUPABASE_SERVICE_ROLE_KEY}, seção 90) — em
 * dev/test fica vazio, e {@link TurnstileService} FALHA FECHADO (recusa a
 * inscrição, nunca deixa passar) quando está vazio, em vez de pular a
 * verificação silenciosamente.</p>
 */
@ConfigurationProperties(prefix = "servire.turnstile")
public record TurnstileProperties(
        String secretKey,
        @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify") String verifyUrl) {
}
