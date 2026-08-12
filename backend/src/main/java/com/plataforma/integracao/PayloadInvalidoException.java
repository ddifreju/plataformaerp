package com.plataforma.integracao;

/**
 * Payload que um {@link AdaptadorDeCanal} nao consegue traduzir de jeito
 * nenhum: JSON malformado, ou ausencia de um campo sem o qual nem a chave
 * de idempotencia (id externo) nem uma coluna {@code NOT NULL} do
 * canonico podem ser preenchidas.
 *
 * Diferente de {@link CampoAusente} (que registra uma lacuna e segue em
 * frente): esta excecao para a ingestao deste evento por completo. Quem
 * captura isto (o pipeline, com.plataforma.ingestao.ServicoIngestao) deve
 * gravar o motivo em evento_ingerido.erro_mensagem e marcar o evento como
 * ERRO, nunca deixar o payload se perder silenciosamente (regra 3 do
 * CLAUDE.md: toda resposta numerica e rastreavel, e isso inclui saber por
 * que um pedido NAO virou numero nenhum).
 */
public class PayloadInvalidoException extends RuntimeException {

    public PayloadInvalidoException(String mensagem) {
        super(mensagem);
    }

    public PayloadInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
