package com.plataforma.pedido;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a pedido. Predicado de tenant vem do @TenantId da entidade
 * (decisao 0007) - nenhum metodo aqui deve escrever "WHERE tenant_id = ?"
 * a mao.
 *
 * findByCanalIdAndIdExterno deriva a chave natural de idempotencia
 * (uq_pedido_origem, V008), MENOS o tenant_id, que o Hibernate ja
 * restringe sozinho via @TenantId. Usado pelo pipeline de ingestao
 * (com.plataforma.ingestao.ServicoIngestao) como a SEGUNDA trava contra
 * pedido duplicado, independente do evento_ingerido (ver comentario da
 * V012 sobre as duas travas serem independentes de proposito).
 */
public interface RepositorioPedido extends JpaRepository<Pedido, UUID> {

    Optional<Pedido> findByCanalIdAndIdExterno(UUID canalId, String idExterno);
}
