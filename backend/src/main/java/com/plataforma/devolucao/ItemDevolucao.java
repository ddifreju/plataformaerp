package com.plataforma.devolucao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento da tabela item_devolucao (migration V009). O que voltou,
 * item a item - existe para que devolucao PARCIAL seja dado, nao palavra
 * (ver cabecalho da V009).
 *
 * devolucaoId e itemPedidoId sao UUID puro, nao @ManyToOne - decisao
 * 0015, ver o comentario completo em Produto.canalId.
 *
 * SEM sincronizadoEm: ao contrario das demais tabelas do modelo canonico,
 * item_devolucao NAO tem coluna sincronizado_em no SQL (conferido na
 * V009). Nao e omissao deste mapeamento.
 */
@Entity
@Table(name = "item_devolucao")
public class ItemDevolucao {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "devolucao_id", nullable = false)
    private UUID devolucaoId;

    @Column(name = "item_pedido_id", nullable = false)
    private UUID itemPedidoId;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "valor_reembolsado", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorReembolsado;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo")
    private MotivoDevolucao motivo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_origem", nullable = false)
    private String dadosOrigem;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected ItemDevolucao() {
        // exigido pelo JPA
    }

    public ItemDevolucao(UUID devolucaoId, UUID itemPedidoId, BigDecimal quantidade,
            BigDecimal valorReembolsado, MotivoDevolucao motivo, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.devolucaoId = devolucaoId;
        this.itemPedidoId = itemPedidoId;
        this.quantidade = quantidade;
        this.valorReembolsado = (valorReembolsado != null) ? valorReembolsado : BigDecimal.ZERO;
        this.motivo = motivo;
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

    public UUID getDevolucaoId() {
        return devolucaoId;
    }

    public UUID getItemPedidoId() {
        return itemPedidoId;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public BigDecimal getValorReembolsado() {
        return valorReembolsado;
    }

    public MotivoDevolucao getMotivo() {
        return motivo;
    }

    public String getDadosOrigem() {
        return dadosOrigem;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
