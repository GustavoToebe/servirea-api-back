package br.com.servire.api.minhaconta;

import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.auth.UsuarioTenantRepository;
import br.com.servire.api.integracao.DireitosLocais;
import br.com.servire.api.integracao.DireitosLocaisRepository;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;

/**
 * Cotas de cadastro, sem contadores duplicados. A trava da paróquia serializa alterações
 * com crescimento e a aplicação dos direitos. Validação após flush inclui responsáveis
 * criados na mesma operação; ultrapassar cancela toda a transação, sem cadastro parcial.
 * Dados anteriores acima de um plano reduzido continuam editáveis, sem permitir crescimento.
 */
@Service
public class CotasService {
    private static final List<String> CODIGOS = List.of("pessoas", "voluntarios", "usuarios", "armazenamento_mb");
    private static final Map<String, String> NOMES = Map.of("pessoas", "Pessoas cadastradas", "voluntarios", "Voluntários cadastrados", "usuarios", "Usuários com acesso ativo ou convite", "armazenamento_mb", "Armazenamento de arquivos vinculados");
    private final TenantRepository tenants;
    private final DireitosLocaisRepository direitos;
    private final UsuarioTenantRepository usuarios;
    private final JsonMapper json;
    private final br.com.servire.api.audit.AuditLogService audit;
    @PersistenceContext private EntityManager em;

    public CotasService(TenantRepository tenants, DireitosLocaisRepository direitos, UsuarioTenantRepository usuarios, JsonMapper json, br.com.servire.api.audit.AuditLogService audit) {
        this.tenants = tenants; this.direitos = direitos; this.usuarios = usuarios; this.json = json;
        this.audit = audit;
    }

    /** Deve ocorrer antes de carregar/mudar pessoa, inscrição ou vínculo de usuário. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Reserva reservar() {
        UUID tenantId = exigirTenant();
        tenants.bloquearParaCotas(tenantId).orElseThrow(() -> new CotaException(HttpStatus.NOT_FOUND, "PAROQUIA_NAO_ENCONTRADA", "Paróquia não encontrada."));
        var limites=limites(direitos.findById(tenantId).orElse(null));
        return new Reserva(tenantId, contagens(tenantId,limites.containsKey("armazenamento_mb")), limites);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void validar(Reserva reserva) {
        if (!reserva.tenantId().equals(exigirTenant())) throw new IllegalStateException("Reserva de outra paróquia.");
        em.flush();
        Map<String, Long> atuais = contagens(reserva.tenantId(),reserva.limites().containsKey("armazenamento_mb"));
        for (String codigo : CODIGOS) {
            Long limite = reserva.limites().get(codigo);
            long usado = atuais.get(codigo);
            if (limite != null && usado > limite && usado > reserva.antes().get(codigo))
                throw new CotaException(HttpStatus.CONFLICT, "COTA_EXCEDIDA", "Limite de " + NOMES.get(codigo).toLowerCase(Locale.ROOT)
                        + " atingido (" + (codigo.equals("armazenamento_mb") ? limite / 1048576 + " MB" : limite) + "). Ajuste seu plano ou adicionais antes de ampliar o cadastro.");
        }
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Consumo consumo() {
        UUID id = exigirTenant();
        DireitosLocais local = direitos.findById(id).orElse(null);
        Map<String, Long> limites = limites(local), usados = contagens(id,true);
        List<Item> itens = CODIGOS.stream().map(codigo -> {
            Long limite = limites.get(codigo); long usado = usados.get(codigo);
            long pendentes = codigo.equals("armazenamento_mb") ? usados.get("fotos_sem_tamanho") : 0;
            String estado = pendentes > 0 ? "INVENTARIO_PENDENTE" : limite == null ? "SEM_LIMITE_CONFIGURADO" : usado > limite ? "EXCEDIDO" : usado == limite ? "ATINGIDO"
                    : limite > 0 && usado >= (limite / 5) * 4 + ((limite % 5) * 4 + 4) / 5 ? "ATENCAO" : "DISPONIVEL";
            return new Item(codigo, NOMES.get(codigo), usado, limite, limite == null || pendentes > 0 ? null : Math.max(0, limite - usado), estado, codigo.equals("armazenamento_mb") ? "bytes" : "unidade", pendentes);
        }).toList();
        return new Consumo(local == null ? null : local.getPlanoNome(), local == null ? null : local.getVersao(),
                local == null ? null : local.getConfirmadoEm(), Instant.now(), itens);
    }

    private Map<String, Long> contagens(UUID tenantId,boolean contarArquivos) {
        Object[] pessoas = em.createQuery("select count(p), coalesce(sum(case when p.eVoluntario = true then 1 else 0 end), 0) from Pessoa p", Object[].class).getSingleResult();
        // Sem teto de arquivos, cadastros comuns não precisam percorrer metadados de fotos.
        var arquivos = contarArquivos ? arquivos() : new Arquivos(0,0,Map.of(),Map.of());
        return Map.of("armazenamento_mb", arquivos.bytes(), "fotos_sem_tamanho", arquivos.pendentes(), "pessoas", ((Number)pessoas[0]).longValue(), "voluntarios", ((Number)pessoas[1]).longValue(),
                "usuarios", usuarios.countByTenant_IdAndStatus(tenantId, UsuarioTenant.Status.ATIVO));
    }

    private Map<String, Long> limites(DireitosLocais local) {
        if (local == null || local.getLimites() == null) return Map.of();
        try {
            var raiz = json.readTree(local.getLimites());
            if (!raiz.isObject()) throw new IllegalArgumentException();
            Map<String, Long> resultado = new HashMap<>();
            for (String codigo : CODIGOS) {
                var valor = raiz.get(codigo);
                if (valor == null) continue;
                if (!valor.isIntegralNumber() || !valor.canConvertToLong() || valor.asLong() < 0) throw new IllegalArgumentException();
                resultado.put(codigo, codigo.equals("armazenamento_mb") ? Math.multiplyExact(valor.asLong(), 1024L * 1024) : valor.asLong());
            }
            return Map.copyOf(resultado);
        } catch (RuntimeException ex) {
            throw new CotaException(HttpStatus.SERVICE_UNAVAILABLE, "COTAS_INVALIDAS", "Os limites do plano precisam ser corrigidos. Contate o suporte.");
        }
    }

    /** Metadados apenas: não carrega imagens nem bytea dos anexos. Caminho compartilhado conta uma vez. */
    private Arquivos arquivos() {
        Map<String,Long> tamanhos=new HashMap<>(); Map<String,Integer> referencias=new HashMap<>();
        for (String consulta : List.of(
                "select v.fotoPath, v.fotoTamanhoBytes from Voluntario v where v.fotoPath is not null",
                "select i.fotoPath, i.fotoTamanhoBytes from Inscricao i where i.fotoPath is not null",
                "select f.caminho, f.fotoTamanhoBytes from EventoFoto f")) {
            for (Object[] linha : em.createQuery(consulta,Object[].class).getResultList()) {
                String caminho=(String)linha[0]; Long bytes=(Long)linha[1];
                referencias.merge(caminho,1,Integer::sum);
                if (!tamanhos.containsKey(caminho) || (bytes!=null && (tamanhos.get(caminho)==null || bytes>tamanhos.get(caminho)))) tamanhos.put(caminho,bytes);
            }
        }
        long total=em.createQuery("select coalesce(sum(a.tamanho),0) from ComunicadoAnexo a where a.conteudo is not null",Long.class).getSingleResult();
        for (Long bytes : tamanhos.values()) if (bytes!=null) total=Math.addExact(total,bytes);
        return new Arquivos(total,tamanhos.values().stream().filter(Objects::isNull).count(),tamanhos,referencias);
    }
    private record Arquivos(long bytes,long pendentes,Map<String,Long> tamanhos,Map<String,Integer> referencias) { }

    /** Antes do upload externo. Crédito da foto substituída só se não estiver compartilhada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validarUpload(Reserva reserva,long tamanho,String caminhoAnterior) {
        if (!reserva.tenantId().equals(exigirTenant())) throw new IllegalStateException("Reserva de outra paróquia.");
        Long limite=reserva.limites().get("armazenamento_mb");
        if (limite==null) return;
        Arquivos atual=arquivos();
        if (atual.pendentes()>0) throw new CotaException(HttpStatus.CONFLICT,"ARMAZENAMENTO_INVENTARIO_PENDENTE","Confira o tamanho das fotos antigas em Minha conta antes de enviar novos arquivos neste plano.");
        long antigo=caminhoAnterior!=null && atual.referencias().getOrDefault(caminhoAnterior,0)==1 ? atual.tamanhos().getOrDefault(caminhoAnterior,0L) : 0;
        long projetado=Math.addExact(atual.bytes()-antigo,tamanho);
        if (projetado>limite && projetado>atual.bytes()) throw new CotaException(HttpStatus.CONFLICT,"COTA_EXCEDIDA","Limite de armazenamento atingido. Amplie seu plano ou libere espaço antes de enviar arquivos.");
    }

    @Transactional(readOnly=true)
    public List<String> fotosSemTamanho(long inicio) {
        exigirTenant();
        if (inicio<0 || inicio>1000000) throw new br.com.servire.api.web.BadRequestException("Posição de conferência inválida.");
        return arquivos().tamanhos().entrySet().stream().filter(e -> e.getValue()==null).map(Map.Entry::getKey).sorted().skip(inicio).limit(5).toList();
    }

    /** Só dados lidos do provedor; nunca aceitar tamanhos informados pelo navegador. */
    @Transactional
    public void registrarTamanhoConferido(String caminho,long tamanho) {
        if (tamanho<=0) throw new IllegalArgumentException("Tamanho não confirmado.");
        tenants.bloquearParaCotas(exigirTenant()).orElseThrow();
        int alterados=0;
        for (String entidade : List.of("Voluntario","Inscricao","EventoFoto")) {
            String campo=entidade.equals("EventoFoto") ? "caminho" : "fotoPath";
            alterados+=em.createQuery("update "+entidade+" f set f.fotoTamanhoBytes=:tamanho where f."+campo+"=:caminho and f.fotoTamanhoBytes is null")
                .setParameter("tamanho",tamanho).setParameter("caminho",caminho).executeUpdate();
        }
        if (alterados>0) audit.registrar("TAMANHO_CONFERIDO","ARMAZENAMENTO",exigirTenant(),List.of("fotoTamanhoBytes"));
    }
    private UUID exigirTenant() {
        UUID id = TenantContext.get();
        if (id == null || id.equals(new UUID(0, 0))) throw new CotaException(HttpStatus.FORBIDDEN, "PAROQUIA_NAO_SELECIONADA", "Selecione uma paróquia.");
        return id;
    }
    public record Reserva(UUID tenantId, Map<String, Long> antes, Map<String, Long> limites) {}
    public record Item(String codigo, String nome, long usado, Long limite, Long disponivel, String estado, String unidade, long pendentes) {}
    public record Consumo(String planoNome, Integer versaoDireitos, Instant direitosConfirmadosEm, Instant consultadoEm, List<Item> itens) {}
}
