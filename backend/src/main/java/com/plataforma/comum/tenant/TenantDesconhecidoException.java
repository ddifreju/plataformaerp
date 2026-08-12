package com.plataforma.comum.tenant;

/**
 * O tenant informado tem formato valido (e um UUID), mas nao corresponde
 * a um tenant existente e ativo no catalogo.
 *
 * Mapeada para HTTP 403 (Forbidden) - ver TratadorGlobalDeErros, para o
 * caso de ser lancada de dentro do ciclo do Spring MVC. Quando lancada
 * pelo FiltroTenant (o caso mais comum), a resposta 403 e escrita pelo
 * proprio filtro - ver o comentario em FiltroTenant sobre o porque.
 *
 * Deliberadamente NAO existe uma subclasse separada para "tenant nao
 * existe" versus "tenant existe mas esta inativo": diferenciar isso na
 * mensagem devolvida ao cliente daria a quem esta adivinhando IDs um
 * jeito de descobrir quais tenants existem de verdade.
 */
public class TenantDesconhecidoException extends RuntimeException {

    public TenantDesconhecidoException(String mensagem) {
        super(mensagem);
    }
}
