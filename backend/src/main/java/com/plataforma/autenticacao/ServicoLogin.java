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
        // O try/catch NAO e defensivismo generico: sem ele, o javadoc
        // acima ("nao bloqueia o login se falhar") seria mentira.
        //
        // Quando este metodo roda, a autenticacao JA ACONTECEU: o
        // Authentication esta no SecurityContext e o cookie de sessao (com
        // changeSessionId aplicado) ja esta a caminho do navegador. Se um
        // timeout de conexao ou deadlock escapasse daqui, a excecao subiria
        // por TratadorSucessoLogin.onAuthenticationSuccess ANTES do
        // setStatus(204) - e o usuario receberia um erro 500 estando, de
        // fato, logado.
        //
        // Esse e o pior tipo de bug para quem opera sozinha: o cliente diz
        // "nao consegui entrar", o log mostra sessao criada com sucesso, e
        // as duas coisas sao verdade.
        //
        // Registrar o ultimo acesso e informacao util, nunca condicao para
        // entrar.
        //
        // ONDE FICA O try/catch, E POR QUE NAO E AQUI DENTRO:
        // este metodo e @Transactional, entao ele roda dentro de um proxy.
        // Capturar a excecao AQUI nao resolveria: uma falha de banco marca
        // a transacao como rollback-only, e o commit feito pelo proxy - ja
        // FORA deste corpo - lancaria UnexpectedRollbackException, que
        // passaria por cima de qualquer catch escrito aqui.
        // Por isso a guarda mora em TratadorSucessoLogin, que chama este
        // metodo de fora do limite transacional e por isso consegue
        // capturar tanto a falha do UPDATE quanto a do commit.
        int linhasAfetadas = repositorioUsuario.marcarUltimoAcesso(usuarioId, OffsetDateTime.now());
        if (linhasAfetadas != 1) {
            LOG.warn("marcarUltimoAcesso afetou {} linha(s) para usuarioId={} - esperado exatamente 1. "
                    + "Provavel bug de ContextoTenant nao definido antes desta chamada (armadilha 3 da V014).",
                    linhasAfetadas, usuarioId);
        }
    }
}
