package com.plataforma.margem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes PUROS (sem Spring, sem banco) do algoritmo de maior resto da
 * secao 6.3 do documento fiscal. Cobre a lista de "testes obrigatorios"
 * da propria secao 6.3, mais os dois exemplos numericos trabalhados
 * (A e B) e o exemplo de estorno (C).
 *
 * Asserções monetarias sempre via compareTo (BigDecimal.equals falha por
 * escala diferente mesmo com o mesmo valor - mesma convencao de PedidoTest).
 */
class RateioTest {

    // ------------------------------------------------------------------
    // Exemplo A da secao 6.3 - empate total: 10,00 entre 3 itens iguais
    // ------------------------------------------------------------------

    @Test
    void exemploATresItensIguaisSomaExataEDesempatePorUuid() {
        UUID idA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID idB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        UUID idC = UUID.fromString("00000000-0000-0000-0000-00000000000c");

        List<Rateio.ItemRateio> itens = List.of(
                new Rateio.ItemRateio(idA, new BigDecimal("100.00")),
                new Rateio.ItemRateio(idB, new BigDecimal("100.00")),
                new Rateio.ItemRateio(idC, new BigDecimal("100.00")));

        List<Rateio.QuotaRateio> resultado = Rateio.ratear(new BigDecimal("10.0000"), itens, 4);

        Map<UUID, BigDecimal> porId = paraMapa(resultado);
        // Todos empatam em resto (0,00003333...) e peso (100,00): o
        // desempate final e o UUID em ordem ASC - A e o menor, recebe a
        // unidade extra (secao 6.3, Exemplo A).
        assertEquals(0, porId.get(idA).compareTo(new BigDecimal("3.3334")), "A (menor UUID) absorve o centavo perdido");
        assertEquals(0, porId.get(idB).compareTo(new BigDecimal("3.3333")));
        assertEquals(0, porId.get(idC).compareTo(new BigDecimal("3.3333")));
        assertSomaExata(new BigDecimal("10.0000"), resultado);
    }

    // ------------------------------------------------------------------
    // Exemplo B da secao 6.3 - pesos distintos
    // ------------------------------------------------------------------

    @Test
    void exemploBPesosDistintosSomaExata() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();

        List<Rateio.ItemRateio> itens = List.of(
                new Rateio.ItemRateio(idA, new BigDecimal("199.90")),
                new Rateio.ItemRateio(idB, new BigDecimal("49.90")),
                new Rateio.ItemRateio(idC, new BigDecimal("30.00")));

        List<Rateio.QuotaRateio> resultado = Rateio.ratear(new BigDecimal("10.0000"), itens, 4);

        Map<UUID, BigDecimal> porId = paraMapa(resultado);
        assertEquals(0, porId.get(idA).compareTo(new BigDecimal("7.1444")));
        assertEquals(0, porId.get(idB).compareTo(new BigDecimal("1.7834")));
        assertEquals(0, porId.get(idC).compareTo(new BigDecimal("1.0722")));
        assertSomaExata(new BigDecimal("10.0000"), resultado);
    }

    // ------------------------------------------------------------------
    // Exemplo C - estorno (total negativo), espelha o Exemplo A
    // ------------------------------------------------------------------

    @Test
    void totalNegativoSomaExataESinalCorreto() {
        UUID idA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID idB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        UUID idC = UUID.fromString("00000000-0000-0000-0000-00000000000c");

        List<Rateio.ItemRateio> itens = List.of(
                new Rateio.ItemRateio(idA, new BigDecimal("100.00")),
                new Rateio.ItemRateio(idB, new BigDecimal("100.00")),
                new Rateio.ItemRateio(idC, new BigDecimal("100.00")));

        List<Rateio.QuotaRateio> resultado = Rateio.ratear(new BigDecimal("-10.0000"), itens, 4);

        Map<UUID, BigDecimal> porId = paraMapa(resultado);
        assertEquals(0, porId.get(idA).compareTo(new BigDecimal("-3.3334")), "espelha o Exemplo A com sinal trocado");
        assertEquals(0, porId.get(idB).compareTo(new BigDecimal("-3.3333")));
        assertEquals(0, porId.get(idC).compareTo(new BigDecimal("-3.3333")));
        assertSomaExata(new BigDecimal("-10.0000"), resultado);
    }

    // ------------------------------------------------------------------
    // Lista de testes obrigatorios da secao 6.3
    // ------------------------------------------------------------------

    @Test
    void doisItensUmCentavoUmRecebeOutroNaoSomaExata() {
        UUID idMenor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID idMaior = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<Rateio.ItemRateio> itens = List.of(
                new Rateio.ItemRateio(idMenor, BigDecimal.ONE),
                new Rateio.ItemRateio(idMaior, BigDecimal.ONE));

        List<Rateio.QuotaRateio> resultado = Rateio.ratear(new BigDecimal("0.01"), itens, 2);

        Map<UUID, BigDecimal> porId = paraMapa(resultado);
        long quantosRecebemAlgo = porId.values().stream().filter(v -> v.signum() > 0).count();
        assertEquals(1, quantosRecebemAlgo, "so um dos dois itens deveria receber o centavo unico");
        assertSomaExata(new BigDecimal("0.01"), resultado);
    }

    @Test
    void umItemRecebeOTotalInteiro() {
        UUID id = UUID.randomUUID();
        List<Rateio.ItemRateio> itens = List.of(new Rateio.ItemRateio(id, new BigDecimal("37.00")));

        List<Rateio.QuotaRateio> resultado = Rateio.ratear(new BigDecimal("123.4567"), itens, 4);

        assertEquals(1, resultado.size());
        assertEquals(0, resultado.get(0).quota().compareTo(new BigDecimal("123.4567")));
    }

    @Test
    void seteItensComPesosPrimosEntreSiSomaExata() {
        // pesos primos entre si (numeros primos distintos), total com
        // muitas casas decimais para forcar resto em quase todo item.
        int[] pesos = {2, 3, 5, 7, 11, 13, 17};
        List<Rateio.ItemRateio> itens = new ArrayList<>();
        for (int peso : pesos) {
            itens.add(new Rateio.ItemRateio(UUID.randomUUID(), BigDecimal.valueOf(peso)));
        }

        List<Rateio.QuotaRateio> resultado = Rateio.ratear(new BigDecimal("1000.3333"), itens, 4);

        assertSomaExata(new BigDecimal("1000.3333"), resultado);
    }

    @Test
    void somaDePesosZeroAbortaComLacunaNaoDivideIgualmente() {
        List<Rateio.ItemRateio> itens = List.of(
                new Rateio.ItemRateio(UUID.randomUUID(), BigDecimal.ZERO),
                new Rateio.ItemRateio(UUID.randomUUID(), BigDecimal.ZERO));

        assertThrows(RateioImpossivelException.class, () -> Rateio.ratear(new BigDecimal("10.00"), itens, 2));
    }

    @Test
    void listaVaziaAborta() {
        assertThrows(RateioImpossivelException.class, () -> Rateio.ratear(new BigDecimal("10.00"), List.of(), 2));
    }

    @Test
    void reprodutibilidadeMesmaEntradaCemExecucoesSaidaIdentica() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        List<Rateio.ItemRateio> itens = List.of(
                new Rateio.ItemRateio(idA, new BigDecimal("33.00")),
                new Rateio.ItemRateio(idB, new BigDecimal("33.00")),
                new Rateio.ItemRateio(idC, new BigDecimal("34.00")));
        BigDecimal total = new BigDecimal("99.9999");

        List<Rateio.QuotaRateio> primeiraExecucao = Rateio.ratear(total, itens, 4);
        for (int i = 0; i < 100; i++) {
            List<Rateio.QuotaRateio> execucaoAtual = Rateio.ratear(total, itens, 4);
            assertEquals(paraMapa(primeiraExecucao), paraMapa(execucaoAtual),
                    "execucao " + i + " divergiu da primeira - rateio nao e deterministico");
        }
    }

    @Test
    void pesoNegativoNoItemLancaExcecao() {
        assertThrows(IllegalArgumentException.class,
                () -> new Rateio.ItemRateio(UUID.randomUUID(), new BigDecimal("-1")));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static Map<UUID, BigDecimal> paraMapa(List<Rateio.QuotaRateio> quotas) {
        return quotas.stream().collect(Collectors.toMap(Rateio.QuotaRateio::id, Rateio.QuotaRateio::quota));
    }

    private static void assertSomaExata(BigDecimal totalEsperado, List<Rateio.QuotaRateio> quotas) {
        BigDecimal soma = quotas.stream().map(Rateio.QuotaRateio::quota).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertTrue(soma.compareTo(totalEsperado) == 0,
                "soma das quotas (" + soma + ") deveria ser EXATAMENTE igual ao total (" + totalEsperado + ")");
    }
}
