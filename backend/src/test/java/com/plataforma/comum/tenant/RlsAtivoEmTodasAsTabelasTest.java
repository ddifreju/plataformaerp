package com.plataforma.comum.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.plataforma.suporte.PostgresDeTeste;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O teste-sentinela da decisao 0010 ("padrao de RLS que toda tabela deve
 * repetir"). E deliberadamente GENERICO: descobre as tabelas a proteger
 * consultando {@code information_schema.columns} por qualquer coluna
 * chamada {@code tenant_id}, em vez de manter uma lista fixa de nomes de
 * tabela.
 *
 * Por que isso importa mais do que parece: hoje so existe
 * {@code consulta_auditada} (V003). Quando a Fase 1 criar {@code pedido},
 * {@code produto}, {@code devolucao} etc., NINGUEM precisa lembrar de
 * atualizar este teste — ele descobre a tabela nova sozinho pela coluna
 * tenant_id, e falha imediatamente se essa tabela nova nasceu sem RLS,
 * sem FORCE, ou sem alguma das quatro policies. Isso e a diferenca entre
 * um teste que precisa de disciplina humana para continuar valendo e um
 * que continua valendo sozinho.
 *
 * NOTA sobre {@code tenant} (V002): essa tabela NAO tem coluna
 * {@code tenant_id} (o proprio id dela E o tenant — ver o comentario da
 * V002) e por isso e automaticamente excluida da varredura, o que e o
 * comportamento correto: ela e o catalogo administrativo, legitimamente
 * sem RLS.
 *
 * Usa a conexao "dono" (superusuario do container de teste) porque este
 * teste consulta CATALOGO do Postgres (pg_class, pg_policies, pg_roles),
 * nao dado de tenant — nao ha RLS sobre o catalogo do sistema, entao qual
 * papel conecta e irrelevante aqui (diferente de IsolamentoDeTenantTest,
 * onde isso e o ponto central).
 */
class RlsAtivoEmTodasAsTabelasTest {

    private static final List<String> COMANDOS_OBRIGATORIOS = List.of("SELECT", "INSERT", "UPDATE", "DELETE");

    @Test
    void todaTabelaComColunaTenantIdTemRlsAtivoForcadoEQuatroPolicies() throws SQLException {
        List<String> problemas = new ArrayList<>();

        try (Connection conexao = PostgresDeTeste.novaConexaoDono()) {
            List<String> tabelas = tabelasComColunaTenantId(conexao);

            // Se isto disparar, o discovery por information_schema esta
            // quebrado (ou nenhuma migration rodou) - de qualquer forma,
            // um teste que "passa" com zero tabelas verificadas nao prova
            // nada e esconderia o problema real. Falhar alto e preferivel.
            assertFalse(tabelas.isEmpty(),
                    "Nenhuma tabela com coluna tenant_id foi encontrada em information_schema.columns. "
                            + "Era esperado encontrar ao menos consulta_auditada (V003). "
                            + "Isso indica falha nas migrations ou no proprio discovery deste teste, "
                            + "nao ausencia legitima de tabela multi-tenant.");

            for (String tabela : tabelas) {
                verificarRlsEForce(conexao, tabela, problemas);
                verificarPolicies(conexao, tabela, problemas);
            }
        }

        assertTrue(problemas.isEmpty(), () -> "Tabela(s) com isolamento de tenant incompleto "
                + "(decisao 0010 - toda tabela com tenant_id precisa ENABLE + FORCE ROW LEVEL SECURITY "
                + "e uma policy para cada um de SELECT/INSERT/UPDATE/DELETE):\n"
                + String.join("\n", problemas));
    }

    @Test
    void papelDaAplicacaoNaoTemSuperuserNemBypassrls() throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT rolsuper, rolbypassrls FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao'")) {

            try (ResultSet resultado = comando.executeQuery()) {
                assertTrue(resultado.next(), "papel app_aplicacao nao existe - a migration V004 rodou?");

                boolean rolsuper = resultado.getBoolean("rolsuper");
                boolean rolbypassrls = resultado.getBoolean("rolbypassrls");

                assertTrue(!rolsuper && !rolbypassrls,
                        "app_aplicacao tem rolsuper=" + rolsuper + " e/ou rolbypassrls=" + rolbypassrls
                                + " - qualquer um dos dois anula TODO o Row Level Security do sistema, "
                                + "para toda tabela, silenciosamente. Corrija com "
                                + "ALTER ROLE app_aplicacao NOSUPERUSER NOBYPASSRLS (como superusuario) "
                                + "- ver o bloco de verificacao da V004.");
            }
        }
    }

    private List<String> tabelasComColunaTenantId(Connection conexao) throws SQLException {
        List<String> tabelas = new ArrayList<>();
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT table_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND column_name = 'tenant_id' "
                        + "ORDER BY table_name")) {
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    tabelas.add(resultado.getString("table_name"));
                }
            }
        }
        return tabelas;
    }

    private void verificarRlsEForce(Connection conexao, String tabela, List<String> problemas) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT c.relrowsecurity, c.relforcerowsecurity "
                        + "FROM pg_catalog.pg_class c "
                        + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relname = ?")) {
            comando.setString(1, tabela);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    problemas.add("- " + tabela + ": nao encontrada em pg_class (nome errado ou schema errado?).");
                    return;
                }

                boolean rowSecurity = resultado.getBoolean("relrowsecurity");
                boolean forceRowSecurity = resultado.getBoolean("relforcerowsecurity");

                if (!rowSecurity) {
                    problemas.add("- " + tabela + ": falta ALTER TABLE " + tabela
                            + " ENABLE ROW LEVEL SECURITY.");
                }
                if (!forceRowSecurity) {
                    problemas.add("- " + tabela + ": falta ALTER TABLE " + tabela
                            + " FORCE ROW LEVEL SECURITY (sem FORCE, o DONO da tabela escapa das "
                            + "policies, e qualquer job/migration rodando como dono vaza dado entre "
                            + "tenants sem aviso - ver decisao 0010, armadilha 1).");
                }
            }
        }
    }

    private void verificarPolicies(Connection conexao, String tabela, List<String> problemas) throws SQLException {
        Set<String> comandosCobertos = new HashSet<>();
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT cmd FROM pg_catalog.pg_policies WHERE schemaname = 'public' AND tablename = ?")) {
            comando.setString(1, tabela);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    comandosCobertos.add(resultado.getString("cmd"));
                }
            }
        }

        boolean temPolicyParaTodosOsComandos = comandosCobertos.contains("ALL");
        for (String comandoObrigatorio : COMANDOS_OBRIGATORIOS) {
            if (!temPolicyParaTodosOsComandos && !comandosCobertos.contains(comandoObrigatorio)) {
                problemas.add("- " + tabela + ": falta policy de " + comandoObrigatorio + ". Molde (decisao 0010): "
                        + "CREATE POLICY " + tabela.toLowerCase() + "_" + comandoObrigatorio.toLowerCase()
                        + " ON " + tabela + " FOR " + comandoObrigatorio + " "
                        + clausulaDoMolde(comandoObrigatorio) + ";");
            }
        }
    }

    /**
     * A clausula correta varia por comando: INSERT so aceita WITH CHECK
     * (nao existe linha "anterior" para um USING filtrar); SELECT e
     * DELETE so aceitam USING; UPDATE aceita e precisa dos dois (ver
     * decisao 0010, armadilha 3 - so com USING seria possivel "doar" uma
     * linha para outro tenant reescrevendo o tenant_id dela).
     */
    private String clausulaDoMolde(String comando) {
        return switch (comando) {
            case "INSERT" -> "WITH CHECK (tenant_id = app_current_tenant_id())";
            case "UPDATE" -> "USING (tenant_id = app_current_tenant_id()) "
                    + "WITH CHECK (tenant_id = app_current_tenant_id())";
            default -> "USING (tenant_id = app_current_tenant_id())";
        };
    }
}
