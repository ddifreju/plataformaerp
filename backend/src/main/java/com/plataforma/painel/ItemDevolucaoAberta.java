package com.plataforma.painel;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.plataforma.devolucao.Devolucao;

/**
 * Uma devolucao ainda nao finalizada (tarefa 20). "Aberta" aqui e
 * {@code finalizadaEm IS NULL} - ver o comentario de
 * {@code RepositorioDevolucao.findTop100ByFinalizadaEmIsNullOrderByAbertaEmDesc}
 * sobre por que essa e a definicao usada, em vez de enumerar status
 * intermediarios.
 */
public record ItemDevolucaoAberta(
        UUID id, UUID pedidoId, String status, String motivo, OffsetDateTime abertaEm, String acao) {

    private static final String ACAO = "Dar andamento a esta devolucao (analisar, aprovar/recusar ou receber o produto).";

    public static ItemDevolucaoAberta de(Devolucao devolucao) {
        return new ItemDevolucaoAberta(
                devolucao.getId(),
                devolucao.getPedidoId(),
                devolucao.getStatus().name(),
                devolucao.getMotivo() != null ? devolucao.getMotivo().name() : null,
                devolucao.getAbertaEm(),
                ACAO);
    }
}
