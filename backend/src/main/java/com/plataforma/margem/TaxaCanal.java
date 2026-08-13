package com.plataforma.margem;

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

import com.plataforma.custo.NaturezaCusto;

/**
 * Mapeamento da tabela taxa_canal (migration V013) - NIVEL 2 da hierarquia
 * de taxas (decisao 0019). Leia o cabecalho da V013 antes de escrever
 * qualquer consulta contra esta tabela: a consulta de selecao EXATA esta
 * la, e {@link com.plataforma.margem.SelecaoTaxaCanal} implementa a leitura
 * do resultado dela (0/1/2 linhas, empate falha alto).
 *
 * ATENCAO ESPECIAL - especificidade e GENERATED ALWAYS AS (...) STORED no
 * banco: mapeada com {@code insertable = false, updatable = false}. Sem
 * isso o Hibernate tenta escrever a coluna no INSERT e o Postgres rejeita
 * a instrucao inteira (comentario da propria V013, linha ~119-122).
 *
 * categoriaCanal e tipoAnuncio sao {@code text NOT NULL} no banco, com o
 * SENTINELA {@code '*'} para curinga (decisao 0019) - nunca null. Ficam
 * como String java (nao enum) porque categoria_canal e vocabulario da
 * FONTE (ex.: 'MLB1051'), nao nosso.
 *
 * canalId e UUID puro, nao @ManyToOne - decisao 0015, mesmo padrao do
 * resto do modelo (ver Produto.canalId).
 */
@Entity
@Table(name = "taxa_canal")
public class TaxaCanal {

    /** Sentinela de curinga para categoriaCanal/tipoAnuncio (decisao 0019). Nunca use null nestas duas colunas. */
    public static final String CURINGA = "*";

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "canal_id", nullable = false)
    private UUID canalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_taxa", nullable = false)
    private TipoTaxaCanal tipoTaxa;

    @Enumerated(EnumType.STRING)
    @Column(name = "natureza_custo", nullable = false)
    private NaturezaCusto naturezaCusto;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_incidencia", nullable = false)
    private BaseIncidencia baseIncidencia;

    @Column(name = "categoria_canal", nullable = false)
    private String categoriaCanal;

    @Column(name = "tipo_anuncio", nullable = false)
    private String tipoAnuncio;

    @Column(name = "faixa_valor_min", precision = 18, scale = 4)
    private BigDecimal faixaValorMin;

    @Column(name = "faixa_valor_max", precision = 18, scale = 4)
    private BigDecimal faixaValorMax;

    @Column(name = "vigencia_inicio", nullable = false)
    private OffsetDateTime vigenciaInicio;

    @Column(name = "vigencia_fim")
    private OffsetDateTime vigenciaFim;

    @Column(name = "percentual", precision = 9, scale = 6)
    private BigDecimal percentual;

    @Column(name = "valor_fixo", precision = 18, scale = 4)
    private BigDecimal valorFixo;

    @Column(name = "valor_minimo", precision = 18, scale = 4)
    private BigDecimal valorMinimo;

    @Column(name = "valor_maximo", precision = 18, scale = 4)
    private BigDecimal valorMaximo;

    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda;

    // GENERATED ALWAYS AS (...) STORED (V013): NUNCA gravavel pela
    // aplicacao. insertable/updatable = false e o que evita o Hibernate
    // listar esta coluna no INSERT/UPDATE - ver o Javadoc da classe. O
    // CONSTRUTOR ainda calcula o valor em Java (espelhando a formula SQL
    // exatamente) para que um objeto recem-criado, antes de qualquer
    // SELECT de volta do banco, ja tenha o valor correto em memoria -
    // e o que permite testar SelecaoTaxaCanal sem banco (ver TaxaCanalTest).
    @Column(name = "especificidade", insertable = false, updatable = false)
    private int especificidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "confianca", nullable = false)
    private ConfiancaTaxa confianca;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false)
    private OrigemTaxa origem;

    @Column(name = "fonte_url")
    private String fonteUrl;

    @Column(name = "consultado_em")
    private OffsetDateTime consultadoEm;

    @Column(name = "observacao")
    private String observacao;

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

    protected TaxaCanal() {
        // exigido pelo JPA
    }

    /**
     * Mesmo padrao de construcao de Custo/Canal/ConsultaAuditada: id e
     * timestamps gerados aqui, nunca deixados para o DEFAULT do banco (o
     * Hibernate lista todas as colunas mapeadas no INSERT, e um campo Java
     * nulo sobrescreveria o DEFAULT com NULL).
     */
    public TaxaCanal(UUID canalId, TipoTaxaCanal tipoTaxa, NaturezaCusto naturezaCusto, BaseIncidencia baseIncidencia,
            String categoriaCanal, String tipoAnuncio, BigDecimal faixaValorMin, BigDecimal faixaValorMax,
            OffsetDateTime vigenciaInicio, OffsetDateTime vigenciaFim, BigDecimal percentual, BigDecimal valorFixo,
            BigDecimal valorMinimo, BigDecimal valorMaximo, String moeda, ConfiancaTaxa confianca, OrigemTaxa origem,
            String fonteUrl, OffsetDateTime consultadoEm, String observacao, String idExterno, String dadosOrigem) {
        if (categoriaCanal == null || categoriaCanal.isBlank()) {
            throw new IllegalArgumentException("categoriaCanal nao pode ser vazio: use TaxaCanal.CURINGA ('*') "
                    + "para 'vale para todas as categorias', nunca null (decisao 0019).");
        }
        if (tipoAnuncio == null || tipoAnuncio.isBlank()) {
            throw new IllegalArgumentException("tipoAnuncio nao pode ser vazio: use TaxaCanal.CURINGA ('*') "
                    + "para 'vale para todos os tipos', nunca null (decisao 0019).");
        }
        if ((percentual != null) == (valorFixo != null)) {
            throw new IllegalArgumentException(
                    "exatamente um entre percentual e valorFixo deve ser informado (ck_taxa_canal_valor_exclusivo, V013).");
        }
        this.id = UUID.randomUUID();
        this.canalId = canalId;
        this.tipoTaxa = tipoTaxa;
        this.naturezaCusto = naturezaCusto;
        this.baseIncidencia = baseIncidencia;
        this.categoriaCanal = categoriaCanal;
        this.tipoAnuncio = tipoAnuncio;
        this.faixaValorMin = faixaValorMin;
        this.faixaValorMax = faixaValorMax;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.percentual = percentual;
        this.valorFixo = valorFixo;
        this.valorMinimo = valorMinimo;
        this.valorMaximo = valorMaximo;
        this.moeda = (moeda != null) ? moeda : "BRL";
        this.confianca = confianca;
        this.origem = origem;
        this.fonteUrl = fonteUrl;
        this.consultadoEm = consultadoEm;
        this.observacao = observacao;
        this.idExterno = idExterno;
        this.dadosOrigem = (dadosOrigem != null) ? dadosOrigem : "{}";
        OffsetDateTime agora = OffsetDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
        // Espelha EXATAMENTE a formula GENERATED da V013 (pesos 4/2/1 -
        // categoria e o discriminador mais forte, faixa o mais fraco).
        this.especificidade = (!TaxaCanal.CURINGA.equals(this.categoriaCanal) ? 4 : 0)
                + (!TaxaCanal.CURINGA.equals(this.tipoAnuncio) ? 2 : 0)
                + ((this.faixaValorMin != null || this.faixaValorMax != null) ? 1 : 0);
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

    public TipoTaxaCanal getTipoTaxa() {
        return tipoTaxa;
    }

    public NaturezaCusto getNaturezaCusto() {
        return naturezaCusto;
    }

    public BaseIncidencia getBaseIncidencia() {
        return baseIncidencia;
    }

    public String getCategoriaCanal() {
        return categoriaCanal;
    }

    public String getTipoAnuncio() {
        return tipoAnuncio;
    }

    public BigDecimal getFaixaValorMin() {
        return faixaValorMin;
    }

    public BigDecimal getFaixaValorMax() {
        return faixaValorMax;
    }

    public OffsetDateTime getVigenciaInicio() {
        return vigenciaInicio;
    }

    public OffsetDateTime getVigenciaFim() {
        return vigenciaFim;
    }

    public BigDecimal getPercentual() {
        return percentual;
    }

    public BigDecimal getValorFixo() {
        return valorFixo;
    }

    public BigDecimal getValorMinimo() {
        return valorMinimo;
    }

    public BigDecimal getValorMaximo() {
        return valorMaximo;
    }

    public String getMoeda() {
        return moeda;
    }

    public int getEspecificidade() {
        return especificidade;
    }

    public ConfiancaTaxa getConfianca() {
        return confianca;
    }

    public OrigemTaxa getOrigem() {
        return origem;
    }

    public String getFonteUrl() {
        return fonteUrl;
    }

    public OffsetDateTime getConsultadoEm() {
        return consultadoEm;
    }

    public String getObservacao() {
        return observacao;
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
