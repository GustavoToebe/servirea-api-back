package br.com.servire.api.pastoral;
import br.com.servire.api.pastoral.dto.PastoralDtos.*;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.integracao.FuncionalidadesPlano;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
@Service
public class PastoralService {
 private final EquipePastoralRepository equipes;private final MembroPastoralRepository membros;private final PessoaRepository pessoas;private final FuncionalidadesPlano plano;private final AuditLogService audit;
 @PersistenceContext private EntityManager em;
 public PastoralService(EquipePastoralRepository equipes,MembroPastoralRepository membros,PessoaRepository pessoas,FuncionalidadesPlano plano,AuditLogService audit){this.equipes=equipes;this.membros=membros;this.pessoas=pessoas;this.plano=plano;this.audit=audit;}
 @Transactional(readOnly=true) public Pagina<Equipe> listar(String busca,int pagina){if(pagina<0||pagina>100000||busca!=null&&busca.length()>120) throw new BadRequestException("Busca inválida.");
  Specification<EquipePastoral> filtro=(r,q,b)->b.conjunction();if(busca!=null&&!busca.isBlank()){String texto="%"+literal(busca)+"%";filtro=(r,q,b)->b.like(b.lower(r.get("nome")),texto,'\\');}
  var p=equipes.findAll(filtro,PageRequest.of(pagina,30,Sort.by("nome","id")));return new Pagina<>(p.map(Equipe::de).getContent(),p.getTotalElements(),pagina,30);}
 @Transactional public Equipe salvar(UUID id,SalvarEquipe req){plano.exigir("PASTORAIS");var e=id==null?new EquipePastoral():equipes.bloquear(id).orElseThrow(()->new ResourceNotFoundException("Equipe não encontrada."));
  if(id!=null&&(req.versao()==null||req.versao()!=e.versao)) throw new ConflictException("A equipe mudou. Atualize antes de salvar.");
  e.nome=req.nome().trim();e.descricao=req.descricao();e.ativo=req.ativo();equipes.saveAndFlush(e);audit.registrar(id==null?"CRIAR":"ALTERAR","PASTORAL",e.id,List.of("nome","descricao","ativo"));return Equipe.de(e);}
 @Transactional(readOnly=true) public Pagina<Membro> membros(UUID equipe,int pagina){if(pagina<0||pagina>100000)throw new BadRequestException("Página inválida.");equipes.findById(equipe).orElseThrow(()->new ResourceNotFoundException("Equipe não encontrada."));var p=membros.findByEquipeId(equipe,PageRequest.of(pagina,30,Sort.by("id")));
  Map<UUID,String> nomes=new HashMap<>();if(!p.isEmpty())for(Object[] n:em.createQuery("select p.id,p.nomeCompleto from Pessoa p where p.id in :ids",Object[].class).setParameter("ids",p.stream().map(m->m.pessoaId).toList()).getResultList())nomes.put((UUID)n[0],(String)n[1]);
  return new Pagina<>(p.stream().map(m->new Membro(m.id,m.pessoaId,nomes.get(m.pessoaId),m.papel,m.ativo,m.versao)).toList(),p.getTotalElements(),pagina,30);}
 @Transactional public void salvarMembro(UUID equipe,SalvarMembro req){plano.exigir("PASTORAIS");var e=equipes.bloquear(equipe).orElseThrow(()->new ResourceNotFoundException("Equipe não encontrada."));if(!e.ativo)throw new ConflictException("Reative a equipe antes de alterar participantes.");pessoas.findById(req.pessoaId()).orElseThrow(()->new ResourceNotFoundException("Pessoa não encontrada."));
  var existente=membros.findByEquipeIdAndPessoaId(equipe,req.pessoaId());var m=existente.orElseGet(MembroPastoral::new);
  if(existente.isPresent()&&(req.versao()==null||req.versao()!=m.versao))throw new ConflictException("O participante já existe ou mudou. Atualize a lista.");
  m.equipeId=equipe;m.pessoaId=req.pessoaId();m.papel=req.papel();m.ativo=req.ativo();membros.saveAndFlush(m);audit.registrar("PARTICIPANTE","PASTORAL",m.id,List.of("pessoaId","papel","ativo"));}
 @Transactional(readOnly=true) public List<PessoaOpcao> pessoas(String busca){if(busca==null||busca.trim().length()<2||busca.length()>120)return List.of();return em.createQuery("select p.id,p.nomeCompleto from Pessoa p where lower(p.nomeCompleto) like :texto escape '\\' order by p.nomeCompleto,p.id",Object[].class).setParameter("texto","%"+literal(busca)+"%").setMaxResults(30).getResultList().stream().map(p->new PessoaOpcao((UUID)p[0],(String)p[1])).toList();}
 private static String literal(String s){return s.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
}
