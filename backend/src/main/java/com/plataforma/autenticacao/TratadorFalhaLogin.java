package com.plataforma.autenticacao;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.web.ErroApi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * MENSAGEM UNICA para toda falha de login (tarefa 17: "falha de login nao
 * distingue e-mail nao existe de senha errada"; armadilha 5 da V014 vai
 * mais longe e lista quatro casos - e-mail inexistente, senha errada,
 * usuario desativado, loja desativada - que respondem exatamente a mesma
 * coisa). Este handler NAO olha o tipo da {@link AuthenticationException}
 * para decidir o que escrever na resposta - so para o log interno.
 *
 * TIMING, nota honesta: hideUserNotFoundExceptions=true
 * (ConfiguracaoSeguranca) roda um BCrypt "de mentira" quando o e-mail nao
 * existe, para que o TEMPO de resposta tambem nao denuncie a ausencia da
 * conta. Para usuario/loja DESATIVADOS, o Spring Security recusa antes de
 * comparar a senha (DisabledException, lancada em preAuthenticationChecks
 * - ver AbstractUserDetailsAuthenticationProvider), entao essa chamada
 * especifica e mais rapida que uma tentativa com senha errada. E uma
 * diferenca de tempo pequena e so relevante para o caso raro de conta
 * desativada; a MENSAGEM (o que importa para nao virar oraculo publico de
 * contas) e sempre identica. Registrado aqui como limitacao conhecida, nao
 * como descuido.
 */
public class TratadorFalhaLogin implements AuthenticationFailureHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorFalhaLogin.class);

    private final ObjectMapper objectMapper;

    public TratadorFalhaLogin(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        // O motivo real fica SO no log do servidor - nunca em log o
        // e-mail/senha tentados, so a CLASSE da excecao (nunca contem o
        // dado informado pelo cliente).
        LOG.info("Falha de login: {}", exception.getClass().getSimpleName());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ErroApi("credenciais_invalidas", "E-mail ou senha invalidos."));
    }
}
