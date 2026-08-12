package com.plataforma.comum.web;

import com.plataforma.comum.tenant.TenantDesconhecidoException;
import com.plataforma.comum.tenant.TenantNaoResolvidoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
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

    /**
     * Rede de seguranca para qualquer excecao nao prevista.
     *
     * Existe para que NENHUMA excecao inesperada chegue ao cliente
     * carregando detalhe interno. A mensagem de uma excecao de banco, por
     * exemplo, costuma incluir o SQL, o nome da constraint violada e as
     * vezes o proprio valor do dado - tudo isso e informacao de um
     * cliente que nao pode aparecer na resposta de ninguem.
     *
     * A mensagem devolvida e fixa e generica DE PROPOSITO: quem precisa
     * do detalhe e o log do servidor, nao o corpo da resposta. O log
     * completo (com stacktrace) fica a cargo do handler padrao do Spring,
     * que continua registrando a excecao no servidor.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroApi> tratarErroInesperado(Exception erro) throws Exception {
        // As excecoes do proprio Spring MVC (rota inexistente, metodo nao
        // permitido, corpo malformado) implementam ErrorResponse e ja
        // sabem virar a resposta HTTP correta sozinhas. Sem esta guarda,
        // este handler as capturaria primeiro e um 404 viraria 500.
        // Relancar faz o Spring seguir para o tratamento padrao dele com
        // a excecao original.
        if (erro instanceof ErrorResponse) {
            throw erro;
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroApi("erro_interno",
                        "Erro interno ao processar a requisicao."));
    }
}
