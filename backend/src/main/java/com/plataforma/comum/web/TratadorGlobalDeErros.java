package com.plataforma.comum.web;

import com.plataforma.comum.tenant.TenantDesconhecidoException;
import com.plataforma.comum.tenant.TenantNaoResolvidoException;
import com.plataforma.margem.PeriodoInvalidoException;
import com.plataforma.margem.TaxaCanalAmbiguaException;
import com.plataforma.pergunta.PerguntaInvalidaException;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
     * Periodo invalido pedido a tarefa 16 (com.plataforma.margem) -
     * "caminho de erro coberto, nao so o caminho feliz" (CLAUDE.md).
     */
    @ExceptionHandler(PeriodoInvalidoException.class)
    public ResponseEntity<ErroApi> tratarPeriodoInvalido(PeriodoInvalidoException erro) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErroApi("periodo_invalido", erro.getMessage()));
    }

    /**
     * Erro de CADASTRO em taxa_canal (duas linhas empatadas em
     * especificidade - secao 8.2 do documento fiscal). 409 porque o
     * problema esta no estado dos dados cadastrados, nao na requisicao em
     * si - a mesma chamada volta a funcionar assim que o cadastro for
     * corrigido, sem mudar nenhum parametro.
     */
    @ExceptionHandler(TaxaCanalAmbiguaException.class)
    public ResponseEntity<ErroApi> tratarTaxaCanalAmbigua(TaxaCanalAmbiguaException erro) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErroApi("taxa_canal_ambigua", erro.getMessage()));
    }

    /**
     * Pergunta vazia ou maior que 500 caracteres
     * ({@code com.plataforma.pergunta}, tarefa 24) - mesmo padrao de
     * {@link PeriodoInvalidoException} acima. Na pratica esta excecao quase
     * nunca chega ate aqui: {@code @Valid} em
     * {@code com.plataforma.pergunta.RequisicaoPergunta} (ver o handler de
     * {@link MethodArgumentNotValidException} logo abaixo) ja barra os dois
     * casos na borda HTTP antes do controller chamar
     * {@code ServicoPergunta.responder}. Mantido mesmo assim porque
     * {@link PerguntaInvalidaException} e publica e continua protegendo
     * qualquer chamador futuro que nao passe pelo controller/{@code @Valid}.
     */
    @ExceptionHandler(PerguntaInvalidaException.class)
    public ResponseEntity<ErroApi> tratarPerguntaInvalida(PerguntaInvalidaException erro) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErroApi("pergunta_invalida", erro.getMessage()));
    }

    /**
     * Falha de validacao de bean ({@code @Valid}) em qualquer
     * {@code @RequestBody} da API - hoje so
     * {@code com.plataforma.pergunta.RequisicaoPergunta} (tarefa 24). SEM
     * esta entrada, {@link MethodArgumentNotValidException} cairia no
     * handler de {@code Exception} abaixo e seria RELANCADA (ela tambem
     * implementa {@link ErrorResponse}, ver a guarda em
     * {@link #tratarErroInesperado}), virando o formato padrao do Spring
     * (ProblemDetail/RFC7807) em vez do {@link ErroApi} que o resto desta
     * API usa - dois formatos de erro coexistindo seria pior para quem
     * consome a API do que nao ter tratado nada.
     *
     * A mensagem devolvida e SOMENTE o texto das mensagens declaradas em
     * {@code @NotBlank}/{@code @Size} (mensagens fixas, sem interpolar o
     * valor recebido) - NUNCA {@code FieldError.getRejectedValue()}, que
     * aqui seria o proprio texto da pergunta enviada pela lojista. Ecoar
     * isso de volta no corpo de um erro 400 vazaria o conteudo digitado
     * pelo cliente num canal (resposta de erro, frequentemente logada por
     * inteiro) que nao deveria carregar esse dado.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroApi> tratarCorpoInvalido(MethodArgumentNotValidException erro) {
        String mensagem = erro.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(texto -> texto != null && !texto.isBlank())
                .collect(Collectors.joining(" "));
        if (mensagem.isBlank()) {
            mensagem = "Requisição inválida.";
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErroApi("requisicao_invalida", mensagem));
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
