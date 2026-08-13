package com.plataforma.autenticacao;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.web.ErroApi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Resposta padrao para 403 dentro do ciclo do Spring Security.
 *
 * Sem uso pratico HOJE - {@code papel} nao e autorizacao nesta fase (ver
 * PapelUsuario), entao nenhuma regra de {@code authorizeHttpRequests}
 * ainda nega acesso a um usuario autenticado. Registrado mesmo assim
 * porque o handler PADRAO do Spring Security devolveria uma pagina HTML
 * de erro, o que quebraria o contrato "toda resposta de erro desta API e
 * JSON" no dia em que a primeira regra de autorizacao entrar.
 */
public class TratadorAcessoNegado implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public TratadorAcessoNegado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ErroApi("acesso_negado", "Sem permissao para este recurso."));
    }
}
