package br.com.servire.api.comunicacao;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.AnexoLinha;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.CriarRequest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Criado;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.DestinatarioPrevia;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.DestinatarioLinha;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.DestinatariosRequest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Destino;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Detalhe;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.PreVisualizacao;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.PreVisualizarRequest;
import br.com.servire.api.comunicacao.dto.ComunicadoDtos.Resumo;
import br.com.servire.api.pessoa.Pessoa;
import br.com.servire.api.pessoa.PessoaEmail;
import br.com.servire.api.pessoa.PessoaRelacao;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.pessoa.PessoaTelefone;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantEmail;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.tenant.TenantTelefone;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ZipSeguro;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comunicado por e-mail ou WhatsApp (PLANO-005): monta os destinatários a partir das pessoas marcadas,
 * renderiza cada mensagem com o {@link Renderizador} e grava tudo na fila, que a {@link FilaDeEnvio} processa.
 */
@Service
public class ComunicadoService {

    static final int MAX_ANEXOS = 5;
    static final long MAX_BYTES_ANEXOS = 10L * 1024 * 1024;
    static final Set<String> TIPOS_ANEXO = Set.of(
            "application/pdf", "image/jpeg", "image/png",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ComunicadoRepository comunicados;
    private final ComunicadoDestinatarioRepository destinatarios;
    private final ComunicadoAnexoRepository anexos;
    private final LayoutRepository layouts;
    private final PessoaRepository pessoas;
    private final TenantRepository tenants;
    private final ParoquiaWhatsappService whatsapp;
    private final AuditLogService auditLogService;
    private final br.com.servire.api.minhaconta.CotasService cotas;

    public ComunicadoService(ComunicadoRepository comunicados, ComunicadoDestinatarioRepository destinatarios,
                             ComunicadoAnexoRepository anexos, LayoutRepository layouts, PessoaRepository pessoas,
                             TenantRepository tenants, ParoquiaWhatsappService whatsapp, AuditLogService auditLogService, br.com.servire.api.minhaconta.CotasService cotas) {
        this.comunicados = comunicados;
        this.destinatarios = destinatarios;
        this.anexos = anexos;
        this.layouts = layouts;
        this.pessoas = pessoas;
        this.tenants = tenants;
        this.whatsapp = whatsapp;
        this.auditLogService = auditLogService; this.cotas = cotas;
    }

    /** Um endereço de envio com o dono (a pessoa marcada ou um responsável dela). */
    record DestinoInterno(Pessoa dono, String endereco, boolean doResponsavel) {
    }

    @Transactional(readOnly = true)
    public List<DestinatarioPrevia> destinatarios(DestinatariosRequest req) {
        return carregar(req.pessoaIds()).stream().map(p -> new DestinatarioPrevia(
                p.getId(), p.getNomeCompleto(),
                destinos(p, req.canal(), req.enviarPara(), req.contatos()).stream()
                        .map(d -> new Destino(d.dono().getNomeCompleto(), d.endereco(), d.doResponsavel() ? "RESPONSAVEL" : "PESSOA"))
                        .toList(),
                p.getVoluntario() == null ? null : p.getVoluntario().isAutorizaWhatsapp())).toList();
    }

    @Transactional(readOnly = true)
    public PreVisualizacao preVisualizar(PreVisualizarRequest req) {
        Layout layout = layouts.findById(req.layoutId())
                .orElseThrow(() -> new ResourceNotFoundException("Layout não encontrado."));
        if (layout.getTipoLayout() == TipoLayout.EVENTO) {
            throw new BadRequestException("Layout de evento só é usado nos eventos.");
        }
        Pessoa pessoa = pessoas.findById(req.pessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));
        Tenant tenant = tenantAtual();
        List<DestinoInterno> ds = destinos(pessoa, layout.getTipoEnvio(), req.enviarPara(), QuaisContatos.PRINCIPAL);
        DestinoInterno d = ds.isEmpty() ? null : ds.getFirst();
        ContextoDeEnvio ctx = contexto(tenant, pessoa, d);
        String assunto = layout.getTipoEnvio() == TipoEnvio.EMAIL
                ? Renderizador.renderizar(assuntoOuSugerido(req.assunto(), layout), TipoEnvio.WHATSAPP, ctx)
                : null;
        String de = layout.getTipoEnvio() == TipoEnvio.EMAIL
                ? tenant.getNome() + emailPrincipal(tenant).map(e -> " <" + e + ">").orElse("")
                : tenant.getNome();
        String para = d == null ? "(sem contato para este canal)" : d.dono().getNomeCompleto() + " <" + d.endereco() + ">";
        return new PreVisualizacao(de, para, assunto, Renderizador.renderizar(layout.getConteudo(), layout.getTipoEnvio(), ctx));
    }

    @Transactional
    public Criado criar(CriarRequest req, List<MultipartFile> arquivos) {
        var reserva=cotas.reservar();
        Layout layout = layouts.findById(req.layoutId())
                .orElseThrow(() -> new ResourceNotFoundException("Layout não encontrado."));
        if (layout.getTipoLayout() == TipoLayout.EVENTO) {
            throw new BadRequestException("Layout de evento só é usado nos eventos.");
        }
        if (!layout.isAtivo() || layout.getTipoEnvio() != req.canal()) {
            throw new BadRequestException("Escolha um layout ativo de " + (req.canal() == TipoEnvio.EMAIL ? "e-mail." : "WhatsApp."));
        }
        List<MultipartFile> lista = arquivos == null ? List.of() : arquivos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        String assunto = null;
        if (req.canal() == TipoEnvio.EMAIL) {
            assunto = assuntoOuSugerido(req.assunto(), layout);
            if (assunto == null || assunto.isBlank()) throw new BadRequestException("Informe o assunto do e-mail.");
            validarAnexos(lista);
        } else {
            if (!lista.isEmpty()) throw new BadRequestException("Anexos só no e-mail.");
            if (whatsapp.ativa().isEmpty()) throw new BadRequestException("Configure o WhatsApp da paróquia antes de enviar.");
        }

        Tenant tenant = tenantAtual();
        Comunicado comunicado = comunicados.save(new Comunicado(req.canal(), layout, assunto, req.enviarPara(), usuarioAtual()));
        List<ComunicadoDestinatario> linhas = new ArrayList<>();
        for (Pessoa pessoa : carregar(req.pessoaIds())) {
            for (DestinoInterno d : destinos(pessoa, req.canal(), req.enviarPara(), req.contatos())) {
                ContextoDeEnvio ctx = contexto(tenant, pessoa, d);
                String assuntoFinal = assunto == null ? null : limitar(Renderizador.renderizar(assunto, TipoEnvio.WHATSAPP, ctx), 200);
                linhas.add(new ComunicadoDestinatario(comunicado.getId(), pessoa.getId(), limitar(d.dono().getNomeCompleto(), 200),
                        d.endereco(), assuntoFinal, Renderizador.renderizar(layout.getConteudo(), req.canal(), ctx)));
            }
        }
        if (linhas.isEmpty()) throw new BadRequestException("Nenhum dos selecionados tem contato para este canal.");
        destinatarios.saveAll(linhas);
        if (!lista.isEmpty()) cotas.validarUpload(reserva,lista.stream().mapToLong(MultipartFile::getSize).sum(),null);
        for (MultipartFile f : lista) {
            anexos.save(new ComunicadoAnexo(comunicado.getId(), nomeArquivo(f), f.getContentType(), bytes(f)));
        }
        cotas.validar(reserva);
        comunicado.setTotal(linhas.size());
        auditLogService.registrar("ENVIO", "COMUNICADO", comunicado.getId(), List.of());
        return new Criado(comunicado.getId(), linhas.size());
    }

    @Transactional(readOnly = true)
    public List<Resumo> listar(TipoEnvio canal, StatusComunicado status) {
        Specification<Comunicado> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (canal != null) ps.add(cb.equal(root.get("canal"), canal));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return comunicados.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(Resumo::de).toList();
    }

    @Transactional(readOnly = true)
    public Detalhe detalhe(UUID id) {
        Comunicado c = buscar(id);
        return new Detalhe(Resumo.de(c),
                destinatarios.findByComunicadoIdOrderByNome(id).stream().map(DestinatarioLinha::de).toList(),
                anexos.findByComunicadoIdOrderByNome(id).stream().map(AnexoLinha::de).toList());
    }

    @Transactional
    public Resumo reenviarFalhas(UUID id) {
        Comunicado c = buscar(id);
        List<ComunicadoDestinatario> falhas = destinatarios.findByComunicadoIdAndStatus(id, StatusEnvio.FALHA);
        if (falhas.isEmpty()) return Resumo.de(c);
        falhas.forEach(ComunicadoDestinatario::voltarParaFila);
        c.contar(c.getEnviados(), 0);
        c.voltarParaFila();
        auditLogService.registrar("REENVIAR", "COMUNICADO", id, List.of("falhas"));
        return Resumo.de(c);
    }

    private Comunicado buscar(UUID id) {
        return comunicados.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comunicado não encontrado."));
    }

    // ---- Montagem dos destinatários

    private List<Pessoa> carregar(List<UUID> ids) {
        Map<UUID, Pessoa> porId = pessoas.findAllById(ids).stream().collect(Collectors.toMap(Pessoa::getId, Function.identity()));
        return new LinkedHashSet<>(ids).stream().map(porId::get).filter(p -> p != null).toList();
    }

    List<DestinoInterno> destinos(Pessoa pessoa, TipoEnvio canal, EnviarPara para, QuaisContatos quais) {
        List<Pessoa> donos = new ArrayList<>();
        List<Pessoa> responsaveis = pessoa.getResponsaveis().stream()
                .sorted(Comparator.comparing((PessoaRelacao r) -> !r.isPrincipal()))
                .map(PessoaRelacao::getResponsavel).toList();
        if (para == EnviarPara.PESSOA || para == EnviarPara.AMBOS) donos.add(pessoa);
        if (para == EnviarPara.RESPONSAVEIS || para == EnviarPara.AMBOS) {
            if (responsaveis.isEmpty() && pessoa.isResponsavel()) {
                if (!donos.contains(pessoa)) donos.add(pessoa);
            } else {
                donos.addAll(responsaveis);
            }
        }
        List<DestinoInterno> resultado = new ArrayList<>();
        Set<String> vistos = new java.util.HashSet<>();
        for (Pessoa dono : donos) {
            for (String endereco : enderecos(dono, canal, quais)) {
                String chave = canal == TipoEnvio.EMAIL ? endereco.toLowerCase() : endereco.replaceAll("\\D", "");
                if (vistos.add(chave)) resultado.add(new DestinoInterno(dono, endereco, dono != pessoa));
            }
        }
        return resultado;
    }

    private static List<String> enderecos(Pessoa dono, TipoEnvio canal, QuaisContatos quais) {
        if (canal == TipoEnvio.EMAIL) {
            List<PessoaEmail> lista = dono.getEmails().stream().filter(e -> e.getEmail() != null && !e.getEmail().isBlank())
                    .sorted(Comparator.comparing((PessoaEmail e) -> !e.isPrincipal())).toList();
            return (quais == QuaisContatos.PRINCIPAL ? lista.stream().limit(1) : lista.stream()).map(e -> e.getEmail().trim()).toList();
        }
        List<PessoaTelefone> lista = dono.getTelefones().stream().filter(t -> t.getNumero() != null && !t.getNumero().isBlank())
                .sorted(Comparator.comparing((PessoaTelefone t) -> !t.isPrincipal())).toList();
        return (quais == QuaisContatos.PRINCIPAL ? lista.stream().limit(1) : lista.stream()).map(t -> t.getNumero().trim()).toList();
    }

    // ---- Renderização

    /**
     * {@code PESSOA.*} = a pessoa marcada; {@code RESPONSAVEL.*} = o dono do contato quando o envio é para o
     * responsável, senão o responsável principal; {@code DEPENDENTES.NOMES} = dependentes do dono do contato.
     */
    ContextoDeEnvio contexto(Tenant tenant, Pessoa pessoa, DestinoInterno destino) {
        Pessoa dono = destino == null ? pessoa : destino.dono();
        Pessoa responsavel = destino != null && destino.doResponsavel() ? dono : responsavelPrincipal(pessoa);
        Voluntario v = pessoa.getVoluntario();
        return new ContextoDeEnvio(
                tenant.getNome(), tenant.getCidade(), tenant.getUf(),
                emailPrincipal(tenant).orElse(null), telefonePrincipal(tenant),
                pessoa.getNomeCompleto(),
                pessoa.getSequencial() == null ? null : String.valueOf(pessoa.getSequencial()),
                pessoa.getDataNascimento(),
                v == null ? null : rotuloTipo(v),
                enderecos(pessoa, TipoEnvio.EMAIL, QuaisContatos.PRINCIPAL).stream().findFirst().orElse(null),
                enderecos(pessoa, TipoEnvio.WHATSAPP, QuaisContatos.PRINCIPAL).stream().findFirst().orElse(null),
                v == null || v.getMandatoFim() == null ? null : v.getMandatoFim().format(DATA),
                responsavel == null ? null : responsavel.getNomeCompleto(),
                dono.getDependentes().stream().map(r -> r.getVoluntario().getNomeCompleto()).toList());
    }

    /** Contexto de uma pessoa para mensagens que outros módulos montam com layout (ex.: eventos). */
    public ContextoDeEnvio contextoDa(Tenant tenant, Pessoa pessoa) {
        return contexto(tenant, pessoa, null);
    }

    private static Pessoa responsavelPrincipal(Pessoa pessoa) {
        return pessoa.getResponsaveis().stream()
                .sorted(Comparator.comparing((PessoaRelacao r) -> !r.isPrincipal()))
                .map(PessoaRelacao::getResponsavel).findFirst().orElse(null);
    }

    private static String rotuloTipo(Voluntario v) {
        return switch (v.getTipo()) {
            case COROINHA -> "Coroinha";
            case ACOLITO -> "Acólito";
            case AMBOS -> "Coroinha e acólito";
            case MESC -> "Ministro";
        };
    }

    private Tenant tenantAtual() {
        return tenants.findById(TenantContext.get()).orElseThrow(() -> new ResourceNotFoundException("Paróquia não encontrada."));
    }

    static java.util.Optional<String> emailPrincipal(Tenant tenant) {
        return tenant.getEmails().stream().sorted(Comparator.comparing((TenantEmail e) -> !e.isPrincipal()))
                .map(TenantEmail::getEmail).filter(e -> e != null && !e.isBlank()).findFirst();
    }

    private static String telefonePrincipal(Tenant tenant) {
        return tenant.getTelefones().stream().sorted(Comparator.comparing((TenantTelefone t) -> !t.isPrincipal()))
                .map(TenantTelefone::getNumero).findFirst().orElse(null);
    }

    /** O assunto é texto puro (não é HTML), por isso é renderizado sem escapar. */
    private static String assuntoOuSugerido(String assunto, Layout layout) {
        return assunto != null && !assunto.isBlank() ? assunto.trim() : layout.getAssunto();
    }

    // ---- Anexos

    private static void validarAnexos(List<MultipartFile> lista) {
        if (lista.size() > MAX_ANEXOS) throw new BadRequestException("No máximo " + MAX_ANEXOS + " anexos.");
        long soma = 0;
        for (MultipartFile f : lista) {
            if (!TIPOS_ANEXO.contains(f.getContentType())) {
                throw new BadRequestException("Tipo de anexo não aceito: " + nomeArquivo(f) + ". Use PDF, JPG, PNG, DOCX ou XLSX.");
            }
            byte[] conteudo = bytes(f);
            if (ehImagem(f.getContentType()) && ZipSeguro.pareceZip(conteudo)) {
                throw new BadRequestException("Tipo de anexo não aceito: " + nomeArquivo(f) + ". Use PDF, JPG, PNG, DOCX ou XLSX.");
            }
            if (ehOffice(f.getContentType())) {
                ZipSeguro.verificar(conteudo);
            }
            soma += f.getSize();
        }
        if (soma > MAX_BYTES_ANEXOS) throw new BadRequestException("Os anexos passam de 10 MB somados.");
    }

    private static boolean ehImagem(String tipo) {
        return "image/jpeg".equals(tipo) || "image/png".equals(tipo);
    }

    private static boolean ehOffice(String tipo) {
        return tipo != null && (tipo.endsWith("wordprocessingml.document") || tipo.endsWith("spreadsheetml.sheet"));
    }

    private static String nomeArquivo(MultipartFile f) {
        String nome = f.getOriginalFilename() == null || f.getOriginalFilename().isBlank() ? "anexo" : f.getOriginalFilename();
        return limitar(nome.replaceAll("[\\\\/]", "_"), 200);
    }

    private static byte[] bytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String limitar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static UUID usuarioAtual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthenticatedUser u ? u.usuarioId() : null;
    }
}
