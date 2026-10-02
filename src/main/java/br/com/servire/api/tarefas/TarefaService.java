package br.com.servire.api.tarefas;
import br.com.servire.api.tarefas.dto.TarefaDtos.*;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.web.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.*;
@Service
public class TarefaService {
 private final TarefaRepository repo;private final AuditLogService audit;private final FuncionalidadesPlano plano;
 public TarefaService(TarefaRepository repo,AuditLogService audit,FuncionalidadesPlano plano) {this.repo=repo;this.audit=audit;this.plano=plano;}
 @Transactional(readOnly=true) public Pagina listar(String busca,Tarefa.Status status,int pagina,int tamanho) {
    if(pagina<0||pagina>100000||tamanho<1||tamanho>100||busca!=null&&busca.length()>160) throw new BadRequestException("Filtro inválido.");
    Specification<Tarefa> filtro=(r,q,b)->b.conjunction();
    if(status!=null) filtro=filtro.and((r,q,b)->b.equal(r.get("status"),status));
    if(busca!=null&&!busca.isBlank()) {String texto="%"+busca.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
       filtro=filtro.and((r,q,b)->b.like(b.lower(r.get("titulo")),texto,'\\'));}
    var p=repo.findAll(filtro,PageRequest.of(pagina,tamanho,Sort.by(Sort.Order.desc("criadoEm"),Sort.Order.desc("id"))));
    return new Pagina(p.getContent().stream().map(Resposta::de).toList(),p.getTotalElements(),pagina,tamanho);
 }
 @Transactional(readOnly=true) public Resposta buscar(UUID id) {return Resposta.de(carregar(id));}
 @Transactional public Resposta salvar(UUID id,Salvar req) {
    plano.exigir("TAREFAS");
    var e=id==null?new Tarefa():carregar(id);
    if(id!=null&&(req.versao()==null||req.versao()!=e.versao)) throw new ConflictException("O registro mudou. Atualize a página antes de salvar.");
    if(id==null) e.criadoEm=Instant.now();
    e.titulo=req.titulo().trim();e.descricao=req.descricao().trim();e.status=req.status();e.prazo=req.prazo();e.equipe=req.equipe()==null||req.equipe().isBlank()?null:req.equipe().trim();
    e.atualizadoEm=Instant.now();repo.saveAndFlush(e);
    audit.registrar(id==null?"CRIAR":"ALTERAR","TAREFAS",e.id,List.of("titulo","descricao","status","prazo"));
    return Resposta.de(e);
 }
 private Tarefa carregar(UUID id) {return repo.findById(id).orElseThrow(()->new ResourceNotFoundException("Registro não encontrado."));}
}
