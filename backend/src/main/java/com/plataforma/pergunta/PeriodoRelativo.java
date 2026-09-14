package com.plataforma.pergunta;

/**
 * Conjunto FECHADO de períodos relativos que {@link ValidadorDeParametros}
 * aceita como alternativa a informar {@code inicio}/{@code fim} explícitos.
 * Resolvido contra um {@link java.time.Clock} injetado (nunca
 * {@code OffsetDateTime.now()} direto) para que o cálculo seja determinístico
 * em teste - ver {@code ConfiguracaoRelogio}.
 */
public enum PeriodoRelativo {
    MES_ATUAL,
    MES_PASSADO,
    ULTIMOS_7_DIAS,
    ULTIMOS_30_DIAS,
    ULTIMOS_90_DIAS,
    ANO_ATUAL
}
