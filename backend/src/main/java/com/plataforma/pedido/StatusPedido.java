package com.plataforma.pedido;

/**
 * Dominio de pedido.status (migration V008, CONSTRAINT ck_pedido_status).
 * Status CANONICO, mesmo para toda fonte. Disputa/mediacao NAO entra
 * aqui: vive em devolucao.status (ver comentario da coluna no SQL).
 */
public enum StatusPedido {
    AGUARDANDO_PAGAMENTO,
    PAGAMENTO_RECUSADO,
    PAGO,
    EM_SEPARACAO,
    ENVIADO,
    ENTREGUE,
    CANCELADO,
    DEVOLVIDO
}
