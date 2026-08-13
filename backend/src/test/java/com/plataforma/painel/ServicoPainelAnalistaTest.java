package com.plataforma.painel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.catalogo.RepositorioVariacao;
import com.plataforma.catalogo.Variacao;
import com.plataforma.devolucao.DestinoProduto;
import com.plataforma.devolucao.Devolucao;
import com.plataforma.devolucao.RepositorioDevolucao;
import com.plataforma.devolucao.StatusDevolucao;
import com.plataforma.devolucao.TipoDevolucao;
import com.plataforma.ingestao.EventoIngerido;
import com.plataforma.ingestao.RepositorioEventoIngerido;
import com.plataforma.ingestao.StatusEventoIngerido;
import com.plataforma.ingestao.TipoEvento;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.RepositorioItemPedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitario puro (Mockito, sem Spring, sem banco) de
 * {@link ServicoPainelAnalista}: prova que as quatro listas da fila de
 * pendencias (tarefa 20) sao montadas a partir dos quatro repositorios, e
 * que CADA item mapeado carrega uma acao nao vazia - o requisito
 * explicito "cada item deve dizer o que a pessoa precisa FAZER".
 */
class ServicoPainelAnalistaTest {

    private static final String HASH_QUALQUER = "a".repeat(64);

    private final RepositorioEventoIngerido repositorioEventoIngerido = mock(RepositorioEventoIngerido.class);
    private final RepositorioItemPedido repositorioItemPedido = mock(RepositorioItemPedido.class);
    private final RepositorioVariacao repositorioVariacao = mock(RepositorioVariacao.class);
    private final RepositorioDevolucao repositorioDevolucao = mock(RepositorioDevolucao.class);
    private final ServicoPainelAnalista servico = new ServicoPainelAnalista(
            repositorioEventoIngerido, repositorioItemPedido, repositorioVariacao, repositorioDevolucao);

    @Test
    void listaEventosComErroComAcaoPreenchida() {
        EventoIngerido evento = new EventoIngerido(
                UUID.randomUUID(), TipoEvento.PEDIDO, "id-externo-1", HASH_QUALQUER, "{}");
        // ORDEM IMPORTA: os stubs "vazios" precisam vir ANTES do stub
        // especifico deste teste, senao semPendenciasNasOutrasTres()
        // sobrescreveria o retorno que acabamos de configurar (o ultimo
        // when(...) para o mesmo metodo "vence" no Mockito).
        semPendenciasNasOutrasTres();
        when(repositorioEventoIngerido.findTop100ByStatusOrderByRecebidoEmDesc(StatusEventoIngerido.ERRO))
                .thenReturn(List.of(evento));

        RespostaFilaPendencias resposta = servico.pendencias();

        assertEquals(1, resposta.eventosComErro().size());
        ItemEventoComErro item = resposta.eventosComErro().get(0);
        assertEquals(evento.getId(), item.id());
        assertEquals(evento.getCanalId(), item.canalId());
        assertEquals("id-externo-1", item.idExterno());
        assertFalse(item.acao().isBlank(), "cada item precisa dizer o que fazer");
    }

    @Test
    void listaItensSemVariacaoComAcaoPreenchida() {
        ItemPedido item = new ItemPedido(
                UUID.randomUUID(), null, "SKU-1", "Produto sem SKU casado",
                BigDecimal.ONE, new BigDecimal("10.0000"), BigDecimal.ZERO, new BigDecimal("10.0000"),
                "item-ext-1", "{}");
        semPendenciasNasOutrasTres();
        when(repositorioItemPedido.findTop100ByVariacaoIdIsNullOrderByCriadoEmDesc())
                .thenReturn(List.of(item));

        RespostaFilaPendencias resposta = servico.pendencias();

        assertEquals(1, resposta.itensSemVariacao().size());
        ItemItemSemVariacao dto = resposta.itensSemVariacao().get(0);
        assertEquals(item.getId(), dto.id());
        assertEquals("SKU-1", dto.skuOrigem());
        assertFalse(dto.acao().isBlank());
    }

    @Test
    void listaVariacoesSemCustoComAcaoPreenchida() {
        Variacao variacao = new Variacao(
                UUID.randomUUID(), "SKU-2", null, "Variacao sem custo", "{}", false,
                new BigDecimal("50.0000"), null, "BRL", BigDecimal.TEN, null, "var-ext-1", "{}");
        semPendenciasNasOutrasTres();
        when(repositorioVariacao.findTop100ByCustoUnitarioAtualIsNullAndAtivoTrueOrderByCriadoEmDesc())
                .thenReturn(List.of(variacao));

        RespostaFilaPendencias resposta = servico.pendencias();

        assertEquals(1, resposta.variacoesSemCusto().size());
        ItemVariacaoSemCusto dto = resposta.variacoesSemCusto().get(0);
        assertEquals(variacao.getId(), dto.id());
        assertEquals("SKU-2", dto.sku());
        assertFalse(dto.acao().isBlank());
    }

    @Test
    void listaDevolucoesAbertasComAcaoPreenchida() {
        Devolucao devolucao = new Devolucao(
                UUID.randomUUID(), UUID.randomUUID(), "dev-ext-1", TipoDevolucao.TOTAL,
                StatusDevolucao.ABERTA, "opened", null, null, false, null,
                OffsetDateTime.parse("2026-01-10T10:00:00Z"), DestinoProduto.NAO_RETORNOU,
                BigDecimal.ZERO, BigDecimal.ZERO, null, "BRL", "{}");
        when(repositorioDevolucao.findTop100ByFinalizadaEmIsNullOrderByAbertaEmDesc())
                .thenReturn(List.of(devolucao));
        semPendenciasNasOutrasTres_MenosDevolucao();

        RespostaFilaPendencias resposta = servico.pendencias();

        assertEquals(1, resposta.devolucoesAbertas().size());
        ItemDevolucaoAberta dto = resposta.devolucoesAbertas().get(0);
        assertEquals(devolucao.getId(), dto.id());
        assertEquals("ABERTA", dto.status());
        assertFalse(dto.acao().isBlank());
    }

    @Test
    void semPendenciaNenhumaDevolveQuatroListasVazias() {
        semPendenciasNasOutrasTres();
        when(repositorioDevolucao.findTop100ByFinalizadaEmIsNullOrderByAbertaEmDesc()).thenReturn(List.of());

        RespostaFilaPendencias resposta = servico.pendencias();

        assertTrue(resposta.eventosComErro().isEmpty());
        assertTrue(resposta.itensSemVariacao().isEmpty());
        assertTrue(resposta.variacoesSemCusto().isEmpty());
        assertTrue(resposta.devolucoesAbertas().isEmpty());
    }

    private void semPendenciasNasOutrasTres() {
        when(repositorioEventoIngerido.findTop100ByStatusOrderByRecebidoEmDesc(StatusEventoIngerido.ERRO))
                .thenReturn(List.of());
        when(repositorioItemPedido.findTop100ByVariacaoIdIsNullOrderByCriadoEmDesc())
                .thenReturn(List.of());
        when(repositorioVariacao.findTop100ByCustoUnitarioAtualIsNullAndAtivoTrueOrderByCriadoEmDesc())
                .thenReturn(List.of());
        when(repositorioDevolucao.findTop100ByFinalizadaEmIsNullOrderByAbertaEmDesc())
                .thenReturn(List.of());
    }

    private void semPendenciasNasOutrasTres_MenosDevolucao() {
        when(repositorioEventoIngerido.findTop100ByStatusOrderByRecebidoEmDesc(StatusEventoIngerido.ERRO))
                .thenReturn(List.of());
        when(repositorioItemPedido.findTop100ByVariacaoIdIsNullOrderByCriadoEmDesc())
                .thenReturn(List.of());
        when(repositorioVariacao.findTop100ByCustoUnitarioAtualIsNullAndAtivoTrueOrderByCriadoEmDesc())
                .thenReturn(List.of());
    }
}
