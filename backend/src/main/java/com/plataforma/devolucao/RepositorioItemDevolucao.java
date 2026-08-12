package com.plataforma.devolucao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a item_devolucao. Repositorio proprio, pelo mesmo raciocinio de
 * RepositorioItemPedido: o schema tem um indice dedicado
 * (ix_item_devolucao_tenant_item_pedido) para responder "este item de
 * pedido ja voltou, e quanto?" a partir do item_pedido_id, sem passar por
 * uma devolucao especifica - consulta que a Fase 2 vai precisar para
 * atribuir custo de devolucao ao SKU certo.
 *
 * Predicado de tenant vem do @TenantId da entidade (decisao 0007) -
 * nenhum metodo aqui deve escrever "WHERE tenant_id = ?" a mao.
 */
public interface RepositorioItemDevolucao extends JpaRepository<ItemDevolucao, UUID> {

    List<ItemDevolucao> findByDevolucaoId(UUID devolucaoId);

    List<ItemDevolucao> findByItemPedidoId(UUID itemPedidoId);
}
