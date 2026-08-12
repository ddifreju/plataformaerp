package com.plataforma.catalogo;

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
 * Mapeamento da tabela variacao (migration V006). E o SKU: a unidade que
 * realmente se vende. item_pedido referencia variacao, nunca produto.
 *
 * produtoId e canalId sao UUID puro, nao @ManyToOne - ver o comentario
 * completo em Produto.canalId (decisao 0015).
 *
 * Dinheiro (preco_venda_atual, custo_unitario_atual): BigDecimal,
 * precision=18/scale=4, igual a toda coluna monetaria do schema
 * (convencao 3 da V005). estoque_disponivel e quantidade tambem, so que
 * precision=14/scale=4: nao e dinheiro, mas venda fracionada existe
 * (granel, kg, metro), entao nao pode ser integer.
 */
@Entity
@Table(name = "variacao")
public class Variacao {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "gtin")
    private String gtin;

    @Column(name = "descricao_variacao")
    private String descricaoVariacao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atributos", nullable = false)
    private String atributos;

    @Column(name = "eh_variacao_padrao", nullable = false)
    private boolean ehVariacaoPadrao;

    @Column(name = "preco_venda_atual", precision = 18, scale = 4)
    private BigDecimal precoVendaAtual;

    @Column(name = "custo_unitario_atual", precision = 18, scale = 4)
    private BigDecimal custoUnitarioAtual;

    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda;

    @Column(name = "estoque_disponivel", precision = 14, scale = 4)
    private BigDecimal estoqueDisponivel;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "canal_id")
    private UUID canalId;

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

    protected Variacao() {
        // exigido pelo JPA
    }

    /**
     * ehVariacaoPadrao e parametro (nao fixo) porque, ao contrario de
     * "ativo", varia de fato na criacao: e true so para a variacao unica
     * sintetica de produto sem variante real (ver cabecalho da V006).
     */
    public Variacao(UUID produtoId, String sku, String gtin, String descricaoVariacao,
            String atributos, boolean ehVariacaoPadrao, BigDecimal precoVendaAtual,
            BigDecimal custoUnitarioAtual, String moeda, BigDecimal estoqueDisponivel,
            UUID canalId, String idExterno, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.produtoId = produtoId;
        this.sku = sku;
        this.gtin = gtin;
        this.descricaoVariacao = descricaoVariacao;
        this.atributos = (atributos != null) ? atributos : "{}";
        this.ehVariacaoPadrao = ehVariacaoPadrao;
        this.precoVendaAtual = precoVendaAtual;
        this.custoUnitarioAtual = custoUnitarioAtual;
        this.moeda = (moeda != null) ? moeda : "BRL";
        this.estoqueDisponivel = estoqueDisponivel;
        this.ativo = true;
        this.canalId = canalId;
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

    public UUID getProdutoId() {
        return produtoId;
    }

    public String getSku() {
        return sku;
    }

    public String getGtin() {
        return gtin;
    }

    public String getDescricaoVariacao() {
        return descricaoVariacao;
    }

    public String getAtributos() {
        return atributos;
    }

    public boolean isEhVariacaoPadrao() {
        return ehVariacaoPadrao;
    }

    public BigDecimal getPrecoVendaAtual() {
        return precoVendaAtual;
    }

    public BigDecimal getCustoUnitarioAtual() {
        return custoUnitarioAtual;
    }

    public String getMoeda() {
        return moeda;
    }

    public BigDecimal getEstoqueDisponivel() {
        return estoqueDisponivel;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public UUID getCanalId() {
        return canalId;
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
