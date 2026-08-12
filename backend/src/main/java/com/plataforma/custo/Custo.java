package com.plataforma.custo;

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
 * Mapeamento da tabela custo (migration V010) - "o coracao do produto".
 * Uma linha por PARCELA de custo, com natureza. Leia o CONTRATO DE SOMA
 * (S1-S6) no cabecalho da V010 antes de escrever qualquer query que some
 * `valor`: em especial, soma de periodo (S4) precisa filtrar
 * "rateado_de_custo_id IS NULL" para nao contar o mesmo real duas vezes,
 * e o sinal de `valor` importa (S5: positivo reduz margem, negativo e
 * estorno).
 *
 * pedidoId, itemPedidoId, devolucaoId, canalId e rateadoDeCustoId sao
 * UUID puro, nao @ManyToOne - decisao 0015, ver o comentario completo em
 * Produto.canalId. rateadoDeCustoId e AUTO-REFERENCIA (FK composta para a
 * propria tabela custo), mesmo tratamento.
 *
 * aliquotaAplicada e FRACAO DECIMAL (0.125000 = 12,5%), nunca 12.5 -
 * precision=9/scale=6 casa exatamente com numeric(9,6) do banco.
 */
@Entity
@Table(name = "custo")
public class Custo {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "natureza", nullable = false)
    private NaturezaCusto natureza;

    @Column(name = "pedido_id")
    private UUID pedidoId;

    @Column(name = "item_pedido_id")
    private UUID itemPedidoId;

    @Column(name = "devolucao_id")
    private UUID devolucaoId;

    @Column(name = "valor", nullable = false, precision = 18, scale = 4)
    private BigDecimal valor;

    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda;

    @Column(name = "competencia_em", nullable = false)
    private OffsetDateTime competenciaEm;

    @Column(name = "eh_estimativa", nullable = false)
    private boolean ehEstimativa;

    @Column(name = "base_calculo", precision = 18, scale = 4)
    private BigDecimal baseCalculo;

    @Column(name = "aliquota_aplicada", precision = 9, scale = 6)
    private BigDecimal aliquotaAplicada;

    @Column(name = "metodo_rateio")
    private String metodoRateio;

    @Column(name = "rateado_de_custo_id")
    private UUID rateadoDeCustoId;

    @Column(name = "descricao")
    private String descricao;

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

    protected Custo() {
        // exigido pelo JPA
    }

    public Custo(NaturezaCusto natureza, UUID pedidoId, UUID itemPedidoId, UUID devolucaoId,
            BigDecimal valor, String moeda, OffsetDateTime competenciaEm, boolean ehEstimativa,
            BigDecimal baseCalculo, BigDecimal aliquotaAplicada, String metodoRateio,
            UUID rateadoDeCustoId, String descricao, UUID canalId, String idExterno,
            String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.natureza = natureza;
        this.pedidoId = pedidoId;
        this.itemPedidoId = itemPedidoId;
        this.devolucaoId = devolucaoId;
        this.valor = valor;
        this.moeda = (moeda != null) ? moeda : "BRL";
        this.competenciaEm = competenciaEm;
        this.ehEstimativa = ehEstimativa;
        this.baseCalculo = baseCalculo;
        this.aliquotaAplicada = aliquotaAplicada;
        this.metodoRateio = metodoRateio;
        this.rateadoDeCustoId = rateadoDeCustoId;
        this.descricao = descricao;
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

    public NaturezaCusto getNatureza() {
        return natureza;
    }

    public UUID getPedidoId() {
        return pedidoId;
    }

    public UUID getItemPedidoId() {
        return itemPedidoId;
    }

    public UUID getDevolucaoId() {
        return devolucaoId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getMoeda() {
        return moeda;
    }

    public OffsetDateTime getCompetenciaEm() {
        return competenciaEm;
    }

    public boolean isEhEstimativa() {
        return ehEstimativa;
    }

    public BigDecimal getBaseCalculo() {
        return baseCalculo;
    }

    public BigDecimal getAliquotaAplicada() {
        return aliquotaAplicada;
    }

    public String getMetodoRateio() {
        return metodoRateio;
    }

    public UUID getRateadoDeCustoId() {
        return rateadoDeCustoId;
    }

    public String getDescricao() {
        return descricao;
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
