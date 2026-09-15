package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plataforma.auditoria.ConsultaAuditada;
import com.plataforma.auditoria.RepositorioConsultaAuditada;
import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.comum.tenant.ContextoTenant;

/**
 * Tarefa 32 (decisao 0033): {@code POST /api/margem/periodo/consolidado} -
 * "quanto sobrou no periodo X, somando MAIS DE UM canal" - a pergunta que
 * a decisao 0017/0021 bloqueou ate existir uma forma de PROVAR que o
 * conjunto pedido nao se sobrepoe.
 *
 * <h2>A soma so acontece se o conjunto for COMPROVADAMENTE disjunto</h2>
 * Antes de somar qualquer coisa, {@link RepositorioDisjuncaoDeCanais}
 * verifica os canais pedidos contra o escopo declarado de cada um
 * (migration V016). O conjunto e disjunto se e somente se NENHUMA linha
 * vier com {@code motivoDoBloqueio} preenchido. Havendo qualquer bloqueio,
 * a resposta e uma RECUSA (409), nunca um numero - regra 5 do CLAUDE.md:
 * "se a fonte nao tem, a resposta e 'nao tenho esse dado'", nunca uma
 * soma que pode estar contando a mesma venda duas vezes.
 *
 * <h2>Formato da recusa - DECISAO TOMADA E JUSTIFICADA</h2>
 * {@link ConjuntoDeCanaisNaoDisjuntoException}, traduzida por
 * {@code TratadorGlobalDeErros} para {@code 409} no MESMO formato
 * {@code ErroApi} do resto da API (nao um corpo estruturado a parte) -
 * ver o Javadoc do handler para a justificativa completa. A mensagem
 * nomeia CADA canal bloqueado e o motivo, e no caso
 * {@code ESPELHADO_POR_OUTRO_DO_CONJUNTO} nomeia OS DOIS LADOS do par
 * (o FONTE_PRIMARIA e o ESPELHO que o mirra dentro do mesmo conjunto
 * pedido).
 *
 * <h2>Como a soma e feita, sendo disjunta</h2>
 * Um canal de cada vez, REUSANDO {@link ServicoMargemPeriodo#calcular}
 * (o motor de calculo por pedido nao e duplicado aqui) - depois:
 * <ul>
 *   <li>N0-N4: soma direta dos resultados por canal, em ESCALA DE
 *       ARMAZENAMENTO (4 casas) - nunca arredondados antes de somar
 *       (regra 1 da secao 6.4 do documento fiscal, a mesma que
 *       {@link RespostaMargemPeriodo} ja aplica por canal);</li>
 *   <li>percentuais: RECALCULADOS a partir das somas
 *       ({@link MotorMargemPedido#percentual}) - nunca media dos
 *       percentuais por canal, que seria matematicamente errado sempre
 *       que os canais tem faturamentos diferentes;</li>
 *   <li>rotulo do conjunto: o PIOR entre os rotulos dos canais somados
 *       (INDETERMINADA &gt; COM_TETO &gt; CALCULADA - a ordem de
 *       declaracao de {@link RotuloTeto} ja e essa, do melhor para o
 *       pior, entao o pior e o de MAIOR ordinal);</li>
 *   <li>lacunas: uniao deduplicada por codigo (mesmo criterio de
 *       {@link ServicoMargemPeriodo}, agora atraves de canais tambem, nao
 *       so atraves de pedidos);</li>
 *   <li>{@code idsPedidoUsados}/{@code idsCustoUsados}: uniao (Set, nunca
 *       duplica - cada pedido pertence a exatamente um canal, entao nem
 *       haveria o que deduplicar de fato, mas Set e a estrutura correta
 *       de qualquer forma).</li>
 * </ul>
 *
 * <h2>Regra 3 do CLAUDE.md - DUAS trilhas de auditoria, de proposito</h2>
 * Cada chamada a {@link ServicoMargemPeriodo#calcular} JA grava sua
 * propria {@code ConsultaAuditada} (auditoria POR CANAL, inalterada).
 * Este servico grava uma TERCEIRA linha, a mais, com os ids de pedido/
 * custo de TODOS os canais somados e o periodo - a prova DIRETA do
 * TOTAL CONSOLIDADO. Sem ela, provar o numero consolidado exigiria achar
 * e somar manualmente N linhas de auditoria separadas; com ela, uma
 * linha so reconstroi a soma exibida.
 */
@Service
public class ServicoMargemPeriodoConsolidada {

    private final RepositorioCanal repositorioCanal;
    private final RepositorioDisjuncaoDeCanais repositorioDisjuncaoDeCanais;
    private final ServicoMargemPeriodo servicoMargemPeriodo;
    private final RepositorioConsultaAuditada repositorioConsultaAuditada;

    public ServicoMargemPeriodoConsolidada(RepositorioCanal repositorioCanal,
            RepositorioDisjuncaoDeCanais repositorioDisjuncaoDeCanais, ServicoMargemPeriodo servicoMargemPeriodo,
            RepositorioConsultaAuditada repositorioConsultaAuditada) {
        this.repositorioCanal = repositorioCanal;
        this.repositorioDisjuncaoDeCanais = repositorioDisjuncaoDeCanais;
        this.servicoMargemPeriodo = servicoMargemPeriodo;
        this.repositorioConsultaAuditada = repositorioConsultaAuditada;
    }

    /**
     * {@code noRollbackFor} - achado da tarefa 33: {@code ServicoPergunta}
     * chama este método de DENTRO da sua própria transação (ele também é
     * {@code @Transactional}, propagação REQUIRED, então as duas
     * participam da MESMA transação física) e PEGA
     * {@link ConjuntoDeCanaisNaoDisjuntoException} para virar
     * ESCLARECIMENTO, continuando a escrever a própria auditoria na
     * sequência. Sem {@code noRollbackFor}, o proxy transacional deste
     * método já marca a transação como rollback-only no INSTANTE em que a
     * exceção cruza esta borda - antes mesmo de {@code ServicoPergunta}
     * conseguir capturá-la - e o commit no fim de
     * {@code ServicoPergunta.responder} falha com
     * {@code UnexpectedRollbackException} (500), mesmo a exceção tendo
     * sido tratada. É seguro não forçar rollback aqui porque, no ponto em
     * que esta exceção é lançada, NENHUMA escrita aconteceu ainda nesta
     * chamada (a verificação de disjunção é toda leitura; a soma e o
     * registro de auditoria só rodam depois dela) - não há o que desfazer.
     * {@code POST /api/margem/periodo/consolidado} (chamado direto pelo
     * controller, sem transação por fora) continua se comportando
     * exatamente igual: a exceção ainda propaga e ainda vira 409.
     */
    @Transactional(noRollbackFor = ConjuntoDeCanaisNaoDisjuntoException.class)
    public ResultadoMargemConsolidada calcular(OffsetDateTime inicio, OffsetDateTime fim, List<UUID> canaisPedidos) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new PeriodoInvalidoException(inicio, fim);
        }

        List<UUID> canaisIncluidos = resolverCanais(canaisPedidos);

        if (canaisIncluidos.isEmpty()) {
            // Tenant sem NENHUM canal ativo - nao ha o que verificar nem
            // somar. Zero e o numero HONESTO aqui (nenhum canal omitido:
            // a lista vazia na resposta prova que nenhum existia), nao um
            // erro - RepositorioDisjuncaoDeCanais.verificar recusaria uma
            // lista vazia por outro motivo (parametro invalido), entao
            // nem chegamos a chama-lo.
            ResultadoMargemConsolidada vazio = agregar(List.of(), inicio, fim, List.of());
            registrarAuditoria(inicio, fim, List.of(), vazio);
            return vazio;
        }

        UUID tenantId = ContextoTenant.atual();
        List<LinhaDisjuncaoCanal> linhas = repositorioDisjuncaoDeCanais.verificar(tenantId, canaisIncluidos);
        List<LinhaDisjuncaoCanal> bloqueando = linhas.stream().filter(LinhaDisjuncaoCanal::bloqueiaASoma).toList();
        if (!bloqueando.isEmpty()) {
            throw new ConjuntoDeCanaisNaoDisjuntoException(construirMensagemDeRecusa(bloqueando, linhas));
        }

        List<ResultadoMargemPeriodo> porCanal = new ArrayList<>(canaisIncluidos.size());
        for (UUID canalId : canaisIncluidos) {
            porCanal.add(servicoMargemPeriodo.calcular(inicio, fim, canalId));
        }

        ResultadoMargemConsolidada resultado = agregar(canaisIncluidos, inicio, fim, porCanal);
        registrarAuditoria(inicio, fim, canaisIncluidos, resultado);
        return resultado;
    }

    /**
     * {@code canaisPedidos} nulo ou vazio significa "todos os canais
     * ATIVOS do tenant" (decisao tomada aqui, nunca no DTO da requisicao -
     * resolver o default e regra de negocio). Devolve INCLUSIVE canais
     * {@code NAO_DECLARADO}/{@code ESPELHO} quando a lista e explicita ou
     * quando resolvida por default: a recusa por escopo faltando e
     * comportamento CORRETO e esperado (0033, "fail closed sempre") -
     * nunca filtrado silenciosamente aqui.
     */
    private List<UUID> resolverCanais(List<UUID> canaisPedidos) {
        if (canaisPedidos != null && !canaisPedidos.isEmpty()) {
            return List.copyOf(canaisPedidos);
        }
        return repositorioCanal.findAllByOrderByNomeAsc().stream()
                .filter(Canal::isAtivo)
                .map(Canal::getId)
                .toList();
    }

    // ==================================================================
    // Mensagem de recusa
    // ==================================================================

    private static String construirMensagemDeRecusa(List<LinhaDisjuncaoCanal> bloqueando,
            List<LinhaDisjuncaoCanal> todas) {
        StringBuilder mensagem = new StringBuilder(
                "O conjunto de canais pedido nao e comprovadamente disjunto - nao e seguro somar (decisao 0033). ");
        for (LinhaDisjuncaoCanal linha : bloqueando) {
            mensagem.append(descreverBloqueio(linha, todas)).append(' ');
        }
        return mensagem.toString().trim();
    }

    private static String descreverBloqueio(LinhaDisjuncaoCanal linha, List<LinhaDisjuncaoCanal> todas) {
        return switch (linha.motivoDoBloqueio()) {
            case CANAL_INEXISTENTE -> "Canal " + linha.canalId() + " nao existe ou nao pertence a este tenant.";
            case NAO_DECLARADO -> "Canal " + identificar(linha) + " ainda nao tem escopo declarado - declare "
                    + "FONTE_PRIMARIA ou ESPELHO (POST /api/canais/" + linha.canalId() + "/escopo) antes de somar.";
            case E_ESPELHO -> "Canal " + identificar(linha) + " e declarado ESPELHO"
                    + (linha.espelhaCanalId() != null ? " de " + linha.espelhaCanalId() : "")
                    + " - canais ESPELHO nunca entram numa soma.";
            case ESPELHADO_POR_OUTRO_DO_CONJUNTO -> {
                Optional<LinhaDisjuncaoCanal> espelho = todas.stream()
                        .filter(outra -> outra.motivoDoBloqueio() == MotivoBloqueioDeSoma.E_ESPELHO)
                        .filter(outra -> linha.canalId().equals(outra.espelhaCanalId()))
                        .findFirst();
                yield "Canal " + identificar(linha) + " e FONTE_PRIMARIA, mas o canal "
                        + espelho.map(ServicoMargemPeriodoConsolidada::identificar).orElse("(fora deste conjunto)")
                        + " - presente no MESMO conjunto pedido - e declarado ESPELHO dele. Somar os dois contaria "
                        + "a mesma venda duas vezes: remova um dos dois do conjunto.";
            }
        };
    }

    private static String identificar(LinhaDisjuncaoCanal linha) {
        return linha.codigo() != null ? linha.codigo() + " (" + linha.canalId() + ")" : linha.canalId().toString();
    }

    // ==================================================================
    // Agregacao (unitariamente testada em ServicoMargemPeriodoConsolidadaTest)
    // ==================================================================

    static ResultadoMargemConsolidada agregar(List<UUID> canaisIncluidos, OffsetDateTime inicio, OffsetDateTime fim,
            List<ResultadoMargemPeriodo> porCanal) {
        if (porCanal.isEmpty()) {
            List<MemoriaCalculoBlocoPeriodo> decomposicaoZerada = new ArrayList<>(BlocoMargem.values().length);
            for (BlocoMargem bloco : BlocoMargem.values()) {
                decomposicaoZerada.add(new MemoriaCalculoBlocoPeriodo(bloco, BigDecimal.ZERO, false));
            }
            return new ResultadoMargemConsolidada(canaisIncluidos, inicio, fim,
                    escopoConsolidadoTexto(canaisIncluidos), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, Optional.empty(), Optional.empty(), decomposicaoZerada,
                    List.of(), RotuloTeto.CALCULADA, 0, Set.of(), Set.of());
        }

        BigDecimal n0 = BigDecimal.ZERO;
        BigDecimal n1 = BigDecimal.ZERO;
        BigDecimal n2 = BigDecimal.ZERO;
        BigDecimal n3 = BigDecimal.ZERO;
        BigDecimal n4 = BigDecimal.ZERO;
        Map<BlocoMargem, BigDecimal> somaDecomposicao = new EnumMap<>(BlocoMargem.class);
        Map<BlocoMargem, Boolean> contemEstimativaPorBloco = new EnumMap<>(BlocoMargem.class);
        for (BlocoMargem bloco : BlocoMargem.values()) {
            somaDecomposicao.put(bloco, BigDecimal.ZERO);
            contemEstimativaPorBloco.put(bloco, Boolean.FALSE);
        }
        Map<String, Lacuna> lacunasPorCodigo = new LinkedHashMap<>();
        Set<UUID> idsPedidoUsados = new LinkedHashSet<>();
        Set<UUID> idsCustoUsados = new LinkedHashSet<>();
        int quantidadePedidos = 0;
        List<RotuloTeto> rotulos = new ArrayList<>(porCanal.size());

        for (ResultadoMargemPeriodo resultado : porCanal) {
            n0 = n0.add(resultado.faturamentoBrutoN0());
            n1 = n1.add(resultado.receitaLiquidaN1());
            n2 = n2.add(resultado.margemContribuicaoN2());
            n3 = n3.add(resultado.resultadoPeriodoN3());
            n4 = n4.add(resultado.lucroOperacionalN4());

            for (MemoriaCalculoBlocoPeriodo bloco : resultado.decomposicao()) {
                somaDecomposicao.merge(bloco.bloco(), bloco.valor(), BigDecimal::add);
                if (bloco.contemEstimativa()) {
                    contemEstimativaPorBloco.put(bloco.bloco(), Boolean.TRUE);
                }
            }
            for (Lacuna lacuna : resultado.lacunas()) {
                lacunasPorCodigo.putIfAbsent(lacuna.codigo(), lacuna);
            }
            idsPedidoUsados.addAll(resultado.idsPedidoUsados());
            idsCustoUsados.addAll(resultado.idsCustoUsados());
            quantidadePedidos += resultado.quantidadePedidos();
            rotulos.add(resultado.rotulo());
        }

        List<MemoriaCalculoBlocoPeriodo> decomposicao = new ArrayList<>(BlocoMargem.values().length);
        for (BlocoMargem bloco : BlocoMargem.values()) {
            decomposicao.add(new MemoriaCalculoBlocoPeriodo(bloco, somaDecomposicao.get(bloco),
                    contemEstimativaPorBloco.get(bloco)));
        }

        // Percentuais RECALCULADOS a partir das somas - nunca media dos
        // percentuais por canal (CLAUDE.md, regra 2, e o pedido explicito
        // da tarefa 32).
        Optional<BigDecimal> margemContribuicaoPercentual = MotorMargemPedido.percentual(n2, n0);
        Optional<BigDecimal> margemLiquidaPercentual = MotorMargemPedido.percentual(n3, n0);

        // Pior rotulo = maior ordinal (RotuloTeto e declarado na ordem
        // CALCULADA, COM_TETO, INDETERMINADA - do melhor para o pior).
        RotuloTeto piorRotulo = Collections.max(rotulos);

        return new ResultadoMargemConsolidada(canaisIncluidos, inicio, fim, escopoConsolidadoTexto(canaisIncluidos),
                n0, n1, n2, n3, n4, margemContribuicaoPercentual, margemLiquidaPercentual, List.copyOf(decomposicao),
                List.copyOf(lacunasPorCodigo.values()), piorRotulo, quantidadePedidos, Set.copyOf(idsPedidoUsados),
                Set.copyOf(idsCustoUsados));
    }

    private static String escopoConsolidadoTexto(List<UUID> canaisIncluidos) {
        return "Escopo: soma consolidada de " + canaisIncluidos.size() + " canal(is) comprovadamente disjunto(s) "
                + "(decisao 0033) - " + canaisIncluidos + ". Canal sem escopo declarado, ESPELHO, ou espelhado por "
                + "outro canal deste mesmo conjunto e sempre recusado antes de somar, nunca incluido "
                + "silenciosamente.";
    }

    // ==================================================================
    // Regra 3 do CLAUDE.md
    // ==================================================================

    private void registrarAuditoria(OffsetDateTime inicio, OffsetDateTime fim, List<UUID> canaisIncluidos,
            ResultadoMargemConsolidada resultado) {
        String descricaoConsulta = "ServicoMargemPeriodoConsolidada.calcular: soma disjunta de canal_id IN ("
                + canaisIncluidos + ") com feito_em em [" + inicio + ", " + fim + "). Cada canal ja e auditado "
                + "individualmente por ServicoMargemPeriodo.calcular; este registro e a prova DIRETA do TOTAL "
                + "CONSOLIDADO exibido.";

        List<UUID> todosOsIds = new ArrayList<>(resultado.idsPedidoUsados().size() + resultado.idsCustoUsados().size());
        todosOsIds.addAll(resultado.idsPedidoUsados());
        todosOsIds.addAll(resultado.idsCustoUsados());

        repositorioConsultaAuditada.save(new ConsultaAuditada(
                null, descricaoConsulta, todosOsIds.toArray(new UUID[0]), todosOsIds.size(),
                "ServicoMargemPeriodoConsolidada"));
    }
}
