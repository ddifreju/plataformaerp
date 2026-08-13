package com.plataforma.autenticacao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * O UNICO caminho de leitura de usuario ANTES de existir sessao - ver os
 * blocos 1 e 2 do cabecalho de {@code V014__usuario.sql} e a "armadilha 1"
 * registrada la:
 *
 * <blockquote>
 * NAO USE UM UserDetailsService QUE FACA findByEmail VIA JPA. A entidade
 * Usuario tem @TenantId (padrao deste projeto), e o Hibernate acrescenta
 * o predicado de tenant a consulta. No login nao ha tenant, entao o
 * predicado vira {@code tenant_id = null} e o resultado e SEMPRE vazio -
 * antes mesmo do RLS entrar em cena.
 * </blockquote>
 *
 * Por isso esta classe usa {@link NamedParameterJdbcTemplate}, fora do
 * EntityManager, chamando diretamente {@code app_usuario_para_login}. A
 * funcao e SECURITY INVOKER: quem autoriza a leitura e a policy
 * {@code usuario_select_login} do banco, verificada pelo motor do
 * Postgres - este codigo nao concede nenhum privilegio, so encapsula a
 * chamada SQL.
 */
@Repository
public class RepositorioLoginUsuario {

    private static final String CONSULTA = """
            SELECT usuario_id, usuario_tenant_id, usuario_email, usuario_senha_hash,
                   usuario_nome, usuario_papel, usuario_ativo, tenant_ativo
              FROM app_usuario_para_login(:email)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RepositorioLoginUsuario(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * NAO marcar este metodo {@code @Transactional} (armadilha 9 da
     * V014): o GUC {@code app.login_email} que a funcao seta e LOCAL,
     * valido ate o FIM DA TRANSACAO. Uma chamada solta (autocommit, o
     * padrao para uma unica instrucao JDBC fora de transacao explicita)
     * fecha a fresta assim que esta instrucao termina. Se este metodo
     * fosse envolvido numa transacao mais longa, a fresta ficaria aberta
     * para este e-mail durante todo o resto dela.
     */
    public Optional<UsuarioParaLogin> buscarPorEmail(String email) {
        List<UsuarioParaLogin> linhas = jdbcTemplate.query(
                CONSULTA,
                Map.of("email", email == null ? "" : email),
                (linha, indice) -> new UsuarioParaLogin(
                        (UUID) linha.getObject("usuario_id"),
                        (UUID) linha.getObject("usuario_tenant_id"),
                        linha.getString("usuario_email"),
                        linha.getString("usuario_senha_hash"),
                        linha.getString("usuario_nome"),
                        PapelUsuario.valueOf(linha.getString("usuario_papel")),
                        linha.getBoolean("usuario_ativo"),
                        linha.getBoolean("tenant_ativo")));
        return linhas.stream().findFirst();
    }
}
