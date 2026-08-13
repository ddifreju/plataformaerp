package com.plataforma.margem;

import java.util.UUID;

/**
 * Lancada quando um chamador exige um PERCENTUAL de margem
 * (obrigatoriamente, sem alternativa) para um pedido com faturamento
 * bruto (N0) igual a zero. Secao 6.5 do documento fiscal:
 *
 * "Se N0 == 0 (pedido de brinde, bonificacao, valor zero), a margem
 * percentual nao existe. Exibir 0%, - ou infinito sao todos mentira. [...]
 * Divisao por zero em percentual de margem e um dos poucos lugares onde o
 * codigo deve preferir lancar excecao a produzir numero."
 *
 * {@link MotorMargemPedido#percentual} NAO lanca isto - ele devolve
 * {@code Optional.empty()}, porque o motor monta uma resposta agregavel
 * (um pedido de brinde no meio de um periodo nao pode derrubar o calculo
 * do periodo inteiro). Esta excecao existe para o caminho INVERSO: um
 * consumidor que precisa mostrar UM percentual de UM pedido especifico
 * (ex.: uma tela "detalhe do pedido X") e prefere falhar alto a herdar um
 * Optional.empty() silencioso na hora de renderizar.
 */
public class FaturamentoZeroException extends RuntimeException {

    public FaturamentoZeroException(UUID pedidoId) {
        super("Pedido " + pedidoId + " tem faturamento bruto (N0) igual a zero: margem percentual nao existe. "
                + "Nem 0%, nem \"-\", nem infinito seriam verdade (secao 6.5 do documento fiscal).");
    }
}
