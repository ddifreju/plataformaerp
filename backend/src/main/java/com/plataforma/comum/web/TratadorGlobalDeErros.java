package com.plataforma.comum.web;

import com.plataforma.comum.tenant.TenantDesconhecidoException;
import com.plataforma.comum.tenant.TenantNaoResolvidoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz excecoes de dominio para respostas HTTP num lugar so, para que
 * nenhum controller precise fazer try/catch e montar corpo de erro na
 * mao - isso seria logica de infraestrutura vazando pro controller.
 *
 * IMPORTANTE - o que isto NAO cobre: os erros de tenant lancados pelo
 * FiltroTenant (cabecalho ausente/invalido, tenant desconhecido ou
 * inativo). O filtro roda ANTES do DispatcherServlet, fora do ciclo de
 * vida que o @RestControllerAdvice consegue interceptar (exception
 * handler do Spring MVC so enxerga excecao lancada durante o
 * despacho de um controller). Por isso o FiltroTenant escreve a
 * resposta de erro sozinho, no mesmo formato ErroApi - ver o metodo
 * escreverErro em FiltroTenant.
 *
 * Este handler cobre as MESMAS excecoes quando lancadas de dentro de um
 * controller ou servico, ja dentro do ciclo do Spring MVC (por exemplo,
 * um endpoint futuro que valide um tenant informado explicitamente).
 */
@RestControllerAdvice
public class TratadorGlobalDeErros {

    @ExceptionHandler(TenantNaoResolvidoException.class)
    public ResponseEntity<ErroApi> tratarTenantNaoResolvido(TenantNaoResolvidoException erro) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErroApi("tenant_nao_resolvido", erro.getMessage()));
    }

    @ExceptionHandler(TenantDesconhecidoException.class)
    public ResponseEntity<ErroApi> tratarTenantDesconhecido(TenantDesconhecidoException erro) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErroApi("tenant_desconhecido", erro.getMessage()));
    }
}
