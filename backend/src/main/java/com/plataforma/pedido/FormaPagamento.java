package com.plataforma.pedido;

/**
 * Dominio de pedido.forma_pagamento (migration V008,
 * CONSTRAINT ck_pedido_forma_pagamento). Nullable: nem toda fonte
 * informa a forma de pagamento no momento da ingestao.
 */
public enum FormaPagamento {
    PIX,
    BOLETO,
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    SALDO_CANAL,
    TRANSFERENCIA,
    DINHEIRO,
    OUTRO
}
