package com.plataforma.margem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import com.plataforma.catalogo.Variacao;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;

/**
 * PRÉ-TAREFA da Fase 3 (docs/ESTADO.md, "A limitacao no 1 do produto
 * hoje"): monta a linha custo(MERCADORIA) que, ate aqui, ninguem gravava.
 * Sem ela, {@link DetectorDeLacunas} sempre dispara
 * {@link CatalogoLacunas#custoMercadoriaNaoCadastrado()} e
 * {@link RotuloTeto#calcular} nunca devolve {@link RotuloTeto#CALCULADA}.
 *
 * Classe PURA (sem Spring, sem banco) - a mesma separacao que
 * {@link ResolvedorCustoPedido}/{@link AplicadorTaxa} ja usam: quem TEM
 * acesso ao banco ({@code com.plataforma.ingestao.ServicoIngestao}, que ja
 * busca a {@link Variacao} por SKU para a divida 3/decisao 0018) so
 * PRECISA chamar {@link #congelar} com o que ja tem em maos - a conta em
 * si nao depende de nenhuma consulta nova, entao fica aqui, testavel sem
 * Testcontainers.
 *
 * <h2>"Congelado na ingestao" (secao 2.5 do documento fiscal)</h2>
 * O valor de {@code variacao.custo_unitario_atual} e COPIADO agora, uma
 * unica vez, para dentro de {@code custo.valor} - nunca mais recalculado a
 * partir da variacao. Se o lojista mudar o custo do produto amanha, a
 * margem de um pedido ja ingerido (e possivelmente ja mostrada a ele) NAO
 * PODE mudar sozinha - senao a memoria de calculo (regra 3 do CLAUDE.md)
 * deixaria de reproduzir o numero que ja foi apresentado. Por isso este
 * metodo LE {@code custoUnitarioAtual} uma unica vez e grava um
 * {@link BigDecimal} congelado, nunca uma referencia viva a variacao.
 * Corrigir o custo de um pedido passado, se um dia for preciso, e
 * responsabilidade de outra rotina (recalculo explicito, nunca automatico
 * - mesma disciplina da secao 8.4 sobre taxa_canal).
 *
 * <h2>Regra 5 do CLAUDE.md: nunca inventar dado</h2>
 * Sem {@code custo_unitario_atual} cadastrado, {@link #congelar} devolve
 * {@link Optional#empty()} e NENHUMA linha e criada. Custo zero seria
 * mentira - e mentira OTIMISTA (infla a margem em vez de escondê-la, a
 * pior direcao possivel). A ausencia fica para {@link DetectorDeLacunas}
 * declarar (lacuna #1 do catalogo).
 */
public final class CongelamentoCustoMercadoria {

    /** Escala de armazenamento (secao 6.1: "4 casas, HALF_UP, ao gravar custo.valor"). */
    private static final int ESCALA_ARMAZENAMENTO = 4;

    private CongelamentoCustoMercadoria() {
        // classe utilitaria: sem instancia
    }

    /**
     * @param pedido   o pedido sendo ingerido - fonte de {@code pedidoId},
     *                 {@code moeda} e {@code competenciaEm} (usa
     *                 {@link Pedido#getFeitoEm()}, a data do fato gerador,
     *                 nunca "agora": mesma convencao de
     *                 {@link ResolvedorCustoPedido})
     * @param item     o item JA casado com a variacao (quem chama resolve
     *                 isso antes - este metodo nao busca SKU, so congela
     *                 o custo de uma variacao que o chamador ja encontrou)
     * @param variacao a variacao casada, de onde sai
     *                 {@code custo_unitario_atual}
     * @return o {@code custo(MERCADORIA)} pronto para gravar, ou
     *         {@link Optional#empty()} quando {@code custo_unitario_atual}
     *         e nulo (nao cadastrado pelo lojista). Distinto de "SKU nao
     *         casou": aqui SABEMOS o produto, so NAO SABEMOS o custo dele
     *         - sao dois problemas diferentes para o lojista resolver, e
     *         quem chama deve declarar cada um com sua propria mensagem
     *         (ver {@code ServicoIngestao.resolverVariacoesEGerarCustoMercadoria}).
     */
    public static Optional<Custo> congelar(Pedido pedido, ItemPedido item, Variacao variacao) {
        BigDecimal custoUnitario = variacao.getCustoUnitarioAtual();
        if (custoUnitario == null) {
            return Optional.empty();
        }

        // Arredonda UMA VEZ, no fim, ao gravar (secao 6.2) - nao ha cadeia
        // de calculo aqui para manter em escala intermediaria, e uma
        // multiplicacao so.
        BigDecimal valor = custoUnitario.multiply(item.getQuantidade())
                .setScale(ESCALA_ARMAZENAMENTO, RoundingMode.HALF_UP);

        String descricao = "custo_unitario_atual (" + custoUnitario + ") x quantidade (" + item.getQuantidade()
                + ") da variacao " + variacao.getId() + " (SKU '" + variacao.getSku() + "'), CONGELADO na "
                + "ingestao em " + pedido.getFeitoEm() + " - mudanca futura em variacao.custo_unitario_atual "
                + "NAO altera esta linha (secao 2.5 do documento fiscal).";

        // eh_estimativa = false: a tabela da secao 2.5 marca a linha
        // MERCADORIA como "nao" estimativa - e o custo real de aquisicao,
        // cadastrado pelo lojista, nao uma taxa calculada por regra (o
        // NIVEL 2 da hierarquia de taxa_canal, esse sim eh_estimativa =
        // true - ver ResolvedorCustoPedido).
        //
        // base_calculo/aliquota_aplicada ficam NULL: MERCADORIA nao e uma
        // aliquota sobre uma base, e quantidade x custo unitario direto -
        // os dois campos so fazem sentido para custo percentual (ver
        // Custo.baseCalculo, comentario da V010).
        //
        // canal_id fica NULL: este custo nao vem do canal de venda, vem
        // do CATALOGO do proprio lojista - preencher canal_id sugeriria
        // "informado pela fonte do marketplace", que nao e o caso.
        return Optional.of(new Custo(NaturezaCusto.MERCADORIA, pedido.getId(), item.getId(), null,
                valor, pedido.getMoeda(), pedido.getFeitoEm(), false,
                null, null, null, null, descricao, null, null, "{}"));
    }
}
