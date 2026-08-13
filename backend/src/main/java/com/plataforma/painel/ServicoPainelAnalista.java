package com.plataforma.painel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plataforma.catalogo.RepositorioVariacao;
import com.plataforma.devolucao.RepositorioDevolucao;
import com.plataforma.ingestao.RepositorioEventoIngerido;
import com.plataforma.ingestao.StatusEventoIngerido;
import com.plataforma.pedido.RepositorioItemPedido;

/**
 * Tarefa 20: monta {@link RespostaFilaPendencias} a partir de itens
 * ACIONAVEIS reais - cada um mapeado para um DTO que ja diz o que fazer
 * (os {@code de(...)} de cada Item*).
 */
@Service
public class ServicoPainelAnalista {

    private final RepositorioEventoIngerido repositorioEventoIngerido;
    private final RepositorioItemPedido repositorioItemPedido;
    private final RepositorioVariacao repositorioVariacao;
    private final RepositorioDevolucao repositorioDevolucao;

    public ServicoPainelAnalista(RepositorioEventoIngerido repositorioEventoIngerido,
            RepositorioItemPedido repositorioItemPedido, RepositorioVariacao repositorioVariacao,
            RepositorioDevolucao repositorioDevolucao) {
        this.repositorioEventoIngerido = repositorioEventoIngerido;
        this.repositorioItemPedido = repositorioItemPedido;
        this.repositorioVariacao = repositorioVariacao;
        this.repositorioDevolucao = repositorioDevolucao;
    }

    @Transactional(readOnly = true)
    public RespostaFilaPendencias pendencias() {
        var eventosComErro = repositorioEventoIngerido
                .findTop100ByStatusOrderByRecebidoEmDesc(StatusEventoIngerido.ERRO)
                .stream().map(ItemEventoComErro::de).toList();

        var itensSemVariacao = repositorioItemPedido
                .findTop100ByVariacaoIdIsNullOrderByCriadoEmDesc()
                .stream().map(ItemPedidoSemVariacao::de).toList();

        var variacoesSemCusto = repositorioVariacao
                .findTop100ByCustoUnitarioAtualIsNullAndAtivoTrueOrderByCriadoEmDesc()
                .stream().map(ItemVariacaoSemCusto::de).toList();

        var devolucoesAbertas = repositorioDevolucao
                .findTop100ByFinalizadaEmIsNullOrderByAbertaEmDesc()
                .stream().map(ItemDevolucaoAberta::de).toList();

        return new RespostaFilaPendencias(eventosComErro, itensSemVariacao, variacoesSemCusto, devolucoesAbertas);
    }
}
