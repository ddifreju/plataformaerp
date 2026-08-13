package com.plataforma.pedido;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a item_pedido. Repositorio proprio, apesar de item_pedido ser
 * filha de pedido: o enunciado desta tarefa cita justamente item_pedido
 * como candidato a NAO ter repositorio proprio "se so for acessado via
 * Pedido" - mas nao e o caso aqui. Duas outras tabelas referenciam
 * item_pedido_id DIRETAMENTE, sem passar por pedido (custo.item_pedido_id
 * e item_devolucao.item_pedido_id), e o proprio schema tem um indice
 * dedicado - ix_item_pedido_tenant_variacao - para a pergunta "quanto
 * vendi desta variacao" (margem por SKU, Fase 2), que atravessa varios
 * pedidos e nao faz sentido navegando por um Pedido especifico. Por isso
 * fica com repositorio proprio.
 *
 * Predicado de tenant vem do @TenantId da entidade (decisao 0007) -
 * nenhum metodo aqui deve escrever "WHERE tenant_id = ?" a mao.
 *
 * findTop100ByVariacaoIdIsNull... alimenta a tarefa 20 (fila do analista -
 * "itens sem variacao_id casado" e uma das lacunas #2 do catalogo fiscal,
 * secao 9.1: sem casamento com o catalogo, nao ha CMV automatico para
 * aquele item).
 */
public interface RepositorioItemPedido extends JpaRepository<ItemPedido, UUID> {

    List<ItemPedido> findByPedidoId(UUID pedidoId);

    List<ItemPedido> findByVariacaoId(UUID variacaoId);

    List<ItemPedido> findTop100ByVariacaoIdIsNullOrderByCriadoEmDesc();
}
