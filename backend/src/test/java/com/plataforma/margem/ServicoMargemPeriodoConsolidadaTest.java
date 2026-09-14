package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste UNITARIO PURO (sem Spring, sem banco) de
 * {@link ServicoMargemPeriodoConsolidada#agregar} - tarefa 32. Constroi
 * {@link ResultadoMargemPeriodo} a mao (o resultado que
 * {@link ServicoMargemPeriodo#calcular} JA produz, testado em separado) e
 * verifica so a AGREGACAO entre canais: soma, percentual recalculado,
 * pior rotulo, uniao de lacunas.
 */
class ServicoMargemPeriodoConsolidadaTest {

    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-01-01T00:00:00-03:00");
    private static final OffsetDateTime FIM = OffsetDateTime.parse("2026-02-01T00:00:00-03:00");

    @Test
    void somaOsQuatroNiveisDosCanaisEnvolvidos() {
        UUID canalA = UUID.randomUUID();
        UUID canalB = UUID.randomUUID();
        ResultadoMargemPeriodo resultadoA = resultado(canalA, "100.0000", "90.0000", "50.0000", "40.0000", "40.0000",
                RotuloTeto.CALCULADA, List.of(), 1);
        ResultadoMargemPeriodo resultadoB = resultado(canalB, "50.0000", "45.0000", "20.0000", "10.0000", "10.0000",
                RotuloTeto.CALCULADA, List.of(), 2);

        ResultadoMargemConsolidada consolidado = ServicoMargemPeriodoConsolidada.agregar(
                List.of(canalA, canalB), INICIO, FIM, List.of(resultadoA, resultadoB));

        assertBigDecimalIgual("150.0000", consolidado.faturamentoBrutoN0());
        assertBigDecimalIgual("135.0000", consolidado.receitaLiquidaN1());
        assertBigDecimalIgual("70.0000", consolidado.margemContribuicaoN2());
        assertBigDecimalIgual("50.0000", consolidado.resultadoPeriodoN3());
        assertBigDecimalIgual("50.0000", consolidado.lucroOperacionalN4());
        assertEquals(3, consolidado.quantidadePedidos());
        assertEquals(List.of(canalA, canalB), consolidado.canaisIncluidos());
    }

    /**
     * O PONTO CENTRAL desta tarefa: percentual RECALCULADO a partir das
     * somas, nunca media dos percentuais individuais - os dois dao
     * numeros DIFERENTES sempre que os canais tem faturamentos diferentes,
     * e este teste escolhe valores onde a diferenca e grande o bastante
     * para nao passar por coincidencia.
     *
     * Canal A: N2/N0 = 90/100 = 90%. Canal B: N2/N0 = 10/1000 = 1%. Media
     * simples dos dois percentuais seria (90+1)/2 = 45,5% - ERRADO. O
     * percentual correto, recalculado sobre a SOMA, e
     * (90+10)/(100+1000) = 100/1100 ~= 9,09%.
     */
    @Test
    void percentualERecalculadoDasSomasNuncaMediaDosPercentuaisPorCanal() {
        UUID canalA = UUID.randomUUID();
        UUID canalB = UUID.randomUUID();
        ResultadoMargemPeriodo resultadoA = resultado(canalA, "100.0000", "100.0000", "90.0000", "90.0000",
                "90.0000", RotuloTeto.CALCULADA, List.of(), 1);
        ResultadoMargemPeriodo resultadoB = resultado(canalB, "1000.0000", "1000.0000", "10.0000", "10.0000",
                "10.0000", RotuloTeto.CALCULADA, List.of(), 1);

        ResultadoMargemConsolidada consolidado = ServicoMargemPeriodoConsolidada.agregar(
                List.of(canalA, canalB), INICIO, FIM, List.of(resultadoA, resultadoB));

        assertTrue(consolidado.margemContribuicaoPercentual().isPresent());
        BigDecimal percentualRecalculado = consolidado.margemContribuicaoPercentual().get();
        BigDecimal percentualEsperado = new BigDecimal("100.0000").divide(new BigDecimal("1100.0000"), 6,
                java.math.RoundingMode.HALF_UP);
        BigDecimal mediaSimplesErrada = new BigDecimal("0.455000");

        assertEquals(0, percentualEsperado.compareTo(percentualRecalculado.setScale(6, java.math.RoundingMode.HALF_UP)),
                "o percentual precisa ser N2total/N0total, nao a media dos percentuais por canal");
        assertTrue(mediaSimplesErrada.subtract(percentualRecalculado).abs().compareTo(new BigDecimal("0.3")) > 0,
                "o valor errado (media simples) e bem diferente do correto - a asserção acima e que prova o certo, "
                        + "esta so documenta o tamanho do erro que seria cometido");
    }

    @Test
    void percentualVazioQuandoFaturamentoTotalEZero() {
        UUID canalA = UUID.randomUUID();
        ResultadoMargemPeriodo resultadoA = resultado(canalA, "0.0000", "0.0000", "0.0000", "0.0000", "0.0000",
                RotuloTeto.CALCULADA, List.of(), 0);

        ResultadoMargemConsolidada consolidado = ServicoMargemPeriodoConsolidada.agregar(
                List.of(canalA), INICIO, FIM, List.of(resultadoA));

        assertTrue(consolidado.margemContribuicaoPercentual().isEmpty());
        assertTrue(consolidado.margemLiquidaPercentual().isEmpty());
    }

    @Test
    void rotuloDoConjuntoEOPiorEntreOsCanais() {
        UUID canalCalculada = UUID.randomUUID();
        UUID canalComTeto = UUID.randomUUID();
        UUID canalIndeterminada = UUID.randomUUID();
        ResultadoMargemPeriodo resultadoCalculada = resultado(canalCalculada, "10.0000", "10.0000", "10.0000",
                "10.0000", "10.0000", RotuloTeto.CALCULADA, List.of(), 1);
        ResultadoMargemPeriodo resultadoComTeto = resultado(canalComTeto, "10.0000", "10.0000", "10.0000",
                "10.0000", "10.0000", RotuloTeto.COM_TETO, List.of(), 1);
        ResultadoMargemPeriodo resultadoIndeterminada = resultado(canalIndeterminada, "10.0000", "10.0000",
                "10.0000", "10.0000", "10.0000", RotuloTeto.INDETERMINADA, List.of(), 1);

        RotuloTeto rotuloDoisPiores = ServicoMargemPeriodoConsolidada.agregar(
                List.of(canalCalculada, canalComTeto), INICIO, FIM,
                List.of(resultadoCalculada, resultadoComTeto)).rotulo();
        assertEquals(RotuloTeto.COM_TETO, rotuloDoisPiores,
                "CALCULADA + COM_TETO precisa fechar em COM_TETO (o pior dos dois)");

        RotuloTeto rotuloTresPiores = ServicoMargemPeriodoConsolidada.agregar(
                List.of(canalCalculada, canalComTeto, canalIndeterminada), INICIO, FIM,
                List.of(resultadoCalculada, resultadoComTeto, resultadoIndeterminada)).rotulo();
        assertEquals(RotuloTeto.INDETERMINADA, rotuloTresPiores,
                "qualquer INDETERMINADA no conjunto contamina o rotulo do conjunto inteiro");

        RotuloTeto rotuloTodosCalculada = ServicoMargemPeriodoConsolidada.agregar(
                List.of(canalCalculada), INICIO, FIM, List.of(resultadoCalculada)).rotulo();
        assertEquals(RotuloTeto.CALCULADA, rotuloTodosCalculada);
    }

    @Test
    void lacunasSaoUniaoDeduplicadaPorCodigoSemDuplicar() {
        UUID canalA = UUID.randomUUID();
        UUID canalB = UUID.randomUUID();
        Lacuna lacunaMercadoria = new Lacuna("custo_mercadoria_nao_cadastrado", "faltou mercadoria",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
        Lacuna lacunaMercadoriaRepetida = new Lacuna("custo_mercadoria_nao_cadastrado", "faltou mercadoria de novo",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
        Lacuna lacunaImposto = new Lacuna("regime_tributario_nao_configurado", "faltou imposto",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);

        ResultadoMargemPeriodo resultadoA = resultado(canalA, "10.0000", "10.0000", "10.0000", "10.0000", "10.0000",
                RotuloTeto.COM_TETO, List.of(lacunaMercadoria), 1);
        ResultadoMargemPeriodo resultadoB = resultado(canalB, "10.0000", "10.0000", "10.0000", "10.0000", "10.0000",
                RotuloTeto.COM_TETO, List.of(lacunaMercadoriaRepetida, lacunaImposto), 1);

        ResultadoMargemConsolidada consolidado = ServicoMargemPeriodoConsolidada.agregar(
                List.of(canalA, canalB), INICIO, FIM, List.of(resultadoA, resultadoB));

        assertEquals(2, consolidado.lacunas().size(),
                "duas lacunas com o MESMO codigo em canais diferentes contam como UMA so na uniao");
        assertEquals("custo_mercadoria_nao_cadastrado", consolidado.lacunas().get(0).codigo());
        assertEquals("faltou mercadoria", consolidado.lacunas().get(0).descricao(),
                "a PRIMEIRA ocorrencia da lacuna e a que fica (putIfAbsent) - mesmo criterio de ServicoMargemPeriodo");
        assertEquals("regime_tributario_nao_configurado", consolidado.lacunas().get(1).codigo());
    }

    @Test
    void idsPedidoEIdsCustoSaoUniaoSemDuplicar() {
        UUID canalA = UUID.randomUUID();
        UUID canalB = UUID.randomUUID();
        UUID pedidoCompartilhadoPorEngano = UUID.randomUUID(); // nunca deveria acontecer de verdade, mas Set garante
        UUID pedidoA = UUID.randomUUID();
        UUID pedidoB = UUID.randomUUID();
        UUID custoA = UUID.randomUUID();
        UUID custoB = UUID.randomUUID();

        ResultadoMargemPeriodo resultadoA = resultadoComIds(canalA, Set.of(pedidoA, pedidoCompartilhadoPorEngano),
                Set.of(custoA));
        ResultadoMargemPeriodo resultadoB = resultadoComIds(canalB, Set.of(pedidoB, pedidoCompartilhadoPorEngano),
                Set.of(custoB));

        ResultadoMargemConsolidada consolidado = ServicoMargemPeriodoConsolidada.agregar(
                List.of(canalA, canalB), INICIO, FIM, List.of(resultadoA, resultadoB));

        assertEquals(Set.of(pedidoA, pedidoB, pedidoCompartilhadoPorEngano), consolidado.idsPedidoUsados());
        assertEquals(Set.of(custoA, custoB), consolidado.idsCustoUsados());
    }

    @Test
    void conjuntoVazioDevolveZeroSemLancarNada() {
        ResultadoMargemConsolidada consolidado = ServicoMargemPeriodoConsolidada.agregar(
                List.of(), INICIO, FIM, List.of());

        assertBigDecimalIgual("0", consolidado.faturamentoBrutoN0());
        assertEquals(RotuloTeto.CALCULADA, consolidado.rotulo());
        assertEquals(0, consolidado.quantidadePedidos());
        assertTrue(consolidado.lacunas().isEmpty());
        assertTrue(consolidado.idsPedidoUsados().isEmpty());
        assertTrue(consolidado.canaisIncluidos().isEmpty());
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static void assertBigDecimalIgual(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor),
                "esperado " + esperado + " mas foi " + valor + " (BigDecimal.compareTo, nunca equals: escalas "
                        + "diferentes nao devem fazer a asserção falhar por acidente)");
    }

    private static ResultadoMargemPeriodo resultado(UUID canalId, String n0, String n1, String n2, String n3,
            String n4, RotuloTeto rotulo, List<Lacuna> lacunas, int quantidadePedidos) {
        return new ResultadoMargemPeriodo(canalId, INICIO, FIM, "escopo de teste",
                new BigDecimal(n0), new BigDecimal(n1), new BigDecimal(n2), new BigDecimal(n3), new BigDecimal(n4),
                MotorMargemPedido.percentual(new BigDecimal(n2), new BigDecimal(n0)),
                MotorMargemPedido.percentual(new BigDecimal(n3), new BigDecimal(n0)),
                decomposicaoZerada(), lacunas, rotulo, quantidadePedidos, Set.of(), Set.of());
    }

    private static ResultadoMargemPeriodo resultadoComIds(UUID canalId, Set<UUID> idsPedido, Set<UUID> idsCusto) {
        return new ResultadoMargemPeriodo(canalId, INICIO, FIM, "escopo de teste",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                java.util.Optional.empty(), java.util.Optional.empty(),
                decomposicaoZerada(), List.of(), RotuloTeto.CALCULADA, idsPedido.size(), idsPedido, idsCusto);
    }

    private static List<MemoriaCalculoBlocoPeriodo> decomposicaoZerada() {
        return List.of(new MemoriaCalculoBlocoPeriodo(BlocoMargem.B1_DEDUCOES_RECEITA, BigDecimal.ZERO, false));
    }
}
