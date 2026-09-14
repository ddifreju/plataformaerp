package com.plataforma.canal;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Corpo JSON aceito por {@code POST /api/canais/{id}/escopo} (tarefa 31,
 * decisao 0033): {@code {"escopo": "FONTE_PRIMARIA"}} ou
 * {@code {"escopo": "ESPELHO", "espelhaCanalId": "..."}}.
 *
 * {@code espelhaCanalId} so e obrigatorio quando {@code escopo} e
 * {@link EscopoCanal#ESPELHO} - validacao CRUZADA entre campos, feita em
 * {@link ServicoEscopoDeCanal} (nao ha como expressar "obrigatorio SE"
 * com as anotacoes de Bean Validation ja usadas neste projeto sem
 * inventar uma anotacao customizada para um unico caso de uso - CLAUDE.md,
 * "nao crie abstracao para um caso so").
 */
public record RequisicaoEscopoCanal(

        @NotNull(message = "O escopo e obrigatorio.")
        EscopoCanal escopo,

        UUID espelhaCanalId) {
}
