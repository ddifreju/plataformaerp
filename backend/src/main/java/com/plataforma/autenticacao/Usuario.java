package com.plataforma.autenticacao;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento POS-LOGIN da tabela usuario (migration V014). "Pos-login"
 * porque esta entidade so serve para operacoes que acontecem DEPOIS que
 * ja existe um tenant no contexto (revalidar se o usuario continua ativo
 * a cada requisicao - {@link RepositorioUsuario#existsByIdAndAtivoTrue} -
 * e carimbar {@code ultimo_acesso_em} apos um login bem sucedido). O
 * caminho de login em si NUNCA usa esta entidade - ver
 * {@link RepositorioLoginUsuario} e a armadilha 1 do cabecalho da V014.
 *
 * SEM CAMPO senha_hash, DE PROPOSITO (armadilha 4 da V014): a aplicacao
 * tem GRANT SELECT na coluna inteira, entao nada no BANCO impediria o
 * hash de cair num JSON de resposta ou num log de debug se ele estivesse
 * mapeado aqui. O unico lugar do codigo que le senha_hash e
 * {@link RepositorioLoginUsuario}, via JDBC puro, para o UNICO uso
 * legitimo (comparar contra a senha informada no login).
 *
 * SEM CONSTRUTOR PUBLICO: esta aplicacao nao cria usuario (decisao 0023 -
 * "o primeiro usuario vem de provisionamento, nao de auto-cadastro"; ver
 * tambem a armadilha 8 da V014, sobre como provisionar via SQL). Se um
 * dia existir um endpoint de "convidar colega de loja", o construtor
 * entra junto com aquele endpoint, nao antes.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false)
    private PapelUsuario papel;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Column(name = "ultimo_acesso_em")
    private OffsetDateTime ultimoAcessoEm;

    protected Usuario() {
        // exigido pelo JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public PapelUsuario getPapel() {
        return papel;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public OffsetDateTime getUltimoAcessoEm() {
        return ultimoAcessoEm;
    }
}
