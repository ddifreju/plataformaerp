package com.plataforma.pedido;

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
 * Mapeamento da tabela pedido (migration V008). Venda canonica, venha de
 * marketplace, loja propria ou ERP. Leia o CONTRATO FINANCEIRO no
 * cabecalho da V008 antes de somar qualquer coluna monetaria daqui: o
 * total do pedido NAO e recalculado a partir dos itens (R1), e receita
 * nunca muda depois de gravada (R2) - devolucao/estorno viram linha em
 * `custo`, nao UPDATE aqui.
 *
 * canalId e clienteId sao UUID puro, nao @ManyToOne - decisao 0015, ver
 * o comentario completo em Produto.canalId.
 *
 * Datas de ciclo de vida (pago_em, enviado_em, entregue_em, cancelado_em,
 * prazo_arrependimento_ate) ficam de fora do construtor, de proposito:
 * elas sao preenchidas em transicoes POSTERIORES a criacao do pedido, por
 * um fluxo de atualizacao de status que e trabalho de outra tarefa (esta
 * entidade so mapeia e constroi, nao tem logica de negocio). Nascem
 * null e assim ficam ate esse mecanismo existir.
 */
@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    // UUID puro (decisao 0015) - ver Produto.canalId.
    @Column(name = "canal_id", nullable = false)
    private UUID canalId;

    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(name = "id_externo")
    private String idExterno;

    @Column(name = "codigo_exibicao")
    private String codigoExibicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusPedido status;

    @Column(name = "status_origem")
    private String statusOrigem;

    @Column(name = "feito_em", nullable = false)
    private OffsetDateTime feitoEm;

    @Column(name = "pago_em")
    private OffsetDateTime pagoEm;

    @Column(name = "enviado_em")
    private OffsetDateTime enviadoEm;

    @Column(name = "entregue_em")
    private OffsetDateTime entregueEm;

    @Column(name = "cancelado_em")
    private OffsetDateTime canceladoEm;

    @Column(name = "prazo_arrependimento_ate")
    private OffsetDateTime prazoArrependimentoAte;

    @Column(name = "valor_bruto_itens", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorBrutoItens;

    @Column(name = "valor_desconto", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorDesconto;

    @Column(name = "valor_frete_cobrado", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorFreteCobrado;

    @Column(name = "valor_total_pedido", nullable = false, precision = 18, scale = 4)
    private BigDecimal valorTotalPedido;

    @Column(name = "valor_repasse_previsto", precision = 18, scale = 4)
    private BigDecimal valorRepassePrevisto;

    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento")
    private FormaPagamento formaPagamento;

    @Column(name = "quantidade_parcelas")
    private Short quantidadeParcelas;

    // SQL declara "text", nao char(8) - o formato de 8 digitos e imposto
    // so pelo CHECK (ck_pedido_cep), nao pelo tipo da coluna. Sem length
    // de proposito: ver o mesmo raciocinio em Cliente.documentoHash.
    @Column(name = "cep_entrega")
    private String cepEntrega;

    @Column(name = "cidade_entrega")
    private String cidadeEntrega;

    @Column(name = "uf_entrega", length = 2)
    private String ufEntrega;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_origem", nullable = false)
    private String dadosOrigem;

    @Column(name = "sincronizado_em")
    private OffsetDateTime sincronizadoEm;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Pedido() {
        // exigido pelo JPA
    }

    /**
     * Os quatro valores monetarios tem DEFAULT 0 no banco, mas isso nao
     * dispensa passa-los aqui: um campo Java nulo vira NULL explicito no
     * INSERT e sobrescreve o DEFAULT (mesma ressalva de ConsultaAuditada).
     * Por isso cada um e coalescido para BigDecimal.ZERO quando o
     * chamador nao tiver o dado ainda (ingestao em duas etapas, ver
     * comentario de valor_bruto_itens no SQL).
     */
    public Pedido(UUID canalId, UUID clienteId, String idExterno, String codigoExibicao,
            StatusPedido status, String statusOrigem, OffsetDateTime feitoEm,
            BigDecimal valorBrutoItens, BigDecimal valorDesconto, BigDecimal valorFreteCobrado,
            BigDecimal valorTotalPedido, BigDecimal valorRepassePrevisto, String moeda,
            FormaPagamento formaPagamento, Short quantidadeParcelas, String cepEntrega,
            String cidadeEntrega, String ufEntrega, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.canalId = canalId;
        this.clienteId = clienteId;
        this.idExterno = idExterno;
        this.codigoExibicao = codigoExibicao;
        this.status = status;
        this.statusOrigem = statusOrigem;
        this.feitoEm = feitoEm;
        this.valorBrutoItens = (valorBrutoItens != null) ? valorBrutoItens : BigDecimal.ZERO;
        this.valorDesconto = (valorDesconto != null) ? valorDesconto : BigDecimal.ZERO;
        this.valorFreteCobrado = (valorFreteCobrado != null) ? valorFreteCobrado : BigDecimal.ZERO;
        this.valorTotalPedido = (valorTotalPedido != null) ? valorTotalPedido : BigDecimal.ZERO;
        this.valorRepassePrevisto = valorRepassePrevisto;
        this.moeda = (moeda != null) ? moeda : "BRL";
        this.formaPagamento = formaPagamento;
        this.quantidadeParcelas = quantidadeParcelas;
        this.cepEntrega = cepEntrega;
        this.cidadeEntrega = cidadeEntrega;
        this.ufEntrega = ufEntrega;
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

    public UUID getCanalId() {
        return canalId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getCodigoExibicao() {
        return codigoExibicao;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public String getStatusOrigem() {
        return statusOrigem;
    }

    public OffsetDateTime getFeitoEm() {
        return feitoEm;
    }

    public OffsetDateTime getPagoEm() {
        return pagoEm;
    }

    public OffsetDateTime getEnviadoEm() {
        return enviadoEm;
    }

    public OffsetDateTime getEntregueEm() {
        return entregueEm;
    }

    public OffsetDateTime getCanceladoEm() {
        return canceladoEm;
    }

    public OffsetDateTime getPrazoArrependimentoAte() {
        return prazoArrependimentoAte;
    }

    public BigDecimal getValorBrutoItens() {
        return valorBrutoItens;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public BigDecimal getValorFreteCobrado() {
        return valorFreteCobrado;
    }

    public BigDecimal getValorTotalPedido() {
        return valorTotalPedido;
    }

    public BigDecimal getValorRepassePrevisto() {
        return valorRepassePrevisto;
    }

    public String getMoeda() {
        return moeda;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public Short getQuantidadeParcelas() {
        return quantidadeParcelas;
    }

    public String getCepEntrega() {
        return cepEntrega;
    }

    public String getCidadeEntrega() {
        return cidadeEntrega;
    }

    public String getUfEntrega() {
        return ufEntrega;
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

    /**
     * Metodo de INTENCAO (dívida 1 do docs/ESTADO.md), nao um setter
     * generico: aplica, num pedido JA EXISTENTE, os campos que um
     * reprocessamento pode legitimamente mudar - o caso motivador e o
     * marketplace mandar "aguardando pagamento" e depois "pago" com o
     * MESMO id_externo. So o pipeline chama isto
     * (com.plataforma.ingestao.ServicoIngestao), nunca um controller.
     *
     * {@code origem} e o {@link Pedido} recem-traduzido pelo adaptador
     * para o MESMO evento reprocessado - ainda nao persistido, so usado
     * aqui como fonte dos valores novos.
     *
     * NUNCA MUDAM (este metodo simplesmente nao toca neles): id,
     * tenantId, canalId, idExterno. Esses quatro sao a IDENTIDADE da
     * linha - a chave natural (canalId, idExterno) e o que localizou este
     * pedido em primeiro lugar (ver ServicoIngestao.persistirResultado),
     * entao "atualizar" a propria chave que serviu para encontrar a linha
     * nao faz sentido e abriria espaco para o pedido "migrar" de chave por
     * engano.
     *
     * DUAS REGRAS DE MERGE DIFERENTES, uma por grupo de campo:
     *
     * <ol>
     *   <li><b>status, valorBrutoItens, valorDesconto, valorFreteCobrado,
     *       valorTotalPedido, feitoEm, moeda, dadosOrigem</b> - sempre
     *       aplicados de {@code origem}, sem condicao. Sao colunas
     *       {@code NOT NULL} do schema (V008) e o construtor de Pedido ja
     *       garante que {@code origem} sempre traz um valor (nunca null,
     *       mesmo quando a fonte nao informa - nesse caso o adaptador usa
     *       um neutro, ex.: {@code AGUARDANDO_PAGAMENTO} ou
     *       {@code BigDecimal.ZERO}, e declara a lacuna via
     *       {@code CampoAusente}). LIMITACAO CONHECIDA, nao resolvida
     *       aqui: se um evento novo trouxer um {@code status_origem} que o
     *       adaptador NAO reconhece, ele cai no neutro
     *       {@code AGUARDANDO_PAGAMENTO} (ver
     *       AdaptadorBling/AdaptadorMercadoLivre.traduzirStatus) - esse
     *       neutro sobrescreveria aqui um status mais avancado (ex.: PAGO)
     *       que este pedido ja tinha. E o mesmo tipo de risco de "default
     *       mascarando ausencia" que ja existe nos adaptadores hoje;
     *       resolver exigiria o pipeline distinguir "status confirmado
     *       pela fonte" de "status neutro por falta de mapeamento", o que
     *       nao existe ainda (fora do escopo desta rodada).</li>
     *   <li><b>codigoExibicao, statusOrigem, valorRepassePrevisto,
     *       formaPagamento, quantidadeParcelas, cepEntrega, cidadeEntrega,
     *       ufEntrega</b> - colunas nullable do schema. So sobrescrevem
     *       quando {@code origem} traz um valor NAO NULO; um valor nulo em
     *       {@code origem} preserva o que ja estava gravado. Motivo:
     *       payload de atualizacao de status normalmente e mais ESTREITO
     *       que o payload original (ex.: um webhook so de pagamento pode
     *       nao repetir cep/cidade/uf de entrega) - tratar "o campo nao
     *       veio desta vez" como "apague o que tinha" destruiria dado bom
     *       por causa de um payload legitimamente incompleto.</li>
     * </ol>
     */
    public void atualizarAPartirDaOrigem(Pedido origem) {
        this.status = origem.status;
        this.feitoEm = origem.feitoEm;
        this.valorBrutoItens = origem.valorBrutoItens;
        this.valorDesconto = origem.valorDesconto;
        this.valorFreteCobrado = origem.valorFreteCobrado;
        this.valorTotalPedido = origem.valorTotalPedido;
        this.moeda = origem.moeda;
        this.dadosOrigem = origem.dadosOrigem;

        this.codigoExibicao = (origem.codigoExibicao != null) ? origem.codigoExibicao : this.codigoExibicao;
        this.statusOrigem = (origem.statusOrigem != null) ? origem.statusOrigem : this.statusOrigem;
        this.valorRepassePrevisto = (origem.valorRepassePrevisto != null) ? origem.valorRepassePrevisto : this.valorRepassePrevisto;
        this.formaPagamento = (origem.formaPagamento != null) ? origem.formaPagamento : this.formaPagamento;
        this.quantidadeParcelas = (origem.quantidadeParcelas != null) ? origem.quantidadeParcelas : this.quantidadeParcelas;
        this.cepEntrega = (origem.cepEntrega != null) ? origem.cepEntrega : this.cepEntrega;
        this.cidadeEntrega = (origem.cidadeEntrega != null) ? origem.cidadeEntrega : this.cidadeEntrega;
        this.ufEntrega = (origem.ufEntrega != null) ? origem.ufEntrega : this.ufEntrega;

        this.atualizadoEm = OffsetDateTime.now();
    }
}
