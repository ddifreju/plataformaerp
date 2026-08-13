package com.plataforma.canal;

import java.util.UUID;

/**
 * Canal como a interface precisa dele: o minimo para montar um seletor.
 *
 * DE PROPOSITO nao expoe {@code chaveCredencial} (nome/identificador do
 * segredo de integracao), {@code idExterno} nem {@code dadosOrigem} (o
 * payload cru da fonte). Nada disso ajuda a escolher um canal numa lista,
 * e {@code chaveCredencial} em particular nao tem por que trafegar ate o
 * navegador em hipotese nenhuma.
 *
 * Mesmo criterio de RespostaSessao: o DTO existe para que a entidade
 * inteira nunca vire JSON por acidente.
 */
public record RespostaCanal(
        UUID id,
        String codigo,
        String nome,
        TipoCanal tipo,
        CategoriaCanal categoria,
        boolean ativo) {

    public static RespostaCanal de(Canal canal) {
        return new RespostaCanal(
                canal.getId(),
                canal.getCodigo(),
                canal.getNome(),
                canal.getTipo(),
                canal.getCategoria(),
                canal.isAtivo());
    }
}
