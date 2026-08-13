package com.plataforma.margem;

import java.math.BigDecimal;

/**
 * A mesma decomposicao por bloco de {@link MemoriaCalculoBloco}, mas
 * AGREGADA no periodo (soma de varios pedidos). Sem {@code idsCusto}
 * aqui de proposito: num periodo com centenas de pedidos, listar cada id
 * de custo por bloco poluiria a resposta sem ajudar - quem quiser a
 * memoria de calculo linha a linha de UM pedido especifico consulta o
 * endpoint de margem POR PEDIDO (tarefa 15), que devolve
 * {@link MemoriaCalculoBloco} completo. A rastreabilidade do periodo como
 * um todo fica em {@code idsPedidoUsados}/{@code idsCustoUsados} de
 * {@link ResultadoMargemPeriodo} e na linha gravada em
 * {@code consulta_auditada} (regra 3 do CLAUDE.md).
 */
public record MemoriaCalculoBlocoPeriodo(BlocoMargem bloco, BigDecimal valor, boolean contemEstimativa) {
}
