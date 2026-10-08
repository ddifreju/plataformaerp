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

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dados de exemplo: só na empresa de demonstração, uma vez, e coerentes de ponta a ponta. */
class RadarExemploTest {

    private static JdbcTemplate db;
    private static RadarService radar;
    private static RadarExemplo exemplo;

    @BeforeAll
    static void preparar() {
        db = BancoRadarDeTeste.comoAplicacao();
        var json = BancoRadarDeTeste.JSON;
        exemplo = new RadarExemplo(db, json, new RadarClientes(db, json));
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
                        new RadarVendedores(db, json, new BCryptPasswordEncoder(12)),
                        new RadarEmpresa(db, new BCryptPasswordEncoder(12)),
                        new RadarConfiguracao(db, json),
                        exemplo);
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void carregaTudoNaEmpresaDeDemonstracaoUmaVezSo() throws SQLException {
        UUID empresa = empresaDeDemonstracao();
        logar(empresa, PapelUsuario.DONO);
        // Passo fora de ordem não roda (não duplica nem pula nada).
        var pulo = assertThrows(ResponseStatusException.class, () -> etapa(empresa, 2));
        assertTrue(pulo.getReason().contains("próximo passo"));
        int total = exemplo.etapas();
        for (int etapa = 1; etapa <= total; etapa++) etapa(empresa, etapa);
        assertEquals(
                total, ((Number) naEmpresa(empresa, () -> exemplo.estado()).get("feitas")).intValue());

        assertEquals(5L, contar(empresa, "select count(*) from radar_loja"));
        assertTrue(contar(empresa, "select count(*) from radar_produto where sku like 'EX-%'") >= 16);
        assertEquals(60L, contar(empresa, "select count(*) from radar_pedido"));
        assertTrue(contar(empresa, "select count(*) from radar_anuncio where estado='PRONTO'") > 20);
        assertTrue(contar(empresa, "select count(*) from radar_titulo where tipo='RECEBER'") > 20);
        assertTrue(contar(empresa, "select count(*) from radar_titulo where tipo='PAGAR'") >= 5);
        assertEquals(3L, contar(empresa, "select count(*) from radar_promocao"));
        assertEquals(
                6L, contar(empresa, "select count(*) from radar_registro where tipo='MENSAGEM'"));
        assertTrue(contar(empresa, "select count(*) from radar_pedido where estado='EXPEDIDO'") > 40);
        assertTrue(contar(empresa, "select count(*) from radar_pedido where estado='CANCELADO'") > 0);
        // Pedidos espalhados pelos últimos 90 dias.
        assertTrue(
                contar(
                                empresa,
                                "select count(*) from radar_pedido where criado_em < now() -"
                                        + " interval '80 days'")
                        > 0);
        // Estoque nunca fica negativo nem com reserva maior que o físico.
        assertEquals(
                0L,
                contar(
                        empresa,
                        "select count(*) from radar_produto where fisico < 0 or reservado < 0 or"
                                + " reservado > fisico"));
        assertEquals(
                0L, contar(empresa, "select count(*) from radar_loja where conectada_em is not null"));

        var deNovo = assertThrows(ResponseStatusException.class, () -> etapa(empresa, 1));
        assertTrue(deNovo.getReason().contains("já foram carregados"));
    }

    @Test
    void empresaDeClienteNaoRecebeDadosInventados() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        logar(empresa, PapelUsuario.DONO);
        var erro = assertThrows(ResponseStatusException.class, () -> etapa(empresa, 1));
        assertTrue(erro.getReason().contains("empresa de demonstração"));
        assertEquals(0L, contar(empresa, "select count(*) from radar_produto"));
    }

    @Test
    void cadaEmpresaDeDemonstracaoFicaComOsSeusDados() throws SQLException {
        UUID a = empresaDeDemonstracao();
        UUID b = empresaDeDemonstracao();
        for (UUID empresa : List.of(a, b)) {
            logar(empresa, PapelUsuario.DONO);
            for (int etapa = 1; etapa <= exemplo.etapas(); etapa++) etapa(empresa, etapa);
        }
        for (UUID empresa : List.of(a, b)) {
            assertEquals(60L, contar(empresa, "select count(*) from radar_pedido"));
            assertEquals(5L, contar(empresa, "select count(*) from radar_loja"));
            // Nenhum anúncio aponta para loja ou produto de outra empresa.
            assertEquals(
                    0L,
                    contar(
                            empresa,
                            "select count(*) from radar_anuncio a where not exists (select 1 from"
                                    + " radar_loja l where l.id=a.loja_id and"
                                    + " l.tenant_id=a.tenant_id) or not exists (select 1 from"
                                    + " radar_produto p where p.id=a.produto_id and"
                                    + " p.tenant_id=a.tenant_id)"));
        }
    }

    @Test
    void slugParecidoComDemonstracaoNaoServe() throws SQLException {
        UUID id = UUID.randomUUID();
        BancoRadarDeTeste.executarComoDono(
                "INSERT INTO tenant (id, nome, slug) VALUES (?, ?, ?)",
                id,
                "Loja Demo " + id,
                "loja-demo-" + id.toString().substring(0, 8));
        logar(id, PapelUsuario.DONO);
        assertThrows(ResponseStatusException.class, () -> etapa(id, 1));
    }

    @Test
    void demonstracaoQueJaTemVendaNaoRecebe() throws SQLException {
        UUID empresa = empresaDeDemonstracao();
        UUID produto = BancoRadarDeTeste.novoProduto(empresa, "REAL-1", "10.00", "50.00");
        BancoRadarDeTeste.novoPedido(empresa, produto, "Mercado Livre", "Ana", "50.00", "10.00");
        logar(empresa, PapelUsuario.DONO);
        var erro = assertThrows(ResponseStatusException.class, () -> etapa(empresa, 1));
        assertTrue(erro.getReason().contains("já tem pedidos"));
    }

    @Test
    void soODonoCarrega() throws SQLException {
        UUID empresa = empresaDeDemonstracao();
        logar(empresa, PapelUsuario.GESTOR);
        var erro = assertThrows(ResponseStatusException.class, () -> etapa(empresa, 1));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
    }

    private static Map<String, Object> etapa(UUID empresa, int etapa) {
        return naEmpresa(
                empresa,
                () ->
                        radar.comando(
                                UUID.randomUUID(),
                                json("{\"op\":\"dados_exemplo\",\"etapa\":" + etapa + "}")));
    }

    private static long contar(UUID empresa, String sql) {
        return naEmpresa(empresa, () -> db.queryForObject(sql, Long.class));
    }

    private static UUID empresaDeDemonstracao() throws SQLException {
        UUID id = UUID.randomUUID();
        BancoRadarDeTeste.executarComoDono(
                "INSERT INTO tenant (id, nome, slug) VALUES (?, ?, ?)",
                id,
                "Demonstração " + id,
                "demo-teste-" + id.toString().substring(0, 8));
        return id;
    }

    private static void logar(UUID empresa, PapelUsuario papel) throws SQLException {
        UUID id = UUID.randomUUID();
        BancoRadarDeTeste.executarComoDono(
                "insert into usuario(id,tenant_id,email,senha_hash,nome,papel)"
                        + " values(?,?,?,?,'Teste',?)",
                id,
                empresa,
                "exemplo" + id.toString().substring(0, 8) + "@radar.test",
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
