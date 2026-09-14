package com.plataforma.margem;

/**
 * Lancada por {@link ServicoMargemPeriodoConsolidada} quando o conjunto de
 * canais pedido para a soma NAO e comprovadamente disjunto (decisao
 * 0033, tarefa 32): algum canal esta sem escopo declarado, e ESPELHO, ou
 * e espelhado por outro canal do MESMO conjunto pedido.
 *
 * A mensagem sempre nomeia CADA canal bloqueado e o motivo - "nunca um
 * numero", ver o Javadoc de {@link ServicoMargemPeriodoConsolidada} sobre
 * o formato da recusa.
 */
public class ConjuntoDeCanaisNaoDisjuntoException extends RuntimeException {

    public ConjuntoDeCanaisNaoDisjuntoException(String mensagem) {
        super(mensagem);
    }
}
