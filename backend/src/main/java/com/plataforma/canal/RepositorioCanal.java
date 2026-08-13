package com.plataforma.canal;

import java.util.List;
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

    /**
     * Canais do tenant, em ordem alfabetica, para o seletor da interface.
     *
     * Devolve inclusive os inativos de proposito: um canal desligado hoje
     * continua tendo pedidos no historico, e a tela de Resultado precisa
     * conseguir consultar periodos passados dele. Quem decide como exibir
     * um canal inativo e a interface, que recebe o campo {@code ativo}.
     *
     * Sem "WHERE tenant_id" aqui: o @TenantId injeta o predicado (ver o
     * javadoc da interface).
     */
    List<Canal> findAllByOrderByNomeAsc();
}
