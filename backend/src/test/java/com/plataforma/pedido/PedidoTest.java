package com.plataforma.pedido;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes UNITARIOS PUROS (sem Spring, sem banco) de
 * {@link Pedido#atualizarAPartirDaOrigem} - dívida 1 do docs/ESTADO.md.
 * Cobre as duas regras de merge documentadas no proprio metodo:
 * sobrescrita incondicional para status/valores/datas/dados_origem, e
 * "nunca apaga com null" para os campos nullable de enriquecimento.
 * Asserções monetárias sempre via compareTo (BigDecimal.equals falharia
 * por escala diferente mesmo com o mesmo valor).
 */
class PedidoTest {

    private static final UUID CANAL_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @Test
    void atualizarAPartirDaOrigemSobrescreveStatusValoresDatasEDadosOrigem() {
        Pedido existente = new Pedido(CANAL_ID, CLIENTE_ID, "pedido-1", "COD-1",
                StatusPedido.AGUARDANDO_PAGAMENTO, "pending", OffsetDateTime.parse("2024-01-01T10:00:00Z"),
                new BigDecimal("100.0000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.0000"),
                null, "BRL", null, null, null, null, null, "{\"v\":1}");

        Pedido origem = new Pedido(CANAL_ID, CLIENTE_ID, "pedido-1", "COD-1",
                StatusPedido.PAGO, "paid", OffsetDateTime.parse("2024-01-02T10:00:00Z"),
                new BigDecimal("150.0000"), new BigDecimal("10.0000"), new BigDecimal("5.0000"), new BigDecimal("145.0000"),
                null, "BRL", null, null, null, null, null, "{\"v\":2}");

        existente.atualizarAPartirDaOrigem(origem);

        assertEquals(StatusPedido.PAGO, existente.getStatus(),
                "o caso motivador da dívida 1: 'aguardando pagamento' -> 'pago' tem que refletir no pedido");
        assertEquals("paid", existente.getStatusOrigem());
        assertEquals(0, existente.getValorBrutoItens().compareTo(new BigDecimal("150.0000")));
        assertEquals(0, existente.getValorDesconto().compareTo(new BigDecimal("10.0000")));
        assertEquals(0, existente.getValorFreteCobrado().compareTo(new BigDecimal("5.0000")));
        assertEquals(0, existente.getValorTotalPedido().compareTo(new BigDecimal("145.0000")));
        assertEquals(OffsetDateTime.parse("2024-01-02T10:00:00Z"), existente.getFeitoEm());
        assertEquals("{\"v\":2}", existente.getDadosOrigem());
    }

    @Test
    void atualizarAPartirDaOrigemNaoApagaCampoNullableComDadoBomJaExistente() {
        Pedido existente = new Pedido(CANAL_ID, CLIENTE_ID, "pedido-2", "COD-2",
                StatusPedido.PAGO, "paid", OffsetDateTime.parse("2024-01-01T10:00:00Z"),
                new BigDecimal("100.0000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.0000"),
                new BigDecimal("90.0000"), "BRL", FormaPagamento.PIX, (short) 1,
                "04101000", "Sao Paulo", "SP", "{}");

        // Payload de atualizacao mais ESTREITO que o original: so trouxe
        // status novo, sem repetir cep/cidade/uf/forma_pagamento/parcelas/
        // repasse/codigo_exibicao - cenario tipico de um webhook so de
        // mudanca de status.
        Pedido origemEstreita = new Pedido(CANAL_ID, CLIENTE_ID, "pedido-2", null,
                StatusPedido.ENVIADO, "shipped", OffsetDateTime.parse("2024-01-01T10:00:00Z"),
                new BigDecimal("100.0000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.0000"),
                null, "BRL", null, null, null, null, null, "{}");

        existente.atualizarAPartirDaOrigem(origemEstreita);

        assertEquals(StatusPedido.ENVIADO, existente.getStatus(), "status sempre atualiza, mesmo com payload estreito");
        assertEquals(0, existente.getValorRepassePrevisto().compareTo(new BigDecimal("90.0000")),
                "valor_repasse_previsto bom nao pode ser apagado por um payload que nao trouxe o campo");
        assertEquals(FormaPagamento.PIX, existente.getFormaPagamento(), "forma_pagamento preservada");
        assertEquals((short) 1, existente.getQuantidadeParcelas(), "quantidade_parcelas preservada");
        assertEquals("04101000", existente.getCepEntrega(), "cep_entrega preservado");
        assertEquals("Sao Paulo", existente.getCidadeEntrega(), "cidade_entrega preservada");
        assertEquals("SP", existente.getUfEntrega(), "uf_entrega preservada");
        assertEquals("COD-2", existente.getCodigoExibicao(), "codigo_exibicao preservado");
    }

    @Test
    void atualizarAPartirDaOrigemNuncaTocaIdentidadeDaLinha() {
        Pedido existente = new Pedido(CANAL_ID, CLIENTE_ID, "pedido-3", "COD-3",
                StatusPedido.AGUARDANDO_PAGAMENTO, "pending", OffsetDateTime.parse("2024-01-01T10:00:00Z"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, "BRL", null, null, null, null, null, "{}");
        UUID idOriginal = existente.getId();
        UUID canalOriginal = existente.getCanalId();
        String idExternoOriginal = existente.getIdExterno();

        // origem com identidade DIFERENTE de proposito - para provar que o
        // metodo simplesmente NAO TOCA em canalId/idExterno/id, nao so que
        // "por coincidencia" o valor bateria.
        Pedido origemComOutraIdentidade = new Pedido(UUID.randomUUID(), CLIENTE_ID, "outro-id-externo", "COD-3",
                StatusPedido.PAGO, "paid", OffsetDateTime.parse("2024-01-02T10:00:00Z"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, "BRL", null, null, null, null, null, "{}");

        existente.atualizarAPartirDaOrigem(origemComOutraIdentidade);

        assertEquals(idOriginal, existente.getId(), "id nunca muda");
        assertEquals(canalOriginal, existente.getCanalId(), "canal_id nunca muda - e parte da chave natural");
        assertEquals(idExternoOriginal, existente.getIdExterno(), "id_externo nunca muda - e parte da chave natural");
    }
}
