package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resposta do endpoint "quanto sobrou no periodo X" (tarefa 16,
 * {@code GET /api/margem/periodo}).
 *
 * {@code escopoCanal} declara em TEXTO a decisao 0017 tomada por
 * {@code ServicoMargemPeriodo} (canal unico, obrigatorio - nunca soma de
 * canais potencialmente sobrepostos) - a resposta se explica sozinha, sem
 * exigir que quem le conheca a decisao de arquitetura por fora.
 *
 * {@code lucroOperacionalN4} e o unico dos quatro numeros que so existe
 * neste endpoint (agregacao de periodo) - N0 a N3 tambem existem por
 * pedido (ver {@link ResultadoMargemPedido}), aqui sao a SOMA dos pedidos
 * do periodo dentro do canal.
 */
public record ResultadoMargemPeriodo(
        UUID canalId,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String escopoCanal,
        BigDecimal faturamentoBrutoN0,
        BigDecimal receitaLiquidaN1,
        BigDecimal margemContribuicaoN2,
        BigDecimal resultadoPeriodoN3,
        BigDecimal lucroOperacionalN4,
        Optional<BigDecimal> margemContribuicaoPercentual,
        Optional<BigDecimal> margemLiquidaPercentual,
        List<MemoriaCalculoBlocoPeriodo> decomposicao,
        List<Lacuna> lacunas,
        RotuloTeto rotulo,
        int quantidadePedidos,
        Set<UUID> idsPedidoUsados,
        Set<UUID> idsCustoUsados) {
}
