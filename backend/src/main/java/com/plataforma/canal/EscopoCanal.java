package com.plataforma.canal;

/**
 * Dominio de {@code canal.escopo_declarado} (migration V016) - decisao
 * 0033. Declaracao da LOJISTA sobre a origem dos pedidos de um canal,
 * nunca inferida por heuristica (ver o cabecalho da V016, secao 1).
 *
 * <ul>
 *   <li>{@link #NAO_DECLARADO} - o padrao, e o estado de todo canal que
 *       existe hoje. BLOQUEIA a soma entre canais (fail-closed).</li>
 *   <li>{@link #FONTE_PRIMARIA} - os pedidos deste canal nascem aqui.
 *       Unico escopo que participa de uma soma entre canais.</li>
 *   <li>{@link #ESPELHO} - os pedidos deste canal sao copia dos de outro
 *       canal, apontado por {@code Canal.espelhaCanalId}.</li>
 * </ul>
 *
 * Sem 'OUTRO' aqui, ao contrario de {@link TipoCanal}: nao ha fonte
 * externa a preservar - o valor e uma declaracao humana, e um quarto
 * estado seria um escopo que a regra de soma nao sabe tratar (mesmo
 * raciocinio da V016).
 */
public enum EscopoCanal {
    NAO_DECLARADO,
    FONTE_PRIMARIA,
    ESPELHO
}
