package com.plataforma.margem;

import com.plataforma.custo.NaturezaCusto;

/**
 * Os oito blocos de apresentacao da margem (docs/fiscal/regras-de-margem.md,
 * secao 2.2). Mapeamento FECHADO de {@link NaturezaCusto} para bloco - a
 * tabela da secao 2.2 vive aqui, em codigo, para que nunca exista um
 * segundo lugar (uma query, uma view) decidindo a mesma coisa de outro
 * jeito.
 *
 * A ORDEM DOS VALORES DO ENUM E A ORDEM DE APRESENTACAO da secao 2.4 -
 * B1 primeiro, B8 por ultimo. {@link MotorMargemPedido} itera
 * {@code values()} para montar a decomposicao na mesma ordem em que "a
 * tela conta a historia" (secao 2.4), entao NAO REORDENE estas constantes
 * sem reler aquela secao.
 *
 * A FRONTEIRA QUE IMPORTA: B1-B6 sao N1->N2 (custo MEDIDO, auditavel linha
 * a linha contra a fatura do canal); B7-B8 sao N2->N3 (custo RATEADO,
 * atribuicao nossa). Ver o paragrafo "a fronteira que importa" na secao 1
 * do documento fiscal.
 */
public enum BlocoMargem {

    /** N0 -> N1. Reduz a base do imposto (secao 3). */
    B1_DEDUCOES_RECEITA,
    /** N1 -> N2. */
    B2_CUSTO_MERCADORIA,
    /** N1 -> N2. */
    B3_CUSTOS_CANAL,
    /** N1 -> N2. */
    B4_CUSTOS_LOGISTICOS,
    /** N1 -> N2. */
    B5_CUSTOS_FINANCEIROS,
    /** N1 -> N2. E o proprio: a base do imposto e calculada a parte (secao 5), nunca sobre o saldo corrente. */
    B6_IMPOSTO,
    /** N2 -> N3. Rateado. */
    B7_MARKETING_ATRIBUIDO,
    /** N2 -> N3. Rateado. */
    B8_OVERHEAD_ATRIBUIDO;

    /**
     * @throws NullPointerException se natureza for nula
     * @throws IllegalStateException se um valor novo for adicionado a
     *         {@link NaturezaCusto} sem que este metodo seja atualizado -
     *         falha alto de proposito: uma natureza sem bloco silenciaria
     *         dinheiro da soma (o modo de falha numero um da Fase 2,
     *         secao 2.1 do documento fiscal).
     */
    public static BlocoMargem deNatureza(NaturezaCusto natureza) {
        return switch (natureza) {
            case REEMBOLSO, DESCONTO_CONCEDIDO -> B1_DEDUCOES_RECEITA;
            case MERCADORIA -> B2_CUSTO_MERCADORIA;
            case COMISSAO_CANAL, TARIFA_FIXA_CANAL -> B3_CUSTOS_CANAL;
            // EMBALAGEM entra aqui (nao em B3): e custo logistico, nao do canal.
            case FRETE, FRETE_REVERSO, EMBALAGEM -> B4_CUSTOS_LOGISTICOS;
            case TAXA_PAGAMENTO, TAXA_PARCELAMENTO, TAXA_ANTECIPACAO -> B5_CUSTOS_FINANCEIROS;
            case IMPOSTO -> B6_IMPOSTO;
            case ADS -> B7_MARKETING_ATRIBUIDO;
            // ARMAZENAGEM fica aqui e nao em B4 mesmo quando a linha tem
            // pedido_id preenchido (raro) - ver a nota de classificacao da
            // secao 2.2: consistencia vale mais que precisao de taxonomia.
            // OUTRO tambem fica aqui de proposito: e o escape hatch da
            // V010, e colocar abaixo de N2 impede que um custo nao
            // classificado contamine o numero mais auditavel do sistema.
            case ARMAZENAGEM, TARIFA_ADMINISTRATIVA, OUTRO -> B8_OVERHEAD_ATRIBUIDO;
        };
    }
}
