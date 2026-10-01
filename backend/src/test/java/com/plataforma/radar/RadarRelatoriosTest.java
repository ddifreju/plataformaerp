package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Números dos relatórios, isolamento entre empresas e acesso por cargo. */
class RadarRelatoriosTest {

    private static RadarRelatorios relatorios;
    private static UUID empresaA;
    private static UUID empresaVazia;

    @BeforeAll
    static void preparar() throws SQLException {
        relatorios = new RadarRelatorios(BancoRadarDeTeste.comoAplicacao());
        empresaA = BancoRadarDeTeste.novaEmpresa();
        empresaVazia = BancoRadarDeTeste.novaEmpresa();
        UUID grande = BancoRadarDeTeste.novoProduto(empresaA, "ABC-1", "10.00", "100.00");
        UUID medio = BancoRadarDeTeste.novoProduto(empresaA, "ABC-2", "10.00", "100.00");
        UUID pequeno = BancoRadarDeTeste.novoProduto(empresaA, "ABC-3", "10.00", "100.00");
        // Receita: 800 + 150 + 50 = 1000 → A (80%), B (até 95%), C.
        BancoRadarDeTeste.novoPedido(empresaA, grande, "Shopee", "Ana", "800.00", "500.00");
        BancoRadarDeTeste.novoPedido(empresaA, medio, "SHEIN", "Bruno", "150.00", "100.00");
        BancoRadarDeTeste.novoPedido(empresaA, pequeno, "Shopee", "Ana", "50.00", "30.00");
    }

    @Test
    void resumoSomaReceitaResultadoETicketMedio() {
        var resumo = (Map<?, ?>) gerar(empresaA, "DONO").get("resumo");
        assertEquals("1000.00", resumo.get("receita"));
        assertEquals("370.00", resumo.get("resultado"));
        assertEquals(3L, resumo.get("pedidos"));
        assertEquals("333.33", resumo.get("ticketMedio"));
        assertEquals("37.00", resumo.get("margem"));
    }

    @Test
    void curvaAbcClassificaPelaReceitaAcumulada() {
        var curva = lista(gerar(empresaA, "DONO"), "curvaAbc");
        assertEquals(
                List.of("ABC-1", "ABC-2", "ABC-3"), curva.stream().map(l -> l.get("sku")).toList());
        assertEquals(List.of("A", "B", "C"), curva.stream().map(l -> l.get("classe")).toList());
        assertEquals("80.00", curva.get(0).get("participacao"));
    }

    @Test
    void canaisSaoAgrupadosComMargem() {
        var canais = lista(gerar(empresaA, "DONO"), "porCanal");
        var shopee =
                canais.stream()
                        .filter(c -> "Shopee".equals(c.get("nome")))
                        .findFirst()
                        .orElseThrow();
        assertEquals("850.00", shopee.get("receita"));
        assertEquals(new BigDecimal("37.65"), new BigDecimal((String) shopee.get("margem")));
    }

    @Test
    void outraEmpresaNaoVeNumeroDaPrimeira() {
        var resumo = (Map<?, ?>) gerar(empresaVazia, "DONO").get("resumo");
        assertEquals(
                "0",
                new BigDecimal((String) resumo.get("receita"))
                        .stripTrailingZeros()
                        .toPlainString());
        assertEquals(0L, resumo.get("pedidos"));
        assertNull(resumo.get("ticketMedio"));
        assertEquals(List.of(), lista(gerar(empresaVazia, "DONO"), "curvaAbc"));
    }

    @Test
    void cargoSemAcessoFinanceiroERecusado() {
        var erro = assertThrows(ResponseStatusException.class, () -> gerar(empresaA, "ESTOQUE"));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
    }

    @Test
    void periodoInvertidoOuLongoDemaisERecusado() {
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresaA,
                                () -> relatorios.gerar("DONO", "2026-02-01", "2026-01-01")));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresaA,
                                () -> relatorios.gerar("DONO", "2024-01-01", "2026-01-01")));
    }

    @Test
    void clientesComMesmoNomeSaoContadosSeparadosPeloId() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID produto = BancoRadarDeTeste.novoProduto(empresa, "HOM-1", "10.00", "100.00");
        for (String codigo : List.of("C00001", "C00002")) {
            UUID cliente = UUID.randomUUID();
            BancoRadarDeTeste.executarComoDono(
                    "insert into radar_cliente(id,tenant_id,codigo,nome) values(?,?,?,?)",
                    cliente,
                    empresa,
                    codigo,
                    "Juliana Souza");
            UUID pedido =
                    BancoRadarDeTeste.novoPedido(
                            empresa, produto, "Shopee", "Juliana Souza", "100.00", "10.00");
            BancoRadarDeTeste.executarComoDono(
                    "update radar_pedido set cliente_id=? where id=?", cliente, pedido);
        }
        var porCliente = lista(gerar(empresa, "DONO"), "porCliente");
        assertEquals(2, porCliente.size());
        assertEquals(
                List.of("C00001", "C00002"),
                porCliente.stream().map(l -> l.get("codigo").toString()).sorted().toList());
    }

    private static Map<String, Object> gerar(UUID empresa, String papel) {
        return naEmpresa(empresa, () -> relatorios.gerar(papel, null, null));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Map<String, Object> relatorio, String chave) {
        return (List<Map<String, Object>>) relatorio.get(chave);
    }
}
