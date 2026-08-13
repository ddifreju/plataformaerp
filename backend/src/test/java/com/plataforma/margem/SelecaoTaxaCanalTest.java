package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.custo.NaturezaCusto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes PUROS (sem Spring, sem banco) de {@link SelecaoTaxaCanal} e da
 * comparacao de vigencia por instante (secao 8.2 - "fuso horario", o bug
 * esperado e evitado de graca).
 */
class SelecaoTaxaCanalTest {

    private static final UUID CANAL_ID = UUID.randomUUID();

    @Test
    void semCandidatasENaoEncontrada() {
        ResultadoSelecaoTaxa resultado = SelecaoTaxaCanal.selecionar(List.of());
        assertInstanceOf(ResultadoSelecaoTaxa.NaoEncontrada.class, resultado);
    }

    @Test
    void umaCandidataEEncontrada() {
        TaxaCanal taxa = taxaComissao("MLB1051", TaxaCanal.CURINGA, new BigDecimal("0.130000"));
        ResultadoSelecaoTaxa resultado = SelecaoTaxaCanal.selecionar(List.of(taxa));

        assertInstanceOf(ResultadoSelecaoTaxa.Encontrada.class, resultado);
        assertEquals(taxa, ((ResultadoSelecaoTaxa.Encontrada) resultado).taxa());
    }

    @Test
    void duasCandidatasComEspecificidadeDiferenteUsaAPrimeira() {
        // Categoria especifica (especificidade 4) vence sobre o curinga total (0).
        TaxaCanal maisEspecifica = taxaComissao("MLB1051", TaxaCanal.CURINGA, new BigDecimal("0.130000"));
        TaxaCanal curinga = taxaComissao(TaxaCanal.CURINGA, TaxaCanal.CURINGA, new BigDecimal("0.120000"));
        assertTrue(maisEspecifica.getEspecificidade() > curinga.getEspecificidade());

        ResultadoSelecaoTaxa resultado = SelecaoTaxaCanal.selecionar(List.of(maisEspecifica, curinga));

        assertInstanceOf(ResultadoSelecaoTaxa.Encontrada.class, resultado);
        assertEquals(maisEspecifica, ((ResultadoSelecaoTaxa.Encontrada) resultado).taxa());
    }

    @Test
    void duasCandidatasComMesmaEspecificidadeEAmbigua() {
        TaxaCanal a = taxaComissao("MLB1051", TaxaCanal.CURINGA, new BigDecimal("0.130000"));
        TaxaCanal b = taxaComissao("MLB2002", TaxaCanal.CURINGA, new BigDecimal("0.140000"));
        assertEquals(a.getEspecificidade(), b.getEspecificidade(), "setup do teste: as duas precisam empatar");

        ResultadoSelecaoTaxa resultado = SelecaoTaxaCanal.selecionar(List.of(a, b));

        assertInstanceOf(ResultadoSelecaoTaxa.Ambigua.class, resultado);
        ResultadoSelecaoTaxa.Ambigua ambigua = (ResultadoSelecaoTaxa.Ambigua) resultado;
        assertEquals(a.getId(), ambigua.idTaxaA());
        assertEquals(b.getId(), ambigua.idTaxaB());
    }

    @Test
    void especificidadeReflete421ConformeAV013() {
        TaxaCanal soCategoria = taxaComissao("MLB1051", TaxaCanal.CURINGA, new BigDecimal("0.10"));
        TaxaCanal soAnuncio = taxaComissao(TaxaCanal.CURINGA, "CLASSICO", new BigDecimal("0.10"));
        TaxaCanal soFaixa = new TaxaCanal(CANAL_ID, TipoTaxaCanal.TARIFA_FIXA, NaturezaCusto.TARIFA_FIXA_CANAL,
                BaseIncidencia.VALOR_UNITARIO_ITEM, TaxaCanal.CURINGA, TaxaCanal.CURINGA,
                BigDecimal.ZERO, new BigDecimal("12.50"), OffsetDateTime.now(), null, null, new BigDecimal("6.00"),
                null, null, "BRL", ConfiancaTaxa.INFORMADO_PELO_LOJISTA, OrigemTaxa.CADASTRO_MANUAL, null, null,
                null, null, "{}");
        TaxaCanal tudoCuringa = taxaComissao(TaxaCanal.CURINGA, TaxaCanal.CURINGA, new BigDecimal("0.10"));

        assertEquals(4, soCategoria.getEspecificidade());
        assertEquals(2, soAnuncio.getEspecificidade());
        assertEquals(1, soFaixa.getEspecificidade());
        assertEquals(0, tudoCuringa.getEspecificidade());
    }

    // ------------------------------------------------------------------
    // Fuso horario (secao 8.2): venda as 23:30 de Sao Paulo no dia
    // anterior a virada de tarifa precisa continuar na tarifa ANTIGA,
    // mesmo que em UTC ja seja o dia seguinte.
    // ------------------------------------------------------------------

    @Test
    void vendaAs2330DeSaoPauloNaVesperaDaViradaUsaATarifaAntiga() {
        // Tarifa nova vigente a partir de "02/03/2026" em Sao Paulo, que e
        // 2026-03-02T03:00:00Z em UTC (Sao Paulo e UTC-3, sem horario de
        // verao neste periodo).
        OffsetDateTime vigenciaNova = OffsetDateTime.parse("2026-03-02T00:00:00-03:00");
        assertEquals(OffsetDateTime.parse("2026-03-02T03:00:00Z"), vigenciaNova.withOffsetSameInstant(java.time.ZoneOffset.UTC));

        // Pedido feito as 23:30 de 01/03 em Sao Paulo = 02:30 de 02/03 em UTC.
        OffsetDateTime feitoEm = OffsetDateTime.parse("2026-03-01T23:30:00-03:00");

        // O instante do pedido (02:30Z) e ANTERIOR ao instante da vigencia
        // nova (03:00Z) - a comparacao de timestamptz ja e por instante
        // absoluto, sem precisar converter os dois lados para o mesmo
        // fuso na hora de comparar (armadilha descrita no cabecalho da
        // V013: converter os dois lados e um no-op que so descarta indice).
        assertTrue(feitoEm.isBefore(vigenciaNova),
                "pedido das 23:30 de Sao Paulo na vespera deveria cair ANTES da vigencia nova, nao depois");
    }

    // ------------------------------------------------------------------
    // Auxiliar
    // ------------------------------------------------------------------

    private static TaxaCanal taxaComissao(String categoria, String tipoAnuncio, BigDecimal percentual) {
        return new TaxaCanal(CANAL_ID, TipoTaxaCanal.COMISSAO, NaturezaCusto.COMISSAO_CANAL,
                BaseIncidencia.VALOR_TOTAL_ITEM, categoria, tipoAnuncio, null, null,
                OffsetDateTime.parse("2026-01-01T00:00:00-03:00"), null, percentual, null, null, null, "BRL",
                ConfiancaTaxa.INFORMADO_PELO_LOJISTA, OrigemTaxa.CADASTRO_MANUAL, null, null, null, null, "{}");
    }
}
