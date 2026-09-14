package com.plataforma.pergunta;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarefa 24: {@code POST /api/pergunta}. Sem lógica de negócio aqui
 * (CLAUDE.md) - recebe o texto, delega para {@link ServicoPergunta} e
 * devolve o resultado, no mesmo padrão de
 * {@link com.plataforma.autenticacao.SessaoController} e
 * {@link com.plataforma.margem.MargemController}.
 *
 * O TENANT NUNCA vem de parâmetro da requisição, pelo mesmo motivo já
 * documentado em {@code MargemController}: {@link ServicoPergunta} e tudo
 * que ele chama (repositórios, {@code ServicoMargemPeriodo}, painéis)
 * filtram por tenant sozinhos, via {@code @TenantId}/RLS (decisão 0007),
 * lido de {@link com.plataforma.comum.tenant.ContextoTenant} pelo
 * {@link com.plataforma.comum.tenant.FiltroTenant} no início da requisição.
 *
 * <h2>Por que é POST, e não GET</h2>
 * Uma pergunta parece, à primeira vista, uma LEITURA - e seria natural
 * pedir {@code GET /api/pergunta?texto=...}. Isto é deliberadamente ERRADO
 * aqui: {@link ServicoPergunta#responder(String)} GRAVA uma
 * {@code ConsultaAuditada} a cada chamada, inclusive em RECUSA e
 * ESCLARECIMENTO (ver o Javadoc daquela classe) - ou seja, a rota ALTERA
 * ESTADO a cada chamada, mesmo que pareça "só" uma consulta.
 *
 * Isto não é só estilo: é a condição 1 da decisão 0025 ("nenhum GET pode
 * alterar estado") que sustenta o CSRF estar desligado em
 * {@link com.plataforma.autenticacao.ConfiguracaoSeguranca} - a defesa que
 * sobra ali é o cookie {@code SameSite=Lax}, e Lax permite o cookie de
 * sessão em navegação de TOPO via GET. Se esta rota fosse GET, um link ou
 * {@code <img src="...">} de outro site, aberto pela lojista já logada,
 * gravaria uma {@code ConsultaAuditada} em nome dela sem que ela tivesse
 * pedido - exatamente o vetor de CSRF que a decisão 0025 julgou coberto
 * partindo da premissa "nenhum GET escreve". Usar POST aqui não é só
 * consistência de estilo: é o que mantém essa premissa verdadeira.
 */
@RestController
public class PerguntaController {

    private final ServicoPergunta servicoPergunta;

    public PerguntaController(ServicoPergunta servicoPergunta) {
        this.servicoPergunta = servicoPergunta;
    }

    @PostMapping("/api/pergunta")
    public RespostaPergunta perguntar(@RequestBody @Valid RequisicaoPergunta requisicao) {
        return servicoPergunta.responder(requisicao.texto());
    }
}
