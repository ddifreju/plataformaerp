package com.plataforma.margem;

/**
 * Dominio de taxa_canal.base_incidencia (migration V013). Sobre que valor
 * o percentual incide, e qual valor a faixa de preco compara - muda por
 * tipo de taxa (a tarifa fixa do ML tem faixa por preco UNITARIO, a
 * comissao incide sobre a linha do item). O valor da linha vencedora e o
 * que o motor grava em {@code custo.base_calculo} (V010).
 */
public enum BaseIncidencia {
    /** Preco de UMA unidade - a faixa da tarifa fixa do ML (abaixo de R$ 12,50 / ate R$ 79,00). */
    VALOR_UNITARIO_ITEM,
    /** Preco unitario x quantidade da linha. */
    VALOR_TOTAL_ITEM,
    /** {@code pedido.valor_total_pedido}. */
    VALOR_TOTAL_PEDIDO,
    /** Tarifa de envio (subsidio, frete reverso). */
    VALOR_FRETE,
    /** Montante antecipado (antecipacao). */
    VALOR_A_RECEBER
}
