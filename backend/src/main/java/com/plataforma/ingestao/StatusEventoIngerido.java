package com.plataforma.ingestao;

/**
 * Dominio de evento_ingerido.status (migration V012, CONSTRAINT
 * ck_evento_ingerido_status).
 */
public enum StatusEventoIngerido {
    RECEBIDO,
    PROCESSANDO,
    PROCESSADO,
    IGNORADO,
    ERRO
}
