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

    // SQL declara "text", nao char(64), mesmo o CHECK exigindo exatamente
    // 64 hex (ck_cliente_documento_hash_formato via regex). Sem length:
    // um @Column(length=64) aqui mapearia para varchar(64) e divergiria
    // do tipo real da coluna. Compare com
    // EventoIngerido.hashPayload, que E char(64) de verdade (V012) - as
    // duas colunas parecem iguais e nao sao; conferido linha a linha nas
    // migrations.
    @Column(name = "documento_hash")
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

    /**
     * Metodo de INTENCAO (dívida 1 do docs/ESTADO.md, "mesma coisa para
     * Cliente"): enriquece um cliente JA EXISTENTE (mesma chave natural
     * canalId+idExterno) com dado mais novo de um reprocessamento. So o
     * pipeline chama isto (com.plataforma.ingestao.ServicoIngestao).
     *
     * NUNCA MUDAM: id, tenantId, canalId, idExterno - mesma razao do
     * {@link com.plataforma.pedido.Pedido#atualizarAPartirDaOrigem}: sao a
     * identidade da linha, nao dado que se atualiza.
     *
     * REGRA DE MERGE: para nome, apelidoOrigem, email, telefone,
     * documentoTipo, documentoHash, documentoMascarado - um valor NULO em
     * {@code origem} preserva o que ja estava gravado, nunca apaga. O
     * cliente e a tabela mais sensivel do sistema (LGPD, V007) e um
     * payload de atualizacao de PEDIDO raramente repete o cadastro
     * completo do comprador; tratar "nao veio desta vez" como "apague o
     * que tinha" perderia dado bom (ex.: um telefone ja capturado) so
     * porque um evento seguinte, sobre outro assunto, nao trouxe de novo.
     *
     * dadosOrigem sempre e sobrescrito (nunca fica null - o construtor ja
     * garante isso, defaultando para "{}"): e o extrato do payload mais
     * recente, faz sentido refletir sempre o ultimo evento visto.
     *
     * LIMITACAO CONHECIDA, mesma familia da registrada em
     * Pedido.atualizarAPartirDaOrigem: {@code tipo} (TipoCliente) NUNCA e
     * null depois de construido - o construtor de Cliente ja aplica o
     * default PESSOA_FISICA quando o adaptador nao confirma o tipo (ver
     * AdaptadorBling.traduzirCliente, contato.tipoPessoa fora de
     * F/J). Ou seja, um {@code origem.tipo} "PESSOA_FISICA" pode ser um
     * dado CONFIRMADO pela fonte (tipoPessoa='F') ou um NEUTRO por falta
     * de confirmacao - as duas coisas sao indistinguiveis neste ponto do
     * pipeline, porque a distincao se perde dentro do proprio construtor
     * de Cliente antes de chegar aqui. Por isso {@code tipo} e sempre
     * sobrescrito por {@code origem.tipo}, igual a decisao para
     * Pedido.status: consistente com o resto do sistema, mas nao resolve
     * o risco de um evento posterior "rebaixar" um PESSOA_JURIDICA
     * confirmado para o neutro PESSOA_FISICA. Resolver de verdade exigiria
     * o adaptador expor a distincao "confirmado vs. neutro" para alem do
     * enum ja resolvido - fora do escopo desta rodada.
     */
    public void atualizarAPartirDaOrigem(Cliente origem) {
        this.tipo = origem.tipo;
        this.dadosOrigem = origem.dadosOrigem;

        this.nome = (origem.nome != null) ? origem.nome : this.nome;
        this.apelidoOrigem = (origem.apelidoOrigem != null) ? origem.apelidoOrigem : this.apelidoOrigem;
        this.email = (origem.email != null) ? origem.email : this.email;
        this.telefone = (origem.telefone != null) ? origem.telefone : this.telefone;
        this.documentoTipo = (origem.documentoTipo != null) ? origem.documentoTipo : this.documentoTipo;
        this.documentoHash = (origem.documentoHash != null) ? origem.documentoHash : this.documentoHash;
        this.documentoMascarado = (origem.documentoMascarado != null) ? origem.documentoMascarado : this.documentoMascarado;

        this.atualizadoEm = OffsetDateTime.now();
    }
}
