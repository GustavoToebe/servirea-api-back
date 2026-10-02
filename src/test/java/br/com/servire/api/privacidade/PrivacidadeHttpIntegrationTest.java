package br.com.servire.api.privacidade;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.Usuario;
import br.com.servire.api.auth.UsuarioRepository;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.comunicacao.*;
import br.com.servire.api.pessoa.*;
import br.com.servire.api.pessoa.dto.PessoaRequest;
import br.com.servire.api.pessoa.dto.VoluntarioPerfilRequest;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.*;
import br.com.servire.api.voluntario.TipoVoluntario;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PrivacidadeHttpIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UsuarioRepository usuarios;
    @Autowired PessoaService pessoaService;
    @Autowired PessoaRepository pessoas;
    @Autowired EnvioAvulso envio;
    @Autowired PlatformTransactionManager tm;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;

    UUID tenant, usuario, pessoa;

    @BeforeEach
    void preparar() {
        String slug = UUID.randomUUID().toString();
        tenant = tenants.saveAndFlush(new Tenant(slug, slug, "Privacidade", Tenant.Status.ATIVO)).getId();
        TenantContext.set(tenant);
        usuario = usuarios.saveAndFlush(new Usuario(UUID.randomUUID() + "@teste.com", "Teste")).getId();
        pessoa = pessoaService.criar(request("Maria Voluntária", true)).getId();
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    PessoaRequest request(String nome, boolean whatsapp) {
        return new PessoaRequest(Set.of(PessoaPapel.VOLUNTARIO), nome, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null,
                null, null, null, null,
                new VoluntarioPerfilRequest(TipoVoluntario.COROINHA, true, null, null, null, null, whatsapp, List.of(), null, null));
    }

    UsernamePasswordAuthenticationToken auth(String... permissoes) {
        TenantContext.set(tenant);
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(usuario, tenant, UsuarioTenant.Role.ADMIN, false),
                null, Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void historicoRegistraSomenteMudancasDeConsentimento() throws Exception {
        mvc.perform(get("/pessoas/" + pessoa + "/consentimentos").with(authentication(auth("PERM_PRIVACIDADE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].tipo").value("WHATSAPP")).andExpect(jsonPath("$.itens[0].concedido").value(true));
        TenantContext.set(tenant);
        pessoaService.atualizar(pessoa, request("Maria Voluntária", true));
        mvc.perform(get("/pessoas/" + pessoa + "/consentimentos").with(authentication(auth("PERM_PRIVACIDADE"))))
                .andExpect(jsonPath("$.total").value(1));
        TenantContext.set(tenant);
        pessoaService.atualizar(pessoa, request("Maria Voluntária", false));
        mvc.perform(get("/pessoas/" + pessoa + "/consentimentos").with(authentication(auth("PERM_PRIVACIDADE"))))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.itens[0].concedido").value(false));
        mvc.perform(get("/pessoas/" + pessoa + "/consentimentos").with(authentication(auth("PERM_PESSOA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportacaoExigePermissaoEOcultaCuidadosSemPermissaoEspecifica() throws Exception {
        TenantContext.set(tenant);
        new TransactionTemplate(tm).executeWithoutResult(tx -> {
            Pessoa p = pessoas.findById(pessoa).orElseThrow();
            p.setCuidados("Informação restrita");
        });
        mvc.perform(get("/pessoas/" + pessoa + "/exportacao").with(authentication(auth("PERM_PRIVACIDADE"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/pessoas/" + pessoa + "/exportacao").with(authentication(auth("PERM_PRIVACIDADE_EXPORTAR"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.nome").value("Maria Voluntária"))
                .andExpect(jsonPath("$.voluntario.autorizaWhatsapp").value(true))
                .andExpect(jsonPath("$.consentimentos.length()").value(1))
                .andExpect(jsonPath("$.cuidados").doesNotExist());
        mvc.perform(get("/pessoas/" + pessoa + "/exportacao")
                        .with(authentication(auth("PERM_PRIVACIDADE_EXPORTAR", "PERM_PESSOA_CUIDADOS_LER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cuidados.cuidados").value("Informação restrita"));
    }

    @Test
    void exportacaoNaoTrazCorpoDaMensagemNemDestinoMasMostraAComunicacao() throws Exception {
        TenantContext.set(tenant);
        new TransactionTemplate(tm).executeWithoutResult(tx -> {
            envio.enfileirarEmailDetalhado("Aviso teste", "Assunto X",
                    List.of(new EnvioAvulso.MensagemEmail(pessoa, "Maria", "maria@teste.com", "Assunto X", "CORPO SECRETO")));
        });
        var corpo = mvc.perform(get("/pessoas/" + pessoa + "/exportacao").with(authentication(auth("PERM_PRIVACIDADE_EXPORTAR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comunicacoes.length()").value(1))
                .andExpect(jsonPath("$.comunicacoes[0].assunto").value("Assunto X")).andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("CORPO SECRETO").doesNotContain("maria@teste.com");
    }

    @Test
    void retencaoExigeConfiguracaoVersaoEAnonimizaSoOQueVenceu() throws Exception {
        TenantContext.set(tenant);
        UUID[] ids = new UUID[2];
        new TransactionTemplate(tm).executeWithoutResult(tx -> {
            ids[0] = comunicadoConcluido("Antigo", 400);
            ids[1] = comunicadoConcluido("Recente", 5);
        });
        mvc.perform(post("/privacidade/retencao/execucao").with(authentication(auth("PERM_PRIVACIDADE_RETENCAO"))).with(csrf())
                .contentType("application/json").content("{\"versao\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/privacidade/retencao").with(authentication(auth("PERM_PRIVACIDADE_RETENCAO"))).with(csrf())
                .contentType("application/json").content("{\"comunicadosDias\":10,\"versao\":0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/privacidade/retencao").with(authentication(auth("PERM_PRIVACIDADE_RETENCAO"))).with(csrf())
                .contentType("application/json").content("{\"comunicadosDias\":90,\"versao\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.elegiveis").value(1)).andExpect(jsonPath("$.versao").value(1));
        mvc.perform(put("/privacidade/retencao").with(authentication(auth("PERM_PRIVACIDADE_RETENCAO"))).with(csrf())
                .contentType("application/json").content("{\"comunicadosDias\":60,\"versao\":0}")).andExpect(status().isConflict());
        mvc.perform(post("/privacidade/retencao/execucao").with(authentication(auth("PERM_PRIVACIDADE"))).with(csrf())
                .contentType("application/json").content("{\"versao\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/privacidade/retencao/execucao").with(authentication(auth("PERM_PRIVACIDADE_RETENCAO"))).with(csrf())
                .contentType("application/json").content("{\"versao\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comunicadosAnonimizados").value(1));
        TenantContext.set(tenant);
        new TransactionTemplate(tm).executeWithoutResult(tx -> {
            var antigo = em.createQuery("select d from ComunicadoDestinatario d where d.comunicadoId=:c", ComunicadoDestinatario.class)
                    .setParameter("c", ids[0]).getSingleResult();
            var recente = em.createQuery("select d from ComunicadoDestinatario d where d.comunicadoId=:c", ComunicadoDestinatario.class)
                    .setParameter("c", ids[1]).getSingleResult();
            assertThat(antigo.getConteudo()).isEqualTo(PrivacidadeService.MARCADOR);
            assertThat(antigo.getPessoaId()).isNull();
            assertThat(recente.getConteudo()).isEqualTo("texto Recente");
        });
        mvc.perform(post("/privacidade/retencao/execucao").with(authentication(auth("PERM_PRIVACIDADE_RETENCAO"))).with(csrf())
                .contentType("application/json").content("{\"versao\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comunicadosAnonimizados").value(0));
        mvc.perform(get("/privacidade/retencao").with(authentication(auth("PERM_PRIVACIDADE"))))
                .andExpect(jsonPath("$.execucoes.length()").value(2));
    }

    UUID comunicadoConcluido(String nome, int diasAtras) {
        UUID id = envio.enfileirarEmailDetalhado("Origem " + nome, "Assunto",
                List.of(new EnvioAvulso.MensagemEmail(pessoa, nome, nome + "@teste.com", "Assunto", "texto " + nome))).comunicadoId();
        em.createQuery("update Comunicado c set c.createdAt=:t, c.status=:s where c.id=:id")
                .setParameter("t", Instant.now().minus(diasAtras, ChronoUnit.DAYS)).setParameter("s", StatusComunicado.CONCLUIDO)
                .setParameter("id", id).executeUpdate();
        em.createQuery("update ComunicadoDestinatario d set d.status=:e where d.comunicadoId=:id")
                .setParameter("e", StatusEnvio.ENVIADO).setParameter("id", id).executeUpdate();
        return id;
    }
}
