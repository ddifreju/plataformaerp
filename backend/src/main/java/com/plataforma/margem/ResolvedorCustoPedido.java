package com.plataforma.margem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.custo.RepositorioCusto;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;

/**
 * Tarefa 14: dado um pedido, monta o custo real por natureza aplicando a
 * hierarquia de tres niveis da decisao 0019:
 * <ol>
 *   <li>valor informado pela fonte ({@code eh_estimativa = false}) - usa direto, e fato;</li>
 *   <li>senao, busca em {@code taxa_canal} a taxa vigente NA DATA DO FATO GERADOR do pedido
 *       (nunca a data de hoje) e calcula, marcando {@code eh_estimativa = true};</li>
 *   <li>senao, lacuna declarada: nenhuma linha de custo e criada, a ausencia entra na resposta.</li>
 * </ol>
 *
 * <h2>Escopo desta implementacao (relatado, nao inventado)</h2>
 * Cobre {@code COMISSAO} e {@code TARIFA_FIXA} - os dois tipos demonstrados
 * no exemplo numerico da secao 2.5 do documento fiscal, resolvidos por
 * ITEM de pedido. NAO cobre, nesta rodada:
 * <ul>
 *   <li>{@code FRETE} - de proposito: a secao 4.4 e explicita que o custo de
 *       frete NUNCA e derivado da regra, so nivel 1 ou lacuna;</li>
 *   <li>{@code PARCELAMENTO}, {@code ANTECIPACAO}, {@code ARMAZENAGEM},
 *       {@code TARIFA_ADMINISTRATIVA} - o mecanismo (nivel 1/2/3) e o
 *       mesmo, mas cada um tem regra de "quando lancar" propria (secao
 *       4.5: parcelamento so quando {@code quantidade_parcelas > 1} e o
 *       anuncio nao embute; antecipacao e opt-in do lojista e custo de
 *       PERIODO, nao de item) que nao foi implementada por falta de tempo
 *       nesta rodada - reportado no relatorio final, nao adivinhado;</li>
 *   <li>{@code tipoAnuncio} sempre curinga ({@link TaxaCanal#CURINGA}): o
 *       adaptador do Mercado Livre ainda nao preserva {@code listing_type_id}
 *       no pedido (pendencia P5 do documento fiscal, secao 10.3) - so
 *       {@code category_id} chega em {@code item_pedido.dados_origem}.</li>
 * </ul>
 *
 * NAO TESTADO CONTRA POSTGRES DE VERDADE nesta rodada (Docker indisponivel
 * no ambiente de desenvolvimento atual) - a logica de decisao pura que
 * este servico orquestra ({@link SelecaoTaxaCanal}, {@link AplicadorTaxa})
 * tem testes unitarios sem banco; a orquestracao em si (consultas,
 * idempotencia, persistencia) so foi verificada por compilacao.
 */
@Service
public class ResolvedorCustoPedido {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RepositorioCusto repositorioCusto;
    private final RepositorioTaxaCanal repositorioTaxaCanal;

    public ResolvedorCustoPedido(RepositorioCusto repositorioCusto, RepositorioTaxaCanal repositorioTaxaCanal) {
        this.repositorioCusto = repositorioCusto;
        this.repositorioTaxaCanal = repositorioTaxaCanal;
    }

    public record ResultadoResolucaoCusto(List<Custo> custos, List<Lacuna> lacunas) {
    }

    /**
     * IDEMPOTENTE: reexecutar para o mesmo pedido nao duplica linha
     * nenhuma - antes de calcular, cada combinacao (item, natureza) e
     * conferida contra o que ja existe (nivel 1 OU nivel 2 ja gravado
     * antes). TRANSACIONAL: ou grava todas as linhas novas, ou nenhuma.
     */
    @Transactional
    public ResultadoResolucaoCusto resolver(Pedido pedido, List<ItemPedido> itens) {
        List<Custo> custosExistentes = repositorioCusto.findByPedidoId(pedido.getId());
        List<Custo> novos = new ArrayList<>();
        List<Lacuna> lacunas = new ArrayList<>();

        for (ItemPedido item : itens) {
            resolverParaItem(pedido, item, TipoTaxaCanal.COMISSAO, NaturezaCusto.COMISSAO_CANAL,
                    custosExistentes, novos, lacunas);
            resolverParaItem(pedido, item, TipoTaxaCanal.TARIFA_FIXA, NaturezaCusto.TARIFA_FIXA_CANAL,
                    custosExistentes, novos, lacunas);
        }

        if (!novos.isEmpty()) {
            repositorioCusto.saveAll(novos);
        }

        List<Custo> todos = new ArrayList<>(custosExistentes.size() + novos.size());
        todos.addAll(custosExistentes);
        todos.addAll(novos);
        return new ResultadoResolucaoCusto(todos, lacunas);
    }

    private void resolverParaItem(Pedido pedido, ItemPedido item, TipoTaxaCanal tipoTaxa, NaturezaCusto naturezaAlvo,
            List<Custo> custosExistentes, List<Custo> novos, List<Lacuna> lacunas) {

        // NIVEL 1: a fonte ja informou o valor cobrado para este item+natureza?
        boolean jaFato = custosExistentes.stream()
                .anyMatch(custo -> naturezaAlvo.equals(custo.getNatureza())
                        && item.getId().equals(custo.getItemPedidoId())
                        && !custo.isEhEstimativa());
        if (jaFato) {
            return;
        }

        // Idempotencia: ja existe QUALQUER linha (nivel 1 ou 2 de execucao
        // anterior) para este item+natureza? Nao recalcula por cima - a
        // secao 8.4 e clara: recalculo e ACIONADO, nunca automatico.
        boolean jaResolvidoAntes = custosExistentes.stream()
                .anyMatch(custo -> naturezaAlvo.equals(custo.getNatureza()) && item.getId().equals(custo.getItemPedidoId()))
                || novos.stream()
                        .anyMatch(custo -> naturezaAlvo.equals(custo.getNatureza()) && item.getId().equals(custo.getItemPedidoId()));
        if (jaResolvidoAntes) {
            return;
        }

        String tipoAnuncio = TaxaCanal.CURINGA; // ver Javadoc da classe: pendencia P5
        String categoria = categoriaDoItem(item);

        if (categoria == null) {
            // GUARDA DO CURINGA (cabecalho da V013): so usa '*' quando NAO
            // existe taxa mais especifica cadastrada para este contexto -
            // senao "nao sei a categoria" viraria silenciosamente "a regra
            // geral vale", que pode estar errado.
            long maisEspecificas = repositorioTaxaCanal.contarTaxasMaisEspecificasQueCuringa(
                    pedido.getCanalId(), tipoTaxa, pedido.getFeitoEm());
            if (maisEspecificas > 0) {
                lacunas.add(CatalogoLacunas.taxaNaoCadastrada(tipoTaxa.name(), "(categoria desconhecida - "
                        + "adaptador nao preservou category_id)", tipoAnuncio, pedido.getFeitoEm()));
                return;
            }
            categoria = TaxaCanal.CURINGA;
        }

        BigDecimal valorReferencia = valorReferenciaParaFiltro(tipoTaxa, item);
        List<TaxaCanal> candidatas = repositorioTaxaCanal.buscarCandidatas(
                pedido.getCanalId(), tipoTaxa, pedido.getFeitoEm(), categoria, tipoAnuncio, valorReferencia);

        switch (SelecaoTaxaCanal.selecionar(candidatas)) {
            case ResultadoSelecaoTaxa.NaoEncontrada ignorado ->
                lacunas.add(CatalogoLacunas.taxaNaoCadastrada(tipoTaxa.name(), categoria, tipoAnuncio, pedido.getFeitoEm()));
            case ResultadoSelecaoTaxa.Ambigua ambigua ->
                throw new TaxaCanalAmbiguaException(ambigua.idTaxaA(), ambigua.idTaxaB(), ambigua.especificidade());
            case ResultadoSelecaoTaxa.Encontrada encontrada -> {
                TaxaCanal taxa = encontrada.taxa();
                BigDecimal baseIncidencia = baseIncidenciaReal(taxa.getBaseIncidencia(), item, pedido);
                AplicadorTaxa.ResultadoAplicacao aplicacao = AplicadorTaxa.aplicar(taxa, baseIncidencia);
                novos.add(new Custo(naturezaAlvo, pedido.getId(), item.getId(), null,
                        aplicacao.valor(), pedido.getMoeda(), pedido.getFeitoEm(), true,
                        aplicacao.baseCalculo(), aplicacao.aliquotaAplicada(), null, null,
                        "Calculada pela taxa " + tipoTaxa + " (" + taxa.getConfianca() + ") vigente em "
                                + taxa.getVigenciaInicio() + " - taxa_canal id " + taxa.getId(),
                        pedido.getCanalId(), null, "{}"));
            }
        }
    }

    /**
     * O valor a passar como {@code :valor} da consulta de selecao (WHERE
     * de faixa) - o "valor previsto pelo tipo_taxa" que o cabecalho da
     * V013 pede para montar a consulta ANTES de saber qual linha vence
     * (a linha so revela sua propria {@code base_incidencia} depois de
     * encontrada). Convencao usada aqui, consistente com o exemplo de
     * cadastro do cabecalho da V013 e a tabela da secao 4.3 do documento
     * fiscal: comissao incide sobre o total da linha; tarifa fixa usa a
     * faixa de preco UNITARIO.
     */
    private static BigDecimal valorReferenciaParaFiltro(TipoTaxaCanal tipoTaxa, ItemPedido item) {
        return switch (tipoTaxa) {
            case TARIFA_FIXA -> item.getValorUnitarioBruto();
            default -> item.getValorTotalLinha();
        };
    }

    /** A base REAL a aplicar, conforme a {@code base_incidencia} que a linha vencedora declarou. */
    private static BigDecimal baseIncidenciaReal(BaseIncidencia baseIncidencia, ItemPedido item, Pedido pedido) {
        return switch (baseIncidencia) {
            case VALOR_UNITARIO_ITEM -> item.getValorUnitarioBruto();
            case VALOR_TOTAL_ITEM -> item.getValorTotalLinha();
            case VALOR_TOTAL_PEDIDO -> pedido.getValorTotalPedido();
            case VALOR_FRETE -> throw new IllegalStateException(
                    "base_incidencia VALOR_FRETE nao e suportada por ResolvedorCustoPedido - frete nunca usa "
                            + "nivel 2 (secao 4.4 do documento fiscal). Corrija o cadastro da taxa " + baseIncidencia);
            case VALOR_A_RECEBER -> throw new IllegalStateException(
                    "base_incidencia VALOR_A_RECEBER e de custo de PERIODO (antecipacao), nao de item - fora "
                            + "do escopo deste resolvedor (ver Javadoc da classe)");
        };
    }

    /**
     * Le {@code category_id} de {@code item_pedido.dados_origem}
     * (decisao 0002, campo de extensao) - onde o adaptador do Mercado
     * Livre preserva a categoria do anuncio (ver
     * {@code AdaptadorMercadoLivre.extensaoItem}). Devolve {@code null}
     * quando ausente - NUNCA um valor chutado.
     */
    private static String categoriaDoItem(ItemPedido item) {
        try {
            JsonNode raiz = MAPPER.readTree(item.getDadosOrigem());
            JsonNode categoria = raiz.get("category_id");
            if (categoria == null || categoria.isNull() || categoria.asText().isBlank()) {
                return null;
            }
            return categoria.asText();
        } catch (JsonProcessingException erro) {
            return null;
        }
    }
}
