package com.plataforma.margem;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Uma linha da memoria de calculo (secao 7 do documento fiscal): quanto
 * este bloco somou, com QUE linhas de {@code custo} ele foi montado, e se
 * alguma delas e estimativa. E o que permite a tela abrir um bloco e
 * mostrar de onde o numero saiu, sem reprocessar nada.
 *
 * {@code valor} esta em escala de ARMAZENAMENTO (4 casas, HALF_UP -
 * secao 6.1). Arredondamento para apresentacao (2 casas) e feito na borda
 * de saida, nunca aqui - ver {@link Apresentacao}.
 */
public record MemoriaCalculoBloco(
        BlocoMargem bloco,
        BigDecimal valor,
        List<UUID> idsCusto,
        boolean contemEstimativa) {
}
