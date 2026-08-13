package com.plataforma.margem;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Arredondamento na BORDA DE SAIDA (DTO de API, tela, export) - secao 6.1
 * e 6.2 do documento fiscal: "arredonde para 2, HALF_UP, apenas na borda
 * de saida. Nunca antes."
 *
 * {@link #paraExibicao(BigDecimal)} implementa a regra 1 da secao 6.4
 * para os QUATRO TOTAIS da margem (N0-N3 e os percentuais): "o total
 * exibido e round(soma exata, 2) - o arredondamento da soma exata, nunca
 * a soma dos arredondados". Como N0-N3 ja SAO a soma exata (nao uma
 * coluna de parcelas), aplicar setScale aqui e suficiente.
 *
 * NAO IMPLEMENTADO (relatado, nao inventado): a regra 2 da secao 6.4 - o
 * rateio de apresentacao das PARCELAS de uma decomposicao em tabela, para
 * que a coluna feche na tela mesmo depois de arredondar cada linha a 2
 * casas. Isso e responsabilidade de quem monta uma TELA com varias linhas
 * lado a lado (usaria {@link Rateio#ratear} com escalaSaida=2 sobre os
 * proprios valores como peso); nenhum endpoint desta rodada expoe uma
 * tabela desse tipo, entao nao foi construido - ver o relatorio final da
 * tarefa.
 */
public final class Apresentacao {

    private static final int ESCALA_EXIBICAO = 2;

    private Apresentacao() {
        // classe utilitaria: sem instancia
    }

    public static BigDecimal paraExibicao(BigDecimal valor) {
        return valor.setScale(ESCALA_EXIBICAO, RoundingMode.HALF_UP);
    }
}
