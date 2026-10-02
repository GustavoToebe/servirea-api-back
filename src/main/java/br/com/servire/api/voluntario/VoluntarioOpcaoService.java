package br.com.servire.api.voluntario;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import br.com.servire.api.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Projeção enxuta para seletores; não materializa Pessoa, contatos, foto ou observações. */ @Service public class VoluntarioOpcaoService {
    @PersistenceContext private EntityManager em;
    private static final String COLUNAS="select v.id,v.pessoa.nomeCompleto,v.tipo,v.ativo,v.funcoesHabilitadas";
    @Transactional(readOnly=true)public Pagina buscar(String nome,TipoVoluntario tipo,int pagina) {
        if(nome!=null&&nome.length()>160||pagina<0||pagina>100000)throw new BadRequestException("Busca/página inválida.");
        String b=(nome==null?"":nome.trim().toLowerCase(Locale.ROOT)).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        var q=em.createQuery(COLUNAS+" from Voluntario v where v.ativo=true and v.pessoa.tenantId=:tenant and lower(v.pessoa.nomeCompleto) like :nome escape '\\'"+(tipo==null?"":" and v.tipo=:tipo")+" order by lower(v.pessoa.nomeCompleto),v.id",Object[].class)
                .setParameter("tenant",br.com.servire.api.tenant.TenantContext.get()).setParameter("nome",b);
        if(tipo!=null)q.setParameter("tipo",tipo);
        var rows=q.setFirstResult(pagina*30).setMaxResults(31).getResultList();
        return new Pagina(rows.stream().limit(30).map(this::opcao).toList(),pagina,30,rows.size()>30);
    }
    @Transactional(readOnly=true)public List<Opcao> resolver(List<UUID> ids) {
        if(ids.isEmpty())return List.of();
        return em.createQuery(COLUNAS+" from Voluntario v where v.pessoa.tenantId=:tenant and v.id in :ids order by v.id",Object[].class)
                .setParameter("tenant",br.com.servire.api.tenant.TenantContext.get()).setParameter("ids",ids.stream().distinct().toList()).getResultList().stream().map(this::opcao).toList();
    }
    private Opcao opcao(Object[] r) {
        return new Opcao((UUID)r[0],(String)r[1],(TipoVoluntario)r[2],(Boolean)r[3],List.of((FuncaoEscala[])r[4]));
    }
    public record Opcao(UUID id,String nomeCompleto,TipoVoluntario tipo,boolean ativo,List<FuncaoEscala> funcoesHabilitadas) {
    }
    public record Pagina(List<Opcao> itens,int pagina,int tamanho,boolean temMais) {
    }
    public record Resolver(@NotNull @Size(max=100)List<@NotNull UUID> ids) {
    }
}
