package com.plataforma.devolucao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Acesso a devolucao. Predicado de tenant vem do @TenantId da entidade
 * (decisao 0007) - nenhum metodo aqui deve escrever "WHERE tenant_id = ?"
 * a mao.
 *
 * contarPorStatus e findTop100ByFinalizadaEmIsNull... alimentam,
 * respectivamente, a tarefa 19 (visao do gestor) e a tarefa 20 (fila de
 * pendencias do analista - "devolucoes abertas").
 * {@code finalizadaEm IS NULL} e a definicao de "aberta" usada aqui: a
 * coluna documenta literalmente "quando a devolucao foi concluida" (V009),
 * entao null e exatamente "ainda em aberto", independente de qual status
 * intermediario ela esta.
 */
public interface RepositorioDevolucao extends JpaRepository<Devolucao, UUID> {

    List<Devolucao> findByPedidoId(UUID pedidoId);

    @Query("select new com.plataforma.devolucao.ContagemPorStatusDevolucao(d.status, count(d)) "
            + "from Devolucao d group by d.status")
    List<ContagemPorStatusDevolucao> contarPorStatus();

    List<Devolucao> findTop100ByFinalizadaEmIsNullOrderByAbertaEmDesc();
}
