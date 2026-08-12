package com.plataforma.integracao;

import java.util.List;

import com.plataforma.cliente.Cliente;
import com.plataforma.custo.Custo;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;

/**
 * O que um {@link AdaptadorDeCanal#traduzirPedido} produz: as entidades
 * canonicas prontas para persistir (ainda NAO persistidas - quem grava e
 * o pipeline de ingestao, nao o adaptador) e a lista do que NAO pode ser
 * preenchido com dado real desta vez ({@link #camposAusentes()}).
 *
 * {@link #cliente()} e nullable de proposito: nem toda fonte expoe
 * comprador em todo pedido (ver Pedido.clienteId). {@link #itens()} e
 * {@link #custos()} podem ser vazios, mas nunca nulos.
 *
 * As listas sao copiadas defensivamente (List.copyOf) no construtor
 * compacto - um record que expõe List mutavel do chamador seria uma
 * mutabilidade escondida atras de uma API que parece imutavel.
 */
public record ResultadoTraducao(
        Pedido pedido,
        List<ItemPedido> itens,
        Cliente cliente,
        List<Custo> custos,
        List<CampoAusente> camposAusentes) {

    public ResultadoTraducao {
        if (pedido == null) {
            throw new IllegalArgumentException("pedido nao pode ser nulo - e o proprio ponto da traducao.");
        }
        itens = List.copyOf(itens);
        custos = List.copyOf(custos);
        camposAusentes = List.copyOf(camposAusentes);
    }
}
