package com.plataforma.autenticacao;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitario puro de {@link ServicoLogin}. O comportamento que
 * importa: NUNCA lanca, mesmo quando o UPDATE afeta zero linhas
 * (armadilha 3 da V014 - e um carimbo auxiliar, nao parte do caminho
 * critico de autenticacao).
 */
class ServicoLoginTest {

    private final RepositorioUsuario repositorioUsuario = mock(RepositorioUsuario.class);
    private final ServicoLogin servico = new ServicoLogin(repositorioUsuario);

    @Test
    void chamaMarcarUltimoAcessoComOIdDoUsuario() {
        UUID usuarioId = UUID.randomUUID();
        when(repositorioUsuario.marcarUltimoAcesso(eq(usuarioId), any(OffsetDateTime.class))).thenReturn(1);

        servico.registrarAcessoBemSucedido(usuarioId);

        verify(repositorioUsuario).marcarUltimoAcesso(eq(usuarioId), any(OffsetDateTime.class));
    }

    @Test
    void naoLancaQuandoUpdateAfetaZeroLinhas() {
        UUID usuarioId = UUID.randomUUID();
        when(repositorioUsuario.marcarUltimoAcesso(eq(usuarioId), any(OffsetDateTime.class))).thenReturn(0);

        // So loga um aviso internamente - o login ja aconteceu, este
        // carimbo nao pode derrubar a resposta ao cliente.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> servico.registrarAcessoBemSucedido(usuarioId));
    }
}
