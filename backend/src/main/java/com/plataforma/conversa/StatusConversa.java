package com.plataforma.conversa;

/**
 * Dominio de conversa.status (migration V011, CONSTRAINT ck_conversa_status).
 */
public enum StatusConversa {
    ABERTA,
    AGUARDANDO_CLIENTE,
    AGUARDANDO_LOJA,
    RESOLVIDA,
    ARQUIVADA
}
