package com.plataforma.cliente;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes UNITARIOS PUROS (sem Spring, sem banco) de
 * {@link Cliente#atualizarAPartirDaOrigem} - dívida 1 do docs/ESTADO.md
 * ("mesma coisa para Cliente"). Mesma logica de merge de
 * {@code com.plataforma.pedido.PedidoTest}: campos NOT NULL sempre
 * sobrescrevem, campos nullable so sobrescrevem quando a origem traz
 * valor novo.
 */
class ClienteTest {

    private static final UUID CANAL_ID = UUID.randomUUID();

    @Test
    void atualizarAPartirDaOrigemEnriqueceCamposQueEstavamNulos() {
        Cliente existente = new Cliente(CANAL_ID, "comprador-1", TipoCliente.PESSOA_FISICA, null, "apelido-antigo",
                null, null, null, null, null, "{}");

        // Segundo evento do MESMO comprador (mesma chave natural
        // canalId+idExterno) trazendo dado que o primeiro nao tinha.
        Cliente origem = new Cliente(CANAL_ID, "comprador-1", TipoCliente.PESSOA_FISICA, "Maria Silva", "apelido-novo",
                "maria@exemplo.com", "+5511999990000", null, null, null, "{\"v\":2}");

        existente.atualizarAPartirDaOrigem(origem);

        assertEquals("Maria Silva", existente.getNome(), "nome ausente antes tem que ser preenchido pelo dado novo");
        assertEquals("apelido-novo", existente.getApelidoOrigem());
        assertEquals("maria@exemplo.com", existente.getEmail());
        assertEquals("+5511999990000", existente.getTelefone());
        assertEquals("{\"v\":2}", existente.getDadosOrigem(), "dados_origem sempre reflete o ultimo evento visto");
    }

    @Test
    void atualizarAPartirDaOrigemNaoApagaDadoBomComPayloadIncompleto() {
        Cliente existente = new Cliente(CANAL_ID, "comprador-2", TipoCliente.PESSOA_FISICA, "Joao Souza", "joaoz",
                "joao@exemplo.com", "+5511988887777", null, null, null, "{}");

        // Evento seguinte sobre o MESMO comprador, mas o payload desta vez
        // nao repete nome/email/telefone (ex.: um evento so de pedido, sem
        // o cadastro completo do comprador de novo).
        Cliente origemIncompleta = new Cliente(CANAL_ID, "comprador-2", TipoCliente.PESSOA_FISICA, null, null,
                null, null, null, null, null, "{}");

        existente.atualizarAPartirDaOrigem(origemIncompleta);

        assertEquals("Joao Souza", existente.getNome(), "nome bom nao pode ser apagado por um payload incompleto");
        assertEquals("joaoz", existente.getApelidoOrigem());
        assertEquals("joao@exemplo.com", existente.getEmail());
        assertEquals("+5511988887777", existente.getTelefone());
    }

    @Test
    void atualizarAPartirDaOrigemNuncaTocaIdentidadeDaLinha() {
        Cliente existente = new Cliente(CANAL_ID, "comprador-3", TipoCliente.PESSOA_FISICA, "Nome Original", null,
                null, null, null, null, null, "{}");
        UUID idOriginal = existente.getId();
        UUID canalOriginal = existente.getCanalId();
        String idExternoOriginal = existente.getIdExterno();

        Cliente origemComOutraIdentidade = new Cliente(UUID.randomUUID(), "outro-id-externo", TipoCliente.PESSOA_JURIDICA,
                "Outro Nome", null, null, null, null, null, null, "{}");

        existente.atualizarAPartirDaOrigem(origemComOutraIdentidade);

        assertEquals(idOriginal, existente.getId(), "id nunca muda");
        assertEquals(canalOriginal, existente.getCanalId(), "canal_id nunca muda - e parte da chave natural");
        assertEquals(idExternoOriginal, existente.getIdExterno(), "id_externo nunca muda - e parte da chave natural");
    }
}
