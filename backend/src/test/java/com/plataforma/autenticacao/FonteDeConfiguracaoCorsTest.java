package com.plataforma.autenticacao;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste unitario puro (sem Spring context, sem banco, sem Docker) de
 * {@link ConfiguracaoSeguranca#fonteDeConfiguracaoCors()} - tarefa 29
 * (decisao 0032). Prova as tres garantias que a decisao 0025 exige:
 *
 * (a) lista vazia nao permite origem alguma;
 * (b) a origem de dev e permitida quando configurada, e so ela;
 * (c) "*" nunca aparece - a aplicacao recusa subir antes disso.
 *
 * {@code checkOrigin} (nao so {@code getAllowedOrigins}) e o metodo certo
 * para a pergunta "esta origem passaria pelo CORS?": e o mesmo que o
 * Spring usa em tempo de requisicao para aceitar ou recusar um Origin.
 */
class FonteDeConfiguracaoCorsTest {

    private CorsConfiguration configuracaoParaCaminhoDaApi(CorsConfigurationSource fonte) {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/api/qualquer");
        CorsConfiguration configuracao = fonte.getCorsConfiguration(requisicao);
        assertNotNull(configuracao, "/api/** precisa ter uma CorsConfiguration registrada, mesmo que vazia");
        return configuracao;
    }

    @Test
    void listaVaziaNaoPermiteNenhumaOrigem() {
        CorsConfigurationSource fonte = new ConfiguracaoSeguranca("").fonteDeConfiguracaoCors();
        CorsConfiguration configuracao = configuracaoParaCaminhoDaApi(fonte);

        assertNull(configuracao.checkOrigin("http://localhost:3000"));
        assertNull(configuracao.checkOrigin("https://qualquer-coisa.com"));
    }

    @Test
    void origemDeDevPermitidaQuandoConfigurada() {
        CorsConfigurationSource fonte =
                new ConfiguracaoSeguranca("http://localhost:3000").fonteDeConfiguracaoCors();
        CorsConfiguration configuracao = configuracaoParaCaminhoDaApi(fonte);

        assertEquals("http://localhost:3000", configuracao.checkOrigin("http://localhost:3000"));
        // Uma origem nao listada continua recusada - a allowlist e exata,
        // nao "libera tudo que parece dev".
        assertNull(configuracao.checkOrigin("http://localhost:4000"));
    }

    @Test
    void aceitaMaisDeUmaOrigemSeparadaPorVirgula() {
        CorsConfigurationSource fonte = new ConfiguracaoSeguranca(
                "http://localhost:3000, https://staging.exemplo.com").fonteDeConfiguracaoCors();
        CorsConfiguration configuracao = configuracaoParaCaminhoDaApi(fonte);

        assertNotNull(configuracao.checkOrigin("http://localhost:3000"));
        assertNotNull(configuracao.checkOrigin("https://staging.exemplo.com"));
    }

    @Test
    void asteriscoNuncaAparecePorqueDerrubaAAplicacaoNaSubida() {
        OrigemCorsInvalidaException excecao = assertThrows(OrigemCorsInvalidaException.class,
                () -> new ConfiguracaoSeguranca("*").fonteDeConfiguracaoCors());

        assertTrue(excecao.getMessage().contains("*"));
    }

    @Test
    void asteriscoMisturadoComOutraOrigemTambemDerruba() {
        assertThrows(OrigemCorsInvalidaException.class,
                () -> new ConfiguracaoSeguranca("http://localhost:3000,*").fonteDeConfiguracaoCors());
    }

    @Test
    void allowCredentialsPermaneceLigadoMesmoComListaVazia() {
        // allowCredentials(true) sozinho nao e o problema - "*" com
        // allowCredentials(true) e que seria. Uma lista vazia com
        // allowCredentials(true) e inofensiva: nao ha origem para o
        // cookie viajar de qualquer forma.
        UrlBasedCorsConfigurationSource fonte =
                (UrlBasedCorsConfigurationSource) new ConfiguracaoSeguranca("").fonteDeConfiguracaoCors();
        CorsConfiguration configuracao = fonte.getCorsConfigurations().get("/api/**");

        assertTrue(configuracao.getAllowCredentials());
        assertNull(configuracao.getAllowedOrigins());
    }
}
