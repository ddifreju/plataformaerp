package com.plataforma.conversa;

/**
 * Dominio de mensagem.autor_tipo (migration V011, CONSTRAINT
 * ck_mensagem_autor_tipo). ATENDENTE e AUTOMACAO separados de proposito:
 * medir quanto a IA respondeu sozinha e objetivo do produto.
 */
public enum TipoAutorMensagem {
    CLIENTE,
    ATENDENTE,
    AUTOMACAO,
    CANAL
}
