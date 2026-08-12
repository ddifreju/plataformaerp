package com.plataforma.canal;

/**
 * Dominio de canal.categoria (migration V005, CONSTRAINT ck_canal_categoria).
 * QUE PAPEL o canal cumpre - a regra de negocio pergunta pelo papel, nao
 * pelo tipo de sistema (ver comentario da coluna no SQL).
 */
public enum CategoriaCanal {
    MARKETPLACE,
    LOJA_PROPRIA,
    ERP,
    COMUNICACAO
}
