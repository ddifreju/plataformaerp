package com.plataforma.autenticacao;

/**
 * A sessao tem um {@code Authentication} valido, mas a revalidacao POR
 * REQUISICAO (armadilha 6 da V014) descobriu que o usuario nao esta mais
 * ativo, ou que a loja dele nao esta mais ativa - tipicamente porque foi
 * desativado DEPOIS que a sessao foi aberta (o caso citado na decisao
 * 0023: "desligar o acesso do funcionario que saiu").
 *
 * Lancada por {@link com.plataforma.comum.tenant.FiltroTenant}, que
 * tambem invalida a sessao HTTP quando isto acontece - nao ha por que
 * deixar uma sessao que ja falhou uma vez continuar sendo tentada.
 *
 * Mapeada para HTTP 401: e diferente de "nunca autenticou" (esse caso e
 * pego pelo Spring Security antes mesmo deste filtro rodar), mas o codigo
 * de status pede a mesma acao do cliente - fazer login de novo.
 */
public class SessaoInvalidaException extends RuntimeException {

    public SessaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
