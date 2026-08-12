package com.plataforma.integracao;

import java.util.UUID;

import com.plataforma.canal.TipoCanal;

/**
 * Porta (decisao 0002/0014): todo conector novo implementa esta
 * interface, nunca uma rota Camel nesta fase (decisao 0014 - "Camel
 * adiado; adaptadores sao classes Spring simples"). Quando Camel entrar,
 * ele entra EM VOLTA disto (transporte, agendamento, retentativa), a
 * tradução em si nao muda.
 *
 * Cada implementacao (AdaptadorMercadoLivre, AdaptadorBling, ...) e uma
 * classe Spring comum (@Component), PURA: recebe o payload ja obtido (por
 * fixture hoje, por HTTP quando a credencial existir) e so traduz. Nao
 * chama rede, nao consulta banco - isso e o que permite testar a tradução
 * com JUnit puro, sem subir Postgres (ver
 * docs/decisoes/0014-camel-adiado-na-fase-1.md).
 */
public interface AdaptadorDeCanal {

    /**
     * De qual {@link TipoCanal} este adaptador sabe traduzir. E a chave
     * que o pipeline de ingestao (com.plataforma.ingestao.ServicoIngestao)
     * usa para escolher o adaptador certo a partir do canal do evento.
     */
    TipoCanal tipoSuportado();

    /**
     * Traduz o payload bruto de um pedido da fonte para o modelo
     * canonico. Nunca lanca excecao para "campo que a fonte nao tem" -
     * isso vira entrada em {@link ResultadoTraducao#camposAusentes()}
     * (regra 5 do CLAUDE.md: nunca inventar dado, mas declarar ausencia
     * é diferente de falhar). So lanca {@link PayloadInvalidoException}
     * quando o payload e estruturalmente inutilizavel (nao e JSON valido,
     * ou falta um campo sem o qual nem a chave de idempotencia nem as
     * colunas NOT NULL do canonico podem ser preenchidas).
     *
     * @param payloadJson o payload bruto da fonte, como recebido (o mesmo
     *                     texto que vira evento_ingerido.payload_bruto)
     * @param canalId      o canal (tenant implicito) de onde este payload
     *                     veio - usado para casar cliente/pedido/custo com
     *                     a origem correta
     */
    ResultadoTraducao traduzirPedido(String payloadJson, UUID canalId);
}
