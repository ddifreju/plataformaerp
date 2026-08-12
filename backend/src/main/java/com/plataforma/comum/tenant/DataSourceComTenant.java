package com.plataforma.comum.tenant;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.UUID;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Decora o DataSource real (o pool Hikari, construido em
 * ConfiguracaoTenant) e, antes de devolver QUALQUER conexao, executa
 * {@code SET app.tenant_id} no banco. E a camada 4 da decisao 0007: o
 * GUC que alimenta o Row Level Security no Postgres (V001, V003).
 *
 * PORQUE STRING VAZIA E NAO "NAO SETAR NADA" QUANDO NAO HA TENANT:
 * o pool REUTILIZA conexoes fisicas entre requisicoes. Se, na ausencia
 * de tenant no contexto, simplesmente nao setassemos o GUC, uma conexao
 * que serviu o tenant A no request anterior sairia do pool ainda com
 * app.tenant_id = A para o proximo request que a pegar - mesmo que esse
 * proximo request seja de outro tenant ou nao tenha tenant nenhum. Por
 * isso esta classe SEMPRE executa o SET, com o tenant do contexto ou
 * com string vazia, nunca "deixa como estava". String vazia faz
 * app_current_tenant_id() (V001) devolver NULL, e NULL em toda policy
 * de RLS e tratado como falso: nenhuma linha visivel, nenhum insert
 * permitido. Falha fechada.
 */
public class DataSourceComTenant implements DataSource {

    private final DataSource origem;

    public DataSourceComTenant(DataSource origem) {
        this.origem = origem;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conexao = origem.getConnection();
        setarGucDeTenant(conexao);
        return conexao;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conexao = origem.getConnection(username, password);
        setarGucDeTenant(conexao);
        return conexao;
    }

    private void setarGucDeTenant(Connection conexao) throws SQLException {
        String tenantOuVazio = ContextoTenant.atualOuVazio()
                .map(UUID::toString)
                .orElse("");

        // set_config(...), NAO "SET app.tenant_id = ?": o comando SET do
        // Postgres nao aceita bind parameter, so literal. set_config() e
        // a funcao equivalente que aceita parametro - e o que nos permite
        // nunca concatenar o valor do tenant direto na string SQL.
        // Terceiro argumento `false` = is_local: o valor vale para toda a
        // conexao (nao so para a transacao atual), coerente com o fato de
        // setarmos isto uma vez por getConnection(), nao por transacao.
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, false)")) {
            comando.setString(1, tenantOuVazio);
            comando.execute();
        } catch (SQLException erroAoSetarGuc) {
            // Nunca devolvemos uma conexao sem o GUC setado - isso seria
            // uma conexao "sem opiniao" sobre tenant. Fechamos para nao
            // vazar uma conexao presa fora do pool e propagamos o erro:
            // o chamador precisa saber que nao conseguiu uma conexao
            // utilizavel, em vez de receber uma conexao que pode nao
            // estar isolada por tenant.
            conexao.close();
            throw erroAoSetarGuc;
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return origem.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        origem.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        origem.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return origem.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return origem.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return origem.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return origem.isWrapperFor(iface);
    }
}
