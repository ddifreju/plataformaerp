package com.plataforma.margem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes PUROS (sem Spring, sem banco) de {@link MotorMargemPedido}.
 *
 * O primeiro teste reproduz EXATAMENTE o exemplo numerico trabalhado da
 * secao 2.5 do documento fiscal (ML Classico, R$ 199,90, Simples faixa 3)
 * - asserção dura em cada um dos quatro numeros. Os demais cobrem os tres
 * rotulos da regra do teto (secao 9.2), um por cenario.
 */
class MotorMargemPedidoTest {

    private static final UUID PEDIDO_ID = UUID.randomUUID();
    private static final UUID CANAL_ID = UUID.randomUUID();
    private static final OffsetDateTime FEITO_EM = OffsetDateTime.parse("2026-03-14T15:00:00-03:00");

    // ------------------------------------------------------------------
    // Exemplo numerico da secao 2.5
    // ------------------------------------------------------------------

    @Test
    void exemploDaSecao25MercadoLivreClassico19990ComSimplesFaixa3() {
        List<Custo> custos = List.of(
                custo(NaturezaCusto.MERCADORIA, new BigDecimal("82.5000"), false, "custo_unitario_atual congelado na ingestao"),
                custo(NaturezaCusto.COMISSAO_CANAL, new BigDecimal("25.9870"), false, "informado pela fonte, base=199,90 aliquota=0,13"),
                custo(NaturezaCusto.TARIFA_FIXA_CANAL, new BigDecimal("0.0000"), false, "acima da faixa de tarifa fixa"),
                custo(NaturezaCusto.FRETE, new BigDecimal("24.9000"), false, "cobrado do vendedor, informado pela fonte"),
                custo(NaturezaCusto.EMBALAGEM, new BigDecimal("1.8000"), true, "rateio EMBALAGEM_POR_ITEM"),
                custo(NaturezaCusto.IMPOSTO, new BigDecimal("13.4494"), true, "Simples, aliquota efetiva 0,067280 x 199,90"),
                custo(NaturezaCusto.ADS, new BigDecimal("6.3000"), true, "rateio ADS_POR_RECEITA_DO_PERIODO"));
        // TAXA_ANTECIPACAO nao cadastrada -> lacuna (nao vira linha de custo).
        List<Lacuna> lacunas = List.of(new Lacuna("taxa_antecipacao_nao_cadastrada",
                "TAXA_ANTECIPACAO nao cadastrada para este canal.", DirecaoViesLacuna.SUPERESTIMA_MARGEM));

        ResultadoMargemPedido resultado = MotorMargemPedido.calcular(
                PEDIDO_ID, new BigDecimal("199.9000"), custos, lacunas);

        assertEquals(0, resultado.faturamentoBrutoN0().compareTo(new BigDecimal("199.9000")), "N0");
        assertEquals(0, resultado.receitaLiquidaN1().compareTo(new BigDecimal("199.9000")), "N1 (sem devolucao)");
        assertEquals(0, resultado.margemContribuicaoN2().compareTo(new BigDecimal("51.2636")), "N2");
        assertEquals(0, resultado.resultadoPedidoN3().compareTo(new BigDecimal("44.9636")), "N3");

        assertTrue(resultado.margemContribuicaoPercentual().isPresent());
        assertTrue(resultado.margemLiquidaPercentual().isPresent());
        assertEquals(0, Apresentacao.paraExibicao(resultado.margemContribuicaoPercentual().get().multiply(BigDecimal.valueOf(100)))
                .compareTo(new BigDecimal("25.64")), "margem de contribuicao % (apresentacao)");
        assertEquals(0, Apresentacao.paraExibicao(resultado.margemLiquidaPercentual().get().multiply(BigDecimal.valueOf(100)))
                .compareTo(new BigDecimal("22.49")), "margem liquida % (apresentacao)");

        // So a lacuna de antecipacao, e ela empurra para cima -> COM_TETO.
        assertEquals(RotuloTeto.COM_TETO, resultado.rotulo());
        assertEquals(7, resultado.idsCustoUsados().size(), "todas as 7 linhas de custo devem estar na memoria de calculo");
    }

    // ------------------------------------------------------------------
    // Os tres rotulos da regra do teto (secao 9.2)
    // ------------------------------------------------------------------

    @Test
    void semLacunasRotuloECalculada() {
        List<Custo> custos = List.of(
                custo(NaturezaCusto.MERCADORIA, new BigDecimal("50.0000"), false, "informado"),
                custo(NaturezaCusto.IMPOSTO, new BigDecimal("10.0000"), true, "Simples"));

        ResultadoMargemPedido resultado = MotorMargemPedido.calcular(
                PEDIDO_ID, new BigDecimal("100.0000"), custos, List.of());

        assertEquals(RotuloTeto.CALCULADA, resultado.rotulo());
    }

    @Test
    void todasAsLacunasParaCimaRotuloEComTeto() {
        List<Lacuna> lacunas = List.of(
                CatalogoLacunas.custoMercadoriaNaoCadastrado(),
                CatalogoLacunas.regimeTributarioNaoConfigurado());

        ResultadoMargemPedido resultado = MotorMargemPedido.calcular(
                PEDIDO_ID, new BigDecimal("100.0000"), List.of(), lacunas);

        assertEquals(RotuloTeto.COM_TETO, resultado.rotulo());
    }

    @Test
    void lacunaDeViesOpostoMisturadaRotuloEIndeterminada() {
        List<Lacuna> lacunas = List.of(
                CatalogoLacunas.custoMercadoriaNaoCadastrado(), // SUPERESTIMA_MARGEM
                new Lacuna("icms_st_nao_sinalizado", "ICMS-ST nao sinalizado para este produto.",
                        DirecaoViesLacuna.SUBESTIMA_MARGEM)); // vies oposto - catalogo #8

        ResultadoMargemPedido resultado = MotorMargemPedido.calcular(
                PEDIDO_ID, new BigDecimal("100.0000"), List.of(), lacunas);

        assertEquals(RotuloTeto.INDETERMINADA, resultado.rotulo());
    }

    @Test
    void lacunaDeDirecaoDesconhecidaTambemDeixaIndeterminada() {
        List<Lacuna> lacunas = List.of(
                CatalogoLacunas.custoMercadoriaNaoCadastrado(), // SUPERESTIMA_MARGEM
                CatalogoLacunas.repassePrevistoAusente()); // INDETERMINADA

        ResultadoMargemPedido resultado = MotorMargemPedido.calcular(
                PEDIDO_ID, new BigDecimal("100.0000"), List.of(), lacunas);

        assertEquals(RotuloTeto.INDETERMINADA, resultado.rotulo());
    }

    // ------------------------------------------------------------------
    // Percentual e faturamento zero (secao 6.5)
    // ------------------------------------------------------------------

    @Test
    void faturamentoZeroPercentualNaoExisteOptionalVazio() {
        ResultadoMargemPedido resultado = MotorMargemPedido.calcular(
                PEDIDO_ID, BigDecimal.ZERO, List.of(), List.of());

        assertFalse(resultado.margemContribuicaoPercentual().isPresent(), "0% seria mentira - nao aplicavel");
        assertFalse(resultado.margemLiquidaPercentual().isPresent());
        // N0-N3 continuam calculados (todos zero aqui), so o percentual e ausente.
        assertEquals(0, resultado.faturamentoBrutoN0().compareTo(BigDecimal.ZERO));
    }

    // ------------------------------------------------------------------
    // Estorno: sinal negativo entra na soma automaticamente (secao 2.3)
    // ------------------------------------------------------------------

    @Test
    void estornoDeComissaoComValorNegativoReduzOBlocoAutomaticamente() {
        List<Custo> custos = List.of(
                custo(NaturezaCusto.COMISSAO_CANAL, new BigDecimal("20.0000"), false, "comissao original"),
                custo(NaturezaCusto.COMISSAO_CANAL, new BigDecimal("-20.0000"), false, "estorno da comissao na devolucao"));

        ResultadoMargemPedido resultado = MotorMargemPedido.calcular(
                PEDIDO_ID, new BigDecimal("100.0000"), custos, List.of());

        // B3 (custos do canal) deve somar zero: comissao original + estorno.
        MemoriaCalculoBloco b3 = resultado.decomposicao().stream()
                .filter(bloco -> bloco.bloco() == BlocoMargem.B3_CUSTOS_CANAL)
                .findFirst().orElseThrow();
        assertEquals(0, b3.valor().compareTo(BigDecimal.ZERO), "estorno deve zerar o bloco sozinho, sem tratamento especial");
        assertEquals(0, resultado.margemContribuicaoN2().compareTo(new BigDecimal("100.0000")));
    }

    // ------------------------------------------------------------------
    // Auxiliar
    // ------------------------------------------------------------------

    private static Custo custo(NaturezaCusto natureza, BigDecimal valor, boolean ehEstimativa, String descricao) {
        return new Custo(natureza, PEDIDO_ID, null, null, valor, "BRL", FEITO_EM, ehEstimativa,
                null, null, null, null, descricao, CANAL_ID, null, "{}");
    }
}
