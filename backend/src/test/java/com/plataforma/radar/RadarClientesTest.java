package com.plataforma.radar;

import static com.plataforma.radar.BancoRadarDeTeste.json;
import static com.plataforma.radar.BancoRadarDeTeste.naEmpresa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cadastro completo de cliente (V021): obrigatórios da NF-e, documento, recorrência, isolamento.
 */
class RadarClientesTest {

    private static final String CPF = "52998224725";
    private static final String CNPJ = "11222333000181";
    private static final String CNPJ_ALFANUMERICO = "12ABC34501DE35";

    private static JdbcTemplate db;
    private static RadarClientes clientes;
    private static UUID empresaA;
    private static UUID empresaB;

    @BeforeAll
    static void preparar() throws SQLException {
        db = BancoRadarDeTeste.comoAplicacao();
        clientes = new RadarClientes(db, BancoRadarDeTeste.JSON);
        empresaA = BancoRadarDeTeste.novaEmpresa();
        empresaB = BancoRadarDeTeste.novaEmpresa();
    }

    @Test
    void validaDigitosDeCpfECnpjInclusiveAlfanumerico() {
        assertTrue(RadarEntrada.cpfValido(CPF));
        assertFalse(RadarEntrada.cpfValido("52998224724"));
        assertFalse(RadarEntrada.cpfValido("11111111111"));
        assertTrue(RadarEntrada.cnpjValido(CNPJ));
        assertFalse(RadarEntrada.cnpjValido("11222333000182"));
        assertTrue(RadarEntrada.cnpjValido(CNPJ_ALFANUMERICO));
        assertFalse(RadarEntrada.cnpjValido("12ABC34501DE36"));
    }

    @Test
    void semCamposDaNotaNaoSalvaEListaOQueFalta() {
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                salvar(
                                        empresaA,
                                        "{\"nome\":\"Sem endereço\",\"tipo_pessoa\":\"F\"}"));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        for (String campo :
                List.of("CPF", "CEP", "Endereço", "Número", "Bairro", "Município", "UF"))
            assertTrue(erro.getReason().contains(campo), campo);
    }

    @Test
    void cpfComDigitoErradoERecusado() {
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () -> salvar(empresaA, completo("F", "529.982.247-24", "Ana")));
        assertTrue(erro.getReason().contains("CPF inválido"));
    }

    @Test
    void salvaClienteCompletoComCodigoAutomaticoEDocumentoLimpo() {
        UUID id = salvar(empresaA, completo("J", "12.ABC.345/01DE-35", "Loja Alfa"));
        var c = linha(empresaA, "select * from radar_cliente where id=?", id);
        assertEquals(CNPJ_ALFANUMERICO, c.get("documento"));
        assertTrue(c.get("codigo").toString().matches("C[0-9]{5}"));
        assertEquals("01310100", c.get("cep"));
        assertEquals(false, c.get("incompleto"));
        assertEquals("MANUAL", c.get("origem"));
    }

    @Test
    void estrangeiraDispensaCpfCepEUfMasExigePais() {
        String corpo =
                "{\"nome\":\"John Smith\",\"tipo_pessoa\":\"E\",\"endereco\":\"Main St\","
                    + "\"numero\":\"10\",\"cidade\":\"Boston\",\"documento_estrangeiro\":\"X123\"}";
        var erro = assertThrows(ResponseStatusException.class, () -> salvar(empresaA, corpo));
        assertTrue(erro.getReason().contains("País"));
        UUID id = salvar(empresaA, corpo.replaceFirst("\\}$", ",\"pais\":\"Estados Unidos\"}"));
        var c = linha(empresaA, "select * from radar_cliente where id=?", id);
        assertNull(c.get("documento"));
        assertEquals("X123", c.get("documento_estrangeiro"));
    }

    @Test
    void documentoRepetidoNaMesmaEmpresaERecusadoMasEmOutraPode() {
        salvar(empresaA, completo("J", CNPJ, "Primeira"));
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () -> salvar(empresaA, completo("J", CNPJ, "Segunda")));
        assertTrue(erro.getReason().contains("Já existe"));
        salvar(empresaB, completo("J", CNPJ, "Outra empresa"));
    }

    @Test
    void listaMostraDocumentoMascaradoEDetalheCompletoSoParaQuemPode() {
        UUID id = salvar(empresaA, completo("F", CPF, "Bruna Mascarada"));
        var lista = lista(empresaA, "DONO");
        var daLista = lista.stream().filter(c -> c.get("id").equals(id)).findFirst().orElseThrow();
        assertEquals("***.982.247-**", daLista.get("documento"));
        assertEquals(CPF, naEmpresa(empresaA, () -> clientes.detalhe("DONO", id)).get("documento"));
        assertEquals(
                "***.982.247-**",
                naEmpresa(empresaA, () -> clientes.detalhe("ANALISTA", id)).get("documento"));
        assertThrows(
                ResponseStatusException.class,
                () -> naEmpresa(empresaA, () -> clientes.detalhe("ESTOQUE", id)));
        assertTrue(lista(empresaA, "ESTOQUE").isEmpty());
    }

    @Test
    void clienteDeOutraEmpresaNaoApareceNemAbre() {
        UUID id = salvar(empresaA, completo("F", "39053344705", "Carla Isolada"));
        assertTrue(lista(empresaB, "DONO").stream().noneMatch(c -> c.get("id").equals(id)));
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () -> naEmpresa(empresaB, () -> clientes.detalhe("DONO", id)));
        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
        var atualizar =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                salvar(
                                        empresaB,
                                        completo("F", "39053344705", "Invadida")
                                                .replaceFirst("\\{", "{\"id\":\"" + id + "\",")));
        assertEquals(HttpStatus.NOT_FOUND, atualizar.getStatusCode());
    }

    @Test
    void pedidoReconheceClienteSoPeloDocumentoNuncaPeloNome() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID produto = BancoRadarDeTeste.novoProduto(empresa, "REC-1", "10.00", "30.00");

        // Mesmo nome, sem documento: pessoas diferentes podem ter o mesmo nome.
        UUID semCpf1 = naEmpresa(empresa, () -> clientes.clienteDoPedido("Diego Alves", ""));
        UUID semCpf2 = naEmpresa(empresa, () -> clientes.clienteDoPedido("Diego Alves", null));
        assertNotEquals(semCpf1, semCpf2);
        var c = linha(empresa, "select * from radar_cliente where id=?", semCpf1);
        assertEquals("PEDIDO", c.get("origem"));
        assertEquals(true, c.get("incompleto"));

        // Mesmo CPF: mesmo cliente, com qualquer nome digitado.
        UUID comCpf = naEmpresa(empresa, () -> clientes.clienteDoPedido("Diego Alves", CPF));
        assertNotEquals(semCpf1, comCpf);
        assertEquals(
                comCpf,
                naEmpresa(empresa, () -> clientes.clienteDoPedido("D. Alves", "529.982.247-25")));

        assertEquals("LEAD", classificacao(empresa, comCpf));
        vincularPedido(empresa, produto, comCpf);
        assertEquals("PRIMEIRA_COMPRA", classificacao(empresa, comCpf));
        vincularPedido(empresa, produto, comCpf);
        assertEquals("RECORRENTE", classificacao(empresa, comCpf));
    }

    @Test
    void incompletoQueRecebeCpfJaCadastradoJuntaOsPedidosNoClienteExistente() throws SQLException {
        UUID empresa = BancoRadarDeTeste.novaEmpresa();
        UUID produto = BancoRadarDeTeste.novoProduto(empresa, "JUN-1", "10.00", "30.00");
        UUID existente = salvar(empresa, completo("F", CPF, "Gabriela Lima"));
        vincularPedido(empresa, produto, existente);
        UUID incompleto = naEmpresa(empresa, () -> clientes.clienteDoPedido("Gabi", null));
        vincularPedido(empresa, produto, incompleto);
        vincularPedido(empresa, produto, incompleto);

        var r =
                naEmpresa(
                        empresa,
                        () ->
                                clientes.salvar(
                                        json(
                                                completo("F", CPF, "Gabi")
                                                        .replaceFirst(
                                                                "\\{",
                                                                "{\"id\":\"" + incompleto + "\",")),
                                        "DONO"));
        assertEquals(existente, r.get("id"));
        assertEquals(2, r.get("pedidos_movidos"));
        assertEquals("RECORRENTE", classificacao(empresa, existente));
        assertTrue(lista(empresa, "DONO").stream().noneMatch(x -> x.get("id").equals(incompleto)));
        // Os dados do cliente existente não são trocados pelos do incompleto.
        assertEquals(
                "Gabriela Lima",
                linha(empresa, "select nome from radar_cliente where id=?", existente).get("nome"));
    }

    @Test
    void clienteCompletoComCpfDeOutroNaoEJuntadoERecusado() {
        UUID empresa = naEmpresaNova();
        salvar(empresa, completo("F", CPF, "Primeira Pessoa"));
        UUID outro = salvar(empresa, completo("F", "39053344705", "Segunda Pessoa"));
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                salvar(
                                        empresa,
                                        completo("F", CPF, "Segunda Pessoa")
                                                .replaceFirst(
                                                        "\\{", "{\"id\":\"" + outro + "\",")));
        assertTrue(erro.getReason().contains("Já existe"));
    }

    @Test
    void salvarPelaTelaCompletaOClienteCriadoPeloPedido() {
        UUID id = naEmpresa(empresaA, () -> clientes.clienteDoPedido("Eva Pedido", null));
        salvar(
                empresaA,
                completo("F", "11144477735", "Eva Pedido")
                        .replaceFirst("\\{", "{\"id\":\"" + id + "\","));
        var c = linha(empresaA, "select * from radar_cliente where id=?", id);
        assertEquals(false, c.get("incompleto"));
        assertEquals("PEDIDO", c.get("origem"));
    }

    @Test
    void cargoSemPermissaoNaoSalva() {
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                naEmpresa(
                                        empresaA,
                                        () ->
                                                clientes.salvar(
                                                        json(completo("F", CPF, "X")), "ESTOQUE")));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
    }

    @Test
    void anexoAcimaDe2MbOuTipoEstranhoERecusado() {
        UUID id = salvar(empresaA, completo("F", "86288366757", "Anexo"));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresaA,
                                () ->
                                        clientes.adicionarAnexo(
                                                "DONO",
                                                id,
                                                "a.pdf",
                                                "application/pdf",
                                                new byte[2 * 1024 * 1024 + 1])));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        naEmpresa(
                                empresaA,
                                () ->
                                        clientes.adicionarAnexo(
                                                "DONO",
                                                id,
                                                "a.exe",
                                                "application/x-msdownload",
                                                new byte[10])));
        UUID anexo =
                naEmpresa(
                        empresaA,
                        () ->
                                clientes.adicionarAnexo(
                                        "DONO",
                                        id,
                                        "../contrato.pdf",
                                        "application/pdf",
                                        new byte[] {1, 2, 3}));
        assertEquals(
                "contrato.pdf",
                naEmpresa(empresaA, () -> clientes.anexo("DONO", anexo)).get("nome_arquivo"));
        assertThrows(
                ResponseStatusException.class,
                () -> naEmpresa(empresaB, () -> clientes.anexo("DONO", anexo)));
    }

    // ---- apoio -----------------------------------------------------------------------------

    private static UUID naEmpresaNova() {
        try {
            return BancoRadarDeTeste.novaEmpresa();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String completo(String tipo, String documento, String nome) {
        return "{\"nome\":\""
                + nome
                + "\",\"tipo_pessoa\":\""
                + tipo
                + "\",\"documento\":\""
                + documento
                + "\",\"cep\":\"01310-100\",\"endereco\":\"Av. Paulista\",\"numero\":\"1000\","
                + "\"bairro\":\"Bela Vista\",\"cidade\":\"São Paulo\",\"uf\":\"sp\","
                + "\"tipos_contato\":[\"CLIENTE\",\"TRANSPORTADOR\"],"
                + "\"pessoas_contato\":[{\"nome\":\"Rita\",\"setor\":\"Compras\"}]}";
    }

    private static UUID salvar(UUID empresa, String corpo) {
        return (UUID) naEmpresa(empresa, () -> clientes.salvar(json(corpo), "DONO")).get("id");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(UUID empresa, String papel) {
        return (List<Map<String, Object>>)
                naEmpresa(empresa, () -> clientes.dados(papel)).get("clientes");
    }

    private static String classificacao(UUID empresa, UUID cliente) {
        return lista(empresa, "DONO").stream()
                .filter(c -> c.get("id").equals(cliente))
                .findFirst()
                .orElseThrow()
                .get("classificacao")
                .toString();
    }

    private static void vincularPedido(UUID empresa, UUID produto, UUID cliente)
            throws SQLException {
        UUID pedido =
                BancoRadarDeTeste.novoPedido(empresa, produto, "Shopee", "Diego", "30.00", "10.00");
        BancoRadarDeTeste.executarComoDono(
                "update radar_pedido set cliente_id=? where id=?", cliente, pedido);
    }

    private static Map<String, Object> linha(UUID empresa, String sql, Object id) {
        return naEmpresa(empresa, () -> db.queryForMap(sql, id));
    }
}
