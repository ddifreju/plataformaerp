package com.plataforma.custo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Acesso a custo - a tabela de que a Fase 2 (motor de margem) inteira
 * depende. Predicado de tenant vem do @TenantId da entidade (decisao
 * 0007) - nenhum metodo aqui deve escrever "WHERE tenant_id = ?" a mao.
 *
 * somaValorPorPedido implementa a soma S1 do cabecalho da V010
 * ("SELECT sum(valor) FROM custo WHERE pedido_id = :id") via @Query
 * JPQL (nao nativa): o Hibernate ainda acrescenta o predicado de tenant
 * normalmente em JPQL, so @Query nativeQuery=true escaparia disso. Por
 * isso esta consulta NAO e um risco de vazamento entre tenants.
 */
public interface RepositorioCusto extends JpaRepository<Custo, UUID> {

    List<Custo> findByPedidoId(UUID pedidoId);

    List<Custo> findByItemPedidoId(UUID itemPedidoId);

    List<Custo> findByDevolucaoId(UUID devolucaoId);

    /**
     * S1 do cabecalho da V010: custo real de um pedido e a soma direta,
     * sem UNION e sem distinguir nivel de pedido/item - custo de item ja
     * carrega pedido_id preenchido (S3).
     */
    @Query("SELECT COALESCE(SUM(c.valor), 0) FROM Custo c WHERE c.pedidoId = :pedidoId")
    BigDecimal somaValorPorPedido(UUID pedidoId);

    /**
     * S4 do cabecalho da V010: custo do periodo, SEM as linhas derivadas
     * de rateio (rateadoDeCustoId IS NULL), senao a fatura-mae e as
     * linhas-filha do rateio seriam somadas duas vezes.
     */
    @Query("""
            SELECT COALESCE(SUM(c.valor), 0) FROM Custo c
            WHERE c.competenciaEm >= :inicio AND c.competenciaEm < :fim
              AND c.rateadoDeCustoId IS NULL
            """)
    BigDecimal somaValorPorPeriodo(OffsetDateTime inicio, OffsetDateTime fim);
}
