package com.plataforma.ingestao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.plataforma.canal.Canal;
import com.plataforma.canal.CategoriaCanal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.RepositorioPedido;
import com.plataforma.suporte.LeitorDeFixture;
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
 * Testes do pipeline de ingestao (tarefa 12) contra Postgres de verdade
 * (decisao 0008) - idempotencia so pode ser provada com o banco real
 * executando o UPSERT (V012) de verdade; mock esconderia exatamente a
 * corrida que a receita da V012 existe para eliminar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ServicoIngestaoTest {

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.configurarPropriedades(registro);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ServicoIngestao servicoIngestao;

    @Autowired
    private RepositorioCanal repositorioCanal;

    @Autowired
    private RepositorioPedido repositorioPedido;

    @Autowired
    private RepositorioEventoIngerido repositorioEventoIngerido;

    @AfterEach
    void limparContexto() {
        // Defesa contra vazamento de tenant entre testes, mesmo funcao do
        // AfterEach de IsolamentoDeTenantTest.
        ContextoTenant.limpar();
    }

    @Test
    void ingerirDuasVezesComOMesmoPayloadCriaUmPedidoEUmEvento() throws SQLException {
        UUID tenantId = criarTenant("ingestao-a");
        UUID canalId = criarCanalMercadoLivre(tenantId);
        String payload = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-completo.json");

        ContextoTenant.definir(tenantId);
        try {
            ResultadoIngestao primeiraVez = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "2000003508649999", payload);
            assertTrue(primeiraVez.processado(), "evento novo tem que ser processado (caso 1 do cabecalho da V012)");
            assertNotNull(primeiraVez.idPedido());

            ResultadoIngestao segundaVez = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "2000003508649999", payload);
            assertFalse(segundaVez.processado(), "reenvio identico tem que ser no-op (caso 2 do cabecalho da V012)");

            assertEquals(1, repositorioPedido.count(), "reenvio identico nao pode duplicar pedido");
            assertEquals(1, repositorioEventoIngerido.count(), "reenvio identico nao pode duplicar evento");
        } finally {
            ContextoTenant.limpar();
        }
    }

    @Test
    void reingestaoComPayloadDiferenteMesmaChaveNaturalNaoDuplicaPedido() throws SQLException {
        UUID tenantId = criarTenant("ingestao-b");
        UUID canalId = criarCanalMercadoLivre(tenantId);
        String payloadOriginal = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-completo.json");
        String payloadAlterado = comCampoAlterado(payloadOriginal, "comment", "atualizado no reprocessamento");

        ContextoTenant.definir(tenantId);
        try {
            ResultadoIngestao primeiraVez = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "2000003508649999", payloadOriginal);
            assertTrue(primeiraVez.processado());

            ResultadoIngestao comHashDiferente = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "2000003508649999", payloadAlterado);
            // Caso 3 do cabecalho da V012: hash mudou, o EVENTO reprocessa
            // (status volta para RECEBIDO, novo payload_bruto gravado)...
            assertTrue(comHashDiferente.processado());
            // ...mas a segunda trava (uq_pedido_origem, V008) impede um
            // SEGUNDO pedido: Pedido e imutavel por desenho nesta rodada
            // (ver javadoc de ServicoIngestao.persistirResultado), entao o
            // id devolvido e o MESMO pedido de antes, nunca um novo.
            assertEquals(primeiraVez.idPedido(), comHashDiferente.idPedido());

            assertEquals(1, repositorioPedido.count(), "reprocessar com a mesma chave natural NUNCA duplica pedido");
            assertEquals(1, repositorioEventoIngerido.count(), "mesma chave natural de evento - e um UPDATE, nao um INSERT novo");
        } finally {
            ContextoTenant.limpar();
        }
    }

    @Test
    void tenantEPropagadoCorretamenteAoLongoDoFluxo() throws SQLException {
        UUID tenantId = criarTenant("ingestao-c");
        UUID canalId = criarCanalMercadoLivre(tenantId);
        String payload = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-cancelado.json");

        ContextoTenant.definir(tenantId);
        ResultadoIngestao resultado;
        try {
            resultado = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "2000003508650321", payload);
        } finally {
            ContextoTenant.limpar();
        }
        assertTrue(resultado.processado());

        ContextoTenant.definir(tenantId);
        try {
            Optional<Pedido> pedido = repositorioPedido.findById(resultado.idPedido());
            assertTrue(pedido.isPresent(), "pedido gravado com o tenant do contexto tem que ser visivel de volta no mesmo contexto");
            assertEquals(tenantId, pedido.get().getTenantId());
            assertEquals(canalId, pedido.get().getCanalId());
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /**
     * Mesma tecnica de IsolamentoDeTenantTest: unica forma de inserir em
     * `tenant` e pela conexao privilegiada, ja que app_aplicacao so tem
     * GRANT SELECT nessa tabela (V004).
     */
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

    private UUID criarCanalMercadoLivre(UUID tenantId) {
        ContextoTenant.definir(tenantId);
        try {
            Canal canal = repositorioCanal.save(new Canal("ml-teste", "Mercado Livre Teste",
                    TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, "123456789", null, null));
            return canal.getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private static String comCampoAlterado(String payloadJson, String campo, String novoValor) {
        try {
            JsonNode raiz = MAPPER.readTree(payloadJson);
            ((ObjectNode) raiz).put(campo, novoValor);
            return MAPPER.writeValueAsString(raiz);
        } catch (Exception erro) {
            throw new IllegalStateException("Falha alterando fixture para teste", erro);
        }
    }
}
