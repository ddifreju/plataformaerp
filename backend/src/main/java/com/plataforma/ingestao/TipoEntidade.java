package com.plataforma.ingestao;

/**
 * Dominio de evento_ingerido.entidade_tipo (migration V012, CONSTRAINT
 * ck_evento_ingerido_entidade_tipo). Divergencia real e conferida linha a
 * linha contra o SQL: este CHECK e um SUBCONJUNTO do dominio de
 * tipo_evento (TipoEvento) - falta ESTOQUE e falta OUTRO. Faz sentido:
 * entidade_tipo/entidade_id sao o "ponteiro de diagnostico" para a linha
 * CANONICA gerada pelo evento (ver comentario da coluna no SQL), e nao
 * existe tabela canonica "estoque" nem "outro" para apontar - so
 * entidades de negocio de verdade. Por isso e um enum Java SEPARADO de
 * TipoEvento, mesmo repetindo 9 dos 11 valores: sao dominios diferentes
 * no banco (duas CONSTRAINTs distintas), e fundir os dois em um so
 * enum permitiria valores invalidos em entidade_tipo (ESTOQUE/OUTRO) que
 * o CHECK do banco rejeitaria em runtime.
 */
public enum TipoEntidade {
    PEDIDO,
    ITEM_PEDIDO,
    PRODUTO,
    VARIACAO,
    CLIENTE,
    DEVOLUCAO,
    CONVERSA,
    MENSAGEM,
    CUSTO
}
