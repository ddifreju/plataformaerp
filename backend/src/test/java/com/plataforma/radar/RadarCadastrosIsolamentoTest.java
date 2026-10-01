package com.plataforma.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.comum.tenant.DataSourceComTenant;
import com.plataforma.suporte.PostgresDeTeste;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Isolamento entre empresas nos cadastros do Radar (V018).
 *
 * <p>Usa a mesma pilha de produção: conexão como app_aplicacao, passando pelo DataSourceComTenant
 * (que seta o GUC a cada conexão), com RLS forçado. A conexão de dono serve só para preparar dados
 * e provar que as linhas existem.
 */
class RadarCadastrosIsolamentoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID EMPRESA_A = UUID.randomUUID();
    private static final UUID EMPRESA_B = UUID.randomUUID();

    private static JdbcTemplate db;
    private static RadarCadastros cadastros;

    @BeforeAll
    static void preparar() throws SQLException {
        try (Connection dono = PostgresDeTeste.novaConexaoDono()) {
            for (UUID t : List.of(EMPRESA_A, EMPRESA_B)) {
                try (PreparedStatement ps =
                        dono.prepareStatement(
                                "INSERT INTO tenant (id, nome, slug) VALUES (?, ?, ?)")) {
                    ps.setObject(1, t);
                    ps.setString(2, "Empresa de teste " + t);
                    ps.setString(3, "radar-teste-" + t.toString().substring(0, 8));
                    ps.executeUpdate();
                }
            }
        }
        var app =
                new AbstractDataSource() {
                    @Override
                    public Connection getConnection() throws SQLException {
                        return PostgresDeTeste.novaConexaoAppAplicacao();
                    }

                    @Override
                    public Connection getConnection(String usuario, String senha) {
                        throw new UnsupportedOperationException();
                    }
                };
        db = new JdbcTemplate(new DataSourceComTenant(app));
        cadastros = new RadarCadastros(db, JSON);
    }

    @AfterEach
    void limparContexto() {
        ContextoTenant.limpar();
    }

    @Test
    void fornecedorDeUmaEmpresaNaoApareceParaOutra() {
        UUID id = criar(EMPRESA_A, "fornecedor", "{\"nome\":\"Malhas Sul\"}");

        assertTrue(idsDe(EMPRESA_B, "fornecedores").isEmpty());
        assertTrue(idsDe(EMPRESA_A, "fornecedores").contains(id.toString()));
    }

    @Test
    void atualizarCadastroDeOutraEmpresaNaoAlteraNada() throws SQLException {
        UUID id = criar(EMPRESA_A, "categoria", "{\"nome\":\"Persianas\"}");

        ContextoTenant.definir(EMPRESA_B);
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                cadastros.executar(
                                        "categoria_atualizar",
                                        json("{\"id\":\"" + id + "\",\"nome\":\"Invadida\"}"),
                                        "DONO"));
        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
        assertEquals("Persianas", lerComoDono("select nome from radar_categoria where id=?", id));
    }

    @Test
    void vincularCadastroDeOutraEmpresaERecusado() {
        UUID categoriaA = criar(EMPRESA_A, "categoria", "{\"nome\":\"Cortinas\"}");

        ContextoTenant.definir(EMPRESA_B);
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                cadastros.vinculoOpcional(
                                        json("{\"categoria_id\":\"" + categoriaA + "\"}"),
                                        "categoria_id",
                                        "radar_categoria"));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
    }

    @Test
    void rlsEscondeLinhasMesmoSemPredicadoNaQuery() {
        criar(EMPRESA_A, "fornecedor", "{\"nome\":\"Tecidos Norte\"}");

        // Query sem filtro de tenant: só o RLS separa as empresas aqui.
        ContextoTenant.definir(EMPRESA_B);
        Integer visiveis =
                db.queryForObject(
                        "select count(*) from radar_fornecedor where nome='Tecidos Norte'",
                        Integer.class);
        assertEquals(0, visiveis);
    }

    @Test
    void semTenantNaConexaoNenhumaLinhaAparece() throws SQLException {
        criar(EMPRESA_A, "embalagem", "{\"nome\":\"Caixa P\",\"custo\":\"1.80\"}");

        try (Connection crua = PostgresDeTeste.novaConexaoAppAplicacao();
                ResultSet rs =
                        crua.createStatement()
                                .executeQuery("select count(*) from radar_embalagem")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void chaveCompostaImpedeProdutoDeApontarParaCategoriaDeOutraEmpresa() throws SQLException {
        UUID categoriaA = criar(EMPRESA_A, "categoria", "{\"nome\":\"Trilhos\"}");
        UUID produtoB = UUID.randomUUID();
        try (Connection dono = PostgresDeTeste.novaConexaoDono()) {
            try (PreparedStatement ps =
                    dono.prepareStatement(
                            "insert into radar_produto(id,tenant_id,sku,nome,custo,preco)"
                                    + " values(?,?,'SKU-B','Produto B',1,2)")) {
                ps.setObject(1, produtoB);
                ps.setObject(2, EMPRESA_B);
                ps.executeUpdate();
            }
            // Nem o dono do schema (que ignora RLS) consegue: a FK é (tenant_id, categoria_id).
            try (PreparedStatement ps =
                    dono.prepareStatement("update radar_produto set categoria_id=? where id=?")) {
                ps.setObject(1, categoriaA);
                ps.setObject(2, produtoB);
                assertThrows(SQLException.class, ps::executeUpdate);
            }
        }
    }

    @Test
    void custoDaEmbalagemSoApareceParaQuemVeFinanceiro() {
        criar(EMPRESA_A, "embalagem", "{\"nome\":\"Envelope\",\"custo\":\"0.90\"}");

        ContextoTenant.definir(EMPRESA_A);
        var semFinanceiro = linhas(cadastros.dados("ESTOQUE", false), "embalagens");
        var comFinanceiro = linhas(cadastros.dados("DONO", true), "embalagens");
        assertFalse(semFinanceiro.isEmpty());
        assertTrue(semFinanceiro.stream().noneMatch(e -> e.containsKey("custo")));
        assertTrue(comFinanceiro.stream().allMatch(e -> e.containsKey("custo")));
    }

    @Test
    void cargoSemPermissaoNaoCadastraFornecedor() {
        ContextoTenant.definir(EMPRESA_A);
        var erro =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                cadastros.executar(
                                        "fornecedor", json("{\"nome\":\"X\"}"), "MARKETING"));
        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
    }

    @Test
    void custoComMaisDeDuasCasasDecimaisERecusado() {
        ContextoTenant.definir(EMPRESA_A);
        assertThrows(
                ResponseStatusException.class,
                () ->
                        cadastros.executar(
                                "embalagem",
                                json("{\"nome\":\"Caixa G\",\"custo\":\"1.805\"}"),
                                "DONO"));
    }

    @Test
    void categoriaGanhaVinculoComMarketplaceQueOutraEmpresaNaoAlcanca() throws SQLException {
        UUID categoria = criar(EMPRESA_A, "categoria", "{\"nome\":\"Blackout\"}");
        String vinculo =
                "{\"categoria_id\":\""
                        + categoria
                        + "\",\"canal\":\"Shopee\",\"codigo_externo\":\"100629\","
                        + "\"nome_externo\":\"Casa e Decoração > Cortinas\"}";

        ContextoTenant.definir(EMPRESA_B);
        assertThrows(
                ResponseStatusException.class,
                () -> cadastros.executar("categoria_vinculo", json(vinculo), "DONO"));
        ContextoTenant.definir(EMPRESA_A);
        cadastros.executar("categoria_vinculo", json(vinculo), "DONO");
        cadastros.executar("categoria_vinculo", json(vinculo.replace("100629", "100630")), "DONO");
        var canais = linhas(cadastros.dados("DONO", true), "categoriaCanais");
        assertEquals(1, canais.size());
        assertEquals("100630", canais.getFirst().get("codigo_externo"));

        cadastros.executar("categoria_vinculo", json(vinculo.replace("100629", "")), "DONO");
        assertTrue(linhas(cadastros.dados("DONO", true), "categoriaCanais").isEmpty());
    }

    @Test
    void embalagensSugeridasEntramUmaVezComTagsECustoZero() throws SQLException {
        ContextoTenant.definir(EMPRESA_B);
        cadastros.executar("embalagens_sugeridas", json("{}"), "ESTOQUE");
        cadastros.executar("embalagens_sugeridas", json("{}"), "ESTOQUE");
        var embalagens = linhas(cadastros.dados("DONO", true), "embalagens");
        long sugeridas =
                embalagens.stream().filter(e -> Boolean.TRUE.equals(e.get("sugerida"))).count();
        assertEquals(RadarCadastros.SUGERIDAS.size(), sugeridas);
        var caixa =
                embalagens.stream()
                        .filter(e -> e.get("nome").toString().startsWith("Caixa de papelão P"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, ((java.math.BigDecimal) caixa.get("custo")).signum());
        assertEquals("CAIXA", caixa.get("tipo"));
        assertTrue(caixa.get("tags").toString().contains("papelão"));
        assertTrue(linhas(cadastros.dados("DONO", true), "embalagens").size() >= 19);
    }

    @Test
    void embalagemSalvaTipoETagsSemRepetir() {
        UUID id =
                criar(
                        EMPRESA_A,
                        "embalagem",
                        "{\"nome\":\"Caixa sob medida\",\"custo\":\"2.50\",\"tipo\":\"caixa\","
                                + "\"tags\":[\"Frágil\",\"frágil\",\"cortina\"]}");
        ContextoTenant.definir(EMPRESA_A);
        var e =
                linhas(cadastros.dados("DONO", true), "embalagens").stream()
                        .filter(x -> x.get("id").equals(id))
                        .findFirst()
                        .orElseThrow();
        assertEquals("CAIXA", e.get("tipo"));
        assertEquals("[\"frágil\", \"cortina\"]", e.get("tags").toString());
    }

    // ---- apoio -----------------------------------------------------------------------------

    private static UUID criar(UUID empresa, String op, String corpo) {
        ContextoTenant.definir(empresa);
        try {
            return (UUID) cadastros.executar(op, json(corpo), "DONO").get("id");
        } finally {
            ContextoTenant.limpar();
        }
    }

    private static List<String> idsDe(UUID empresa, String lista) {
        ContextoTenant.definir(empresa);
        try {
            return linhas(cadastros.dados("DONO", true), lista).stream()
                    .map(l -> l.get("id").toString())
                    .toList();
        } finally {
            ContextoTenant.limpar();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> linhas(Map<String, Object> dados, String lista) {
        return (List<Map<String, Object>>) dados.get(lista);
    }

    private static String lerComoDono(String sql, UUID id) throws SQLException {
        try (Connection dono = PostgresDeTeste.novaConexaoDono();
                PreparedStatement ps = dono.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static JsonNode json(String texto) {
        try {
            return JSON.readTree(texto);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
