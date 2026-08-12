package com.plataforma.canal;

/**
 * Dominio de canal.tipo (migration V005, CONSTRAINT ck_canal_tipo).
 * Mapeado com @Enumerated(EnumType.STRING) - decisao de schema (convencao
 * 5 da V005): CHECK em vez de ENUM nativo do Postgres. Os nomes abaixo
 * tem que ser EXATAMENTE iguais aos valores do CHECK; o ddl-auto: validate
 * nao confere isso, so o INSERT em runtime.
 *
 * OUTRO e escape hatch consciente (ver comentario no SQL): melhor ingerir
 * com OUTRO do que perder o dado.
 */
public enum TipoCanal {
    MERCADO_LIVRE,
    SHOPEE,
    AMAZON,
    MAGALU,
    AMERICANAS,
    SHOPIFY,
    NUVEMSHOP,
    WOOCOMMERCE,
    LOJA_PROPRIA,
    ERP_BLING,
    ERP_TINY,
    WHATSAPP,
    EMAIL,
    INSTAGRAM,
    OUTRO
}
