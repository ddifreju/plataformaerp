package com.plataforma.painel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plataforma.devolucao.RepositorioDevolucao;
import com.plataforma.ingestao.RepositorioEventoIngerido;
import com.plataforma.ingestao.StatusEventoIngerido;
import com.plataforma.pedido.RepositorioPedido;

/**
 * Tarefa 19: monta {@link RespostaGargalosProcesso} a partir de fontes que
 * ja existem no modelo - sem inventar metrica (regra 5 do CLAUDE.md) e
 * sem tocar {@code executado_por} (decisao 0012).
 */
@Service
public class ServicoPainelGestor {

    private final RepositorioPedido repositorioPedido;
    private final RepositorioDevolucao repositorioDevolucao;
    private final RepositorioEventoIngerido repositorioEventoIngerido;

    public ServicoPainelGestor(RepositorioPedido repositorioPedido, RepositorioDevolucao repositorioDevolucao,
            RepositorioEventoIngerido repositorioEventoIngerido) {
        this.repositorioPedido = repositorioPedido;
        this.repositorioDevolucao = repositorioDevolucao;
        this.repositorioEventoIngerido = repositorioEventoIngerido;
    }

    @Transactional(readOnly = true)
    public RespostaGargalosProcesso gargalos() {
        return new RespostaGargalosProcesso(
                repositorioPedido.contarPorStatus(),
                repositorioDevolucao.contarPorStatus(),
                repositorioEventoIngerido.countByStatus(StatusEventoIngerido.ERRO),
                repositorioPedido.contarPedidosSemCustoMercadoria());
    }
}
