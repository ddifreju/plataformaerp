package com.plataforma.painel;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.plataforma.devolucao.ContagemPorStatusDevolucao;
import com.plataforma.devolucao.RepositorioDevolucao;
import com.plataforma.devolucao.StatusDevolucao;
import com.plataforma.ingestao.RepositorioEventoIngerido;
import com.plataforma.ingestao.StatusEventoIngerido;
import com.plataforma.pedido.ContagemPorStatusPedido;
import com.plataforma.pedido.RepositorioPedido;
import com.plataforma.pedido.StatusPedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitario puro (Mockito, sem Spring, sem banco) de
 * {@link ServicoPainelGestor}. Prova que os quatro numeros da tarefa 19
 * ("gargalos do processo") vem exatamente das quatro fontes declaradas -
 * nenhuma agregacao por pessoa, nenhum numero calculado em Java por cima
 * (decisao 0003/0012).
 */
class ServicoPainelGestorTest {

    private final RepositorioPedido repositorioPedido = mock(RepositorioPedido.class);
    private final RepositorioDevolucao repositorioDevolucao = mock(RepositorioDevolucao.class);
    private final RepositorioEventoIngerido repositorioEventoIngerido = mock(RepositorioEventoIngerido.class);
    private final ServicoPainelGestor servico =
            new ServicoPainelGestor(repositorioPedido, repositorioDevolucao, repositorioEventoIngerido);

    @Test
    void montaAResposta1Para1ComAsQuatroFontes() {
        List<ContagemPorStatusPedido> pedidosPorStatus = List.of(
                new ContagemPorStatusPedido(StatusPedido.PAGO, 10),
                new ContagemPorStatusPedido(StatusPedido.ENVIADO, 3));
        List<ContagemPorStatusDevolucao> devolucoesPorStatus = List.of(
                new ContagemPorStatusDevolucao(StatusDevolucao.ABERTA, 2));

        when(repositorioPedido.contarPorStatus()).thenReturn(pedidosPorStatus);
        when(repositorioDevolucao.contarPorStatus()).thenReturn(devolucoesPorStatus);
        when(repositorioEventoIngerido.countByStatus(StatusEventoIngerido.ERRO)).thenReturn(5L);
        when(repositorioPedido.contarPedidosSemCustoMercadoria()).thenReturn(7L);

        RespostaGargalosProcesso resposta = servico.gargalos();

        assertEquals(pedidosPorStatus, resposta.pedidosPorStatus());
        assertEquals(devolucoesPorStatus, resposta.devolucoesPorStatus());
        assertEquals(5L, resposta.eventosIngestaoComErro());
        assertEquals(7L, resposta.pedidosSemCustoMercadoria());
    }
}
