package com.plataforma.canal;

/**
 * Lancada quando a declaracao de {@link EscopoCanal#ESPELHO} proposta
 * formaria uma cadeia invalida: espelho de espelho, ciclo de tres ou mais
 * (o banco so impede o ciclo de DOIS - indice unico parcial sobre o par
 * nao ordenado, ver a secao 6 do cabecalho da migration V016), ou uma
 * cadeia longa demais para caminhar com seguranca (limite explicito de
 * profundidade - {@link ServicoEscopoDeCanal#PROFUNDIDADE_MAXIMA_CADEIA}).
 *
 * A mensagem sempre nomeia os canais da cadeia envolvida - "recuse, com
 * mensagem que diga qual e a cadeia" (tarefa 31): quem le o erro precisa
 * conseguir corrigir a declaracao sem adivinhar.
 */
public class CadeiaDeEspelhoInvalidaException extends RuntimeException {

    public CadeiaDeEspelhoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
