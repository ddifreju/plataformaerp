import java.sql.*;
import java.util.*;

class VerifyDb {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.setProperty("user", "app_aplicacao");
        p.setProperty("password", System.getenv("RADAR_APP_PASSWORD"));
        try (var c =
                DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55432/radar_local", p)) {
            var r = c.createStatement().executeQuery("select count(*) from radar_produto");
            r.next();
            if (r.getInt(1) != 0) throw new AssertionError("Tenant ausente expôs dados");
            c.createStatement()
                    .execute(
                            "select"
                                + " set_config('app.tenant_id','11111111-1111-1111-1111-111111111111',false)");
            try {
                c.createStatement()
                        .execute(
                                "insert into radar_produto(id,tenant_id,sku,nome)"
                                    + " values(gen_random_uuid(),'"
                                        + UUID.nameUUIDFromBytes("qa-a".getBytes())
                                        + "','MUST-FAIL','MUST-FAIL')");
                throw new AssertionError("RLS permitiu outro tenant");
            } catch (SQLException expected) {
                if (!expected.getSQLState().equals("42501")) throw expected;
            }
            for (String table :
                    List.of(
                            "radar_movimento",
                            "radar_lancamento",
                            "radar_auditoria",
                            "radar_comando"))
                try {
                    c.createStatement().execute("delete from " + table + " where false");
                    throw new AssertionError("Permissão de exclusão: " + table);
                } catch (SQLException expected) {
                    if (!expected.getSQLState().equals("42501")) throw expected;
                }
            System.out.println("6 verificações de isolamento RLS e registros imutáveis passaram.");
        }
    }
}
