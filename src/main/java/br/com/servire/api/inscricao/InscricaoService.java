package br.com.servire.api.inscricao;

import br.com.servire.api.inscricao.dto.InscricaoAtualizarRequest;
import br.com.servire.api.inscricao.dto.InscricaoPublicaRequest;
import br.com.servire.api.inscricao.dto.InscricaoResponsavelRequest;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.Responsavel;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.voluntario.VoluntarioRepository;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Regras de negócio de inscrição pública e da fila de aprovação (Fase 8,
 * seção 44/108 do plano mestre).
 */
@Service
public class InscricaoService {

    private final InscricaoRepository inscricaoRepository;
    private final VoluntarioRepository voluntarioRepository;
    private final TenantRepository tenantRepository;
    private final StorageService storageService;
    private final TurnstileService turnstileService;
    private final InscricaoRateLimiter rateLimiter;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public InscricaoService(InscricaoRepository inscricaoRepository,
                             VoluntarioRepository voluntarioRepository,
                             TenantRepository tenantRepository,
                             StorageService storageService,
                             TurnstileService turnstileService,
                             InscricaoRateLimiter rateLimiter,
                             PlatformTransactionManager transactionManager) {
        this.inscricaoRepository = inscricaoRepository;
        this.voluntarioRepository = voluntarioRepository;
        this.tenantRepository = tenantRepository;
        this.storageService = storageService;
        this.turnstileService = turnstileService;
        this.rateLimiter = rateLimiter;
        // Ver javadoc de criarPublica: precisamos abrir a transação só
        // DEPOIS de TenantContext.set(...), então não dá pra usar
        // @Transactional (que abriria a sessão do Hibernate na entrada do
        // método, antes do tenant ser resolvido) — Bug real #8, seção 108.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Cria uma inscrição a partir do formulário público — SEM autenticação
     * (rota {@code /public/**}, seção 44). Como não passa por
     * {@link br.com.servire.api.security.JwtAuthenticationFilter}, o
     * {@link TenantContext} não vem setado por ninguém: esta é a única
     * classe de negócio do projeto que chama {@link TenantContext#set}
     * diretamente, imitando o que o filtro faz para requisições
     * autenticadas — sempre com {@code try/finally} (regra P0, seção
     * 20/78/79/80).
     *
     * <p>Ordem deliberada: rate limit (mais barato) → Turnstile (chamada de
     * rede) → resolver tenant → gravar. Cada barreira só é cruzada se a
     * anterior passou, para não gastar uma chamada HTTP ao Cloudflare em
     * requisições que o rate limit já teria barrado.</p>
     *
     * <p><b>Bug real #8 (seção 108):</b> este método NÃO pode ser anotado
     * com {@code @Transactional} — o Hibernate resolve e fixa o
     * identificador de tenant da sessão no momento em que a sessão é
     * aberta, o que sob {@code @Transactional} declarativo acontece na
     * ENTRADA do método (antes do corpo rodar), e não no momento em que
     * {@link TenantContext#set} é chamado dentro dele. Com
     * {@code @Transactional} no método, a sessão nascia presa ao tenant
     * sentinela {@code SEM_TENANT} e o INSERT (adiado até o commit)
     * violava a FK {@code inscricoes_tenant_id_fkey} — a inscrição pública
     * nunca funcionou de fato contra um banco real. A correção é abrir a
     * transação manualmente, via {@link #transactionTemplate}, só DEPOIS
     * de {@code TenantContext.set(...)} já ter rodado.</p>
     */
    public Inscricao criarPublica(String tenantSlug, InscricaoPublicaRequest request, MultipartFile foto, String ipRemetente) {
        rateLimiter.registrarTentativa(ipRemetente);
        turnstileService.validar(request.turnstileToken(), ipRemetente);

        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
        // Mesma checagem de Kill Switch do JwtAuthenticationFilter (seção 28)
        // — uma paróquia BLOQUEADA/CANCELADA não deve nem aceitar novas
        // inscrições no formulário público.
        if (tenant.getStatus() != Tenant.Status.ATIVO && tenant.getStatus() != Tenant.Status.TRIAL) {
            throw new ResourceNotFoundException("Paróquia não encontrada.");
        }

        TenantContext.set(tenant.getId());
        MDC.put(br.com.servire.api.security.JwtAuthenticationFilter.MDC_KEY, tenant.getId().toString());
        try {
            return transactionTemplate.execute(status -> gravarInscricaoPublica(request, foto));
        } finally {
            MDC.remove(br.com.servire.api.security.JwtAuthenticationFilter.MDC_KEY);
            TenantContext.clear();
        }
    }

    /**
     * Parte da gravação de {@link #criarPublica} que precisa mesmo de uma
     * transação — extraída para rodar dentro de {@link #transactionTemplate},
     * já com {@link TenantContext#set} aplicado (ver Bug real #8 acima).
     */
    private Inscricao gravarInscricaoPublica(InscricaoPublicaRequest request, MultipartFile foto) {
        Inscricao inscricao = new Inscricao(request.nomeCompleto());
        aplicarCampos(inscricao, request.dataNascimento(), request.tipo(), request.etapaCatequese(),
                request.eucaristiaAno(), request.crismaAno(), request.rua(), request.numero(), request.bairro(),
                request.telefone(), request.celular(), request.email(), request.horarioEstudo(),
                request.observacoes(), request.autorizaWhatsapp(), request.funcoesHabilitadas());
        substituirResponsaveis(inscricao, request.responsaveis());
        inscricao = inscricaoRepository.save(inscricao);

        if (foto != null && !foto.isEmpty()) {
            String caminho = "inscricoes/" + inscricao.getId() + "/foto" + extensaoDe(foto);
            byte[] conteudo;
            try {
                conteudo = foto.getBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Falha ao ler o arquivo de foto enviado.", e);
            }
            String caminhoSalvo = storageService.armazenar(caminho, conteudo, foto.getContentType());
            inscricao.setFotoPath(caminhoSalvo);
        }
        return inscricao;
    }

    @Transactional(readOnly = true)
    public List<Inscricao> buscar(StatusInscricao status) {
        return inscricaoRepository.buscar(status);
    }

    @Transactional(readOnly = true)
    public Inscricao buscarPorId(UUID id) {
        return inscricaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
    }

    @Transactional
    public Inscricao atualizarPendente(UUID id, InscricaoAtualizarRequest request) {
        Inscricao inscricao = buscarPorId(id);
        exigirPendente(inscricao);
        inscricao.setNomeCompleto(request.nomeCompleto());
        aplicarCampos(inscricao, request.dataNascimento(), request.tipo(), request.etapaCatequese(),
                request.eucaristiaAno(), request.crismaAno(), request.rua(), request.numero(), request.bairro(),
                request.telefone(), request.celular(), request.email(), request.horarioEstudo(),
                request.observacoes(), request.autorizaWhatsapp(), request.funcoesHabilitadas());
        substituirResponsaveis(inscricao, request.responsaveis());
        return inscricao;
    }

    /**
     * Aprova a inscrição: cria um {@link Voluntario} de verdade a partir
     * dos dados da inscrição (copiando também os responsáveis), vincula
     * {@code inscricao.voluntario_id} ao voluntário recém-criado e marca a
     * inscrição como {@code APROVADA} — tudo na mesma transação (a CHECK
     * {@code inscricoes_aprovada_ck}, V008, exige que os três campos de
     * auditoria estejam preenchidos juntos).
     *
     * <p>Sem tabela de auditoria dedicada ainda (só um item futuro do
     * plano mestre, seção 16 — pacote {@code auditoria/}) — por ora a
     * aprovação/rejeição só fica registrada nos próprios campos de
     * {@code inscricoes} e neste log; ver README/plano mestre.</p>
     */
    @Transactional
    public Inscricao aprovar(UUID id, UUID usuarioIdAprovador) {
        Inscricao inscricao = buscarPorId(id);
        exigirPendente(inscricao);

        Voluntario voluntario = new Voluntario(inscricao.getNomeCompleto());
        voluntario.setDataNascimento(inscricao.getDataNascimento());
        voluntario.setTipo(inscricao.getTipo());
        voluntario.setAtivo(true);
        voluntario.setFotoPath(inscricao.getFotoPath());
        voluntario.setEtapaCatequese(inscricao.getEtapaCatequese());
        voluntario.setEucaristiaAno(inscricao.getEucaristiaAno());
        voluntario.setCrismaAno(inscricao.getCrismaAno());
        voluntario.setRua(inscricao.getRua());
        voluntario.setNumero(inscricao.getNumero());
        voluntario.setBairro(inscricao.getBairro());
        voluntario.setTelefone(inscricao.getTelefone());
        voluntario.setCelular(inscricao.getCelular());
        voluntario.setEmail(inscricao.getEmail());
        voluntario.setHorarioEstudo(inscricao.getHorarioEstudo());
        voluntario.setObservacoes(inscricao.getObservacoes());
        voluntario.setAutorizaWhatsapp(inscricao.isAutorizaWhatsapp());
        voluntario.setFuncoesHabilitadas(inscricao.getFuncoesHabilitadas());
        for (InscricaoResponsavel ir : inscricao.getResponsaveis()) {
            Responsavel responsavel = new Responsavel(ir.getParentesco(), ir.getNome(), ir.getTelefone(), ir.getCelular(),
                    ir.getEmail(), ir.isPrincipal());
            responsavel.setVoluntario(voluntario);
            voluntario.getResponsaveis().add(responsavel);
        }
        voluntario = voluntarioRepository.save(voluntario);

        inscricao.setStatus(StatusInscricao.APROVADA);
        inscricao.setVoluntarioId(voluntario.getId());
        inscricao.setDataAprovacao(Instant.now());
        inscricao.setAprovadoPor(usuarioIdAprovador);
        return inscricao;
    }

    @Transactional
    public Inscricao rejeitar(UUID id, String motivo, UUID usuarioIdRejeitador) {
        if (motivo == null || motivo.isBlank()) {
            throw new BadRequestException("Motivo da rejeição é obrigatório.");
        }
        Inscricao inscricao = buscarPorId(id);
        exigirPendente(inscricao);

        inscricao.setStatus(StatusInscricao.REJEITADA);
        inscricao.setDataRejeicao(Instant.now());
        inscricao.setRejeitadoPor(usuarioIdRejeitador);
        inscricao.setMotivoRejeicao(motivo);
        return inscricao;
    }

    private void exigirPendente(Inscricao inscricao) {
        if (inscricao.getStatus() != StatusInscricao.PENDENTE) {
            throw new ConflictException(
                    "Esta inscrição já foi " + (inscricao.getStatus() == StatusInscricao.APROVADA ? "aprovada" : "rejeitada")
                            + " — não pode mais ser alterada.");
        }
    }

    private void aplicarCampos(Inscricao inscricao, java.time.LocalDate dataNascimento,
                                br.com.servire.api.voluntario.TipoVoluntario tipo, String etapaCatequese,
                                String eucaristiaAno, String crismaAno, String rua, String numero, String bairro,
                                String telefone, String celular, String email, Voluntario.HorarioEstudo horarioEstudo,
                                String observacoes, boolean autorizaWhatsapp,
                                List<br.com.servire.api.voluntario.FuncaoEscala> funcoesHabilitadas) {
        inscricao.setDataNascimento(dataNascimento);
        inscricao.setTipo(tipo);
        inscricao.setEtapaCatequese(etapaCatequese);
        inscricao.setEucaristiaAno(eucaristiaAno);
        inscricao.setCrismaAno(crismaAno);
        inscricao.setRua(rua);
        inscricao.setNumero(numero);
        inscricao.setBairro(bairro);
        inscricao.setTelefone(telefone);
        inscricao.setCelular(celular);
        inscricao.setEmail(email);
        inscricao.setHorarioEstudo(horarioEstudo);
        inscricao.setObservacoes(observacoes);
        inscricao.setAutorizaWhatsapp(autorizaWhatsapp);
        inscricao.setFuncoesHabilitadas(funcoesHabilitadas == null ? new br.com.servire.api.voluntario.FuncaoEscala[0]
                : funcoesHabilitadas.toArray(new br.com.servire.api.voluntario.FuncaoEscala[0]));
    }

    /**
     * Mesma regra "exatamente um principal" (seção 38) reaplicada aqui —
     * {@link Inscricao} não compartilha entidade com {@link Voluntario}.
     *
     * <p><b>Bug real #10 (mesmo mecanismo de {@code VoluntarioService},
     * seção 106):</b> encontrado por revisão manual proativa depois do
     * bug idêntico confirmado em {@code VoluntarioService.substituirResponsaveis}
     * — sem o {@code entityManager.flush()} logo após o {@code clear()},
     * o Hibernate pode tentar inserir o novo responsável principal ANTES
     * de deletar o antigo (a ordem de flush do Hibernate agrupa por tipo
     * de operação, não segue a ordem cronológica da coleção Java),
     * violando o índice único parcial {@code ux_inscricao_responsavel_principal}
     * (V009) quando esta chamada é usada para SUBSTITUIR responsáveis de
     * uma inscrição já existente (via {@link #atualizarPendente}) — não
     * afeta {@link #criarPublica}, que sempre parte de uma inscrição nova
     * sem responsáveis antigos para conflitar. Ainda sem um teste
     * automatizado que exercite essa troca de principal em
     * {@code atualizarPendente} (só {@code InscricaoServiceIntegrationTest.atualizarPendenteDepoisDeAprovadaLancaConflictException}
     * toca esse método, e não troca o principal) — corrigido
     * proativamente por analogia, não por um build real que o tenha
     * confirmado.</p>
     */
    private void substituirResponsaveis(Inscricao inscricao, List<InscricaoResponsavelRequest> requests) {
        long principais = requests.stream().filter(InscricaoResponsavelRequest::principal).count();
        if (principais != 1) {
            throw new BadRequestException(
                    "Deve existir exatamente um responsável principal (seção 38 do plano mestre) — recebido: " + principais + ".");
        }
        inscricao.getResponsaveis().clear();
        entityManager.flush();
        for (InscricaoResponsavelRequest r : requests) {
            InscricaoResponsavel responsavel = new InscricaoResponsavel(r.parentesco(), r.nome(), r.telefone(), r.celular(),
                    r.email(), r.principal());
            responsavel.setInscricao(inscricao);
            inscricao.getResponsaveis().add(responsavel);
        }
    }

    private String extensaoDe(MultipartFile foto) {
        String nomeOriginal = foto.getOriginalFilename();
        if (nomeOriginal != null && nomeOriginal.contains(".")) {
            return nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
        }
        // Mesma proteção contra contentType nulo de VoluntarioService.extensaoDe.
        return switch (String.valueOf(foto.getContentType())) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            default -> ".jpg";
        };
    }
}
