package com.plataforma.conversa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a conversa. Predicado de tenant vem do @TenantId da entidade
 * (decisao 0007) - nenhum metodo aqui deve escrever "WHERE tenant_id = ?"
 * a mao.
 */
public interface RepositorioConversa extends JpaRepository<Conversa, UUID> {
}
