package br.com.servire.api.voluntario;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.voluntario.dto.ResponsavelRequest;
import br.com.servire.api.voluntario.dto.VoluntarioRequest;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/**
 * Regras de negócio de voluntário/responsável (seção 37/38/39/106 do
 * plano mestre).
 *
 * <p>{@link #criar} e {@link #atualizar} são {@code @Transactional}
 * porque voluntário + responsáveis precisam ser consistentes juntos
 * (seção 39) — se a validação de responsável principal falhar depois de
 * já ter mexido no voluntário, a transação inteira desfaz. O arquivo
 * físico da foto (Storage, Fase 7) não participa dessa transação SQL,
 * como já avisa a seção 39 — não é responsabilidade desta classe ainda.</p>
 */
@Service
public class VoluntarioService {

    /** Campos cobertos por {@link VoluntarioRequest}/{@link #aplicarCampos} — usado só para popular {@code changedFields} da auditoria (seção 59), ver javadoc de {@link #atualizar}. */
    private static final List<String> CAMPOS_ATUALIZAVEIS = List.of(
            "nomeCompleto", "dataNascimento", "tipo", "ativo", "etapaCatequese", "eucaristiaAno", "crismaAno",
            "rua", "numero", "bairro", "telefone", "celular", "email", "horarioEstudo", "observacoes",
            "autorizaWhatsapp", "funcoesHabilitadas", "responsaveis");

    private final VoluntarioRepository voluntarioRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @PersistenceContext
    private EntityManager entityManager;

    public VoluntarioService(VoluntarioRepository voluntarioRepository, StorageService storageService,
                              AuditLogService auditLogService) {
        this.voluntarioRepository = voluntarioRepository;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<Voluntario> buscar(Boolean ativo, TipoVoluntario tipo, String nome) {
        return voluntarioRepository.buscar(ativo, tipo, nome);
    }

    @Transactional(readOnly = true)
    public long contarPorAtivo(boolean ativo) {
        return voluntarioRepository.countByAtivo(ativo);
    }

    @Transactional(readOnly = true)
    public Voluntario buscarPorId(UUID id) {
        return voluntarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voluntário não encontrado."));
    }

    @Transactional
    public Voluntario criar(VoluntarioRequest request) {
        Voluntario voluntario = new Voluntario(request.nomeCompleto());
        aplicarCampos(voluntario, request);
        substituirResponsaveis(voluntario, request.responsaveis());
        voluntario = voluntarioRepository.save(voluntario);
        auditLogService.registrar("CRIACAO", "VOLUNTARIO", voluntario.getId(), null);
        return voluntario;
    }

    /**
     * {@code changedFields} da auditoria (seção 59) registra a lista FIXA
     * de campos que {@link VoluntarioRequest}/{@link #aplicarCampos} cobrem
     * ({@link #CAMPOS_ATUALIZAVEIS}), não um diff de verdade contra o
     * estado anterior — simplificação deliberada (evita ter que capturar um
     * snapshot do voluntário antes de aplicar as mudanças só para comparar
     * campo a campo depois); ainda cumpre o objetivo da seção 59 de não
     * duplicar o conteúdo pessoal em si, só nomeia os campos que ESTE tipo
     * de requisição é capaz de alterar.
     */
    @Transactional
    public Voluntario atualizar(UUID id, VoluntarioRequest request) {
        Voluntario voluntario = buscarPorId(id);
        aplicarCampos(voluntario, request);
        substituirResponsaveis(voluntario, request.responsaveis());
        voluntario = voluntarioRepository.save(voluntario);
        auditLogService.registrar("ATUALIZACAO", "VOLUNTARIO", voluntario.getId(), CAMPOS_ATUALIZAVEIS);
        return voluntario;
    }

    @Transactional
    public Voluntario setAtivo(UUID id, boolean ativo) {
        Voluntario voluntario = buscarPorId(id);
        voluntario.setAtivo(ativo);
        auditLogService.registrar(ativo ? "ATIVACAO" : "DESATIVACAO", "VOLUNTARIO", id, List.of("ativo"));
        return voluntario;
    }

    /**
     * Envia a foto do voluntário para o Storage (Fase 7, seção 107) e
     * grava o caminho retornado em {@code foto_path}. O arquivo em si NÃO
     * participa da transação SQL (é uma chamada HTTP ao Supabase Storage,
     * não algo que o rollback do banco desfaz) — por isso o upload
     * acontece ANTES de qualquer escrita no banco: se o Storage falhar,
     * nada muda no voluntário; se o upload funcionar mas o commit da
     * transação falhar depois, fica um arquivo órfão no bucket (aceitável,
     * documentado — pior cenário é bem menos grave que o inverso).
     */
    @Transactional
    public Voluntario definirFoto(UUID id, MultipartFile foto) {
        Voluntario voluntario = buscarPorId(id);
        if (foto == null || foto.isEmpty()) {
            throw new BadRequestException("Nenhum arquivo de foto enviado.");
        }
        String caminho = voluntario.getId() + "/perfil-" + System.currentTimeMillis() + extensaoDe(foto);
        byte[] conteudo;
        try {
            conteudo = foto.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo de foto enviado.", e);
        }
        String caminhoSalvo = storageService.armazenar(caminho, conteudo, foto.getContentType());
        voluntario.setFotoPath(caminhoSalvo);
        auditLogService.registrar("FOTO_ATUALIZADA", "VOLUNTARIO", id, List.of("fotoPath"));
        return voluntario;
    }

    @Transactional(readOnly = true)
    public String obterUrlFoto(UUID id) {
        Voluntario voluntario = buscarPorId(id);
        if (voluntario.getFotoPath() == null) {
            throw new ResourceNotFoundException("Voluntário não possui foto cadastrada.");
        }
        return storageService.gerarUrlAssinada(voluntario.getFotoPath());
    }

    private String extensaoDe(MultipartFile foto) {
        String nomeOriginal = foto.getOriginalFilename();
        if (nomeOriginal != null && nomeOriginal.contains(".")) {
            return nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
        }
        // contentType pode vir nulo (cliente não setou) — switch sobre
        // String nula lançaria NullPointerException, por isso o
        // String.valueOf() abaixo (nunca nulo, cai no "default").
        return switch (String.valueOf(foto.getContentType())) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            default -> ".jpg";
        };
    }

    private void aplicarCampos(Voluntario voluntario, VoluntarioRequest request) {
        voluntario.setNomeCompleto(request.nomeCompleto());
        voluntario.setDataNascimento(request.dataNascimento());
        voluntario.setTipo(request.tipo());
        voluntario.setAtivo(request.ativo());
        voluntario.setEtapaCatequese(request.etapaCatequese());
        voluntario.setEucaristiaAno(request.eucaristiaAno());
        voluntario.setCrismaAno(request.crismaAno());
        voluntario.setRua(request.rua());
        voluntario.setNumero(request.numero());
        voluntario.setBairro(request.bairro());
        voluntario.setTelefone(request.telefone());
        voluntario.setCelular(request.celular());
        voluntario.setEmail(request.email());
        voluntario.setHorarioEstudo(request.horarioEstudo());
        voluntario.setObservacoes(request.observacoes());
        voluntario.setAutorizaWhatsapp(request.autorizaWhatsapp());
        List<FuncaoEscala> funcoes = request.funcoesHabilitadas();
        voluntario.setFuncoesHabilitadas(funcoes == null ? new FuncaoEscala[0] : funcoes.toArray(new FuncaoEscala[0]));
    }

    /**
     * Substitui TODOS os responsáveis do voluntário pela lista recebida —
     * mesmo padrão "apaga tudo e reinsere" do Angular atual
     * ({@code replaceResponsaveis}). {@code clear()} seguido de
     * reinserção na mesma coleção gerenciada pelo Hibernate (em vez de
     * criar uma lista nova) é o que faz o {@code orphanRemoval = true}
     * de {@link Voluntario#getResponsaveis()} de fato deletar os antigos
     * no flush.
     *
     * <p><b>Bug real #10 (seção 106):</b> o {@code entityManager.flush()}
     * logo depois do {@code clear()} é necessário — sem ele, dentro do
     * mesmo contexto de persistência o Hibernate podia executar o
     * {@code INSERT} do novo responsável principal ANTES do
     * {@code DELETE} do antigo (a ordem de flush do Hibernate não segue a
     * ordem cronológica das operações na coleção Java, e sim agrupa por
     * tipo de operação). Com os dois responsáveis "principais" existindo
     * ao mesmo tempo, mesmo que só por um instante dentro da mesma
     * transação, a constraint {@code uq_responsavel_principal_por_voluntario}
     * (índice único parcial, V004) recusava o `INSERT`, com
     * {@code duplicate key value violates unique constraint}. O
     * {@code flush()} força os `DELETE`s pendentes a serem enviados ao
     * banco imediatamente (sem commitar a transação), garantindo que o
     * responsável principal antigo já não exista mais quando o novo for
     * inserido — encontrado pelo `mvn clean verify` real da Fase 10
     * (`VoluntarioServiceIntegrationTest.atualizarSubstituiTodosOsResponsaveisAntigosPelosNovos`),
     * já com todos os outros bugs desta rodada corrigidos.</p>
     */
    private void substituirResponsaveis(Voluntario voluntario, List<ResponsavelRequest> requests) {
        long principais = requests.stream().filter(ResponsavelRequest::principal).count();
        if (principais != 1) {
            throw new BadRequestException(
                    "Deve existir exatamente um responsável principal (seção 38 do plano mestre) — recebido: " + principais + ".");
        }
        voluntario.getResponsaveis().clear();
        entityManager.flush();
        for (ResponsavelRequest r : requests) {
            Responsavel responsavel = new Responsavel(r.parentesco(), r.nome(), r.telefone(), r.celular(), r.email(), r.principal());
            responsavel.setVoluntario(voluntario);
            voluntario.getResponsaveis().add(responsavel);
        }
    }
}
