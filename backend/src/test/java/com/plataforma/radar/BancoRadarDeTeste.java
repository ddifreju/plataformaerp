package com.plataforma.radar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.comum.tenant.DataSourceComTenant;
import com.plataforma.suporte.PostgresDeTeste;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Apoio dos testes do Radar: JdbcTemplate como app_aplicacao (pilha de produção, com RLS) e preparo
 * de dados como dono do schema.
 */
final class BancoRadarDeTeste {

    static final ObjectMapper JSON = new ObjectMapper();

    private BancoRadarDeTeste() {}

    static JdbcTemplate comoAplicacao() {
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
        return new JdbcTemplate(new DataSourceComTenant(app));
    }

    static UUID novaEmpresa() throws SQLException {
        UUID id = UUID.randomUUID();
        executarComoDono(
                "INSERT INTO tenant (id, nome, slug) VALUES (?, ?, ?)",
                id,
                "Empresa de teste " + id,
                "radar-teste-" + id.toString().substring(0, 8));
        return id;
    }

    static UUID novoProduto(UUID empresa, String sku, String custo, String preco)
            throws SQLException {
        UUID id = UUID.randomUUID();
        executarComoDono(
                "insert into radar_produto(id,tenant_id,sku,nome,custo,preco,fisico)"
                        + " values(?,?,?,?,?,?,1000)",
                id,
                empresa,
                sku,
                "Produto " + sku,
                new BigDecimal(custo),
                new BigDecimal(preco));
        return id;
    }

    /** Pedido mínimo com lançamentos de receita e custo, como o RadarService grava. */
    static UUID novoPedido(
            UUID empresa, UUID produto, String canal, String cliente, String receita, String custo)
            throws SQLException {
        UUID id = UUID.randomUUID();
        executarComoDono(
                "insert into radar_pedido(id,tenant_id,produto_id,numero,canal,cliente,quantidade,"
                        + "preco,custo_unitario,comissao,frete,imposto,ads,embalagem,desconto)"
                        + " values(?,?,?,?,?,?,1,?,?,0,0,0,0,0,0)",
                id,
                empresa,
                produto,
                "T-" + id.toString().substring(0, 8),
                canal,
                cliente,
                new BigDecimal(receita),
                new BigDecimal(custo));
        Timestamp agora = Timestamp.from(Instant.now());
        executarComoDono(
                "insert into radar_lancamento(id,tenant_id,pedido_id,tipo,valor,fonte,criado_em)"
                        + " values(?,?,?,'RECEITA',?,'teste',?)",
                UUID.randomUUID(),
                empresa,
                id,
                new BigDecimal(receita),
                agora);
        executarComoDono(
                "insert into radar_lancamento(id,tenant_id,pedido_id,tipo,valor,fonte,criado_em)"
                        + " values(?,?,?,'CMV',?,'teste',?)",
                UUID.randomUUID(),
                empresa,
                id,
                new BigDecimal(custo).negate(),
                agora);
        return id;
    }

    static void executarComoDono(String sql, Object... parametros) throws SQLException {
        try (Connection dono = PostgresDeTeste.novaConexaoDono();
                PreparedStatement ps = dono.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) ps.setObject(i + 1, parametros[i]);
            ps.executeUpdate();
        }
    }

    static <T> T naEmpresa(UUID empresa, Supplier<T> acao) {
        ContextoTenant.definir(empresa);
        try {
            return acao.get();
        } finally {
            ContextoTenant.limpar();
        }
    }

    static JsonNode json(String texto) {
        try {
            return JSON.readTree(texto);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
