package br.com.servire.api.storage;

/**
 * Abstração do módulo de Storage (Fase 7, seção 107 do plano mestre) —
 * hoje só tem a implementação {@link SupabaseStorageService}, mas fica
 * como interface para não acoplar {@code VoluntarioService}/
 * {@code InscricaoService} diretamente à API HTTP do Supabase Storage.
 */
public interface StorageService {

    /**
     * Envia (ou substitui, se já existir) o arquivo em {@code caminho}
     * dentro do bucket configurado. Valida tamanho/mime type ANTES de
     * tentar o upload (seção 9.5: 5&nbsp;MB, jpeg/png/webp/heic) — nunca
     * envia um arquivo fora dessas regras, mesmo que o bucket real também
     * as valide do seu lado.
     *
     * @return o próprio {@code caminho} recebido — é isso que fica salvo
     * em {@code voluntarios.foto_path}/{@code inscricoes.foto_path}, nunca
     * uma URL (o bucket é privado; a URL só existe temporariamente via
     * {@link #gerarUrlAssinada}).
     */
    String armazenar(String caminho, byte[] conteudo, String contentType);

    /** URL assinada e temporária (TTL configurado em {@code servire.storage.signed-url-ttl}) para ler o arquivo de um bucket privado. */
    String gerarUrlAssinada(String caminho);

    /** Remove o arquivo — usado quando uma foto é substituída ou um voluntário/inscrição é excluído. */
    void excluir(String caminho);

    /** Tamanho real pelo provedor, sem baixar a imagem. Não aceitar tamanho do navegador. */
    long tamanho(String caminho);
}
