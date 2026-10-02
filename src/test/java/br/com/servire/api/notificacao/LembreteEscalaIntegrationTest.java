package br.com.servire.api.notificacao;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.comunicacao.TipoEnvio;
import br.com.servire.api.escala.*;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import br.com.servire.api.voluntario.FuncaoEscala;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LembreteEscalaIntegrationTest extends AbstractIntegrationTest {
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    @Autowired NotificacaoService service;
    @Autowired TenantRepository tenants;
    @Autowired PessoaRepository pessoas;
    @Autowired EscalaRepository escalas;
    @Autowired PlatformTransactionManager tm;
    @PersistenceContext EntityManager em;

    UUID tenant;
    TransactionTemplate tx;

    @BeforeEach
    void preparar() {
        String slug = UUID.randomUUID().toString();
        tenant = tenants.saveAndFlush(new Tenant(slug, slug, "Lembretes", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenant);
        tx = new TransactionTemplate(tm);
        var ana = voluntario("Ana", true);
        var beto = voluntario("Beto", true);
        var caio = voluntario("Caio", false);
        LocalDateTime em3h = LocalDateTime.now(BRASILIA).plusHours(3).withNano(0);
        escala("Em breve", StatusEscala.FINALIZADA, em3h, ana, beto, caio);
        escala("Longe", StatusEscala.FINALIZADA, em3h.plusDays(3), ana);
        escala("Rascunho", StatusEscala.RASCUNHO, em3h, ana);
        escala("Passada", StatusEscala.FINALIZADA, LocalDateTime.now(BRASILIA).minusHours(2).withNano(0), ana);
        tx.executeWithoutResult(s -> em.createQuery("select v from EscalaVaga v where v.voluntario.id=:b", EscalaVaga.class)
                .setParameter("b", beto.getId()).getResultList()
                .forEach(v -> v.responder(RespostaParticipacao.RECUSADA, Instant.now())));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    Pessoa voluntario(String nome, boolean contato) {
        Pessoa p = Pessoas.voluntario(nome);
        p.getVoluntario().setAutorizaWhatsapp(true);
        if (contato) {
            var t = new PessoaTelefone("celular", "(45) 99999-" + (1000 + nome.length()), true);
            t.setPessoa(p);
            p.getTelefones().add(t);
            var m = new PessoaEmail("pessoal", nome.toLowerCase() + "@teste.test", true);
            m.setPessoa(p);
            p.getEmails().add(m);
        }
        return pessoas.saveAndFlush(p);
    }

    void escala(String titulo, StatusEscala status, LocalDateTime inicio, Pessoa... escalados) {
        var s = new Escala(titulo, TipoEscala.MENSAL);
        s.setStatus(status);
        var e = new EscalaEvento(inicio.toLocalDate(), inicio.toLocalTime(), "Missa " + titulo);
        e.setEscala(s);
        FuncaoEscala[] funcoes = FuncaoEscala.values();
        int i = 0;
        for (Pessoa p : escalados) {
            var v = new EscalaVaga(funcoes[i % funcoes.length], i + 1);
            v.setEvento(e);
            v.setVoluntario(p.getVoluntario());
            e.getVagas().add(v);
            i++;
        }
        s.getEventos().add(e);
        escalas.saveAndFlush(s);
    }

    void ligar(TipoEnvio canal) {
        tx.executeWithoutResult(s -> {
            var c = new NotificacaoConfig();
            c.origem = OrigemNotificacao.ESCALA_LEMBRETE;
            c.canal = canal;
            c.ativo = true;
            c.versao = 1;
            em.persist(c);
        });
    }

    @Test
    void semGatilhoLigadoNadaVaiParaAFila() {
        assertThat(service.lembretesDeEscala()).isZero();
        assertThat(service.entregas(OrigemNotificacao.ESCALA_LEMBRETE, 0).total()).isZero();
    }

    @Test
    void lembraSoQuemEstaEscaladoNaoRecusouETemContatoENaoRepete() {
        ligar(TipoEnvio.EMAIL);
        // Ana recebe; Beto recusou; Caio não tem contato. Escala distante, em rascunho e celebração passada ficam de fora.
        assertThat(service.lembretesDeEscala()).isEqualTo(1);
        assertThat(service.lembretesDeEscala()).isZero();
        var entregas = service.entregas(OrigemNotificacao.ESCALA_LEMBRETE, 0);
        assertThat(entregas.total()).isEqualTo(1);
        var e = entregas.itens().getFirst();
        assertThat(e.total()).isEqualTo(1);
        assertThat(e.ignorados()).isEqualTo(1);
        assertThat(e.gatilho()).isEqualTo(NotificacaoEntrega.Gatilho.AUTOMATICO);
        assertThat(e.titulo()).contains("Missa Em breve");
        assertThat(e.pendentes()).isEqualTo(1);
    }

    @Test
    void cadaCanalLembraUmaVezESoComAutorizacaoNoWhatsapp() {
        ligar(TipoEnvio.EMAIL);
        ligar(TipoEnvio.WHATSAPP);
        assertThat(service.lembretesDeEscala()).isEqualTo(2);
        assertThat(service.entregas(OrigemNotificacao.ESCALA_LEMBRETE, 0).total()).isEqualTo(2);
        assertThat(service.lembretesDeEscala()).isZero();
    }
}
