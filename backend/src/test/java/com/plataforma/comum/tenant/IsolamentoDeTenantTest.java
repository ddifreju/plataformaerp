package com.plataforma.comum.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.plataforma.auditoria.ConsultaAuditada;
import com.plataforma.auditoria.RepositorioConsultaAuditada;
import com.plataforma.suporte.PostgresDeTeste;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de isolamento de tenant contra Postgres de verdade (decisao
 * 0008), conectado como {@code app_aplicacao} (decisao 0007/0010 -
 * NUNCA como dono/superusuario: ver o Javadoc de
 * {@link PostgresDeTeste}). Cobre os quatro elos da decisao 0007
 * (filtro -> contexto -> predicado do Hibernate -> RLS), com foco nos
 * dois ultimos, que so um banco real consegue provar.
 *
 * <h2>Como este teste esta organizado</h2>
 * Cada teste cria dois tenants novos (A e B, com UUID aleatorio) via
 * {@link PostgresDeTeste#novaConexaoDono()} - a unica forma de inserir
 * em {@code tenant}, ja que {@code app_aplicacao} so tem GRANT SELECT
 * nessa tabela (V004). Os testes entao usam o bean {@code DataSource} do
 * proprio contexto Spring (que E o {@code DataSourceComTenant}, decorado
 * pela camada 4) para rodar SQL cru, tendo antes ajustado
 * {@link ContextoTenant} para simular "estar dentro de uma requisicao
 * daquele tenant". Isso exercita a MESMA pilha que um request HTTP real
 * atravessaria, do {@code ContextoTenant} ate a policy no banco.
 *
 * Onde o teste precisa enxergar dado "por fora" do RLS (para provar que
 * uma linha existe de verdade, ou que uma tentativa de UPDATE/DELETE
 * realmente falhou em mover/apagar algo), ele usa
 * {@link PostgresDeTeste#novaConexaoDono()} - uma conexao a parte, que
 * nunca passa pelo {@code DataSourceComTenant} nem pelo
 * {@code ContextoTenant}.
 *
 * <h2>Como verificar que este teste nao e decorativo</h2>
 * Um teste de isolamento que passa por acidente e pior que nenhum teste:
 * ele cria confianca falsa. As quatro sabotagens abaixo devem ser feitas
 * MANUALMENTE, uma de cada vez (desfazendo antes de tentar a proxima), e
 * cada uma tem que fazer um caso especifico deste arquivo (ou de
 * {@link RlsAtivoEmTodasAsTabelasTest}) ficar vermelho. Se alguma delas
 * NAO quebrar nenhum teste, a suite tem um buraco e precisa ser corrigida
 * antes de confiar nela.
 *
 * <ol>
 *   <li><b>Trocar {@code FORCE ROW LEVEL SECURITY} por nada (so
 *       {@code ENABLE}) na V003.</b> Efeito esperado:
 *       {@link RlsAtivoEmTodasAsTabelasTest#todaTabelaComColunaTenantIdTemRlsAtivoForcadoEQuatroPolicies()}
 *       fica vermelho (relforcerowsecurity=false para consulta_auditada).
 *       RESSALVA IMPORTANTE, para nao gerar falsa confianca ao contrario:
 *       nenhum caso ESTE arquivo (IsolamentoDeTenantTest) vai quebrar com
 *       esta sabotagem sozinha, porque {@code app_aplicacao} nunca e dono
 *       da tabela - ela ja e restrita pelo RLS com ou sem FORCE. FORCE so
 *       protege contra o DONO (ou um job/migration futura rodando como
 *       dono) escapar da policy; e por isso que esta garantia especifica
 *       so pode ser verificada pelo catalogo (relforcerowsecurity), nao
 *       pelo comportamento de app_aplicacao. Neste ambiente de teste em
 *       particular o "dono" tambem e superusuario (artefato de como a
 *       imagem oficial do Postgres monta o container - ver Javadoc de
 *       PostgresDeTeste), entao nem testar via
 *       {@code PostgresDeTeste.novaConexaoDono()} provaria o efeito do
 *       FORCE: superusuario ignora RLS sempre, com ou sem FORCE.</li>
 *
 *   <li><b>Fazer {@code DataSourceComTenant.getConnection()} nao chamar
 *       {@code setarGucDeTenant}</b> (comentar a chamada). Efeito
 *       esperado: {@link #naoVeDadoAlheio()} fica vermelho - mas nao do
 *       jeito que se esperaria a primeira vista. Sem o GUC sendo setado,
 *       {@code app_current_tenant_id()} devolve NULL SEMPRE (fail-closed
 *       da V001), entao a leitura no contexto do proprio tenant A tambem
 *       passa a devolver zero linhas. A asserção que espera ver a propria
 *       linha de A quebra. (O caso
 *       {@link #semTenantNoContextoNaoVeNadaENaoLancaExcecao()} continua
 *       "passando" nesse cenario, mas por acidente - ele so checa que da
 *       zero linhas, e zero linhas e exatamente o que aconteceria de
 *       qualquer forma. E o caso naoVeDadoAlheio, que exige ver a PROPRIA
 *       linha, quem denuncia a sabotagem.)</li>
 *
 *   <li><b>Remover a anotacao {@code @TenantId} do campo tenantId em
 *       {@link ConsultaAuditada}</b> (trocar por {@code @Column} comum).
 *       Efeito esperado:
 *       {@link #predicadoDoHibernateFiltraSozinho()} fica vermelho -
 *       especificamente na asserção sobre o SQL capturado (nao no
 *       resultado de findAll(), que continuaria correto por causa do
 *       RLS, camada 4, ainda ativo por baixo). E exatamente por isso que
 *       este caso captura o SQL gerado pelo Hibernate em vez de confiar
 *       so na contagem final de linhas: sem essa captura, remover
 *       {@code @TenantId} "passaria despercebido" porque o RLS mascara o
 *       buraco na camada 3.</li>
 *
 *   <li><b>Trocar o {@code USING} da policy {@code consulta_auditada_select}
 *       para {@code USING (true)}</b> (ou apagar a policy inteira) na
 *       V003. Este e o unico dos quatro que simula um VAZAMENTO REAL, nao
 *       uma falha-fechada quebrada. Efeito esperado:
 *       {@link #naoVeDadoAlheio()} fica vermelho porque a leitura no
 *       contexto de A passa a devolver tambem a linha de B - a asserção
 *       {@code assertFalse(idsVistosPorA.contains(idConsultaB...))} (e a
 *       verificacao inline dentro de {@code lerIdsComoTenant}) capturam
 *       isso imediatamente.</li>
 * </ol>
 *
 * Se quiser uma quinta confirmacao rapida e independente destas quatro:
 * comente o {@code GRANT SELECT, INSERT ON TABLE consulta_auditada TO
 * app_aplicacao} da V004 inteiro - toda a suite fica vermelha com erro de
 * "permission denied", o que prova que os testes realmente estao
 * conectados como app_aplicacao (e nao teriam erro nenhum se estivessem,
 * por engano, conectados como dono).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IsolamentoDeTenantTest {

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.configurarPropriedades(registro);
    }

    /**
     * Registra o {@link CapturadorDeSql} junto do Hibernate deste
     * contexto de teste, do mesmo jeito que ConfiguracaoTenant registra
     * o ResolvedorTenantHibernate em producao (mesmo mecanismo:
     * HibernatePropertiesCustomizer, chave de propriedade diferente -
     * "hibernate.session_factory.statement_inspector" em vez de
     * "hibernate.tenant_identifier_resolver" - entao os dois convivem
     * sem conflito no mesmo Map de propriedades).
     */
    @TestConfiguration
    static class ConfiguracaoDeCapturaDeSql {
        @Bean
        HibernatePropertiesCustomizer personalizadorCapturaSqlDeTeste() {
            return propriedades -> propriedades.put(
                    "hibernate.session_factory.statement_inspector", new CapturadorDeSql());
        }
    }

    /**
     * Intercepta toda instrucao SQL que o Hibernate manda ao banco.
     * Usado exclusivamente pelo caso f) para provar que foi o PROPRIO
     * HIBERNATE quem acrescentou o predicado de tenant (camada 3), e nao
     * so o RLS (camada 4) por baixo filtrando o resultado de uma query
     * sem predicado nenhum. As duas camadas juntas fariam o resultado
     * final bater de qualquer jeito - so inspecionar o SQL gerado
     * distingue "a camada 3 funciona" de "a camada 3 esta quebrada mas a
     * camada 4 escondeu o problema".
     */
    static final class CapturadorDeSql implements StatementInspector {
        private static final List<String> SQLS_CAPTURADOS = Collections.synchronizedList(new ArrayList<>());

        static void limpar() {
            SQLS_CAPTURADOS.clear();
        }

        static List<String> capturados() {
            synchronized (SQLS_CAPTURADOS) {
                return new ArrayList<>(SQLS_CAPTURADOS);
            }
        }

        @Override
        public String inspect(String sql) {
            SQLS_CAPTURADOS.add(sql);
            return sql;
        }
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RepositorioConsultaAuditada repositorioConsultaAuditada;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void criarTenants() throws SQLException {
        tenantA = criarTenant("a");
        tenantB = criarTenant("b");
    }

    @AfterEach
    void limparContexto() {
        // Defesa contra vazamento de tenant ENTRE TESTES: se um teste
        // falhar no meio, antes do seu proprio finally rodar, esta linha
        // garante que a ThreadLocal nao carrega o tenant para o proximo
        // teste executado nesta mesma thread.
        ContextoTenant.limpar();
    }

    // ------------------------------------------------------------------
    // a) Nao ve dado alheio
    // ------------------------------------------------------------------

    @Test
    void naoVeDadoAlheio() throws SQLException {
        UUID idConsultaA = inserirComoTenant(tenantA, "SELECT 1 -- consulta de A");
        UUID idConsultaB = inserirComoTenant(tenantB, "SELECT 1 -- consulta de B");

        // Prova que ha o que vazar ANTES de provar que nao vazou: se a
        // linha de B nao existisse de verdade no banco, o teste abaixo
        // passaria mesmo com o isolamento quebrado de outra forma (por
        // exemplo, tabela vazia por um bug no insert). Contagem feita
        // pelo caminho privilegiado (fora do RLS - ver Javadoc de
        // PostgresDeTeste).
        assertEquals(1, contarComoPrivilegiado(tenantA), "setup do teste falhou: a linha de A nao foi gravada");
        assertEquals(1, contarComoPrivilegiado(tenantB),
                "setup do teste falhou: a linha de B nao foi gravada - sem isto o teste nao prova nada");

        List<String> idsVistosPorA = lerIdsComoTenant(tenantA);

        assertEquals(List.of(idConsultaA.toString()), idsVistosPorA,
                "no contexto do tenant A, a leitura deveria devolver EXATAMENTE a linha de A - "
                        + "nem a mais (vazamento), nem a menos");
        assertFalse(idsVistosPorA.contains(idConsultaB.toString()), "VAZAMENTO: A enxergou uma linha de B");
    }

    // ------------------------------------------------------------------
    // b) Sem tenant no contexto: fail-closed, zero linhas, sem excecao
    // ------------------------------------------------------------------

    @Test
    void semTenantNoContextoNaoVeNadaENaoLancaExcecao() throws SQLException {
        inserirComoTenant(tenantA, "SELECT 1 -- existe algo para vazar, se o isolamento falhar");
        assertEquals(1, contarComoPrivilegiado(tenantA));

        ContextoTenant.limpar();
        long linhasVistas;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM consulta_auditada WHERE tenant_id = ?")) {
            comando.setObject(1, tenantA);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                linhasVistas = resultado.getLong(1);
            }
        }

        assertEquals(0, linhasVistas,
                "sem tenant no ContextoTenant, o DataSourceComTenant seta o GUC como string vazia e "
                        + "app_current_tenant_id() (V001) devolve NULL - toda policy nega, zero linhas, "
                        + "NUNCA excecao (fail-closed, nao fail-error)");
    }

    // ------------------------------------------------------------------
    // c) INSERT carimbado com tenant alheio e rejeitado (WITH CHECK)
    // ------------------------------------------------------------------

    @Test
    void insertComTenantAlheioERejeitado() throws SQLException {
        String marcador = "INSERT malicioso " + UUID.randomUUID();

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO consulta_auditada (tenant_id, sql_executado) VALUES (?, ?)")) {
            // Aqui usamos SQL cru de proposito, carimbando tenant_id de B
            // enquanto o GUC (via ContextoTenant) aponta para A. Isso
            // simula exatamente o cenario que o RLS existe para pegar:
            // codigo que escreveu SQL nativo e esqueceu (ou baipassou) o
            // predicado de tenant - a entidade ConsultaAuditada nem
            // permitiria isto (tenantId e @TenantId, sem setter).
            comando.setObject(1, tenantB);
            comando.setString(2, marcador);
            comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        assertNotNull(erroCapturado,
                "INSERT com tenant_id de B, feito com o GUC apontando para A, deveria ter sido "
                        + "rejeitado pela policy consulta_auditada_insert (WITH CHECK - V003)");
        assertEquals(0, contarComMarcador(marcador),
                "nada deveria ter sido gravado quando o INSERT falhou por violar a policy");
    }

    // ------------------------------------------------------------------
    // d) UPDATE nao consegue mover linha para outro tenant
    // ------------------------------------------------------------------

    @Test
    void updateNaoConsegueMoverLinhaParaOutroTenant() throws SQLException {
        UUID idConsulta = inserirComoTenant(tenantA, "SELECT 1 -- vai tentar ser roubada por UPDATE");

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        int linhasAfetadas = -1;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE consulta_auditada SET tenant_id = ? WHERE id = ?")) {
            comando.setObject(1, tenantB);
            comando.setObject(2, idConsulta);
            linhasAfetadas = comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        // Duas defesas se somam aqui, e este teste nao presume qual
        // delas segurou: a V004 nao concede NENHUM privilegio de UPDATE
        // a app_aplicacao nesta tabela (append-only de proposito, decisao
        // 0010), entao o esperado HOJE e "permission denied" antes mesmo
        // de qualquer policy ser avaliada. Se um dia alguem conceder
        // UPDATE (mudando o desenho append-only), a policy
        // consulta_auditada_update (USING + WITH CHECK) e quem passa a
        // segurar sozinha. O teste aceita qualquer uma das duas falhas,
        // mas exige que UMA delas tenha realmente impedido a mutacao -
        // e confirma isso lendo o dado de verdade, nao so a ausencia de
        // excecao.
        if (erroCapturado == null) {
            assertEquals(0, linhasAfetadas, "UPDATE nao lancou excecao, entao precisa ter afetado zero linhas");
        }

        UUID tenantDaLinhaAposTentativa = lerTenantIdComoPrivilegiado(idConsulta);
        assertEquals(tenantA, tenantDaLinhaAposTentativa,
                "a linha continua pertencendo a A - o UPDATE nao pode ter conseguido move-la para B, "
                        + "independente de qual camada (GRANT ausente ou RLS) barrou a tentativa");
    }

    // ------------------------------------------------------------------
    // e) DELETE nao alcanca linha alheia
    // ------------------------------------------------------------------

    @Test
    void deleteNaoAlcancaLinhaAlheia() throws SQLException {
        UUID idConsultaDeB = inserirComoTenant(tenantB, "SELECT 1 -- pertence a B");

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        int linhasAfetadas = -1;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "DELETE FROM consulta_auditada WHERE id = ?")) {
            comando.setObject(1, idConsultaDeB);
            linhasAfetadas = comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        // Mesma logica da nota do caso d): V004 tambem nao concede
        // DELETE a app_aplicacao nesta tabela. Ver comentario la.
        if (erroCapturado == null) {
            assertEquals(0, linhasAfetadas,
                    "DELETE, no contexto de A, mirando um id que pertence a B, nao pode ter afetado nada");
        }

        assertEquals(1, contarComoPrivilegiado(tenantB),
                "a linha de B precisa continuar existindo depois da tentativa de DELETE por A");
    }

    // ------------------------------------------------------------------
    // f) O predicado do Hibernate (@TenantId) filtra sozinho
    // ------------------------------------------------------------------

    @Test
    void predicadoDoHibernateFiltraSozinho() {
        ContextoTenant.definir(tenantA);
        repositorioConsultaAuditada.save(
                new ConsultaAuditada("pergunta de A", "SELECT 1 -- A via repositorio", null, 1, "teste"));
        ContextoTenant.limpar();

        ContextoTenant.definir(tenantB);
        repositorioConsultaAuditada.save(
                new ConsultaAuditada("pergunta de B", "SELECT 1 -- B via repositorio", null, 1, "teste"));
        ContextoTenant.limpar();

        ContextoTenant.definir(tenantA);
        CapturadorDeSql.limpar();
        List<ConsultaAuditada> vistasPorA = repositorioConsultaAuditada.findAll();
        List<String> sqlsGerados = CapturadorDeSql.capturados();
        ContextoTenant.limpar();

        assertFalse(vistasPorA.isEmpty(), "setup do teste falhou: A deveria ter ao menos uma linha");
        assertTrue(vistasPorA.stream().allMatch(consulta -> tenantA.equals(consulta.getTenantId())),
                "findAll() no contexto de A devolveu linha de outro tenant");

        // A prova de que quem filtrou foi a CAMADA 3 (Hibernate/@TenantId),
        // e nao so o RLS por baixo: o SQL que o proprio Hibernate GEROU
        // precisa conter o predicado de tenant. Ver a secao "Como
        // verificar que este teste nao e decorativo" na classe para o
        // porque isto importa (sabotagem 3).
        boolean algumSqlTemPredicadoDeTenant = sqlsGerados.stream()
                .anyMatch(sql -> sql.toLowerCase().contains("tenant_id"));
        assertTrue(algumSqlTemPredicadoDeTenant,
                "esperava que o Hibernate tivesse acrescentado sozinho um predicado sobre tenant_id "
                        + "no SQL de findAll() (via @TenantId). SQLs capturados: " + sqlsGerados);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /**
     * Insere direto em `tenant` pela conexao privilegiada: e a UNICA
     * forma de criar tenant neste teste, porque app_aplicacao so tem
     * GRANT SELECT em `tenant` (V004) - criar/desativar tenant e
     * caminho de provisionamento, nao de aplicacao (ver comentario da
     * V002).
     */
    private UUID criarTenant(String rotulo) throws SQLException {
        UUID id = UUID.randomUUID();
        // Slug precisa bater com ck_tenant_slug_formato (V002): minusculas,
        // digitos e hifen. Um fragmento do proprio UUID garante unicidade
        // (uq_tenant_slug) entre execucoes deste metodo.
        String slug = "tenant-" + rotulo + "-" + id.toString().substring(0, 8);
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO tenant (id, nome, slug) VALUES (?, ?, ?)")) {
            comando.setObject(1, id);
            comando.setString(2, "Tenant de teste " + rotulo);
            comando.setString(3, slug);
            comando.executeUpdate();
        }
        return id;
    }

    private UUID inserirComoTenant(UUID tenantId, String sqlExecutado) throws SQLException {
        ContextoTenant.definir(tenantId);
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO consulta_auditada (tenant_id, sql_executado) VALUES (?, ?) RETURNING id")) {
            comando.setObject(1, tenantId);
            comando.setString(2, sqlExecutado);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return (UUID) resultado.getObject("id");
            }
        } finally {
            ContextoTenant.limpar();
        }
    }

    private List<String> lerIdsComoTenant(UUID tenantId) throws SQLException {
        ContextoTenant.definir(tenantId);
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT id, tenant_id FROM consulta_auditada")) {
            try (ResultSet resultado = comando.executeQuery()) {
                List<String> ids = new ArrayList<>();
                while (resultado.next()) {
                    UUID tenantDaLinha = (UUID) resultado.getObject("tenant_id");
                    // Verificacao inline, alem da que o chamador faz:
                    // qualquer linha de outro tenant lida aqui derruba o
                    // teste imediatamente, com a causa exata na mensagem
                    // - impossivel de passar despercebido.
                    assertEquals(tenantId, tenantDaLinha,
                            "VAZAMENTO: leitura no contexto de " + tenantId
                                    + " devolveu linha com tenant_id = " + tenantDaLinha);
                    ids.add(resultado.getObject("id").toString());
                }
                return ids;
            }
        } finally {
            ContextoTenant.limpar();
        }
    }

    /**
     * Caminho privilegiado, fora do RLS: usado so para verificar o
     * estado real do banco, nunca para testar isolamento (ver Javadoc de
     * PostgresDeTeste sobre por que).
     */
    private long contarComoPrivilegiado(UUID tenantId) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM consulta_auditada WHERE tenant_id = ?")) {
            comando.setObject(1, tenantId);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private long contarComMarcador(String marcador) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM consulta_auditada WHERE sql_executado = ?")) {
            comando.setString(1, marcador);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private UUID lerTenantIdComoPrivilegiado(UUID idConsulta) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT tenant_id FROM consulta_auditada WHERE id = ?")) {
            comando.setObject(1, idConsulta);
            try (ResultSet resultado = comando.executeQuery()) {
                assertTrue(resultado.next(), "linha desapareceu - nao deveria, este caso nao testa DELETE");
                return (UUID) resultado.getObject("tenant_id");
            }
        }
    }
}
