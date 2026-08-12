package com.plataforma.auditoria;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento da tabela consulta_auditada (migration V003). Atende a
 * regra 3 do CLAUDE.md: "toda resposta numerica e rastreavel" - guarda
 * a pergunta, o SQL executado e os IDs devolvidos, para provar ou
 * corrigir um numero contestado pelo cliente em minutos.
 *
 * tenantId e anotado com @TenantId (Hibernate), nao e uma coluna comum:
 * e ele que faz o Hibernate acrescentar o predicado de tenant
 * automaticamente em toda consulta e preencher o valor sozinho no
 * insert (decisao 0007, camada 3). O tipo e UUID porque a coluna no
 * banco e `uuid` (V003) e porque ResolvedorTenantHibernate resolve para
 * UUID - os dois lados dessa ponte tem que casar exatamente.
 *
 * @TenantId exige que o campo seja imutavel na entidade: por isso nao
 * ha setter para tenantId, e ele nunca e atribuido no construtor - quem
 * preenche e o Hibernate, na hora do insert.
 */
@Entity
@Table(name = "consulta_auditada")
public class ConsultaAuditada {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "pergunta")
    private String pergunta;

    @Column(name = "sql_executado", nullable = false)
    private String sqlExecutado;

    @Column(name = "ids_retornados")
    private UUID[] idsRetornados;

    @Column(name = "linhas_retornadas", nullable = false)
    private int linhasRetornadas;

    @Column(name = "executado_em", nullable = false)
    private OffsetDateTime executadoEm;

    @Column(name = "executado_por")
    private String executadoPor;

    protected ConsultaAuditada() {
        // exigido pelo JPA
    }

    /**
     * O id e o instante sao gerados aqui, em Java, em vez de depender do
     * DEFAULT da coluna no banco (gen_random_uuid()/now(), ver V003): o
     * Hibernate monta o INSERT listando todas as colunas mapeadas, e um
     * campo Java nulo vira um NULL explicito no INSERT, que SOBRESCREVE
     * o DEFAULT da coluna em vez de deixar o banco preenche-lo. Gerando
     * os dois aqui, o valor gravado e sempre o que a aplicacao decidiu,
     * nunca depende dessa nuance de como o Hibernate monta o INSERT.
     */
    public ConsultaAuditada(String pergunta, String sqlExecutado, UUID[] idsRetornados,
            int linhasRetornadas, String executadoPor) {
        if (sqlExecutado == null || sqlExecutado.isBlank()) {
            throw new IllegalArgumentException("sqlExecutado nao pode ser vazio: e a prova do numero reportado.");
        }

        this.id = UUID.randomUUID();
        this.pergunta = pergunta;
        this.sqlExecutado = sqlExecutado;
        // Nunca gravamos NULL num array NOT NULL DEFAULT '{}' (V003).
        this.idsRetornados = (idsRetornados != null) ? idsRetornados : new UUID[0];
        this.linhasRetornadas = linhasRetornadas;
        this.executadoPor = executadoPor;
        this.executadoEm = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPergunta() {
        return pergunta;
    }

    public String getSqlExecutado() {
        return sqlExecutado;
    }

    public UUID[] getIdsRetornados() {
        return idsRetornados;
    }

    public int getLinhasRetornadas() {
        return linhasRetornadas;
    }

    public OffsetDateTime getExecutadoEm() {
        return executadoEm;
    }

    public String getExecutadoPor() {
        return executadoPor;
    }
}
