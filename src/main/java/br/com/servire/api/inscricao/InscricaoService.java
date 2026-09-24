package br.com.servire.api.inscricao;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.inscricao.dto.InscricaoAtualizarRequest;
import br.com.servire.api.inscricao.dto.InscricaoPublicaRequest;
import br.com.servire.api.inscricao.dto.InscricaoResponsavelRequest;
import br.com.servire.api.pessoa.Contatos;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaEmail;
import br.com.servire.api.pessoa.PessoaPapel;
import br.com.servire.api.pessoa.PessoaRelacao;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.PessoaTelefone;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.storage.StorageService;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.TipoVoluntario;
import br.com.servire.api.voluntario.Voluntario;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Inscrição pública e fila de aprovação. Aprovar materializa
 * {@code pessoa} VOLUNTARIO + perfil e reusa RESPONSAVEL pelo e-mail
 * principal (ou cria um novo).
 */
@Service
public class InscricaoService {

    private final InscricaoRepository inscricaoRepository;
    private final PessoaRepository pessoaRepository;
    private final TenantRepository tenantRepository;
    private final StorageService storageService;
    private final TurnstileService turnstileService;
    private final InscricaoRateLimiter rateLimiter;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public InscricaoService(InscricaoRepository inscricaoRepository,
                             PessoaRepository pessoaRepository,
                             TenantRepository tenantRepository,
                             StorageService storageService,
                             TurnstileService turnstileService,
                             InscricaoRateLimiter rateLimiter,
                             AuditLogService auditLogService,
                             PlatformTransactionManager transactionManager) {
        this.inscricaoRepository = inscricaoRepository;
        this.pessoaRepository = pessoaRepository;
        this.tenantRepository = tenantRepository;
        this.storageService = storageService;
        this.turnstileService = turnstileService;
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Sem {@code @Transactional}: o Hibernate fixa o tenant na abertura da
     * sessão. {@code TenantContext.set} precisa rodar antes (Bug real #8).
     */
    public Inscricao criarPublica(String tenantSlug, InscricaoPublicaRequest request, MultipartFile foto,
                                  String ipRemetente) {
        rateLimiter.registrarTentativa(ipRemetente);
        turnstileService.validar(request.turnstileToken(), ipRemetente);

        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
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

    private Inscricao gravarInscricaoPublica(InscricaoPublicaRequest request, MultipartFile foto) {
        validarContatos(request.emails(), request.telefones(), request.responsaveis());
        Inscricao inscricao = new Inscricao(request.nomeCompleto().trim());
        aplicarCampos(inscricao, request.dataNascimento(), request.sexo(), request.cpf(), request.rg(),
                request.tipo(), request.etapaCatequese(), request.eucaristiaAno(), request.crismaAno(),
                request.cep(), request.cidade(), request.uf(), request.rua(), request.numero(),
                request.complemento(), request.bairro(), request.horarioEstudo(), request.observacoes(),
                request.autorizaWhatsapp(), request.funcoesHabilitadas());
        substituirContatos(inscricao, request.emails(), request.telefones());
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
            inscricao.setFotoPath(storageService.armazenar(caminho, conteudo, foto.getContentType()));
        }
        auditLogService.registrar("CRIACAO", "INSCRICAO", inscricao.getId(), null);
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
        validarContatos(request.emails(), request.telefones(), request.responsaveis());
        inscricao.setNomeCompleto(request.nomeCompleto().trim());
        aplicarCampos(inscricao, request.dataNascimento(), request.sexo(), request.cpf(), request.rg(),
                request.tipo(), request.etapaCatequese(), request.eucaristiaAno(), request.crismaAno(),
                request.cep(), request.cidade(), request.uf(), request.rua(), request.numero(),
                request.complemento(), request.bairro(), request.horarioEstudo(), request.observacoes(),
                request.autorizaWhatsapp(), request.funcoesHabilitadas());
        substituirContatos(inscricao, request.emails(), request.telefones());
        substituirResponsaveis(inscricao, request.responsaveis());
        auditLogService.registrar("ATUALIZACAO", "INSCRICAO", inscricao.getId(),
                List.of("identidade", "contatos", "responsaveis"));
        return inscricao;
    }

    /**
     * Materializa pessoa VOLUNTARIO + perfil e reusa/cria RESPONSAVEL
     * pelo e-mail principal de cada responsável da inscrição.
     */
    @Transactional
    public Inscricao aprovar(UUID id, UUID usuarioIdAprovador) {
        Inscricao inscricao = buscarPorId(id);
        exigirPendente(inscricao);

        Pessoa voluntarioPessoa = new Pessoa(PessoaPapel.VOLUNTARIO, inscricao.getNomeCompleto());
        voluntarioPessoa.setDataNascimento(inscricao.getDataNascimento());
        voluntarioPessoa.setSexo(opcional(inscricao.getSexo()));
        voluntarioPessoa.setCpf(opcional(inscricao.getCpf()));
        voluntarioPessoa.setRg(opcional(inscricao.getRg()));
        voluntarioPessoa.setCep(opcional(inscricao.getCep()));
        voluntarioPessoa.setCidade(opcional(inscricao.getCidade()));
        voluntarioPessoa.setUf(opcional(inscricao.getUf()));
        voluntarioPessoa.setLogradouro(opcional(inscricao.getRua()));
        voluntarioPessoa.setNumero(opcional(inscricao.getNumero()));
        voluntarioPessoa.setComplemento(opcional(inscricao.getComplemento()));
        voluntarioPessoa.setBairro(opcional(inscricao.getBairro()));
        voluntarioPessoa.setObservacoes(opcional(inscricao.getObservacoes()));
        copiarEmailsParaPessoa(voluntarioPessoa, inscricao.getEmails());
        copiarTelefonesParaPessoa(voluntarioPessoa, inscricao.getTelefones());

        Voluntario perfil = new Voluntario();
        perfil.setTipo(inscricao.getTipo());
        perfil.setAtivo(true);
        perfil.setFotoPath(inscricao.getFotoPath());
        perfil.setEtapaCatequese(opcional(inscricao.getEtapaCatequese()));
        perfil.setEucaristiaAno(opcional(inscricao.getEucaristiaAno()));
        perfil.setCrismaAno(opcional(inscricao.getCrismaAno()));
        perfil.setHorarioEstudo(inscricao.getHorarioEstudo());
        perfil.setAutorizaWhatsapp(inscricao.isAutorizaWhatsapp());
        perfil.setFuncoesHabilitadas(inscricao.getFuncoesHabilitadas());
        voluntarioPessoa.setVoluntario(perfil);

        voluntarioPessoa = pessoaRepository.saveAndFlush(voluntarioPessoa);

        for (InscricaoResponsavel ir : inscricao.getResponsaveis()) {
            Pessoa responsavel = resolverResponsavel(ir);
            PessoaRelacao relacao = new PessoaRelacao(responsavel, voluntarioPessoa, ir.getParentesco(),
                    opcional(ir.getParentescoInverso()), ir.isPrincipal());
            voluntarioPessoa.getResponsaveis().add(relacao);
        }
        pessoaRepository.save(voluntarioPessoa);
        auditLogService.registrar("CRIACAO", "PESSOA", voluntarioPessoa.getId(), null);

        inscricao.setStatus(StatusInscricao.APROVADA);
        inscricao.setVoluntarioId(voluntarioPessoa.getId());
        inscricao.setDataAprovacao(Instant.now());
        inscricao.setAprovadoPor(usuarioIdAprovador);
        auditLogService.registrar("APROVACAO", "INSCRICAO", inscricao.getId(), List.of("status"));
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
        auditLogService.registrar("REJEICAO", "INSCRICAO", id, List.of("status", "motivoRejeicao"));
        return inscricao;
    }

    private Pessoa resolverResponsavel(InscricaoResponsavel ir) {
        String emailPrincipal = ir.getEmails().stream()
                .filter(InscricaoResponsavelEmail::isPrincipal)
                .map(InscricaoResponsavelEmail::getEmail)
                .findFirst()
                .orElse(null);
        if (emailPrincipal != null) {
            return pessoaRepository.findResponsavelPorEmailPrincipal(PessoaPapel.RESPONSAVEL, emailPrincipal)
                    .orElseGet(() -> criarPessoaResponsavel(ir));
        }
        return criarPessoaResponsavel(ir);
    }

    private Pessoa criarPessoaResponsavel(InscricaoResponsavel ir) {
        Pessoa pessoa = new Pessoa(PessoaPapel.RESPONSAVEL, ir.getNome());
        for (InscricaoResponsavelEmail e : ir.getEmails()) {
            PessoaEmail linha = new PessoaEmail(e.getTipo(), e.getEmail(), e.isPrincipal());
            linha.setPessoa(pessoa);
            pessoa.getEmails().add(linha);
        }
        for (InscricaoResponsavelTelefone t : ir.getTelefones()) {
            PessoaTelefone linha = new PessoaTelefone(t.getTipo(), t.getNumero(), t.isPrincipal());
            linha.setPessoa(pessoa);
            pessoa.getTelefones().add(linha);
        }
        return pessoaRepository.saveAndFlush(pessoa);
    }

    private void exigirPendente(Inscricao inscricao) {
        if (inscricao.getStatus() != StatusInscricao.PENDENTE) {
            throw new ConflictException(
                    "Esta inscrição já foi " + (inscricao.getStatus() == StatusInscricao.APROVADA ? "aprovada" : "rejeitada")
                            + " — não pode mais ser alterada.");
        }
    }

    private void validarContatos(List<ContatoEmailRequest> emails, List<ContatoTelefoneRequest> telefones,
                                 List<InscricaoResponsavelRequest> responsaveis) {
        Contatos.exigirUmPrincipalEmail(emails);
        Contatos.exigirUmPrincipalTelefone(telefones);
        long principais = responsaveis.stream().filter(InscricaoResponsavelRequest::principal).count();
        if (principais != 1) {
            throw new BadRequestException(
                    "Deve existir exatamente um responsável principal (seção 38 do plano mestre) — recebido: "
                            + principais + ".");
        }
        for (InscricaoResponsavelRequest r : responsaveis) {
            Contatos.exigirUmPrincipalEmail(r.emails());
            Contatos.exigirUmPrincipalTelefone(r.telefones());
        }
    }

    private void aplicarCampos(Inscricao inscricao, LocalDate dataNascimento, String sexo, String cpf, String rg,
                                TipoVoluntario tipo, String etapaCatequese, String eucaristiaAno, String crismaAno,
                                String cep, String cidade, String uf, String rua, String numero, String complemento,
                                String bairro, Voluntario.HorarioEstudo horarioEstudo, String observacoes,
                                boolean autorizaWhatsapp, List<FuncaoEscala> funcoesHabilitadas) {
        inscricao.setDataNascimento(dataNascimento);
        inscricao.setSexo(opcional(sexo));
        inscricao.setCpf(opcional(cpf));
        inscricao.setRg(opcional(rg));
        inscricao.setTipo(tipo);
        inscricao.setEtapaCatequese(opcional(etapaCatequese));
        inscricao.setEucaristiaAno(opcional(eucaristiaAno));
        inscricao.setCrismaAno(opcional(crismaAno));
        inscricao.setCep(opcional(cep));
        inscricao.setCidade(opcional(cidade));
        inscricao.setUf(opcional(uf));
        inscricao.setRua(opcional(rua));
        inscricao.setNumero(opcional(numero));
        inscricao.setComplemento(opcional(complemento));
        inscricao.setBairro(opcional(bairro));
        inscricao.setHorarioEstudo(horarioEstudo);
        inscricao.setObservacoes(opcional(observacoes));
        inscricao.setAutorizaWhatsapp(autorizaWhatsapp);
        inscricao.setFuncoesHabilitadas(funcoesHabilitadas == null
                ? new FuncaoEscala[0]
                : funcoesHabilitadas.toArray(new FuncaoEscala[0]));
    }

    private void substituirContatos(Inscricao inscricao, List<ContatoEmailRequest> emails,
                                    List<ContatoTelefoneRequest> telefones) {
        inscricao.getEmails().clear();
        inscricao.getTelefones().clear();
        entityManager.flush();
        if (emails != null) {
            for (ContatoEmailRequest e : emails) {
                InscricaoEmail linha = new InscricaoEmail(e.tipo().trim(), e.email().trim(), e.principal());
                linha.setInscricao(inscricao);
                inscricao.getEmails().add(linha);
            }
        }
        if (telefones != null) {
            for (ContatoTelefoneRequest t : telefones) {
                InscricaoTelefone linha = new InscricaoTelefone(t.tipo().trim(), t.numero().trim(), t.principal());
                linha.setInscricao(inscricao);
                inscricao.getTelefones().add(linha);
            }
        }
    }

    private void substituirResponsaveis(Inscricao inscricao, List<InscricaoResponsavelRequest> requests) {
        inscricao.getResponsaveis().clear();
        entityManager.flush();
        for (InscricaoResponsavelRequest r : requests) {
            InscricaoResponsavel responsavel = new InscricaoResponsavel(r.parentesco().trim(), r.nome().trim(),
                    r.principal());
            responsavel.setParentescoInverso(opcional(r.parentescoInverso()));
            responsavel.setInscricao(inscricao);
            if (r.emails() != null) {
                for (ContatoEmailRequest e : r.emails()) {
                    InscricaoResponsavelEmail linha = new InscricaoResponsavelEmail(
                            e.tipo().trim(), e.email().trim(), e.principal());
                    linha.setResponsavel(responsavel);
                    responsavel.getEmails().add(linha);
                }
            }
            if (r.telefones() != null) {
                for (ContatoTelefoneRequest t : r.telefones()) {
                    InscricaoResponsavelTelefone linha = new InscricaoResponsavelTelefone(
                            t.tipo().trim(), t.numero().trim(), t.principal());
                    linha.setResponsavel(responsavel);
                    responsavel.getTelefones().add(linha);
                }
            }
            inscricao.getResponsaveis().add(responsavel);
        }
    }

    private static void copiarEmailsParaPessoa(Pessoa pessoa, List<InscricaoEmail> emails) {
        for (InscricaoEmail e : emails) {
            PessoaEmail linha = new PessoaEmail(e.getTipo(), e.getEmail(), e.isPrincipal());
            linha.setPessoa(pessoa);
            pessoa.getEmails().add(linha);
        }
    }

    private static void copiarTelefonesParaPessoa(Pessoa pessoa, List<InscricaoTelefone> telefones) {
        for (InscricaoTelefone t : telefones) {
            PessoaTelefone linha = new PessoaTelefone(t.getTipo(), t.getNumero(), t.isPrincipal());
            linha.setPessoa(pessoa);
            pessoa.getTelefones().add(linha);
        }
    }

    private static String opcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private String extensaoDe(MultipartFile foto) {
        String nomeOriginal = foto.getOriginalFilename();
        if (nomeOriginal != null && nomeOriginal.contains(".")) {
            return nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
        }
        return switch (String.valueOf(foto.getContentType())) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            default -> ".jpg";
        };
    }
}
