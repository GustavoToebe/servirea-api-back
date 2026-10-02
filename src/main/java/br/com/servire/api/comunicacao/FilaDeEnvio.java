package br.com.servire.api.comunicacao;

import br.com.servire.api.auth.Anexo;
import br.com.servire.api.auth.EmailSender;
import br.com.servire.api.integracao.AcessoParoquia;
import br.com.servire.api.tenant.*;
import br.com.servire.api.pessoa.PessoaRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Reservas duráveis, ritmo no banco e workers limitados. HTTP nunca ocupa transação. */
@Component
public class FilaDeEnvio {
    private static final Logger log=LoggerFactory.getLogger(FilaDeEnvio.class);
    static final int LOTE=30;
    private static final List<StatusComunicado> ABERTOS=List.of(StatusComunicado.NA_FILA,StatusComunicado.ENVIANDO);
    private final br.com.servire.api.integracao.FuncionalidadesPlano funcionalidades;
    private final br.com.servire.api.minhaconta.CotasService cotas;
    private final TenantRepository tenants;
    private final AcessoParoquia acesso;
    private final ComunicadoRepository comunicados;
    private final ComunicadoDestinatarioRepository destinatarios;
    private final ComunicadoAnexoRepository anexos;
    private final ParoquiaWhatsappService whatsapp;
    private final EmailSender email;
    private final WhatsappSender zap;
    private final WhatsappProperties properties;
    private final JanelaEnvioRepository janelas;
    private final PessoaRepository pessoas;
    private final TransactionTemplate tx;
    private final boolean ativa;
    private volatile boolean pronta;
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    void pronta() { pronta=true; }
    private final long pausaEmailMs,retentativaMs;
    private final AtomicInteger cursor=new AtomicInteger();
    private final Set<String> emCurso=ConcurrentHashMap.newKeySet();
    private final Semaphore vagasEmail=new Semaphore(2), vagasWhatsapp=new Semaphore(6);
    private final AtomicInteger pagina=new AtomicInteger();
    private final ExecutorService workersWhatsapp=new ThreadPoolExecutor(3,3,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(3),
        Thread.ofVirtual().name("fila-comunicados-",0).factory(),new ThreadPoolExecutor.AbortPolicy());

    private final ExecutorService workersEmail=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),
        Thread.ofVirtual().name("fila-email-",0).factory(),new ThreadPoolExecutor.AbortPolicy());

    public FilaDeEnvio(TenantRepository tenants,AcessoParoquia acesso,ComunicadoRepository comunicados,
        ComunicadoDestinatarioRepository destinatarios,ComunicadoAnexoRepository anexos,ParoquiaWhatsappService whatsapp,
        EmailSender email,WhatsappSender zap,WhatsappProperties properties,JanelaEnvioRepository janelas,PessoaRepository pessoas,
        PlatformTransactionManager tm,br.com.servire.api.integracao.FuncionalidadesPlano funcionalidades,br.com.servire.api.minhaconta.CotasService cotas,@Value("${servire.comunicado.fila-ativa:true}") boolean ativa,
        @Value("${servire.comunicado.pausa-email-ms:600}") long pausaEmailMs,
        @Value("${servire.comunicado.retentativa-ms:60000}") long retentativaMs) {
        this.funcionalidades=funcionalidades; this.cotas=cotas; this.tenants=tenants; this.acesso=acesso; this.comunicados=comunicados; this.destinatarios=destinatarios;
        this.anexos=anexos; this.whatsapp=whatsapp; this.email=email; this.zap=zap; this.properties=properties;
        this.janelas=janelas; this.pessoas=pessoas; this.tx=new TransactionTemplate(tm); this.ativa=ativa;
        this.pausaEmailMs=Math.max(0,pausaEmailMs); this.retentativaMs=Math.max(0,retentativaMs);
    }
    @Scheduled(fixedDelay=500,initialDelay=15000)
    void agendado() {
        if (!ativa || !pronta) return;
        var lote=new ArrayList<>(tenants.loteDaFila(PageRequest.of(pagina.getAndIncrement(),100)));
        if (!lote.isEmpty()) Collections.rotate(lote,-Math.floorMod(cursor.getAndIncrement(),lote.size()));
        if (lote.size()<100) pagina.set(0);
        for (var tenant : lote) for (TipoEnvio canal : TipoEnvio.values()) {
            String chave=canal+":"+tenant.getId();
            Semaphore vagas=canal==TipoEnvio.EMAIL ? vagasEmail : vagasWhatsapp;
            ExecutorService workers=canal==TipoEnvio.EMAIL ? workersEmail : workersWhatsapp;
            if (emCurso.contains(chave) || !vagas.tryAcquire()) continue;
            if (!emCurso.add(chave)) { vagas.release(); continue; }
            try { workers.submit(() -> { try { rodada(tenant,List.of(canal)); } finally { emCurso.remove(chave); vagas.release(); } }); }
            catch (RejectedExecutionException ex) { emCurso.remove(chave); vagas.release(); }
        }
    }

    /** Rodada síncrona para testes/operação local; produção despacha sem aguardar provedor lento. */
    public void processarAgora() {
        for (int r=0;r<LOTE;r++) {
            int tratados=0;
            for (Tenant tenant : ordem()) tratados+=rodada(tenant,List.of(TipoEnvio.values()));
            if (tratados==0) break;
        }
    }
    private List<Tenant> ordem() {
        var lista=new ArrayList<>(tenants.findAll());
        if (!lista.isEmpty()) Collections.rotate(lista,-Math.floorMod(cursor.getAndIncrement(),lista.size()));
        return lista;
    }
    private int rodada(Tenant tenant,List<TipoEnvio> canais) {
        if (!acesso.liberada(tenant)) return 0;
        TenantContext.set(tenant.getId());
        int n=0;
        try {
            for (TipoEnvio canal : canais) {
                Envio envio=tx.execute(status -> reservar(tenant,canal));
                if (envio==null) continue;
                n++; String erro=null;
                try {
                    if (canal==TipoEnvio.EMAIL) email.enviarComunicadoIdempotente(envio.destino(),envio.assunto(),envio.conteudo(),envio.anexos(),envio.responderPara(),"comunicado/"+envio.id());
                    else {
                        var config=whatsapp.ativa().orElseThrow(() -> new IllegalStateException("WhatsApp desativado."));
                        zap.enviarTexto(config.getInstancia(),config.getToken(),envio.destino(),envio.conteudo());
                    }
                } catch (RuntimeException ex) { erro="Falha no envio ("+ex.getClass().getSimpleName()+")."; }
                String falha=erro; tx.executeWithoutResult(status -> concluir(envio,falha));
            }
        } catch (RuntimeException ex) { log.error("Falha na fila da paróquia {}: {}",tenant.getId(),ex.getClass().getSimpleName()); }
        finally { TenantContext.clear(); }
        return n;
    }
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager aniversarioEm;
    private Envio reservar(Tenant tenant,TipoEnvio canal) {
        Instant agora=Instant.now(); String janela=canal==TipoEnvio.EMAIL ? "EMAIL" : "WHATSAPP:"+tenant.getId();
        var ids=destinatarios.candidatos(StatusEnvio.PENDENTE,ABERTOS,canal,agora,PageRequest.of(0,1));
        if (ids.isEmpty()) return null;
        cotas.travarEnvios();
        if(!funcionalidades.permitida("COMUNICACAO")) return null;
        agora=Instant.now();
        var j=janelas.buscarParaAlterar(janela).orElse(null);
        if (j==null) { janelas.saveAndFlush(new JanelaEnvio(janela)); j=janelas.buscarParaAlterar(janela).orElseThrow(); }
        if (!j.disponivel(agora)) return null;
        UUID id=ids.getFirst(); UUID comunicadoId=destinatarios.comunicadoDoDestinatario(id).orElseThrow();
        var c=comunicados.buscarParaAlterar(comunicadoId).orElseThrow();
        var d=destinatarios.buscarParaAlterar(id).orElseThrow();
        if (!d.pronto(agora) || !ABERTOS.contains(c.getStatus())) return null;
        var aniversarios=aniversarioEm.createQuery("select x from AniversarioExecucao x where x.comunicadoId=:id",AniversarioExecucao.class).setParameter("id",comunicadoId).setMaxResults(1).getResultList();
        if(!aniversarios.isEmpty()){
            var x=aniversarios.getFirst();var hoje=java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
            long autorizados=aniversarioEm.createQuery("select count(a) from AniversarioAutorizacao a where a.pessoaId=:p and a.canal=:c and a.autorizado=true",Long.class).setParameter("p",x.pessoaId).setParameter("c",canal).getSingleResult();
            long ativos=aniversarioEm.createQuery("select count(c) from AniversarioConfig c where c.canal=:c and c.ativo=true",Long.class).setParameter("c",canal).getSingleResult();
            if(autorizados==0||ativos==0||!x.dia.equals(hoje)){d.falhaDefinitiva("Felicitação cancelada: autorização, configuração ou data indisponível.");atualizar(c);return null;}
        }
        if (canal==TipoEnvio.WHATSAPP && d.getPessoaId()!=null) {
            var pessoa=pessoas.findById(d.getPessoaId()).orElse(null);
            if (pessoa==null || pessoa.getVoluntario()==null || !pessoa.getVoluntario().isAutorizaWhatsapp()) {
                d.falhaDefinitiva("Autorização de WhatsApp revogada ou indisponível."); atualizar(c); return null;
            }
        }
        if (!cotas.contabilizarEnvio(d,canal,agora)) {
            d.aguardarCota(agora.plusSeconds(300));
            return null;
        }
        UUID dono=UUID.randomUUID(); Instant ate=agora.plusSeconds(120);
        long intervalo=canal==TipoEnvio.EMAIL ? pausaEmailMs : intervaloZap();
        j.reservar(dono,ate,agora.plusMillis(intervalo)); d.reservar(dono,ate); c.marcarEnviando();
        var arquivos=canal==TipoEnvio.EMAIL ? anexos.findByComunicadoIdOrderByNome(c.getId()).stream().filter(a -> a.getConteudo()!=null)
            .map(a -> new Anexo(a.getNome(),a.getTipo(),a.getConteudo())).toList() : List.<Anexo>of();
        String responder=tenants.findById(tenant.getId()).flatMap(ComunicadoService::emailPrincipal).orElse(null);
        return new Envio(d.getId(),c.getId(),dono,janela,d.getDestino(),d.getAssunto(),d.getConteudo(),arquivos,responder);
    }
    private void concluir(Envio envio,String erro) {
        var janela=janelas.buscarParaAlterar(envio.janela()).orElseThrow();
        var c=comunicados.buscarParaAlterar(envio.comunicadoId()).orElseThrow();
        var d=destinatarios.buscarParaAlterar(envio.id()).orElse(null);
        if (d==null || !d.pertenceA(envio.dono(),Instant.now())) return;
        d.liberar(); janela.liberar(envio.dono());
        if (d.getStatus()!=StatusEnvio.PENDENTE) return;
        if (erro==null) d.marcarEnviado();
        else { d.registrarFalha(erro); d.reagendar(Instant.now().plusMillis(retentativaMs * (d.getTentativas()==1 ? 1 : 5))); }
        destinatarios.flush(); atualizar(c);
    }
    private void atualizar(Comunicado c) {
        destinatarios.flush(); UUID id=c.getId();
        c.contar((int)destinatarios.countByComunicadoIdAndStatus(id,StatusEnvio.ENVIADO),(int)destinatarios.countByComunicadoIdAndStatus(id,StatusEnvio.FALHA));
        if (destinatarios.countByComunicadoIdAndStatus(id,StatusEnvio.PENDENTE)==0) { c.concluir(); anexos.findByComunicadoIdOrderByNome(id).forEach(ComunicadoAnexo::apagarConteudo); }
    }
    private long intervaloZap() { long min=Math.max(0,properties.intervaloMinMs()),max=Math.max(min,properties.intervaloMaxMs()); return max==min ? min : ThreadLocalRandom.current().nextLong(min,max+1); }
    @PreDestroy void encerrar() { workersEmail.shutdownNow(); workersWhatsapp.shutdownNow(); }
    private record Envio(UUID id,UUID comunicadoId,UUID dono,String janela,String destino,String assunto,String conteudo,List<Anexo> anexos,String responderPara) {
        @Override public String toString() { return "Envio[id="+id+"]"; }
    }
}
