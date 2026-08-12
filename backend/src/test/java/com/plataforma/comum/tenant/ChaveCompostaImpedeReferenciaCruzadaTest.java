package com.plataforma.comum.tenant;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.suporte.PostgresDeTeste;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prova em runtime a garantia central da decisao 0015 ("chave estrangeira
 * composta carregando tenant_id"): o banco RECUSA FISICAMENTE uma FK que
 * atravessa tenants, mesmo quando quem escreve e o proprio DONO do schema
 * - sem passar por RLS, sem passar pela aplicacao, sem depender de
 * nenhuma disciplina humana. Antes deste arquivo, a decisao 0015 nunca
 * tinha sido exercitada por nenhum teste: {@link RlsAtivoEmTodasAsTabelasTest}
 * so verifica RLS (policies, FORCE), e {@link IsolamentoDeTenantTest} /
 * {@link IsolamentoFaseUmTest} testam a mesma coisa que RLS protege
 * (visibilidade e escrita DENTRO da propria tabela), nunca a integridade
 * REFERENCIAL entre duas tabelas. A decisao 0015 e explicita sobre o
 * porque isso importa: "RLS filtra leitura, nao valida integridade
 * referencial. Uma escrita com o canal_id errado passa, e o erro so
 * aparece muito depois, como numero errado num relatorio". ESTE arquivo e
 * quem prova que essa escrita, na verdade, NAO passa.
 *
 * <h2>Por que a conexao e sempre a de DONO, nunca app_aplicacao</h2>
 * Todo INSERT aqui roda contra {@link PostgresDeTeste#novaConexaoDono()}
 * de proposito: o dono e superusuario neste ambiente de teste (ver Javadoc
 * de PostgresDeTeste) e portanto BYPASSA RLS inteiramente. Se o INSERT
 * malicioso ainda assim falhar, a UNICA causa possivel e a restraint de
 * FOREIGN KEY em si - nunca uma policy de RLS que por acaso tambem
 * rejeitaria a mesma linha por outro motivo (o que aconteceria se este
 * teste usasse a conexao de app_aplicacao com o GUC de tenant errado, por
 * exemplo). Isso e o que faz este teste provar especificamente a FK
 * composta, e nao alguma outra camada de defesa.
 *
 * Cada teste confere o {@code SQLState} da excecao (23503 -
 * {@code foreign_key_violation}), nao so "alguma excecao aconteceu": uma
 * asserção que aceitasse qualquer SQLException deixaria passar despercebido,
 * por exemplo, um NOT NULL quebrado por engano na propria escrita do teste
 * - o que provaria bug no teste, nao a garantia da 0015.
 *
 * <h2>Como verificar que este teste nao e decorativo</h2>
 * Duas sabotagens diretas no schema, uma de cada vez:
 * <ol>
 *   <li><b>Em V008, trocar a FK composta de pedido para canal por uma FK
 *       simples</b> - de
 *       {@code FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id)}
 *       para {@code FOREIGN KEY (canal_id) REFERENCES canal (id)} (canal.id
 *       sozinho ja e PK, unico no banco inteiro - a FK simples "funciona"
 *       do ponto de vista do Postgres, so nao impede a referencia cruzada).
 *       Efeito esperado:
 *       {@link #pedidoDoTenantANaoPodeReferenciarCanalDeTenantB()} fica
 *       vermelho - o INSERT que devia ser rejeitado passa silenciosamente,
 *       {@code erroCapturado} fica {@code null}, e
 *       {@code assertNotNull(erroCapturado)} falha.</li>
 *   <li><b>Em V008, trocar {@code fk_item_pedido_pedido} de composta
 *       para simples</b> ({@code FOREIGN KEY (pedido_id) REFERENCES pedido (id)}).
 *       Efeito esperado:
 *       {@link #itemPedidoDoTenantANaoPodeReferenciarPedidoDeTenantB()}
 *       fica vermelho, pela mesma razao do item acima.</li>
 * </ol>
 *
 * RESSALVA HONESTA: este arquivo exercita SO DUAS das varias FKs
 * compostas introduzidas na Fase 1 pela decisao 0015 (pedido -> canal, e
 * item_pedido -> pedido). Existem outras (pedido -> cliente, item_pedido
 * -> variacao, cliente -> canal, custo -> pedido/item_pedido, devolucao ->
 * pedido, item_devolucao -> devolucao/item_pedido, conversa -> canal/
 * cliente, mensagem -> conversa, evento_ingerido -> canal) que este
 * arquivo NAO toca. Ele prova que o PADRAO funciona e e exercitavel onde
 * foi testado; nao prova que toda FK composta do schema foi de fato
 * declarada corretamente - isso ainda depende de revisao humana da
 * migration (ou de generalizar este padrao, tarefa futura, no mesmo
 * espirito de RlsAtivoEmTodasAsTabelasTest ser generico por
 * information_schema em vez de uma lista fixa).
 */
class ChaveCompostaImpedeReferenciaCruzadaTest {

    private static final String SQLSTATE_VIOLACAO_DE_CHAVE_ESTRANGEIRA = "23503";

    @Test
    void pedidoDoTenantANaoPodeReferenciarCanalDeTenantB() throws SQLException {
        UUID tenantA = criarTenant("fk-pedido-a");
        UUID tenantB = criarTenant("fk-pedido-b");
        UUID canalDeB = criarCanal(tenantB, "canal-de-b-para-fk");
        String marcador = "pedido-malicioso-" + UUID.randomUUID();

        SQLException erroCapturado = null;
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO pedido (tenant_id, canal_id, codigo_exibicao, status, feito_em) "
                                + "VALUES (?, ?, ?, 'PAGO', now())")) {
            // tenant_id = A, mas canal_id pertence de verdade a B (par
            // (B, canalDeB) existe em canal; o par (A, canalDeB) nao).
            comando.setObject(1, tenantA);
            comando.setObject(2, canalDeB);
            comando.setString(3, marcador);
            comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        }

        assertNotNull(erroCapturado,
                "um pedido do tenant A apontando para um canal do tenant B deveria ser recusado pela FK "
                        + "composta fk_pedido_canal (decisao 0015), mesmo escrevendo como DONO (sem RLS no meio)");
        assertEquals(SQLSTATE_VIOLACAO_DE_CHAVE_ESTRANGEIRA, erroCapturado.getSQLState(),
                "a excecao capturada precisa ser especificamente violacao de FK (23503) - qualquer outro "
                        + "SQLState indicaria que algo diferente barrou o INSERT, nao a garantia da 0015 "
                        + "que este teste existe para provar. Erro real: " + erroCapturado);
        assertEquals(0, contarPedidoComMarcador(marcador),
                "nenhuma linha deveria ter sido gravada quando o INSERT violou a FK composta");
    }

    @Test
    void itemPedidoDoTenantANaoPodeReferenciarPedidoDeTenantB() throws SQLException {
        UUID tenantA = criarTenant("fk-item-a");
        UUID tenantB = criarTenant("fk-item-b");
        UUID canalDeB = criarCanal(tenantB, "canal-de-b-para-item-fk");
        UUID pedidoDeB = criarPedido(tenantB, canalDeB, "pedido-de-b-para-item-fk");
        String marcador = "item-pedido-malicioso-" + UUID.randomUUID();

        SQLException erroCapturado = null;
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO item_pedido "
                                + "(tenant_id, pedido_id, titulo_origem, quantidade, valor_unitario_bruto, valor_total_linha) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            // tenant_id = A, mas pedido_id pertence de verdade a B (par
            // (B, pedidoDeB) existe em pedido; o par (A, pedidoDeB) nao).
            comando.setObject(1, tenantA);
            comando.setObject(2, pedidoDeB);
            comando.setString(3, marcador);
            comando.setBigDecimal(4, new BigDecimal("1"));
            comando.setBigDecimal(5, new BigDecimal("10.00"));
            comando.setBigDecimal(6, new BigDecimal("10.00"));
            comando.executeUpdate();
        } catch (SQLException erro) {
            erroCapturado = erro;
        }

        assertNotNull(erroCapturado,
                "um item_pedido do tenant A apontando para um pedido do tenant B deveria ser recusado pela "
                        + "FK composta fk_item_pedido_pedido (decisao 0015), mesmo escrevendo como DONO");
        assertEquals(SQLSTATE_VIOLACAO_DE_CHAVE_ESTRANGEIRA, erroCapturado.getSQLState(),
                "a excecao capturada precisa ser especificamente violacao de FK (23503). Erro real: " + erroCapturado);
        assertEquals(0, contarItemPedidoComMarcador(marcador),
                "nenhuma linha deveria ter sido gravada quando o INSERT violou a FK composta");
    }

    // ------------------------------------------------------------------
    // Auxiliares - tudo via conexao de DONO, de proposito (ver Javadoc da
    // classe): nao estamos testando RLS aqui, so integridade referencial.
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

    private UUID criarCanal(UUID tenantId, String rotuloCodigo) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO canal (tenant_id, codigo, nome, tipo, categoria) "
                                + "VALUES (?, ?, ?, 'MERCADO_LIVRE', 'MARKETPLACE') RETURNING id")) {
            comando.setObject(1, tenantId);
            comando.setString(2, "canal-" + UUID.randomUUID().toString().substring(0, 8));
            comando.setString(3, rotuloCodigo);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return (UUID) resultado.getObject("id");
            }
        }
    }

    private UUID criarPedido(UUID tenantId, UUID canalId, String marcadorCodigoExibicao) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
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
        }
    }

    private long contarPedidoComMarcador(String marcador) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM pedido WHERE codigo_exibicao = ?")) {
            comando.setString(1, marcador);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private long contarItemPedidoComMarcador(String marcador) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM item_pedido WHERE titulo_origem = ?")) {
            comando.setString(1, marcador);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }
}
