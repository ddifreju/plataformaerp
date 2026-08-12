package com.plataforma.integracao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.canal.TipoCanal;
import com.plataforma.cliente.TipoCliente;
import com.plataforma.cliente.TipoDocumento;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.StatusPedido;
import com.plataforma.suporte.LeitorDeFixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de tradução do AdaptadorBling - UNITARIOS PUROS, sem Spring, sem
 * banco. Asserções monetárias sempre via compareTo.
 */
class AdaptadorBlingTest {

    private final AdaptadorBling adaptador = new AdaptadorBling();

    @Test
    void tipoSuportadoEErpBling() {
        assertEquals(TipoCanal.ERP_BLING, adaptador.tipoSuportado());
    }

    @Test
    void traduzPedidoVendaComValoresMonetariosExatos() {
        String payload = LeitorDeFixture.ler("/fixtures/bling/pedido-venda.json");
        UUID canalId = UUID.randomUUID();

        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, canalId);
        Pedido pedido = resultado.pedido();

        assertEquals("987654321", pedido.getIdExterno());
        assertEquals("15234", pedido.getCodigoExibicao());
        assertEquals("Atendido", pedido.getStatusOrigem());
        // situacao configuravel por tenant, sem tabela de traducao ainda:
        // AGUARDANDO_PAGAMENTO neutro, nunca um chute de que "Atendido" e "pago".
        assertEquals(StatusPedido.AGUARDANDO_PAGAMENTO, pedido.getStatus());

        assertEquals(0, pedido.getValorBrutoItens().compareTo(new BigDecimal("259.70")));
        assertEquals(0, pedido.getValorDesconto().compareTo(BigDecimal.ZERO));
        // total = 264.70, NUNCA a soma das parcelas (88.24+88.23+88.23) -
        // mesmo que, nesta fixture, os dois numeros coincidam (ver teste
        // dedicado totalPedidoNuncaEhSomaDasParcelas para o caso em que
        // NAO coincidem).
        assertEquals(0, pedido.getValorTotalPedido().compareTo(new BigDecimal("264.70")));

        // transporte.frete=5.00 com fretePorConta=0 (!=1): classificado
        // como CUSTO do lojista, nao receita.
        assertEquals(0, pedido.getValorFreteCobrado().compareTo(BigDecimal.ZERO));
        assertNull(pedido.getValorRepassePrevisto(), "ERP nunca tem repasse de canal - nao e lacuna, e esperado");

        assertEquals("04101000", pedido.getCepEntrega());
        assertEquals("Sao Paulo", pedido.getCidadeEntrega());
        assertEquals("SP", pedido.getUfEntrega());

        assertNull(pedido.getFormaPagamento(), "forma_pagamento configuravel por tenant, sem tabela de traducao ainda");
        assertEquals((short) 3, pedido.getQuantidadeParcelas());

        List<ItemPedido> itens = resultado.itens();
        assertEquals(2, itens.size());
        ItemPedido item1 = itens.get(0);
        assertEquals("CAM-PRT-M", item1.getSkuOrigem());
        assertEquals(0, item1.getValorUnitarioBruto().compareTo(new BigDecimal("89.90")));
        assertEquals(0, item1.getValorTotalLinha().compareTo(new BigDecimal("179.80")));

        List<Custo> custos = resultado.custos();
        assertTrue(custos.stream().noneMatch(c -> c.getNatureza() == NaturezaCusto.COMISSAO_CANAL),
                "Bling e ERP - NUNCA gera comissao de canal (isso e do adaptador de ML)");
        assertTrue(custos.stream().anyMatch(c -> c.getNatureza() == NaturezaCusto.FRETE
                && c.getValor().compareTo(new BigDecimal("5.00")) == 0
                && c.isEhEstimativa()),
                "frete pago pelo lojista, classificacao CIF/FOB incerta -> eh_estimativa=true");
        assertEquals(3, custos.stream().filter(c -> c.getNatureza() == NaturezaCusto.IMPOSTO).count(),
                "ICMS, IPI e ICMS-ST, cada um sua propria linha");

        // Cliente
        assertEquals("Fulano de Tal da Silva", resultado.cliente().getNome());
        assertEquals(TipoCliente.PESSOA_FISICA, resultado.cliente().getTipo());
        assertNull(resultado.cliente().getDocumentoHash(), "sem infra de HMAC nesta rodada - documento nunca gravado em claro nem hash");
        assertEquals("+5511999998888", resultado.cliente().getTelefone());

        List<CampoAusente> ausentes = resultado.camposAusentes();
        assertTrue(ausentes.stream().anyMatch(a -> a.campo().equals("pedido.status")));
        assertTrue(ausentes.stream().anyMatch(a -> a.campo().equals("pedido.forma_pagamento")));
        assertTrue(ausentes.stream().anyMatch(a -> a.campo().equals("cliente.documento_hash")));
        assertTrue(ausentes.stream().anyMatch(a -> a.campo().equals("pedido.feito_em")));
    }

    @Test
    void totalPedidoNuncaEhSomaDasParcelas() {
        // Variante local (nao a fixture gravada) construída so para
        // discriminar o R1 da V008: total explicitamente DIFERENTE da
        // soma das parcelas. Se o adaptador algum dia regredir para somar
        // parcelas.valor, este teste denuncia.
        String payload = """
                {
                  "id": 1,
                  "numero": 1,
                  "data": "2024-01-10",
                  "totalProdutos": 100.00,
                  "total": 999.99,
                  "contato": { "id": 1, "nome": "Teste" },
                  "situacao": { "valor": "Em aberto" },
                  "itens": [],
                  "parcelas": [
                    { "valor": 50.00 },
                    { "valor": 50.00 }
                  ]
                }
                """;
        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, UUID.randomUUID());
        assertEquals(0, resultado.pedido().getValorTotalPedido().compareTo(new BigDecimal("999.99")));
    }

    @Test
    void situacaoCanceladoMapeiaParaCancelado() {
        String payload = """
                {
                  "id": 2,
                  "numero": 2,
                  "data": "2024-01-10",
                  "totalProdutos": 10.00,
                  "total": 10.00,
                  "situacao": { "valor": "Cancelado" },
                  "itens": []
                }
                """;
        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, UUID.randomUUID());
        assertEquals(StatusPedido.CANCELADO, resultado.pedido().getStatus());
    }

    @Test
    void descontoPercentualECalculadoNuncaCopiaDireta() {
        String payload = """
                {
                  "id": 3,
                  "numero": 3,
                  "data": "2024-01-10",
                  "totalProdutos": 200.00,
                  "total": 180.00,
                  "desconto": { "valor": 10, "unidade": "PERCENTUAL" },
                  "situacao": { "valor": "Em aberto" },
                  "itens": []
                }
                """;
        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, UUID.randomUUID());
        // 10% de 200.00 = 20.00
        assertEquals(0, resultado.pedido().getValorDesconto().compareTo(new BigDecimal("20.00")));
        assertTrue(resultado.camposAusentes().stream().anyMatch(a -> a.campo().startsWith("pedido.valor_desconto")));
    }

    @Test
    void tipoPessoaJuridicaMapeiaCnpj() {
        String payload = """
                {
                  "id": 4,
                  "numero": 4,
                  "data": "2024-01-10",
                  "totalProdutos": 10.00,
                  "total": 10.00,
                  "situacao": { "valor": "Em aberto" },
                  "contato": { "id": 9, "nome": "Empresa Teste", "tipoPessoa": "J" },
                  "itens": []
                }
                """;
        ResultadoTraducao resultado = adaptador.traduzirPedido(payload, UUID.randomUUID());
        assertEquals(TipoCliente.PESSOA_JURIDICA, resultado.cliente().getTipo());
        assertEquals(TipoDocumento.CNPJ, resultado.cliente().getDocumentoTipo());
    }

    @Test
    void payloadInvalidoLancaExcecaoPropria() {
        assertThrows(PayloadInvalidoException.class,
                () -> adaptador.traduzirPedido("{ isto nao e json valido", UUID.randomUUID()));
    }

    @Test
    void payloadSemIdLancaExcecaoPropria() {
        assertThrows(PayloadInvalidoException.class,
                () -> adaptador.traduzirPedido("{\"numero\": 1}", UUID.randomUUID()));
    }
}
