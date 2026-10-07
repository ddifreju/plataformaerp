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

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dados da empresa (V031) e usuários do sistema: validação, permissão, regras e isolamento. */
@SuppressWarnings("unchecked")
class RadarEmpresaTest {

    private static JdbcTemplate db;
    private static RadarEmpresa empresa;

    @BeforeAll
    static void preparar() {
        db = BancoRadarDeTeste.comoAplicacao();
        empresa = new RadarEmpresa(db, new BCryptPasswordEncoder(12));
    }

    @Test
    void salvaEAtualizaOsDadosDaEmpresa() throws SQLException {
        UUID e = BancoRadarDeTeste.novaEmpresa();
        salvar(
                e,
                "{\"razao_social\":\"Loja Teste LTDA\",\"cnpj\":\"11.222.333/0001-81\","
                        + "\"regime_tributario\":\"SIMPLES\",\"cep\":\"01310-100\",\"uf\":\"sp\","
                        + "\"cnae\":\"4781-4/00\",\"inscricao_estadual\":\"isento\"}");
        var d = dados(e, "DONO");
        assertEquals("Loja Teste LTDA", d.get("razao_social"));
        assertEquals("11222333000181", d.get("cnpj"));
        assertEquals("01310100", d.get("cep"));
        assertEquals("SP", d.get("uf"));
        assertEquals("4781400", d.get("cnae"));
        assertEquals("ISENTO", d.get("inscricao_estadual"));
        assertEquals(false, d.get("tem_logo"));

        // Salvar de novo substitui o formulário inteiro: o que veio vazio fica vazio.
        salvar(e, "{\"nome_fantasia\":\"Loja\"}");
        d = dados(e, "DONO");
        assertEquals("Loja", d.get("nome_fantasia"));
        assertEquals(null, d.get("cnpj"));
    }

    @Test
    void recusaCnpjCepRegimeECnaeInvalidos() throws SQLException {
        UUID e = BancoRadarDeTeste.novaEmpresa();
        for (String corpo :
                List.of(
                        "{\"cnpj\":\"11222333000180\"}",
                        "{\"cep\":\"123\"}",
                        "{\"regime_tributario\":\"OUTRO\"}",
                        "{\"cnae\":\"12\"}",
                        "{\"uf\":\"XX\"}"))
            assertThrows(ResponseStatusException.class, () -> salvar(e, corpo), corpo);
    }

    @Test
    void soADonaAltera() throws SQLException {
        UUID e = BancoRadarDeTeste.novaEmpresa();
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        e,
                                        () ->
                                                empresa.executar(
                                                        "empresa_salvar",
                                                        json("{}"),
                                                        "GESTOR",
                                                        UUID.randomUUID())));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
        assertEquals(List.of(), naEmpresa(e, () -> empresa.dados("GESTOR")).get("usuarios"));
    }

    @Test
    void dadosDeUmaEmpresaNaoAparecemEmOutra() throws SQLException {
        UUID a = BancoRadarDeTeste.novaEmpresa();
        UUID b = BancoRadarDeTeste.novaEmpresa();
        salvar(a, "{\"razao_social\":\"Empresa A\"}");
        criar(a, "Ana", "DONO");
        assertEquals(Map.of(), naEmpresa(b, () -> empresa.dados("DONO")).get("empresa"));
        var usuariosB = (List<?>) naEmpresa(b, () -> empresa.dados("DONO")).get("usuarios");
        assertTrue(usuariosB.isEmpty());
        UUID deA = criar(a, "Bia", "GESTOR");
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () -> editar(b, UUID.randomUUID(), deA, "Bia", "DONO", true));
        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
    }

    @Test
    void criaUsuarioComSenhaEEmailUnico() throws SQLException {
        UUID e = BancoRadarDeTeste.novaEmpresa();
        String mail = "novo" + UUID.randomUUID().toString().substring(0, 8) + "@radar.test";
        UUID id =
                naEmpresa(
                        e,
                        () ->
                                empresa.criarUsuario(
                                        "DONO",
                                        json(
                                                "{\"nome\":\"Caio\",\"email\":\""
                                                        + mail.toUpperCase()
                                                        + "\",\"papel\":\"estoque\","
                                                        + "\"senha\":\"senhaBoa1\","
                                                        + "\"confirmacao\":\"senhaBoa1\"}")));
        String hash =
                naEmpresa(
                        e,
                        () ->
                                db.queryForObject(
                                        "select senha_hash from usuario where id=?",
                                        String.class,
                                        id));
        assertTrue(new BCryptPasswordEncoder(12).matches("senhaBoa1", hash));
        UUID outra = BancoRadarDeTeste.novaEmpresa();
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        outra,
                                        () ->
                                                empresa.criarUsuario(
                                                        "DONO",
                                                        json(
                                                                "{\"nome\":\"X\",\"email\":\""
                                                                        + mail
                                                                        + "\",\"papel\":\"GESTOR\","
                                                                        + "\"senha\":\"senhaBoa1\","
                                                                        + "\"confirmacao\":\"senhaBoa1\"}"))));
        assertTrue(erro.getReason().contains("já é usado"));
        for (String ruim :
                List.of(
                        "\"senha\":\"curta\",\"confirmacao\":\"curta\"",
                        "\"senha\":\"senhaBoa1\",\"confirmacao\":\"outra1234\""))
            assertThrows(
                    ResponseStatusException.class,
                    () ->
                            naEmpresa(
                                    e,
                                    () ->
                                            empresa.criarUsuario(
                                                    "DONO",
                                                    json(
                                                            "{\"nome\":\"Y\",\"email\":\"y"
                                                                    + UUID.randomUUID()
                                                                            .toString()
                                                                            .substring(0, 8)
                                                                    + "@radar.test\","
                                                                    + "\"papel\":\"GESTOR\","
                                                                    + ruim
                                                                    + "}"))),
                    ruim);
    }

    @Test
    void naoMudaOProprioCargoNemDeixaAEmpresaSemDono() throws SQLException {
        UUID e = BancoRadarDeTeste.novaEmpresa();
        UUID dona = criar(e, "Dona", "DONO");
        UUID gestor = criar(e, "Gestor", "GESTOR");
        assertThrows(
                ResponseStatusException.class,
                () -> editar(e, dona, dona, "Dona", "GESTOR", true));
        assertThrows(
                ResponseStatusException.class, () -> editar(e, dona, dona, "Dona", "DONO", false));
        // Outra pessoa tentando tirar a única dona também não pode.
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () -> editar(e, gestor, dona, "Dona", "GESTOR", true));
        assertTrue(erro.getReason().contains("pelo menos um dono"));
        // Com uma segunda dona, pode.
        UUID segunda = criar(e, "Segunda", "DONO");
        editar(e, segunda, dona, "Dona", "GESTOR", true);
        editar(e, segunda, gestor, "Gestor Novo", "ESTOQUE", false);
        var u =
                naEmpresa(
                        e,
                        () ->
                                db.queryForMap(
                                        "select nome,papel,ativo from usuario where id=?",
                                        gestor));
        assertEquals("Gestor Novo", u.get("nome"));
        assertEquals("ESTOQUE", u.get("papel"));
        assertEquals(false, u.get("ativo"));
    }

    @Test
    void reconheceOTipoDoLogoPeloConteudo() {
        assertEquals("image/png", RadarEmpresa.tipo(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0}));
        assertEquals("image/jpeg", RadarEmpresa.tipo(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals("", RadarEmpresa.tipo("<svg>nao</svg>".getBytes()));
    }

    private static void salvar(UUID e, String corpo) {
        naEmpresa(
                e,
                () -> empresa.executar("empresa_salvar", json(corpo), "DONO", UUID.randomUUID()));
    }

    private static Map<String, Object> dados(UUID e, String papel) {
        return (Map<String, Object>) naEmpresa(e, () -> empresa.dados(papel)).get("empresa");
    }

    private static UUID criar(UUID e, String nome, String papel) {
        return naEmpresa(
                e,
                () ->
                        empresa.criarUsuario(
                                "DONO",
                                json(
                                        "{\"nome\":\""
                                                + nome
                                                + "\",\"email\":\"u"
                                                + UUID.randomUUID().toString().substring(0, 12)
                                                + "@radar.test\",\"papel\":\""
                                                + papel
                                                + "\",\"senha\":\"senhaBoa1\","
                                                + "\"confirmacao\":\"senhaBoa1\"}")));
    }

    private static void editar(
            UUID e, UUID ator, UUID id, String nome, String papel, boolean ativo) {
        naEmpresa(
                e,
                () ->
                        empresa.executar(
                                "usuario_salvar",
                                json(
                                        "{\"id\":\""
                                                + id
                                                + "\",\"nome\":\""
                                                + nome
                                                + "\",\"papel\":\""
                                                + papel
                                                + "\",\"ativo\":"
                                                + ativo
                                                + "}"),
                                "DONO",
                                ator));
    }
}
