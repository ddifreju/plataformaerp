package com.plataforma.pergunta;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Um período [inicio, fim) já resolvido e validado por
 * {@link ValidadorDeParametros#validarPeriodo(java.util.Map)} - pronto
 * para ser passado direto a {@code ServicoMargemPeriodo.calcular}, que
 * usa a mesma semântica de intervalo (fim exclusivo).
 */
public record Periodo(OffsetDateTime inicio, OffsetDateTime fim) {

    public Periodo {
        Objects.requireNonNull(inicio, "inicio é obrigatório");
        Objects.requireNonNull(fim, "fim é obrigatório");
    }
}
