package com.plataforma.autenticacao;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitario puro (sem Spring context, sem banco) de
 * {@link FiltroLoginJson}: prova que ele extrai email/senha tanto de um
 * corpo JSON quanto de parametros de formulario, e delega os DOIS para o
 * mesmo {@link AuthenticationManager} - a parte que a tarefa 17 pede
 * ("login por formulario/JSON").
 */
class FiltroLoginJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthenticationManager gerenciador = mock(AuthenticationManager.class);
    private final FiltroLoginJson filtro = new FiltroLoginJson(gerenciador, objectMapper);

    @Test
    void extraiEmailESenhaDeCorpoJson() throws Exception {
        Authentication autenticacaoDevolvida = mock(Authentication.class);
        when(gerenciador.authenticate(any())).thenReturn(autenticacaoDevolvida);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/json");
        byte[] corpo = objectMapper.writeValueAsBytes(new CredenciaisLogin("dona@loja.com.br", "senha-correta"));
        request.setContent(corpo);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication resultado = filtro.attemptAuthentication(request, response);

        assertEquals(autenticacaoDevolvida, resultado);
        var captor = org.mockito.ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(gerenciador).authenticate(captor.capture());
        assertEquals("dona@loja.com.br", captor.getValue().getPrincipal());
        assertEquals("senha-correta", captor.getValue().getCredentials());
    }

    @Test
    void corpoJsonMalformadoViraCredencialVaziaNuncaErroInterno() throws Exception {
        Authentication autenticacaoDevolvida = mock(Authentication.class);
        when(gerenciador.authenticate(any())).thenReturn(autenticacaoDevolvida);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/json");
        request.setContent("{ isto nao e json valido".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Nao pode lancar excecao de parsing - o corpo quebrado tem que
        // virar uma tentativa de login com credencial vazia, que o
        // AuthenticationManager (ou o UserDetailsService por tras dele)
        // recusa normalmente, terminando na MESMA mensagem generica de
        // falha (armadilha 5 da V014).
        filtro.attemptAuthentication(request, response);

        var captor = org.mockito.ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(gerenciador).authenticate(captor.capture());
        assertEquals("", captor.getValue().getPrincipal());
        assertEquals("", captor.getValue().getCredentials());
    }

    @Test
    void extraiEmailESenhaDeParametrosDeFormulario() {
        Authentication autenticacaoDevolvida = mock(Authentication.class);
        when(gerenciador.authenticate(any())).thenReturn(autenticacaoDevolvida);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        request.setContentType("application/x-www-form-urlencoded");
        request.setParameter("email", "gestor@loja.com.br");
        request.setParameter("senha", "outra-senha");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.attemptAuthentication(request, response);

        var captor = org.mockito.ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(gerenciador).authenticate(captor.capture());
        assertEquals("gestor@loja.com.br", captor.getValue().getPrincipal());
        assertEquals("outra-senha", captor.getValue().getCredentials());
    }
}
