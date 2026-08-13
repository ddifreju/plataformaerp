package com.plataforma.pedido;

/**
 * Uma linha de "quantos pedidos ha em cada status" (tarefa 19, visao do
 * gestor - "contagem de pedido por status" e uma das fontes reais listadas
 * para gargalos do processo). Alvo de constructor expression JPQL em
 * {@link RepositorioPedido#contarPorStatus()} - fica no pacote pedido
 * porque e o resultado de uma consulta SOBRE pedido, no mesmo espirito de
 * {@code ResultadoMargemPeriodo} ficar em margem.
 */
public record ContagemPorStatusPedido(StatusPedido status, long quantidade) {
}
