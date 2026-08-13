package com.plataforma.margem;

/**
 * Dominio de taxa_canal.origem (migration V013). Por onde a linha entrou
 * - diferente de {@link ConfiancaTaxa}: uma planilha importada pode trazer
 * dado oficial, e uma digitacao manual pode ser palpite.
 */
public enum OrigemTaxa {
    CADASTRO_MANUAL,
    API_CANAL,
    IMPORTACAO_ARQUIVO
}
