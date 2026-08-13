package com.plataforma.autenticacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste unitario puro de {@link TratadorFalhaLogin}: a regra de negocio
 * central da tarefa 17 - "falha de login nao distingue e-mail nao existe
 * de senha errada" (e, mais amplo, os quatro casos da armadilha 5 da
 * V014) - vira aqui uma asserção DURA: TRES tipos de excecao diferentes
 * (senha errada, usuario desativado, conta bloqueada) produzem o MESMO
 * corpo de resposta, byte a byte.
 */
class TratadorFalhaLoginTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TratadorFalhaLogin tratador = new TratadorFalhaLogin(objectMapper);

    @Test
    void senhaErradaEUsuarioDesativadoProduzemAMesmaRespostaExata() throws Exception {
        String corpoSenhaErrada = corpoDaResposta(new BadCredentialsException("senha nao confere"));
        String corpoUsuarioDesativado = corpoDaResposta(new DisabledException("usuario inativo"));
        String corpoContaBloqueada = corpoDaResposta(new LockedException("conta bloqueada"));

        assertEquals(corpoSenhaErrada, corpoUsuarioDesativado,
                "cliente nao pode distinguir 'senha errada' de 'usuario desativado' pela resposta");
        assertEquals(corpoSenhaErrada, corpoContaBloqueada,
                "cliente nao pode distinguir 'senha errada' de 'conta bloqueada' pela resposta");
    }

    @Test
    void statusEQuatroZeroUmParaQualquerFalha() throws Exception {
        MockHttpServletResponse response = falhar(new BadCredentialsException("qualquer motivo"));
        assertEquals(401, response.getStatus());
    }

    @Test
    void corpoTemFormatoErroApiSemDetalheInterno() throws Exception {
        MockHttpServletResponse response = falhar(new BadCredentialsException("detalhe que NAO pode vazar"));
        JsonNode json = objectMapper.readTree(response.getContentAsString());

        assertEquals("credenciais_invalidas", json.get("erro").asText());
        assertEquals("E-mail ou senha invalidos.", json.get("mensagem").asText());
        // A mensagem interna da excecao original nunca aparece na resposta.
        String corpoEmMinusculas = response.getContentAsString().toLowerCase();
        org.junit.jupiter.api.Assertions.assertFalse(corpoEmMinusculas.contains("detalhe que nao pode vazar"));
    }

    private String corpoDaResposta(AuthenticationException excecao) throws Exception {
        return falhar(excecao).getContentAsString();
    }

    private MockHttpServletResponse falhar(AuthenticationException excecao) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        tratador.onAuthenticationFailure(request, response, excecao);
        return response;
    }
}
