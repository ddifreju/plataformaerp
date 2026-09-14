package com.plataforma.margem;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Corpo JSON aceito por {@code POST /api/margem/periodo/consolidado}
 * (tarefa 32, decisao 0033):
 * {@code {"inicio": "...", "fim": "...", "canais": ["...", "..."]}}.
 *
 * {@code canais} e OPCIONAL - {@code null} ou lista vazia significa
 * "todos os canais ATIVOS do tenant" (resolvido em
 * {@link ServicoMargemPeriodoConsolidada}, nunca aqui: escolher o
 * conjunto default e regra de negocio, nao validacao de forma do corpo).
 * Diferente de {@link RequisicaoMargemPeriodo#canalId()} (decisao 0021,
 * que continua obrigatorio e unico naquele endpoint) - este endpoint E o
 * modo agregado que a decisao 0021 previu como evolucao aditiva, sem
 * mudar o comportamento do endpoint antigo.
 */
public record RequisicaoMargemConsolidada(

        @NotNull(message = "O inicio do periodo e obrigatorio.")
        OffsetDateTime inicio,

        @NotNull(message = "O fim do periodo e obrigatorio.")
        OffsetDateTime fim,

        List<UUID> canais) {
}
