package com.plataforma.devolucao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a devolucao. Predicado de tenant vem do @TenantId da entidade
 * (decisao 0007) - nenhum metodo aqui deve escrever "WHERE tenant_id = ?"
 * a mao.
 */
public interface RepositorioDevolucao extends JpaRepository<Devolucao, UUID> {

    List<Devolucao> findByPedidoId(UUID pedidoId);
}
