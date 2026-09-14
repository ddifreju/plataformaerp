package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resultado de {@link ServicoMargemPeriodoConsolidada#calcular} - MESMA
 * escala de armazenamento (4 casas) de {@link ResultadoMargemPeriodo},
 * pelo mesmo motivo: e daqui que uma proxima soma/consulta partiria.
 * {@link RespostaMargemConsolidada} formata para 2 casas na borda de
 * saida.
 *
 * {@code canaisIncluidos} substitui o {@code canalId} unico de
 * {@link ResultadoMargemPeriodo} - a resposta declara EXPLICITAMENTE
 * quais canais entraram na soma (tarefa 32), na mesma ordem em que foram
 * somados.
 */
public record ResultadoMargemConsolidada(
        List<UUID> canaisIncluidos,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String escopoConsolidado,
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
