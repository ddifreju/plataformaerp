package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.json;
import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

/** Cadastro completo de produto (V020): campos, variações, kit, vínculos e isolamento. */
class RadarProdutosTest {

    private static JdbcTemplate db;
    private static RadarProdutos produtos;
    private static UUID empresaA;
    private static UUID empresaB;

    @BeforeAll
    static void preparar() throws SQLException {
        db = BancoRadarDeTeste.comoAplicacao();
        produtos = new RadarProdutos(db, BancoRadarDeTeste.JSON);
        empresaA = BancoRadarDeTeste.novaEmpresa();
        empresaB = BancoRadarDeTeste.novaEmpresa();
        // Movimentos de estoque registram o usuário logado.
        var usuario =
                new UsuarioAutenticado(
                        new UsuarioParaLogin(
                                UUID.randomUUID(),
                                empresaA,
                                "teste@radar.test",
                                "x",
                                "Teste",
                                PapelUsuario.DONO,
                                true,
                                true));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
    }

    @AfterAll
    static void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void gtinComDigitoVerificadorErradoERecusado() {
        assertTrue(RadarEntrada.gtinValido("4006381333931"));
        assertFalse(RadarEntrada.gtinValido("4006381333932"));
        assertTrue(RadarEntrada.gtinValido("96385074"));
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                salvar(
                                        empresaA,
                                        "{" + base("GTIN-1") + ",\"gtin\":\"4006381333932\"}"));
        assertTrue(erro.getReason().contains("GTIN"));
    }

    @Test
    void produtoSimplesGravaCamposCriaCategoriaEEmbalagemNovas() {
        UUID id =
                salvar(
                        empresaA,
                        "{"
                                + base("SIMPLES-1")
                                + ",\"gtin\":\"4006381333931\",\"origem\":\"0\",\"ncm\":\"6303.12.00\","
                                + "\"cest\":\"28.038.00\",\"saldo\":\"7\",\"peso_bruto_kg\":\"1,250\",\"categoria_nome\":\"Cortinas"
                                + " Novas\",\"embalagem_nova\":{\"nome\":\"Caixa sob"
                                + " medida\",\"largura_cm\":\"30\","
                                + "\"altura_cm\":\"10\",\"comprimento_cm\":\"40\"},"
                                + "\"tags\":[\"azul\",\"azul\",\"sala\"],"
                                + "\"atributos\":[{\"nome\":\"Material\",\"valor\":\"Linho\"}]}");
        var p = linha(empresaA, "select * from radar_produto where id=?", id);
        assertEquals("63031200", p.get("ncm"));
        assertEquals("2803800", p.get("cest"));
        assertEquals(7, p.get("fisico"));
        assertEquals(0, new BigDecimal("1.250").compareTo((BigDecimal) p.get("peso_bruto_kg")));
        assertEquals("[\"azul\", \"sala\"]", p.get("tags").toString());
        assertEquals(
                "Cortinas Novas",
                linha(
                                empresaA,
                                "select nome from radar_categoria where id=?",
                                p.get("categoria_id"))
                        .get("nome"));
        assertEquals(
                "Caixa sob medida",
                linha(
                                empresaA,
                                "select nome from radar_embalagem where id=?",
                                p.get("embalagem_id"))
                        .get("nome"));
    }

    @Test
    void atualizarNaoMexeNoEstoque() {
        UUID id = salvar(empresaA, "{" + base("ESTOQUE-1") + ",\"saldo\":\"5\"}");
        salvar(empresaA, "{\"id\":\"" + id + "\"," + base("ESTOQUE-1") + ",\"saldo\":\"99\"}");
        assertEquals(
                5,
                linha(empresaA, "select fisico from radar_produto where id=?", id).get("fisico"));
    }

    @Test
    void variacoesViramProdutosFilhosComEstoqueProprio() {
        UUID pai =
                salvar(
                        empresaA,
                        "{"
                                + base("CAMISA", "VARIACAO")
                                + ",\"tipos_variacao\":[\"Cor\",\"Tamanho\"],\"variacoes\":["
                                + "{\"sku\":\"CAMISA-AZ-M\",\"atributos\":{\"Cor\":\"Azul\",\"Tamanho\":\"M\"},\"saldo\":\"3\"},"
                                + "{\"sku\":\"CAMISA-AZ-G\",\"atributos\":{\"Cor\":\"Azul\",\"Tamanho\":\"G\"},\"preco\":\"55.00\"}]}");
        var filhas =
                naEmpresa(
                        empresaA,
                        () ->
                                db.queryForList(
                                        "select * from radar_produto where pai_id=? order by sku",
                                        pai));
        assertEquals(2, filhas.size());
        assertEquals("Produto CAMISA - Azul / G", filhas.get(0).get("nome"));
        assertEquals(0, new BigDecimal("55.00").compareTo((BigDecimal) filhas.get(0).get("preco")));
        assertEquals(3, filhas.get(1).get("fisico"));
        assertEquals(
                0,
                linha(empresaA, "select fisico from radar_produto where id=?", pai).get("fisico"));

        // Tirar uma variação da grade não apaga: deixa fora de venda.
        String id = filhas.get(1).get("id").toString();
        salvar(
                empresaA,
                "{\"id\":\""
                        + pai
                        + "\","
                        + base("CAMISA", "VARIACAO")
                        + ",\"tipos_variacao\":[\"Cor\",\"Tamanho\"],\"variacoes\":["
                        + "{\"id\":\""
                        + id
                        + "\",\"sku\":\"CAMISA-AZ-M\",\"atributos\":{\"Cor\":\"Azul\",\"Tamanho\":\"M\"}}]}");
        assertEquals(
                false,
                linha(
                                empresaA,
                                "select permite_venda from radar_produto where id=?",
                                filhas.get(0).get("id"))
                        .get("permite_venda"));
    }

    @Test
    void gradeRecusaCombinacaoRepetidaEMaisDeTresTipos() {
        assertThrows(
                ResponseStatusException.class,
                () ->
                        salvar(
                                empresaA,
                                "{"
                                        + base("REP", "VARIACAO")
                                        + ",\"tipos_variacao\":[\"Cor\"],\"variacoes\":["
                                        + "{\"sku\":\"REP-1\",\"atributos\":{\"Cor\":\"Azul\"}},"
                                        + "{\"sku\":\"REP-2\",\"atributos\":{\"Cor\":\"azul\"}}]}"));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        salvar(
                                empresaA,
                                "{"
                                        + base("QUATRO", "VARIACAO")
                                        + ",\"tipos_variacao\":[\"A\",\"B\",\"C\",\"D\"],\"variacoes\":[]}"));
    }

    @Test
    void kitSomaOCustoDosComponentesENaoTemEstoqueProprio() {
        UUID a = salvar(empresaA, "{" + base("KIT-A") + ",\"custo\":\"10.50\"}");
        UUID b = salvar(empresaA, "{" + base("KIT-B") + ",\"custo\":\"4.25\"}");
        UUID kit =
                salvar(
                        empresaA,
                        "{"
                                + base("KIT-1", "KIT")
                                + ",\"saldo\":\"10\",\"kit\":[{\"componente_id\":\""
                                + a
                                + "\",\"quantidade\":\"2\"},{\"componente_id\":\""
                                + b
                                + "\",\"quantidade\":\"1\"}]}");
        var p = linha(empresaA, "select custo, fisico from radar_produto where id=?", kit);
        assertEquals(new BigDecimal("25.25"), p.get("custo"));
        assertEquals(0, p.get("fisico"));
        // Kit dentro de kit é recusado.
        assertThrows(
                ResponseStatusException.class,
                () ->
                        salvar(
                                empresaA,
                                "{"
                                        + base("KIT-2", "KIT")
                                        + ",\"kit\":[{\"componente_id\":\""
                                        + kit
                                        + "\",\"quantidade\":\"1\"}]}"));
    }

    @Test
    void categoriaDeOutraEmpresaNaoPodeSerVinculada() {
        UUID categoriaB =
                naEmpresa(
                        empresaB,
                        () -> {
                            UUID id = UUID.randomUUID();
                            db.update(
                                    "insert into radar_categoria(id,tenant_id,nome) values(?,?,?)",
                                    id,
                                    empresaB,
                                    "Só da B");
                            return id;
                        });
        assertThrows(
                ResponseStatusException.class,
                () ->
                        salvar(
                                empresaA,
                                "{" + base("CAT-B") + ",\"categoria_id\":\"" + categoriaB + "\"}"));
    }

    @Test
    void produtoEImagemDeOutraEmpresaNaoAparecem() {
        UUID produtoA = salvar(empresaA, "{" + base("IMG-1") + "}");
        UUID imagem =
                naEmpresa(
                        empresaA,
                        () ->
                                produtos.adicionarImagem(
                                        "DONO", produtoA, new byte[] {1, 2, 3}, "image/png"));
        assertNotNull(naEmpresa(empresaA, () -> produtos.imagem(imagem)));

        var erroImagem =
                assertThrows(
                        ResponseStatusException.class,
                        () -> naEmpresa(empresaB, () -> produtos.imagem(imagem)));
        assertEquals(HttpStatus.NOT_FOUND, erroImagem.getStatusCode());
        var erroProduto =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                salvar(
                                        empresaB,
                                        "{\"id\":\"" + produtoA + "\"," + base("IMG-1") + "}"));
        assertEquals(HttpStatus.NOT_FOUND, erroProduto.getStatusCode());
    }

    @Test
    void imagemComFormatoOuTamanhoInvalidoERecusada() {
        UUID id = salvar(empresaA, "{" + base("IMG-2") + "}");
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresaA,
                                () ->
                                        produtos.adicionarImagem(
                                                "DONO", id, new byte[] {1}, "image/gif")));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresaA,
                                () ->
                                        produtos.adicionarImagem(
                                                "DONO",
                                                id,
                                                new byte[RadarProdutos.MAX_BYTES_IMAGEM + 1],
                                                "image/png")));
    }

    @Test
    void cargoSemPermissaoNaoSalvaProduto() {
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresaA,
                                        () ->
                                                produtos.salvar(
                                                        json("{" + base("X") + "}"), "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
    }

    // ---- apoio -----------------------------------------------------------------------------

    @Test
    void loteReajustaPrecoEmPercentualComArredondamentoEAlcancaVariacoes() {
        UUID simples = salvar(empresaA, "{" + base("LOTE-P1") + "}");
        naEmpresa(
                empresaA,
                () ->
                        produtos.lote(
                                json(
                                        "{\"acao\":\"EDITAR\",\"campo\":\"preco\",\"modo\":"
                                                + "\"AUMENTAR_PCT\",\"valor\":\"10\",\"ids\":[\""
                                                + simples
                                                + "\"]}"),
                                "DONO"));
        // 49,90 + 10% = 54,89 (54,890).
        assertEquals(
                0,
                new BigDecimal("54.89")
                        .compareTo(
                                (BigDecimal)
                                        linha(
                                                        empresaA,
                                                        "select preco from radar_produto where"
                                                                + " id=?",
                                                        simples)
                                                .get("preco")));
        var invalido =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresaA,
                                        () ->
                                                produtos.lote(
                                                        json(
                                                                "{\"acao\":\"EDITAR\",\"campo\":\"preco\","
                                                                    + "\"modo\":\"SUBTRAIR\",\"valor\":\"999\","
                                                                    + "\"ids\":[\""
                                                                        + simples
                                                                        + "\"]}"),
                                                        "DONO")));
        assertTrue(invalido.getReason().contains("valor inválido"));
    }

    @Test
    void loteDefineCampoTagsEInativa() {
        UUID a = salvar(empresaA, "{" + base("LOTE-T1") + ",\"tags\":[\"azul\"]}");
        String ids = "\"ids\":[\"" + a + "\"]";
        lote("{\"acao\":\"EDITAR\",\"campo\":\"marca\",\"valor\":\"Nova Marca\"," + ids + "}");
        lote("{\"acao\":\"TAGS\",\"modo\":\"ADICIONAR\",\"tags\":[\"sala\",\"azul\"]," + ids + "}");
        lote("{\"acao\":\"TAGS\",\"modo\":\"REMOVER\",\"tags\":[\"azul\"]," + ids + "}");
        lote("{\"acao\":\"INATIVAR\"," + ids + "}");
        var p =
                linha(
                        empresaA,
                        "select marca, tags::text t, permite_venda from radar_produto where id=?",
                        a);
        assertEquals("Nova Marca", p.get("marca"));
        assertEquals("[\"sala\"]", p.get("t"));
        assertEquals(false, p.get("permite_venda"));
        assertThrows(
                ResponseStatusException.class,
                () -> lote("{\"acao\":\"EDITAR\",\"campo\":\"ncm\",\"valor\":\"\"," + ids + "}"));
        assertThrows(
                ResponseStatusException.class,
                () -> lote("{\"acao\":\"EDITAR\",\"campo\":\"sku\",\"valor\":\"X\"," + ids + "}"));
    }

    @Test
    void preencherCompletaUmProdutoPorVez() {
        UUID a = salvar(empresaA, "{" + base("PREENCHE-1") + "}");
        UUID b = salvar(empresaA, "{" + base("PREENCHE-2") + "}");
        String um = "\"ids\":[\"" + a + "\"]";
        lote("{\"acao\":\"PREENCHER\",\"campo\":\"gtin\",\"valor\":\"4006381333931\"," + um + "}");
        lote("{\"acao\":\"PREENCHER\",\"campo\":\"ncm\",\"valor\":\"94049000\"," + um + "}");
        var p =
                linha(
                        empresaA,
                        "select gtin, motivo_sem_gtin, ncm from radar_produto where id=?",
                        a);
        assertEquals("4006381333931", p.get("gtin"));
        assertEquals(null, p.get("motivo_sem_gtin"));
        assertEquals("94049000", p.get("ncm"));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        lote(
                                "{\"acao\":\"PREENCHER\",\"campo\":\"gtin\",\"valor\":\"123\","
                                        + um
                                        + "}"));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        lote(
                                "{\"acao\":\"PREENCHER\",\"campo\":\"descricao\",\"valor\":\"x\",\"ids\":[\""
                                        + a
                                        + "\",\""
                                        + b
                                        + "\"]}"));
    }

    @Test
    void semDadosDeNotaEAnuncioSalvaComoRascunhoEListaOQueFalta() {
        var r =
                naEmpresa(
                        empresaA,
                        () ->
                                produtos.salvar(
                                        json(
                                                "{\"tipo\":\"SIMPLES\",\"sku\":\"FALTA-1\",\"nome\":\"X\"}"),
                                        "DONO"));
        String msg = (String) r.get("mensagem");
        assertTrue(msg.contains("rascunho"), msg);
        for (String campo :
                List.of(
                        "Origem",
                        "NCM",
                        "Código de barras",
                        "Preço",
                        "Marca",
                        "Categoria",
                        "Descrição",
                        "Peso bruto",
                        "Medidas")) assertTrue(msg.contains(campo), campo);
        UUID id = (UUID) r.get("id");
        assertEquals(true, linha(empresaA, "select incompleto from radar_produto where id=?", id).get("incompleto"));
        // Completo, deixa de ser rascunho.
        salvar(empresaA, "{\"id\":\"" + id + "\"," + base("FALTA-1") + "}");
        assertEquals(false, linha(empresaA, "select incompleto from radar_produto where id=?", id).get("incompleto"));
        // Sem nome não salva.
        assertThrows(
                ResponseStatusException.class,
                () -> salvar(empresaA, "{\"tipo\":\"SIMPLES\",\"sku\":\"SEM-NOME\"}"));
    }

    @Test
    void embalagemValeNoLugarDasMedidas() {
        String semMedidas =
                base("EMB-1")
                        .replace(
                                ",\"largura_cm\":\"10\",\"altura_cm\":\"10\",\"comprimento_cm\":\"10\"",
                                "");
        UUID rascunho = salvar(empresaA, "{" + semMedidas + "}");
        assertEquals(true, linha(empresaA, "select incompleto from radar_produto where id=?", rascunho).get("incompleto"));
        UUID id =
                salvar(
                        empresaA,
                        "{"
                                + semMedidas.replace("EMB-1", "EMB-2")
                                + ",\"embalagem_nova\":{\"nome\":\"Caixa"
                                + " EMB-2\",\"largura_cm\":\"20\","
                                + "\"altura_cm\":\"10\",\"comprimento_cm\":\"30\"}}");
        assertEquals(false, linha(empresaA, "select incompleto from radar_produto where id=?", id).get("incompleto"));
    }

    @Test
    void skuAutomaticoSegueAConfiguracao() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        String semSku = "{\"tipo\":\"SIMPLES\",\"nome\":\"Sem SKU\"}";
        // Manual (padrão): SKU obrigatório.
        assertThrows(ResponseStatusException.class, () -> salvar(empresa, semSku));
        var cfg = new RadarConfiguracao(db, BancoRadarDeTeste.JSON);
        naEmpresa(
                empresa,
                () ->
                        cfg.salvar(
                                json(
                                        "{\"chave\":\"produtos\",\"valor\":{\"sku_modo\":\"PREFIXO\","
                                                + "\"sku_prefixo\":\"cam-\",\"sku_digitos\":4}}"),
                                "GESTOR"));
        UUID a = salvar(empresa, semSku);
        UUID b = salvar(empresa, semSku);
        assertEquals("CAM-0001", linha(empresa, "select sku from radar_produto where id=?", a).get("sku"));
        assertEquals("CAM-0002", linha(empresa, "select sku from radar_produto where id=?", b).get("sku"));
        // Outra empresa não enxerga a configuração.
        assertThrows(ResponseStatusException.class, () -> salvar(empresaB, semSku));
        for (String ruim :
                List.of(
                        "{\"sku_modo\":\"PREFIXO\",\"sku_prefixo\":\"\"}",
                        "{\"sku_modo\":\"OUTRO\"}",
                        "{\"sku_prefixo\":\"A B\"}",
                        "{\"unidade_padrao\":\"XX\"}",
                        "{\"ncm_padrao\":\"123\"}"))
            assertThrows(
                    ResponseStatusException.class,
                    () ->
                            naEmpresa(
                                    empresa,
                                    () ->
                                            cfg.salvar(
                                                    json("{\"chave\":\"produtos\",\"valor\":" + ruim + "}"),
                                                    "DONO")),
                    ruim);
        var proibido =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                cfg.salvar(
                                                        json("{\"chave\":\"produtos\",\"valor\":{}}"),
                                                        "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, proibido.getStatusCode());
    }

    @Test
    void variacaoHerdaOPromocionalDoPrincipal() {
        UUID pai =
                salvar(
                        empresaA,
                        "{"
                                + base("PROMO-V", "VARIACAO")
                                + ",\"preco_promocional\":\"39.90\",\"tipos_variacao\":[\"Cor\"],"
                                + "\"variacoes\":[{\"atributos\":{\"Cor\":\"Azul\"}}]}");
        var v =
                linha(
                        empresaA,
                        "select sku, preco_promocional from radar_produto where pai_id=?",
                        pai);
        assertEquals(0, new BigDecimal("39.90").compareTo((BigDecimal) v.get("preco_promocional")));
        // Sem SKU na grade, o Radar gera a partir do SKU do principal.
        assertEquals("PROMO-V-AZUL", v.get("sku"));
    }

    @Test
    void excluirMandaParaALixeiraERestaurarDevolveInativo() {
        UUID novo = salvar(empresaA, "{" + base("LIX-1") + "}");
        UUID comEstoque = salvar(empresaA, "{" + base("LIX-2") + ",\"saldo\":\"3\"}");
        lote("{\"acao\":\"EXCLUIR\",\"ids\":[\"" + novo + "\",\"" + comEstoque + "\"]}");
        var p = linha(empresaA, "select excluido_em, permite_venda from radar_produto where id=?", comEstoque);
        assertNotNull(p.get("excluido_em"));
        assertEquals(false, p.get("permite_venda"));
        // Na lixeira não se edita, não se clona nem mexe em lote comum.
        assertThrows(
                ResponseStatusException.class,
                () -> salvar(empresaA, "{\"id\":\"" + comEstoque + "\"," + base("LIX-2") + "}"));
        assertThrows(
                ResponseStatusException.class,
                () -> lote("{\"acao\":\"INATIVAR\",\"ids\":[\"" + comEstoque + "\"]}"));
        // Apagar de vez: só o que não tem histórico.
        var preso =
                assertThrows(
                        ResponseStatusException.class,
                        () -> lote("{\"acao\":\"EXCLUIR_DEFINITIVO\",\"ids\":[\"" + comEstoque + "\"]}"));
        assertTrue(preso.getReason().contains("LIX-2"));
        lote("{\"acao\":\"EXCLUIR_DEFINITIVO\",\"ids\":[\"" + novo + "\"]}");
        assertEquals(
                0L,
                naEmpresa(
                        empresaA,
                        () -> db.queryForObject("select count(*) from radar_produto where id=?", Long.class, novo)));
        // Outra empresa não alcança.
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresaB,
                                () ->
                                        produtos.lote(
                                                json("{\"acao\":\"RESTAURAR\",\"ids\":[\"" + comEstoque + "\"]}"),
                                                "DONO")));
        lote("{\"acao\":\"RESTAURAR\",\"ids\":[\"" + comEstoque + "\"]}");
        p = linha(empresaA, "select excluido_em, permite_venda from radar_produto where id=?", comEstoque);
        assertEquals(null, p.get("excluido_em"));
        assertEquals(false, p.get("permite_venda"));
    }

    @Test
    void naoExcluiComponenteDeKitAtivo() {
        UUID componente = salvar(empresaA, "{" + base("LIX-COMP") + "}");
        UUID kit =
                salvar(
                        empresaA,
                        "{"
                                + base("LIX-KIT", "KIT")
                                + ",\"kit\":[{\"componente_id\":\""
                                + componente
                                + "\",\"quantidade\":2}]}");
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () -> lote("{\"acao\":\"EXCLUIR\",\"ids\":[\"" + componente + "\"]}"));
        assertTrue(erro.getReason().contains("kit"));
        // Junto com o kit, pode.
        lote("{\"acao\":\"EXCLUIR\",\"ids\":[\"" + componente + "\",\"" + kit + "\"]}");
    }

    @Test
    void clonarCopiaVariacoesKitEFornecedoresComEstoqueZero() {
        UUID pai =
                salvar(
                        empresaA,
                        "{"
                                + base("CLONE", "VARIACAO")
                                + ",\"tipos_variacao\":[\"Cor\"],\"variacoes\":["
                                + "{\"sku\":\"CLONE-AZ\",\"atributos\":{\"Cor\":\"Azul\"},\"saldo\":\"5\"},"
                                + "{\"sku\":\"CLONE-VD\",\"atributos\":{\"Cor\":\"Verde\"}}]}");
        var r =
                naEmpresa(
                        empresaA,
                        () -> produtos.clonar(json("{\"id\":\"" + pai + "\"}"), "GESTOR"));
        UUID copia = (UUID) r.get("id");
        assertEquals("CLONE-COPIA", r.get("sku"));
        var filhas =
                naEmpresa(
                        empresaA,
                        () ->
                                db.queryForList(
                                        "select sku, fisico, nome from radar_produto where pai_id=? order by sku",
                                        copia));
        assertEquals(2, filhas.size());
        assertEquals("CLONE-COPIA-AZ", filhas.get(0).get("sku"));
        assertEquals(0, ((Number) filhas.get(0).get("fisico")).intValue());
        assertTrue(filhas.get(0).get("nome").toString().startsWith("Produto CLONE (cópia)"));
        // Clonar de novo não repete SKU.
        var r2 = naEmpresa(empresaA, () -> produtos.clonar(json("{\"id\":\"" + pai + "\"}"), "DONO"));
        assertEquals("CLONE-COPIA-2", r2.get("sku"));
        // Outra empresa não clona.
        assertThrows(
                ResponseStatusException.class,
                () -> naEmpresa(empresaB, () -> produtos.clonar(json("{\"id\":\"" + pai + "\"}"), "DONO")));
        var proibido =
                assertThrows(
                        ResponseStatusException.class,
                        () -> naEmpresa(empresaA, () -> produtos.clonar(json("{\"id\":\"" + pai + "\"}"), "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, proibido.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void historicoMostraEstoqueEEscondeDeOutraEmpresa() {
        UUID id = salvar(empresaA, "{" + base("HIST-1") + ",\"saldo\":\"4\"}");
        var h = naEmpresa(empresaA, () -> produtos.historico(id, "DONO", false));
        var eventos = (List<Map<String, Object>>) h.get("eventos");
        assertTrue(
                eventos.stream().anyMatch(e -> e.get("titulo").toString().contains("Saldo inicial")),
                eventos.toString());
        // Quem não gerencia não vê quem alterou o cadastro.
        var deAnalista =
                (List<Map<String, Object>>)
                        naEmpresa(empresaA, () -> produtos.historico(id, "ANALISTA", false))
                                .get("eventos");
        assertTrue(deAnalista.stream().noneMatch(e -> "CADASTRO".equals(e.get("tipo"))));
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () -> naEmpresa(empresaB, () -> produtos.historico(id, "DONO", true)));
        // Outra empresa também não manda para a lixeira nem apaga.
        for (String acao : List.of("EXCLUIR", "EXCLUIR_DEFINITIVO"))
            assertThrows(
                    ResponseStatusException.class,
                    () ->
                            naEmpresa(
                                    empresaB,
                                    () ->
                                            produtos.lote(
                                                    json("{\"acao\":\"" + acao + "\",\"ids\":[\"" + id + "\"]}"),
                                                    "DONO")),
                    acao);
        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
    }

    private static void lote(String corpo) {
        naEmpresa(empresaA, () -> produtos.lote(json(corpo), "DONO"));
    }

    @Test
    void skuAutomaticoIgnoraCodigoDeBarrasEPulaOQueJaExiste() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_configuracao(tenant_id,chave,valor) values(?,'produtos',"
                        + "'{\"sku_modo\":\"SEQUENCIAL\",\"sku_digitos\":5}'::jsonb)",
                empresa);
        // Código de barras digitado no campo SKU e um SKU comprido não puxam a sequência.
        salvar(empresa, "{\"tipo\":\"SIMPLES\",\"nome\":\"Barras\",\"sku\":\"7891234567895\"}");
        salvar(empresa, "{\"tipo\":\"SIMPLES\",\"nome\":\"Longo\",\"sku\":\"999999999999999999\"}");
        // O próximo da sequência já existe (digitado à mão): pula para o seguinte livre.
        salvar(empresa, "{\"tipo\":\"SIMPLES\",\"nome\":\"Manual\",\"sku\":\"00002\"}");
        assertEquals("00003", sku(empresa, salvar(empresa, "{\"tipo\":\"SIMPLES\",\"nome\":\"A\"}")));
        assertEquals("00004", sku(empresa, salvar(empresa, "{\"tipo\":\"SIMPLES\",\"nome\":\"B\"}")));
    }

    @Test
    void valoresPadraoValemForaDaTelaEPrecoAceitaVirgula() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        BancoRadarDeTeste.executarComoDono(
                "insert into radar_configuracao(tenant_id,chave,valor) values(?,'produtos',"
                        + "'{\"sku_modo\":\"MANUAL\",\"unidade_padrao\":\"KG\","
                        + "\"ncm_padrao\":\"63039100\",\"origem_padrao\":\"1\"}'::jsonb)",
                empresa);
        UUID id =
                salvar(
                        empresa,
                        "{\"tipo\":\"SIMPLES\",\"nome\":\"Padrao\",\"sku\":\"PAD-1\","
                                + "\"preco\":\"59,90\"}");
        var p = linha(empresa, "select unidade, ncm, origem, preco from radar_produto where id=?", id);
        assertEquals("KG", p.get("unidade"));
        assertEquals("63039100", p.get("ncm"));
        assertEquals(1, ((Number) p.get("origem")).intValue());
        assertEquals(0, new BigDecimal("59.90").compareTo((BigDecimal) p.get("preco")));
        // Preço em formato errado (lista) é recusado, não vira zero.
        assertThrows(
                ResponseStatusException.class,
                () ->
                        salvar(
                                empresa,
                                "{\"tipo\":\"SIMPLES\",\"nome\":\"Q\",\"sku\":\"PAD-2\","
                                        + "\"preco\":[1,2]}"));
    }

    private static String sku(UUID empresa, UUID id) {
        return (String) linha(empresa, "select sku from radar_produto where id=?", id).get("sku");
    }

    private static String base(String sku) {
        return base(sku, "SIMPLES");
    }

    private static String base(String sku, String tipo) {
        return "\"tipo\":\""
                + tipo
                + "\",\"sku\":\""
                + sku
                + "\",\"nome\":\"Produto "
                + sku
                + "\",\"preco\":\"49.90\",\"custo\":\"20.00\""
                // Obrigatórios para nota e anúncio.
                + ",\"origem\":\"0\",\"ncm\":\"63031200\",\"motivo_sem_gtin\":\"SEM_CODIGO_DO_FABRICANTE\",\"marca\":\"Casa"
                + " Clara\",\"categoria_nome\":\"Testes\",\"descricao\":\"Produto de"
                + " teste.\",\"peso_bruto_kg\":\"1\","
                + "\"largura_cm\":\"10\",\"altura_cm\":\"10\",\"comprimento_cm\":\"10\"";
    }

    private static UUID salvar(UUID empresa, String corpo) {
        return (UUID) naEmpresa(empresa, () -> produtos.salvar(json(corpo), "DONO")).get("id");
    }

    private static Map<String, Object> linha(UUID empresa, String sql, Object id) {
        return naEmpresa(empresa, () -> db.queryForMap(sql, id));
    }
}
