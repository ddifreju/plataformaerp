package com.plataforma.ingestao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;
import com.plataforma.catalogo.RepositorioVariacao;
import com.plataforma.catalogo.Variacao;
import com.plataforma.cliente.Cliente;
import com.plataforma.cliente.RepositorioCliente;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.custo.Custo;
import com.plataforma.custo.RepositorioCusto;
import com.plataforma.integracao.AdaptadorDeCanal;
import com.plataforma.integracao.CampoAusente;
import com.plataforma.integracao.PayloadInvalidoException;
import com.plataforma.integracao.ResultadoTraducao;
import com.plataforma.margem.CongelamentoCustoMercadoria;
import com.plataforma.margem.ResolvedorCustoPedido;
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
 *
 * ---------------------------------------------------------------------
 * POR QUE DUAS TRANSACOES SEPARADAS, NAO UMA SO (dívida 2 do ESTADO.md)
 * ---------------------------------------------------------------------
 * {@link #ingerir} NAO tem {@code @Transactional} no metodo: ele abre
 * transacoes explicitamente, via {@link TransactionTemplate}, em vez de
 * depender do proxy declarativo do Spring. O motivo e estrutural, nao
 * estilo:
 *
 * <p>O caminho feliz (upsert do evento -> traducao -> persistencia ->
 * marcar processado) PRECISA ser atomico - se qualquer passo falhar, o
 * upsert do evento tambem deve desfazer (senao um pedido "meio gravado"
 * fica associado a um evento que diz "processado"). Mas quando quem falha
 * e especificamente a TRADUCAO ({@link PayloadInvalidoException}), o
 * requisito e o OPOSTO: o evento tem que sobreviver, com
 * {@code status=ERRO} e {@code erro_mensagem}, para o reenvio nao cair
 * num buraco negro (ver javadoc de PayloadInvalidoException).
 *
 * <p>Essas duas necessidades sao inconciliaveis dentro de UMA transacao:
 * nao existe "desfaz tudo, mas mantem esta UPDATE aqui". A solucao nao e
 * {@code REQUIRES_NEW} ANINHADA dentro da mesma transacao ambiente -
 * isso faria a transacao de erro tentar dar UPDATE numa linha que a
 * transacao externa (ainda aberta, ainda segurando o lock da linha que
 * ela mesma inseriu) nao liberou, travando a aplicacao esperando um lock
 * que so ela mesma poderia soltar (autodeadlock). A solucao usada aqui e
 * SEQUENCIAL: a transacao do caminho feliz RODA E TERMINA (commit ou
 * rollback) inteiramente antes de qualquer decisao sobre gravar erro.
 * So DEPOIS que ela terminou (e, no caso de falha de traducao, deu
 * rollback e soltou o lock) e que uma SEGUNDA transacao, independente,
 * grava o {@code status=ERRO}. {@link TransactionTemplate} com
 * {@code PROPAGATION_REQUIRES_NEW} garante que cada uma das duas
 * chamadas abre sua PROPRIA transacao do zero, mesmo que no futuro
 * {@code ingerir} passe a ser chamado de dentro de outro
 * {@code @Transactional} (ex.: um controller transacional).
 *
 * <p>Efeito colateral aceito: se a traducao falhar, o upsert de
 * {@code status='RECEBIDO'} feito dentro da primeira transacao e
 * desfeito junto - por isso a segunda transacao REFAZ o upsert (variante
 * com {@code status='ERRO'}), nao faz so um UPDATE. Ver
 * {@link #SQL_UPSERT_EVENTO_ERRO}.
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
    //
    // O "OR status = 'ERRO'" NO WHERE — bug encontrado na primeira
    // execucao real da suite:
    //   So com "hash IS DISTINCT FROM", QUALQUER reenvio identico virava
    //   no-op, inclusive o de um evento que terminou em ERRO. Ou seja: um
    //   pedido que falhou ao traduzir ficava travado PARA SEMPRE. Mesmo
    //   depois de corrigido o bug que causou a falha, reenviar o mesmo
    //   payload nao reprocessava nada - o sistema respondia "ja vi esse,
    //   ignorei" e seguia em frente, em silencio.
    //   Isso esvaziava a divida 2 pela metade: gravar status=ERRO da
    //   visibilidade, mas sem poder RETENTAR a visibilidade nao serve
    //   para muita coisa.
    //   Com o OR, evento em ERRO sempre reprocessa; evento PROCESSADO com
    //   payload identico continua sendo no-op puro, que e a idempotencia
    //   que importa preservar.
    //
    // O CASE no "tentativas" existe por causa do OR acima:
    //   zerar sempre apagaria o contador justamente na retentativa. Agora
    //   payload DIFERENTE zera (e outro conteudo, historia nova) e payload
    //   IGUAL preserva (e a mesma falha, tentando de novo) - que e o que
    //   deixa "isto falhou N vezes" legivel para quem for investigar.
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
                          tentativas    = CASE
                                              WHEN evento_ingerido.hash_payload
                                                   IS DISTINCT FROM excluded.hash_payload
                                              THEN 0
                                              ELSE evento_ingerido.tentativas
                                          END,
                          erro_mensagem = NULL,
                          atualizado_em = now()
            WHERE evento_ingerido.hash_payload IS DISTINCT FROM excluded.hash_payload
               OR evento_ingerido.status = 'ERRO'
            RETURNING id
            """;

    // Variante de erro (dívida 2). Duas diferencas deliberadas em relacao
    // a SQL_UPSERT_EVENTO:
    //   1. SEM o "WHERE hash_payload IS DISTINCT FROM ...": um reenvio do
    //      MESMO payload quebrado tem que continuar sendo contado (
    //      tentativas incrementa, atualizado_em atualiza) - diferente do
    //      caminho feliz, aqui "reenvio identico" NAO e no-op, e mais uma
    //      tentativa falha que vale registrar para quem for investigar.
    //   2. tentativas SOBE (evento_ingerido.tentativas + 1) em vez de
    //      zerar - e o contador de "quantas vezes isto falhou".
    private static final String SQL_UPSERT_EVENTO_ERRO = """
            INSERT INTO evento_ingerido
                (tenant_id, canal_id, tipo_evento, id_externo, hash_payload,
                 payload_bruto, status, recebido_em, tentativas, erro_mensagem)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, 'ERRO', now(), 1, ?)
            ON CONFLICT (tenant_id, canal_id, tipo_evento, id_externo)
            DO UPDATE SET hash_payload  = excluded.hash_payload,
                          payload_bruto = excluded.payload_bruto,
                          status        = 'ERRO',
                          recebido_em   = excluded.recebido_em,
                          processado_em = NULL,
                          tentativas    = evento_ingerido.tentativas + 1,
                          erro_mensagem = excluded.erro_mensagem,
                          atualizado_em = now()
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
    private final RepositorioVariacao repositorioVariacao;
    private final ResolvedorCustoPedido resolvedorCustoPedido;
    private final Map<TipoCanal, AdaptadorDeCanal> adaptadoresPorTipo;
    // PROPAGATION_REQUIRES_NEW definido no construtor (nao e o default do
    // TransactionTemplate) - ver o "POR QUE DUAS TRANSACOES SEPARADAS" no
    // javadoc da classe.
    private final TransactionTemplate transactionTemplate;

    public ServicoIngestao(JdbcTemplate jdbcTemplate, RepositorioCanal repositorioCanal,
            RepositorioPedido repositorioPedido, RepositorioItemPedido repositorioItemPedido,
            RepositorioCliente repositorioCliente, RepositorioCusto repositorioCusto,
            RepositorioVariacao repositorioVariacao, ResolvedorCustoPedido resolvedorCustoPedido,
            PlatformTransactionManager gerenciadorTransacao, List<AdaptadorDeCanal> adaptadores) {
        this.jdbcTemplate = jdbcTemplate;
        this.repositorioCanal = repositorioCanal;
        this.repositorioPedido = repositorioPedido;
        this.repositorioItemPedido = repositorioItemPedido;
        this.repositorioCliente = repositorioCliente;
        this.repositorioCusto = repositorioCusto;
        this.repositorioVariacao = repositorioVariacao;
        this.resolvedorCustoPedido = resolvedorCustoPedido;
        this.adaptadoresPorTipo = adaptadores.stream()
                .collect(Collectors.toUnmodifiableMap(AdaptadorDeCanal::tipoSuportado, adaptador -> adaptador));
        this.transactionTemplate = new TransactionTemplate(gerenciadorTransacao);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
     * @throws PayloadInvalidoException quando o adaptador nao consegue
     *         traduzir o payload. ANTES de propagar, o evento e gravado
     *         (em transacao propria - ver javadoc da classe) com
     *         {@code status='ERRO'} e {@code erro_mensagem} preenchida:
     *         quem chama ve a falha (pode responder 4xx/alertar), e o
     *         evento fica rastreavel para investigacao, em vez de "nunca
     *         ter existido".
     */
    public ResultadoIngestao ingerir(UUID canalId, TipoEvento tipoEvento, String idExterno, String payloadBruto) {
        UUID tenantId = ContextoTenant.atual();
        String hash = sha256Hex(payloadBruto);

        // VALIDACAO DO CANAL ANTES DE QUALQUER ESCRITA.
        //
        // canalId e o unico parametro deste metodo que NAO vem do
        // ContextoTenant - vem de quem chama. Quando existir um webhook ou
        // um endpoint de reprocessamento por cima deste metodo, ele passa a
        // ser entrada externa.
        //
        // O banco ja recusaria um canal de outro tenant: a FK composta
        // (tenant_id, canal_id) -> canal (tenant_id, id) da decisao 0015
        // torna isso impossivel de gravar. Mas depender so dela significa
        // descobrir o problema como violacao de integridade no meio de um
        // INSERT, virando 500 generico, depois de ja ter tocado o banco.
        //
        // Esta consulta e implicitamente restrita ao tenant pelo @TenantId
        // (decisao 0007, camada 3), entao "nao encontrou" ja significa
        // "nao existe OU nao e deste tenant" - sem precisar comparar
        // tenant a mao. Falha explicita, testavel e antes de escrever.
        // Roda fora de qualquer transacao explicita: leitura simples,
        // Spring Data ja envolve findById num @Transactional(readOnly)
        // proprio.
        Canal canal = repositorioCanal.findById(canalId)
                .orElseThrow(() -> new CanalDesconhecidoException(
                        "Canal " + canalId + " nao existe ou nao pertence a este tenant."));

        AdaptadorDeCanal adaptador = adaptadoresPorTipo.get(canal.getTipo());
        if (adaptador == null) {
            throw new IllegalStateException(
                    "Nao ha AdaptadorDeCanal registrado para o tipo " + canal.getTipo()
                            + " (canal " + canalId + "). Nada foi gravado.");
        }

        try {
            return transactionTemplate.execute(status ->
                    processarDentroDeUmaTransacao(tenantId, canalId, tipoEvento, idExterno, hash, payloadBruto, adaptador));
        } catch (PayloadInvalidoException falhaDeTraducao) {
            // A transacao acima ja deu ROLLBACK sozinha (TransactionTemplate
            // desfaz e relanca quando o callback propaga uma
            // RuntimeException) - o upsert de evento_ingerido feito dentro
            // dela foi desfeito junto. Por isso a chamada abaixo roda numa
            // SEGUNDA transacao (nova, ver construtor) e REFAZ o upsert do
            // zero, desta vez com status=ERRO.
            registrarErroDeTraducao(tenantId, canalId, tipoEvento, idExterno, hash, payloadBruto, falhaDeTraducao);
            throw falhaDeTraducao;
        }
    }

    private ResultadoIngestao processarDentroDeUmaTransacao(UUID tenantId, UUID canalId, TipoEvento tipoEvento,
            String idExterno, String hash, String payloadBruto, AdaptadorDeCanal adaptador) {
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

        // Pode lancar PayloadInvalidoException - quem trata isso e
        // ingerir() (ver javadoc da classe: precisa terminar/desfazer ESTA
        // transacao antes de gravar o erro numa transacao nova).
        ResultadoTraducao resultado = adaptador.traduzirPedido(payloadBruto, canalId);

        ResultadoPersistencia persistencia = persistirResultado(resultado, canalId);

        marcarComoProcessado(idEvento, tenantId, persistencia.idPedido());

        List<CampoAusente> camposAusentes = new ArrayList<>(resultado.camposAusentes());
        camposAusentes.addAll(persistencia.camposAusentesAdicionais());
        return ResultadoIngestao.processado(idEvento, persistencia.idPedido(), camposAusentes);
    }

    /**
     * Grava a falha de traducao de forma duravel (dívida 2). Roda em
     * transacao PROPRIA (REQUIRES_NEW, ver construtor) - a transacao do
     * caminho feliz ja terminou (rollback) quando isto e chamado, entao
     * nao ha risco do autodeadlock descrito no javadoc da classe.
     *
     * CUIDADO COM DADO PESSOAL: erro_mensagem NUNCA pode conter o payload
     * nem trecho dele - o payload do marketplace/ERP carrega nome,
     * endereco e contato do comprador (ver PayloadInvalidoException e
     * regra 3/CLAUDE.md sobre isso). {@code falha.getMessage()} e seguro
     * de usar aqui PORQUE, por contrato do proprio tipo (ver javadoc de
     * PayloadInvalidoException e os pontos onde e lancada em
     * AdaptadorBling/AdaptadorMercadoLivre), a mensagem so referencia
     * id_externo de pedido/item e nome de campo ausente/invalido - nunca
     * nome, endereco, e-mail ou telefone do comprador (que so aparecem
     * dentro de traduzirCliente(), e esse metodo NUNCA lanca
     * PayloadInvalidoException, so acumula CampoAusente). O prefixo com o
     * nome da classe da excecao e o "tipo do erro" pedido pela dívida 2;
     * o restante da mensagem e o "campo problematico", nunca conteudo do
     * payload.
     */
    private void registrarErroDeTraducao(UUID tenantId, UUID canalId, TipoEvento tipoEvento, String idExterno,
            String hash, String payloadBruto, PayloadInvalidoException falha) {
        String erroMensagem = falha.getClass().getSimpleName() + ": " + falha.getMessage();
        LOG.warn("Falha de traducao registrada como ERRO (tenant={}, canal={}, tipo={}, idExterno={}, tipoErro={})",
                tenantId, canalId, tipoEvento, idExterno, falha.getClass().getSimpleName());
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(SQL_UPSERT_EVENTO_ERRO,
                tenantId, canalId, tipoEvento.name(), idExterno, hash, payloadBruto, erroMensagem));
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
     * O que {@link #persistirResultado} produz: o id do pedido persistido
     * (novo, atualizado ou ja existente) e os {@link CampoAusente} que so
     * a PERSISTENCIA descobre (hoje, so a resolucao de variacao por SKU -
     * dívida 3) - distintos dos que o ADAPTADOR ja declarou em
     * {@link ResultadoTraducao#camposAusentes()}, porque o adaptador nao
     * tem acesso ao catalogo para saber se o SKU casa com uma variacao.
     */
    private record ResultadoPersistencia(UUID idPedido, List<CampoAusente> camposAusentesAdicionais) {
    }

    /**
     * Persiste o resultado da traducao.
     *
     * SEGUNDA TRAVA contra pedido duplicado, alem do evento_ingerido
     * (V012): antes de inserir um pedido novo, verifica se ja existe um
     * pedido com a mesma chave natural (canal_id, id_externo) -
     * uq_pedido_origem (V008).
     *
     * QUANDO JA EXISTE (dívida 1 - reprocessar payload alterado agora
     * ATUALIZA): em vez de so devolver o id existente sem tocar em nada,
     * chama {@link Pedido#atualizarAPartirDaOrigem}, o metodo de INTENCAO
     * que aplica status/valores/datas/dados_origem do pedido
     * recem-traduzido, preservando a identidade da linha (id, tenantId,
     * canalId, idExterno nunca mudam - ver javadoc daquele metodo). Itens
     * e custos NAO sao tocados neste caminho: esta rodada so cobre
     * pedido e cliente (ver docs/ESTADO.md, dívida 1) - atualizar
     * item_pedido/custo em reprocessamento e trabalho de outra tarefa.
     */
    private ResultadoPersistencia persistirResultado(ResultadoTraducao resultado, UUID canalId) {
        Pedido pedidoTraduzido = resultado.pedido();

        Optional<Pedido> pedidoExistente = (pedidoTraduzido.getIdExterno() == null)
                ? Optional.empty()
                : repositorioPedido.findByCanalIdAndIdExterno(canalId, pedidoTraduzido.getIdExterno());

        if (pedidoExistente.isPresent()) {
            Pedido existente = pedidoExistente.get();
            existente.atualizarAPartirDaOrigem(pedidoTraduzido);
            repositorioPedido.save(existente);
            repositorioPedido.flush();
            LOG.info("Pedido ja existente ATUALIZADO para canal={} idExterno={} (id={}) a partir do payload reprocessado.",
                    canalId, pedidoTraduzido.getIdExterno(), existente.getId());

            persistirClienteOuAtualizar(resultado.cliente(), canalId);

            return new ResultadoPersistencia(existente.getId(), List.of());
        }

        // O id REAL do cliente, que difere do id gerado pelo adaptador
        // sempre que o comprador ja existia. Apontar o pedido para o id
        // certo ANTES do save e o que impede a FK de rejeitar o segundo
        // pedido de um comprador recorrente - ver Pedido.resolverCliente.
        UUID clienteIdPersistido = persistirClienteOuAtualizar(resultado.cliente(), canalId);
        if (clienteIdPersistido != null) {
            pedidoTraduzido.resolverCliente(clienteIdPersistido);
        }

        // Casamento de item x variacao (dívida 3) E congelamento de
        // custo(MERCADORIA) (PRÉ-TAREFA da Fase 3 - ver
        // CongelamentoCustoMercadoria): so o pipeline tem banco, por isso
        // so aqui, nunca no adaptador. Precisa rodar ANTES do save() dos
        // itens, ja que muda o campo variacaoId dos MESMOS objetos
        // ItemPedido que serao gravados logo abaixo.
        ResolucaoVariacoes resolucaoVariacoes = resolverVariacoesEGerarCustoMercadoria(resultado.itens(), pedidoTraduzido);

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
            return new ResultadoPersistencia(jaExistente.getId(), List.of());
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
        // custo(MERCADORIA) congelado nesta ingestao (PRÉ-TAREFA da Fase
        // 3) - salvo no MESMO lugar que o custo do adaptador (nivel 1),
        // pelo mesmo motivo de FK (comentario acima).
        for (Custo custoMercadoria : resolucaoVariacoes.custosMercadoria()) {
            repositorioCusto.save(custoMercadoria);
        }
        repositorioCusto.flush();

        // NIVEL 2 da hierarquia de taxas (decisao 0019 / tarefa 14):
        // ResolvedorCustoPedido tenta fechar COMISSAO_CANAL e
        // TARIFA_FIXA_CANAL por taxa_canal vigente PARA OS ITENS em que a
        // fonte (nivel 1, os custos do adaptador salvos acima) nao
        // informou o valor cobrado. Roda AQUI, depois do flush de custo,
        // porque ResolvedorCustoPedido.resolver() comeca consultando
        // repositorioCusto.findByPedidoId(...) para decidir o que ja esta
        // resolvido (idempotencia - ver o Javadoc daquela classe): sem o
        // flush acima, essa consulta nao veria as linhas que acabamos de
        // salvar nesta mesma transacao. Falha para o lado seguro: sem
        // taxa cadastrada para a vigencia, o item continua em lacuna
        // (nivel 3) - nunca inventa (mesma regra 5 do CLAUDE.md).
        //
        // So roda no caminho de pedido NOVO (esta branch do metodo) - o
        // mesmo limite ja documentado para itens/custo em reprocessamento
        // (dívida 1 do docs/ESTADO.md): "itens e custos NAO sao tocados
        // neste caminho". Reprocessar um pedido reenviando o MESMO
        // payload nao deveria acionar recalculo de taxa, que e sempre
        // ACIONADO, nunca automatico (secao 8.4 do documento fiscal).
        resolvedorCustoPedido.resolver(pedidoSalvo, resultado.itens());

        return new ResultadoPersistencia(pedidoSalvo.getId(), resolucaoVariacoes.camposAusentes());
    }

    /**
     * O que {@link #resolverVariacoesEGerarCustoMercadoria} produz: as
     * DUAS coisas que nascem da MESMA consulta por SKU (divida 3 +
     * PRÉ-TAREFA da Fase 3), para nao consultar a variacao duas vezes por
     * item.
     */
    private record ResolucaoVariacoes(List<CampoAusente> camposAusentes, List<Custo> custosMercadoria) {
    }

    /**
     * Casa cada item com a variacao do catalogo pelo SKU (dívida 3,
     * decisao 0018), DENTRO do tenant (RepositorioVariacao.findBySku ja e
     * restrito pelo @TenantId - decisao 0007), E congela custo(MERCADORIA)
     * quando casa (PRÉ-TAREFA da Fase 3 - ver
     * {@link CongelamentoCustoMercadoria}, que faz a conta em si; este
     * metodo so decide QUANDO chama-la, porque so aqui existe banco).
     * Muta os MESMOS objetos ItemPedido recebidos (List.copyOf em
     * ResultadoTraducao protege a LISTA, nao os elementos - mutar o
     * elemento aqui e valido e e exatamente o que
     * {@link ItemPedido#resolverVariacao} existe para permitir).
     *
     * TRES situacoes possiveis por item, cada uma com sua PROPRIA
     * declaracao (regra 5 do CLAUDE.md - "nao sei o produto" e "sei o
     * produto mas nao sei o custo" sao problemas diferentes para o
     * lojista resolver, entao nao viram a mesma mensagem):
     * <ol>
     *   <li>SKU nao bate com nenhuma variacao (ou item sem SKU): variacaoId
     *       fica NULL, custo(MERCADORIA) nem e tentado. Cobre tanto
     *       "produto ainda nao sincronizado no catalogo" quanto "venda
     *       avulsa fora do catalogo", que sao situacoes legitimas, nao
     *       erros.</li>
     *   <li>SKU bate, mas a variacao nao tem custo_unitario_atual
     *       cadastrado: variacaoId FICA preenchido (o casamento aconteceu
     *       de verdade), so a linha de custo que nao e criada.</li>
     *   <li>SKU bate e tem custo cadastrado: variacaoId preenchido e
     *       custo(MERCADORIA) congelado.</li>
     * </ol>
     */
    private ResolucaoVariacoes resolverVariacoesEGerarCustoMercadoria(List<ItemPedido> itens, Pedido pedido) {
        List<CampoAusente> ausentes = new ArrayList<>();
        List<Custo> custosMercadoria = new ArrayList<>();
        for (ItemPedido item : itens) {
            String sku = item.getSkuOrigem();
            if (sku == null || sku.isBlank()) {
                // Sem SKU na origem: nao ha o que buscar. Nao e uma
                // ausencia NOVA declarada aqui - se for relevante, e o
                // proprio adaptador quem ja documentou a falta do SKU.
                continue;
            }
            Optional<Variacao> variacaoEncontrada = repositorioVariacao.findBySku(sku);
            if (variacaoEncontrada.isEmpty()) {
                ausentes.add(new CampoAusente("item_pedido.variacao_id",
                        "SKU '" + sku + "' nao corresponde a nenhuma variacao cadastrada/sincronizada para este "
                                + "tenant - variacao_id fica NULL, nunca inventado (regra 5 do CLAUDE.md)."));
                continue;
            }

            Variacao variacao = variacaoEncontrada.get();
            item.resolverVariacao(variacao.getId());

            CongelamentoCustoMercadoria.congelar(pedido, item, variacao).ifPresentOrElse(
                    custosMercadoria::add,
                    () -> ausentes.add(new CampoAusente("custo.mercadoria",
                            "Variacao " + variacao.getId() + " (SKU '" + sku + "') nao tem custo_unitario_atual "
                                    + "cadastrado - sei qual produto e, mas nao sei quanto ele custou. "
                                    + "custo(MERCADORIA) NAO foi gravado (regra 5 do CLAUDE.md): custo zero seria "
                                    + "mentira otimista.")));
        }
        return new ResolucaoVariacoes(ausentes, custosMercadoria);
    }

    /**
     * Dívida 1 ("mesma coisa para Cliente"): quando o cliente da mesma
     * chave natural (canalId, idExterno) ja existe, enriquece com
     * {@link Cliente#atualizarAPartirDaOrigem} em vez de so ignorar o
     * cliente recem-traduzido. Quando nao existe, insere normalmente.
     */
    /**
     * @return o id REAL do cliente no banco, que NAO e necessariamente o
     *         id que o adaptador gerou ao traduzir. Quando o comprador ja
     *         existe, o id certo e o da linha existente. Quem chama
     *         precisa usar este retorno para apontar o pedido - ver
     *         {@link Pedido#resolverCliente(UUID)}, que documenta o bug
     *         que isso evita. Devolve {@code null} quando o payload nao
     *         trouxe comprador.
     */
    private UUID persistirClienteOuAtualizar(Cliente clienteTraduzido, UUID canalId) {
        if (clienteTraduzido == null) {
            return null;
        }

        Optional<Cliente> clienteExistente = (clienteTraduzido.getIdExterno() == null)
                ? Optional.empty()
                : repositorioCliente.findByCanalIdAndIdExterno(canalId, clienteTraduzido.getIdExterno());

        if (clienteExistente.isPresent()) {
            Cliente existente = clienteExistente.get();
            existente.atualizarAPartirDaOrigem(clienteTraduzido);
            repositorioCliente.save(existente);
            repositorioCliente.flush();
            return existente.getId();
        }

        repositorioCliente.save(clienteTraduzido);
        // flush() antes de voltar para persistirResultado inserir o
        // pedido: fk_pedido_cliente (V008) precisa que o cliente ja
        // exista no banco - ver comentario do flush() em persistirResultado.
        repositorioCliente.flush();
        return clienteTraduzido.getId();
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
