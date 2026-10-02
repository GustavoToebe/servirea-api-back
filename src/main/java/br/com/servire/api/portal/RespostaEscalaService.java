package br.com.servire.api.portal;

import br.com.servire.api.portal.dto.RespostaEscalaDtos.*;
import br.com.servire.api.escala.*;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.*;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;

/**
 * Resposta pessoal até o início da celebração (Brasília). Recusar preserva a alocação.
 * Lock da paróquia protege a identidade; lock da escala serializa edição/cancelamento.
 * A versão da vaga impede sobrescrever uma resposta ou alocação recebida depois da leitura.
 */
@Service
public class RespostaEscalaService {
    private final PortalService portal;
    private final FuncionalidadesPlano plano;
    private final TenantRepository tenants;
    private final EscalaRepository escalas;
    private final AuditLogService audit;
    @PersistenceContext private EntityManager em;

    public RespostaEscalaService(PortalService portal, FuncionalidadesPlano plano,
            TenantRepository tenants, EscalaRepository escalas, AuditLogService audit) {
        this.portal=portal;this.plano=plano;this.tenants=tenants;this.escalas=escalas;this.audit=audit;
    }

    @Transactional
    public Atual responder(UUID vagaId, Responder req) {return responderInterno(vagaId,req,null);}
    @Transactional
    public Atual responderDependente(UUID dependente,UUID vagaId,Responder req){return responderInterno(vagaId,req,dependente);}
    private Atual responderInterno(UUID vagaId,Responder req,UUID dependente) {
        plano.exigir("PORTAL_VOLUNTARIO");
        tenants.bloquearParaCotas(TenantContext.get()).orElseThrow();
        UUID pessoa=dependente==null?portal.pessoaAtual():portal.pessoaAutorizada(dependente,true);
        if(pessoa==null) throw new ResourceNotFoundException("Compromisso não encontrado.");
        UUID escalaId=em.createQuery("select v.evento.escala.id from EscalaVaga v where v.id=:id and v.voluntario.id=:pessoa", UUID.class)
                .setParameter("id",vagaId).setParameter("pessoa",pessoa).getResultStream().findFirst()
                .orElseThrow(RespostaEscalaService::naoEncontrado);
        var escala=escalas.bloquear(escalaId).orElseThrow(RespostaEscalaService::naoEncontrado);
        var vaga=em.createQuery("select v from EscalaVaga v join fetch v.evento where v.id=:id and v.voluntario.id=:pessoa",EscalaVaga.class)
                .setParameter("id",vagaId).setParameter("pessoa",pessoa).getResultStream().findFirst()
                .orElseThrow(RespostaEscalaService::naoEncontrado);
        var evento=vaga.getEvento();
        if(escala.getStatus()!=StatusEscala.FINALIZADA||evento.isReferencia()) throw naoEncontrado();
        Instant agora=Instant.now();
        if(!evento.getData().atTime(evento.getHorario()).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().isAfter(agora))
            throw new ConflictException("O prazo para responder terminou no início da celebração.");
        if(req.resposta()==RespostaParticipacao.PENDENTE) throw new BadRequestException("Escolha confirmar ou recusar.");
        if(req.versao()!=vaga.getRespostaVersao()) throw new ConflictException("O compromisso mudou. Atualize antes de responder.");
        if(vaga.getResposta()==req.resposta()) return atual(vaga); // repetição não duplica histórico
        vaga.responder(req.resposta(),agora);
        em.flush();
        var h=new RespostaHistorico();h.escalaId=escalaId;h.vagaId=vagaId;h.pessoaId=pessoa;
        h.usuarioId=((AuthenticatedUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).usuarioId();
        h.resposta=req.resposta();h.respondidoEm=agora;h.versao=vaga.getRespostaVersao();em.persist(h);
        audit.registrar("RESPOSTA_PARTICIPACAO","ESCALA_VAGA",vagaId,List.of("resposta"));
        return atual(vaga);
    }

    @Transactional(readOnly=true)
    public Pagina<Historico> historico(UUID vagaId,int pagina) {
        plano.exigir("PORTAL_VOLUNTARIO");validarPagina(pagina);
        UUID pessoa=portal.pessoaAtual();if(pessoa==null) throw naoEncontrado();
        var itens=em.createQuery("select h from RespostaHistorico h where h.vagaId=:vaga and h.pessoaId=:pessoa order by h.respondidoEm desc,h.id desc",RespostaHistorico.class)
                .setParameter("vaga",vagaId).setParameter("pessoa",pessoa).setFirstResult(pagina*30).setMaxResults(30).getResultList();
        long total=em.createQuery("select count(h) from RespostaHistorico h where h.vagaId=:vaga and h.pessoaId=:pessoa",Long.class)
                .setParameter("vaga",vagaId).setParameter("pessoa",pessoa).getSingleResult();
        return new Pagina<>(itens.stream().map(h->new Historico(h.id,h.resposta,h.respondidoEm,h.versao)).toList(),total,pagina,30);
    }

    @Transactional(readOnly=true)
    public Pagina<Item> consultarCoordenacao(UUID escalaId,int pagina,RespostaParticipacao resposta) {
        validarPagina(pagina);
        escalas.findById(escalaId).orElseThrow(RespostaEscalaService::naoEncontrado);
        String where=" from EscalaVaga v join v.evento e join v.voluntario vol join vol.pessoa p where e.escala.id=:escala and e.referencia=false";
        if(resposta!=null) where+=" and v.resposta=:resposta";
        var q=em.createQuery("select v.id,p.nomeCompleto,e.celebracao,e.data,e.horario,v.funcao,v.resposta,v.respostaEm"+where+" order by e.data,e.horario,v.id",Object[].class).setParameter("escala",escalaId);
        var count=em.createQuery("select count(v)"+where,Long.class).setParameter("escala",escalaId);
        if(resposta!=null){q.setParameter("resposta",resposta);count.setParameter("resposta",resposta);}
        var itens=q.setFirstResult(pagina*30).setMaxResults(30).getResultList().stream()
                .map(v->new Item((UUID)v[0],(String)v[1],(String)v[2],((LocalDate)v[3]).atTime((LocalTime)v[4]),String.valueOf(v[5]),(RespostaParticipacao)v[6],(Instant)v[7])).toList();
        return new Pagina<>(itens,count.getSingleResult(),pagina,30);
    }

    private static void validarPagina(int pagina){if(pagina<0||pagina>100000)throw new BadRequestException("Página inválida.");}
    private static ResourceNotFoundException naoEncontrado(){return new ResourceNotFoundException("Compromisso não encontrado.");}
    private static Atual atual(EscalaVaga v){return new Atual(v.getResposta(),v.getRespostaEm(),v.getRespostaVersao());}
}
