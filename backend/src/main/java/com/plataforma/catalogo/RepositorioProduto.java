package com.plataforma.catalogo;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a produto. Predicado de tenant vem do @TenantId da entidade
 * (decisao 0007) - nenhum metodo aqui deve escrever "WHERE tenant_id = ?"
 * a mao.
 */
public interface RepositorioProduto extends JpaRepository<Produto, UUID> {
}
