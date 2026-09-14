package com.plataforma.comum.ambiente;

/**
 * A aplicacao subiu fora do perfil {@code dev} sem uma variavel de
 * ambiente que a tarefa 30 (Fase 4, bloco B) considera obrigatoria fora
 * de desenvolvimento - hoje, a senha do datasource e a chave HMAC de
 * documento (ver {@link ValidadorDeAmbiente}).
 *
 * Lancada por {@link ValidadorDeAmbiente} ANTES do contexto Spring
 * terminar de subir: e melhor a aplicacao recusar iniciar com uma
 * mensagem clara do que subir pela metade (por exemplo, com o hash de
 * documento sempre vazio porque a chave HMAC nunca existiu).
 */
public class VariavelDeAmbienteAusenteException extends RuntimeException {

    public VariavelDeAmbienteAusenteException(String mensagem) {
        super(mensagem);
    }
}
