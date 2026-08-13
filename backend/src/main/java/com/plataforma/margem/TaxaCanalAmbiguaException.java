package com.plataforma.margem;

import java.util.UUID;

/**
 * Lancada quando a selecao de taxa (secao 8.2, cabecalho da V013) encontra
 * duas linhas de {@code taxa_canal} com a MESMA especificidade para o
 * mesmo contexto - erro de CADASTRO, nunca resolvido silenciosamente. "Um
 * numero silenciosamente errado e pior que um erro visivel" (secao 8.2).
 *
 * Quem captura isto deve mostrar as duas linhas ({@code idTaxaA},
 * {@code idTaxaB}) para quem cadastra corrigir - nunca escolher uma
 * sozinho.
 */
public class TaxaCanalAmbiguaException extends RuntimeException {

    private final UUID idTaxaA;
    private final UUID idTaxaB;

    public TaxaCanalAmbiguaException(UUID idTaxaA, UUID idTaxaB, int especificidade) {
        super("Duas linhas de taxa_canal empatam em especificidade (" + especificidade + ") para o mesmo contexto: "
                + idTaxaA + " e " + idTaxaB + ". Isto e erro de cadastro - corrija encerrando uma das duas "
                + "(vigencia_fim) antes de recalcular.");
        this.idTaxaA = idTaxaA;
        this.idTaxaB = idTaxaB;
    }

    public UUID getIdTaxaA() {
        return idTaxaA;
    }

    public UUID getIdTaxaB() {
        return idTaxaB;
    }
}
