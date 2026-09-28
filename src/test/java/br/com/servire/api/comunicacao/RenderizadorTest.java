package br.com.servire.api.comunicacao;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do {@link Renderizador} (JUnit puro, sem Spring).
 * Cobre os cenários especificados no PLANO-004, tópico 3.
 */
class RenderizadorTest {

    private static final ContextoDeEnvio CTX_PADRAO = new ContextoDeEnvio(
            "Paróquia São José", "São Paulo", "SP",
            "secretaria@sj.org", "(11) 3333-4444",
            "Ana Beatriz Souza", "42",
            LocalDate.of(2012, 3, 15), "Coroinha",
            "ana@email.com", "(11) 9999-8888",
            "31/12/2026",
            "Maria Souza",
            List.of("Pedro", "Lucas", "Carla")
    );

    @Test
    void trocaSimples() {
        String resultado = Renderizador.renderizar(
                "Olá, #PESSOA.NOME#!", TipoEnvio.EMAIL, CTX_PADRAO);
        assertThat(resultado).isEqualTo("Olá, Ana Beatriz Souza!");
    }

    @Test
    void primeiroNome() {
        String resultado = Renderizador.renderizar(
                "Oi, #PESSOA.PRIMEIRO_NOME#!", TipoEnvio.WHATSAPP, CTX_PADRAO);
        assertThat(resultado).isEqualTo("Oi, Ana!");
    }

    @Test
    void responsavelNuloViraVazio() {
        ContextoDeEnvio semResponsavel = new ContextoDeEnvio(
                CTX_PADRAO.paroquiaNome(), CTX_PADRAO.paroquiaCidade(), CTX_PADRAO.paroquiaUf(),
                CTX_PADRAO.paroquiaEmail(), CTX_PADRAO.paroquiaTelefone(),
                CTX_PADRAO.pessoaNome(), CTX_PADRAO.pessoaNumero(),
                CTX_PADRAO.pessoaNascimento(), CTX_PADRAO.pessoaTipo(),
                CTX_PADRAO.pessoaEmail(), CTX_PADRAO.pessoaTelefone(),
                CTX_PADRAO.pessoaMandatoFim(),
                null, // responsável nulo
                List.of()
        );
        String resultado = Renderizador.renderizar(
                "Resp: #RESPONSAVEL.NOME#", TipoEnvio.EMAIL, semResponsavel);
        assertThat(resultado).isEqualTo("Resp: ");
    }

    @Test
    void scriptNoNomeSaiEscapadoNoEmail() {
        ContextoDeEnvio ctx = new ContextoDeEnvio(
                CTX_PADRAO.paroquiaNome(), CTX_PADRAO.paroquiaCidade(), CTX_PADRAO.paroquiaUf(),
                CTX_PADRAO.paroquiaEmail(), CTX_PADRAO.paroquiaTelefone(),
                "<script>alert('xss')</script>", "1",
                null, "Coroinha", null, null, null, null, List.of()
        );
        String email = Renderizador.renderizar("#PESSOA.NOME#", TipoEnvio.EMAIL, ctx);
        assertThat(email).isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");

        String whatsapp = Renderizador.renderizar("#PESSOA.NOME#", TipoEnvio.WHATSAPP, ctx);
        assertThat(whatsapp).isEqualTo("<script>alert('xss')</script>");
    }

    @Test
    void dependentesNomesComUmNome() {
        ContextoDeEnvio ctx = new ContextoDeEnvio(
                CTX_PADRAO.paroquiaNome(), CTX_PADRAO.paroquiaCidade(), CTX_PADRAO.paroquiaUf(),
                CTX_PADRAO.paroquiaEmail(), CTX_PADRAO.paroquiaTelefone(),
                CTX_PADRAO.pessoaNome(), CTX_PADRAO.pessoaNumero(),
                CTX_PADRAO.pessoaNascimento(), CTX_PADRAO.pessoaTipo(),
                CTX_PADRAO.pessoaEmail(), CTX_PADRAO.pessoaTelefone(),
                CTX_PADRAO.pessoaMandatoFim(),
                CTX_PADRAO.responsavelNome(),
                List.of("Ana")
        );
        assertThat(Renderizador.renderizar("#DEPENDENTES.NOMES#", TipoEnvio.EMAIL, ctx))
                .isEqualTo("Ana");
    }

    @Test
    void dependentesNomesComDoisNomes() {
        ContextoDeEnvio ctx = new ContextoDeEnvio(
                CTX_PADRAO.paroquiaNome(), CTX_PADRAO.paroquiaCidade(), CTX_PADRAO.paroquiaUf(),
                CTX_PADRAO.paroquiaEmail(), CTX_PADRAO.paroquiaTelefone(),
                CTX_PADRAO.pessoaNome(), CTX_PADRAO.pessoaNumero(),
                CTX_PADRAO.pessoaNascimento(), CTX_PADRAO.pessoaTipo(),
                CTX_PADRAO.pessoaEmail(), CTX_PADRAO.pessoaTelefone(),
                CTX_PADRAO.pessoaMandatoFim(),
                CTX_PADRAO.responsavelNome(),
                List.of("Ana", "Bruno")
        );
        assertThat(Renderizador.renderizar("#DEPENDENTES.NOMES#", TipoEnvio.EMAIL, ctx))
                .isEqualTo("Ana e Bruno");
    }

    @Test
    void dependentesNomesComTresNomes() {
        assertThat(Renderizador.renderizar("#DEPENDENTES.NOMES#", TipoEnvio.EMAIL, CTX_PADRAO))
                .isEqualTo("Pedro, Lucas e Carla");
    }

    @Test
    void tagDesconhecidaFicaComoEsta() {
        String resultado = Renderizador.renderizar(
                "Texto #TAG.INEXISTENTE# aqui", TipoEnvio.EMAIL, CTX_PADRAO);
        assertThat(resultado).isEqualTo("Texto #TAG.INEXISTENTE# aqui");
    }

    @Test
    void tagsDesconhecidasListaTagsInexistentes() {
        List<String> desconhecidas = Renderizador.tagsDesconhecidas(
                "Olá #PESSOA.NOME# e #TAG.INEXISTENTE# e #OUTRA.TAG#",
                TipoLayout.TODOS);
        assertThat(desconhecidas).containsExactly("#TAG.INEXISTENTE#", "#OUTRA.TAG#");
    }

    @Test
    void responsavelNomeEDesconhecidaParaTipoResponsavel() {
        // #RESPONSAVEL.NOME# NÃO existe para tipo RESPONSAVEL (só para COROINHA, ACOLITO, etc e TODOS)
        List<String> desconhecidas = Renderizador.tagsDesconhecidas(
                "#RESPONSAVEL.NOME# #RESPONSAVEL.PRIMEIRO_NOME#",
                TipoLayout.RESPONSAVEL);
        assertThat(desconhecidas).containsExactlyInAnyOrder("#RESPONSAVEL.NOME#", "#RESPONSAVEL.PRIMEIRO_NOME#");
    }
}
