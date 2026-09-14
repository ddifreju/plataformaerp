package com.plataforma.margem;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarefa 16: {@code POST /api/margem/periodo}. Sem logica de negocio aqui
 * (CLAUDE.md) - recebe os parametros, delega para
 * {@link ServicoMargemPeriodo} e devolve o resultado.
 *
 * O TENANT NUNCA vem de parametro da requisicao: os repositorios usados
 * por {@link ServicoMargemPeriodo} filtram por tenant sozinhos, via
 * {@code @TenantId} (decisao 0007), lido de {@link com.plataforma.comum.tenant.ContextoTenant}
 * pelo {@link com.plataforma.comum.tenant.FiltroTenant} no inicio da
 * requisicao - nao existe (e nao deveria existir) um {@code @RequestParam
 * tenantId} nesta assinatura.
 *
 * {@code canalId} e OBRIGATORIO de proposito - ver o Javadoc de
 * {@link ServicoMargemPeriodo} sobre a decisao 0017.
 *
 * A resposta e {@link RespostaMargemPeriodo}, nao
 * {@link ResultadoMargemPeriodo} diretamente: a formatacao para 2 casas
 * (secao 6.1/6.2 do documento fiscal) acontece NA BORDA DE SAIDA, aqui -
 * {@link ServicoMargemPeriodo} continua devolvendo escala de armazenamento
 * (4 casas), que e o que o resto do sistema espera reusar/somar.
 *
 * <h2>Por que e POST, e nao GET (decisao 0034)</h2>
 * Esta rota parecia leitura e era GET ate a decisao 0034 corrigir isso:
 * {@link ServicoMargemPeriodo#calcular} GRAVA uma {@code ConsultaAuditada}
 * a cada chamada (regra 3 do CLAUDE.md), ou seja, ALTERA ESTADO a cada
 * chamada. Isso quebrava a condicao 1 da decisao 0025 ("nenhum GET pode
 * alterar estado"), que e a premissa que sustenta o CSRF desligado em
 * {@link com.plataforma.autenticacao.ConfiguracaoSeguranca}: com
 * {@code SameSite=Lax}, uma navegacao de topo GET ainda leva o cookie de
 * sessao, entao um site hostil conseguia forcar essa gravacao em nome da
 * lojista logada. Ver o mesmo raciocinio, escrito em detalhe, em
 * {@link com.plataforma.pergunta.PerguntaController}.
 */
@RestController
public class MargemController {

    private final ServicoMargemPeriodo servicoMargemPeriodo;

    public MargemController(ServicoMargemPeriodo servicoMargemPeriodo) {
        this.servicoMargemPeriodo = servicoMargemPeriodo;
    }

    @PostMapping("/api/margem/periodo")
    public RespostaMargemPeriodo periodo(@RequestBody @Valid RequisicaoMargemPeriodo requisicao) {
        ResultadoMargemPeriodo resultado = servicoMargemPeriodo.calcular(
                requisicao.inicio(), requisicao.fim(), requisicao.canalId());
        return RespostaMargemPeriodo.de(resultado);
    }
}
