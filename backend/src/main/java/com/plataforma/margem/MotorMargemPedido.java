package com.plataforma.margem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.plataforma.custo.Custo;

/**
 * A formula N0-&gt;N4 do documento fiscal (docs/fiscal/regras-de-margem.md,
 * secoes 1 e 2), decomposta e auditavel. Classe pura: recebe o total do
 * pedido e as linhas de {@code custo} JA RESOLVIDAS (nivel 1 e nivel 2 da
 * hierarquia - ver {@link com.plataforma.margem} / tarefa 14) mais a lista
 * de lacunas conhecidas, e devolve os quatro numeros com a memoria de
 * calculo completa. NAO consulta banco, NAO resolve hierarquia de taxa -
 * isso e trabalho de quem monta a lista de {@code custos} antes de chamar
 * este metodo.
 *
 * <h2>De onde vem cada numero</h2>
 * <pre>
 * N0  Faturamento bruto      = pedido.valor_total_pedido
 * N1  Receita liquida        = N0 - C(B1)
 * N2  Margem de contribuicao = N1 - C(B2) - C(B3) - C(B4) - C(B5) - C(B6)
 * N3  Resultado do pedido    = N2 - C(B7) - C(B8)
 *
 * Margem de contribuicao %   = N2 / N0   (denominador SEMPRE N0, nunca N1 - secao 2.3)
 * Margem liquida %           = N3 / N0
 * </pre>
 * N4 (lucro operacional do periodo) NAO e calculado aqui - e agregacao de
 * varios N3 menos custo de periodo nao rateado, trabalho da tarefa 16
 * ({@code ServicoMargemPeriodo}).
 *
 * <h2>Escalas (secao 6.1)</h2>
 * Os quatro valores absolutos ficam em escala de ARMAZENAMENTO (4 casas,
 * HALF_UP). O denominador da divisao de percentual usa escala
 * INTERMEDIARIA (8 casas) - nunca arredonde a aliquota/percentual antes
 * do fim da cadeia (secao 6.2).
 */
public final class MotorMargemPedido {

    private static final int ESCALA_ARMAZENAMENTO = 4;
    private static final int ESCALA_INTERMEDIARIA = 8;

    private MotorMargemPedido() {
        // classe utilitaria: sem instancia
    }

    /**
     * @param pedidoId          identidade do pedido, so para carimbar o resultado
     * @param n0FaturamentoBruto {@code pedido.valor_total_pedido} (R1 da V008) - NUNCA recalculado a partir dos itens
     * @param custosResolvidos  linhas de {@code custo} JA resolvidas (nivel 1 "fato" + nivel 2 "regra"
     *                          calculado); nunca inclua uma linha para uma lacuna - lacuna nao vira `custo`
     *                          (decisao 0019, nivel 3)
     * @param lacunas           as lacunas conhecidas para este pedido (catalogo da secao 9.1) - determina o
     *                          {@link RotuloTeto} do resultado
     */
    public static ResultadoMargemPedido calcular(UUID pedidoId, BigDecimal n0FaturamentoBruto,
            List<Custo> custosResolvidos, List<Lacuna> lacunas) {
        Objects.requireNonNull(pedidoId, "pedidoId nao pode ser nulo");
        Objects.requireNonNull(n0FaturamentoBruto, "n0FaturamentoBruto nao pode ser nulo");

        List<Custo> custos = (custosResolvidos != null) ? custosResolvidos : List.of();
        List<Lacuna> todasLacunas = (lacunas != null) ? List.copyOf(lacunas) : List.of();

        Map<BlocoMargem, List<Custo>> custosPorBloco = custos.stream()
                .collect(Collectors.groupingBy(custo -> BlocoMargem.deNatureza(custo.getNatureza()),
                        () -> new EnumMap<>(BlocoMargem.class), Collectors.toList()));

        Map<BlocoMargem, BigDecimal> somaPorBloco = new EnumMap<>(BlocoMargem.class);
        List<MemoriaCalculoBloco> decomposicao = new ArrayList<>(BlocoMargem.values().length);
        for (BlocoMargem bloco : BlocoMargem.values()) {
            List<Custo> linhasDoBloco = custosPorBloco.getOrDefault(bloco, List.of());
            BigDecimal somaBloco = linhasDoBloco.stream()
                    .map(Custo::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(ESCALA_ARMAZENAMENTO, RoundingMode.HALF_UP);
            somaPorBloco.put(bloco, somaBloco);
            decomposicao.add(new MemoriaCalculoBloco(
                    bloco,
                    somaBloco,
                    linhasDoBloco.stream().map(Custo::getId).toList(),
                    linhasDoBloco.stream().anyMatch(Custo::isEhEstimativa)));
        }

        BigDecimal n0 = n0FaturamentoBruto.setScale(ESCALA_ARMAZENAMENTO, RoundingMode.HALF_UP);
        BigDecimal n1 = n0.subtract(somaPorBloco.get(BlocoMargem.B1_DEDUCOES_RECEITA));
        BigDecimal n2 = n1
                .subtract(somaPorBloco.get(BlocoMargem.B2_CUSTO_MERCADORIA))
                .subtract(somaPorBloco.get(BlocoMargem.B3_CUSTOS_CANAL))
                .subtract(somaPorBloco.get(BlocoMargem.B4_CUSTOS_LOGISTICOS))
                .subtract(somaPorBloco.get(BlocoMargem.B5_CUSTOS_FINANCEIROS))
                .subtract(somaPorBloco.get(BlocoMargem.B6_IMPOSTO));
        BigDecimal n3 = n2
                .subtract(somaPorBloco.get(BlocoMargem.B7_MARKETING_ATRIBUIDO))
                .subtract(somaPorBloco.get(BlocoMargem.B8_OVERHEAD_ATRIBUIDO));

        Optional<BigDecimal> margemContribuicaoPercentual = percentual(n2, n0);
        Optional<BigDecimal> margemLiquidaPercentual = percentual(n3, n0);

        Set<UUID> idsCustoUsados = custos.stream()
                .map(Custo::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        RotuloTeto rotulo = RotuloTeto.calcular(todasLacunas);

        return new ResultadoMargemPedido(pedidoId, n0, n1, n2, n3,
                margemContribuicaoPercentual, margemLiquidaPercentual,
                List.copyOf(decomposicao), todasLacunas, rotulo, idsCustoUsados);
    }

    /**
     * {@code numerador / n0}, em escala intermediaria (secao 6.5).
     * {@code n0 == 0} devolve {@link Optional#empty()} em vez de 0%, "-"
     * ou infinito - todos mentira segundo a especificacao. Quem precisa de
     * um percentual OBRIGATORIO para um pedido especifico (nao para uma
     * agregacao de periodo) deve tratar o {@code empty()} lancando
     * {@link FaturamentoZeroException} no ponto de apresentacao.
     */
    public static Optional<BigDecimal> percentual(BigDecimal numerador, BigDecimal n0) {
        if (n0.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(numerador.divide(n0, ESCALA_INTERMEDIARIA, RoundingMode.HALF_UP));
    }
}
