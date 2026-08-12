package com.plataforma.devolucao;

/**
 * Dominio de devolucao.responsavel_frete_reverso (migration V009,
 * CONSTRAINT ck_devolucao_responsavel_frete). Nullable: so faz sentido
 * quando ha frete de retorno a atribuir.
 */
public enum ResponsavelFreteReverso {
    VENDEDOR,
    COMPRADOR,
    CANAL,
    NAO_SE_APLICA
}
