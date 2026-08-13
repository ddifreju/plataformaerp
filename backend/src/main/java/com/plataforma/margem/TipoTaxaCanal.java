package com.plataforma.margem;

/**
 * Dominio de taxa_canal.tipo_taxa (migration V013, CONSTRAINT
 * ck_taxa_canal_tipo_taxa). O que a regra precifica - discriminador de
 * SELECAO (secao 8.2 do documento fiscal). Imposto NAO esta aqui de
 * proposito (decisao 0020: tabela propria).
 */
public enum TipoTaxaCanal {
    COMISSAO,
    TARIFA_FIXA,
    PARCELAMENTO,
    ANTECIPACAO,
    PAGAMENTO,
    FRETE,
    FRETE_SUBSIDIO,
    FRETE_REVERSO,
    ARMAZENAGEM,
    TARIFA_ADMINISTRATIVA,
    OUTRO
}
