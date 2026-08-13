package com.plataforma.autenticacao;

import java.lang.reflect.RecordComponent;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.comum.tenant.RepositorioTenant;
import com.plataforma.comum.tenant.Tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitario puro de {@link ServicoSessao}: prova o mapeamento de
 * {@link UsuarioAutenticado} para {@link RespostaSessao} (tarefa 17, item
 * 7 - "devolvendo usuario logado para o frontend saber o que mostrar").
 *
 * {@link Tenant} e mockado (nao construido com "new"): a entidade e
 * deliberadamente somente-leitura, sem construtor publico (ver o Javadoc
 * dela) - Mockito cria um proxy sem passar pelo construtor, entao isto
 * nao exige mudar a entidade so para o teste.
 */
class ServicoSessaoTest {

    private final RepositorioTenant repositorioTenant = mock(RepositorioTenant.class);
    private final ServicoSessao servico = new ServicoSessao(repositorioTenant);

    private static UsuarioAutenticado usuarioAutenticado(UUID tenantId) {
        UsuarioParaLogin dados = new UsuarioParaLogin(
                UUID.randomUUID(), tenantId, "gestor@loja.com.br", "hash-nunca-exposto",
                "Gestor da Loja", PapelUsuario.GESTOR, true, true);
        return new UsuarioAutenticado(dados);
    }

    @Test
    void mapeiaNomePapelETenantIdDoUsuarioAutenticado() {
        UUID tenantId = UUID.randomUUID();
        UsuarioAutenticado usuario = usuarioAutenticado(tenantId);
        Tenant tenant = mock(Tenant.class);
        when(tenant.getNome()).thenReturn("Loja da Juliana");
        when(repositorioTenant.findById(tenantId)).thenReturn(Optional.of(tenant));

        RespostaSessao resposta = servico.sessaoAtual(usuario);

        assertEquals("gestor@loja.com.br", resposta.email());
        assertEquals("Gestor da Loja", resposta.nome());
        assertEquals(PapelUsuario.GESTOR, resposta.papel());
        assertEquals(tenantId, resposta.tenantId());
        assertEquals("Loja da Juliana", resposta.tenantNome());
    }

    @Test
    void tenantNaoEncontradoDevolveNomeNuloSemLancar() {
        // Caminho de erro: na pratica nunca deveria acontecer (o
        // FiltroTenant ja confirmou que o tenant existe e esta ativo
        // antes deste endpoint rodar), mas a ausencia nao pode virar
        // NullPointerException numa tela que so quer mostrar quem esta
        // logado.
        UUID tenantId = UUID.randomUUID();
        UsuarioAutenticado usuario = usuarioAutenticado(tenantId);
        when(repositorioTenant.findById(tenantId)).thenReturn(Optional.empty());

        RespostaSessao resposta = servico.sessaoAtual(usuario);

        assertNull(resposta.tenantNome());
        assertEquals(tenantId, resposta.tenantId());
    }

    @Test
    void respostaSessaoNuncaTemCampoDeSenha() {
        // Guarda de regressao para o requisito "NUNCA devolva senha_hash"
        // (tarefa 17, item 7): confere por reflexao que nenhum componente
        // do record menciona senha/hash/password, em vez de confiar so em
        // "ninguem vai adicionar isso por engano".
        for (RecordComponent componente : RespostaSessao.class.getRecordComponents()) {
            String nome = componente.getName().toLowerCase();
            assertFalse(nome.contains("senha"), "RespostaSessao nao pode ter campo de senha: " + nome);
            assertFalse(nome.contains("password"), "RespostaSessao nao pode ter campo de senha: " + nome);
            assertFalse(nome.contains("hash"), "RespostaSessao nao pode ter campo de hash: " + nome);
        }
    }
}
