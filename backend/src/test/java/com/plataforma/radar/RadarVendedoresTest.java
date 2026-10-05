package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.json;
import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cadastro de vendedores (V025): obrigatórios, acesso, comissão, permissão e isolamento. */
@SuppressWarnings("unchecked")
class RadarVendedoresTest {

    private static JdbcTemplate db;
    private static RadarVendedores vendedores;
    private static RadarClientes clientes;

    @BeforeAll
    static void preparar() {
        db = BancoRadarDeTeste.comoAplicacao();
        vendedores = new RadarVendedores(db, BancoRadarDeTeste.JSON, new BCryptPasswordEncoder(12));
        clientes = new RadarClientes(db, BancoRadarDeTeste.JSON);
    }

    @Test
    void semNomeCpfOuEmailNaoSalva() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () -> salvar(empresa, "{\"tipo_pessoa\":\"F\"}"));
        for (String campo : List.of("Nome", "CPF", "E-mail"))
            assertTrue(erro.getReason().contains(campo), campo);
    }

    @Test
    void salvaCompletoComCodigoAutomaticoAcessoEComissao() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID id =
                salvar(
                        empresa,
                        completo("Rafa Vendas")
                                .replace(
                                        "}",
                                        ",\"acesso_horario_inicio\":\"08:00\","
                                                + "\"acesso_horario_fim\":\"18:00\","
                                                + "\"acesso_dias\":[\"SEG\",\"sex\"],"
                                                + "\"acesso_ips\":[\"200.150.10.0/24\"],"
                                                + "\"modulos\":[\"CLIENTES\",\"PEDIDOS\"],"
                                                + "\"comissao_regra\":\"DESCONTO\","
                                                + "\"comissao_aliquota\":\"4,5\"}"));
        var v = naEmpresa(empresa, () -> vendedores.detalhe("DONO", id));
        assertEquals("V00001", v.get("codigo"));
        assertEquals("52998224725", v.get("documento"));
        assertEquals("08:00", v.get("acesso_horario_inicio"));
        assertEquals(List.of("SEG", "SEX"), v.get("acesso_dias"));
        assertEquals(List.of("CLIENTES", "PEDIDOS"), v.get("modulos"));
        assertEquals("4.50", v.get("comissao_aliquota"));
        var lista =
                (List<Map<String, Object>>)
                        naEmpresa(empresa, () -> vendedores.dados("ATENDIMENTO")).get("vendedores");
        assertEquals("***.982.247-**", lista.getFirst().get("documento"));
        assertTrue(!lista.getFirst().containsKey("comissao_aliquota"));
    }

    @Test
    void validaHorarioIpEComissao() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        for (String extra :
                List.of(
                        ",\"acesso_horario_inicio\":\"08:00\"",
                        ",\"acesso_horario_inicio\":\"18:00\",\"acesso_horario_fim\":\"08:00\"",
                        ",\"acesso_ips\":[\"999.1.1.1\"]",
                        ",\"comissao_aliquota\":\"100.01\""))
            assertThrows(
                    ResponseStatusException.class,
                    () -> salvar(empresa, completo("X").replace("}", extra + "}")),
                    extra);
    }

    @Test
    void usuarioDoSistemaLigaAUmVendedorSo() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID usuario = UUID.randomUUID();
        BancoRadarDeTeste.executarComoDono(
                "insert into usuario(id,tenant_id,email,senha_hash,nome,papel)"
                        + " values(?,?,?,?,'Rafa','ATENDIMENTO')",
                usuario,
                empresa,
                "rafa" + usuario.toString().substring(0, 8) + "@radar.test",
                // Só o formato importa aqui (ck_usuario_senha_hash_formato); ninguém entra com ela.
                "$2a$12$" + "a".repeat(53));
        String comUsuario = ",\"usuario_id\":\"" + usuario + "\"}";
        salvar(empresa, completo("Primeiro").replace("}", comUsuario));
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                salvar(
                                        empresa,
                                        completo("Segundo")
                                                .replace("529.982.247-25", "390.533.447-05")
                                                .replace("}", comUsuario)));
        assertTrue(erro.getReason().contains("já está ligado"));
    }

    @Test
    void clienteGanhaVendedorPadraoDaMesmaEmpresa() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        UUID vendedor = salvar(empresa, completo("Rafa"));
        String cliente =
                "{\"nome\":\"Ana\",\"tipo_pessoa\":\"F\",\"documento\":\"39053344705\","
                        + "\"cep\":\"01310100\",\"endereco\":\"Av\",\"numero\":\"1\","
                        + "\"bairro\":\"B\",\"cidade\":\"SP\",\"uf\":\"SP\",\"vendedor_id\":\""
                        + vendedor
                        + "\"}";
        assertThrows(
                ResponseStatusException.class,
                () -> naEmpresa(outra, () -> clientes.salvar(json(cliente), "DONO")));
        UUID id = (UUID) naEmpresa(empresa, () -> clientes.salvar(json(cliente), "DONO")).get("id");
        assertEquals(
                vendedor,
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select vendedor_id from radar_cliente where id=?",
                                        UUID.class,
                                        id)));
    }

    @Test
    void soDonoEGestorCadastramEOutraEmpresaNaoVe() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                vendedores.salvar(
                                                        json(completo("X")), "ATENDIMENTO")));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
        UUID id = salvar(empresa, completo("Rafa"));
        assertTrue(
                ((List<?>) naEmpresa(outra, () -> vendedores.dados("DONO")).get("vendedores"))
                        .isEmpty());
        var abrir =
                assertThrows(
                        ResponseStatusException.class,
                        () -> naEmpresa(outra, () -> vendedores.detalhe("DONO", id)));
        assertEquals(HttpStatus.NOT_FOUND, abrir.getStatusCode());
    }

    @Test
    void excluirMandaParaExcluidosDesligaOAcessoERestaura() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        UUID usuario = novoUsuario(empresa);
        UUID id =
                salvar(
                        empresa,
                        completo("Rafa").replace("}", ",\"usuario_id\":\"" + usuario + "\"}"));
        String excluir = "{\"acao\":\"EXCLUIR\",\"ids\":[\"" + id + "\"]}";

        var deFora =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        outra,
                                        () ->
                                                vendedores.executar(
                                                        "vendedores_lote", json(excluir), "DONO")));
        assertEquals(HttpStatus.NOT_FOUND, deFora.getStatusCode());
        var semCargo =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                vendedores.executar(
                                                        "vendedores_lote",
                                                        json(excluir),
                                                        "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, semCargo.getStatusCode());

        String clienteAntigo =
                "{\"nome\":\"Bia\",\"tipo_pessoa\":\"F\",\"documento\":\"52998224725\","
                        + "\"cep\":\"01310100\",\"endereco\":\"Av\",\"numero\":\"1\","
                        + "\"bairro\":\"B\",\"cidade\":\"SP\",\"uf\":\"SP\",\"vendedor_id\":\""
                        + id
                        + "\"}";
        UUID cli =
                (UUID)
                        naEmpresa(empresa, () -> clientes.salvar(json(clienteAntigo), "DONO"))
                                .get("id");
        naEmpresa(empresa, () -> vendedores.executar("vendedores_lote", json(excluir), "GESTOR"));
        var v =
                naEmpresa(
                        empresa,
                        () -> db.queryForMap("select * from radar_vendedor where id=?", id));
        assertTrue(v.get("excluido_em") != null);
        assertEquals(null, v.get("usuario_id"));
        assertEquals("INATIVO", v.get("situacao"));
        // Excluído não se edita nem vira vendedor de cliente.
        assertThrows(
                ResponseStatusException.class,
                () -> salvar(empresa, completo("Rafa").replace("}", ",\"id\":\"" + id + "\"}")));
        var cliente =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () ->
                                                clientes.salvar(
                                                        json(
                                                                "{\"nome\":\"Ana\",\"tipo_pessoa\":\"F\","
                                                                    + "\"documento\":\"39053344705\","
                                                                    + "\"cep\":\"01310100\",\"endereco\":\"Av\","
                                                                    + "\"numero\":\"1\",\"bairro\":\"B\","
                                                                    + "\"cidade\":\"SP\",\"uf\":\"SP\","
                                                                    + "\"vendedor_id\":\""
                                                                        + id
                                                                        + "\"}"),
                                                        "DONO")));
        assertTrue(cliente.getReason().contains("Vendedor não encontrado"), cliente.getReason());

        // Cliente que já tinha o vendedor continua salvando sem trocar.
        naEmpresa(
                empresa,
                () ->
                        clientes.salvar(
                                json(clienteAntigo.replace("{", "{\"id\":\"" + cli + "\",")),
                                "DONO"));

        naEmpresa(
                empresa,
                () ->
                        vendedores.executar(
                                "vendedores_lote",
                                json("{\"acao\":\"RESTAURAR\",\"ids\":[\"" + id + "\"]}"),
                                "DONO"));
        assertEquals(
                null,
                naEmpresa(
                                empresa,
                                () ->
                                        db.queryForMap(
                                                "select excluido_em from radar_vendedor where id=?",
                                                id))
                        .get("excluido_em"));
    }

    @Test
    void comissaoRapidaValidaAAliquota() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID id = salvar(empresa, completo("Rafa"));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        comissao(
                                empresa,
                                id,
                                "{\"comissao_regra\":\"FIXA\",\"comissao_aliquota\":\"101\"}"));
        comissao(
                empresa,
                id,
                "{\"comissao_regra\":\"DESCONTO\",\"comissao_aliquota\":\"4,5\","
                        + "\"desconsiderar_comissao_linha\":true}");
        var v =
                naEmpresa(
                        empresa,
                        () -> db.queryForMap("select * from radar_vendedor where id=?", id));
        assertEquals("DESCONTO", v.get("comissao_regra"));
        assertEquals(new BigDecimal("4.50"), v.get("comissao_aliquota"));
        assertEquals(true, v.get("desconsiderar_comissao_linha"));
    }

    @Test
    void senhaDeAcessoSoADonaTrocaComUsuarioLigado() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        UUID semUsuario = salvar(empresa, completo("Sem acesso"));
        UUID usuario = novoUsuario(empresa);
        UUID id =
                salvar(
                        empresa,
                        completo("Rafa")
                                .replace("529.982.247-25", "390.533.447-05")
                                .replace("}", ",\"usuario_id\":\"" + usuario + "\"}"));

        var gestor =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresa,
                                        () -> senha("GESTOR", id, "novaSenha1", "novaSenha1")));
        assertEquals(HttpStatus.FORBIDDEN, gestor.getStatusCode());
        assertTrue(
                assertThrows(
                                ResponseStatusException.class,
                                () ->
                                        naEmpresa(
                                                empresa,
                                                () ->
                                                        senha(
                                                                "DONO",
                                                                semUsuario,
                                                                "novaSenha1",
                                                                "novaSenha1")))
                        .getReason()
                        .contains("não tem usuário"));
        assertTrue(
                assertThrows(
                                ResponseStatusException.class,
                                () -> naEmpresa(empresa, () -> senha("DONO", id, "curta", "curta")))
                        .getReason()
                        .contains("8 caracteres"));
        assertTrue(
                assertThrows(
                                ResponseStatusException.class,
                                () ->
                                        naEmpresa(
                                                empresa,
                                                () ->
                                                        senha(
                                                                "DONO",
                                                                id,
                                                                "novaSenha1",
                                                                "outraSenha")))
                        .getReason()
                        .contains("confirmação"));
        var deFora =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        outra,
                                        () -> senha("DONO", id, "novaSenha1", "novaSenha1")));
        assertEquals(HttpStatus.NOT_FOUND, deFora.getStatusCode());

        naEmpresa(empresa, () -> senha("DONO", id, "novaSenha1", "novaSenha1"));
        String hash =
                naEmpresa(
                        empresa,
                        () ->
                                db.queryForObject(
                                        "select senha_hash from usuario where id=?",
                                        String.class,
                                        usuario));
        assertTrue(new BCryptPasswordEncoder(12).matches("novaSenha1", hash));
    }

    private static UUID novoUsuario(UUID empresa) throws SQLException {
        UUID usuario = UUID.randomUUID();
        BancoRadarDeTeste.executarComoDono(
                "insert into usuario(id,tenant_id,email,senha_hash,nome,papel)"
                        + " values(?,?,?,?,'Rafa','ATENDIMENTO')",
                usuario,
                empresa,
                "rafa" + usuario.toString().substring(0, 8) + "@radar.test",
                "$2a$12$" + "a".repeat(53));
        return usuario;
    }

    private static Object senha(String papel, UUID id, String senha, String confirmacao) {
        vendedores.alterarSenha(papel, id, senha, confirmacao);
        return null;
    }

    private static void comissao(UUID empresa, UUID id, String corpo) {
        naEmpresa(
                empresa,
                () ->
                        vendedores.executar(
                                "vendedor_comissao",
                                json(corpo.replace("{", "{\"id\":\"" + id + "\",")),
                                "DONO"));
    }

    private static String completo(String nome) {
        return "{\"nome\":\""
                + nome
                + "\",\"tipo_pessoa\":\"F\",\"documento\":\"529.982.247-25\","
                + "\"email\":\"vendas@exemplo.com\",\"cep\":\"01310-100\",\"uf\":\"sp\"}";
    }

    private static UUID salvar(UUID empresa, String corpo) {
        return (UUID) naEmpresa(empresa, () -> vendedores.salvar(json(corpo), "DONO")).get("id");
    }
}
