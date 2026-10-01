package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.json;
import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.plataforma.autenticacao.PapelUsuario;
import com.plataforma.autenticacao.UsuarioAutenticado;
import com.plataforma.autenticacao.UsuarioParaLogin;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Anúncios (V023): importação com vínculo obrigatório, relacionar, preços em lote, isolamento. */
class RadarAnunciosTest {

    private static JdbcTemplate db;
    private static RadarAnuncios anuncios;
    private static RadarProdutos produtos;

    @BeforeAll
    static void preparar() throws SQLException {
        db = BancoRadarDeTeste.comoAplicacao();
        anuncios = new RadarAnuncios(db);
        produtos = new RadarProdutos(db, BancoRadarDeTeste.JSON);
    }

    @AfterAll
    static void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void importadoSemProdutoCriaProdutoIncompletoJaVinculado() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        var r =
                importar(
                        empresa,
                        "[{\"id_externo\":\"MLB123\",\"titulo\":\"Cortina"
                                + " azul\",\"preco\":\"89.90\"}]");
        assertEquals(1, r.get("produtosCriados"));
        var a = anuncio(empresa, "MLB123");
        var p = linha(empresa, "select * from radar_produto where id=?", a.get("produto_id"));
        assertEquals(true, p.get("incompleto"));
        assertEquals("ANUNCIO", p.get("origem_cadastro"));
        assertEquals("ML-MLB123", p.get("sku"));
        assertEquals("Cortina azul", p.get("nome"));
        assertEquals("IMPORTACAO", a.get("origem"));
        assertEquals("ATIVO", a.get("situacao_ecommerce"));
    }

    @Test
    void importadoComSkuOuGtinDeProdutoExistenteVinculaSemCriar() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID porSku = BancoRadarDeTeste.novoProduto(empresa, "PERS-01", "10.00", "50.00");
        UUID porGtin = BancoRadarDeTeste.novoProduto(empresa, "OUTRO-SKU", "10.00", "50.00");
        BancoRadarDeTeste.executarComoDono(
                "update radar_produto set gtin='4006381333931' where id=?", porGtin);
        var r =
                importar(
                        empresa,
                        "[{\"id_externo\":\"A1\",\"titulo\":\"x\",\"preco\":\"50.00\","
                                + "\"sku\":\"pers-01\"},"
                                + "{\"id_externo\":\"A2\",\"titulo\":\"y\",\"preco\":\"50.00\","
                                + "\"gtin\":\"4006381333931\"}]");
        assertEquals(0, r.get("produtosCriados"));
        assertEquals(2, r.get("vinculadosAProdutoExistente"));
        assertEquals(porSku, anuncio(empresa, "A1").get("produto_id"));
        assertEquals(porGtin, anuncio(empresa, "A2").get("produto_id"));
    }

    @Test
    void reimportarAtualizaOMesmoAnuncioSemDuplicar() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        importar(empresa, "[{\"id_externo\":\"R1\",\"titulo\":\"Antes\",\"preco\":\"10.00\"}]");
        var r =
                importar(
                        empresa,
                        "[{\"id_externo\":\"R1\",\"titulo\":\"Depois\",\"preco\":\"12.00\","
                                + "\"situacao\":\"REJEITADO\",\"motivo_rejeicao\":\"Foto"
                                + " com marca d'água\"}]");
        assertEquals(1, r.get("atualizados"));
        var a = anuncio(empresa, "R1");
        assertEquals("Depois", a.get("titulo"));
        assertEquals("REJEITADO", a.get("situacao_ecommerce"));
        Integer total =
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select count(*) from radar_anuncio where tenant_id=?",
                                        Integer.class,
                                        empresa));
        assertEquals(1, total);
    }

    @Test
    void skuJaUsadoPorOutroProdutoGanhaSufixo() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        BancoRadarDeTeste.novoProduto(empresa, "SHP-99", "1.00", "2.00");
        // Sem SKU no anúncio, o código gerado (SHP-99) já existe: vira SHP-99-2.
        UUID[] criado = new UUID[1];
        naEmpresa(
                empresa,
                () -> {
                    anuncios.importar(
                            "Shopee",
                            json("[{\"id_externo\":\"99\",\"titulo\":\"z\",\"preco\":\"3.00\"}]"));
                    criado[0] =
                            db.queryForObject(
                                    "select produto_id from radar_anuncio where id_externo='99'",
                                    UUID.class);
                    return null;
                });
        assertEquals(
                "SHP-99-2",
                linha(empresa, "select sku from radar_produto where id=?", criado[0]).get("sku"));
    }

    @Test
    void salvarProdutoPelaTelaTiraOIncompleto() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        logar(empresa);
        importar(empresa, "[{\"id_externo\":\"S1\",\"titulo\":\"Persiana\",\"preco\":\"80.00\"}]");
        UUID produto = (UUID) anuncio(empresa, "S1").get("produto_id");
        naEmpresa(
                empresa,
                () ->
                        produtos.salvar(
                                json(
                                        "{\"id\":\""
                                                + produto
                                                + "\",\"tipo\":\"SIMPLES\",\"sku\":\"ML-S1\","
                                                + "\"nome\":\"Persiana rolô\",\"preco\":\"80.00\","
                                                + "\"custo\":\"30.00\"}"),
                                "DONO"));
        assertEquals(
                false,
                linha(empresa, "select incompleto from radar_produto where id=?", produto)
                        .get("incompleto"));
    }

    @Test
    void relacionarTrocaOProdutoEOutraEmpresaNaoConsegue() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        UUID certo = BancoRadarDeTeste.novoProduto(empresa, "CERTO", "10.00", "50.00");
        importar(empresa, "[{\"id_externo\":\"V1\",\"titulo\":\"x\",\"preco\":\"50.00\"}]");
        UUID anuncio = (UUID) anuncio(empresa, "V1").get("id");
        String corpo = "{\"produto_id\":\"" + certo + "\",\"ids\":[\"" + anuncio + "\"]}";

        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                outra,
                                () ->
                                        anuncios.executar(
                                                "anuncio_relacionar", json(corpo), "DONO")));
        naEmpresa(empresa, () -> anuncios.executar("anuncio_relacionar", json(corpo), "DONO"));
        assertEquals(certo, anuncio(empresa, "V1").get("produto_id"));
    }

    @Test
    void precosEmLoteViramPropostasSemMudarOPreco() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        logar(empresa);
        importar(empresa, "[{\"id_externo\":\"P1\",\"titulo\":\"x\",\"preco\":\"50.00\"}]");
        UUID anuncio = (UUID) anuncio(empresa, "P1").get("id");
        String corpo =
                "{\"motivo\":\"Reajuste\",\"itens\":[{\"id\":\""
                        + anuncio
                        + "\",\"preco\":\"55.00\"}]}";
        naEmpresa(empresa, () -> anuncios.executar("anuncios_precos", json(corpo), "MARKETING"));
        assertEquals(
                0,
                new BigDecimal("50.00")
                        .compareTo((BigDecimal) anuncio(empresa, "P1").get("preco")));
        var acao =
                linha(empresa, "select depois, estado from radar_acao where anuncio_id=?", anuncio);
        assertEquals("PENDENTE", acao.get("estado"));
        assertEquals(0, new BigDecimal("55.00").compareTo((BigDecimal) acao.get("depois")));

        var semCargo =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                anuncios.executar(
                                                        "anuncios_precos",
                                                        json(corpo),
                                                        "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, semCargo.getStatusCode());
    }

    @Test
    void anuncioImportadoNumaEmpresaNaoApareceNaOutra() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        importar(empresa, "[{\"id_externo\":\"I1\",\"titulo\":\"x\",\"preco\":\"5.00\"}]");
        // Mesmo id_externo em outra empresa é outro anúncio, com outro produto.
        importar(outra, "[{\"id_externo\":\"I1\",\"titulo\":\"y\",\"preco\":\"5.00\"}]");
        Integer visiveis =
                naEmpresa(
                        outra,
                        () ->
                                db.queryForObject(
                                        "select count(*) from radar_anuncio where id_externo='I1'",
                                        Integer.class));
        assertEquals(1, visiveis);
        assertTrue(
                naEmpresa(
                                outra,
                                () ->
                                        db.queryForList(
                                                "select titulo from radar_anuncio", String.class))
                        .equals(List.of("y")));
    }

    @Test
    void importacaoTrazACategoriaDoMarketplaceJaVinculada() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        var r =
                importar(
                        empresa,
                        "[{\"id_externo\":\"C1\",\"titulo\":\"a\",\"preco\":\"9.00\","
                                + "\"categoria_codigo\":\"MLB1234\",\"categoria_nome\":"
                                + "\"Casa > Cortinas > Blackout\"},"
                                + "{\"id_externo\":\"C2\",\"titulo\":\"b\",\"preco\":\"9.00\","
                                + "\"categoria_codigo\":\"MLB1234\",\"categoria_nome\":"
                                + "\"Casa > Cortinas > Blackout\"}]");
        assertEquals(1, r.get("categoriasCriadas"));
        var produto1 =
                linha(
                        empresa,
                        "select categoria_id from radar_produto where id=?",
                        anuncio(empresa, "C1").get("produto_id"));
        var produto2 =
                linha(
                        empresa,
                        "select categoria_id from radar_produto where id=?",
                        anuncio(empresa, "C2").get("produto_id"));
        assertEquals(produto1.get("categoria_id"), produto2.get("categoria_id"));
        var categoria =
                linha(
                        empresa,
                        "select nome, origem from radar_categoria where id=?",
                        produto1.get("categoria_id"));
        assertEquals("Blackout", categoria.get("nome"));
        assertEquals("IMPORTACAO", categoria.get("origem"));
        var vinculo =
                linha(
                        empresa,
                        "select codigo_externo, nome_externo from radar_categoria_canal"
                                + " where categoria_id=?",
                        produto1.get("categoria_id"));
        assertEquals("MLB1234", vinculo.get("codigo_externo"));
        assertEquals("Casa > Cortinas > Blackout", vinculo.get("nome_externo"));
    }

    @Test
    void categoriaRenomeadaContinuaRecebendoAImportacao() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        String item =
                "\"preco\":\"9.00\",\"categoria_codigo\":\"MLB9\",\"categoria_nome\":\"Persianas\"";
        importar(empresa, "[{\"id_externo\":\"N1\",\"titulo\":\"a\"," + item + "}]");
        UUID categoria =
                (UUID)
                        linha(
                                        empresa,
                                        "select categoria_id from radar_produto where id=?",
                                        anuncio(empresa, "N1").get("produto_id"))
                                .get("categoria_id");
        BancoRadarDeTeste.executarComoDono(
                "update radar_categoria set nome='Persianas da Ju' where id=?", categoria);
        var r = importar(empresa, "[{\"id_externo\":\"N2\",\"titulo\":\"b\"," + item + "}]");
        assertEquals(0, r.get("categoriasCriadas"));
        assertEquals(
                categoria,
                linha(
                                empresa,
                                "select categoria_id from radar_produto where id=?",
                                anuncio(empresa, "N2").get("produto_id"))
                        .get("categoria_id"));
    }

    // ---- apoio -----------------------------------------------------------------------------

    private static Map<String, Object> importar(UUID empresa, String itens) {
        return naEmpresa(empresa, () -> anuncios.importar("Mercado Livre", json(itens)));
    }

    private static Map<String, Object> anuncio(UUID empresa, String externo) {
        return naEmpresa(
                empresa,
                () ->
                        db.queryForMap(
                                "select * from radar_anuncio where tenant_id=? and id_externo=?",
                                empresa,
                                externo));
    }

    private static Map<String, Object> linha(UUID empresa, String sql, Object id) {
        return naEmpresa(empresa, () -> db.queryForMap(sql, id));
    }

    /** Propostas registram quem propôs (o usuário logado). */
    private static void logar(UUID empresa) {
        UUID id = UUID.randomUUID();
        var u =
                new UsuarioAutenticado(
                        new UsuarioParaLogin(
                                id,
                                empresa,
                                "teste@radar.test",
                                "x",
                                "Teste",
                                PapelUsuario.DONO,
                                true,
                                true));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }
}
