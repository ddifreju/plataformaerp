package com.plataforma.painel;

import java.util.List;

import com.plataforma.devolucao.ContagemPorStatusDevolucao;
import com.plataforma.pedido.ContagemPorStatusPedido;

/**
 * Resposta de {@code GET /api/painel/gestor} (tarefa 19 - "gargalos do
 * processo"). Quatro fontes REAIS, nenhuma metrica inventada:
 *
 * <ul>
 *   <li>{@code pedidosPorStatus} - onde os pedidos estao parados no funil;</li>
 *   <li>{@code devolucoesPorStatus} - o mesmo para devolucao;</li>
 *   <li>{@code eventosIngestaoComErro} - quanto da ingestao esta falhando;</li>
 *   <li>{@code pedidosSemCustoMercadoria} - quantos pedidos nao tem como
 *       calcular margem confiavel hoje (lacuna #1 do catalogo fiscal).</li>
 * </ul>
 *
 * DE PROPOSITO NAO EXISTE aqui nenhum campo por PESSOA (quem processou,
 * quem respondeu) - decisao 0003 ("metrica de processo, nunca de
 * pessoa") e decisao 0012 (proibe explicitamente agregacao por
 * {@code consulta_auditada.executado_por}, citando esta tarefa por nome).
 * Gargalo e sempre do PROCESSO.
 *
 * SEM {@code consulta_auditada} aqui (ao contrario de
 * {@code ServicoMargemPeriodo}, regra 3 do CLAUDE.md): estes quatro
 * numeros sao leituras diretas (COUNT/GROUP BY), sem logica de negocio
 * combinando varias fontes em Java - a propria query, reexecutada, JA E a
 * prova. A rastreabilidade da regra 3 existe para numeros que dependem de
 * como o codigo combinou os dados (o caso de margem); aqui o "como" e so
 * SQL, visivel e estavel.
 */
public record RespostaGargalosProcesso(
        List<ContagemPorStatusPedido> pedidosPorStatus,
        List<ContagemPorStatusDevolucao> devolucoesPorStatus,
        long eventosIngestaoComErro,
        long pedidosSemCustoMercadoria) {
}
