package com.plataforma.integracao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.canal.TipoCanal;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.pedido.FormaPagamento;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.StatusPedido;
import com.plataforma.suporte.LeitorDeFixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de tradução do AdaptadorMercadoLivre - UNITARIOS PUROS, sem
 * Spring, sem banco (instancia a classe direto). Asserções monetárias
 * SEMPRE via compareTo (nunca equals, que falharia por escala diferente
 * mesmo com o mesmo valor - ex.: 244.70 vs 244.7000).
 */
class AdaptadorMercadoLivreTest {

    private final AdaptadorMercadoLivre adaptador = new AdaptadorMercadoLivre();

    @Test
    void tipoSuportadoEMercadoLivre() {
        assertEquals(TipoCanal.MERCADO_LIVRE, adaptador.tipoSuportado());
    }

    @Test
    void traduzPedidoCompletoComValoresMonetariosExatos() {
        String payload = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-completo.json");
        UUID canalId = UUID.randomUUID();

        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, canalId);
        Pedido pedido = resultado.pedido();

        assertEquals("2000003508649999", pedido.getIdExterno());
        assertEquals(StatusPedido.PAGO, pedido.getStatus());
        assertEquals("paid", pedido.getStatusOrigem());
        assertEquals("BRL", pedido.getMoeda());
        assertEquals(canalId, pedido.getCanalId());

        // valor_bruto_itens = soma de (unit_price * quantity) dos dois
        // itens: 89.90*2 + 79.90*1 = 179.80 + 79.90 = 259.70
        assertEquals(0, pedido.getValorBrutoItens().compareTo(new BigDecimal("259.70")));
        assertEquals(4, pedido.getValorBrutoItens().scale());

        // valor_desconto = coupon.amount
        assertEquals(0, pedido.getValorDesconto().compareTo(new BigDecimal("15.00")));

        // valor_total_pedido = total_amount, direto da fonte (R1 - nunca recalculado)
        assertEquals(0, pedido.getValorTotalPedido().compareTo(new BigDecimal("244.70")));
        assertEquals(4, pedido.getValorTotalPedido().scale());

        // frete/repasse/endereco nao vem em /orders/{id}
        assertEquals(0, pedido.getValorFreteCobrado().compareTo(BigDecimal.ZERO));
        assertNull(pedido.getValorRepassePrevisto());
        assertNull(pedido.getCepEntrega());

        assertEquals(FormaPagamento.CARTAO_CREDITO, pedido.getFormaPagamento());
        assertEquals((short) 3, pedido.getQuantidadeParcelas());

        List<ItemPedido> itens = resultado.itens();
        assertEquals(2, itens.size());

        ItemPedido item1 = itens.get(0);
        assertEquals("MLB4123456789-158541288641", item1.getIdExterno());
        assertEquals("CAM-PRT-M", item1.getSkuOrigem());
        assertEquals(0, item1.getValorUnitarioBruto().compareTo(new BigDecimal("89.90")));
        assertEquals(0, item1.getValorDescontoLinha().compareTo(BigDecimal.ZERO), "full_unit_price == unit_price: sem desconto de linha");
        assertEquals(0, item1.getValorTotalLinha().compareTo(new BigDecimal("179.80")));

        ItemPedido item2 = itens.get(1);
        assertEquals("MLB4123456790", item2.getIdExterno(), "sem variation_id, id_externo e so o item.id");
        // full_unit_price=99.90, unit_price=79.90 -> desconto de linha = (99.90-79.90)*1 = 20.00
        assertEquals(0, item2.getValorDescontoLinha().compareTo(new BigDecimal("20.00")));
        assertEquals(0, item2.getValorTotalLinha().compareTo(new BigDecimal("79.90")));

        List<Custo> custos = resultado.custos();
        assertEquals(2, custos.size(), "um Custo(COMISSAO_CANAL) por item com sale_fee");
        assertTrue(custos.stream().allMatch(c -> c.getNatureza() == NaturezaCusto.COMISSAO_CANAL));
        assertTrue(custos.stream().anyMatch(c -> c.getValor().compareTo(new BigDecimal("25.17")) == 0));
        assertTrue(custos.stream().anyMatch(c -> c.getValor().compareTo(new BigDecimal("11.19")) == 0));
        assertTrue(custos.stream().allMatch(c -> pedido.getId().equals(c.getPedidoId())));
        assertTrue(custos.stream().allMatch(c -> !c.isEhEstimativa()), "sale_fee e valor informado pela fonte, nao estimativa");

        // Cliente
        assertEquals("COMPRADOR_TESTE123", resultado.cliente().getApelidoOrigem());
        assertEquals(pedido.getClienteId(), resultado.cliente().getId());

        // Campos ausentes declarados (regra 5 do CLAUDE.md)
        List<CampoAusente> ausentes = resultado.camposAusentes();
        assertTrue(ausentes.stream().anyMatch(a -> a.campo().equals("pedido.valor_frete_cobrado")));
        assertTrue(ausentes.stream().anyMatch(a -> a.campo().equals("pedido.valor_repasse_previsto")));
        assertTrue(ausentes.stream().anyMatch(a -> a.campo().contains("cep_entrega")));
    }

    @Test
    void campoAusenteNaFonteFicaNuloENuncaEhEstimado() {
        // Regra 5 do CLAUDE.md: buyer.first_name/last_name vem nulos na
        // fixture (comum no ML) - cliente.nome tem que ficar NULL, NUNCA
        // ser inventado a partir do nickname.
        String payload = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-completo.json");
        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, UUID.randomUUID());

        assertNull(resultado.cliente().getNome(), "nome nao pode ser inventado a partir do apelido");
        assertEquals("COMPRADOR_TESTE123", resultado.cliente().getApelidoOrigem(), "apelido e o que a fonte de fato entregou");

        assertTrue(resultado.camposAusentes().stream().anyMatch(a -> a.campo().equals("cliente.nome")),
                "a ausencia precisa estar DECLARADA, nao so silenciosamente nula");
    }

    @Test
    void traduzPedidoCanceladoComCaminhoTriste() {
        String payload = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-cancelado.json");
        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, UUID.randomUUID());
        Pedido pedido = resultado.pedido();

        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        assertEquals("cancelled", pedido.getStatusOrigem());
        // total_amount = 0.00 no pedido cancelado - ainda assim precisa
        // and sair exatamente 0, com escala 4, nunca null (R1: total vem
        // sempre da fonte).
        assertEquals(0, pedido.getValorTotalPedido().compareTo(BigDecimal.ZERO));
        assertEquals(4, pedido.getValorTotalPedido().scale());

        assertEquals(1, resultado.itens().size());
        assertEquals(1, resultado.custos().size(), "sale_fee ainda presente mesmo em pedido cancelado - gravado como a fonte manda");
        assertEquals(0, resultado.custos().get(0).getValor().compareTo(new BigDecimal("5.59")));

        assertEquals(FormaPagamento.PIX, pedido.getFormaPagamento());
    }

    @Test
    void payloadInvalidoLancaExcecaoPropria() {
        assertThrows(PayloadInvalidoException.class,
                () -> adaptador.traduzirPedido("{ isto nao e json valido", UUID.randomUUID()));
    }

    @Test
    void payloadSemIdLancaExcecaoPropria() {
        assertThrows(PayloadInvalidoException.class,
                () -> adaptador.traduzirPedido("{\"status\": \"paid\"}", UUID.randomUUID()));
    }

    @Test
    void variacaoIdSempreNuloTraducaoEPura() {
        // O adaptador nao consulta banco (decisao 0014/testabilidade
        // pura) - casar item vendido com variacao do catalogo e trabalho
        // do pipeline de ingestao, nao deste tradutor.
        String payload = LeitorDeFixture.ler("/fixtures/mercadolivre/pedido-completo.json");
        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, UUID.randomUUID());
        assertFalse(resultado.itens().isEmpty());
        assertTrue(resultado.itens().stream().allMatch(i -> i.getVariacaoId() == null));
    }
}
