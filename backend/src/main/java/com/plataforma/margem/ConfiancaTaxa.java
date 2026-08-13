package com.plataforma.margem;

/**
 * Dominio de taxa_canal.confianca (migration V013). Qualidade da fonte -
 * o que permite a interface dizer "comissao 13% - informada por voce em
 * 08/01/2026" em vez de apresentar palpite como fato (regra 5 do
 * CLAUDE.md).
 */
public enum ConfiancaTaxa {
    CONFIRMADO_FONTE_OFICIAL,
    INFORMADO_PELO_LOJISTA,
    ESTIMADO
}
