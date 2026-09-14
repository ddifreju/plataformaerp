package com.plataforma.pergunta;

/**
 * Os três caminhos de {@link ServicoPergunta#responder(String)} (decisão
 * 0030: "recusa é caminho de primeira classe"). Nenhum dos três é um erro
 * HTTP - os três são {@code 200 OK} com {@link RespostaPergunta}, porque
 * "não sei responder isso ainda" e "preciso que você especifique o canal"
 * são respostas válidas do produto, não falhas de requisição.
 */
public enum TipoResposta {

    /** A pergunta caiu numa intenção do catálogo, os parâmetros foram resolvidos, e a consulta rodou. */
    RESPOSTA,

    /** A intenção foi identificada, mas falta ou é ambíguo um parâmetro obrigatório (canal, período). */
    ESCLARECIMENTO,

    /** Confiança abaixo do limiar, ou a intenção não existe no catálogo fechado. */
    RECUSA
}
