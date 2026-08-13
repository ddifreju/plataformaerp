package com.plataforma.devolucao;

/**
 * Uma linha de "quantas devolucoes ha em cada status" (tarefa 19, visao do
 * gestor). Alvo de constructor expression JPQL em
 * {@link RepositorioDevolucao#contarPorStatus()}.
 */
public record ContagemPorStatusDevolucao(StatusDevolucao status, long quantidade) {
}
