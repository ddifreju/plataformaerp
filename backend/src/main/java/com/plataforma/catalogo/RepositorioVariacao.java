package com.plataforma.catalogo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a variacao (o SKU). Repositorio proprio, separado de
 * RepositorioProduto: variacao e referenciada diretamente por
 * item_pedido e custo (nao por produto), e "o SKU X nao esta batendo" e
 * uma busca que comeca aqui, nao em produto.
 *
 * findBySku deriva a query pelo nome do metodo; o Hibernate acrescenta o
 * predicado de tenant sozinho (@TenantId da entidade). Nunca escreva
 * "WHERE tenant_id = ?" a mao num metodo deste repositorio - e exatamente
 * o predicado que o @TenantId ja garante.
 *
 * findTop100ByCustoUnitarioAtualIsNull... alimenta a tarefa 20 (fila do
 * analista - "variacoes sem custo_unitario_atual"). So variacoes ATIVAS:
 * uma variacao desativada sem custo cadastrado nao e uma pendencia de
 * ninguem, porque nao esta mais sendo vendida.
 */
public interface RepositorioVariacao extends JpaRepository<Variacao, UUID> {

    Optional<Variacao> findBySku(String sku);

    List<Variacao> findTop100ByCustoUnitarioAtualIsNullAndAtivoTrueOrderByCriadoEmDesc();
}
