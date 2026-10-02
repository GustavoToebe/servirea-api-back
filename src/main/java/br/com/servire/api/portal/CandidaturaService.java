package br.com.servire.api.portal;

import br.com.servire.api.portal.dto.CandidaturaDtos.*;
import br.com.servire.api.portal.Candidatura.Situacao;
import br.com.servire.api.escala.*;
import br.com.servire.api.voluntario.*;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.tenant.*;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;

/** Candidatura pessoal com decisão explícita. Ordem de locks: paróquia → escala → vaga. */
@Service
public class CandidaturaService {
    private static final ZoneId BRASILIA=ZoneId.of("America/Sao_Paulo");
    private final PortalService portal;private final FuncionalidadesPlano plano;
    private final TenantRepository tenants;private final EscalaRepository escalas;
    private final VoluntarioRepository voluntarios;private final UsuarioTenantRepository usuarios;
    private final AuditLogService audit;private final ElegibilidadeEscalaService elegibilidade;
    @PersistenceContext private EntityManager em;
    public CandidaturaService(PortalService portal,FuncionalidadesPlano plano,TenantRepository tenants,
            EscalaRepository escalas,VoluntarioRepository voluntarios,UsuarioTenantRepository usuarios,AuditLogService audit,ElegibilidadeEscalaService elegibilidade){
        this.portal=portal;this.plano=plano;this.tenants=tenants;this.escalas=escalas;
        this.voluntarios=voluntarios;this.usuarios=usuarios;this.audit=audit;this.elegibilidade=elegibilidade;
    }

    @Transactional(readOnly=true)
    public Pagina<Vaga> vagas(LocalDate de,LocalDate ate,int pagina){
        plano.exigir("PORTAL_VOLUNTARIO");pagina(pagina);UUID pessoa=pessoa();
        if(de==null||ate==null||ate.isBefore(de)||java.time.temporal.ChronoUnit.DAYS.between(de,ate)>93)
            throw new BadRequestException("Informe um período de até 93 dias.");
        String where=" from EscalaVaga v join v.evento e where v.voluntario is null and e.referencia=false and e.escala.status=:status and e.data between :de and :ate and (e.data>:hoje or (e.data=:hoje and e.horario>:hora))";
        var agora=LocalDateTime.now(BRASILIA);
        var q=em.createQuery("select v"+where.replace("join v.evento e","join fetch v.evento e join fetch e.escala")+" order by e.data,e.horario,v.id",EscalaVaga.class);
        var count=em.createQuery("select count(v)"+where,Long.class);
        for(var query:List.of(q,count))query.setParameter("status",StatusEscala.FINALIZADA).setParameter("de",de).setParameter("ate",ate).setParameter("hoje",agora.toLocalDate()).setParameter("hora",agora.toLocalTime());
        var itens=q.setFirstResult(pagina*30).setMaxResults(30).getResultList();
        var contexto=elegibilidade.contexto(pessoa,de,ate);
        return new Pagina<>(itens.stream().map(v->{String motivo=elegibilidade.impedimento(v,contexto);return new Vaga(v.getId(),v.getEvento().getCelebracao(),inicio(v),v.getFuncao().name(),v.getRespostaVersao(),motivo==null,motivo);}).toList(),count.getSingleResult(),pagina,30);
    }

    @Transactional
    public Pedido candidatar(UUID vagaId,Versao req){
        plano.exigir("PORTAL_VOLUNTARIO");bloquearTenant();UUID pessoa=pessoa();
        var vaga=bloquearVaga(vagaId);validarLivre(vaga);
        if(req.versao()!=vaga.getRespostaVersao())throw mudou();
        String motivo=elegibilidade.impedimento(vaga,elegibilidade.contexto(pessoa,vaga.getEvento().getData(),vaga.getEvento().getData()));
        if(motivo!=null)throw new ConflictException(motivo);
        var existente=em.createQuery("select c from Candidatura c where c.vagaId=:vaga and c.pessoaId=:pessoa and c.vagaVersao=:versao",Candidatura.class)
                .setParameter("vaga",vagaId).setParameter("pessoa",pessoa).setParameter("versao",vaga.getRespostaVersao()).getResultStream().findFirst();
        var c=existente.orElseGet(Candidatura::new);
        if(existente.isPresent()&&c.situacao==Situacao.PENDENTE)return pedido(c,null,vaga);
        if(existente.isPresent()&&c.situacao!=Situacao.DESISTIDA)throw new ConflictException("A candidatura deste ciclo já foi encerrada.");
        Instant agora=Instant.now();
        if(existente.isEmpty()){
            c.escalaId=vaga.getEvento().getEscala().getId();c.vagaId=vagaId;c.pessoaId=pessoa;
            c.usuarioId=usuario();c.vagaVersao=vaga.getRespostaVersao();c.criadaEm=agora;
            c.celebracao=vaga.getEvento().getCelebracao();c.inicio=inicio(vaga);c.funcao=vaga.getFuncao().name();
        } else c.usuarioId=usuario();
        c.situacao=Situacao.PENDENTE;c.atualizadaEm=agora;
        if(existente.isEmpty())em.persist(c);em.flush();
        audit.registrar("CANDIDATAR","ESCALA_CANDIDATURA",c.id,List.of("situacao"));
        return pedido(c,null,vaga);
    }

    @Transactional
    public Pedido desistir(UUID id,Versao req){
        bloquearTenant();UUID pessoa=pessoa();
        var c=em.createQuery("select c from Candidatura c where c.id=:id and c.pessoaId=:pessoa",Candidatura.class)
                .setParameter("id",id).setParameter("pessoa",pessoa).getResultStream().findFirst().orElseThrow(CandidaturaService::naoEncontrada);
        if(c.versao!=req.versao())throw mudou();
        if(c.situacao!=Situacao.PENDENTE&&c.situacao!=Situacao.DESISTIDA)throw new ConflictException("Só é possível desistir de candidatura pendente.");
        if(c.situacao==Situacao.PENDENTE){c.situacao=Situacao.DESISTIDA;c.atualizadaEm=Instant.now();em.flush();audit.registrar("DESISTIR","ESCALA_CANDIDATURA",c.id,List.of("situacao"));}
        return pedido(c,null,em.find(EscalaVaga.class,c.vagaId));
    }

    @Transactional
    public Pedido decidir(UUID id,Decisao req){
        plano.exigir("PORTAL_VOLUNTARIO");plano.exigir("ESCALAS");bloquearTenant();
        var c=em.find(Candidatura.class,id);if(c==null)throw naoEncontrada();
        var vaga=bloquearVaga(c.vagaId);
        em.refresh(c);
        if(c.versao!=req.versao())throw mudou();
        if(c.situacao!=Situacao.PENDENTE)throw new ConflictException("A candidatura já foi encerrada.");
        validarLivre(vaga);if(c.vagaVersao!=vaga.getRespostaVersao())throw mudou();
        if(req.aprovar()){
            var vinculo=usuarios.findByUsuario_IdAndTenant_Id(c.usuarioId,TenantContext.get()).orElseThrow(CandidaturaService::naoEncontrada);
            if(vinculo.getStatus()!=UsuarioTenant.Status.ATIVO||!c.pessoaId.equals(vinculo.getPessoaId()))throw new ConflictException("O vínculo pessoal do candidato mudou.");
            var contexto=elegibilidade.contexto(c.pessoaId,vaga.getEvento().getData(),vaga.getEvento().getData());
            if(contexto.voluntario()!=null)em.lock(contexto.voluntario(),LockModeType.PESSIMISTIC_WRITE);
            String motivo=elegibilidade.impedimento(vaga,contexto);if(motivo!=null)throw new ConflictException(motivo);
            vaga.setVoluntario(contexto.voluntario());vaga.setPresenca(Presenca.PENDENTE);
            c.situacao=Situacao.APROVADA;
            // Só uma alocação; os demais pedidos deixam de poder ser aprovados.
            for(var outro:em.createQuery("select c from Candidatura c where c.vagaId=:vaga and c.situacao=:situacao and c.id<>:id",Candidatura.class)
                    .setParameter("vaga",c.vagaId).setParameter("situacao",Situacao.PENDENTE).setParameter("id",c.id).getResultList()){
                outro.situacao=Situacao.EXPIRADA;outro.atualizadaEm=Instant.now();
            }
            audit.registrar("ALOCACAO_CANDIDATURA","ESCALA_VAGA",c.vagaId,List.of("voluntario","resposta","presenca"));
        } else c.situacao=Situacao.RECUSADA;
        c.atualizadaEm=Instant.now();em.flush();
        audit.registrar(req.aprovar()?"APROVAR":"RECUSAR","ESCALA_CANDIDATURA",c.id,List.of("situacao"));
        return pedido(c,null,vaga);
    }

    @Transactional(readOnly=true)
    public Pagina<Pedido> minhas(int pagina){plano.exigir("PORTAL_VOLUNTARIO");return listar("c.pessoaId=:chave",pessoa(),pagina,false);}
    @Transactional(readOnly=true)
    public Pagina<Pedido> coordenacao(UUID escalaId,int pagina){
        escalas.findById(escalaId).orElseThrow(CandidaturaService::naoEncontrada);
        return listar("c.escalaId=:chave",escalaId,pagina,true);
    }
    private Pagina<Pedido> listar(String filtro,UUID chave,int pagina,boolean nomes){
        pagina(pagina);var itens=em.createQuery("select c from Candidatura c where "+filtro+" order by c.criadaEm desc,c.id desc",Candidatura.class)
                .setParameter("chave",chave).setFirstResult(pagina*30).setMaxResults(30).getResultList();
        long total=em.createQuery("select count(c) from Candidatura c where "+filtro,Long.class).setParameter("chave",chave).getSingleResult();
        Map<UUID,EscalaVaga> vagas=new HashMap<>();Map<UUID,String> pessoas=new HashMap<>();
        if(!itens.isEmpty()){
            for(var v:em.createQuery("select v from EscalaVaga v join fetch v.evento e join fetch e.escala where v.id in :ids",EscalaVaga.class).setParameter("ids",itens.stream().map(c->c.vagaId).toList()).getResultList())vagas.put(v.getId(),v);
            if(nomes)for(var p:em.createQuery("select p.id,p.nomeCompleto from Pessoa p where p.id in :ids",Object[].class).setParameter("ids",itens.stream().map(c->c.pessoaId).toList()).getResultList())pessoas.put((UUID)p[0],(String)p[1]);
        }
        return new Pagina<>(itens.stream().map(c->pedido(c,pessoas.get(c.pessoaId),vagas.get(c.vagaId))).toList(),total,pagina,30);
    }

    private Pedido pedido(Candidatura c,String nome,EscalaVaga vaga){
        boolean vigente=c.situacao==Situacao.PENDENTE&&livre(vaga)&&vaga.getRespostaVersao()==c.vagaVersao;
        var situacao=c.situacao==Situacao.PENDENTE&&!vigente?Situacao.EXPIRADA:c.situacao;
        return new Pedido(c.id,c.vagaId,c.celebracao,c.inicio,c.funcao,situacao,c.versao,c.criadaEm,c.atualizadaEm,nome,vigente);
    }
    private EscalaVaga bloquearVaga(UUID id){
        UUID escala=em.createQuery("select v.evento.escala.id from EscalaVaga v where v.id=:id",UUID.class).setParameter("id",id).getResultStream().findFirst().orElseThrow(CandidaturaService::naoEncontrada);
        escalas.bloquear(escala).orElseThrow(CandidaturaService::naoEncontrada);
        return em.createQuery("select v from EscalaVaga v join fetch v.evento where v.id=:id",EscalaVaga.class).setParameter("id",id).getResultStream().findFirst().orElseThrow(CandidaturaService::naoEncontrada);
    }
    private void bloquearTenant(){tenants.bloquearParaCotas(TenantContext.get()).orElseThrow(CandidaturaService::naoEncontrada);}
    private UUID pessoa(){UUID p=portal.pessoaAtual();if(p==null)throw new BadRequestException("Peça ao administrador para vincular sua pessoa.");return p;}
    private static UUID usuario(){return ((AuthenticatedUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).usuarioId();}
    private static boolean livre(EscalaVaga v){return v!=null&&v.getVoluntario()==null&&!v.getEvento().isReferencia()&&v.getEvento().getEscala().getStatus()==StatusEscala.FINALIZADA&&inicio(v).atZone(BRASILIA).toInstant().isAfter(Instant.now());}
    private static void validarLivre(EscalaVaga v){if(!livre(v))throw new ConflictException("A vaga não está mais aberta para candidatura.");}
    private static LocalDateTime inicio(EscalaVaga v){return v.getEvento().getData().atTime(v.getEvento().getHorario());}
    private static Periodo periodo(LocalTime h){return h.isBefore(LocalTime.NOON)?Periodo.MANHA:h.isBefore(LocalTime.of(18,0))?Periodo.TARDE:Periodo.NOITE;}
    private static void pagina(int p){if(p<0||p>100000)throw new BadRequestException("Página inválida.");}
    private static ConflictException mudou(){return new ConflictException("A vaga ou candidatura mudou. Atualize antes de continuar.");}
    private static ResourceNotFoundException naoEncontrada(){return new ResourceNotFoundException("Vaga ou candidatura não encontrada.");}
}
