package com.plataforma.margem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A saida de {@link MotorMargemPedido#calcular}: os quatro numeros da
 * secao 1 do documento fiscal, cada um com nome proprio, mais a memoria
 * de calculo completa (secao 7) e o rotulo obrigatorio da regra do teto
 * (secao 9.2).
 *
 * Os quatro valores absolutos (N0-N3) estao SEMPRE preenchidos, em escala
 * de armazenamento (4 casas). Os DOIS PERCENTUAIS sao {@link Optional}: a
 * secao 6.5 exige que "faturamento zero" vire ausencia explicita, nunca
 * 0%, "-" ou infinito (ver o Javadoc de {@link FaturamentoZeroException}
 * para a decisao de nao lancar excecao aqui).
 *
 * {@code idsCustoUsados} e o que sustenta a regra 3 do CLAUDE.md ("se o
 * cliente contestar um numero, provamos ou corrigimos em minutos") -
 * junto com {@code lacunas}, e o que a chamada grava em
 * {@code consulta_auditada}.
 */
public record ResultadoMargemPedido(
        UUID pedidoId,
        BigDecimal faturamentoBrutoN0,
        BigDecimal receitaLiquidaN1,
        BigDecimal margemContribuicaoN2,
        BigDecimal resultadoPedidoN3,
        Optional<BigDecimal> margemContribuicaoPercentual,
        Optional<BigDecimal> margemLiquidaPercentual,
        List<MemoriaCalculoBloco> decomposicao,
        List<Lacuna> lacunas,
        RotuloTeto rotulo,
        Set<UUID> idsCustoUsados) {
}
