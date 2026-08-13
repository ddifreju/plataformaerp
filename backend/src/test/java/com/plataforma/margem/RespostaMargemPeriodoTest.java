package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste PURO (sem Spring, sem banco) de {@link RespostaMargemPeriodo}.
 *
 * Prova a secao 6.1/6.2 do documento fiscal na BORDA DE SAIDA: o
 * {@link ResultadoMargemPeriodo} chega em escala de armazenamento (4
 * casas) e {@link RespostaMargemPeriodo#de} devolve os MESMOS numeros, so
 * que arredondados para 2 - sem recalcular nada (os valores de entrada e
 * saida sao numericamente equivalentes ate a 2a casa, so a escala muda).
 */
class RespostaMargemPeriodoTest {

    private static final UUID CANAL_ID = UUID.randomUUID();
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-03-01T00:00:00-03:00");
    private static final OffsetDateTime FIM = OffsetDateTime.parse("2026-04-01T00:00:00-03:00");

    @Test
    void arredondaOsQuatroTotaisParaDuasCasasSemRecalcular() {
        ResultadoMargemPeriodo origem = resultadoComValores(
                new BigDecimal("199.9000"), new BigDecimal("199.9000"),
                new BigDecimal("51.2636"), new BigDecimal("44.9636"), new BigDecimal("44.9636"));

        RespostaMargemPeriodo resposta = RespostaMargemPeriodo.de(origem);

        assertEquals(0, resposta.faturamentoBrutoN0().compareTo(new BigDecimal("199.90")));
        assertEquals(2, resposta.faturamentoBrutoN0().scale(), "borda de saida: 2 casas (secao 6.1)");
        assertEquals(0, resposta.margemContribuicaoN2().compareTo(new BigDecimal("51.26")));
        assertEquals(2, resposta.margemContribuicaoN2().scale());
        assertEquals(0, resposta.resultadoPeriodoN3().compareTo(new BigDecimal("44.96")));
        assertEquals(0, resposta.lucroOperacionalN4().compareTo(new BigDecimal("44.96")));
    }

    @Test
    void convertePercentualDeFracaoParaPontoPercentualComDuasCasas() {
        ResultadoMargemPeriodo origem = new ResultadoMargemPeriodo(
                CANAL_ID, INICIO, FIM, "escopo de teste",
                new BigDecimal("199.9000"), new BigDecimal("199.9000"),
                new BigDecimal("51.2636"), new BigDecimal("44.9636"), new BigDecimal("44.9636"),
                Optional.of(new BigDecimal("0.25644822")), // fracao, 8 casas (escala intermediaria)
                Optional.of(new BigDecimal("0.22494247")),
                List.of(), List.of(), RotuloTeto.CALCULADA, 1, Set.of(), Set.of());

        RespostaMargemPeriodo resposta = RespostaMargemPeriodo.de(origem);

        assertTrue(resposta.margemContribuicaoPercentual().isPresent());
        // 0,25644822 x 100 = 25,644822 -> HALF_UP para 2 casas = 25,64 (mesmo numero da secao 2.5)
        assertEquals(0, resposta.margemContribuicaoPercentual().get().compareTo(new BigDecimal("25.64")));
        assertEquals(0, resposta.margemLiquidaPercentual().get().compareTo(new BigDecimal("22.49")));
    }

    @Test
    void percentualAusenteContinuaAusenteNaResposta() {
        ResultadoMargemPeriodo origem = resultadoComValores(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        RespostaMargemPeriodo resposta = RespostaMargemPeriodo.de(origem);

        assertFalse(resposta.margemContribuicaoPercentual().isPresent(), "faturamento zero: sem percentual, nunca 0%");
        assertFalse(resposta.margemLiquidaPercentual().isPresent());
    }

    @Test
    void decomposicaoPorBlocoTambemSaiEmDuasCasas() {
        Map<BlocoMargem, BigDecimal> valoresPorBloco = new EnumMap<>(BlocoMargem.class);
        for (BlocoMargem bloco : BlocoMargem.values()) {
            valoresPorBloco.put(bloco, BigDecimal.ZERO);
        }
        valoresPorBloco.put(BlocoMargem.B2_CUSTO_MERCADORIA, new BigDecimal("82.5000"));

        List<MemoriaCalculoBlocoPeriodo> decomposicaoOrigem = valoresPorBloco.entrySet().stream()
                .map(entrada -> new MemoriaCalculoBlocoPeriodo(entrada.getKey(), entrada.getValue(), false))
                .toList();

        ResultadoMargemPeriodo origem = new ResultadoMargemPeriodo(
                CANAL_ID, INICIO, FIM, "escopo de teste",
                new BigDecimal("199.9000"), new BigDecimal("199.9000"),
                new BigDecimal("51.2636"), new BigDecimal("44.9636"), new BigDecimal("44.9636"),
                Optional.empty(), Optional.empty(),
                decomposicaoOrigem, List.of(), RotuloTeto.CALCULADA, 1, Set.of(), Set.of());

        RespostaMargemPeriodo resposta = RespostaMargemPeriodo.de(origem);

        MemoriaCalculoBlocoPeriodo blocoMercadoria = resposta.decomposicao().stream()
                .filter(bloco -> bloco.bloco() == BlocoMargem.B2_CUSTO_MERCADORIA)
                .findFirst().orElseThrow();
        assertEquals(0, blocoMercadoria.valor().compareTo(new BigDecimal("82.50")));
        assertEquals(2, blocoMercadoria.valor().scale());
    }

    private static ResultadoMargemPeriodo resultadoComValores(
            BigDecimal n0, BigDecimal n1, BigDecimal n2, BigDecimal n3, BigDecimal n4) {
        return new ResultadoMargemPeriodo(
                CANAL_ID, INICIO, FIM, "escopo de teste",
                n0, n1, n2, n3, n4,
                Optional.empty(), Optional.empty(),
                List.of(), List.of(), RotuloTeto.CALCULADA, 1, Set.of(), Set.of());
    }
}
