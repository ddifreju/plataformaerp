package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.json;
import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Montar relatório: fontes isoladas por empresa, colunas por cargo e relatórios salvos. */
class RadarFontesTest {

    private static JdbcTemplate db;
    private static RadarFontes fontes;
    private static final String HOJE = LocalDate.now().toString();

    @BeforeAll
    static void preparar() {
        db = BancoRadarDeTeste.comoAplicacao();
        fontes = new RadarFontes(db, BancoRadarDeTeste.JSON);
    }

    @Test
    void pedidosDeUmaEmpresaNaoAparecemNaOutra() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID b = BancoRadarDeTeste.novaEmpresa();
        UUID pa = BancoRadarDeTeste.novoProduto(a, "REL-A", "10.00", "50.00");
        UUID pb = BancoRadarDeTeste.novoProduto(b, "REL-B", "10.00", "50.00");
        BancoRadarDeTeste.novoPedido(a, pa, "Mercado Livre", "Ana", "50.00", "10.00");
        BancoRadarDeTeste.novoPedido(b, pb, "Shopee", "Bia", "50.00", "10.00");
        var linhas = linhas(a, "DONO", "pedidos");
        assertEquals(1, linhas.size());
        assertEquals("REL-A", linhas.getFirst().get("sku"));
        assertEquals("50.00", linhas.getFirst().get("receita_bruta"));
    }

    @Test
    void custoELucroSoParaQuemVeOFinanceiro() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID p = BancoRadarDeTeste.novoProduto(a, "REL-C", "10.00", "50.00");
        BancoRadarDeTeste.novoPedido(a, p, "Mercado Livre", "Ana", "50.00", "10.00");
        var dono = linhas(a, "DONO", "pedidos").getFirst();
        assertTrue(dono.containsKey("resultado"));
        assertTrue(dono.containsKey("custo_produtos"));
        for (String papel : List.of("ESTOQUE", "MARKETING", "ATENDIMENTO", "ANALISTA")) {
            var linha = linhas(a, papel, "pedidos").getFirst();
            for (String col : List.of("resultado", "custo_produtos", "comissao", "margem", "desconto"))
                assertFalse(linha.containsKey(col), papel + " não deveria ver " + col);
            var produto = linhas(a, papel, "produtos").getFirst();
            assertFalse(produto.containsKey("custo"));
            assertFalse(produto.containsKey("valor_estoque"));
            assertFalse(
                    naEmpresa(a, () -> fontes.catalogo(papel)).stream()
                            .anyMatch(f -> f.get("nome").equals("contas")));
        }
    }

    @Test
    void fonteProibidaOuDesconhecidaERecusada() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        var contas =
                assertThrows(ResponseStatusException.class, () -> linhas(a, "MARKETING", "contas"));
        assertEquals(HttpStatus.FORBIDDEN, contas.getStatusCode());
        var atendimento =
                assertThrows(
                        ResponseStatusException.class, () -> linhas(a, "ESTOQUE", "atendimento"));
        assertEquals(HttpStatus.FORBIDDEN, atendimento.getStatusCode());
        // Histórico de custos: só quem vê o financeiro.
        for (String papel : List.of("ESTOQUE", "MARKETING", "ANALISTA", "ATENDIMENTO")) {
            var custos =
                    assertThrows(ResponseStatusException.class, () -> linhas(a, papel, "custos"));
            assertEquals(HttpStatus.FORBIDDEN, custos.getStatusCode());
        }
        var inventada =
                assertThrows(ResponseStatusException.class, () -> linhas(a, "DONO", "senhas"));
        assertEquals(HttpStatus.NOT_FOUND, inventada.getStatusCode());
        assertThrows(
                ResponseStatusException.class,
                () -> naEmpresa(a, () -> fontes.linhas("DONO", "pedidos", "2026-01-01", "2027-06-01")));
        assertThrows(
                ResponseStatusException.class,
                () -> naEmpresa(a, () -> fontes.linhas("DONO", "pedidos", "ontem", null)));
    }

    @Test
    void todasAsFontesRodamParaODono() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID p = BancoRadarDeTeste.novoProduto(a, "REL-T", "10.00", "50.00");
        BancoRadarDeTeste.novoPedido(a, p, "Mercado Livre", "Ana", "50.00", "10.00");
        var catalogo = naEmpresa(a, () -> fontes.catalogo("DONO"));
        assertEquals(9, catalogo.size());
        for (var fonte : catalogo) linhas(a, "DONO", (String) fonte.get("nome"));
    }

    @Test
    void nenhumaFonteMostraDadoDeOutraEmpresa() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID b = BancoRadarDeTeste.novaEmpresa();
        UUID p = BancoRadarDeTeste.novoProduto(a, "REL-ISO", "10.00", "50.00");
        UUID pedido = BancoRadarDeTeste.novoPedido(a, p, "Mercado Livre", "Ana", "50.00", "10.00");
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_movimento(id,tenant_id,produto_id,pedido_id,tipo,fisico_delta,"
                        + "reserva_delta,motivo,ator) values(?,?,?,?,'AJUSTE',1,0,'teste',?)",
                UUID.randomUUID(), a, p, pedido, UUID.randomUUID());
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_registro(id,tenant_id,tipo,dados) values(?,?,'COMPRA',"
                        + "jsonb_build_object('produto_id',?::text,'quantidade','2',"
                        + "'custo_unitario','9.50','fornecedor','F')),(?,?,'MENSAGEM',"
                        + "'{\"cliente\":\"Ana\",\"canal\":\"Shopee\",\"mensagem\":\"oi\"}')",
                UUID.randomUUID(), a, p.toString(), UUID.randomUUID(), a);
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_titulo(id,tenant_id,descricao,tipo,valor,vencimento)"
                        + " values(?,?,'Aluguel','PAGAR',100,current_date)",
                UUID.randomUUID(), a);
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_anuncio(id,tenant_id,produto_id,canal,titulo,preco)"
                        + " values(?,?,?,'Shopee','Anúncio',50)",
                UUID.randomUUID(), a, p);
        for (var fonte : naEmpresa(a, () -> fontes.catalogo("DONO"))) {
            String nome = (String) fonte.get("nome");
            assertFalse(linhas(a, "DONO", nome).isEmpty(), "a empresa A deveria ver " + nome);
            assertTrue(linhas(b, "DONO", nome).isEmpty(), "a empresa B viu dado de A em " + nome);
        }
    }

    @Test
    void cidadeDoClienteSoParaQuemVeOCadastroDeClientes() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID p = BancoRadarDeTeste.novoProduto(a, "REL-CID", "10.00", "50.00");
        BancoRadarDeTeste.novoPedido(a, p, "Mercado Livre", "Ana", "50.00", "10.00");
        for (String papel : List.of("MARKETING", "ESTOQUE")) {
            var linha = linhas(a, papel, "pedidos").getFirst();
            assertFalse(linha.containsKey("cidade"));
            assertFalse(linha.containsKey("uf"));
        }
        assertTrue(linhas(a, "ATENDIMENTO", "pedidos").getFirst().containsKey("cidade"));
    }

    @Test
    void relatorioSalvoTemFormatoConferidoESoApareceParaQuemAbre() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID ana = UUID.randomUUID();
        for (String ruim :
                List.of(
                        "{\"fonte\":\"pedidos\",\"colunas\":\"tudo\"}",
                        "{\"fonte\":\"pedidos\",\"colunas\":[\"a;drop\"]}",
                        "{\"fonte\":\"pedidos\",\"colunas\":[],\"filtros\":[{\"coluna\":\"x\","
                                + "\"op\":\"igual\",\"valor\":\"" + "x".repeat(300) + "\"}]}",
                        "{\"fonte\":\"pedidos\",\"colunas\":[],\"dias\":5000}"))
            assertThrows(
                    ResponseStatusException.class,
                    () ->
                            naEmpresa(
                                    a,
                                    () ->
                                            fontes.executar(
                                                    "relatorio_salvar",
                                                    json("{\"nome\":\"X\",\"config\":" + ruim + "}"),
                                                    "DONO",
                                                    ana)));
        naEmpresa(
                a,
                () ->
                        fontes.executar(
                                "relatorio_salvar",
                                json(
                                        "{\"nome\":\"Contas\",\"config\":{\"fonte\":\"contas\","
                                                + "\"colunas\":[\"valor\"],\"filtros\":[],\"dias\":30}}"),
                                "FINANCEIRO",
                                ana));
        assertEquals(1, naEmpresa(a, () -> fontes.modelos("DONO")).size());
        // Marketing não usa a fonte de contas: o relatório salvo nem chega até ele.
        assertTrue(naEmpresa(a, () -> fontes.modelos("MARKETING")).isEmpty());
    }

    @Test
    void produtosContamVendasDoPeriodo() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID p = BancoRadarDeTeste.novoProduto(a, "REL-V", "10.00", "50.00");
        BancoRadarDeTeste.novoProduto(a, "REL-PARADO", "10.00", "50.00");
        BancoRadarDeTeste.novoPedido(a, p, "Mercado Livre", "Ana", "50.00", "10.00");
        var linhas = linhas(a, "DONO", "produtos");
        var vendido = linhas.stream().filter(l -> l.get("sku").equals("REL-V")).findFirst().get();
        var parado = linhas.stream().filter(l -> l.get("sku").equals("REL-PARADO")).findFirst().get();
        assertTrue(((Number) vendido.get("vendidos")).longValue() > 0);
        assertEquals(0L, ((Number) parado.get("vendidos")).longValue());
    }

    @Test
    void relatoriosSalvosSaoDaEmpresaEQuemApagaEAutorDonoOuGestor() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID b = BancoRadarDeTeste.novaEmpresa();
        UUID ana = UUID.randomUUID(), bia = UUID.randomUUID();
        var r =
                naEmpresa(
                        a,
                        () ->
                                fontes.executar(
                                        "relatorio_salvar",
                                        json(
                                                "{\"nome\":\"Vendas por loja\",\"config\":"
                                                        + "{\"fonte\":\"pedidos\",\"colunas\":[\"marketplace\"]}}"),
                                        "ANALISTA",
                                        ana));
        String id = (String) r.get("id");
        assertEquals(1, naEmpresa(a, () -> fontes.modelos("DONO")).size());
        assertTrue(naEmpresa(b, () -> fontes.modelos("DONO")).isEmpty());
        // Fonte que o cargo não usa não pode ser salva.
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                a,
                                () ->
                                        fontes.executar(
                                                "relatorio_salvar",
                                                json("{\"nome\":\"X\",\"config\":{\"fonte\":\"contas\"}}"),
                                                "MARKETING",
                                                bia)));
        var outro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        a,
                                        () ->
                                                fontes.executar(
                                                        "relatorio_excluir",
                                                        json("{\"id\":\"" + id + "\"}"),
                                                        "MARKETING",
                                                        bia)));
        assertEquals(HttpStatus.FORBIDDEN, outro.getStatusCode());
        // Outra empresa não acha o relatório salvo.
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                b,
                                () ->
                                        fontes.executar(
                                                "relatorio_excluir",
                                                json("{\"id\":\"" + id + "\"}"),
                                                "DONO",
                                                bia)));
        naEmpresa(
                a,
                () ->
                        fontes.executar(
                                "relatorio_excluir", json("{\"id\":\"" + id + "\"}"), "GESTOR", bia));
        assertTrue(naEmpresa(a, () -> fontes.modelos("DONO")).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> linhas(UUID empresa, String papel, String fonte) {
        return (List<Map<String, Object>>)
                naEmpresa(empresa, () -> fontes.linhas(papel, fonte, null, HOJE)).get("linhas");
    }
}
