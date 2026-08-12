package com.plataforma.comum.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.plataforma.suporte.PostgresDeTeste;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolamento de tenant COMPORTAMENTAL nas tabelas novas da Fase 1
 * (V005-V012). Antes deste arquivo, so {@code consulta_auditada} (V003)
 * tinha teste comportamental de isolamento - as doze tabelas novas so
 * tinham a verificacao ESTRUTURAL do catalogo em
 * {@link RlsAtivoEmTodasAsTabelasTest} (RLS ligado, FORCE ligado, quatro
 * policies existem). Estrutural prova que a "moldura" existe; nao prova
 * que ela FUNCIONA contra dado de verdade. Este arquivo prova o
 * comportamento em tres tabelas representativas: {@code canal} (a mais
 * simples, sem dependencia), {@code pedido} (depende de {@code canal} via
 * FK composta - decisao 0015) e {@code cliente} (a mais sensivel, LGPD -
 * V007).
 *
 * Mesma arquitetura de {@link IsolamentoDeTenantTest} (leia o Javadoc de
 * la primeiro, nao repito aqui): conecta como {@code app_aplicacao}
 * (nunca dono/superusuario) via o bean {@code DataSource} do proprio
 * contexto Spring (o {@code DataSourceComTenant} decorado - decisao 0007,
 * camada 4), simulando "estar dentro de uma requisicao daquele tenant"
 * atraves de {@link ContextoTenant}. {@link PostgresDeTeste#novaConexaoDono()}
 * so aparece para provar, "por fora", que uma linha existe de verdade ou
 * que ela nao mudou de tenant - nunca para testar se o isolamento
 * funciona.
 *
 * DIFERENCA IMPORTANTE em relacao ao caso d) de IsolamentoDeTenantTest:
 * {@code consulta_auditada} NAO tem GRANT UPDATE para app_aplicacao
 * (append-only, V003/V004), entao o teste de UPDATE de la e "protegido
 * pelo GRANT ausente, com o RLS como reforco nao comprovavel isoladamente".
 * Aqui e diferente: {@code canal}, {@code pedido} e {@code cliente} TEM
 * GRANT UPDATE (V005/V007/V008 - sao tabelas de negocio, nao append-only).
 * Isso significa que os testes de UPDATE deste arquivo exercitam de fato
 * a policy {@code *_update} (USING + WITH CHECK) como UNICA linha de
 * defesa - nao ha GRANT ausente para disfarcar uma policy quebrada.
 *
 * <h2>Como verificar que este teste nao e decorativo</h2>
 * Tres sabotagens manuais, uma de cada vez (desfazendo antes da proxima):
 * <ol>
 *   <li><b>Trocar o {@code WITH CHECK} de {@code pedido_insert} (V008)
 *       para {@code WITH CHECK (true)}.</b> Efeito esperado:
 *       {@link #pedidoInsertComTenantAlheioERejeitado()} fica vermelho -
 *       o INSERT carimbado com {@code tenant_id} de B, feito com o GUC em
 *       A, deixa de ser rejeitado, e a asserção
 *       {@code assertNotNull(erroCapturado)} falha. Os testes equivalentes
 *       de {@code canal} e {@code cliente} NAO quebram com esta sabotagem
 *       isolada - cada policy e independente; e por isso este arquivo
 *       repete o trio (ver/insert/update) para tres tabelas em vez de
 *       assumir que provar uma prova todas.</li>
 *   <li><b>Trocar o {@code USING} de {@code cliente_select} (V007) para
 *       {@code USING (true)}.</b> Este e o unico dos tres que simula um
 *       VAZAMENTO REAL de dado pessoal, nao uma falha-fechada quebrada.
 *       Efeito esperado: {@link #clienteNaoVeDadoAlheio()} fica vermelho -
 *       a leitura no contexto de A passa a devolver tambem a linha de B, e
 *       tanto a asserção final quanto a verificação inline dentro do
 *       helper {@code lerIdsComoTenant} (usado por
 *       {@link #clienteNaoVeDadoAlheio()} com {@code tabela = "cliente"})
 *       capturam isso imediatamente (mesmo padrao de
 *       {@code lerIdsComoTenant} em IsolamentoDeTenantTest).</li>
 *   <li><b>Remover o {@code WITH CHECK} de {@code canal_update} (V005),
 *       deixando so o {@code USING}.</b> Efeito esperado:
 *       {@link #canalUpdateNaoConsegueMoverLinhaEntreTenants()} fica
 *       vermelho - sem WITH CHECK, nada impede reescrever
 *       {@code tenant_id} da propria linha para B (a armadilha 3 da
 *       decisao 0010, citada no Javadoc de RlsAtivoEmTodasAsTabelasTest).
 *       A leitura final via {@code novaConexaoDono()} mostraria a linha
 *       agora pertencendo a B, e a asserção
 *       {@code assertEquals(tenantA, tenantDaLinhaAposTentativa)} falha.</li>
 * </ol>
 *
 * RESSALVA HONESTA: este arquivo NAO prova isolamento nas outras nove
 * tabelas da Fase 1 (produto, variacao, devolucao, item_devolucao, custo,
 * conversa, mensagem, evento_ingerido - este ultimo tem teste proprio de
 * comportamento indireto via {@code ServicoIngestaoTest}). Para elas,
 * ainda vale so a garantia ESTRUTURAL de RlsAtivoEmTodasAsTabelasTest
 * (policy existe, RLS ligado) - nao a comportamental (a policy FUNCIONA
 * contra dado real). Generalizar este padrao para as nove restantes fica
 * como trabalho futuro; ate la, ha uma lacuna real de cobertura ali, nao
 * escondida por este comentario. Tambem nao repito a garantia de
 * DELETE (ja coberta pelo padrao equivalente em IsolamentoDeTenantTest e
 * pela mesma policy generica do molde da decisao 0010) nem a garantia do
 * predicado do Hibernate (@TenantId) via SQL capturado - aquela already
 * provada uma vez, contra ConsultaAuditada, e o mecanismo (@TenantId) e o
 * MESMO em toda entidade deste pacote (Canal, Pedido, Cliente inclusos);
 * repetir a captura de SQL aqui provaria de novo o Hibernate, nao a tabela.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IsolamentoFaseUmTest {

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.configurarPropriedades(registro);
    }

    @Autowired
    private DataSource dataSource;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void criarTenants() throws SQLException {
        tenantA = criarTenant("fase1-a");
        tenantB = criarTenant("fase1-b");
    }

    @AfterEach
    void limparContexto() {
        // Mesma defesa contra vazamento de tenant ENTRE TESTES que
        // IsolamentoDeTenantTest usa - ver o comentario la.
        ContextoTenant.limpar();
    }

    // ==================================================================
    // canal
    // ==================================================================

    @Test
    void canalNaoVeDadoAlheio() throws SQLException {
        UUID idCanalA = inserirCanalComoTenant(tenantA, "marcador-canal-a-" + UUID.randomUUID());
        UUID idCanalB = inserirCanalComoTenant(tenantB, "marcador-canal-b-" + UUID.randomUUID());

        // Prova que ha o que vazar ANTES de provar que nao vazou (mesmo
        // racional de IsolamentoDeTenantTest#naoVeDadoAlheio).
        assertEquals(1, contarComoPrivilegiado("canal", tenantA), "setup falhou: canal de A nao foi gravado");
        assertEquals(1, contarComoPrivilegiado("canal", tenantB), "setup falhou: canal de B nao foi gravado");

        List<String> idsVistosPorA = lerIdsComoTenant("canal", tenantA);

        assertEquals(List.of(idCanalA.toString()), idsVistosPorA,
                "no contexto de A, a leitura de canal deveria devolver EXATAMENTE a linha de A");
        assertFalse(idsVistosPorA.contains(idCanalB.toString()), "VAZAMENTO: A enxergou um canal de B");
    }

    @Test
    void canalInsertComTenantAlheioERejeitado() throws SQLException {
        String marcador = "insert-malicioso-canal-" + UUID.randomUUID();

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO canal (tenant_id, codigo, nome, tipo, categoria) "
                                + "VALUES (?, ?, ?, 'MERCADO_LIVRE', 'MARKETPLACE')")) {
            // GUC (via ContextoTenant) aponta para A; tenant_id carimbado no
            // INSERT e de B. E exatamente o cenario que canal_insert (WITH
            // CHECK) existe para pegar.
            comando.setObject(1, tenantB);
            comando.setString(2, "canal-" + UUID.randomUUID().toString().substring(0, 8));
            comando.setString(3, marcador);
            comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        assertNotNull(erroCapturado,
                "INSERT em canal com tenant_id de B, GUC apontando para A, deveria ser rejeitado por canal_insert (V005)");
        assertEquals(0, contarComMarcador("canal", "nome", marcador),
                "nada deveria ter sido gravado quando o INSERT falhou por violar a policy");
    }

    @Test
    void canalUpdateNaoConsegueMoverLinhaEntreTenants() throws SQLException {
        UUID idCanal = inserirCanalComoTenant(tenantA, "canal-alvo-de-update-" + UUID.randomUUID());

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        int linhasAfetadas = -1;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE canal SET tenant_id = ? WHERE id = ?")) {
            comando.setObject(1, tenantB);
            comando.setObject(2, idCanal);
            linhasAfetadas = comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        // Diferente de consulta_auditada: canal TEM GRANT UPDATE (V005).
        // Quem tem que segurar aqui e SO a policy canal_update (USING +
        // WITH CHECK) - nao ha GRANT ausente para disfarcar.
        if (erroCapturado == null) {
            assertEquals(0, linhasAfetadas, "UPDATE nao lancou excecao, entao precisa ter afetado zero linhas");
        }

        UUID tenantDaLinhaAposTentativa = lerTenantIdComoPrivilegiado("canal", idCanal);
        assertEquals(tenantA, tenantDaLinhaAposTentativa,
                "o canal continua pertencendo a A - o UPDATE nao pode te-lo movido para B");
    }

    // ==================================================================
    // pedido (depende de canal - FK composta, decisao 0015)
    // ==================================================================

    @Test
    void pedidoNaoVeDadoAlheio() throws SQLException {
        UUID canalA = inserirCanalComoTenant(tenantA, "canal-para-pedido-a-" + UUID.randomUUID());
        UUID canalB = inserirCanalComoTenant(tenantB, "canal-para-pedido-b-" + UUID.randomUUID());
        UUID idPedidoA = inserirPedidoComoTenant(tenantA, canalA, "marcador-pedido-a-" + UUID.randomUUID());
        UUID idPedidoB = inserirPedidoComoTenant(tenantB, canalB, "marcador-pedido-b-" + UUID.randomUUID());

        assertEquals(1, contarComoPrivilegiado("pedido", tenantA), "setup falhou: pedido de A nao foi gravado");
        assertEquals(1, contarComoPrivilegiado("pedido", tenantB), "setup falhou: pedido de B nao foi gravado");

        List<String> idsVistosPorA = lerIdsComoTenant("pedido", tenantA);

        assertEquals(List.of(idPedidoA.toString()), idsVistosPorA,
                "no contexto de A, a leitura de pedido deveria devolver EXATAMENTE o pedido de A");
        assertFalse(idsVistosPorA.contains(idPedidoB.toString()), "VAZAMENTO: A enxergou um pedido de B");
    }

    @Test
    void pedidoInsertComTenantAlheioERejeitado() throws SQLException {
        // canalB e um par (tenant, id) VALIDO para a FK composta
        // fk_pedido_canal (decisao 0015) - de proposito: assim, se o
        // INSERT for rejeitado, a causa so pode ser a policy de RLS
        // (tenant_id do INSERT != GUC), nunca a FK (que passaria de
        // qualquer forma, ja que FK ignora RLS - ver docs/decisoes/0015).
        UUID canalB = inserirCanalComoTenant(tenantB, "canal-fk-valido-b-" + UUID.randomUUID());
        String marcador = "insert-malicioso-pedido-" + UUID.randomUUID();

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO pedido (tenant_id, canal_id, codigo_exibicao, status, feito_em) "
                                + "VALUES (?, ?, ?, 'PAGO', now())")) {
            comando.setObject(1, tenantB);
            comando.setObject(2, canalB);
            comando.setString(3, marcador);
            comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        assertNotNull(erroCapturado,
                "INSERT em pedido com tenant_id de B (e canal_id VALIDO de B), GUC apontando para A, "
                        + "deveria ser rejeitado por pedido_insert (V008) - a FK sozinha nao bastaria aqui");
        assertEquals(0, contarComMarcador("pedido", "codigo_exibicao", marcador),
                "nada deveria ter sido gravado quando o INSERT falhou por violar a policy");
    }

    @Test
    void pedidoUpdateNaoConsegueMoverLinhaEntreTenants() throws SQLException {
        UUID canalA = inserirCanalComoTenant(tenantA, "canal-para-update-pedido-a-" + UUID.randomUUID());
        UUID idPedido = inserirPedidoComoTenant(tenantA, canalA, "pedido-alvo-de-update-" + UUID.randomUUID());

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        int linhasAfetadas = -1;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE pedido SET tenant_id = ? WHERE id = ?")) {
            comando.setObject(1, tenantB);
            comando.setObject(2, idPedido);
            linhasAfetadas = comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        if (erroCapturado == null) {
            assertEquals(0, linhasAfetadas, "UPDATE nao lancou excecao, entao precisa ter afetado zero linhas");
        }

        UUID tenantDaLinhaAposTentativa = lerTenantIdComoPrivilegiado("pedido", idPedido);
        assertEquals(tenantA, tenantDaLinhaAposTentativa,
                "o pedido continua pertencendo a A - o UPDATE nao pode te-lo movido para B "
                        + "(mover um pedido de tenant corromperia faturamento de dois clientes ao mesmo tempo)");
    }

    // ==================================================================
    // cliente (LGPD - a tabela mais sensivel, V007)
    // ==================================================================

    @Test
    void clienteNaoVeDadoAlheio() throws SQLException {
        UUID idClienteA = inserirClienteComoTenant(tenantA, "marcador-cliente-a-" + UUID.randomUUID());
        UUID idClienteB = inserirClienteComoTenant(tenantB, "marcador-cliente-b-" + UUID.randomUUID());

        assertEquals(1, contarComoPrivilegiado("cliente", tenantA), "setup falhou: cliente de A nao foi gravado");
        assertEquals(1, contarComoPrivilegiado("cliente", tenantB), "setup falhou: cliente de B nao foi gravado");

        List<String> idsVistosPorA = lerIdsComoTenant("cliente", tenantA);

        assertEquals(List.of(idClienteA.toString()), idsVistosPorA,
                "no contexto de A, a leitura de cliente deveria devolver EXATAMENTE o cliente de A");
        assertFalse(idsVistosPorA.contains(idClienteB.toString()),
                "VAZAMENTO DE DADO PESSOAL: A enxergou um cliente de B");
    }

    @Test
    void clienteInsertComTenantAlheioERejeitado() throws SQLException {
        String marcador = "insert-malicioso-cliente-" + UUID.randomUUID();

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO cliente (tenant_id, nome) VALUES (?, ?)")) {
            comando.setObject(1, tenantB);
            comando.setString(2, marcador);
            comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        assertNotNull(erroCapturado,
                "INSERT em cliente com tenant_id de B, GUC apontando para A, deveria ser rejeitado por "
                        + "cliente_insert (V007)");
        assertEquals(0, contarComMarcador("cliente", "nome", marcador),
                "nada deveria ter sido gravado quando o INSERT falhou por violar a policy");
    }

    @Test
    void clienteUpdateNaoConsegueMoverLinhaEntreTenants() throws SQLException {
        UUID idCliente = inserirClienteComoTenant(tenantA, "cliente-alvo-de-update-" + UUID.randomUUID());

        ContextoTenant.definir(tenantA);
        SQLException erroCapturado = null;
        int linhasAfetadas = -1;
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE cliente SET tenant_id = ? WHERE id = ?")) {
            comando.setObject(1, tenantB);
            comando.setObject(2, idCliente);
            linhasAfetadas = comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        } finally {
            ContextoTenant.limpar();
        }

        if (erroCapturado == null) {
            assertEquals(0, linhasAfetadas, "UPDATE nao lancou excecao, entao precisa ter afetado zero linhas");
        }

        UUID tenantDaLinhaAposTentativa = lerTenantIdComoPrivilegiado("cliente", idCliente);
        assertEquals(tenantA, tenantDaLinhaAposTentativa,
                "o cliente continua pertencendo a A - mover dado pessoal de tenant seria vazamento de LGPD, "
                        + "nao so bug de faturamento");
    }

    // ------------------------------------------------------------------
    // Auxiliares - criacao de tenant (identico a IsolamentoDeTenantTest)
    // ------------------------------------------------------------------

    private UUID criarTenant(String rotulo) throws SQLException {
        UUID id = UUID.randomUUID();
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

    // ------------------------------------------------------------------
    // Auxiliares - insercao "legitima" (como o proprio tenant, via
    // DataSourceComTenant/RLS), usadas para montar dado real a ser lido
    // ou movido nos testes.
    // ------------------------------------------------------------------

    private UUID inserirCanalComoTenant(UUID tenantId, String nomeMarcador) throws SQLException {
        ContextoTenant.definir(tenantId);
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO canal (tenant_id, codigo, nome, tipo, categoria) "
                                + "VALUES (?, ?, ?, 'MERCADO_LIVRE', 'MARKETPLACE') RETURNING id")) {
            comando.setObject(1, tenantId);
            // codigo precisa bater com ck_canal_codigo_formato (V005) e ser
            // unico por tenant (uq_canal_codigo) - fragmento de UUID garante
            // as duas coisas entre chamadas.
            comando.setString(2, "canal-" + UUID.randomUUID().toString().substring(0, 8));
            comando.setString(3, nomeMarcador);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return (UUID) resultado.getObject("id");
            }
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID inserirPedidoComoTenant(UUID tenantId, UUID canalId, String marcadorCodigoExibicao) throws SQLException {
        ContextoTenant.definir(tenantId);
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO pedido (tenant_id, canal_id, codigo_exibicao, status, feito_em) "
                                + "VALUES (?, ?, ?, 'PAGO', now()) RETURNING id")) {
            comando.setObject(1, tenantId);
            comando.setObject(2, canalId);
            comando.setString(3, marcadorCodigoExibicao);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return (UUID) resultado.getObject("id");
            }
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID inserirClienteComoTenant(UUID tenantId, String nomeMarcador) throws SQLException {
        ContextoTenant.definir(tenantId);
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO cliente (tenant_id, nome) VALUES (?, ?) RETURNING id")) {
            comando.setObject(1, tenantId);
            comando.setString(2, nomeMarcador);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return (UUID) resultado.getObject("id");
            }
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares - leitura como um tenant especifico (exercita RLS via
    // DataSourceComTenant), mesma tecnica de
    // IsolamentoDeTenantTest#lerIdsComoTenant.
    // ------------------------------------------------------------------

    private List<String> lerIdsComoTenant(String tabela, UUID tenantId) throws SQLException {
        ContextoTenant.definir(tenantId);
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT id, tenant_id FROM " + tabela)) {
            try (ResultSet resultado = comando.executeQuery()) {
                List<String> ids = new ArrayList<>();
                while (resultado.next()) {
                    UUID tenantDaLinha = (UUID) resultado.getObject("tenant_id");
                    // Verificacao inline: qualquer linha de outro tenant
                    // lida aqui derruba o teste imediatamente, com a causa
                    // exata na mensagem - impossivel de passar despercebido.
                    assertEquals(tenantId, tenantDaLinha,
                            "VAZAMENTO em " + tabela + ": leitura no contexto de " + tenantId
                                    + " devolveu linha com tenant_id = " + tenantDaLinha);
                    ids.add(resultado.getObject("id").toString());
                }
                return ids;
            }
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares - caminho privilegiado (fora do RLS), so para verificar
    // o estado real do banco - nunca para testar isolamento. Ver Javadoc
    // de PostgresDeTeste.
    // ------------------------------------------------------------------

    private long contarComoPrivilegiado(String tabela, UUID tenantId) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM " + tabela + " WHERE tenant_id = ?")) {
            comando.setObject(1, tenantId);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private long contarComMarcador(String tabela, String coluna, String marcador) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM " + tabela + " WHERE " + coluna + " = ?")) {
            comando.setString(1, marcador);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private UUID lerTenantIdComoPrivilegiado(String tabela, UUID id) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT tenant_id FROM " + tabela + " WHERE id = ?")) {
            comando.setObject(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                assertTrue(resultado.next(), "linha desapareceu de " + tabela + " - nao deveria, este caso nao testa DELETE");
                return (UUID) resultado.getObject("tenant_id");
            }
        }
    }
}
