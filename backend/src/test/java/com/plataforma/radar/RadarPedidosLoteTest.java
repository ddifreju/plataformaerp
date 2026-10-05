package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.json;
import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.plataforma.autenticacao.PapelUsuario;
import com.plataforma.autenticacao.UsuarioAutenticado;
import com.plataforma.autenticacao.UsuarioParaLogin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ações em lote dos pedidos: situação, cancelamento, marcadores, contas e isolamento. */
class RadarPedidosLoteTest {

    private static JdbcTemplate db;
    private static RadarService radar;

    @BeforeAll
    static void preparar() {
        db = BancoRadarDeTeste.comoAplicacao();
        var json = BancoRadarDeTeste.JSON;
        radar =
                new RadarService(
                        db,
                        json,
                        new RadarCadastros(db, json),
                        new RadarPromocoes(db),
                        new RadarRelatorios(db),
                        new RadarProdutos(db, json),
                        new RadarClientes(db, json),
                        new RadarAnuncios(db),
                        new RadarVendedores(db, json, new BCryptPasswordEncoder(12)));
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void separarExpedirECancelarEmLotePulamQuemNaoCabe() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        logar(empresa, PapelUsuario.DONO);
        UUID produto = BancoRadarDeTeste.novoProduto(empresa, "LOTE-1", "10.00", "50.00");
        UUID a = pedido(empresa, produto, 2);
        UUID b = pedido(empresa, produto, 3);
        assertEquals(5, reservado(empresa, produto));

        lote(empresa, "{\"acao\":\"ESTADO\",\"estado\":\"SEPARADO\",\"ids\":[\"" + a + "\"]}");
        var r =
                lote(
                        empresa,
                        "{\"acao\":\"ESTADO\",\"estado\":\"EXPEDIDO\",\"confirmar_simulacao\":true,"
                                + "\"ids\":[\""
                                + a
                                + "\",\""
                                + b
                                + "\"]}");
        // Só o separado é expedido; o reservado fica e aparece como pulado.
        assertEquals(1, r.get("alterados"));
        assertEquals(1, ((List<?>) r.get("pulados")).size());
        assertEquals("EXPEDIDO", estado(empresa, a));
        assertEquals("RESERVADO", estado(empresa, b));

        // "Excluir" cancela: devolve a reserva e não apaga o pedido.
        lote(empresa, "{\"acao\":\"EXCLUIR\",\"ids\":[\"" + a + "\",\"" + b + "\"]}");
        assertEquals("CANCELADO", estado(empresa, b));
        assertEquals("EXPEDIDO", estado(empresa, a));
        assertEquals(0, reservado(empresa, produto));

        var semConfirmar =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                lote(
                                        empresa,
                                        "{\"acao\":\"ESTADO\",\"estado\":\"EXPEDIDO\",\"ids\":[\""
                                                + a
                                                + "\"]}"));
        assertTrue(semConfirmar.getReason().contains("confirmação"));
    }

    @Test
    void marcadoresDataEContasAReceber() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        logar(empresa, PapelUsuario.DONO);
        UUID produto = BancoRadarDeTeste.novoProduto(empresa, "LOTE-2", "10.00", "50.00");
        UUID a = pedido(empresa, produto, 2);
        String ids = "\"ids\":[\"" + a + "\"]";

        lote(
                empresa,
                "{\"acao\":\"MARCADORES\",\"modo\":\"ADICIONAR\",\"marcadores\":[\"Urgente\",\"presente\"],"
                        + ids
                        + "}");
        lote(
                empresa,
                "{\"acao\":\"MARCADORES\",\"modo\":\"REMOVER\",\"marcadores\":[\"presente\"],"
                        + ids
                        + "}");
        assertEquals(
                "[\"urgente\"]",
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select marcadores::text from radar_pedido where id=?",
                                        String.class,
                                        a)));

        lote(empresa, "{\"acao\":\"DATA_FATURAMENTO\",\"data\":\"2026-10-05\"," + ids + "}");
        assertEquals(
                "2026-10-05",
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select data_faturamento::text from radar_pedido where"
                                            + " id=?",
                                        String.class,
                                        a)));

        String contas = "{\"acao\":\"LANCAR_CONTAS\",\"vencimento\":\"2026-11-05\"," + ids + "}";
        lote(empresa, contas);
        var r = lote(empresa, contas);
        // A segunda vez não duplica.
        assertEquals(0, r.get("lancadas"));
        var titulo =
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForMap(
                                        "select tipo, valor from radar_titulo where pedido_id=?",
                                        a));
        assertEquals("RECEBER", titulo.get("tipo"));
        // 2 × 50,00 − desconto 5,00.
        assertEquals(new BigDecimal("95.00"), titulo.get("valor"));
    }

    @Test
    void outraEmpresaNaoAlcancaECargoSemPermissaoEBarrado() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        logar(empresa, PapelUsuario.DONO);
        UUID produto = BancoRadarDeTeste.novoProduto(empresa, "LOTE-3", "10.00", "50.00");
        UUID a = pedido(empresa, produto, 1);
        String corpo = "{\"acao\":\"EXCLUIR\",\"ids\":[\"" + a + "\"]}";

        logar(outra, PapelUsuario.DONO);
        var deFora = assertThrows(ResponseStatusException.class, () -> lote(outra, corpo));
        assertEquals(HttpStatus.NOT_FOUND, deFora.getStatusCode());

        logar(empresa, PapelUsuario.ESTOQUE);
        var estoque = assertThrows(ResponseStatusException.class, () -> lote(empresa, corpo));
        assertEquals(HttpStatus.FORBIDDEN, estoque.getStatusCode());
        assertEquals("RESERVADO", estado(empresa, a));
    }

    // ---- apoio -----------------------------------------------------------------------------

    private static UUID pedido(UUID empresa, UUID produto, int quantidade) {
        var r =
                naEmpresa(
                        empresa,
                        () ->
                                radar.comando(
                                        UUID.randomUUID(),
                                        json(
                                                "{\"op\":\"pedido\",\"produto_id\":\""
                                                        + produto
                                                        + "\",\"quantidade\":"
                                                        + quantidade
                                                        + ",\"preco\":\"50.00\",\"canal\":\"Shopee\",\"cliente\":\"Cliente"
                                                        + " Teste\",\"custo_unitario\":\"10.00\","
                                                        + "\"comissao\":\"0\",\"frete\":\"0\",\"imposto\":\"0\","
                                                        + "\"ads\":\"0\",\"embalagem\":\"0\",\"desconto\":\"5.00\"}")));
        return UUID.fromString(r.get("id").toString());
    }

    private static Map<String, Object> lote(UUID empresa, String corpo) {
        return naEmpresa(
                empresa,
                () ->
                        radar.comando(
                                UUID.randomUUID(),
                                json(corpo.replace("{", "{\"op\":\"pedidos_lote\","))));
    }

    private static String estado(UUID empresa, UUID pedido) {
        return naEmpresa(
                empresa,
                () ->
                        db.queryForObject(
                                "select estado from radar_pedido where id=?",
                                String.class,
                                pedido));
    }

    private static int reservado(UUID empresa, UUID produto) {
        return naEmpresa(
                empresa,
                () ->
                        db.queryForObject(
                                "select reservado from radar_produto where id=?",
                                Integer.class,
                                produto));
    }

    /** Usuário de verdade na empresa: o serviço lê o cargo do banco. */
    private static void logar(UUID empresa, PapelUsuario papel) throws SQLException {
        UUID id = UUID.randomUUID();
        BancoRadarDeTeste.executarComoDono(
                "insert into usuario(id,tenant_id,email,senha_hash,nome,papel)"
                        + " values(?,?,?,?,'Teste',?)",
                id,
                empresa,
                "lote" + id.toString().substring(0, 8) + "@radar.test",
                "$2a$12$" + "a".repeat(53),
                papel.name());
        var u =
                new UsuarioAutenticado(
                        new UsuarioParaLogin(
                                id, empresa, "teste@radar.test", "x", "Teste", papel, true, true));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }
}
