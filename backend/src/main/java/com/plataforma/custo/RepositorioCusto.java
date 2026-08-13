package com.plataforma.custo;

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
 * DECISAO DA TAREFA 14/15 SOBRE somaValorPorPedido/somaValorPorPeriodo
 * (que existiam aqui sem nenhum chamador): REMOVIDOS, nao adotados.
 * Motivo: o motor de margem (com.plataforma.margem.MotorMargemPedido)
 * precisa da soma DECOMPOSTA POR NATUREZA/BLOCO (secao 2.2 do documento
 * fiscal - "N2 = N1 - C(B2) - C(B3) - ... "), nao de um total unico por
 * pedido; e a tarefa 16 (P&L do periodo) precisa da mesma decomposicao,
 * mais o escopo por canal (decisao 0017), que a soma cega de periodo nao
 * carregava. Um SUM(valor) sozinho, sem GROUP BY natureza, teria que ser
 * refeito de qualquer jeito para alimentar a memoria de calculo (regra 3
 * do CLAUDE.md) - por isso os dois metodos abaixo devolvem List<Custo> e
 * a agregacao por bloco acontece em Java
 * (com.plataforma.margem.MotorMargemPedido), num lugar so, testado sem
 * banco.
 */
public interface RepositorioCusto extends JpaRepository<Custo, UUID> {

    List<Custo> findByPedidoId(UUID pedidoId);

    List<Custo> findByItemPedidoId(UUID itemPedidoId);

    List<Custo> findByDevolucaoId(UUID devolucaoId);

    /**
     * Custo de PERIODO explicitamente ligado a um CANAL (custo.canal_id
     * preenchido - ex.: fatura mensal de Ads ou mensalidade de uma conta
     * do Mercado Livre especifica), nao rateado (rateadoDeCustoId IS
     * NULL - S4 do cabecalho da V010, senao a fatura-mae e as linhas-filha
     * do rateio seriam somadas duas vezes) e sem pedido (pedidoId IS
     * NULL - custo de pedido ja entra pela soma por pedido, nao por
     * aqui). Usada pela tarefa 16 para compor N4 dentro do escopo de UM
     * canal (decisao 0017: nunca somamos canais potencialmente
     * sobrepostos as cegas).
     */
    @Query("""
            SELECT c FROM Custo c
            WHERE c.canalId = :canalId
              AND c.pedidoId IS NULL
              AND c.rateadoDeCustoId IS NULL
              AND c.competenciaEm >= :inicio AND c.competenciaEm < :fim
            """)
    List<Custo> buscarCustoDePeriodoDoCanal(UUID canalId, OffsetDateTime inicio, OffsetDateTime fim);
}
