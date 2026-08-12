package com.plataforma.cliente;

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
 * Mapeamento da tabela cliente (migration V007). TABELA MAIS SENSIVEL DO
 * SISTEMA - dado pessoal de terceiro, LGPD, somos operador. Leia o
 * cabecalho da V007 antes de tocar aqui.
 *
 * anonimizadoEm existe para o caminho de exclusao a pedido do titular
 * (Art. 18, VI): a linha sobrevive (o pedido depende dela), a pessoa nao.
 * A ANONIMIZACAO EM SI (UPDATE zerando nome/email/telefone/documento/
 * dados_origem) e operacao futura, fora do escopo deste mapeamento -
 * esta entidade so getters/construcao, sem logica de negocio (regra do
 * enunciado desta tarefa). Quem implementar o fluxo de anonimizacao vai
 * precisar de um mecanismo de escrita que esta classe hoje nao tem.
 *
 * documentoHash e char(64) no banco (HMAC-SHA256 hex) - ver nota de menor
 * confianca no relatorio final sobre validacao de tamanho fixo.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "canal_id")
    private UUID canalId;

    @Column(name = "id_externo")
    private String idExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoCliente tipo;

    @Column(name = "nome")
    private String nome;

    @Column(name = "apelido_origem")
    private String apelidoOrigem;

    @Column(name = "email")
    private String email;

    @Column(name = "telefone")
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento_tipo")
    private TipoDocumento documentoTipo;

    @Column(name = "documento_hash", length = 64)
    private String documentoHash;

    @Column(name = "documento_mascarado")
    private String documentoMascarado;

    @Column(name = "anonimizado_em")
    private OffsetDateTime anonimizadoEm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_origem", nullable = false)
    private String dadosOrigem;

    @Column(name = "sincronizado_em")
    private OffsetDateTime sincronizadoEm;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Cliente() {
        // exigido pelo JPA
    }

    public Cliente(UUID canalId, String idExterno, TipoCliente tipo, String nome,
            String apelidoOrigem, String email, String telefone, TipoDocumento documentoTipo,
            String documentoHash, String documentoMascarado, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.canalId = canalId;
        this.idExterno = idExterno;
        // Mesma coluna com DEFAULT 'PESSOA_FISICA' no banco: se deixarmos
        // nulo aqui, o INSERT do Hibernate grava NULL e viola o NOT NULL,
        // em vez de cair no DEFAULT (ver javadoc de ConsultaAuditada).
        this.tipo = (tipo != null) ? tipo : TipoCliente.PESSOA_FISICA;
        this.nome = nome;
        this.apelidoOrigem = apelidoOrigem;
        this.email = email;
        this.telefone = telefone;
        this.documentoTipo = documentoTipo;
        this.documentoHash = documentoHash;
        this.documentoMascarado = documentoMascarado;
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

    public TipoCliente getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public String getApelidoOrigem() {
        return apelidoOrigem;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public TipoDocumento getDocumentoTipo() {
        return documentoTipo;
    }

    public String getDocumentoHash() {
        return documentoHash;
    }

    public String getDocumentoMascarado() {
        return documentoMascarado;
    }

    public OffsetDateTime getAnonimizadoEm() {
        return anonimizadoEm;
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
