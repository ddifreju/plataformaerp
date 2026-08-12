package com.plataforma.ingestao;

/**
 * Dominio de evento_ingerido.tipo_evento (migration V012, CONSTRAINT
 * ck_evento_ingerido_tipo_evento). O que o payload representa - faz
 * parte da chave natural de idempotencia (tenant_id, canal_id,
 * tipo_evento, id_externo).
 *
 * ATENCAO: este dominio tem ESTOQUE e OUTRO, que o dominio irmao
 * (TipoEntidade, coluna entidade_tipo) NAO tem - sao CHECKs diferentes no
 * SQL, mesmo os dois parecendo "o mesmo tipo de coisa". Ver TipoEntidade.
 */
public enum TipoEvento {
    PEDIDO,
    ITEM_PEDIDO,
    PRODUTO,
    VARIACAO,
    CLIENTE,
    DEVOLUCAO,
    CONVERSA,
    MENSAGEM,
    CUSTO,
    ESTOQUE,
    OUTRO
}
