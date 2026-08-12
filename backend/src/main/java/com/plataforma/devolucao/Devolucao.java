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
 * Mapeamento da tabela devolucao (migration V009). Devolucao canonica:
 * claim do ML, return da Shopee e e-mail da loja propria viram a mesma
 * linha aqui.
 *
 * ATENCAO AO SOMAR DINHEIRO: valorReembolsado e valorFreteReverso sao
 * DESCRICAO do que a fonte informou, nao entram no motor de margem (que
 * soma `custo`). Ver cabecalho da V009 antes de usar estas colunas em
 * qualquer calculo.
 *
 * pedidoId e canalId sao UUID puro, nao @ManyToOne - decisao 0015, ver o
 * comentario completo em Produto.canalId.
 */
@Entity
@Table(name = "devolucao")
public class Devolucao {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "pedido_id", nullable = false)
    private UUID pedidoId;

    @Column(name = "canal_id")
    private UUID canalId;

    @Column(name = "id_externo")
    private String idExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoDevolucao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusDevolucao status;

    @Column(name = "status_origem")
    private String statusOrigem;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo")
    private MotivoDevolucao motivo;

    @Column(name = "motivo_origem")
    private String motivoOrigem;

    @Column(name = "eh_arrependimento_cdc", nullable = false)
    private boolean ehArrependimentoCdc;

    @Column(name = "dentro_prazo_legal")
    private Boolean dentroPrazoLegal;

    @Column(name = "aberta_em", nullable = false)
    private OffsetDateTime abertaEm;

    @Column(name = "recebida_em")
    private OffsetDateTime recebidaEm;

    @Column(name = "finalizada_em")
    private OffsetDateTime finalizadaEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "destino_produto", nullable = false)
    private DestinoProduto destinoProduto;

    @Column(name = "valor_reembolsado", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorReembolsado;

    @Column(name = "valor_frete_reverso", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorFreteReverso;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsavel_frete_reverso")
    private ResponsavelFreteReverso responsavelFreteReverso;

    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_origem", nullable = false)
    private String dadosOrigem;

    @Column(name = "sincronizado_em")
    private OffsetDateTime sincronizadoEm;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Devolucao() {
        // exigido pelo JPA
    }

    /**
     * recebidaEm e finalizadaEm ficam de fora do construtor: sao
     * preenchidas em transicoes posteriores (produto voltou fisicamente,
     * devolucao concluida), fora do escopo desta entidade.
     */
    public Devolucao(UUID pedidoId, UUID canalId, String idExterno, TipoDevolucao tipo,
            StatusDevolucao status, String statusOrigem, MotivoDevolucao motivo,
            String motivoOrigem, boolean ehArrependimentoCdc, Boolean dentroPrazoLegal,
            OffsetDateTime abertaEm, DestinoProduto destinoProduto, BigDecimal valorReembolsado,
            BigDecimal valorFreteReverso, ResponsavelFreteReverso responsavelFreteReverso,
            String moeda, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.pedidoId = pedidoId;
        this.canalId = canalId;
        this.idExterno = idExterno;
        this.tipo = tipo;
        this.status = status;
        this.statusOrigem = statusOrigem;
        this.motivo = motivo;
        this.motivoOrigem = motivoOrigem;
        this.ehArrependimentoCdc = ehArrependimentoCdc;
        this.dentroPrazoLegal = dentroPrazoLegal;
        this.abertaEm = abertaEm;
        // Mesma coluna com DEFAULT 'NAO_RETORNOU' no banco: coalescido
        // aqui pelo mesmo motivo do tipo em Cliente (ver ConsultaAuditada).
        this.destinoProduto = (destinoProduto != null) ? destinoProduto : DestinoProduto.NAO_RETORNOU;
        this.valorReembolsado = (valorReembolsado != null) ? valorReembolsado : BigDecimal.ZERO;
        this.valorFreteReverso = (valorFreteReverso != null) ? valorFreteReverso : BigDecimal.ZERO;
        this.responsavelFreteReverso = responsavelFreteReverso;
        this.moeda = (moeda != null) ? moeda : "BRL";
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

    public UUID getCanalId() {
        return canalId;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public TipoDevolucao getTipo() {
        return tipo;
    }

    public StatusDevolucao getStatus() {
        return status;
    }

    public String getStatusOrigem() {
        return statusOrigem;
    }

    public MotivoDevolucao getMotivo() {
        return motivo;
    }

    public String getMotivoOrigem() {
        return motivoOrigem;
    }

    public boolean isEhArrependimentoCdc() {
        return ehArrependimentoCdc;
    }

    public Boolean getDentroPrazoLegal() {
        return dentroPrazoLegal;
    }

    public OffsetDateTime getAbertaEm() {
        return abertaEm;
    }

    public OffsetDateTime getRecebidaEm() {
        return recebidaEm;
    }

    public OffsetDateTime getFinalizadaEm() {
        return finalizadaEm;
    }

    public DestinoProduto getDestinoProduto() {
        return destinoProduto;
    }

    public BigDecimal getValorReembolsado() {
        return valorReembolsado;
    }

    public BigDecimal getValorFreteReverso() {
        return valorFreteReverso;
    }

    public ResponsavelFreteReverso getResponsavelFreteReverso() {
        return responsavelFreteReverso;
    }

    public String getMoeda() {
        return moeda;
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
