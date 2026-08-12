package com.plataforma.comum.tenant;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento da tabela `tenant` (migration V002). E o catalogo
 * administrativo de tenants: SEM coluna tenant_id (o proprio id E o
 * tenant) e SEM Row Level Security - ver o comentario da V002 para o
 * porque.
 *
 * Esta entidade e efetivamente somente-leitura do ponto de vista da
 * aplicacao web: o papel de banco app_aplicacao so tem GRANT SELECT
 * nesta tabela (V004). Nao ha setters de proposito - criar, renomear ou
 * desativar tenant e fluxo de provisionamento, fora desta aplicacao.
 */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    protected Tenant() {
        // exigido pelo JPA
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
