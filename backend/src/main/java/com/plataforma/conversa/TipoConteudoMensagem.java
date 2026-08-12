package com.plataforma.conversa;

/**
 * Dominio de mensagem.tipo_conteudo (migration V011, CONSTRAINT
 * ck_mensagem_tipo_conteudo).
 */
public enum TipoConteudoMensagem {
    TEXTO,
    IMAGEM,
    AUDIO,
    VIDEO,
    ARQUIVO,
    LOCALIZACAO,
    SISTEMA
}
