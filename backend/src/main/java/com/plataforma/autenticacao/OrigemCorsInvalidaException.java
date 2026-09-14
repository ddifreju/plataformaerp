package com.plataforma.autenticacao;

/**
 * A lista de origens de CORS (propriedade {@code app.cors.origens-permitidas},
 * variavel de ambiente {@code APP_CORS_ORIGENS}) contem um valor que a
 * decisao 0025 proibe explicitamente - hoje, o unico caso e {@code "*"}.
 *
 * Lancada na SUBIDA da aplicacao (dentro de {@link ConfiguracaoSeguranca#fonteDeConfiguracaoCors()}),
 * de proposito: e melhor a aplicacao recusar subir com uma mensagem clara
 * do que deixar o Spring lancar uma excecao generica no meio do primeiro
 * request com Origin cruzado.
 */
public class OrigemCorsInvalidaException extends RuntimeException {

    public OrigemCorsInvalidaException(String mensagem) {
        super(mensagem);
    }
}
