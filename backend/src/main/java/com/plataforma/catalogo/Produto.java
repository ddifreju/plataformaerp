package com.plataforma.catalogo;

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
 * Mapeamento da tabela produto (migration V006). Agrupador conceitual do
 * que se anuncia - quem se vende de verdade e a Variacao (o SKU).
 *
 * SOBRE canalId (leia aqui, e so aqui - as demais entidades so remetem a
 * este comentario): a FK para canal no banco e COMPOSTA,
 * (tenant_id, canal_id) -> canal (tenant_id, id) - convencao 2 da V005,
 * formalizada na decisao 0015. Em JPA isso NAO vira @ManyToOne. Guardamos
 * so o UUID do canal (canalId), com o tenant_id implicito por @TenantId.
 * A decisao 0015 e explicita: "associacao com chave composta em JPA e
 * fonte conhecida de complexidade, e a legibilidade vale mais que a
 * navegacao de objeto". Custo aceito: quem precisar dos dados do canal
 * busca com RepositorioCanal.findById(produto.getCanalId()) - um select a
 * mais, zero mapeamento de relacionamento composto. A integridade
 * referencial cross-tenant continua garantida, so que pelo BANCO (FK
 * composta), nao pelo Hibernate.
 */
@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    // Ver javadoc da classe: UUID puro, nao @ManyToOne (decisao 0015).
    @Column(name = "canal_id")
    private UUID canalId;

    @Column(name = "id_externo")
    private String idExterno;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "marca")
    private String marca;

    @Column(name = "categoria")
    private String categoria;

    // char(8) no banco. Ver nota de menor confianca no relatorio final
    // sobre validacao de char(n) vs varchar em ddl-auto: validate.
    @Column(name = "ncm", length = 8)
    private String ncm;

    @Column(name = "cest", length = 7)
    private String cest;

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

    protected Produto() {
        // exigido pelo JPA
    }

    public Produto(UUID canalId, String idExterno, String titulo, String descricao, String marca,
            String categoria, String ncm, String cest, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.canalId = canalId;
        this.idExterno = idExterno;
        this.titulo = titulo;
        this.descricao = descricao;
        this.marca = marca;
        this.categoria = categoria;
        this.ncm = ncm;
        this.cest = cest;
        this.ativo = true;
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

    public String getIdExterno() {
        return idExterno;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getMarca() {
        return marca;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getNcm() {
        return ncm;
    }

    public String getCest() {
        return cest;
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
