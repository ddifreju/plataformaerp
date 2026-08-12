package com.plataforma.canal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a canal. Como toda entidade com @TenantId, o predicado de tenant
 * e injetado pelo Hibernate em toda consulta feita por aqui - nunca
 * escreva "WHERE tenant_id = ?" manualmente num metodo deste repositorio,
 * seria redundante e, se um dia o RLS falhar, ilude quem le achando que a
 * protecao esta explicita aqui quando na verdade vem de fora.
 *
 * Um repositorio por agregado: canal e raiz do seu proprio agregado
 * (nao tem tabela filha).
 */
public interface RepositorioCanal extends JpaRepository<Canal, UUID> {
}
