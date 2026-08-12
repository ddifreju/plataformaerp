package com.plataforma.conversa;

/**
 * Dominio de mensagem.direcao (migration V011, CONSTRAINT
 * ck_mensagem_direcao). Do ponto de vista DA LOJA: ENTRADA = a loja
 * recebeu, SAIDA = a loja enviou (ver comentario da coluna no SQL -
 * inverter esta leitura inverte a metrica de tempo de resposta).
 */
public enum DirecaoMensagem {
    ENTRADA,
    SAIDA
}
