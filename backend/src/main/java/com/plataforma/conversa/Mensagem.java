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
 * Mapeamento da tabela mensagem (migration V011). Mensagem de um thread
 * de atendimento, imutavel na pratica (o que muda tem coluna propria,
 * lidaEm) - por isso NAO existe campo atualizadoEm aqui: a tabela
 * mensagem nao tem essa coluna no SQL, ao contrario de todas as outras
 * deste modelo canonico. Confirmado na V011, nao e omissao.
 *
 * conversaId e UUID puro, nao @ManyToOne - decisao 0015, ver o comentario
 * completo em Produto.canalId. Nao existe @OneToMany de Conversa para
 * Mensagem por simetria com essa mesma decisao: uma colecao mapeada
 * exigiria o Hibernate reconstruir o join composto (tenant_id,
 * conversa_id) por baixo dos panos, o mesmo problema que a 0015 evita.
 * Quem precisa das mensagens de uma conversa usa RepositorioMensagem.
 */
@Entity
@Table(name = "mensagem")
public class Mensagem {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "conversa_id", nullable = false)
    private UUID conversaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direcao", nullable = false)
    private DirecaoMensagem direcao;

    @Enumerated(EnumType.STRING)
    @Column(name = "autor_tipo", nullable = false)
    private TipoAutorMensagem autorTipo;

    @Column(name = "autor_identificacao")
    private String autorIdentificacao;

    @Column(name = "conteudo")
    private String conteudo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_conteudo", nullable = false)
    private TipoConteudoMensagem tipoConteudo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "anexos", nullable = false)
    private String anexos;

    @Column(name = "enviada_em", nullable = false)
    private OffsetDateTime enviadaEm;

    @Column(name = "lida_em")
    private OffsetDateTime lidaEm;

    @Column(name = "id_externo")
    private String idExterno;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_origem", nullable = false)
    private String dadosOrigem;

    @Column(name = "sincronizado_em")
    private OffsetDateTime sincronizadoEm;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected Mensagem() {
        // exigido pelo JPA
    }

    public Mensagem(UUID conversaId, DirecaoMensagem direcao, TipoAutorMensagem autorTipo,
            String autorIdentificacao, String conteudo, TipoConteudoMensagem tipoConteudo,
            String anexos, OffsetDateTime enviadaEm, String idExterno, String dadosOrigem) {
        this.id = UUID.randomUUID();
        this.conversaId = conversaId;
        this.direcao = direcao;
        this.autorTipo = autorTipo;
        this.autorIdentificacao = autorIdentificacao;
        this.conteudo = conteudo;
        // Mesma coluna com DEFAULT 'TEXTO' no banco: coalescido aqui pelo
        // mesmo motivo do tipo em Cliente (ver ConsultaAuditada).
        this.tipoConteudo = (tipoConteudo != null) ? tipoConteudo : TipoConteudoMensagem.TEXTO;
        // anexos e uma LISTA (CHECK jsonb_typeof(anexos) = 'array'); o
        // default aqui tem que ser "[]", nunca "{}".
        this.anexos = (anexos != null) ? anexos : "[]";
        this.enviadaEm = enviadaEm;
        this.idExterno = idExterno;
        this.dadosOrigem = (dadosOrigem != null) ? dadosOrigem : "{}";
        this.criadoEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getConversaId() {
        return conversaId;
    }

    public DirecaoMensagem getDirecao() {
        return direcao;
    }

    public TipoAutorMensagem getAutorTipo() {
        return autorTipo;
    }

    public String getAutorIdentificacao() {
        return autorIdentificacao;
    }

    public String getConteudo() {
        return conteudo;
    }

    public TipoConteudoMensagem getTipoConteudo() {
        return tipoConteudo;
    }

    public String getAnexos() {
        return anexos;
    }

    public OffsetDateTime getEnviadaEm() {
        return enviadaEm;
    }

    public OffsetDateTime getLidaEm() {
        return lidaEm;
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
}
