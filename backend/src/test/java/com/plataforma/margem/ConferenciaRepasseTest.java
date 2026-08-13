package com.plataforma.margem;

import java.math.BigDecimal;
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
 * Testes PUROS de {@link ConferenciaRepasse} (secao 2.6 do documento
 * fiscal).
 */
class ConferenciaRepasseTest {

    private static final UUID PEDIDO_ID = UUID.randomUUID();
    private static final OffsetDateTime FEITO_EM = OffsetDateTime.parse("2026-03-14T15:00:00-03:00");

    @Test
    void repasseDentroDaToleranciaNaoGeraLacunaNemAlerta() {
        List<Custo> custos = List.of(custo(NaturezaCusto.COMISSAO_CANAL, new BigDecimal("13.0000")));
        // repasse_esperado = 100 - 0 - 13 - 0 - 0 = 87.0000
        ConferenciaRepasse.ResultadoConferencia resultado = ConferenciaRepasse.conferir(
                new BigDecimal("100.0000"), custos, new BigDecimal("87.0000"));

        assertTrue(resultado.lacuna().isEmpty());
        assertTrue(resultado.alerta().isEmpty());
        assertEquals(0, resultado.delta().orElseThrow().compareTo(BigDecimal.ZERO));
    }

    @Test
    void repasseMenorQueOEsperadoGeraLacunaQuantificavel() {
        List<Custo> custos = List.of(custo(NaturezaCusto.COMISSAO_CANAL, new BigDecimal("13.0000")));
        // esperado = 87.00, repasse informado = 80.00 -> delta = -7.00: existe custo invisivel de R$7.
        ConferenciaRepasse.ResultadoConferencia resultado = ConferenciaRepasse.conferir(
                new BigDecimal("100.0000"), custos, new BigDecimal("80.0000"));

        assertTrue(resultado.lacuna().isPresent());
        assertEquals(DirecaoViesLacuna.SUPERESTIMA_MARGEM, resultado.lacuna().get().direcaoVies());
        assertTrue(resultado.alerta().isEmpty());
    }

    @Test
    void repasseMaiorQueOEsperadoGeraAlertaNuncaLacuna() {
        List<Custo> custos = List.of(custo(NaturezaCusto.COMISSAO_CANAL, new BigDecimal("13.0000")));
        // esperado = 87.00, repasse informado = 95.00 -> delta = +8.00: bug nosso, nao dado do mundo.
        ConferenciaRepasse.ResultadoConferencia resultado = ConferenciaRepasse.conferir(
                new BigDecimal("100.0000"), custos, new BigDecimal("95.0000"));

        assertTrue(resultado.lacuna().isEmpty(), "delta positivo e bug, nao lacuna (secao 2.6)");
        assertTrue(resultado.alerta().isPresent());
    }

    @Test
    void repassePrevistoAusenteGeraLacunaIndeterminada() {
        ConferenciaRepasse.ResultadoConferencia resultado = ConferenciaRepasse.conferir(
                new BigDecimal("100.0000"), List.of(), null);

        assertTrue(resultado.lacuna().isPresent());
        assertEquals(DirecaoViesLacuna.INDETERMINADA, resultado.lacuna().get().direcaoVies());
        assertFalse(resultado.delta().isPresent());
    }

    @Test
    void embalagemNaoEntraNaFatiaDeFreteDoRepasse() {
        // B4 tem FRETE (entra no repasse) e EMBALAGEM (NAO entra - "inclui
        // apenas o que o canal desconta na fonte", secao 2.6).
        List<Custo> custos = List.of(
                custo(NaturezaCusto.FRETE, new BigDecimal("10.0000")),
                custo(NaturezaCusto.EMBALAGEM, new BigDecimal("5.0000")));
        // Se EMBALAGEM entrasse por engano, esperado seria 100-10-5=85; sem
        // ela, esperado e 100-10=90.
        ConferenciaRepasse.ResultadoConferencia resultado = ConferenciaRepasse.conferir(
                new BigDecimal("100.0000"), custos, new BigDecimal("90.0000"));

        assertTrue(resultado.lacuna().isEmpty(), "90,00 bate com o esperado SEM embalagem - nao pode sobrar lacuna");
        assertEquals(0, resultado.repasseEsperado().orElseThrow().compareTo(new BigDecimal("90.0000")));
    }

    private static Custo custo(NaturezaCusto natureza, BigDecimal valor) {
        return new Custo(natureza, PEDIDO_ID, null, null, valor, "BRL", FEITO_EM, false,
                null, null, null, null, "teste", null, null, "{}");
    }
}
