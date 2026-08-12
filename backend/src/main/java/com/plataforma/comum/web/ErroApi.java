package com.plataforma.comum.web;

/**
 * Corpo padrao de resposta de erro da API: {@code {"erro": "...", "mensagem": "..."}}.
 *
 * "erro" e um codigo curto e estavel, pensado para o cliente decidir
 * programaticamente o que fazer (ex.: "tenant_desconhecido").
 * "mensagem" e texto para humano, em portugues.
 *
 * NUNCA inclui stacktrace, SQL ou qualquer detalhe interno - nem aqui,
 * nem em quem constroi este objeto (TratadorGlobalDeErros, FiltroTenant).
 */
public record ErroApi(String erro, String mensagem) {
}
