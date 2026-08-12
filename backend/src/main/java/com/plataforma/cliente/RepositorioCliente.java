package com.plataforma.cliente;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a cliente. Predicado de tenant vem do @TenantId da entidade
 * (decisao 0007) - nenhum metodo aqui deve escrever "WHERE tenant_id = ?"
 * a mao.
 *
 * Tabela mais sensivel do sistema (LGPD - ver Cliente e V007). Qualquer
 * @Query nativa aqui seria especialmente arriscada: nao recebe o
 * predicado de tenant do Hibernate, so o RLS protegeria. Evite; se um dia
 * for inevitavel, comente o risco em destaque no proprio metodo.
 */
public interface RepositorioCliente extends JpaRepository<Cliente, UUID> {
}
