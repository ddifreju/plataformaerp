package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plataforma.auditoria.ConsultaAuditada;
import com.plataforma.auditoria.RepositorioConsultaAuditada;
import com.plataforma.custo.Custo;
import com.plataforma.custo.RepositorioCusto;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.RepositorioItemPedido;
import com.plataforma.pedido.RepositorioPedido;

/**
 * Tarefa 16: "quanto sobrou no periodo X" - agrega
 * {@link MotorMargemPedido} sobre todos os pedidos de UM canal no
 * periodo, mais o custo de periodo nao rateado ligado a esse canal, para
 * chegar em N4.
 *
 * <h2>Escopo de canal (decisao 0017) - a decisao que este servico toma e explica</h2>
 * O parametro {@code canalId} e OBRIGATORIO, nunca uma lista opcional de
 * canais. Decidido assim (em vez de "devolver separado por canal quando
 * multiplos forem pedidos") porque o proprio {@code RepositorioPedido} ja
 * exige um canal por consulta (ver {@code findByCanalIdAndFeitoEm...}) -
 * a API mais simples que ainda resolve 80% do problema (CLAUDE.md,
 * "existe caminho mais simples que resolve 80% do problema"): quem
 * precisa comparar dois canais faz duas chamadas e soma na TELA, sabendo
 * que esta somando; o backend nunca soma dois canais potencialmente
 * sobrepostos silenciosamente. Ver {@code escopoCanal} na resposta.
 *
 * <h2>Regra 3 do CLAUDE.md dentro deste servico</h2>
 * Toda chamada grava uma linha em {@code consulta_auditada} com os IDs de
 * pedido e de custo que compuseram o resultado - se o lojista contestar o
 * numero do periodo, os IDs exatos estao la.
 */
@Service
public class ServicoMargemPeriodo {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoMargemPeriodo.class);

    private final RepositorioPedido repositorioPedido;
    private final RepositorioItemPedido repositorioItemPedido;
    private final RepositorioCusto repositorioCusto;
    private final RepositorioConsultaAuditada repositorioConsultaAuditada;

    public ServicoMargemPeriodo(RepositorioPedido repositorioPedido, RepositorioItemPedido repositorioItemPedido,
            RepositorioCusto repositorioCusto, RepositorioConsultaAuditada repositorioConsultaAuditada) {
        this.repositorioPedido = repositorioPedido;
        this.repositorioItemPedido = repositorioItemPedido;
        this.repositorioCusto = repositorioCusto;
        this.repositorioConsultaAuditada = repositorioConsultaAuditada;
    }

    @Transactional
    public ResultadoMargemPeriodo calcular(OffsetDateTime inicio, OffsetDateTime fim, UUID canalId) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new PeriodoInvalidoException(inicio, fim);
        }

        List<Pedido> pedidos = repositorioPedido.findByCanalIdAndFeitoEmGreaterThanEqualAndFeitoEmLessThan(
                canalId, inicio, fim);

        BigDecimal n0 = BigDecimal.ZERO;
        BigDecimal n1 = BigDecimal.ZERO;
        BigDecimal n2 = BigDecimal.ZERO;
        BigDecimal n3 = BigDecimal.ZERO;
        Map<BlocoMargem, BigDecimal> somaDecomposicao = new EnumMap<>(BlocoMargem.class);
        Map<BlocoMargem, Boolean> contemEstimativaPorBloco = new EnumMap<>(BlocoMargem.class);
        for (BlocoMargem bloco : BlocoMargem.values()) {
            somaDecomposicao.put(bloco, BigDecimal.ZERO);
            contemEstimativaPorBloco.put(bloco, Boolean.FALSE);
        }

        // Lacunas deduplicadas por codigo: agregar 500 pedidos com o mesmo
        // "custo do produto nao cadastrado" nao deveria virar 500 linhas
        // repetidas na resposta. SIMPLIFICACAO DECLARADA (nao inventada):
        // a contagem de QUANTOS pedidos tem cada lacuna nao e mantida
        // aqui - a resposta diz "existe pelo menos um pedido faltando X",
        // nao quantos. Ver relatorio final da tarefa.
        Map<String, Lacuna> lacunasPorCodigo = new LinkedHashMap<>();
        Set<UUID> idsPedidoUsados = new LinkedHashSet<>();
        Set<UUID> idsCustoUsados = new LinkedHashSet<>();

        for (Pedido pedido : pedidos) {
            List<ItemPedido> itens = repositorioItemPedido.findByPedidoId(pedido.getId());
            List<Custo> custos = repositorioCusto.findByPedidoId(pedido.getId());

            List<Lacuna> lacunasPedido = new ArrayList<>(DetectorDeLacunas.detectar(custos, itens));
            ConferenciaRepasse.ResultadoConferencia conferencia = ConferenciaRepasse.conferir(
                    pedido.getValorTotalPedido(), custos, pedido.getValorRepassePrevisto());
            conferencia.lacuna().ifPresent(lacunasPedido::add);

            // O alerta e diferente da lacuna: lacuna e "nao sei", alerta e
            // "sei que esta errado". E o UNICO sinal do sistema que aponta
            // para custo contado em dobro, taxa cadastrada errada ou
            // estorno nao lancado.
            //
            // Ele NAO vira Lacuna de proposito (ver ConferenciaRepasse):
            // lacuna puxa a margem numa direcao conhecida e alimenta o
            // rotulo do teto; alerta nao tem direcao definida.
            //
            // Mas tambem nao pode ser descartado em silencio - detectar um
            // problema e nao contar a ninguem e pior do que nao detectar,
            // porque cria falsa sensacao de cobertura. Ate existir campo
            // proprio na resposta, no minimo fica no log com o id do
            // pedido, que e o suficiente para investigar.
            conferencia.alerta().ifPresent(alerta ->
                    LOG.warn("Repasse divergente para CIMA no pedido {}: {}. "
                            + "Isto sugere custo faltando, taxa cadastrada errada ou estorno nao lancado - investigar.",
                            pedido.getId(), alerta));

            ResultadoMargemPedido resultadoPedido = MotorMargemPedido.calcular(
                    pedido.getId(), pedido.getValorTotalPedido(), custos, lacunasPedido);

            n0 = n0.add(resultadoPedido.faturamentoBrutoN0());
            n1 = n1.add(resultadoPedido.receitaLiquidaN1());
            n2 = n2.add(resultadoPedido.margemContribuicaoN2());
            n3 = n3.add(resultadoPedido.resultadoPedidoN3());

            for (MemoriaCalculoBloco bloco : resultadoPedido.decomposicao()) {
                somaDecomposicao.merge(bloco.bloco(), bloco.valor(), BigDecimal::add);
                if (bloco.contemEstimativa()) {
                    contemEstimativaPorBloco.put(bloco.bloco(), Boolean.TRUE);
                }
            }
            for (Lacuna lacuna : resultadoPedido.lacunas()) {
                lacunasPorCodigo.putIfAbsent(lacuna.codigo(), lacuna);
            }

            idsPedidoUsados.add(pedido.getId());
            idsCustoUsados.addAll(resultadoPedido.idsCustoUsados());
        }

        // N4: custo de periodo NAO rateado, ligado diretamente a este
        // canal (contrato S4 da V010, escopado por canal.decisao 0017).
        List<Custo> custosDePeriodoDoCanal = repositorioCusto.buscarCustoDePeriodoDoCanal(canalId, inicio, fim);
        BigDecimal custosPeriodoNaoRateados = custosDePeriodoDoCanal.stream()
                .map(Custo::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        idsCustoUsados.addAll(custosDePeriodoDoCanal.stream().map(Custo::getId).toList());
        BigDecimal n4 = n3.subtract(custosPeriodoNaoRateados);

        List<Lacuna> lacunasFinais = List.copyOf(lacunasPorCodigo.values());
        RotuloTeto rotulo = RotuloTeto.calcular(lacunasFinais);

        List<MemoriaCalculoBlocoPeriodo> decomposicao = new ArrayList<>(BlocoMargem.values().length);
        for (BlocoMargem bloco : BlocoMargem.values()) {
            decomposicao.add(new MemoriaCalculoBlocoPeriodo(bloco, somaDecomposicao.get(bloco), contemEstimativaPorBloco.get(bloco)));
        }

        var margemContribuicaoPercentual = MotorMargemPedido.percentual(n2, n0);
        var margemLiquidaPercentual = MotorMargemPedido.percentual(n3, n0);

        registrarAuditoria(inicio, fim, canalId, idsPedidoUsados, idsCustoUsados);

        String escopoCanal = "Escopo: canal " + canalId + " apenas (decisao 0017) - "
                + "para comparar canais, faca uma chamada por canal; o backend nunca soma canais "
                + "potencialmente sobrepostos.";

        return new ResultadoMargemPeriodo(canalId, inicio, fim, escopoCanal, n0, n1, n2, n3, n4,
                margemContribuicaoPercentual, margemLiquidaPercentual, List.copyOf(decomposicao), lacunasFinais,
                rotulo, pedidos.size(), Set.copyOf(idsPedidoUsados), Set.copyOf(idsCustoUsados));
    }

    /**
     * Regra 3 do CLAUDE.md: grava a "consulta" (aqui, a combinacao de
     * filtros que produziu o numero - nao ha um unico SQL literal, porque
     * o calculo e feito em Java sobre varias consultas) e os IDs
     * retornados, para reconstruir o numero em minutos se for contestado.
     */
    private void registrarAuditoria(OffsetDateTime inicio, OffsetDateTime fim, UUID canalId,
            Set<UUID> idsPedido, Set<UUID> idsCusto) {
        String descricaoConsulta = "ServicoMargemPeriodo.calcular: pedidos de canal_id=" + canalId
                + " com feito_em em [" + inicio + ", " + fim + "), mais custo de periodo nao rateado do mesmo canal "
                + "no mesmo intervalo (contrato S4 da V010).";

        List<UUID> todosOsIds = new ArrayList<>(idsPedido.size() + idsCusto.size());
        todosOsIds.addAll(idsPedido);
        todosOsIds.addAll(idsCusto);

        repositorioConsultaAuditada.save(new ConsultaAuditada(
                null, descricaoConsulta, todosOsIds.toArray(new UUID[0]), todosOsIds.size(), "ServicoMargemPeriodo"));
    }
}
