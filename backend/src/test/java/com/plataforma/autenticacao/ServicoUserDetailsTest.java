package com.plataforma.autenticacao;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitario puro (Mockito, sem Spring, sem banco) de
 * {@link ServicoUserDetails}. RepositorioLoginUsuario e mockado: o que
 * importa aqui e o CONTRATO com o Spring Security (devolver
 * UsuarioAutenticado, ou lancar UsernameNotFoundException), nao a query
 * SQL de verdade (isso exigiria Postgres - fora do escopo de um teste
 * puro).
 */
class ServicoUserDetailsTest {

    private final RepositorioLoginUsuario repositorioLoginUsuario = mock(RepositorioLoginUsuario.class);
    private final ServicoUserDetails servico = new ServicoUserDetails(repositorioLoginUsuario);

    @Test
    void devolveUsuarioAutenticadoQuandoEmailExiste() {
        UsuarioParaLogin dados = new UsuarioParaLogin(
                UUID.randomUUID(), UUID.randomUUID(), "dona@loja.com.br", "hash-bcrypt",
                "Dona da Loja", PapelUsuario.DONO, true, true);
        when(repositorioLoginUsuario.buscarPorEmail("dona@loja.com.br")).thenReturn(Optional.of(dados));

        UserDetails resultado = servico.loadUserByUsername("dona@loja.com.br");

        UsuarioAutenticado usuario = assertInstanceOf(UsuarioAutenticado.class, resultado);
        assertEquals(dados.id(), usuario.usuarioId());
        assertEquals(dados.email(), usuario.getUsername());
    }

    @Test
    void lancaUsernameNotFoundQuandoEmailNaoExiste() {
        // Esta excecao NUNCA deve chegar ao cliente com esta mensagem -
        // ConfiguracaoSeguranca liga hideUserNotFoundExceptions para que
        // o DaoAuthenticationProvider a converta em BadCredentialsException
        // antes de qualquer resposta HTTP (armadilha 5 da V014). Este
        // teste so garante que ESTA classe lanca o tipo certo para que
        // aquele mecanismo funcione.
        when(repositorioLoginUsuario.buscarPorEmail("fantasma@loja.com.br")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> servico.loadUserByUsername("fantasma@loja.com.br"));
    }
}
