package com.plataforma.devolucao;

/**
 * Dominio de devolucao.status (migration V009,
 * CONSTRAINT ck_devolucao_status).
 */
public enum StatusDevolucao {
    ABERTA,
    EM_ANALISE,
    EM_MEDIACAO,
    APROVADA,
    RECUSADA,
    EM_TRANSITO,
    RECEBIDA,
    CONCLUIDA,
    CANCELADA
}
