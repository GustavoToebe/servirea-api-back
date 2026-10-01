package br.com.servire.api.pessoa;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.auth.UsuarioTenant;
import br.com.servire.api.pessoa.dto.AniversarianteResponse;
import br.com.servire.api.security.AuthenticatedUser;
import br.com.servire.api.tenant.Tenant;
import br.com.servire.api.tenant.TenantContext;
import br.com.servire.api.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /pessoas/aniversariantes: cartão do Início. */
@AutoConfigureMockMvc
class AniversariantesHttpIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PessoaRepository pessoas;

    private UUID paroquia;
    private UUID outraParoquia;

    @BeforeEach
    void preparar() {
        outraParoquia = novaParoquia();
        TenantContext.set(outraParoquia);
        pessoa("Pessoa de outra paróquia", LocalDate.of(1990, 3, 5));

        paroquia = novaParoquia();
        TenantContext.set(paroquia);
        pessoa("Carla", LocalDate.of(1985, 3, 20));
        pessoa("Bruno", LocalDate.of(2010, 3, 5));
        pessoa("Ana", LocalDate.of(1970, 3, 5));
        pessoa("Diego", LocalDate.of(2000, 4, 5));
        pessoa("Sem data", null);
        pessoa("Bissexto", LocalDate.of(2000, 2, 29));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
    }

    private UUID novaParoquia() {
        String sufixo = UUID.randomUUID().toString();
        return tenantRepository.saveAndFlush(new Tenant("TENANT-ANIV-" + sufixo, "tenant-aniv-" + sufixo,
                "Paróquia de teste (aniversariantes)", Tenant.Status.ATIVO)).getId();
    }

    private void pessoa(String nome, LocalDate nascimento) {
        Pessoa p = Pessoas.voluntario(nome);
        p.setDataNascimento(nascimento);
        pessoas.saveAndFlush(p);
    }

    private Authentication usuario(String... permissoes) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(UUID.randomUUID(), paroquia, UsuarioTenant.Role.ADMIN, false), null,
                Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void listaSoDiaENomeDoMesOrdenadoPorDiaENomeSoDaPropriaParoquia() throws Exception {
        mvc.perform(get("/pessoas/aniversariantes").param("mes", "3").with(authentication(usuario("PERM_PESSOA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nome").value("Ana"))
                .andExpect(jsonPath("$[0].dia").value(5))
                .andExpect(jsonPath("$[1].nome").value("Bruno"))
                .andExpect(jsonPath("$[2].nome").value("Carla"))
                .andExpect(jsonPath("$[2].dia").value(20))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].dataNascimento").doesNotExist())
                .andExpect(jsonPath("$[0].telefone").doesNotExist());
    }

    @Test
    void vinteENoveDeFevereiroAparece() throws Exception {
        mvc.perform(get("/pessoas/aniversariantes").param("mes", "2").with(authentication(usuario("PERM_PESSOA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Bissexto"))
                .andExpect(jsonPath("$[0].dia").value(29));
    }

    @Test
    void mesSemAniversariantesDevolveListaVazia() throws Exception {
        mvc.perform(get("/pessoas/aniversariantes").param("mes", "12").with(authentication(usuario("PERM_PESSOA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void mesInvalidoDa400() throws Exception {
        mvc.perform(get("/pessoas/aniversariantes").param("mes", "13").with(authentication(usuario("PERM_PESSOA"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/pessoas/aniversariantes").param("mes", "0").with(authentication(usuario("PERM_PESSOA"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semPermissaoDePessoasRecebe403() throws Exception {
        mvc.perform(get("/pessoas/aniversariantes").with(authentication(usuario("PERM_ESCALA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void semMesUsaOMesDeBrasiliaMesmoComOServidorJaNoMesSeguinteEmUtc() {
        // 30/09 às 23h30 em Brasília = 01/10 02h30 em UTC.
        Clock virada = Clock.fixed(Instant.parse("2026-10-01T02:30:00Z"), ZoneOffset.UTC);
        pessoa("Setembrino", LocalDate.of(1999, 9, 30));
        pessoa("Outubrino", LocalDate.of(1999, 10, 1));

        List<AniversarianteResponse> lista = new Aniversariantes(pessoas, virada).doMes(null);

        assertThat(lista).extracting(AniversarianteResponse::nome).containsExactly("Setembrino");
    }
}
