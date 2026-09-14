package com.plataforma.canal;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Canal como a interface precisa dele: o minimo para montar um seletor e
 * mostrar/editar o escopo declarado (tarefa 31, decisao 0033).
 *
 * DE PROPOSITO nao expoe {@code chaveCredencial} (nome/identificador do
 * segredo de integracao), {@code idExterno} nem {@code dadosOrigem} (o
 * payload cru da fonte). Nada disso ajuda a escolher um canal numa lista,
 * e {@code chaveCredencial} em particular nao tem por que trafegar ate o
 * navegador em hipotese nenhuma.
 *
 * TAMBEM DE PROPOSITO nao expoe {@code escopoDeclaradoPor}: e dado
 * pessoal (id de usuario) e prova pontual de auditoria, nunca informacao
 * que a tela de canais precisa mostrar (decisao 0012, comentario da
 * coluna na V016) - quem precisar da autoria consulta a linha
 * especificamente para explicar/contestar um numero, nao numa listagem.
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
        boolean ativo,
        EscopoCanal escopoDeclarado,
        UUID espelhaCanalId,
        OffsetDateTime escopoDeclaradoEm) {

    public static RespostaCanal de(Canal canal) {
        return new RespostaCanal(
                canal.getId(),
                canal.getCodigo(),
                canal.getNome(),
                canal.getTipo(),
                canal.getCategoria(),
                canal.isAtivo(),
                canal.getEscopoDeclarado(),
                canal.getEspelhaCanalId(),
                canal.getEscopoDeclaradoEm());
    }
}
