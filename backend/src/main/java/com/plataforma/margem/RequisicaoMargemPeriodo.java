package com.plataforma.margem;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Corpo JSON aceito por {@code POST /api/margem/periodo}:
 * {@code {"inicio": "...", "fim": "...", "canalId": "..."}}.
 *
 * {@code canalId} continua OBRIGATORIO (decisao 0021) - a validacao
 * {@code @NotNull} aqui devolve {@code 400} ANTES de o pedido chegar em
 * {@link ServicoMargemPeriodo#calcular}, no mesmo espirito de
 * {@link com.plataforma.pergunta.RequisicaoPergunta}.
 */
public record RequisicaoMargemPeriodo(

        @NotNull(message = "O inicio do periodo e obrigatorio.")
        OffsetDateTime inicio,

        @NotNull(message = "O fim do periodo e obrigatorio.")
        OffsetDateTime fim,

        @NotNull(message = "O canalId e obrigatorio (decisao 0021).")
        UUID canalId) {
}
