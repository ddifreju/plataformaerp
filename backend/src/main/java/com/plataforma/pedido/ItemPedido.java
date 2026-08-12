package com.plataforma.pedido;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento da tabela item_pedido (migration V008). Linha de venda.
 * Referencia VARIACAO (o SKU), nunca produto - variacaoId e nullable
 * porque marketplace vende item fora do catalogo sincronizado (ver
 * cabecalho da V008).
 *
 * pedidoId e variacaoId sao UUID puro, nao @ManyToOne - decisao 0015, ver
 * o comentario completo em Produto.canalId.
 *
 * valorTotalLinha e redundancia PROPOSITAL, nao GENERATED: a fonte
 * informa o total da linha e ele nem sempre bate com
 * quantidade * valor_unitario_bruto (arredondamento de rateio da fonte).
 * Gravamos o que a fonte mandou, nunca recalculamos por cima - regra 5 do
 * CLAUDE.md. Por isso nao existe metodo calcularTotal() nesta classe.
 */
@Entity
@Table(name = "item_pedido")
public class ItemPedido {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "variacao_id")
    private UUID variacaoId;

    @Column(name = "sku_origem")
    private String skuOrigem;

    @Column(name = "titulo_origem", nullable = false)
    private String tituloOrigem;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario_bruto", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorUnitarioBruto;

    @Column(name = "valor_desconto_linha", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorDescontoLinha;

    @Column(name = "valor_total_linha", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorTotalLinha;

    @Column(name = "id_externo")
    private String idExterno;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_origem", nullable = false)
    private String dadosOrigem;

    @Column(name = "sincronizado_em")
    private OffsetDateTime sincronizadoEm;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected ItemPedido() {
        // exigido pelo JPA
    }

    public ItemPedido(UUID pedidoId, UUID variacaoId, String skuOrigem, String tituloOrigem,
            BigDecimal quantidade, BigDecimal valorUnitarioBruto, BigDecimal valorDescontoLinha,
            BigDecimal valorTotalLinha, String idExterno, String dadosOrigem) {
        if (tituloOrigem == null || tituloOrigem.isBlank()) {
            // Mesma invariante da CHECK ck_item_pedido_titulo_nao_vazio:
            // e o que explica o item mesmo sem variacao casada (ver
            // comentario da coluna no SQL). Falhar aqui e mais cedo e mais
            // claro do que deixar o INSERT estourar a CHECK no banco.
            throw new IllegalArgumentException(
                    "tituloOrigem nao pode ser vazio: e o que explica o item vendido.");
        }
        this.id = UUID.randomUUID();
        this.pedidoId = pedidoId;
        this.variacaoId = variacaoId;
        this.skuOrigem = skuOrigem;
        this.tituloOrigem = tituloOrigem;
        this.quantidade = quantidade;
        this.valorUnitarioBruto = valorUnitarioBruto;
        this.valorDescontoLinha = (valorDescontoLinha != null) ? valorDescontoLinha : BigDecimal.ZERO;
        this.valorTotalLinha = valorTotalLinha;
        this.idExterno = idExterno;
        this.dadosOrigem = (dadosOrigem != null) ? dadosOrigem : "{}";
        OffsetDateTime agora = OffsetDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPedidoId() {
        return pedidoId;
    }

    public UUID getVariacaoId() {
        return variacaoId;
    }

    public String getSkuOrigem() {
        return skuOrigem;
    }

    public String getTituloOrigem() {
        return tituloOrigem;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public BigDecimal getValorUnitarioBruto() {
        return valorUnitarioBruto;
    }

    public BigDecimal getValorDescontoLinha() {
        return valorDescontoLinha;
    }

    public BigDecimal getValorTotalLinha() {
        return valorTotalLinha;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getDadosOrigem() {
        return dadosOrigem;
    }

    public OffsetDateTime getSincronizadoEm() {
        return sincronizadoEm;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
