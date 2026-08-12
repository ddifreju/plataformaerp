package com.plataforma.conversa;

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
 * Mapeamento da tabela conversa (migration V011). Thread de atendimento -
 * a unidade de trabalho, nao a mensagem individual (ver cabecalho da
 * V011). Conteudo de conversa e dado pessoal (LGPD); a exclusao a pedido
 * do titular e anonimizacao (anonimizadoEm), nao remocao - o mecanismo de
 * ESCRITA dessa anonimizacao e trabalho de outra tarefa, esta entidade so
 * mapeia e constroi.
 *
 * canalId, clienteId e pedidoId sao UUID puro, nao @ManyToOne - decisao
 * 0015, ver o comentario completo em Produto.canalId.
 *
 * ultimaMensagemEm fica de fora do construtor: e materializada pela
 * ingestao no mesmo passo em que grava a mensagem (ver comentario da
 * coluna no SQL), nao no momento em que a conversa nasce.
 */
@Entity
@Table(name = "conversa")
public class Conversa {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "canal_id", nullable = false)
    private UUID canalId;

    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(name = "pedido_id")
    private UUID pedidoId;

    @Column(name = "id_externo")
    private String idExterno;

    @Column(name = "assunto")
    private String assunto;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusConversa status;

    @Column(name = "iniciada_em", nullable = false)
    private OffsetDateTime iniciadaEm;

    @Column(name = "ultima_mensagem_em")
    private OffsetDateTime ultimaMensagemEm;

    @Column(name = "encerrada_em")
    private OffsetDateTime encerradaEm;

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

    protected Conversa() {
        // exigido pelo JPA
    }

    public Conversa(UUID canalId, UUID clienteId, UUID pedidoId, String idExterno, String assunto,
            StatusConversa status, OffsetDateTime iniciadaEm, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.canalId = canalId;
        this.clienteId = clienteId;
        this.pedidoId = pedidoId;
        this.idExterno = idExterno;
        this.assunto = assunto;
        // Mesma coluna com DEFAULT 'ABERTA' no banco: coalescido aqui pelo
        // mesmo motivo do tipo em Cliente (ver ConsultaAuditada).
        this.status = (status != null) ? status : StatusConversa.ABERTA;
        this.iniciadaEm = iniciadaEm;
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

    public UUID getPedidoId() {
        return pedidoId;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getAssunto() {
        return assunto;
    }

    public StatusConversa getStatus() {
        return status;
    }

    public OffsetDateTime getIniciadaEm() {
        return iniciadaEm;
    }

    public OffsetDateTime getUltimaMensagemEm() {
        return ultimaMensagemEm;
    }

    public OffsetDateTime getEncerradaEm() {
        return encerradaEm;
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
