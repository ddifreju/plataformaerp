package com.plataforma.autenticacao;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste unitario puro (sem Spring, sem banco) de {@link UsuarioAutenticado}.
 * O ponto critico e {@link UsuarioAutenticado#isEnabled()}: e o unico
 * sinal que faz o {@code DaoAuthenticationProvider} recusar login de
 * usuario/loja desativados (ver ConfiguracaoSeguranca).
 */
class UsuarioAutenticadoTest {

    private static UsuarioParaLogin dados(boolean usuarioAtivo, boolean tenantAtivo) {
        return new UsuarioParaLogin(
                UUID.randomUUID(), UUID.randomUUID(), "dona@loja.com.br", "hash-bcrypt",
                "Dona da Loja", PapelUsuario.DONO, usuarioAtivo, tenantAtivo);
    }

    @Test
    void isEnabledVerdadeiroQuandoUsuarioELojaEstaoAtivos() {
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados(true, true));
        assertTrue(usuario.isEnabled());
    }

    @Test
    void isEnabledFalsoQuandoUsuarioDesativado() {
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados(false, true));
        assertFalse(usuario.isEnabled());
    }

    @Test
    void isEnabledFalsoQuandoLojaDesativada() {
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados(true, false));
        assertFalse(usuario.isEnabled());
    }

    @Test
    void isEnabledFalsoQuandoAmbosDesativados() {
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados(false, false));
        assertFalse(usuario.isEnabled());
    }

    @Test
    void getPasswordDevolveOHashNuncaASenhaEmClaro() {
        UsuarioParaLogin dados = dados(true, true);
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados);
        assertEquals(dados.senhaHash(), usuario.getPassword());
    }

    @Test
    void getUsernameDevolveOEmail() {
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados(true, true));
        assertEquals("dona@loja.com.br", usuario.getUsername());
    }

    @Test
    void getAuthoritiesEstaSempreVazio() {
        // papel NAO e autorizacao nesta fase - ver o Javadoc da classe e
        // de PapelUsuario. Este teste e uma trava contra reintroduzir
        // authority a partir do papel sem uma decisao explicita.
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados(true, true));
        assertTrue(usuario.getAuthorities().isEmpty());
    }

    @Test
    void camposDeIdentidadeNaoAutenticacaoContinuamDisponiveis() {
        UsuarioParaLogin dados = dados(true, true);
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados);

        assertEquals(dados.id(), usuario.usuarioId());
        assertEquals(dados.tenantId(), usuario.tenantId());
        assertEquals(dados.nome(), usuario.nome());
        assertEquals(dados.papel(), usuario.papel());
    }
}
