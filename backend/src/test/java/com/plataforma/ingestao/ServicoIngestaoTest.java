package com.plataforma.ingestao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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
import com.plataforma.catalogo.Produto;
import com.plataforma.catalogo.RepositorioProduto;
import com.plataforma.catalogo.RepositorioVariacao;
import com.plataforma.catalogo.Variacao;
import com.plataforma.cliente.RepositorioCliente;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.integracao.PayloadInvalidoException;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.RepositorioItemPedido;
import com.plataforma.pedido.RepositorioPedido;
import com.plataforma.pedido.StatusPedido;
import com.plataforma.suporte.LeitorDeFixture;
import com.plataforma.suporte.PostgresDeTeste;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Autowired
    private RepositorioCliente repositorioCliente;

    @Autowired
    private RepositorioItemPedido repositorioItemPedido;

    @Autowired
    private RepositorioProduto repositorioProduto;

    @Autowired
    private RepositorioVariacao repositorioVariacao;

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
            // Antes desta linha, so repositorioPedido.count() era conferido -
            // se o dedup de CLIENTE (uq_cliente_origem, V007, via
            // persistirClienteSeNovo) quebrasse, nada acusava. buyer.id
            // ("987654321") e constante na fixture, entao o segundo
            // ingerir() teria que reconhecer o MESMO comprador, nunca criar
            // um segundo.
            assertEquals(1, repositorioCliente.count(), "reenvio identico nao pode duplicar cliente");
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
            // ...e a segunda trava (uq_pedido_origem, V008) impede um
            // SEGUNDO pedido: o id devolvido e o MESMO pedido de antes -
            // mas, desde a dívida 1 (docs/ESTADO.md), esse pedido existente
            // agora e ATUALIZADO com os campos do payload reprocessado
            // (Pedido.atualizarAPartirDaOrigem), nao mais ignorado. Como o
            // unico campo alterado nesta fixture ('comment') nao e mapeado
            // para nenhuma coluna canonica, nenhum valor visivel muda -
            // mas a identidade da linha (id) e preservada de qualquer forma.
            assertEquals(primeiraVez.idPedido(), comHashDiferente.idPedido());

            assertEquals(1, repositorioPedido.count(), "reprocessar com a mesma chave natural NUNCA duplica pedido");
            assertEquals(1, repositorioEventoIngerido.count(), "mesma chave natural de evento - e um UPDATE, nao um INSERT novo");
            // Mesmo racional da nota no teste de reenvio identico: sem esta
            // linha, uma quebra no dedup de cliente (buyer.id continua
            // "987654321" mesmo no payload alterado, so 'comment' mudou)
            // passaria despercebida.
            assertEquals(1, repositorioCliente.count(), "reprocessar com hash diferente e mesma chave natural nao pode duplicar cliente");
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
    // Canal de outro tenant: rejeitado ANTES de qualquer escrita
    // ------------------------------------------------------------------

    @Test
    void ingerirComCanalDeOutroTenantLancaCanalDesconhecidoENadaEhGravado() throws SQLException {
        UUID tenantA = criarTenant("ingestao-canal-alheio-a");
        UUID tenantB = criarTenant("ingestao-canal-alheio-b");
        UUID canalDeB = criarCanalMercadoLivre(tenantB);
        String idExterno = "id-externo-canal-alheio-" + UUID.randomUUID();
        String payload = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-completo.json");

        ContextoTenant.definir(tenantA);
        try {
            // A consulta que busca o canal (repositorioCanal.findById, dentro
            // de ServicoIngestao.ingerir) ja e restrita ao tenant pelo
            // @TenantId (decisao 0007, camada 3) - por isso um canalId que
            // existe de verdade, mas pertence a B, precisa "nao ser
            // encontrado" no contexto de A. Ver Javadoc de
            // CanalDesconhecidoException sobre por que "nao existe" e "e de
            // outro tenant" sao a MESMA excecao de proposito.
            assertThrows(CanalDesconhecidoException.class,
                    () -> servicoIngestao.ingerir(canalDeB, TipoEvento.PEDIDO, idExterno, payload),
                    "canal de outro tenant tem que ser tratado como inexistente");
        } finally {
            ContextoTenant.limpar();
        }

        // Confirmado pela conexao de DONO (fora do RLS, fora do @TenantId):
        // nao basta o app nao ENXERGAR nada no contexto de A - nada pode ter
        // sido fisicamente gravado em NENHUM lugar do banco. A validacao do
        // canal acontece antes do upsert de evento_ingerido (comentario de
        // ServicoIngestao.ingerir: "VALIDACAO DO CANAL ANTES DE QUALQUER
        // ESCRITA"), entao os dois contadores abaixo tem que ser zero.
        assertEquals(0, contarEventoIngeridoComoPrivilegiado(canalDeB, idExterno),
                "nada deveria ter sido gravado em evento_ingerido - a validacao de canal acontece ANTES do upsert");
        assertEquals(0, contarPedidoComoPrivilegiado(canalDeB, idExterno),
                "nada deveria ter sido gravado em pedido - a excecao interrompe o fluxo antes de qualquer escrita");
    }

    // ------------------------------------------------------------------
    // Backstop de corrida: uq_pedido_origem pega o que o
    // findByCanalIdAndIdExterno eventualmente deixasse passar
    // ------------------------------------------------------------------

    /**
     * Simula (sem concorrencia real) o cenario que o bloco
     * {@code catch (DataIntegrityViolationException)} de
     * {@code ServicoIngestao.persistirResultado} existe para segurar:
     * chegar na hora de inserir o pedido com uma linha JA existente para a
     * mesma chave natural (tenant_id, canal_id, id_externo).
     *
     * LIMITACAO HONESTA, registrada em vez de escondida: pre-inserir a
     * linha conflitante ANTES de chamar {@code ingerir()} e chamar em
     * seguida, em sequencia, sem threads reais, faz essa linha estar
     * COMMITADA E VISIVEL quando {@code persistirResultado} roda o SELECT
     * de {@code repositorioPedido.findByCanalIdAndIdExterno} (READ
     * COMMITTED: cada instrucao ve tudo que ja foi commitado antes dela
     * comecar, nao importa a conexao). Ou seja, o caminho mais provavel
     * REALMENTE exercitado por este teste e a PRIMEIRA trava (o
     * {@code if (pedidoExistente.isPresent())}), nao literalmente a linha
     * do {@code catch} - forcar exatamente o catch exigiria concorrencia
     * de verdade (duas conexoes, uma com transacao aberta e nao commitada
     * enquanto a outra tenta inserir e bloqueia na unique index), que a
     * tarefa que originou este teste marcou como desnecessaria. O que este
     * teste PROVA com certeza, e que vale independente de qual das duas
     * travas segurou: reingerir um payload cuja chave natural ja existe
     * fisicamente no banco nunca propaga excecao e nunca duplica o pedido -
     * o comportamento OBSERVAVEL que as duas defesas prometem entregar.
     */
    @Test
    void ingerirComPedidoJaExistenteNaChaveNaturalNaoDuplicaNemPropagaExcecao() throws SQLException {
        UUID tenantId = criarTenant("ingestao-corrida");
        UUID canalId = criarCanalMercadoLivre(tenantId);
        String payload = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-completo.json");
        // id_externo do PEDIDO propriamente dito - vem do campo "id" do
        // payload (ver AdaptadorMercadoLivre.traduzirPedido), NAO do
        // parametro idExterno de ingerir() (que so serve para a chave de
        // evento_ingerido - as duas coisas sao independentes, por isso
        // usamos um idExterno de evento diferente abaixo).
        String idExternoDoPedidoNoPayload = "2000003508649999";

        UUID idPedidoPreExistente = inserirPedidoDiretoComoDono(tenantId, canalId, idExternoDoPedidoNoPayload);

        ContextoTenant.definir(tenantId);
        ResultadoIngestao resultado;
        try {
            resultado = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "evento-corrida-" + UUID.randomUUID(), payload);
        } finally {
            ContextoTenant.limpar();
        }

        assertTrue(resultado.processado());
        assertEquals(idPedidoPreExistente, resultado.idPedido(),
                "quando ja existe pedido com a mesma chave natural, o resultado tem que apontar para ELE, "
                        + "nunca para um pedido novo");
        assertEquals(1, repositorioPedido.count(),
                "nao pode ter sido criado um segundo pedido para a mesma chave natural (tenant, canal, id_externo)");
    }

    // ------------------------------------------------------------------
    // Dívida 1 (docs/ESTADO.md): reprocessar com status novo ATUALIZA o
    // pedido existente, em vez de so auditar em evento_ingerido.
    // ------------------------------------------------------------------

    @Test
    void reprocessarPedidoComStatusNovoAtualizaOStatusDoPedidoExistente() throws SQLException {
        UUID tenantId = criarTenant("ingestao-status");
        UUID canalId = criarCanalMercadoLivre(tenantId);
        String payloadPago = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-completo.json");
        // Mesmo id_externo de PEDIDO (o "id" dentro do payload nao muda),
        // status_origem trocado para algo que o adaptador ainda nao
        // mapeia -> cai no neutro AGUARDANDO_PAGAMENTO (ver
        // AdaptadorMercadoLivre.traduzirStatus). Simula o primeiro
        // webhook do ciclo de vida do pedido, antes do pagamento.
        String payloadAindaNaoPago = comCampoAlterado(payloadPago, "status", "in_process");

        ContextoTenant.definir(tenantId);
        try {
            ResultadoIngestao primeiraVez = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "evt-status-1", payloadAindaNaoPago);
            assertTrue(primeiraVez.processado());
            Pedido pedidoAntes = repositorioPedido.findById(primeiraVez.idPedido()).orElseThrow();
            assertEquals(StatusPedido.AGUARDANDO_PAGAMENTO, pedidoAntes.getStatus());

            // Segundo webhook, MESMO pedido (mesmo "id" no payload), agora
            // com status_origem="paid" - simula o pagamento sendo
            // confirmado depois. idExterno do EVENTO e outro de proposito:
            // sao dois eventos distintos (dois webhooks) sobre o MESMO
            // pedido, exatamente o cenario que motivou a dívida 1.
            ResultadoIngestao segundaVez = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "evt-status-2", payloadPago);
            assertTrue(segundaVez.processado());
            assertEquals(primeiraVez.idPedido(), segundaVez.idPedido(),
                    "mesma chave natural de pedido (canal_id + id_externo do PAYLOAD) - tem que ser o MESMO pedido");

            Pedido pedidoDepois = repositorioPedido.findById(segundaVez.idPedido()).orElseThrow();
            assertEquals(StatusPedido.PAGO, pedidoDepois.getStatus(),
                    "dívida 1: reprocessar com status novo tem que ATUALIZAR o pedido existente");
            assertEquals(1, repositorioPedido.count(), "atualizar status nunca cria um segundo pedido");
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ------------------------------------------------------------------
    // Dívida 2 (docs/ESTADO.md): falha de traducao grava status=ERRO,
    // sem apagar o evento e sem vazar payload em erro_mensagem.
    // ------------------------------------------------------------------

    @Test
    void falhaDeTraducaoPersisteEventoComStatusErroSemVazarPayloadNaMensagem() throws SQLException {
        UUID tenantId = criarTenant("ingestao-erro");
        UUID canalId = criarCanalMercadoLivre(tenantId);
        // Payload sem 'id': AdaptadorMercadoLivre.traduzirPedido lanca
        // PayloadInvalidoException antes de qualquer outra coisa (nao ha
        // chave de idempotencia possivel sem id do pedido).
        String payloadSemId = "{\"date_created\": \"2024-01-10T10:00:00.000-04:00\"}";
        String idExternoDoEvento = "evt-erro-" + UUID.randomUUID();

        ContextoTenant.definir(tenantId);
        try {
            assertThrows(PayloadInvalidoException.class,
                    () -> servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, idExternoDoEvento, payloadSemId),
                    "quem chama ingerir() continua vendo a falha - gravar o erro nao pode virar sucesso silencioso");

            EventoIngerido evento = repositorioEventoIngerido
                    .findByCanalIdAndTipoEventoAndIdExterno(canalId, TipoEvento.PEDIDO, idExternoDoEvento)
                    .orElseThrow(() -> new AssertionError(
                            "evento_ingerido tem que SOBREVIVER a uma falha de traducao (dívida 2) - "
                                    + "sem isso o reenvio identico tenta para sempre, invisivel"));

            assertEquals(StatusEventoIngerido.ERRO, evento.getStatus());
            assertNotNull(evento.getErroMensagem(), "erro_mensagem tem que estar preenchida quando status=ERRO");
            assertTrue(evento.getErroMensagem().contains("PayloadInvalidoException"),
                    "erro_mensagem guarda o TIPO do erro (regra da dívida 2)");
            assertFalse(evento.getErroMensagem().contains(payloadSemId),
                    "erro_mensagem NUNCA pode conter o payload nem trecho dele - risco de dado pessoal do comprador");
            assertEquals(1, evento.getTentativas());
        } finally {
            ContextoTenant.limpar();
        }
    }

    @Test
    void reenvioIdenticoDoMesmoPayloadQuebradoContinuaFalhandoEIncrementaTentativas() throws SQLException {
        UUID tenantId = criarTenant("ingestao-erro-reenvio");
        UUID canalId = criarCanalMercadoLivre(tenantId);
        String payloadSemId = "{\"date_created\": \"2024-01-10T10:00:00.000-04:00\"}";
        String idExternoDoEvento = "evt-erro-reenvio";

        ContextoTenant.definir(tenantId);
        try {
            assertThrows(PayloadInvalidoException.class,
                    () -> servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, idExternoDoEvento, payloadSemId));
            assertThrows(PayloadInvalidoException.class,
                    () -> servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, idExternoDoEvento, payloadSemId));

            EventoIngerido evento = repositorioEventoIngerido
                    .findByCanalIdAndTipoEventoAndIdExterno(canalId, TipoEvento.PEDIDO, idExternoDoEvento)
                    .orElseThrow();
            // Diferente do caminho feliz (reenvio identico de payload BOM
            // e no-op silencioso, ver SQL_UPSERT_EVENTO): reenvio do
            // MESMO payload QUEBRADO continua contando, para dar visibilidade
            // de "isto esta falhando ha N tentativas" a quem for investigar.
            assertEquals(2, evento.getTentativas());
            assertEquals(1, repositorioEventoIngerido.count(), "continua sendo UMA linha, nao uma por tentativa");
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ------------------------------------------------------------------
    // Dívida 3 (docs/ESTADO.md): variacaoId resolvido pelo PIPELINE,
    // por SKU, sempre restrito ao tenant do evento.
    // ------------------------------------------------------------------

    @Test
    void resolveVariacaoPorSkuQuandoExisteNoCatalogoDoMesmoTenant() throws SQLException {
        UUID tenantId = criarTenant("ingestao-variacao-ok");
        String sku = "SKU-RESOLVIDO-" + UUID.randomUUID();
        UUID variacaoId = criarVariacao(tenantId, sku);
        UUID canalId = criarCanalMercadoLivre(tenantId);
        String payload = payloadComUmItem(sku);

        ContextoTenant.definir(tenantId);
        ResultadoIngestao resultado;
        try {
            resultado = servicoIngestao.ingerir(canalId, TipoEvento.PEDIDO, "evt-variacao-ok", payload);
        } finally {
            ContextoTenant.limpar();
        }
        assertTrue(resultado.processado());

        ContextoTenant.definir(tenantId);
        try {
            List<ItemPedido> itens = repositorioItemPedido.findByPedidoId(resultado.idPedido());
            assertEquals(1, itens.size());
            assertEquals(variacaoId, itens.get(0).getVariacaoId(),
                    "SKU casando com uma variacao do MESMO tenant tem que resolver variacao_id");
        } finally {
            ContextoTenant.limpar();
        }
    }

    /**
     * Isolamento de tenant obrigatorio para toda query nova (regra do
     * enunciado desta tarefa): {@code RepositorioVariacao.findBySku} e uma
     * consulta NOVA que a dívida 3 introduziu no pipeline. Este teste
     * FALHARIA se ela vazasse entre tenants - o tenant B nunca pode
     * resolver variacao_id a partir de uma variacao cadastrada pelo
     * tenant A, mesmo com o SKU identico.
     */
    @Test
    void resolucaoDeVariacaoPorSkuNuncaVazaEntreTenants() throws SQLException {
        UUID tenantA = criarTenant("ingestao-variacao-a");
        UUID tenantB = criarTenant("ingestao-variacao-b");
        String skuCompartilhado = "SKU-ISOLAMENTO-" + UUID.randomUUID();

        criarVariacao(tenantA, skuCompartilhado);

        UUID canalB = criarCanalMercadoLivre(tenantB);
        String payload = payloadComUmItem(skuCompartilhado);

        ContextoTenant.definir(tenantB);
        ResultadoIngestao resultado;
        try {
            resultado = servicoIngestao.ingerir(canalB, TipoEvento.PEDIDO, "evt-variacao-isolamento", payload);
        } finally {
            ContextoTenant.limpar();
        }
        assertTrue(resultado.processado());
        assertTrue(resultado.camposAusentes().stream().anyMatch(a -> a.campo().equals("item_pedido.variacao_id")),
                "ausencia de variacao tem que ser DECLARADA (regra 5 do CLAUDE.md), nunca silenciosa");

        ContextoTenant.definir(tenantB);
        try {
            List<ItemPedido> itens = repositorioItemPedido.findByPedidoId(resultado.idPedido());
            assertEquals(1, itens.size());
            assertNull(itens.get(0).getVariacaoId(),
                    "isolamento de tenant: SKU de OUTRO tenant NUNCA pode resolver variacao_id aqui");
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

    /**
     * Caminho privilegiado, fora do RLS e fora do @TenantId: usado so para
     * confirmar ausencia de dado em NENHUM lugar do banco apos uma
     * ingestao que deveria ter sido rejeitada - nunca para testar
     * isolamento (mesma ressalva do Javadoc de PostgresDeTeste). canal_id +
     * id_externo bastam como marcador aqui porque cada teste usa um
     * canalId e/ou idExterno novos (UUID aleatorio).
     */
    private long contarEventoIngeridoComoPrivilegiado(UUID canalId, String idExterno) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM evento_ingerido WHERE canal_id = ? AND id_externo = ?")) {
            comando.setObject(1, canalId);
            comando.setString(2, idExterno);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private long contarPedidoComoPrivilegiado(UUID canalId, String idExterno) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM pedido WHERE canal_id = ? AND id_externo = ?")) {
            comando.setObject(1, canalId);
            comando.setString(2, idExterno);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    /**
     * Insere um pedido diretamente, por fora da aplicacao (conexao de
     * dono), com a chave natural (tenant_id, canal_id, id_externo) que o
     * pipeline de ingestao vai tentar gravar em seguida - simula a linha
     * "ja existente" que o backstop de corrida precisa encontrar. Ver
     * Javadoc de {@link #ingerirComPedidoJaExistenteNaChaveNaturalNaoDuplicaNemPropagaExcecao()}
     * para a ressalva sobre o que isto prova e o que nao prova.
     */
    private UUID inserirPedidoDiretoComoDono(UUID tenantId, UUID canalId, String idExterno) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO pedido (tenant_id, canal_id, id_externo, status, feito_em) "
                                + "VALUES (?, ?, ?, 'PAGO', now()) RETURNING id")) {
            comando.setObject(1, tenantId);
            comando.setObject(2, canalId);
            comando.setString(3, idExterno);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return (UUID) resultado.getObject("id");
            }
        }
    }

    /**
     * Cria produto + variacao (o SKU) para os testes de resolucao de
     * variacao (dívida 3). Roda sob o tenant informado - quem chama decide
     * de qual tenant e a variacao, e e exatamente essa escolha que o
     * teste de isolamento explora.
     */
    private UUID criarVariacao(UUID tenantId, String sku) {
        ContextoTenant.definir(tenantId);
        try {
            Produto produto = repositorioProduto.save(
                    new Produto(null, null, "Produto de teste - " + sku, null, null, null, null, null, null));
            Variacao variacao = repositorioVariacao.save(
                    new Variacao(produto.getId(), sku, null, null, null, true, null, null, null, null, null, null, null));
            return variacao.getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    /**
     * Payload minimo valido de pedido do Mercado Livre, com um unico item
     * do SKU informado - usado pelos testes de resolucao de variacao
     * (dívida 3), que nao precisam de nenhum outro campo do pedido.
     */
    private static String payloadComUmItem(String sku) {
        String idExternoPedido = "pedido-variacao-" + UUID.randomUUID();
        return """
                {
                  "id": "%s",
                  "date_created": "2024-01-10T10:00:00.000-04:00",
                  "status": "paid",
                  "total_amount": 10.00,
                  "order_items": [
                    {
                      "item": { "id": "ITEM-1", "title": "Produto de teste", "seller_sku": "%s" },
                      "quantity": 1,
                      "unit_price": 10.00
                    }
                  ]
                }
                """.formatted(idExternoPedido, sku);
    }
}
