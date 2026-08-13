package com.plataforma.painel;

import java.util.List;

/**
 * Resposta de {@code GET /api/painel/analista} (tarefa 20 - "fila de
 * pendencias"). Quatro fontes reais, cada item dizendo o que fazer:
 *
 * <ul>
 *   <li>{@code eventosComErro} - o que falhou ao ingerir;</li>
 *   <li>{@code itensSemVariacao} - itens sem SKU casado;</li>
 *   <li>{@code variacoesSemCusto} - variacoes sem custo cadastrado;</li>
 *   <li>{@code devolucoesAbertas} - devolucoes ainda nao concluidas.</li>
 * </ul>
 *
 * SEM PAGINACAO nesta primeira versao - cada lista e limitada as 100 mais
 * recentes (ver os metodos {@code findTop100By...} dos repositorios).
 * Limitacao conhecida e aceita: uma fila que cresce alem de 100 itens por
 * categoria precisa de paginacao de verdade, e isso e trabalho de UI
 * (scroll/paginas), nao deste endpoint.
 */
public record RespostaFilaPendencias(
        List<ItemEventoComErro> eventosComErro,
        List<ItemItemSemVariacao> itensSemVariacao,
        List<ItemVariacaoSemCusto> variacoesSemCusto,
        List<ItemDevolucaoAberta> devolucoesAbertas) {
}
