package com.plataforma.autenticacao;

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Login "por formulario/JSON" (tarefa 17, item 2): o
 * {@code UsernamePasswordAuthenticationFilter} padrao do Spring Security
 * so entende {@code application/x-www-form-urlencoded}. O frontend
 * (Next.js) manda JSON, entao este filtro le o corpo como
 * {@link CredenciaisLogin} quando o {@code Content-Type} e JSON, e cai no
 * comportamento padrao (parametros de formulario) nos demais casos - um
 * filtro so, os dois formatos.
 *
 * Substitui o filtro padrao (nao e adicionado ALEM dele - ver
 * {@code ConfiguracaoSeguranca.addFilterAt}), entao os campos de
 * usuario/senha sao renomeados aqui para "email"/"senha", em portugues,
 * consistente com o resto da API.
 */
public class FiltroLoginJson extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper objectMapper;

    public FiltroLoginJson(AuthenticationManager authenticationManager, ObjectMapper objectMapper) {
        super(authenticationManager);
        this.objectMapper = objectMapper;
        setFilterProcessesUrl("/api/login");
        setUsernameParameter("email");
        setPasswordParameter("senha");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        String tipoConteudo = request.getContentType();
        boolean corpoEJson = tipoConteudo != null && tipoConteudo.toLowerCase(Locale.ROOT).contains("json");

        if (!corpoEJson) {
            // Form urlencoded: comportamento padrao da classe-mae, so com
            // os nomes de campo "email"/"senha" ja configurados acima.
            return super.attemptAuthentication(request, response);
        }

        CredenciaisLogin credenciais;
        try {
            credenciais = objectMapper.readValue(request.getInputStream(), CredenciaisLogin.class);
        } catch (IOException erroDeLeitura) {
            // Corpo JSON malformado vira credencial vazia, NUNCA um 500:
            // do ponto de vista de quem chamou a API, "mandei um JSON
            // quebrado" e "mandei a senha errada" devem terminar na MESMA
            // mensagem generica de falha de login (armadilha 5 da V014).
            credenciais = new CredenciaisLogin("", "");
        }

        String email = credenciais.email() != null ? credenciais.email() : "";
        String senha = credenciais.senha() != null ? credenciais.senha() : "";

        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(email, senha);
        setDetails(request, token);
        return this.getAuthenticationManager().authenticate(token);
    }
}
