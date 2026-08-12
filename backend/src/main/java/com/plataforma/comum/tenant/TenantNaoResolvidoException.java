package com.plataforma.comum.tenant;

/**
 * O tenant da requisicao nao pode ser resolvido: cabecalho ausente, em
 * branco, ou com um valor que nao e um UUID valido.
 *
 * Mapeada para HTTP 400 (Bad Request) - ver TratadorGlobalDeErros, para
 * o caso de ser lancada de dentro do ciclo do Spring MVC. Quando lancada
 * pelo FiltroTenant (o caso mais comum), a resposta 400 e escrita pelo
 * proprio filtro, porque filtros rodam fora do alcance do
 * @RestControllerAdvice - ver o comentario em FiltroTenant.
 */
public class TenantNaoResolvidoException extends RuntimeException {

    public TenantNaoResolvidoException(String mensagem) {
        super(mensagem);
    }
}
