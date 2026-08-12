package com.plataforma.devolucao;

/**
 * Dominio de devolucao.destino_produto (migration V009,
 * CONSTRAINT ck_devolucao_destino_produto). O que aconteceu com o produto
 * devolvido - separa a devolucao que custou so o frete da que custou o
 * produto inteiro (DESCARTE).
 */
public enum DestinoProduto {
    NAO_RETORNOU,
    ESTOQUE,
    ESTOQUE_COMO_SEGUNDA_LINHA,
    ASSISTENCIA,
    DEVOLVIDO_AO_FORNECEDOR,
    DESCARTE
}
