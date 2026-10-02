package br.com.servire.api.pessoa;

import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.pessoa.dto.ContatoEmailRequest;
import br.com.servire.api.pessoa.dto.ContatoTelefoneRequest;
import br.com.servire.api.pessoa.dto.NovaPessoaRequest;
import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.pessoa.dto.RelacaoRequest;
import br.com.servire.api.pessoa.dto.VoluntarioPerfilRequest;
import br.com.servire.api.voluntario.FuncaoEscala;
import br.com.servire.api.voluntario.Voluntario;
import br.com.servire.api.web.BadRequestException;
import br.com.servire.api.web.ConflictException;
import br.com.servire.api.web.Formatos;
import br.com.servire.api.web.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro pessoa-primeiro. Papéis não são exclusivos e podem ser
 * acrescentados (nunca removidos). Relação responsável↔voluntário é
 * opcional — adulto/ministro entra sem responsável.
 */
@Service
public class PessoaService {

    /** Coluna {@code parentesco} é NOT NULL; usado quando a tela do responsável não diz o que ele é. */
    static final String RESPONSAVEL_SEM_ROTULO = "Responsável";

    private final PessoaRepository pessoaRepository;
    private final AuditLogService auditLogService;
    private final br.com.servire.api.minhaconta.CotasService cotas;
    private final br.com.servire.api.privacidade.ConsentimentoService consentimentos;

    @PersistenceContext
    private EntityManager entityManager;

    public PessoaService(PessoaRepository pessoaRepository, AuditLogService auditLogService, br.com.servire.api.minhaconta.CotasService cotas,
                         br.com.servire.api.privacidade.ConsentimentoService consentimentos) {
        this.pessoaRepository = pessoaRepository;
        this.auditLogService = auditLogService;
        this.cotas = cotas;
        this.consentimentos = consentimentos;
    }

    @Transactional(readOnly = true)
    public List<Pessoa> buscar(PessoaPapel papel, String nome) {
        List<Pessoa> pessoas = pessoaRepository.findAll(filtro(papel, nome), Sort.by(Sort.Direction.ASC, "nomeCompleto"));
        pessoas.forEach(PessoaService::inicializarFicha);
        return pessoas;
    }


    public record Resumo(long pessoas,long ativos,long inativos,long pendentes) {}
    public record Opcao(UUID id,Long sequencial,String nomeCompleto,boolean voluntario,boolean responsavel) {}

    /** Só a página inicializa a ficha, com dados de cuidados sujeitos às permissões da sessão. */
    @Transactional(readOnly = true)
    public br.com.servire.api.web.PaginaLista<br.com.servire.api.pessoa.dto.PessoaResponse> pagina(
        PessoaPapel papel,String nome,br.com.servire.api.voluntario.TipoVoluntario tipo,Boolean ativo,int pagina,int tamanho) {
        Specification<Pessoa> spec=filtro(papel,nome);
        if (tipo!=null) spec=spec.and((root,q,cb) -> cb.equal(root.get("voluntario").get("tipo"),tipo));
        if (ativo!=null) spec=spec.and((root,q,cb) -> cb.equal(root.get("voluntario").get("ativo"),ativo));
        var resultado=pessoaRepository.findAll(spec,br.com.servire.api.web.PaginaLista.pedido(pagina,tamanho,Sort.by("nomeCompleto","id")));
        return br.com.servire.api.web.PaginaLista.de(resultado.map(p -> br.com.servire.api.pessoa.dto.PessoaResponse.deAutorizada(inicializarFicha(p))));
    }
    /** Contagens agregadas, sem carregar pessoas/inscrições nem incluir informação de saúde. */
    @Transactional(readOnly = true)
    public Resumo resumo() {
        Object[] linha=entityManager.createQuery("""
            select count(p),coalesce(sum(case when v.ativo=true then 1 else 0 end),0),
                coalesce(sum(case when v.ativo=false then 1 else 0 end),0)
            from Pessoa p left join p.voluntario v
            """,Object[].class).getSingleResult();
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean inscricoes=auth==null || auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("PERM_INSCRICAO"));
        long pendentes=inscricoes ? entityManager.createQuery("select count(i) from Inscricao i where i.status=:status",Long.class)
            .setParameter("status",br.com.servire.api.inscricao.StatusInscricao.PENDENTE).getSingleResult() : 0;
        return new Resumo(((Number)linha[0]).longValue(),((Number)linha[1]).longValue(),((Number)linha[2]).longValue(),pendentes);
    }
    /** Seletor enxuto, ordenado e limitado: não retorna contatos, endereço nem cuidados. */
    @Transactional(readOnly = true)
    public List<Opcao> opcoes(PessoaPapel papel,String nome,int limite) {
        br.com.servire.api.web.PaginaLista.pedido(0,limite,Sort.unsorted());
        var cb=entityManager.getCriteriaBuilder();var q=cb.createQuery(Opcao.class);var root=q.from(Pessoa.class);
        q.select(cb.construct(Opcao.class,root.get("id"),root.get("sequencial"),root.get("nomeCompleto"),root.get("eVoluntario"),root.get("eResponsavel")))
            .where(filtro(papel,nome).toPredicate(root,q,cb)).orderBy(cb.asc(root.get("nomeCompleto")),cb.asc(root.get("id")));
        return entityManager.createQuery(q).setMaxResults(limite).getResultList();
    }

    private static Specification<Pessoa> filtro(PessoaPapel papel, String nome) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (papel == PessoaPapel.VOLUNTARIO) {
                predicados.add(cb.isTrue(root.get("eVoluntario")));
            } else if (papel == PessoaPapel.RESPONSAVEL) {
                predicados.add(cb.isTrue(root.get("eResponsavel")));
            }
            if (nome != null && !nome.isBlank()) {
                String texto = nome.trim();
                Predicate porNome = cb.like(cb.lower(root.get("nomeCompleto")), "%" + texto.toLowerCase(java.util.Locale.ROOT) + "%");
                // Número curto da pessoa (V038) também encontra.
                predicados.add(texto.matches("\\d{1,18}")
                        ? cb.or(porNome, cb.equal(root.get("sequencial"), Long.valueOf(texto)))
                        : porNome);
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    /**
     * Ficha completa para {@code PessoaResponse}: inicializa contatos,
     * relações e o nome/papéis da outra ponta de cada relação ainda dentro
     * da transação ({@code open-in-view: false}).
     */
    @Transactional(readOnly = true)
    public Pessoa buscarPorId(UUID id) {
        return inicializarFicha(carregar(id));
    }

    /**
     * Tudo que {@code PessoaResponse.de} lê. A listagem também precisa: o
     * {@code GET /pessoas} estourava {@code LazyInitializationException}
     * (500) assim que existia qualquer pessoa, porque só e-mails/telefones
     * eram tocados (bug de 24/09/2026, achado no teste de ponta a ponta).
     * O {@code default_batch_fetch_size} junta as coleções em poucos SELECTs.
     */
    private static Pessoa inicializarFicha(Pessoa pessoa) {
        Hibernate.initialize(pessoa.getEmails());
        Hibernate.initialize(pessoa.getTelefones());
        pessoa.getResponsaveis().forEach(r -> Hibernate.initialize(r.getResponsavel()));
        pessoa.getDependentes().forEach(r -> Hibernate.initialize(r.getVoluntario()));
        return pessoa;
    }

    private Pessoa carregar(UUID id) {
        return pessoaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));
    }

    @Transactional
    public Pessoa criar(PessoaRequest request) {
        var reserva = cotas.reservar();
        Set<PessoaPapel> papeis = papeisDe(request);
        validarRequest(request, papeis);
        Pessoa pessoa = new Pessoa(papeis, request.nomeCompleto().trim());
        aplicarIdentidade(pessoa, request);
        substituirContatos(pessoa, request.emails(), request.telefones());
        if (pessoa.isVoluntario()) {
            aplicarPerfilVoluntario(pessoa, request.voluntario());
        }
        substituirRelacoes(pessoa, request);
        pessoa = pessoaRepository.save(pessoa);
        cotas.validar(reserva);
        auditLogService.registrar("CRIACAO", "PESSOA", pessoa.getId(), null);
        if (pessoa.isVoluntario() && pessoa.getVoluntario().isAutorizaWhatsapp()) {
            consentimentos.registrar(pessoa.getId(), br.com.servire.api.privacidade.Privacidade.TipoConsentimento.WHATSAPP, true, "Cadastro pela coordenação");
        }
        return buscarPorId(pessoa.getId());
    }

    @Transactional
    public Pessoa atualizar(UUID id, PessoaRequest request) {
        var reserva = cotas.reservar();
        Pessoa pessoa = carregar(id);
        Set<PessoaPapel> novos = papeisDe(request);
        if (pessoa.isVoluntario() && !novos.contains(PessoaPapel.VOLUNTARIO)) {
            throw new BadRequestException("Não é possível remover o papel VOLUNTARIO.");
        }
        if (pessoa.isResponsavel() && !novos.contains(PessoaPapel.RESPONSAVEL)) {
            throw new BadRequestException("Não é possível remover o papel RESPONSAVEL.");
        }
        validarRequest(request, novos);
        boolean whatsappAntes = pessoa.getVoluntario() != null && pessoa.getVoluntario().isAutorizaWhatsapp();
        pessoa.setPapeis(novos);
        List<String> alterados = new ArrayList<>(List.of("identidade", "contatos", "responsaveis", "dependentes", "papeis"));
        CondicaoEspecial[] antigas = pessoa.getCondicoes();
        CondicaoEspecial[] novas = request.condicoes() == null ? new CondicaoEspecial[0] : request.condicoes().toArray(new CondicaoEspecial[0]);
        if (!java.util.Arrays.equals(antigas, novas)) {
            alterados.add("condicoes");
        }
        
        aplicarIdentidade(pessoa, request);
        substituirContatos(pessoa, request.emails(), request.telefones());
        if (pessoa.isVoluntario()) {
            aplicarPerfilVoluntario(pessoa, request.voluntario());
        }
        substituirRelacoes(pessoa, request);
        pessoaRepository.save(pessoa);
        cotas.validar(reserva);
        auditLogService.registrar("ATUALIZACAO", "PESSOA", id, alterados);
        boolean whatsappDepois = pessoa.getVoluntario() != null && pessoa.getVoluntario().isAutorizaWhatsapp();
        if (whatsappAntes != whatsappDepois) {
            consentimentos.registrar(id, br.com.servire.api.privacidade.Privacidade.TipoConsentimento.WHATSAPP, whatsappDepois, "Edição do cadastro pela coordenação");
        }
        return buscarPorId(id);
    }

    private static Set<PessoaPapel> papeisDe(PessoaRequest request) {
        if (request.papeis() == null || request.papeis().isEmpty()) {
            throw new BadRequestException("Informe ao menos um papel (VOLUNTARIO e/ou RESPONSAVEL).");
        }
        return EnumSet.copyOf(request.papeis());
    }

    private void validarRequest(PessoaRequest request, Set<PessoaPapel> papeis) {
        Contatos.exigirUmPrincipalEmail(request.emails());
        Contatos.exigirUmPrincipalTelefone(request.telefones());
        if (papeis.contains(PessoaPapel.VOLUNTARIO) && request.voluntario() == null) {
            throw new BadRequestException("O bloco de voluntário é obrigatório quando o papel inclui VOLUNTARIO.");
        }
        List<RelacaoRequest> responsaveis = lista(request.responsaveis());
        List<RelacaoRequest> dependentes = lista(request.dependentes());
        if (!responsaveis.isEmpty() && !papeis.contains(PessoaPapel.VOLUNTARIO)) {
            throw new BadRequestException("Só quem tem o papel VOLUNTARIO pode ter responsáveis.");
        }
        if (!dependentes.isEmpty() && !papeis.contains(PessoaPapel.RESPONSAVEL)) {
            throw new BadRequestException("Só quem tem o papel RESPONSAVEL pode ter dependentes.");
        }
        validarPrincipaisDosResponsaveis(responsaveis);
        Set<UUID> idsResponsaveis = idsSemRepeticao(responsaveis, "responsável");
        Set<UUID> idsDependentes = idsSemRepeticao(dependentes, "dependente");
        if (idsResponsaveis.stream().anyMatch(idsDependentes::contains)) {
            throw new BadRequestException("A mesma pessoa não pode ser responsável e dependente ao mesmo tempo.");
        }
        for (RelacaoRequest r : responsaveis) {
            exigirPessoaOuNova(r);
        }
        for (RelacaoRequest r : dependentes) {
            if (r.pessoaId() == null) {
                throw new BadRequestException(
                        "Escolha um dependente já cadastrado — o voluntário novo precisa do próprio cadastro.");
            }
        }
    }

    private static void exigirPessoaOuNova(RelacaoRequest r) {
        if ((r.pessoaId() == null) == (r.novaPessoa() == null)) {
            throw new BadRequestException(
                    "Cada responsável precisa ou de uma pessoa já cadastrada ou dos dados de uma pessoa nova.");
        }
    }

    /**
     * Responsável é opcional. Se vier algum, no máximo um principal — e se
     * houver mais de um, exige exatamente um principal (contato da família).
     */
    private static void validarPrincipaisDosResponsaveis(List<RelacaoRequest> responsaveis) {
        if (responsaveis.isEmpty()) {
            return;
        }
        long principais = responsaveis.stream().filter(RelacaoRequest::principal).count();
        if (principais > 1) {
            throw new BadRequestException(
                    "Deve existir no máximo um responsável principal — recebido: " + principais + ".");
        }
        if (responsaveis.size() > 1 && principais != 1) {
            throw new BadRequestException(
                    "Quando há mais de um responsável, deve existir exatamente um principal — recebido: "
                            + principais + ".");
        }
    }

    /** Par repetido bateria em {@code pessoa_relacao_par_key} (500) — vira 400 aqui. */
    private static Set<UUID> idsSemRepeticao(List<RelacaoRequest> relacoes, String rotulo) {
        Set<UUID> ids = new HashSet<>();
        for (RelacaoRequest r : relacoes) {
            if (r.pessoaId() != null && !ids.add(r.pessoaId())) {
                throw new BadRequestException("A mesma pessoa aparece mais de uma vez como " + rotulo + ".");
            }
        }
        return ids;
    }

    private static void validarMandato(LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new BadRequestException("O vencimento do mandato não pode ser anterior à investidura.");
        }
    }

    private static <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }

    private void aplicarIdentidade(Pessoa pessoa, PessoaRequest request) {
        pessoa.setNomeCompleto(request.nomeCompleto().trim());
        pessoa.setDataNascimento(request.dataNascimento());
        pessoa.setSexo(Formatos.sexo(request.sexo()));
        String cpfFormatado = Formatos.cpf(request.cpf());
        if (cpfFormatado != null && !cpfFormatado.isBlank()) {
            pessoaRepository.findByCpf(cpfFormatado).ifPresent(p -> {
                if (!p.getId().equals(pessoa.getId())) {
                    throw new ConflictException("Já existe um cadastro com este CPF: " + p.getNomeCompleto() + " (" + p.getSequencial() + ").");
                }
            });
        }
        pessoa.setCpf(cpfFormatado);
        pessoa.setRg(Formatos.rg(request.rg()));
        pessoa.setCep(Formatos.cep(request.cep()));
        pessoa.setCidade(opcional(request.cidade()));
        pessoa.setUf(Formatos.uf(request.uf()));
        pessoa.setLogradouro(opcional(request.logradouro()));
        pessoa.setNumero(opcional(request.numero()));
        pessoa.setComplemento(opcional(request.complemento()));
        pessoa.setBairro(opcional(request.bairro()));
        pessoa.setObservacoes(opcional(request.observacoes()));
        
        if (!br.com.servire.api.acesso.PermissaoCuidados.alterarSePermitido(request.condicoes(), request.nivelSuporteTea(), request.condicaoOutra(), request.cuidados())) return;
        CondicaoEspecial[] condicoes = request.condicoes() == null ? new CondicaoEspecial[0] : request.condicoes().toArray(new CondicaoEspecial[0]);
        Integer[] nivelRef = { request.nivelSuporteTea() };
        String[] outraRef = { request.condicaoOutra() };
        CondicaoEspecial.validar(condicoes, nivelRef, outraRef);
        
        pessoa.setCondicoes(condicoes);
        pessoa.setNivelSuporteTea(nivelRef[0]);
        pessoa.setCondicaoOutra(outraRef[0]);
        pessoa.setCuidados(opcional(request.cuidados()));
    }

    private void aplicarPerfilVoluntario(Pessoa pessoa, VoluntarioPerfilRequest perfil) {
        Voluntario voluntario = pessoa.getVoluntario();
        if (voluntario == null) {
            voluntario = new Voluntario();
            pessoa.setVoluntario(voluntario);
        }
        voluntario.setTipo(perfil.tipo());
        // `ativo` do JSON é ignorado. Novo perfil nasce ativo (default da entidade);
        // ligar/desligar depois só em PATCH /voluntarios/{id}/ativo (PERM_PESSOA_ATIVAR_INATIVAR).
        voluntario.setEtapaCatequese(opcional(perfil.etapaCatequese()));
        voluntario.setEucaristiaAno(opcional(perfil.eucaristiaAno()));
        voluntario.setCrismaAno(opcional(perfil.crismaAno()));
        voluntario.setHorarioEstudo(perfil.horarioEstudo());
        voluntario.setAutorizaWhatsapp(perfil.autorizaWhatsapp());
        validarMandato(perfil.mandatoInicio(), perfil.mandatoFim());
        voluntario.setMandatoInicio(perfil.mandatoInicio());
        voluntario.setMandatoFim(perfil.mandatoFim());
        List<FuncaoEscala> funcoes = perfil.funcoesHabilitadas();
        voluntario.setFuncoesHabilitadas(funcoes == null ? new FuncaoEscala[0] : funcoes.toArray(new FuncaoEscala[0]));
    }

    private void substituirContatos(Pessoa pessoa, List<ContatoEmailRequest> emails,
                                    List<ContatoTelefoneRequest> telefones) {
        pessoa.getEmails().clear();
        pessoa.getTelefones().clear();
        entityManager.flush();
        if (emails != null) {
            for (ContatoEmailRequest e : emails) {
                PessoaEmail linha = new PessoaEmail(e.tipo().trim(), e.email().trim(), e.principal());
                linha.setPessoa(pessoa);
                pessoa.getEmails().add(linha);
            }
        }
        if (telefones != null) {
            for (ContatoTelefoneRequest t : telefones) {
                PessoaTelefone linha = new PessoaTelefone(t.tipo().trim(), Formatos.telefone(t.numero()), t.principal());
                linha.setPessoa(pessoa);
                pessoa.getTelefones().add(linha);
            }
        }
    }

    /**
     * "Apaga tudo e reinsere" de cada lado que a pessoa tem. Quem tem os
     * dois papéis tem as duas listas substituídas, cada uma com a sua
     * direção — ver {@link RelacaoRequest} para o sentido de
     * {@code parentesco}/{@code parentescoInverso}.
     */
    private void substituirRelacoes(Pessoa pessoa, PessoaRequest request) {
        if (pessoa.isVoluntario()) {
            substituirResponsaveis(pessoa, lista(request.responsaveis()));
        }
        if (pessoa.isResponsavel()) {
            substituirDependentes(pessoa, lista(request.dependentes()));
        }
    }

    private void substituirResponsaveis(Pessoa voluntario, List<RelacaoRequest> requests) {
        voluntario.getResponsaveis().clear();
        entityManager.flush();
        for (RelacaoRequest r : requests) {
            Pessoa responsavel = r.pessoaId() != null ? carregar(r.pessoaId()) : criarResponsavelNovo(r.novaPessoa());
            if (!responsavel.isResponsavel()) {
                throw new BadRequestException(
                        responsavel.getNomeCompleto() + " não tem o papel RESPONSAVEL — marque o papel na ficha dele primeiro.");
            }
            if (responsavel.getId().equals(voluntario.getId())) {
                throw new BadRequestException("Uma pessoa não pode se relacionar consigo mesma.");
            }
            PessoaRelacao relacao = new PessoaRelacao(responsavel, voluntario, r.parentesco().trim(),
                    opcional(r.parentescoInverso()), r.principal());
            voluntario.getResponsaveis().add(relacao);
        }
    }

    /**
     * Lado do responsável: o request fala do ponto de vista dele
     * ({@code parentesco} = o que o dependente é), então a coluna
     * {@code parentesco} (o "é" do responsável) recebe o
     * {@code parentescoInverso} do request, e vice-versa.
     *
     * <p>Marcar como principal aqui não pode roubar o posto de outro
     * responsável do mesmo voluntário ({@code uq_pessoa_relacao_principal}
     * viraria 500) — vira 409 com o nome de quem já é o principal.</p>
     */
    private void substituirDependentes(Pessoa responsavel, List<RelacaoRequest> requests) {
        responsavel.getDependentes().clear();
        entityManager.flush();
        for (RelacaoRequest r : requests) {
            Pessoa voluntario = carregar(r.pessoaId());
            if (!voluntario.isVoluntario()) {
                throw new BadRequestException(
                        voluntario.getNomeCompleto() + " não tem o papel VOLUNTARIO — só voluntário pode ser dependente.");
            }
            if (voluntario.getId().equals(responsavel.getId())) {
                throw new BadRequestException("Uma pessoa não pode se relacionar consigo mesma.");
            }
            if (r.principal()) {
                voluntario.getResponsaveis().stream()
                        .filter(outra -> outra.isPrincipal() && !outra.getResponsavel().getId().equals(responsavel.getId()))
                        .findFirst()
                        .ifPresent(outra -> {
                            throw new ConflictException(voluntario.getNomeCompleto() + " já tem "
                                    + outra.getResponsavel().getNomeCompleto()
                                    + " como responsável principal — troque na ficha do voluntário.");
                        });
            }
            String esteE = opcional(r.parentescoInverso());
            PessoaRelacao relacao = new PessoaRelacao(responsavel, voluntario,
                    esteE == null ? RESPONSAVEL_SEM_ROTULO : esteE, opcional(r.parentesco()), r.principal());
            responsavel.getDependentes().add(relacao);
        }
    }

    private Pessoa criarResponsavelNovo(NovaPessoaRequest nova) {
        Pessoa pessoa = new Pessoa(Set.of(PessoaPapel.RESPONSAVEL), nova.nomeCompleto().trim());
        String email = opcional(nova.email());
        if (email != null) {
            PessoaEmail linha = new PessoaEmail("E-mail pessoal", email, true);
            linha.setPessoa(pessoa);
            pessoa.getEmails().add(linha);
        }
        String telefone = Formatos.telefone(nova.telefone());
        if (telefone != null) {
            PessoaTelefone linha = new PessoaTelefone("celular", telefone, true);
            linha.setPessoa(pessoa);
            pessoa.getTelefones().add(linha);
        }
        pessoa = pessoaRepository.saveAndFlush(pessoa);
        auditLogService.registrar("CRIACAO", "PESSOA", pessoa.getId(), null);
        return pessoa;
    }

    private static String opcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
