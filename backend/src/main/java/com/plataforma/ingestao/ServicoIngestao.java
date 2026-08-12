package com.plataforma.ingestao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;
import com.plataforma.cliente.Cliente;
import com.plataforma.cliente.RepositorioCliente;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.custo.Custo;
import com.plataforma.custo.RepositorioCusto;
import com.plataforma.integracao.AdaptadorDeCanal;
import com.plataforma.integracao.ResultadoTraducao;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.RepositorioItemPedido;
import com.plataforma.pedido.RepositorioPedido;

/**
 * Pipeline de ingestao (tarefa 12). Ponto unico de entrada para "chegou
 * um evento de uma fonte externa": calcula a chave de idempotencia,
 * aplica o UPSERT atomico de evento_ingerido (V012), e - so quando o
 * UPSERT diz que ha algo NOVO para processar - chama o
 * {@link AdaptadorDeCanal} certo e persiste o resultado canonico.
 *
 * Classe Spring comum (decisao 0014 - Camel adiado): sem rota, sem DSL.
 *
 * ---------------------------------------------------------------------
 * POR QUE NAO DEDUPLICA ENTRE CANAIS (decisao 0017 - LEIA ANTES DE MEXER)
 * ---------------------------------------------------------------------
 * O mesmo pedido de venda pode chegar pelo Mercado Livre E pelo Bling (o
 * ERP espelha o pedido do marketplace). A chave de idempotencia de
 * evento_ingerido e (tenant_id, canal_id, tipo_evento, id_externo) - ela
 * impede reprocessar o MESMO evento da MESMA fonte, mas nao teria como
 * (nem deveria) impedir que a mesma venda real chegue por dois canais
 * diferentes. A decisao 0017 e explicita: casar "este pedido do ML e o
 * mesmo do Bling" e uma etapa SEPARADA e EXPLICITA de reconciliacao, que
 * so sera construida quando existir uma chave CONFIRMADA contra dado
 * real (o candidato hoje, numeroLoja do Bling, e hipotese nao
 * confirmada - ver mapeamento-bling.md). Ate la, cada canal gera sua
 * propria linha de pedido, e "quanto vendi" tem que declarar o canal.
 */
@Service
public class ServicoIngestao {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoIngestao.class);

    // ---------------------------------------------------------------
    // Receita do cabecalho da V012, EXATA na logica (colunas, ON
    // CONFLICT, WHERE hash_payload IS DISTINCT FROM, RETURNING id).
    // UNICA adaptacao: o cast "?::jsonb" no parametro de payload_bruto -
    // necessario porque JdbcTemplate faz bind de String como texto (a
    // coluna e jsonb); nao muda nenhuma coluna, condicao ou semantica da
    // receita descrita na migration.
    //
    // Volta linha -> processa (casos 1 e 3 do cabecalho). Nao volta
    // nada -> reenvio identico, no-op (caso 2).
    // ---------------------------------------------------------------
    private static final String SQL_UPSERT_EVENTO = """
            INSERT INTO evento_ingerido
                (tenant_id, canal_id, tipo_evento, id_externo, hash_payload,
                 payload_bruto, status, recebido_em)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, 'RECEBIDO', now())
            ON CONFLICT (tenant_id, canal_id, tipo_evento, id_externo)
            DO UPDATE SET hash_payload  = excluded.hash_payload,
                          payload_bruto = excluded.payload_bruto,
                          status        = 'RECEBIDO',
                          recebido_em   = excluded.recebido_em,
                          processado_em = NULL,
                          tentativas    = 0,
                          erro_mensagem = NULL,
                          atualizado_em = now()
            WHERE evento_ingerido.hash_payload IS DISTINCT FROM excluded.hash_payload
            RETURNING id
            """;

    // Fecha o ciclo do evento (RECEBIDO -> PROCESSADO) e aponta para a
    // linha canonica gerada (rastreabilidade - comentario de
    // entidade_tipo/entidade_id na V012). SQL nativo pelo MESMO motivo do
    // upsert acima: EventoIngerido nao tem setters (entidade so-de-
    // construcao, ver seu javadoc), entao nao ha como fazer isto via
    // repositorio JPA comum.
    private static final String SQL_MARCAR_PROCESSADO = """
            UPDATE evento_ingerido
               SET status = 'PROCESSADO',
                   processado_em = now(),
                   entidade_tipo = ?,
                   entidade_id = ?,
                   atualizado_em = now()
             WHERE id = ? AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RepositorioCanal repositorioCanal;
    private final RepositorioPedido repositorioPedido;
    private final RepositorioItemPedido repositorioItemPedido;
    private final RepositorioCliente repositorioCliente;
    private final RepositorioCusto repositorioCusto;
    private final Map<TipoCanal, AdaptadorDeCanal> adaptadoresPorTipo;

    public ServicoIngestao(JdbcTemplate jdbcTemplate, RepositorioCanal repositorioCanal,
            RepositorioPedido repositorioPedido, RepositorioItemPedido repositorioItemPedido,
            RepositorioCliente repositorioCliente, RepositorioCusto repositorioCusto,
            List<AdaptadorDeCanal> adaptadores) {
        this.jdbcTemplate = jdbcTemplate;
        this.repositorioCanal = repositorioCanal;
        this.repositorioPedido = repositorioPedido;
        this.repositorioItemPedido = repositorioItemPedido;
        this.repositorioCliente = repositorioCliente;
        this.repositorioCusto = repositorioCusto;
        this.adaptadoresPorTipo = adaptadores.stream()
                .collect(Collectors.toUnmodifiableMap(AdaptadorDeCanal::tipoSuportado, adaptador -> adaptador));
    }

    /**
     * Ponto de entrada unico da ingestao. Tenant e implicito
     * ({@link ContextoTenant#atual()}) - nunca parametro, seguindo a
     * regra 1 do CLAUDE.md ("tenant e identidade, nao filtro").
     *
     * @param canalId      canal de onde o evento veio
     * @param tipoEvento   o que o payload representa (parte da chave natural)
     * @param idExterno    id do evento na fonte (parte da chave natural) -
     *                     se a fonte nao fornecer, quem chama sintetiza um
     *                     id deterministico a partir do payload (ver
     *                     comentario da V012), esta classe nao faz isso
     * @param payloadBruto o payload cru, exatamente como recebido
     *
     * LIMITACAO CONHECIDA (registrada aqui em vez de escondida): o metodo
     * inteiro roda numa unica @Transactional. Se o adaptador ou a
     * persistencia falharem DEPOIS do upsert do evento (ex.:
     * PayloadInvalidoException), a transacao inteira desfaz - inclusive o
     * upsert de evento_ingerido. Ou seja: hoje uma falha de tradução NAO
     * fica gravada como status=ERRO/erro_mensagem (essas colunas existem
     * na V012, mas esta rodada nao as usa) - o evento simplesmente volta
     * a nao existir, e o proximo reenvio tenta de novo do zero. Gravar o
     * erro de forma durável exigiria uma transacao separada (ex.:
     * REQUIRES_NEW) só para a marcação de erro, fora do escopo desta
     * tarefa - registrar aqui para quem for tratar retry/alerta de erro
     * de verdade não presumir que já existe.
     */
    @Transactional
    public ResultadoIngestao ingerir(UUID canalId, TipoEvento tipoEvento, String idExterno, String payloadBruto) {
        UUID tenantId = ContextoTenant.atual();
        String hash = sha256Hex(payloadBruto);

        // ATENCAO CRITICA: SQL nativo (abaixo) NAO recebe o predicado de
        // tenant que o Hibernate acrescenta sozinho via @TenantId em
        // consultas JPA (decisao 0007, camada 3) - aqui so o RLS (camada
        // 4, policies evento_ingerido_insert/update da V012) protegeria
        // por baixo. Por isso o tenant_id abaixo vem SEMPRE de
        // ContextoTenant.atual() (nunca de um parametro que um chamador
        // poderia forjar) e e SEMPRE passado como bind parameter - nunca
        // concatenado na string SQL (a mesma disciplina de
        // DataSourceComTenant.setarGucDeTenant, que usa set_config(...)
        // com parametro em vez de SET literal).
        UUID idEvento = executarUpsertEvento(tenantId, canalId, tipoEvento, idExterno, hash, payloadBruto);

        if (idEvento == null) {
            // Caso 2 do cabecalho da V012: reenvio identico. No-op puro -
            // nada foi gravado, nada foi alterado.
            LOG.debug("Reenvio identico ignorado (tenant={}, canal={}, tipo={}, idExterno={})",
                    tenantId, canalId, tipoEvento, idExterno);
            return ResultadoIngestao.reenvioIdentico();
        }

        Canal canal = repositorioCanal.findById(canalId)
                .orElseThrow(() -> new IllegalStateException(
                        "Evento " + idEvento + " gravado apontando para canal " + canalId
                                + " que nao existe (ou nao pertence a este tenant) - dado inconsistente."));

        AdaptadorDeCanal adaptador = adaptadoresPorTipo.get(canal.getTipo());
        if (adaptador == null) {
            throw new IllegalStateException(
                    "Nao ha AdaptadorDeCanal registrado para o tipo " + canal.getTipo() + " (canal " + canalId
                            + "). Evento " + idEvento + " gravado, mas nao pode ser traduzido ainda.");
        }

        ResultadoTraducao resultado = adaptador.traduzirPedido(payloadBruto, canalId);

        UUID idPedidoPersistido = persistirResultado(resultado, canalId);

        marcarComoProcessado(idEvento, tenantId, idPedidoPersistido);

        return ResultadoIngestao.processado(idEvento, idPedidoPersistido, resultado.camposAusentes());
    }

    private UUID executarUpsertEvento(UUID tenantId, UUID canalId, TipoEvento tipoEvento, String idExterno,
            String hash, String payloadBruto) {
        List<UUID> linhas = jdbcTemplate.query(SQL_UPSERT_EVENTO,
                (linha, numeroDaLinha) -> (UUID) linha.getObject("id"),
                tenantId, canalId, tipoEvento.name(), idExterno, hash, payloadBruto);
        return linhas.isEmpty() ? null : linhas.get(0);
    }

    private void marcarComoProcessado(UUID idEvento, UUID tenantId, UUID idPedido) {
        jdbcTemplate.update(SQL_MARCAR_PROCESSADO, TipoEntidade.PEDIDO.name(), idPedido, idEvento, tenantId);
    }

    /**
     * Persiste o resultado da traducao.
     *
     * SEGUNDA TRAVA contra pedido duplicado, alem do evento_ingerido
     * (V012): antes de inserir um pedido novo, verifica se ja existe um
     * pedido com a mesma chave natural (canal_id, id_externo) -
     * uq_pedido_origem (V008). Se ja existir, este metodo NAO tenta
     * atualiza-lo campo a campo: {@link Pedido} e imutavel por desenho
     * (sem setters - ver seu javadoc: "esta entidade so mapeia e
     * constroi, nao tem logica de negocio"; as datas de transicao de
     * status nascem NULL e sao preenchidas por outro fluxo, de outra
     * tarefa). Reprocessar um payload com hash diferente (caso 3 do
     * cabecalho da V012) ainda fica registrado em evento_ingerido (hash e
     * payload_bruto novos gravados), mas ATUALIZAR os campos do pedido ja
     * existente e trabalho fora do escopo desta tarefa. A garantia que
     * ESTA tarefa pede - "reprocessar nao duplica" - esta cumprida: o
     * mesmo payload (ou uma versao alterada dele, com a mesma chave
     * natural) NUNCA cria uma segunda linha de pedido.
     */
    private UUID persistirResultado(ResultadoTraducao resultado, UUID canalId) {
        Pedido pedidoTraduzido = resultado.pedido();

        Optional<Pedido> pedidoExistente = (pedidoTraduzido.getIdExterno() == null)
                ? Optional.empty()
                : repositorioPedido.findByCanalIdAndIdExterno(canalId, pedidoTraduzido.getIdExterno());

        if (pedidoExistente.isPresent()) {
            LOG.info("Pedido ja existente para canal={} idExterno={} (id={}) - nenhuma linha nova criada "
                    + "(Pedido e imutavel por desenho; atualizacao de campos e outra tarefa).",
                    canalId, pedidoTraduzido.getIdExterno(), pedidoExistente.get().getId());
            return pedidoExistente.get().getId();
        }

        persistirClienteSeNovo(resultado.cliente(), canalId);

        Pedido pedidoSalvo;
        try {
            pedidoSalvo = repositorioPedido.save(pedidoTraduzido);
            // flush() explicito: pedido, item_pedido e custo se referenciam
            // por FK composta usando so o UUID cru (decisao 0015, sem
            // @ManyToOne) - o Hibernate NAO enxerga essas dependencias como
            // um grafo de objeto, entao nao ha garantia automatica de que
            // o INSERT do pai saia antes do filho no mesmo flush. Forcar o
            // flush aqui (e depois do cliente, e depois dos itens) elimina
            // essa ambiguidade em vez de confiar na ordem interna da
            // ActionQueue do Hibernate.
            repositorioPedido.flush();
        } catch (DataIntegrityViolationException conflito) {
            // Ultimo backstop contra corrida: dois processamentos
            // concorrentes do MESMO evento nao deveriam acontecer (o
            // upsert de evento_ingerido e atomico), mas este trecho roda
            // DEPOIS dele - ha uma janela pequena entre "o upsert
            // retornou linha" e "o insert do pedido efetivamente
            // commitou". uq_pedido_origem (V008) e quem pega isso.
            Pedido jaExistente = repositorioPedido.findByCanalIdAndIdExterno(canalId, pedidoTraduzido.getIdExterno())
                    .orElseThrow(() -> conflito);
            LOG.warn("Corrida detectada ao inserir pedido canal={} idExterno={} - uq_pedido_origem pegou a "
                    + "duplicata, pedido existente (id={}) mantido.",
                    canalId, pedidoTraduzido.getIdExterno(), jaExistente.getId());
            return jaExistente.getId();
        }

        for (ItemPedido item : resultado.itens()) {
            repositorioItemPedido.save(item);
        }
        repositorioItemPedido.flush();
        // custo.item_pedido_id (quando preenchido) exige custo.pedido_id
        // tambem preenchido (ck_custo_item_exige_pedido, V010) - ambos ja
        // vieram prontos do adaptador (Custo aponta para pedido.getId() e
        // item.getId() construidos antes), o flush acima so garante que a
        // LINHA de item_pedido ja existe no banco quando o FK de custo for
        // checado.
        for (Custo custo : resultado.custos()) {
            repositorioCusto.save(custo);
        }

        return pedidoSalvo.getId();
    }

    private void persistirClienteSeNovo(Cliente clienteTraduzido, UUID canalId) {
        if (clienteTraduzido == null) {
            return;
        }
        boolean jaExiste = clienteTraduzido.getIdExterno() != null
                && repositorioCliente.findByCanalIdAndIdExterno(canalId, clienteTraduzido.getIdExterno()).isPresent();
        if (jaExiste) {
            // Mesma logica do pedido: Cliente e imutavel por desenho
            // (sem setters), enriquecer um cadastro ja existente com dado
            // mais recente da fonte e trabalho de outra tarefa.
            return;
        }
        repositorioCliente.save(clienteTraduzido);
        // flush() antes de voltar para persistirResultado inserir o
        // pedido: fk_pedido_cliente (V008) precisa que o cliente ja
        // exista no banco - ver comentario do flush() em persistirResultado.
        repositorioCliente.flush();
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            // HexFormat.of() gera hex MINUSCULO por padrao - exatamente o
            // formato que ck_evento_ingerido_hash_formato (V012) exige e
            // que EventoIngerido ja valida em Java antes do INSERT.
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossivel) {
            // SHA-256 e obrigatorio em toda JVM (JCA) - nunca deveria
            // acontecer. Sem recuperacao sensata possivel.
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM - ambiente quebrado.", impossivel);
        }
    }
}
