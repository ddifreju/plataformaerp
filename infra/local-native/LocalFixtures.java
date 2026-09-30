import java.sql.*;
import java.util.*;

public class LocalFixtures {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.setProperty("user", "radar_admin");
        p.setProperty("password", System.getenv("RADAR_ADMIN_PASSWORD"));
        try (var c =
                DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55432/radar_local", p)) {
            for (String role : List.of("FINANCEIRO", "ATENDIMENTO", "ESTOQUE", "MARKETING")) {
                var q =
                        c.prepareStatement(
                                "insert into"
                                    + " usuario(id,tenant_id,email,senha_hash,nome,papel,ativo)"
                                    + " select"
                                    + " gen_random_uuid(),'11111111-1111-1111-1111-111111111111',?,crypt('demo1234',gen_salt('bf',12)),?,?,true"
                                    + " where not exists(select 1 from usuario where email=?)");
                String email = role.toLowerCase() + "@demo.plataforma";
                q.setString(1, email);
                q.setString(2, "Demo " + role.toLowerCase());
                q.setString(3, role);
                q.setString(4, email);
                q.executeUpdate();
            }
            for (String name : List.of("qa-a", "qa-b")) {
                UUID tid = UUID.nameUUIDFromBytes(name.getBytes());
                var q =
                        c.prepareStatement(
                                "insert into tenant(id,nome,slug,ativo) values(?,?,?,true) on"
                                    + " conflict do nothing");
                q.setObject(1, tid);
                q.setString(2, "TESTE LOCAL " + name);
                q.setString(3, "radar-" + name);
                q.executeUpdate();
                for (String role : List.of("DONO", "ATENDIMENTO")) {
                    String email = name + "-" + role.toLowerCase() + "@radar.test";
                    var u =
                            c.prepareStatement(
                                    "insert into"
                                        + " usuario(id,tenant_id,email,senha_hash,nome,papel,ativo)"
                                        + " select"
                                        + " gen_random_uuid(),?,?,crypt('demo1234',gen_salt('bf',12)),?,?,true"
                                        + " where not exists(select 1 from usuario where email=?)");
                    u.setObject(1, tid);
                    u.setString(2, email);
                    u.setString(3, "Teste " + role);
                    u.setString(4, role);
                    u.setString(5, email);
                    u.executeUpdate();
                }
            }
            System.out.println("Perfis de demonstração e tenants isolados de teste preparados.");
        }
    }
}
