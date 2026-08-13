package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.catalogo.Variacao;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.pedido.FormaPagamento;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.StatusPedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes PUROS (sem Spring, sem banco) de {@link CongelamentoCustoMercadoria}.
 *
 * O metodo {@link #pedidoComVariacaoCasadaECustoCadastradoProduzRotuloCalculada}
 * e O CRITERIO DE ACEITE da PRÉ-TAREFA da Fase 3 (docs/ESTADO.md, "A
 * limitacao no 1 do produto hoje"): antes desta mudanca, nada gravava
 * custo(MERCADORIA), {@link DetectorDeLacunas} disparava a lacuna #1
 * sempre e {@link RotuloTeto#calcular} nunca devolvia
 * {@link RotuloTeto#CALCULADA}. Este teste prova que, com variacao casada
 * e custo cadastrado, agora devolve.
 */
class CongelamentoCustoMercadoriaTest {

    private static final UUID CANAL_ID = UUID.randomUUID();
    private static final OffsetDateTime FEITO_EM = OffsetDateTime.parse("2026-03-14T15:00:00-03:00");

    // ------------------------------------------------------------------
    // A conta em si
    // ------------------------------------------------------------------

    @Test
    void congelaValorComoCustoUnitarioVezesQuantidade() {
        Pedido pedido = pedido(new BigDecimal("199.9000"));
        ItemPedido item = item(pedido, new BigDecimal("2.0000"), new BigDecimal("99.9500"));
        Variacao variacao = variacao("SKU-1", new BigDecimal("41.2500"));

        Optional<Custo> resultado = CongelamentoCustoMercadoria.congelar(pedido, item, variacao);

        assertTrue(resultado.isPresent());
        Custo custo = resultado.get();
        assertEquals(NaturezaCusto.MERCADORIA, custo.getNatureza());
        assertEquals(pedido.getId(), custo.getPedidoId());
        assertEquals(item.getId(), custo.getItemPedidoId());
        // 41,2500 x 2,0000 = 82,5000 - mesmo numero do exemplo da secao 2.5.
        assertEquals(0, custo.getValor().compareTo(new BigDecimal("82.5000")), "valor congelado");
        assertEquals(4, custo.getValor().scale(), "escala de armazenamento (secao 6.1)");
        assertFalse(custo.isEhEstimativa(), "MERCADORIA e fato cadastrado, nao estimativa (secao 2.5)");
    }

    @Test
    void semCustoUnitarioAtualDevolveVazioNuncaZero() {
        Pedido pedido = pedido(new BigDecimal("100.0000"));
        ItemPedido item = item(pedido, BigDecimal.ONE, new BigDecimal("100.0000"));
        Variacao variacaoSemCusto = variacao("SKU-2", null);

        Optional<Custo> resultado = CongelamentoCustoMercadoria.congelar(pedido, item, variacaoSemCusto);

        assertTrue(resultado.isEmpty(), "sem custo_unitario_atual, nenhuma linha e criada - regra 5 do CLAUDE.md");
    }

    // ------------------------------------------------------------------
    // "Congelado na ingestao": mudar a variacao DEPOIS nao muda o custo ja gravado
    // ------------------------------------------------------------------

    @Test
    void custoJaCongeladoNaoMudaQuandoCustoUnitarioAtualDaVariacaoMudaDepois() {
        Pedido pedido = pedido(new BigDecimal("100.0000"));
        ItemPedido item = item(pedido, BigDecimal.ONE, new BigDecimal("100.0000"));
        Variacao variacaoNoMomentoDaVenda = variacao("SKU-3", new BigDecimal("50.0000"));

        Custo custoCongelado = CongelamentoCustoMercadoria.congelar(pedido, item, variacaoNoMomentoDaVenda)
                .orElseThrow();

        // O lojista "muda o custo do produto amanha" - simulado aqui como
        // uma NOVA leitura da variacao (é assim que aconteceria de
        // verdade: um UPDATE na linha, e uma consulta futura veria o novo
        // valor). O objeto Variacao e imutavel neste teste (sem setter -
        // ver Variacao.java), entao simulamos a mudanca criando uma
        // segunda leitura com o mesmo id e custo diferente.
        Variacao variacaoDepoisDoReajuste = new Variacao(variacaoNoMomentoDaVenda.getProdutoId(), "SKU-3", null,
                null, "{}", false, null, new BigDecimal("999.0000"), "BRL", null, CANAL_ID, "ext-sku-3", "{}");

        // Se o motor recalculasse a partir da variacao "atual" em vez de
        // usar o valor congelado, o custo mudaria para 999,00 - o que
        // quebraria a regra 3 do CLAUDE.md (memoria de calculo tem que
        // reproduzir o numero que ja foi mostrado ao lojista).
        assertEquals(0, custoCongelado.getValor().compareTo(new BigDecimal("50.0000")),
                "custo ja gravado continua com o valor de quando foi congelado");
        assertFalse(variacaoDepoisDoReajuste.getCustoUnitarioAtual()
                .compareTo(custoCongelado.getValor()) == 0, "sanity check: o reajuste simulado e mesmo diferente");
    }

    // ------------------------------------------------------------------
    // CRITERIO DE ACEITE: variacao casada + custo cadastrado -> CALCULADA
    // ------------------------------------------------------------------

    @Test
    void pedidoComVariacaoCasadaECustoCadastradoProduzRotuloCalculada() {
        Pedido pedido = pedido(new BigDecimal("100.0000"));
        ItemPedido item = item(pedido, BigDecimal.ONE, new BigDecimal("100.0000"));
        item.resolverVariacao(UUID.randomUUID()); // decisao 0018: pipeline ja casou o SKU
        Variacao variacao = variacao("SKU-4", new BigDecimal("50.0000"));

        Custo custoMercadoria = CongelamentoCustoMercadoria.congelar(pedido, item, variacao).orElseThrow();
        // IMPOSTO tambem precisa existir - senao a lacuna #6 (regime
        // tributario nao configurado) sozinha ja impediria CALCULADA. Nao
        // e o foco deste teste (isso e tarefa do motor de imposto, fora
        // de escopo desta PRÉ-TAREFA), entao so garantimos que a linha
        // exista.
        Custo custoImposto = new Custo(NaturezaCusto.IMPOSTO, pedido.getId(), null, null,
                new BigDecimal("6.7280"), "BRL", FEITO_EM, true, pedido.getValorTotalPedido(),
                new BigDecimal("0.067280"), null, null, "Simples, aliquota efetiva", null, null, "{}");

        List<Custo> custos = List.of(custoMercadoria, custoImposto);
        List<ItemPedido> itens = List.of(item);

        List<Lacuna> lacunas = DetectorDeLacunas.detectar(custos, itens);
        assertTrue(lacunas.isEmpty(), "sem lacunas: variacao casada + custo cadastrado + imposto configurado");

        ResultadoMargemPedido resultado = MotorMargemPedido.calcular(
                pedido.getId(), pedido.getValorTotalPedido(), custos, lacunas);

        assertEquals(RotuloTeto.CALCULADA, resultado.rotulo(),
                "antes desta mudanca isto era IMPOSSIVEL (docs/ESTADO.md, 'A limitacao no 1') - "
                        + "agora tem que sair CALCULADA");
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static Pedido pedido(BigDecimal valorTotal) {
        return new Pedido(CANAL_ID, null, "pedido-ext-1", "1001", StatusPedido.PAGO, "paid", FEITO_EM,
                valorTotal, BigDecimal.ZERO, BigDecimal.ZERO, valorTotal, null, "BRL",
                FormaPagamento.CARTAO_CREDITO, (short) 1, null, null, null, "{}");
    }

    private static ItemPedido item(Pedido pedido, BigDecimal quantidade, BigDecimal valorUnitario) {
        BigDecimal total = valorUnitario.multiply(quantidade);
        return new ItemPedido(pedido.getId(), null, "SKU-QUALQUER", "Produto de teste",
                quantidade, valorUnitario, BigDecimal.ZERO, total, "item-ext-1", "{}");
    }

    private static Variacao variacao(String sku, BigDecimal custoUnitarioAtual) {
        return new Variacao(UUID.randomUUID(), sku, null, null, "{}", false,
                new BigDecimal("199.9000"), custoUnitarioAtual, "BRL", null, CANAL_ID, "var-ext-1", "{}");
    }
}
