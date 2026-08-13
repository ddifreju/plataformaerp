package com.plataforma.pedido;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
 *
 * findByCanalIdAndFeitoEm... usa o indice ix_pedido_tenant_canal_feito_em
 * (V008, "faturamento por canal no periodo"). E a base da tarefa 16
 * (com.plataforma.margem.ServicoMargemPeriodo): o CANAL e parametro
 * OBRIGATORIO, nunca opcional - decisao 0017 (reconciliacao entre fontes
 * e explicita): somar pedidos de canais potencialmente sobrepostos (ML +
 * Bling espelhando a mesma venda) contaria a mesma venda duas vezes.
 *
 * contarPorStatus e contarPedidosSemCustoMercadoria alimentam a tarefa 19
 * (visao do gestor - gargalos do PROCESSO, nunca de pessoa: decisao 0003).
 * Como toda consulta JPQL sobre uma entidade com @TenantId, o Hibernate
 * acrescenta o predicado de tenant automaticamente - nao ha "WHERE
 * tenant_id" escrito a mao em nenhuma das duas.
 */
public interface RepositorioPedido extends JpaRepository<Pedido, UUID> {

    Optional<Pedido> findByCanalIdAndIdExterno(UUID canalId, String idExterno);

    List<Pedido> findByCanalIdAndFeitoEmGreaterThanEqualAndFeitoEmLessThan(
            UUID canalId, OffsetDateTime inicio, OffsetDateTime fim);

    @Query("select new com.plataforma.pedido.ContagemPorStatusPedido(p.status, count(p)) "
            + "from Pedido p group by p.status")
    List<ContagemPorStatusPedido> contarPorStatus();

    /**
     * Pedidos sem NENHUMA linha de custo MERCADORIA - a lacuna #1 do
     * catalogo (secao 9.1 do documento fiscal, ver
     * {@code CatalogoLacunas.custoMercadoriaNaoCadastrado}): falta a maior
     * parcela de custo, e sem ela nenhum numero de margem sai confiavel
     * para aquele pedido. E o proxy escolhido para "pedidos com lacuna de
     * custo" (tarefa 19) - existem outras lacunas possiveis (item sem
     * variacao, taxa nao cadastrada...), mas esta e a unica com fonte
     * direta e barata de consultar sem reprocessar o motor de margem
     * inteiro so para montar um painel.
     */
    @Query("select count(p) from Pedido p where not exists ("
            + "select 1 from Custo c where c.pedidoId = p.id and c.natureza = "
            + "com.plataforma.custo.NaturezaCusto.MERCADORIA)")
    long contarPedidosSemCustoMercadoria();
}
