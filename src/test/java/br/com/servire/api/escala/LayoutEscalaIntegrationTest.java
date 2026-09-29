package br.com.servire.api.escala;

import br.com.servire.api.AbstractIntegrationTest;
import br.com.servire.api.voluntario.FuncaoEscala;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class LayoutEscalaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LayoutEscalaRepository repository;

    @Test
    @WithMockUser(authorities = "PERM_ESCALA_ALTERAR")
    void deveCriarLayout() throws Exception {
        br.com.servire.api.tenant.TenantContext.set(UUID.randomUUID());
        LayoutEscalaDto dto = new LayoutEscalaDto(null, "Meu Layout", TipoEscala.SEMANAL,
                List.of(new ColunaEscalaDto(1, FuncaoEscala.MISSAL, 1, "Acólito Missal")), true, false);
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dto);
        mvc.perform(post("/escalas/layouts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.nome", is("Meu Layout")));
        br.com.servire.api.tenant.TenantContext.clear();
    }
}
