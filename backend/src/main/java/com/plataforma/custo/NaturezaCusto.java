package com.plataforma.custo;

/**
 * Dominio de custo.natureza (migration V010, CONSTRAINT ck_custo_natureza).
 * Cada valor e um custo real do e-commerce brasileiro - ver o comentario
 * completo de cada natureza no cabecalho da CONSTRAINT no SQL (V010).
 * OUTRO e escape hatch consciente; aparecer em producao e sinal de
 * natureza faltando, nao lugar para morar para sempre.
 */
public enum NaturezaCusto {
    MERCADORIA,
    EMBALAGEM,
    FRETE,
    FRETE_REVERSO,
    COMISSAO_CANAL,
    TARIFA_FIXA_CANAL,
    TAXA_PAGAMENTO,
    TAXA_PARCELAMENTO,
    TAXA_ANTECIPACAO,
    IMPOSTO,
    ADS,
    DESCONTO_CONCEDIDO,
    REEMBOLSO,
    ARMAZENAGEM,
    TARIFA_ADMINISTRATIVA,
    OUTRO
}
