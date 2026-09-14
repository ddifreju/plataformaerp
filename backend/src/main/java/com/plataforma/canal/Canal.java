package com.plataforma.canal;

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
 * Mapeamento da tabela canal (migration V005). Integracao configurada de
 * um tenant: fonte de venda (ML, Shopee), de dado (ERP) ou de comunicacao
 * (WhatsApp). Nunca guarda segredo - so o NOME da variavel/cofre
 * (chave_credencial).
 *
 * Mesmo padrao de construcao de ConsultaAuditada: id e timestamps gerados
 * em Java (nunca deixados para o DEFAULT do banco), @TenantId cuida do
 * tenant sozinho, sem setter.
 *
 * SOBRE jsonb (vale para toda entidade deste pacote em diante, nao repito
 * o raciocinio em cada uma): dados_origem e mapeado como String com
 * @JdbcTypeCode(SqlTypes.JSON). Escolha deliberada em vez de
 * Map<String,Object>: o conteudo e opaco por natureza (e payload da fonte
 * que nao coube no modelo canonico - decisao 0002), ninguem no dominio
 * deve navegar essas chaves em Java, e String evita trazer um
 * dependencia/serializador extra so para isso. Quem precisa interpretar o
 * conteudo (um adaptador especifico) desserializa por fora, sabendo o
 * formato daquela fonte.
 */
@Entity
@Table(name = "canal")
public class Canal {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "codigo", nullable = false)
    private String codigo;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoCanal tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false)
    private CategoriaCanal categoria;

    @Column(name = "id_externo")
    private String idExterno;

    @Column(name = "chave_credencial")
    private String chaveCredencial;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_origem", nullable = false)
    private String dadosOrigem;

    @Column(name = "sincronizado_em")
    private OffsetDateTime sincronizadoEm;

    /**
     * Declaracao da LOJISTA sobre a origem dos pedidos deste canal
     * (migration V016, decisao 0033). O {@code length = 20} e LOAD-BEARING:
     * sem ele o Hibernate espera {@code varchar(255)} para este
     * {@code @Enumerated(EnumType.STRING)} e {@code ddl-auto: validate}
     * derruba o boot - mesmo modo de falha da decisao 0027 (ver secao 2 do
     * cabecalho da V016).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "escopo_declarado", nullable = false, length = 20)
    private EscopoCanal escopoDeclarado;

    /**
     * Canal de quem este e copia, SE E SOMENTE SE {@code escopoDeclarado}
     * for {@link EscopoCanal#ESPELHO} (ck_canal_espelho_exige_alvo, V016).
     * UUID solto, NAO {@code @ManyToOne} (decisao 0015): a FK e composta
     * com {@code tenant_id} no banco, e associacao JPA com chave composta
     * e complexidade que a legibilidade nao paga aqui.
     */
    @Column(name = "espelha_canal_id")
    private UUID espelhaCanalId;

    /** Quando a declaracao foi feita. Preenchido se e somente se {@code escopoDeclarado <> NAO_DECLARADO}. */
    @Column(name = "escopo_declarado_em")
    private OffsetDateTime escopoDeclaradoEm;

    /**
     * Usuario que declarou. UUID solto (mesma razao de {@code espelhaCanalId}),
     * nullable mesmo quando ha declaracao: NULL = "declarado fora da
     * aplicacao" (seed, provisionamento, migration) - estado real, nao
     * ausencia a preencher (secao 4 do cabecalho da V016).
     *
     * PROVA PONTUAL, NUNCA METRICA (decisao 0012): nenhuma consulta deste
     * sistema agrupa ou ordena por este campo. O acesso legitimo e ler a
     * declaracao do PROPRIO canal sendo explicado ou contestado - nunca
     * "quem declarou mais", "ranking de configuracao por pessoa" ou
     * qualquer coisa que trate esta coluna como metrica de pessoa.
     */
    @Column(name = "escopo_declarado_por")
    private UUID escopoDeclaradoPor;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Canal() {
        // exigido pelo JPA
    }

    /**
     * Canal nasce sempre ativo (mesmo default da coluna, DEFAULT true) e
     * sem sincronizacao ainda (sincronizado_em so e preenchido pelo
     * adaptador na primeira leitura da fonte). Igual a ConsultaAuditada:
     * id, criado_em e atualizado_em sao gerados aqui, nao deixados para o
     * DEFAULT do banco - o Hibernate lista todas as colunas mapeadas no
     * INSERT, e um campo Java nulo sobrescreveria o DEFAULT com NULL.
     */
    public Canal(String codigo, String nome, TipoCanal tipo, CategoriaCanal categoria,
            String idExterno, String chaveCredencial, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.codigo = codigo;
        this.nome = nome;
        this.tipo = tipo;
        this.categoria = categoria;
        this.idExterno = idExterno;
        this.chaveCredencial = chaveCredencial;
        this.ativo = true;
        // Mesmo racional do idsRetornados em ConsultaAuditada: nunca
        // gravamos NULL num jsonb NOT NULL DEFAULT '{}'::jsonb.
        this.dadosOrigem = (dadosOrigem != null) ? dadosOrigem : "{}";
        // Mesmo racional, agora para escopo_declarado (V016, decisao
        // 0033): a coluna e NOT NULL DEFAULT 'NAO_DECLARADO' no banco, e o
        // Hibernate lista toda coluna mapeada no INSERT - um campo Java
        // nulo viraria um NULL explicito que SOBRESCREVE o DEFAULT do
        // banco. Todo canal novo nasce NAO_DECLARADO; as outras tres
        // colunas da declaracao continuam null ate uma declaracao real
        // acontecer.
        this.escopoDeclarado = EscopoCanal.NAO_DECLARADO;
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

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public TipoCanal getTipo() {
        return tipo;
    }

    public CategoriaCanal getCategoria() {
        return categoria;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getChaveCredencial() {
        return chaveCredencial;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public String getDadosOrigem() {
        return dadosOrigem;
    }

    public OffsetDateTime getSincronizadoEm() {
        return sincronizadoEm;
    }

    public EscopoCanal getEscopoDeclarado() {
        return escopoDeclarado;
    }

    public UUID getEspelhaCanalId() {
        return espelhaCanalId;
    }

    public OffsetDateTime getEscopoDeclaradoEm() {
        return escopoDeclaradoEm;
    }

    public UUID getEscopoDeclaradoPor() {
        return escopoDeclaradoPor;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    /**
     * Declara este canal como {@link EscopoCanal#FONTE_PRIMARIA}: os
     * pedidos dele nascem aqui, e ele passa a poder entrar numa soma entre
     * canais (decisao 0033).
     *
     * Muda as QUATRO colunas da declaracao numa unica atribuicao, no
     * mesmo espirito de {@code Pedido.atualizarAPartirDaOrigem}: os CHECK
     * da V016 sao avaliados por linha ao fim da instrucao (nunca campo a
     * campo), entao um estado intermediario com so parte das colunas
     * atualizadas seria recusado pelo banco - ou, pior, aceito por
     * coincidencia e inconsistente.
     *
     * {@code porUsuario} pode ser {@code null} (seed, provisionamento,
     * migration - "declarado fora da aplicacao", ver o Javadoc do campo).
     */
    public void declararFontePrimaria(UUID porUsuario) {
        this.escopoDeclarado = EscopoCanal.FONTE_PRIMARIA;
        this.espelhaCanalId = null;
        this.escopoDeclaradoEm = OffsetDateTime.now();
        this.escopoDeclaradoPor = porUsuario;
        this.atualizadoEm = OffsetDateTime.now();
    }

    /**
     * Declara este canal como {@link EscopoCanal#ESPELHO} de
     * {@code canalId}: os pedidos deste canal sao copia dos de
     * {@code canalId}, e ele fica de fora de qualquer soma entre canais
     * (decisao 0033). Continua ingerindo e continua consultavel
     * individualmente - a declaracao afeta so a soma.
     *
     * A validacao de QUEM {@code canalId} e (mesmo tenant, nao inexistente,
     * sem formar cadeia/ciclo, nao ser ele mesmo ESPELHO) e
     * responsabilidade de {@link ServicoEscopoDeCanal} - ela precisa olhar
     * OUTRAS linhas de canal, o que uma entidade isolada nao pode fazer.
     * Aqui so os dois invariantes que dependem SO deste objeto: alvo
     * informado, e alvo diferente de si mesmo
     * (ck_canal_nao_espelha_a_si_mesmo).
     */
    public void declararEspelhoDe(UUID canalId, UUID porUsuario) {
        if (canalId == null) {
            throw new IllegalArgumentException(
                    "espelhaCanalId nao pode ser nulo ao declarar ESPELHO (ck_canal_espelho_exige_alvo)");
        }
        if (canalId.equals(this.id)) {
            throw new IllegalArgumentException(
                    "canal nao pode ser declarado espelho de si mesmo (ck_canal_nao_espelha_a_si_mesmo)");
        }
        this.escopoDeclarado = EscopoCanal.ESPELHO;
        this.espelhaCanalId = canalId;
        this.escopoDeclaradoEm = OffsetDateTime.now();
        this.escopoDeclaradoPor = porUsuario;
        this.atualizadoEm = OffsetDateTime.now();
    }

    /**
     * Volta este canal para {@link EscopoCanal#NAO_DECLARADO} - o estado
     * que BLOQUEIA a soma entre canais (fail-closed, decisao 0033).
     *
     * Zera as QUATRO colunas juntas, pelo mesmo motivo do Javadoc de
     * {@link #declararFontePrimaria}: ck_canal_escopo_declarado_em_coerente
     * e ck_canal_escopo_declarado_por_coerente exigem que
     * {@code escopoDeclaradoEm}/{@code escopoDeclaradoPor} sejam NULL
     * quando o escopo e NAO_DECLARADO.
     */
    public void limparDeclaracao() {
        this.escopoDeclarado = EscopoCanal.NAO_DECLARADO;
        this.espelhaCanalId = null;
        this.escopoDeclaradoEm = null;
        this.escopoDeclaradoPor = null;
        this.atualizadoEm = OffsetDateTime.now();
    }
}
