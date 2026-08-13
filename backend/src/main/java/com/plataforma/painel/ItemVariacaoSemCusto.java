package com.plataforma.painel;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.plataforma.catalogo.Variacao;

/**
 * Uma variacao ativa sem {@code custo_unitario_atual} cadastrado (tarefa
 * 20). Sem este dado, todo pedido que vender esta variacao vira lacuna
 * #1 do catalogo fiscal (custo do produto nao cadastrado).
 */
public record ItemVariacaoSemCusto(
        UUID id, String sku, String descricaoVariacao, OffsetDateTime criadoEm, String acao) {

    private static final String ACAO = "Cadastrar o custo unitario atual desta variacao.";

    public static ItemVariacaoSemCusto de(Variacao variacao) {
        return new ItemVariacaoSemCusto(
                variacao.getId(), variacao.getSku(), variacao.getDescricaoVariacao(),
                variacao.getCriadoEm(), ACAO);
    }
}
