package br.com.servire.api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Configuração do módulo de Storage (Fase 7, seção 107 do plano mestre),
 * lida do prefixo {@code servire.storage} em {@code application*.yml}.
 *
 * <p>{@code baseUrl}/{@code serviceRoleKey} nunca têm valor padrão — assim
 * como {@code jwt.secret} ({@link br.com.servire.api.security.SecurityProperties}),
 * são segredo/endpoint de produção e só existem via variável de ambiente
 * ({@code SUPABASE_URL}/{@code SUPABASE_SERVICE_ROLE_KEY}) em
 * {@code application-prod.yml} — em dev/test ficam vazios de propósito
 * (upload real falha alto e cedo em vez de silenciosamente "funcionar" com
 * um valor errado; ver {@link SupabaseStorageService}).</p>
 *
 * <p>{@code bucket}/{@code maxFileSizeBytes}/{@code allowedMimeTypes} têm
 * valor padrão igual ao já confirmado em produção para o bucket
 * {@code voluntarios-fotos} (V015, seção 9.5 do documento técnico):
 * privado, 5&nbsp;MB, jpeg/png/webp/heic.</p>
 */
@ConfigurationProperties(prefix = "servire.storage")
public record StorageProperties(
        @DefaultValue("voluntarios-fotos") String bucket,
        String baseUrl,
        String serviceRoleKey,
        @DefaultValue("PT1H") Duration signedUrlTtl,
        @DefaultValue("5242880") long maxFileSizeBytes,
        @DefaultValue({"image/jpeg", "image/png", "image/webp", "image/heic"}) List<String> allowedMimeTypes) {

    public StorageProperties {
        if (baseUrl != null) {
            baseUrl = baseUrl.trim();
        }
        if (serviceRoleKey != null) {
            serviceRoleKey = serviceRoleKey.trim();
        }
    }
}
