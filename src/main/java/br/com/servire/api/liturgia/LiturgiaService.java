package br.com.servire.api.liturgia;
import br.com.servire.api.liturgia.dto.LiturgiaDtos.*;
import br.com.servire.api.tenant.*;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.net.URI;
/** Referências fornecidas pela equipe e sequência manual; não gera conteúdo litúrgico. */ @Service public class LiturgiaService {
    @PersistenceContext private EntityManager em;
    private final TenantRepository tenants;
    private final AuditLogService audit;
    private final JsonMapper json;
    public LiturgiaService(TenantRepository tenants,AuditLogService audit,JsonMapper json) {
        this.tenants=tenants;
        this.audit=audit;
        this.json=json;
    }
    @Transactional(readOnly=true) public Pagina<Referencia> referencias(String busca,int pagina) {
        validarBusca(busca,pagina);
        String f=" where lower(e.titulo) like :busca escape '\\'";
        var q=em.createQuery("select e from ReferenciaLiturgica e"+f+" order by lower(e.titulo),e.id",ReferenciaLiturgica.class).setParameter("busca",literal(busca));
        long total=em.createQuery("select count(e) from ReferenciaLiturgica e"+f,Long.class).setParameter("busca",literal(busca)).getSingleResult();
        return new Pagina<>(q.setFirstResult(pagina*30).setMaxResults(30).getResultList().stream().map(this::referencia).toList(),total,pagina,30);
    }
    @Transactional(readOnly=true) public Pagina<Roteiro> roteiros(String busca,int pagina) {
        validarBusca(busca,pagina);
        String f=" where lower(e.titulo) like :busca escape '\\'";
        var q=em.createQuery("select e from RoteiroLiturgico e"+f+" order by lower(e.titulo),e.id",RoteiroLiturgico.class).setParameter("busca",literal(busca));
        long total=em.createQuery("select count(e) from RoteiroLiturgico e"+f,Long.class).setParameter("busca",literal(busca)).getSingleResult();
        return new Pagina<>(q.setFirstResult(pagina*30).setMaxResults(30).getResultList().stream().map(this::roteiro).toList(),total,pagina,30);
    }
    @Transactional public Referencia salvarReferencia(UUID id,ReferenciaSalvar r) {
        travar();
        var e=id==null?new ReferenciaLiturgica():exigir(ReferenciaLiturgica.class,id);
        conferir(id,e.versao,r.versao());
        String url=r.url()==null||r.url().isBlank()?null:r.url().trim();
        if(url!=null) {
            try {
                var u=URI.create(url);
                if(!Set.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException();
            }
            catch(IllegalArgumentException ex) {
                throw new BadRequestException("Informe URL http/https sem credenciais.");
            }
        }
        e.titulo=r.titulo().trim();
        e.fonte=r.fonte().trim();
        e.url=url;
        e.observacao=r.observacao();
        e.ativo=r.ativo();
        if(id==null) {
            e.versao=1;
            em.persist(e);
        }
        em.flush();
        audit.registrar(id==null?"CRIAR":"ALTERAR","LITURGIA_REFERENCIA",e.id,List.of("titulo","fonte","url","observacao","ativo"));
        return referencia(e);
    }
    @Transactional public Roteiro salvarRoteiro(UUID id,RoteiroSalvar r) {
        travar();
        var e=id==null?new RoteiroLiturgico():exigir(RoteiroLiturgico.class,id);
        conferir(id,e.versao,r.versao());
        var ids=r.passos().stream().map(Passo::referenciaId).filter(Objects::nonNull).distinct().toList();
        if(!ids.isEmpty()&&em.createQuery("select count(r) from ReferenciaLiturgica r where r.id in :ids and r.ativo=true",Long.class).setParameter("ids",ids).getSingleResult()!=ids.size())throw new BadRequestException("Escolha referências ativas desta paróquia.");
        e.titulo=r.titulo().trim();
        e.celebracao=r.celebracao().trim();
        e.passos=json.writeValueAsString(r.passos());
        if(e.passos.length()>120000)throw new BadRequestException("Sequência excede o tamanho permitido.");
        e.ativo=r.ativo();
        if(id==null) {
            e.versao=1;
            em.persist(e);
        }
        em.flush();
        audit.registrar(id==null?"CRIAR":"ALTERAR","LITURGIA_ROTEIRO",e.id,List.of("titulo","celebracao","passos","ativo"));
        return roteiro(e);
    }
    private Referencia referencia(ReferenciaLiturgica e) {
        return new Referencia(e.id,e.versao,e.titulo,e.fonte,e.url,e.observacao,e.ativo);
    }
    private Roteiro roteiro(RoteiroLiturgico e) {
        return new Roteiro(e.id,e.versao,e.titulo,e.celebracao,List.of(json.readValue(e.passos,Passo[].class)),e.ativo);
    }
    private <T>T exigir(Class<T> tipo,UUID id) {
        var e=em.find(tipo,id);
        if(e==null)throw new ResourceNotFoundException("Registro não encontrado.");
        return e;
    }
    private void travar() {
        tenants.bloquearParaCotas(TenantContext.get()).orElseThrow();
    }
    private void conferir(UUID id,long atual,long recebida) {
        if(recebida!=(id==null?0:atual))throw new ConflictException("Registro mudou. Atualize antes de salvar.");
    }
    private void validarBusca(String b,int p) {
        if(b!=null&&b.length()>160||p<0||p>100000)throw new BadRequestException("Busca ou página inválida.");
    }
    private String literal(String b) {
        return "%"+(b==null?"":b.trim().toLowerCase(Locale.ROOT)).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    }
}
