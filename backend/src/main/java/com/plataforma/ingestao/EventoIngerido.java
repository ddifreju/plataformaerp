package com.plataforma.ingestao;

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
 * Mapeamento da tabela evento_ingerido (migration V012) - a trava de
 * idempotencia da ingestao. Leia a SEMANTICA (3 casos) e a receita de
 * UPSERT no cabecalho da V012 antes de usar esta entidade num pipeline de
 * verdade: o UPSERT ali descrito e uma INSTRUCAO SO
 * (INSERT ... ON CONFLICT ... DO UPDATE ... WHERE hash_payload IS
 * DISTINCT FROM ...), porque SELECT-depois-INSERT tem corrida sob
 * concorrencia. Esta classe so mapeia a tabela; ela NAO implementa esse
 * upsert (seria logica de negocio/persistencia especifica, fora do
 * escopo de um mapeamento JPA simples com JpaRepository.save).
 *
 * canalId e UUID puro, nao @ManyToOne - decisao 0015, ver o comentario
 * completo em Produto.canalId.
 *
 * entidadeTipo/entidadeId sao POLIMORFICOS e SEM FK, de proposito (ver
 * comentario da coluna no SQL): sao ponteiro de DIAGNOSTICO, nunca
 * relacao de negocio. Nenhuma query de dominio deve navegar por eles.
 *
 * UNICA tabela da Fase 1 com GRANT DELETE para a aplicacao (convencao 6
 * da V005, excecao anunciada) - isso nao muda o mapeamento JPA em si,
 * so significa que RepositorioEventoIngerido.deleteById funciona aqui
 * sem violar GRANT, ao contrario de qualquer outra entidade deste pacote.
 */
@Entity
@Table(name = "evento_ingerido")
public class EventoIngerido {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "canal_id", nullable = false)
    private UUID canalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false)
    private TipoEvento tipoEvento;

    @Column(name = "id_externo", nullable = false)
    private String idExterno;

    @Column(name = "hash_payload", nullable = false, length = 64)
    private String hashPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_bruto", nullable = false)
    private String payloadBruto;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusEventoIngerido status;

    @Column(name = "recebido_em", nullable = false)
    private OffsetDateTime recebidoEm;

    @Column(name = "processado_em")
    private OffsetDateTime processadoEm;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "erro_mensagem")
    private String erroMensagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "entidade_tipo")
    private TipoEntidade entidadeTipo;

    @Column(name = "entidade_id")
    private UUID entidadeId;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected EventoIngerido() {
        // exigido pelo JPA
    }

    /**
     * Todo evento nasce RECEBIDO, com zero tentativas e sem processado_em
     * / erro_mensagem / entidade_tipo / entidade_id - esses quatro so
     * ganham valor quando o pipeline de processamento roda, que e
     * trabalho de outra tarefa. status e tentativas sao fixados aqui (nao
     * parametros) porque, diferente de outras colunas com DEFAULT deste
     * schema, nunca variam na criacao de um evento novo - variam so em
     * transicao posterior.
     */
    public EventoIngerido(UUID canalId, TipoEvento tipoEvento, String idExterno,
            String hashPayload, String payloadBruto) {
        if (hashPayload == null || !hashPayload.matches("^[0-9a-f]{64}$")) {
            // hashPayload e o que distingue reenvio identico (no-op) de
            // atualizacao legitima (reprocessa) - ver cabecalho da V012.
            // Um hash mal formado aqui corrompe a idempotencia da
            // ingestao inteira, por isso falha cedo em vez de deixar o
            // banco rejeitar so no INSERT.
            throw new IllegalArgumentException(
                    "hashPayload precisa ser sha256 em hex minusculo (64 caracteres).");
        }
        this.id = UUID.randomUUID();
        this.canalId = canalId;
        this.tipoEvento = tipoEvento;
        this.idExterno = idExterno;
        this.hashPayload = hashPayload;
        this.payloadBruto = payloadBruto;
        this.status = StatusEventoIngerido.RECEBIDO;
        this.tentativas = 0;
        OffsetDateTime agora = OffsetDateTime.now();
        this.recebidoEm = agora;
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

    public TipoEvento getTipoEvento() {
        return tipoEvento;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getHashPayload() {
        return hashPayload;
    }

    public String getPayloadBruto() {
        return payloadBruto;
    }

    public StatusEventoIngerido getStatus() {
        return status;
    }

    public OffsetDateTime getRecebidoEm() {
        return recebidoEm;
    }

    public OffsetDateTime getProcessadoEm() {
        return processadoEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getErroMensagem() {
        return erroMensagem;
    }

    public TipoEntidade getEntidadeTipo() {
        return entidadeTipo;
    }

    public UUID getEntidadeId() {
        return entidadeId;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
