package br.com.servire.api.acesso;
import br.com.servire.api.auth.*;
import br.com.servire.api.pessoa.PessoaRepository;
import br.com.servire.api.acesso.ConcessaoDePermissao;
import br.com.servire.api.audit.AuditLogService;
import br.com.servire.api.tenant.*;
import br.com.servire.api.web.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class VinculoPessoaService {
 private final UsuarioTenantRepository usuarios;private final PessoaRepository pessoas;private final TenantRepository tenants;private final AuditLogService audit;
 @PersistenceContext private EntityManager em;
 public VinculoPessoaService(UsuarioTenantRepository usuarios,PessoaRepository pessoas,TenantRepository tenants,AuditLogService audit){this.usuarios=usuarios;this.pessoas=pessoas;this.tenants=tenants;this.audit=audit;}
 @Transactional(readOnly=true) public Vinculo buscar(UUID usuario){var v=carregar(usuario);return resposta(v);}
 @Transactional public Vinculo salvar(UUID usuario,UUID pessoa){
  tenants.bloquearParaCotas(TenantContext.get()).orElseThrow();var v=carregar(usuario);
  ConcessaoDePermissao.exigirPodeAlterarAcessoTotal(v.getPerfil()!=null?v.getPerfil().isAcessoTotal():v.getRole()==UsuarioTenant.Role.ADMIN);
  if(pessoa!=null){pessoas.findById(pessoa).orElseThrow(()->new ResourceNotFoundException("Pessoa não encontrada."));
   var outros=em.createQuery("select count(v) from UsuarioTenant v where v.tenant.id=:tenant and v.pessoaId=:pessoa and v.usuario.id<>:usuario",Long.class).setParameter("tenant",TenantContext.get()).setParameter("pessoa",pessoa).setParameter("usuario",usuario).getSingleResult();
   if(outros>0) throw new ConflictException("Esta pessoa já está vinculada a outro usuário.");}
  em.createQuery("delete from CalendarioAssinatura c where c.tenantId=:tenant and c.usuarioId=:usuario").setParameter("tenant",TenantContext.get()).setParameter("usuario",usuario).executeUpdate();
  v.setPessoaId(pessoa);usuarios.saveAndFlush(v);audit.registrar("VINCULAR_PESSOA","USUARIO",usuario,List.of("pessoaId"));return resposta(v);
 }
 private UsuarioTenant carregar(UUID id){return usuarios.findComPerfilByUsuario_IdAndTenant_Id(id,TenantContext.get()).orElseThrow(()->new ResourceNotFoundException("Usuário não encontrado."));}
 private Vinculo resposta(UsuarioTenant v){return new Vinculo(v.getPessoaId(),v.getPessoaId()==null?null:pessoas.findById(v.getPessoaId()).map(p->p.getNomeCompleto()).orElse(null));}
 public record Vinculo(UUID pessoaId,String pessoaNome) { }
}
