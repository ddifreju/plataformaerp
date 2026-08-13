package com.plataforma.autenticacao;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Acesso POS-LOGIN a usuario. Predicado de tenant vem do @TenantId da
 * entidade (decisao 0007) - nenhum metodo aqui deve escrever
 * "WHERE tenant_id = ?" a mao.
 *
 * NUNCA use este repositorio para autenticar (ver a armadilha 1 da V014 e
 * {@link RepositorioLoginUsuario}): qualquer metodo derivado aqui embute
 * o predicado de tenant automaticamente, e no momento do login nao ha
 * tenant no contexto - a busca sempre devolveria vazio.
 */
public interface RepositorioUsuario extends JpaRepository<Usuario, UUID> {

    /**
     * Revalidacao POR REQUISICAO de que o usuario ainda tem acesso -
     * armadilha 6 da V014: "ativo e verificado no login, mas a sessao ja
     * aberta nao cai sozinha". Chamado pelo FiltroTenant depois de definir
     * o ContextoTenant com o tenant do principal autenticado, para que o
     * predicado de tenant (@TenantId + RLS) restrinja a busca ao usuario
     * daquele tenant especifico - o mesmo padrao de
     * RepositorioTenant.existsByIdAndAtivoTrue, so que para a pessoa em
     * vez da loja.
     *
     * CANDIDATA A CACHE, mesma nota de RepositorioTenant: roda em toda
     * requisicao autenticada. Nao cacheado agora pelo mesmo motivo -
     * poucos usuarios por tenant, consulta por chave primaria, e invalidar
     * corretamente no exato instante da desativacao e mais complexo do que
     * o ganho justifica hoje.
     */
    boolean existsByIdAndAtivoTrue(UUID id);

    /**
     * Carimba o login bem sucedido. UPDATE explicito em vez de
     * save(usuario) porque so DUAS colunas mudam (GRANT UPDATE da V014
     * cobre bem mais que isso, mas escrever so o que muda evita reescrever
     * nome/papel/ativo por engano a partir de uma entidade desatualizada
     * em memoria).
     *
     * Devolve a quantidade de linhas afetadas DE PROPOSITO (armadilha 3 da
     * V014): 0 e um resultado valido de se obter (RLS sem predicado
     * batendo, por exemplo o ContextoTenant nao ter sido definido ainda),
     * e "afetou 0 linhas" precisa ser um fato observavel para quem chama,
     * nao um void que esconde o problema.
     */
    @Modifying
    @Query("update Usuario u set u.ultimoAcessoEm = :agora, u.atualizadoEm = :agora where u.id = :id")
    int marcarUltimoAcesso(UUID id, OffsetDateTime agora);
}
