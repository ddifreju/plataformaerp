package com.plataforma.comum.tenant;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.autenticacao.RepositorioUsuario;
import com.plataforma.autenticacao.SessaoInvalidaException;
import com.plataforma.autenticacao.UsuarioAutenticado;
import com.plataforma.comum.web.ErroApi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Camada 1 da decisao 0007: resolve o tenant da requisicao e o define no
 * {@link ContextoTenant} antes de qualquer controller rodar.
 *
 * DESDE A TAREFA 17 (decisao 0023), o tenant vem do USUARIO AUTENTICADO,
 * nunca mais de um cabecalho. O antigo {@code X-Tenant-Id} foi REMOVIDO
 * (nao desativado por flag): com sessao existindo, aceitar um tenant
 * informado pelo cliente seria um vetor trivial de escalonamento
 * horizontal (qualquer chamador autenticado "seria" qualquer tenant so
 * trocando um header).
 *
 * <h2>A ordem deste filtro mudou, e e o ponto delicado da tarefa 17</h2>
 * Ate a tarefa 17 este filtro rodava em {@code Ordered.HIGHEST_PRECEDENCE}
 * (antes de QUALQUER outro filtro). Agora ele PRECISA rodar DEPOIS de
 * toda a cadeia do Spring Security (registrada por
 * {@code com.plataforma.autenticacao.ConfiguracaoSeguranca}): so depois
 * que o Security autentica a requisicao existe um {@code Authentication}
 * no {@code SecurityContext} de quem extrair o tenant. Se este filtro
 * continuasse com prioridade maxima, ele rodaria ANTES de qualquer
 * autenticacao acontecer, nao encontraria usuario nenhum e toda
 * requisicao - inclusive a de login - falharia com "tenant nao
 * resolvido". A ordem real (nao o {@code @Order} abaixo, que nunca teve
 * efeito por si so numa instancia criada com "new" - ver a nota no fim
 * deste Javadoc) e definida em
 * {@code ConfiguracaoTenant.registroFiltroTenant}, com
 * {@code SecurityProperties.DEFAULT_FILTER_ORDER + 1}.
 *
 * <h2>O que NAO muda (decisao 0007)</h2>
 * {@link ContextoTenant}, {@code @TenantId} e {@code DataSourceComTenant}
 * continuam identicos - so a FONTE do tenant muda, de header para
 * principal autenticado. Autenticacao responde "quem e voce"; este filtro
 * continua respondendo "de qual loja" - um usuario autenticado sem tenant
 * resolvido continua sendo erro (nunca acontece na pratica, porque o
 * tenant sai do PROPRIO usuario, mas o filtro falha fechado mesmo assim
 * se isso um dia deixar de ser verdade).
 *
 * <h2>Revalidacao por requisicao (armadilha 6 da V014)</h2>
 * "Ativo" e verificado no LOGIN (ver {@code UsuarioAutenticado.isEnabled}),
 * mas a SESSAO ja aberta nao cai sozinha so por isso - o principal fica
 * em cache na sessao HTTP. Por isso este filtro tambem revalida, a CADA
 * requisicao, que o usuario (e a loja dele) continuam ativos, contra o
 * banco - e invalida a sessao HTTP se a resposta for nao. E o mecanismo
 * que cumpre a promessa citada na decisao 0023 ("desligar o acesso do
 * funcionario que saiu"): sem uma tela de gestao de usuario nesta fase,
 * revalidar a cada request e mais simples do que empurrar eventos de
 * desativacao para um {@code SessionRegistry}, e continua funcionando no
 * dia em que aquela tela existir.
 *
 * Nao e um {@code @Component}: e instanciado e registrado manualmente por
 * {@code ConfiguracaoTenant}, com a ordem explicada acima via
 * {@code FilterRegistrationBean}.
 *
 * Sobre o try/catch das excecoes de tenant: este filtro roda ANTES do
 * DispatcherServlet, entao um {@code @RestControllerAdvice}
 * (TratadorGlobalDeErros) nao teria como interceptar uma excecao lancada
 * aqui. Por isso o corpo de erro e escrito diretamente por este filtro,
 * no mesmo formato ErroApi usado pelo resto da API.
 */
public class FiltroTenant extends OncePerRequestFilter {

    // Allowlist, nunca denylist (decisao 0007): so estas rotas dispensam
    // usuario autenticado E tenant. "/api/login" entra aqui porque, no
    // momento em que o login acontece, ainda nao ha Authentication
    // nenhuma no SecurityContext - nao ha tenant para resolver.
    private static final Set<String> ROTAS_ISENTAS = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/api/login");

    // As sub-rotas de health (/actuator/health/liveness e /readiness) sao
    // usadas por probe de container. Sao isentas pelo mesmo motivo que
    // /actuator/health: nao devolvem dado de tenant nenhum. Isento por
    // PREFIXO so aqui, e so para health - a lista acima continua sendo
    // comparacao exata, para que uma rota nova nao vire isenta por
    // acidente de nome parecido.
    private static final String PREFIXO_HEALTH_ISENTO = "/actuator/health/";

    private final RepositorioTenant repositorioTenant;
    private final RepositorioUsuario repositorioUsuario;
    private final ObjectMapper objectMapper;

    public FiltroTenant(RepositorioTenant repositorioTenant, RepositorioUsuario repositorioUsuario,
            ObjectMapper objectMapper) {
        this.repositorioTenant = repositorioTenant;
        this.repositorioUsuario = repositorioUsuario;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String caminho = request.getRequestURI().substring(request.getContextPath().length());

        if (ROTAS_ISENTAS.contains(caminho) || caminho.startsWith(PREFIXO_HEALTH_ISENTO)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            UsuarioAutenticado usuario = resolverUsuarioAutenticado();
            confirmarTenantAtivo(usuario.tenantId());

            ContextoTenant.definir(usuario.tenantId());
            confirmarUsuarioAindaAtivo(usuario.usuarioId());

            chain.doFilter(request, response);

        } catch (TenantNaoResolvidoException erro) {
            escreverErro(response, HttpServletResponse.SC_BAD_REQUEST, "tenant_nao_resolvido", erro.getMessage());
        } catch (TenantDesconhecidoException erro) {
            escreverErro(response, HttpServletResponse.SC_FORBIDDEN, "tenant_desconhecido", erro.getMessage());
        } catch (SessaoInvalidaException erro) {
            invalidarSessao(request);
            escreverErro(response, HttpServletResponse.SC_UNAUTHORIZED, "sessao_invalida", erro.getMessage());
        } finally {
            // SEMPRE limpa, mesmo nos caminhos de erro acima e mesmo se
            // chain.doFilter lancar. O container reutiliza a mesma thread
            // para requisicoes futuras: um tenant esquecido aqui vazaria
            // para o proximo request atendido por essa thread.
            ContextoTenant.limpar();
        }
    }

    private UsuarioAutenticado resolverUsuarioAutenticado() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao == null || !autenticacao.isAuthenticated()
                || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            // Defesa em profundidade: na pratica o Spring Security ja
            // barrou qualquer requisicao nao autenticada para rotas fora
            // da allowlist (authorizeHttpRequests().anyRequest().authenticated(),
            // em ConfiguracaoSeguranca) antes deste filtro sequer rodar -
            // e exatamente o reordenamento descrito no Javadoc da classe.
            // Este ramo so seria alcancado se aquela configuracao fosse
            // quebrada por engano; falhar aqui tambem (fail-closed) evita
            // depender de uma unica camada para todo o isolamento.
            throw new TenantNaoResolvidoException("Nenhum usuario autenticado na requisicao.");
        }

        return usuario;
    }

    private void confirmarTenantAtivo(UUID tenantId) {
        if (!repositorioTenant.existsByIdAndAtivoTrue(tenantId)) {
            // Mensagem deliberadamente generica: nao dizemos se o tenant
            // nao existe ou se existe e esta inativo, para nao dar a
            // quem esta adivinhando IDs um jeito de diferenciar os dois
            // casos (ver TenantDesconhecidoException).
            throw new TenantDesconhecidoException("Tenant desconhecido ou inativo.");
        }
    }

    /**
     * Revalidacao por requisicao (armadilha 6 da V014, ver o Javadoc da
     * classe). PRECISA rodar DEPOIS de {@link ContextoTenant#definir},
     * nunca antes: e o predicado de tenant (@TenantId + RLS) que garante
     * que esta consulta so enxerga o usuario DENTRO do tenant certo.
     */
    private void confirmarUsuarioAindaAtivo(UUID usuarioId) {
        if (!repositorioUsuario.existsByIdAndAtivoTrue(usuarioId)) {
            throw new SessaoInvalidaException("Sessao invalida - faca login novamente.");
        }
    }

    private void invalidarSessao(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
    }

    private void escreverErro(HttpServletResponse response, int status, String codigoErro, String mensagem)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // Sem stacktrace, sem SQL, sem detalhe interno - so o codigo curto
        // e a mensagem que a propria excecao ja carrega, que por
        // construcao nunca inclui esse tipo de informacao.
        objectMapper.writeValue(response.getWriter(), new ErroApi(codigoErro, mensagem));
    }
}
