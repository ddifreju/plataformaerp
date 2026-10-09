package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.json;
import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                                                + "\",\"tipo\":\"SIMPLES\",\"sku\":\"ML-S1\",\"nome\":\"Persiana"
                                                + " rolô\",\"preco\":\"80.00\","
                                                + "\"custo\":\"30.00\",\"origem\":\"0\",\"ncm\":\"63031200\","
                                                + "\"motivo_sem_gtin\":\"SEM_CODIGO_DO_FABRICANTE\",\"marca\":\"X\","
                                                + "\"categoria_nome\":\"Persianas\",\"descricao\":\"d\","
                                                + "\"peso_bruto_kg\":\"1\",\"largura_cm\":\"1\","
                                                + "\"altura_cm\":\"1\",\"comprimento_cm\":\"1\"}"),
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

    @Test
    void excluirEmLoteSoApagaOQueNaoEstaNoArNemTemHistorico() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        logar(empresa);
        importar(
                empresa,
                "[{\"id_externo\":\"E1\",\"titulo\":\"x\",\"preco\":\"5.00\","
                        + "\"situacao\":\"NAO_PUBLICADO\"},"
                        + "{\"id_externo\":\"E2\",\"titulo\":\"x\",\"preco\":\"5.00\"},"
                        + "{\"id_externo\":\"E3\",\"titulo\":\"x\",\"preco\":\"5.00\","
                        + "\"situacao\":\"ENCERRADO\"}]");
        UUID e1 = (UUID) anuncio(empresa, "E1").get("id");
        UUID e2 = (UUID) anuncio(empresa, "E2").get("id");
        UUID e3 = (UUID) anuncio(empresa, "E3").get("id");
        // E3 tem proposta de preço: fica, pelo histórico.
        naEmpresa(
                empresa,
                () ->
                        anuncios.executar(
                                "anuncios_precos",
                                json(
                                        "{\"motivo\":\"r\",\"itens\":[{\"id\":\""
                                                + e3
                                                + "\",\"preco\":\"6.00\"}]}"),
                                "DONO"));
        String corpo =
                "{\"acao\":\"EXCLUIR\",\"ids\":[\"" + e1 + "\",\"" + e2 + "\",\"" + e3 + "\"]}";

        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                outra,
                                () ->
                                        anuncios.executar(
                                                "anuncios_acao_lote", json(corpo), "DONO")));
        var r =
                naEmpresa(
                        empresa,
                        () -> anuncios.executar("anuncios_acao_lote", json(corpo), "DONO"));
        assertEquals(1, r.get("excluidos"));
        assertEquals(1, r.get("mantidosNoAr"));
        assertEquals(1, r.get("mantidosComHistorico"));
        Integer restam =
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select count(*) from radar_anuncio where id_externo in"
                                                + " ('E1','E2','E3')",
                                        Integer.class));
        assertEquals(2, restam);

        var semCargo =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                anuncios.executar(
                                                        "anuncios_acao_lote",
                                                        json(corpo),
                                                        "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, semCargo.getStatusCode());
    }

    @Test
    void criarProdutosEmLoteCriaIncompletoEVinculaOAnuncio() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID antigo = BancoRadarDeTeste.novoProduto(empresa, "CORT-1", "10.00", "50.00");
        importar(
                empresa,
                "[{\"id_externo\":\"C1\",\"titulo\":\"Cortina verde\",\"preco\":\"79.90\","
                        + "\"sku\":\"CORT-1\"}]");
        assertEquals(antigo, anuncio(empresa, "C1").get("produto_id"));
        UUID a = (UUID) anuncio(empresa, "C1").get("id");
        naEmpresa(
                empresa,
                () ->
                        anuncios.executar(
                                "anuncios_acao_lote",
                                json("{\"acao\":\"CRIAR_PRODUTOS\",\"ids\":[\"" + a + "\"]}"),
                                "MARKETING"));
        Object novo = anuncio(empresa, "C1").get("produto_id");
        assertTrue(!antigo.equals(novo));
        var p = linha(empresa, "select * from radar_produto where id=?", novo);
        assertEquals(true, p.get("incompleto"));
        assertEquals("Cortina verde", p.get("nome"));
        assertEquals("CORT-1-2", p.get("sku"));
        assertEquals(0, new BigDecimal("79.90").compareTo((BigDecimal) p.get("preco")));
    }

    // ---- apoio -----------------------------------------------------------------------------

    @Test
    void variasLojasNoMesmoMarketplaceComNomesDiferentes() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID x = novaLoja(empresa, "Mercado Livre", "ML Loja X");
        novaLoja(empresa, "Mercado Livre", "ML Loja Y");
        var repetida =
                assertThrows(
                        ResponseStatusException.class,
                        () -> novaLoja(empresa, "Mercado Livre", "ml loja x"));
        // A mensagem mostra o nome que já existe, não o digitado.
        assertTrue(repetida.getReason().contains("Já existe a loja ML Loja X no Mercado Livre"));
        // Em outro marketplace o mesmo nome pode.
        novaLoja(empresa, "Shopee", "ML Loja X");
        assertThrows(
                ResponseStatusException.class, () -> novaLoja(empresa, "SHEIN", "Loja SHEIN"));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresa,
                                () ->
                                        anuncios.executar(
                                                "loja_salvar",
                                                json("{\"marketplace\":\"Shopee\",\"nome\":\"S\"}"),
                                                "MARKETING")));
        naEmpresa(
                empresa,
                () ->
                        anuncios.executar(
                                "loja_remover", json("{\"id\":\"" + x + "\"}"), "GESTOR"));
        // Removida, o nome fica livre de novo.
        novaLoja(empresa, "Mercado Livre", "ML Loja X");
        assertEquals(3, naEmpresa(empresa, () -> anuncios.lojas()).size());
        // Renomear com id inventado: loja não encontrada (não "nome repetido").
        var inventada =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                anuncios.executar(
                                                        "loja_salvar",
                                                        json(
                                                                "{\"id\":\""
                                                                        + UUID.randomUUID()
                                                                        + "\",\"nome\":\"ML Loja X\"}"),
                                                        "DONO")));
        assertEquals(HttpStatus.NOT_FOUND, inventada.getStatusCode());
    }

    @Test
    void lojaDeOutraEmpresaNaoApareceNemPodeSerUsada() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        UUID lojaDaOutra = novaLoja(outra, "Shopee", "Shopee da outra");
        assertTrue(naEmpresa(empresa, () -> anuncios.lojas()).isEmpty());
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresa,
                                () ->
                                        anuncios.executar(
                                                "loja_salvar",
                                                json(
                                                        "{\"id\":\""
                                                                + lojaDaOutra
                                                                + "\",\"nome\":\"Minha\"}"),
                                                "DONO")));
        UUID produto = produtoCompleto(empresa, "Shopee");
        assertThrows(
                ResponseStatusException.class,
                () -> anunciar(empresa, produto, lojaDaOutra, "Cortina blackout azul", "5"));
        var remover =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                anuncios.executar(
                                                        "loja_remover",
                                                        json("{\"id\":\"" + lojaDaOutra + "\"}"),
                                                        "DONO")));
        assertEquals(HttpStatus.NOT_FOUND, remover.getStatusCode());
        assertEquals(1, naEmpresa(outra, () -> anuncios.lojas()).size());
    }

    @Test
    void cargoSemPermissaoNaoRemoveLojaNemAnuncia() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID loja = novaLoja(empresa, "Shopee", "Shopee");
        UUID produto = produtoCompleto(empresa, "Shopee");
        var remover =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                anuncios.executar(
                                                        "loja_remover",
                                                        json("{\"id\":\"" + loja + "\"}"),
                                                        "MARKETING")));
        assertEquals(HttpStatus.FORBIDDEN, remover.getStatusCode());
        String corpo =
                "{\"itens\":[{\"produto_id\":\""
                        + produto
                        + "\",\"loja_id\":\""
                        + loja
                        + "\",\"titulo\":\"Cortina\",\"preco\":\"89.90\",\"estoque\":\"1\"}]}";
        var anunciar =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () -> anuncios.executar("anunciar", json(corpo), "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, anunciar.getStatusCode());
    }

    @Test
    void precoAbaixoDoCustoNaoFicaProntoELojaRemovidaVoltaARascunho() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID loja = novaLoja(empresa, "Shopee", "Shopee");
        UUID produto = produtoCompleto(empresa, "Shopee");
        BancoRadarDeTeste.executarComoDono(
                "update radar_produto set custo=100 where id=?", produto);
        var abaixo =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, loja, "Cortina", "1"));
        assertTrue(abaixo.getReason().contains("política de preço")); // marketing não fica sabendo que o motivo é o custo
        BancoRadarDeTeste.executarComoDono("update radar_produto set custo=30 where id=?", produto);
        anunciar(empresa, produto, loja, "Cortina", "1");
        naEmpresa(
                empresa,
                () ->
                        anuncios.executar(
                                "loja_remover", json("{\"id\":\"" + loja + "\"}"), "DONO"));
        assertEquals(
                "RASCUNHO",
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select estado from radar_anuncio where produto_id=?",
                                        String.class,
                                        produto)));
    }

    @Test
    void anunciarCriaAnuncioProntoNaLojaComEstoqueLivre() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID loja = novaLoja(empresa, "Mercado Livre", "ML Loja X");
        UUID produto = produtoCompleto(empresa, "Mercado Livre");
        // Estoque físico do produto é zero: a quantidade anunciada é escolha da lojista.
        var r = anunciar(empresa, produto, loja, "Cortina blackout azul", "50000");
        assertEquals(1, r.get("criados"));
        var a =
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForMap(
                                        "select * from radar_anuncio where produto_id=?",
                                        produto));
        assertEquals("PRONTO", a.get("estado"));
        assertEquals("Mercado Livre", a.get("canal"));
        assertEquals(loja, a.get("loja_id"));
        assertEquals(50000, a.get("estoque"));
        assertEquals("NAO_PUBLICADO", a.get("situacao_ecommerce"));
        // Outro anúncio do mesmo produto na mesma loja é permitido (teste de título, ads).
        anunciar(empresa, produto, loja, "Outro título", "1");
        assertEquals(
                2,
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select count(*) from radar_anuncio where loja_id=?",
                                        Integer.class,
                                        loja)));
    }

    @Test
    void anunciarConfereTituloCadastroImagemECategoria() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID ml = novaLoja(empresa, "Mercado Livre", "ML");
        UUID tiktok = novaLoja(empresa, "TikTok Shop", "TikTok");
        UUID produto = produtoCompleto(empresa, "Mercado Livre");

        var longo =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, ml, "x".repeat(61), "1"));
        assertTrue(longo.getReason().contains("vai até 60 letras"));
        // Categoria ligada só ao Mercado Livre: no TikTok Shop falta o vínculo.
        var semVinculo =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                anunciar(
                                        empresa,
                                        produto,
                                        tiktok,
                                        "Cortina blackout azul para quarto",
                                        "1"));
        assertTrue(semVinculo.getReason().contains("ligue a categoria"));

        BancoRadarDeTeste.executarComoDono(
                "delete from radar_produto_imagem where produto_id=?", produto);
        var semImagem =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, ml, "Cortina", "1"));
        assertTrue(semImagem.getReason().contains("imagem"));

        BancoRadarDeTeste.executarComoDono("update radar_produto set ncm='' where id=?", produto);
        var incompleto =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, ml, "Cortina", "1"));
        assertTrue(incompleto.getReason().contains("NCM"));
    }

    @Test
    void anunciarRespeitaPrecoEQuantidadeDeCadaMarketplace() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID tiktok = novaLoja(empresa, "TikTok Shop", "TikTok");
        UUID produto = produtoCompleto(empresa, "TikTok Shop");
        String titulo = "Cortina blackout azul para quarto";
        var curto =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, tiktok, "Cortina azul", "1"));
        assertTrue(curto.getReason().contains("pelo menos 25 letras"));
        var demais =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, tiktok, titulo, "100000"));
        assertTrue(demais.getReason().contains("no máximo 99.999"));
        assertEquals(1, anunciar(empresa, produto, tiktok, titulo, "99999").get("criados"));

        UUID ml = novaLoja(empresa, "Mercado Livre", "ML");
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_categoria_canal(tenant_id,categoria_id,canal,codigo_externo,"
                        + "nome_externo) select tenant_id,categoria_id,'Mercado Livre','M','M'"
                        + " from radar_categoria_canal where tenant_id=?",
                empresa);
        var zero =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, ml, "Cortina", "0"));
        assertTrue(zero.getReason().contains("pelo menos 1"));
    }

    @Test
    void anunciarMostraTodosOsProblemasDeUmaVez() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID tiktok = novaLoja(empresa, "TikTok Shop", "TikTok");
        UUID shopee = novaLoja(empresa, "Shopee", "Shopee");
        UUID produto = produtoCompleto(empresa, "TikTok Shop");
        BancoRadarDeTeste.executarComoDono(
                "update radar_produto set ncm='', descricao='Curta demais' where id=?", produto);
        var tudo =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, tiktok, "Curto", "0"));
        String msg = tudo.getReason();
        for (String trecho :
                List.of("NCM", "pelo menos 25 letras", "pelo menos 1", "30 palavras"))
            assertTrue(msg.contains(trecho), "faltou \"" + trecho + "\" em: " + msg);
        // Shopee não tem quantidade mínima oficial, mas anúncio com zero não fica pronto.
        UUID completo = produtoCompleto(empresa, "Shopee");
        var zero =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, completo, shopee, "Cortina", "0"));
        assertTrue(zero.getReason().contains("pelo menos 1"));
        // Título acima de 250 na Shopee: o limite é do Radar, não da Shopee.
        var longo =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, completo, shopee, "x".repeat(251), "1"));
        assertTrue(longo.getReason().contains("o Radar guarda títulos de até 250"));
    }

    @Test
    void fotoPequenaEEmojiNoTitulo() throws Exception {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID ml = novaLoja(empresa, "Mercado Livre", "ML");
        UUID produto = produtoCompleto(empresa, "Mercado Livre");
        // Emoji conta como 1: 59 letras + 1 emoji = 60.
        String titulo = "x".repeat(59) + "\uD83D\uDE00";
        assertEquals(1, anunciar(empresa, produto, ml, titulo, "1").get("criados"));
        // Foto de 200 px: o Mercado Livre pede 500 px no maior lado.
        var imagem = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(imagem, "png", bytes);
        BancoRadarDeTeste.executarComoDono(
                "update radar_produto_imagem set dados=? where produto_id=?", bytes.toByteArray(), produto);
        var pequena =
                assertThrows(
                        ResponseStatusException.class,
                        () -> anunciar(empresa, produto, ml, "Cortina", "1"));
        assertTrue(pequena.getReason().contains("foto(s) pequena(s)"));
    }

    @Test
    void enviarPrecosEEstoqueAtualizaOsAnunciosDasLojasEscolhidas() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID ml = novaLoja(empresa, "Mercado Livre", "ML");
        UUID shopee = novaLoja(empresa, "Shopee", "Shopee");
        UUID produto = produtoCompleto(empresa, "Mercado Livre");
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_categoria_canal(tenant_id,categoria_id,canal,codigo_externo,"
                        + "nome_externo) select tenant_id,categoria_id,'Shopee','S','S'"
                        + " from radar_categoria_canal where tenant_id=?",
                empresa);
        anunciar(empresa, produto, ml, "Cortina", "1");
        anunciar(empresa, produto, shopee, "Cortina", "1");
        BancoRadarDeTeste.executarComoDono(
                "update radar_produto set preco=120, fisico=7, reservado=2 where id=?", produto);
        String corpo =
                "{\"campo\":\"%s\",\"produto_ids\":[\"" + produto + "\"],\"loja_ids\":[\"" + ml + "\"]}";
        // Dono: o preço do cadastro vai direto para o anúncio do ML; o da Shopee fica como estava.
        naEmpresa(
                empresa,
                () -> anuncios.executar("anuncios_sincronizar", json(String.format(corpo, "PRECO")), "DONO"));
        assertEquals(0, new BigDecimal("120.00").compareTo(precoNaLoja(empresa, produto, ml)));
        assertEquals(0, new BigDecimal("89.90").compareTo(precoNaLoja(empresa, produto, shopee)));
        // Estoque: o disponível (7 - 2).
        naEmpresa(
                empresa,
                () -> anuncios.executar("anuncios_sincronizar", json(String.format(corpo, "ESTOQUE")), "DONO"));
        assertEquals(
                5,
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select estoque from radar_anuncio where loja_id=?",
                                        Integer.class,
                                        ml)));
        // Cargo Estoque: leva o estoque, mas não mexe em preço.
        BancoRadarDeTeste.executarComoDono("update radar_produto set fisico=9 where id=?", produto);
        naEmpresa(
                empresa,
                () -> anuncios.executar("anuncios_sincronizar", json(String.format(corpo, "ESTOQUE")), "ESTOQUE"));
        assertEquals(
                7,
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select estoque from radar_anuncio where loja_id=?",
                                        Integer.class,
                                        ml)));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresa,
                                () ->
                                        anuncios.executar(
                                                "anuncios_sincronizar",
                                                json(String.format(corpo, "PRECO")),
                                                "ESTOQUE")));
        assertEquals(0, new BigDecimal("120.00").compareTo(precoNaLoja(empresa, produto, ml)));
        // Nem outra operação de anúncio, mesmo dizendo "ESTOQUE".
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresa,
                                () ->
                                        anuncios.executar(
                                                "anuncios_precos",
                                                json(String.format(corpo, "ESTOQUE")),
                                                "ESTOQUE")));
        // Marketing: vira proposta para aprovação, o preço não muda.
        BancoRadarDeTeste.executarComoDono("update radar_produto set preco=130 where id=?", produto);
        var r =
                naEmpresa(
                        empresa,
                        () ->
                                anuncios.executar(
                                        "anuncios_sincronizar", json(String.format(corpo, "PRECO")), "MARKETING"));
        assertEquals(1, r.get("propostas"));
        assertEquals(0, new BigDecimal("120.00").compareTo(precoNaLoja(empresa, produto, ml)));
        // Repetir não enche a Central de ações com a mesma proposta.
        var repetida =
                naEmpresa(
                        empresa,
                        () ->
                                anuncios.executar(
                                        "anuncios_sincronizar", json(String.format(corpo, "PRECO")), "MARKETING"));
        assertEquals(0, repetida.get("propostas"));
        // A mudança direta do dono também fica no histórico, já executada.
        assertEquals(
                1,
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select count(*) from radar_acao where estado='EXECUTADA'",
                                        Integer.class)));
        // Abaixo do custo: mantém.
        BancoRadarDeTeste.executarComoDono("update radar_produto set preco=10 where id=?", produto);
        var abaixo =
                naEmpresa(
                        empresa,
                        () -> anuncios.executar("anuncios_sincronizar", json(String.format(corpo, "PRECO")), "DONO"));
        assertEquals(1, abaixo.get("mantidos"));
        assertTrue(String.valueOf(abaixo.get("mensagem")).contains("abaixo do custo"));
        // Quem não vê custo não fica sabendo o motivo.
        var semCusto =
                naEmpresa(
                        empresa,
                        () ->
                                anuncios.executar(
                                        "anuncios_sincronizar", json(String.format(corpo, "PRECO")), "MARKETING"));
        assertFalse(String.valueOf(semCusto.get("mensagem")).contains("custo"));
        // Produto de outra empresa: nenhum anúncio é tocado.
        UUID alheio = produtoCompleto(BancoRadarDeTeste.novaEmpresa(), "Mercado Livre");
        var deOutra =
                naEmpresa(
                        empresa,
                        () ->
                                anuncios.executar(
                                        "anuncios_sincronizar",
                                        json(
                                                "{\"campo\":\"ESTOQUE\",\"produto_ids\":[\""
                                                        + alheio
                                                        + "\"],\"loja_ids\":[\""
                                                        + ml
                                                        + "\"]}"),
                                        "DONO"));
        assertEquals(0, deOutra.get("anuncios"));
        // Loja de outra empresa: não encontrada.
        UUID outra = novaLoja(BancoRadarDeTeste.novaEmpresa(), "Shopee", "Outra");
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresa,
                                () ->
                                        anuncios.executar(
                                                "anuncios_sincronizar",
                                                json(
                                                        "{\"campo\":\"PRECO\",\"produto_ids\":[\""
                                                                + produto
                                                                + "\"],\"loja_ids\":[\""
                                                                + outra
                                                                + "\"]}"),
                                                "DONO")));
    }

    private static BigDecimal precoNaLoja(UUID empresa, UUID produto, UUID loja) {
        return naEmpresa(
                empresa,
                () ->
                        db.queryForObject(
                                "select preco from radar_anuncio where produto_id=? and loja_id=?",
                                BigDecimal.class,
                                produto,
                                loja));
    }

    private static UUID novaLoja(UUID empresa, String marketplace, String nome) {
        var r =
                naEmpresa(
                        empresa,
                        () ->
                                anuncios.executar(
                                        "loja_salvar",
                                        json(
                                                "{\"marketplace\":\""
                                                        + marketplace
                                                        + "\",\"nome\":\""
                                                        + nome
                                                        + "\"}"),
                                        "DONO"));
        return (UUID) r.get("id");
    }

    @Test
    void conferirAntesDeSalvarAvisaSemGravarNemRevelarOCusto() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID loja = novaLoja(empresa, "Shopee", "Shopee");
        UUID produto = produtoCompleto(empresa, "Shopee");
        BancoRadarDeTeste.executarComoDono("update radar_produto set custo=100 where id=?", produto);
        String corpo =
                "{\"conferir\":true,\"itens\":[{\"produto_id\":\""
                        + produto
                        + "\",\"loja_id\":\""
                        + loja
                        + "\",\"titulo\":\"Cortina\",\"preco\":\"89.90\",\"estoque\":\"1\"}]}";
        var r = naEmpresa(empresa, () -> anuncios.executar("anunciar", json(corpo), "MARKETING"));
        @SuppressWarnings("unchecked")
        var problemas = (List<String>) r.get("problemas");
        assertEquals(1, problemas.size());
        assertTrue(problemas.getFirst().contains("política de preço"));
        assertFalse(problemas.getFirst().contains("custo"));
        assertEquals(
                0,
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select count(*) from radar_anuncio where produto_id=?",
                                        Integer.class,
                                        produto)));
    }

    private static Map<String, Object> anunciar(
            UUID empresa, UUID produto, UUID loja, String titulo, String estoque) {
        String corpo =
                "{\"itens\":[{\"produto_id\":\""
                        + produto
                        + "\",\"loja_id\":\""
                        + loja
                        + "\",\"titulo\":\""
                        + titulo
                        + "\",\"preco\":\"89.90\",\"estoque\":\""
                        + estoque
                        + "\"}]}";
        return naEmpresa(empresa, () -> anuncios.executar("anunciar", json(corpo), "MARKETING"));
    }

    /** Produto sem pendência, com uma imagem e categoria ligada ao marketplace informado. */
    private static UUID produtoCompleto(UUID empresa, String marketplace) throws SQLException {
        UUID produto =
                BancoRadarDeTeste.novoProduto(
                        empresa, "CORT-" + UUID.randomUUID().toString().substring(0, 6), "30", "90");
        UUID categoria = UUID.randomUUID();
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_categoria(id,tenant_id,nome) values(?,?,?)",
                categoria,
                empresa,
                "Cortinas " + categoria.toString().substring(0, 6));
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_categoria_canal(tenant_id,categoria_id,canal,codigo_externo,"
                        + "nome_externo) values(?,?,?,'C1','Casa > Cortinas')",
                empresa,
                categoria,
                marketplace);
        BancoRadarDeTeste.executarComoDono(
                "update radar_produto set ncm='63039200', origem=0, gtin='4006381333931',"
                        + " marca='Lar', categoria_id=?, descricao=?,"
                        + " peso_bruto_kg=1.2, largura_cm=10, altura_cm=10, comprimento_cm=30"
                        + " where id=?",
                categoria,
                // 30 palavras: o mínimo do TikTok Shop.
                "Cortina blackout azul " + "que bloqueia a luz ".repeat(7) + "e dura muito tempo",
                produto);
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_produto_imagem(id,tenant_id,produto_id,tipo_conteudo,dados)"
                        + " values(?,?,?,'image/png',?)",
                UUID.randomUUID(),
                empresa,
                produto,
                new byte[] {1, 2, 3});
        return produto;
    }

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
