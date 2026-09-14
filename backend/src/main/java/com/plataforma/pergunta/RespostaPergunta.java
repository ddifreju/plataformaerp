package com.plataforma.pergunta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta de {@link ServicoPergunta#responder(String)} - o contrato
 * inteiro da camada de IA (decisão 0030) cabe neste record.
 *
 * {@code consultaAuditadaId} nunca é nulo: toda chamada a
 * {@code responder} grava uma {@code ConsultaAuditada}, inclusive em
 * {@link TipoResposta#RECUSA} e {@link TipoResposta#ESCLARECIMENTO} - é
 * o texto cru da pergunta recusada que alimenta a decisão de quais
 * perguntas entram no catálogo (decisão 0031).
 *
 * {@code perguntasQueSeiResponder} só é preenchida fora de
 * {@link TipoResposta#RESPOSTA} - numa resposta de verdade não faz
 * sentido sugerir outra pergunta.
 */
public record RespostaPergunta(
        TipoResposta tipo,
        String texto,
        List<NumeroCitado> numeros,
        String rotuloConfianca,
        List<String> lacunas,
        UUID consultaAuditadaId,
        String intencao,
        Map<String, String> parametrosUsados,
        List<String> perguntasQueSeiResponder) {
}
