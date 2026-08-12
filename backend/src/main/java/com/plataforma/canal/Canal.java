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

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
