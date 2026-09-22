package br.com.servire.api.storage;

import br.com.servire.api.web.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Implementação do Storage (Fase 7, seção 107 do plano mestre) chamando
 * diretamente a API REST do Supabase Storage via {@link RestClient} — em
 * vez do SDK/protocolo S3 completo (opção descartada de propósito: só
 * precisamos de 3 operações simples — enviar, assinar URL, excluir — e
 * evitar mais uma dependência/SDK pesado quando três chamadas HTTP diretas
 * resolvem, seção 15 do plano mestre sobre manter a stack enxuta).
 *
 * <p><b>Endpoints usados (documentação pública do Supabase Storage,
 * consultada em 22/09/2026 — não foi possível confirmar contra um projeto
 * Supabase real neste ambiente de pesquisa; se o próximo
 * {@code mvn clean verify}/teste manual acusar erro de formato de request/
 * response aqui, este é o primeiro lugar a checar):</b></p>
 * <ul>
 *   <li>{@code POST /storage/v1/object/{bucket}/{caminho}}, corpo = bytes
 *   crus, header {@code x-upsert: true} (permite sobrescrever uma foto já
 *   existente sem precisar de um DELETE antes) — upload.</li>
 *   <li>{@code POST /storage/v1/object/sign/{bucket}/{caminho}}, corpo
 *   {@code {"expiresIn": <segundos>}}, resposta
 *   {@code {"signedURL": "/object/sign/{bucket}/{caminho}?token=..."}} — a
 *   URL final é {@code baseUrl + "/storage/v1" + signedURL}.</li>
 *   <li>{@code DELETE /storage/v1/object/{bucket}/{caminho}} — exclusão de
 *   um único arquivo.</li>
 * </ul>
 *
 * <p>Todas as chamadas usam {@code Authorization: Bearer
 * <service-role-key>} — a {@code service_role} do Supabase ignora RLS/
 * policies de {@code storage.objects} (V015), então é a própria aplicação
 * Java, não o Postgres, quem passa a ser responsável por só deixar um
 * usuário autenticado do tenant certo chegar a estes métodos (chamada
 * sempre a partir de {@code VoluntarioService}/{@code InscricaoService},
 * nunca exposta direto num controller sem validação de tenant antes).</p>
 *
 * <p><b>Bug real #7 (22/09/2026), encontrado escrevendo
 * {@code SupabaseStorageServiceTest} (não por build — débito técnico de
 * testes da Fase 7 pago só agora, na Fase 10):</b> os três métodos
 * abaixo originalmente montavam a URI com {@code .uri(template,
 * bucket, caminho)}, tratando {@code {caminho}} como UMA única variável
 * de template. Como {@code caminho} SEMPRE contém {@code /} (ex.:
 * {@code voluntarioId + "/perfil-" + timestamp + extensão}, ver
 * {@code VoluntarioService#definirFoto}), o {@code UriComponentsBuilder}
 * por trás do {@link RestClient} codifica esse {@code /} como
 * {@code %2F} ao expandir uma única variável — comportamento padrão e
 * documentado do Spring, não um bug do Spring em si, mas que quebraria
 * TODA chamada real ao Supabase Storage (a API do Supabase espera os
 * segmentos separados por {@code /} de verdade na URL, não
 * {@code %2F} literal). <b>Corrigido</b> embutindo {@code bucket}/
 * {@code caminho} diretamente na string da URI (sem placeholder de
 * template para eles) — como os dois vêm de valores controlados pela
 * própria aplicação (nome de bucket fixo em configuração; caminho
 * montado só com UUID + sufixo fixo + extensão de uma lista permitida,
 * nunca com conteúdo arbitrário do usuário), não há necessidade de
 * escapar caracteres especiais além de preservar as barras como
 * separadoras de verdade. <b>Ainda não confirmado por um
 * {@code mvn clean verify} real</b> — {@code SupabaseStorageServiceTest}
 * cobre isso com {@code MockRestServiceServer} (URL literal esperada
 * com barras, não {@code %2F}), mas só um teste contra um projeto
 * Supabase de verdade fecharia esse risco por completo.</p>
 */
@Service
public class SupabaseStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    private final RestClient restClient;
    private final StorageProperties properties;

    public SupabaseStorageService(RestClient.Builder restClientBuilder, StorageProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String armazenar(String caminho, byte[] conteudo, String contentType) {
        validarArquivo(contentType, conteudo == null ? 0 : conteudo.length);
        requireConfigurado();
        try {
            restClient.post()
                    .uri(properties.baseUrl() + "/storage/v1/object/" + properties.bucket() + "/" + caminho)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceRoleKey())
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(conteudo)
                    .retrieve()
                    .toBodilessEntity();
            return caminho;
        } catch (RestClientException e) {
            // Falha de infraestrutura (Supabase fora do ar, credencial errada
            // etc.) — nunca detalhe do driver HTTP pro cliente, só log (mesma
            // regra do GlobalExceptionHandler para exceção não mapeada).
            log.error("Falha ao enviar arquivo para o Supabase Storage: bucket={} caminho={}", properties.bucket(), caminho, e);
            throw new StorageException("Não foi possível enviar o arquivo no momento. Tente novamente em instantes.", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String gerarUrlAssinada(String caminho) {
        requireConfigurado();
        try {
            Map<String, Object> resposta = restClient.post()
                    .uri(properties.baseUrl() + "/storage/v1/object/sign/" + properties.bucket() + "/" + caminho)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceRoleKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", properties.signedUrlTtl().toSeconds()))
                    .retrieve()
                    .body(Map.class);
            if (resposta == null || resposta.get("signedURL") == null) {
                throw new StorageException("Resposta inesperada do Supabase Storage ao assinar URL.", null);
            }
            return properties.baseUrl() + "/storage/v1" + resposta.get("signedURL");
        } catch (RestClientException e) {
            log.error("Falha ao gerar URL assinada no Supabase Storage: bucket={} caminho={}", properties.bucket(), caminho, e);
            throw new StorageException("Não foi possível gerar o link da foto no momento. Tente novamente em instantes.", e);
        }
    }

    @Override
    public void excluir(String caminho) {
        requireConfigurado();
        try {
            restClient.delete()
                    .uri(properties.baseUrl() + "/storage/v1/object/" + properties.bucket() + "/" + caminho)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceRoleKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Exclusão é best-effort de propósito: um arquivo órfão no bucket
            // é bem menos grave que travar a operação de negócio (ex.: trocar
            // a foto do voluntário) por causa de um DELETE que falhou.
            log.warn("Falha ao excluir arquivo do Supabase Storage (ignorado, best-effort): bucket={} caminho={}", properties.bucket(), caminho, e);
        }
    }

    private void validarArquivo(String contentType, long tamanhoBytes) {
        if (contentType == null || !properties.allowedMimeTypes().contains(contentType)) {
            throw new BadRequestException(
                    "Tipo de arquivo não permitido. Tipos aceitos: " + String.join(", ", properties.allowedMimeTypes()) + ".");
        }
        if (tamanhoBytes <= 0) {
            throw new BadRequestException("Arquivo vazio.");
        }
        if (tamanhoBytes > properties.maxFileSizeBytes()) {
            throw new BadRequestException(
                    "Arquivo maior que o limite permitido (" + (properties.maxFileSizeBytes() / 1024 / 1024) + " MB).");
        }
    }

    /**
     * Falha alto e cedo (não um 500 genérico depois de tentar a chamada
     * HTTP) se {@code baseUrl}/{@code serviceRoleKey} não foram
     * configurados — evita um erro confuso de "host desconhecido" quando
     * na verdade é só configuração ausente (mesma filosofia do
     * {@code JwtService} recusando um {@code jwt.secret} curto demais).
     */
    private void requireConfigurado() {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.serviceRoleKey() == null || properties.serviceRoleKey().isBlank()) {
            throw new IllegalStateException(
                    "Storage não configurado (servire.storage.base-url/service-role-key ausentes) — "
                            + "defina SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY.");
        }
    }
}
