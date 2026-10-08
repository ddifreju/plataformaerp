package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.json;
import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Regras de desconto e isolamento das promoções (V019). */
class RadarPromocoesTest {

    private static final LocalDate HOJE = LocalDate.now();

    private static RadarPromocoes promocoes;
    private static UUID empresaA;
    private static UUID empresaB;
    private static UUID produtoA;
    private static UUID outroProdutoA;

    @BeforeAll
    static void preparar() throws SQLException {
        promocoes = new RadarPromocoes(BancoRadarDeTeste.comoAplicacao());
        empresaA = BancoRadarDeTeste.novaEmpresa();
        empresaB = BancoRadarDeTeste.novaEmpresa();
        produtoA = BancoRadarDeTeste.novoProduto(empresaA, "PROMO-1", "10.00", "33.33");
        outroProdutoA = BancoRadarDeTeste.novoProduto(empresaA, "PROMO-2", "10.00", "50.00");
    }

    @Test
    void percentualArredondaParaOCentavoMaisProximo() {
        UUID promo = criar(empresaA, "PERCENTUAL", "15", produtoA, null, HOJE, HOJE);
        // 33,33 × 3 = 99,99; 15% = 14,9985 → 15,00 (HALF_UP)
        BigDecimal desconto =
                naEmpresa(
                        empresaA,
                        () ->
                                promocoes.desconto(
                                        promo, produtoA, "Shopee", new BigDecimal("33.33"), 3));
        assertEquals(new BigDecimal("15.00"), desconto);
    }

    @Test
    void valorFixoEPorUnidade() {
        UUID promo = criar(empresaA, "VALOR_FIXO", "2.50", null, "Shopee", HOJE, HOJE);
        BigDecimal desconto =
                naEmpresa(
                        empresaA,
                        () ->
                                promocoes.desconto(
                                        promo, produtoA, "Shopee", new BigDecimal("33.33"), 4));
        assertEquals(new BigDecimal("10.00"), desconto);
    }

    @Test
    void promocaoForaDoPeriodoERecusada() {
        UUID promo =
                criar(empresaA, "PERCENTUAL", "10", null, null, HOJE.plusDays(1), HOJE.plusDays(5));
        assertRecusado(empresaA, promo, produtoA, "Shopee", "fora do período");
    }

    @Test
    void promocaoDeOutroProdutoOuCanalERecusada() {
        UUID deOutroProduto = criar(empresaA, "PERCENTUAL", "10", outroProdutoA, null, HOJE, HOJE);
        UUID deOutroCanal = criar(empresaA, "PERCENTUAL", "10", null, "AliExpress", HOJE, HOJE);
        assertRecusado(empresaA, deOutroProduto, produtoA, "Shopee", "outro produto");
        assertRecusado(empresaA, deOutroCanal, produtoA, "Shopee", "outro canal");
    }

    @Test
    void promocaoEncerradaERecusada() {
        UUID promo = criar(empresaA, "PERCENTUAL", "10", null, null, HOJE, HOJE);
        naEmpresa(
                empresaA,
                () ->
                        promocoes.executar(
                                "promocao_encerrar", json("{\"id\":\"" + promo + "\"}"), "DONO"));
        assertRecusado(empresaA, promo, produtoA, "Shopee", "encerrada");
    }

    @Test
    void descontoMaiorQueAVendaERecusado() {
        UUID promo = criar(empresaA, "VALOR_FIXO", "40.00", null, null, HOJE, HOJE);
        assertRecusado(empresaA, promo, produtoA, "Shopee", "maior que a venda");
    }

    @Test
    void promocaoDeUmaEmpresaNaoExisteParaOutra() {
        UUID promoA = criar(empresaA, "PERCENTUAL", "10", null, null, HOJE, HOJE);
        assertRecusado(empresaB, promoA, produtoA, "Shopee", "não encontrada");
        @SuppressWarnings("unchecked")
        var daB =
                (List<Map<String, Object>>)
                        naEmpresa(empresaB, () -> promocoes.dados()).get("promocoes");
        assertTrue(daB.isEmpty());
    }

    @Test
    void cargoSemPermissaoNaoCriaPromocao() {
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresaA,
                                        () ->
                                                promocoes.executar(
                                                        "promocao",
                                                        corpo(
                                                                "PERCENTUAL",
                                                                "5",
                                                                null,
                                                                null,
                                                                HOJE,
                                                                HOJE),
                                                        "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
    }

    @Test
    void percentualAcimaDeCemERecusado() {
        assertThrows(
                ResponseStatusException.class,
                () -> criar(empresaA, "PERCENTUAL", "120", null, null, HOJE, HOJE));
    }

    // ---- apoio -----------------------------------------------------------------------------

    private static UUID criar(
            UUID empresa,
            String tipo,
            String valor,
            UUID produto,
            String canal,
            LocalDate de,
            LocalDate ate) {
        return (UUID)
                naEmpresa(
                                empresa,
                                () ->
                                        promocoes.executar(
                                                "promocao",
                                                corpo(tipo, valor, produto, canal, de, ate),
                                                "DONO"))
                        .get("id");
    }

    private static com.fasterxml.jackson.databind.JsonNode corpo(
            String tipo, String valor, UUID produto, String canal, LocalDate de, LocalDate ate) {
        return json(
                "{\"nome\":\"Teste\",\"tipo\":\""
                        + tipo
                        + "\",\"valor\":\""
                        + valor
                        + "\",\"produto_id\":\""
                        + (produto == null ? "" : produto)
                        + "\",\"canal\":\""
                        + (canal == null ? "" : canal)
                        + "\",\"inicio\":\""
                        + de
                        + "\",\"fim\":\""
                        + ate
                        + "\"}");
    }

    private static void assertRecusado(
            UUID empresa, UUID promo, UUID produto, String canal, String trechoDaMensagem) {
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                promocoes.desconto(
                                                        promo,
                                                        produto,
                                                        canal,
                                                        new BigDecimal("33.33"),
                                                        1)));
        assertTrue(
                erro.getReason() != null && erro.getReason().contains(trechoDaMensagem),
                () -> "Mensagem inesperada: " + erro.getReason());
    }
}
