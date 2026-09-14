package com.plataforma.margem;

/**
 * Os quatro motivos pelos quais um canal bloqueia a soma consolidada
 * (tarefa 32, decisao 0033) - espelham EXATAMENTE os quatro valores que a
 * consulta nativa de {@link RepositorioDisjuncaoDeCanais} pode devolver em
 * {@code motivo_do_bloqueio}. Modelado como enum (nunca String solta) para
 * que o compilador garanta que {@code ServicoMargemPeriodoConsolidada}
 * trata todos os casos - mesmo racional de {@link RotuloTeto}.
 */
public enum MotivoBloqueioDeSoma {

    /** {@code canalId} nao existe, ou nao pertence a este tenant (LEFT JOIN sem correspondencia). */
    CANAL_INEXISTENTE,

    /** Canal existe, mas a lojista ainda nao declarou o escopo dele (decisao 0033: fail-closed por padrao). */
    NAO_DECLARADO,

    /**
     * Canal e declarado {@code ESPELHO} (ver {@code com.plataforma.canal.EscopoCanal}) -
     * nunca entra em soma, seja qual for o conjunto.
     */
    E_ESPELHO,

    /**
     * Canal e FONTE_PRIMARIA, mas outro canal DO MESMO CONJUNTO pedido o
     * declara como {@code espelha_canal_id} - somar os dois contaria a
     * mesma venda duas vezes. Redundante com {@link #E_ESPELHO} do ponto
     * de vista logico (o outro lado do mesmo par ja aparece bloqueado por
     * {@code E_ESPELHO}), e NAO e codigo morto: e o que permite a recusa
     * nomear os DOIS lados do par, em vez de so um.
     */
    ESPELHADO_POR_OUTRO_DO_CONJUNTO
}
