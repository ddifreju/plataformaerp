package com.plataforma.pedido;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a pedido. Predicado de tenant vem do @TenantId da entidade
 * (decisao 0007) - nenhum metodo aqui deve escrever "WHERE tenant_id = ?"
 * a mao.
 */
public interface RepositorioPedido extends JpaRepository<Pedido, UUID> {
}
