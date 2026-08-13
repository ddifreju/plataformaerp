package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.custo.NaturezaCusto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Testes PUROS de {@link AplicadorTaxa}.
 */
class AplicadorTaxaTest {

    private static final UUID CANAL_ID = UUID.randomUUID();
    private static final OffsetDateTime VIGENCIA = OffsetDateTime.parse("2026-01-01T00:00:00-03:00");

    @Test
    void percentualMultiplicaBaseSemArredondarNoMeio() {
        // Mesma conta do exemplo 2.5: 0,130000 x 199,90 = 25,987000 -> 25,9870.
        TaxaCanal taxa = new TaxaCanal(CANAL_ID, TipoTaxaCanal.COMISSAO, NaturezaCusto.COMISSAO_CANAL,
                BaseIncidencia.VALOR_TOTAL_ITEM, "MLB1051", "CLASSICO", null, null, VIGENCIA, null,
                new BigDecimal("0.130000"), null, null, null, "BRL",
                ConfiancaTaxa.INFORMADO_PELO_LOJISTA, OrigemTaxa.CADASTRO_MANUAL, null, null, null, null, "{}");

        AplicadorTaxa.ResultadoAplicacao resultado = AplicadorTaxa.aplicar(taxa, new BigDecimal("199.9000"));

        assertEquals(0, resultado.valor().compareTo(new BigDecimal("25.9870")));
        assertEquals(0, resultado.baseCalculo().compareTo(new BigDecimal("199.9000")));
        assertEquals(0, resultado.aliquotaAplicada().compareTo(new BigDecimal("0.130000")));
    }

    @Test
    void valorFixoIgnoraBaseEAliquotaFicaNula() {
        TaxaCanal taxa = new TaxaCanal(CANAL_ID, TipoTaxaCanal.TARIFA_FIXA, NaturezaCusto.TARIFA_FIXA_CANAL,
                BaseIncidencia.VALOR_UNITARIO_ITEM, TaxaCanal.CURINGA, TaxaCanal.CURINGA,
                BigDecimal.ZERO, new BigDecimal("12.50"), VIGENCIA, null, null, new BigDecimal("6.00"), null, null,
                "BRL", ConfiancaTaxa.INFORMADO_PELO_LOJISTA, OrigemTaxa.CADASTRO_MANUAL, null, null, null, null, "{}");

        AplicadorTaxa.ResultadoAplicacao resultado = AplicadorTaxa.aplicar(taxa, new BigDecimal("9.9900"));

        assertEquals(0, resultado.valor().compareTo(new BigDecimal("6.0000")));
        assertNull(resultado.aliquotaAplicada(), "valor fixo nao tem aliquota - grava NULL (secao 7, regra 2)");
    }

    @Test
    void resultadoAbaixoDoMinimoEElevadoAoPiso() {
        TaxaCanal taxa = new TaxaCanal(CANAL_ID, TipoTaxaCanal.COMISSAO, NaturezaCusto.COMISSAO_CANAL,
                BaseIncidencia.VALOR_TOTAL_ITEM, TaxaCanal.CURINGA, TaxaCanal.CURINGA, null, null, VIGENCIA, null,
                new BigDecimal("0.100000"), null, new BigDecimal("5.0000"), null, "BRL",
                ConfiancaTaxa.INFORMADO_PELO_LOJISTA, OrigemTaxa.CADASTRO_MANUAL, null, null, null, null, "{}");

        // 10% de 10,00 = 1,00, abaixo do minimo de 5,00 -> usa o minimo.
        AplicadorTaxa.ResultadoAplicacao resultado = AplicadorTaxa.aplicar(taxa, new BigDecimal("10.0000"));

        assertEquals(0, resultado.valor().compareTo(new BigDecimal("5.0000")));
    }

    @Test
    void resultadoAcimaDoMaximoELimitadoAoTeto() {
        TaxaCanal taxa = new TaxaCanal(CANAL_ID, TipoTaxaCanal.COMISSAO, NaturezaCusto.COMISSAO_CANAL,
                BaseIncidencia.VALOR_TOTAL_ITEM, TaxaCanal.CURINGA, TaxaCanal.CURINGA, null, null, VIGENCIA, null,
                new BigDecimal("0.200000"), null, null, new BigDecimal("50.0000"), "BRL",
                ConfiancaTaxa.INFORMADO_PELO_LOJISTA, OrigemTaxa.CADASTRO_MANUAL, null, null, null, null, "{}");

        // 20% de 1000,00 = 200,00, acima do maximo de 50,00 -> usa o teto.
        AplicadorTaxa.ResultadoAplicacao resultado = AplicadorTaxa.aplicar(taxa, new BigDecimal("1000.0000"));

        assertEquals(0, resultado.valor().compareTo(new BigDecimal("50.0000")));
    }
}
