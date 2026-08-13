package com.plataforma.margem;

/**
 * Para que lado uma {@link Lacuna} empurra o numero de margem, quando ela
 * poderia ser preenchida (catalogo, secao 9.1 do documento fiscal). E o
 * que permite a "regra do teto" (secao 9.2) decidir mecanicamente entre
 * {@link RotuloTeto#COM_TETO} (todo mundo empurra para cima, o numero
 * calculado e um limite superior honesto) e
 * {@link RotuloTeto#INDETERMINADA} (vieses opostos ou desconhecidos
 * misturados - nao existe teto honesto).
 */
public enum DirecaoViesLacuna {

    /**
     * Faltar este dado faz a margem CALCULADA parecer MAIOR do que a
     * real (falta custo/deducao). Ex.: custo do produto nao cadastrado,
     * regime tributario nao configurado. A maioria das lacunas do
     * catalogo cai aqui.
     */
    SUPERESTIMA_MARGEM,

    /**
     * Faltar este dado faz a margem CALCULADA parecer MENOR do que a
     * real (custo aplicado a mais). Ex.: ICMS-ST nao sinalizado (secao
     * 5.5) - o unico erro da especificacao que faz o lojista parecer
     * pior do que e.
     */
    SUBESTIMA_MARGEM,

    /**
     * Nao sabemos para que lado o numero real fica em relacao ao
     * calculado. Ex.: canais sobrepostos (decisao 0017), repasse previsto
     * ausente (sem conferencia externa nenhuma).
     */
    INDETERMINADA
}
