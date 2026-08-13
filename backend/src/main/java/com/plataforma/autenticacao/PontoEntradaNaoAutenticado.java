package com.plataforma.autenticacao;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.web.ErroApi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * O que a API devolve quando uma rota protegida e chamada sem sessao.
 *
 * Escreve a resposta diretamente (mesmo formato {@link ErroApi} do resto
 * da API) em vez de redirecionar para uma pagina de login: esta e uma API
 * JSON, nao uma aplicacao web com pagina de login renderizada pelo
 * servidor - o comportamento padrao do Spring Security aqui seria um
 * redirect HTTP para "/login", que nao existe e nao faz sentido para um
 * cliente que espera JSON.
 */
public class PontoEntradaNaoAutenticado implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public PontoEntradaNaoAutenticado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ErroApi("nao_autenticado", "Faca login para continuar."));
    }
}
