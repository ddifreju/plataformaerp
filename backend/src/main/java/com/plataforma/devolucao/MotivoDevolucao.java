package com.plataforma.devolucao;

/**
 * Dominio de devolucao.motivo e item_devolucao.motivo (migration V009,
 * CONSTRAINT ck_devolucao_motivo e ck_item_devolucao_motivo - o MESMO
 * dominio nas duas colunas, confirmado linha a linha no SQL). Nullable
 * nas duas: quando o motivo do item e null, vale o motivo da devolucao
 * (ver comentario da coluna em item_devolucao no SQL).
 *
 * Nao e detalhe de relatorio: define de quem e o prejuizo (ver comentario
 * da coluna devolucao.motivo no SQL).
 */
public enum MotivoDevolucao {
    ARREPENDIMENTO,
    PRODUTO_COM_DEFEITO,
    PRODUTO_DIFERENTE_DO_ANUNCIO,
    AVARIA_NO_TRANSPORTE,
    ATRASO_NA_ENTREGA,
    NAO_ENTREGUE,
    ERRO_DE_ENVIO_DA_LOJA,
    FRAUDE,
    OUTRO
}
