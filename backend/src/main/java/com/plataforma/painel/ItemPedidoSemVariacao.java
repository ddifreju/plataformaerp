package com.plataforma.painel;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.plataforma.pedido.ItemPedido;

/**
 * Um item de pedido nao casado com nenhuma variacao do catalogo (tarefa
 * 20 - lacuna #2 do catalogo fiscal, secao 9.1).
 */
public record ItemPedidoSemVariacao(
        UUID id, UUID pedidoId, String skuOrigem, String tituloOrigem, OffsetDateTime criadoEm, String acao) {

    private static final String ACAO =
            "Casar este item a uma variacao do catalogo (ou cadastrar o SKU, se ainda nao existir).";

    public static ItemPedidoSemVariacao de(ItemPedido item) {
        return new ItemPedidoSemVariacao(
                item.getId(), item.getPedidoId(), item.getSkuOrigem(), item.getTituloOrigem(),
                item.getCriadoEm(), ACAO);
    }
}
