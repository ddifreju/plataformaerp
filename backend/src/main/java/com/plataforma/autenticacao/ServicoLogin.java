package com.plataforma.autenticacao;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que acontece DEPOIS que o Spring Security ja autenticou com sucesso -
 * hoje, so carimbar {@code usuario.ultimo_acesso_em}. Nao bloqueia o
 * login se falhar: e um carimbo auxiliar ("esta conta ainda e usada?"),
 * nunca parte do caminho critico de autenticacao.
 */
@Service
public class ServicoLogin {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoLogin.class);

    private final RepositorioUsuario repositorioUsuario;

    public ServicoLogin(RepositorioUsuario repositorioUsuario) {
        this.repositorioUsuario = repositorioUsuario;
    }

    /**
     * ORDEM CRITICA (armadilha 3 da V014): quem chama este metodo
     * ({@link TratadorSucessoLogin}) PRECISA ter definido o
     * {@link com.plataforma.comum.tenant.ContextoTenant} com o tenant do
     * usuario recem-autenticado ANTES desta chamada. O UPDATE roda com
     * RLS ativo, e o GUC de tenant e setado no {@code getConnection()} do
     * {@code DataSourceComTenant} - se a conexao for obtida sem o
     * contexto certo, o UPDATE afeta ZERO linhas SEM ERRO NENHUM, e a
     * coluna simplesmente nunca atualiza, em silencio. Por isso este
     * metodo em si NAO mexe no ContextoTenant - so consome o tenant que
     * ja deveria estar resolvido, e confere o retorno (a segunda
     * providencia que a mesma armadilha exige).
     */
    @Transactional
    public void registrarAcessoBemSucedido(UUID usuarioId) {
        int linhasAfetadas = repositorioUsuario.marcarUltimoAcesso(usuarioId, OffsetDateTime.now());
        if (linhasAfetadas != 1) {
            LOG.warn("marcarUltimoAcesso afetou {} linha(s) para usuarioId={} - esperado exatamente 1. "
                    + "Provavel bug de ContextoTenant nao definido antes desta chamada (armadilha 3 da V014).",
                    linhasAfetadas, usuarioId);
        }
    }
}
