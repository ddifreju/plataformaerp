package com.plataforma.comum.tenant;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.web.ErroApi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Camada 1 da decisao 0007: resolve o tenant da requisicao e o define
 * no {@link ContextoTenant} antes de qualquer controller rodar.
 *
 * PROVISORIO: hoje o tenant vem do cabecalho X-Tenant-Id, sem nenhuma
 * autenticacao por tras. Isso e aceitavel APENAS enquanto nao existe
 * sessao: qualquer chamador pode mandar qualquer X-Tenant-Id e "ser"
 * aquele tenant. Quando a tarefa 17 (autenticacao) existir, o tenant
 * passa a vir do token assinado, e este cabecalho DEIXA DE SER ACEITO -
 * do contrario ele vira um vetor trivial de escalonamento horizontal
 * entre clientes (qualquer um le dado de qualquer tenant so trocando um
 * header).
 *
 * Nao e um @Component: e instanciado e registrado manualmente por
 * ConfiguracaoTenant, com ordem Ordered.HIGHEST_PRECEDENCE via
 * FilterRegistrationBean. E essa configuracao manual que garante a
 * ordem em tempo de execucao. O @Order abaixo documenta a intencao mas
 * nao tem efeito por si so numa instancia criada com "new" - o Spring
 * so usa @Order para ordenar BEANS que ele mesmo gerencia.
 *
 * Sobre o try/catch das excecoes de tenant: este filtro roda ANTES do
 * DispatcherServlet, entao um @RestControllerAdvice (TratadorGlobalDeErros)
 * nao teria como interceptar uma excecao lancada aqui. Por isso o corpo
 * de erro e escrito diretamente por este filtro, no mesmo formato
 * ErroApi usado pelo resto da API.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroTenant extends OncePerRequestFilter {

    private static final String CABECALHO_TENANT = "X-Tenant-Id";

    // Allowlist, nunca denylist (decisao 0007): so estas rotas dispensam
    // tenant. Qualquer rota nova nasce EXIGINDO tenant por padrao - e o
    // comportamento seguro por omissao.
    private static final Set<String> ROTAS_ISENTAS = Set.of(
            "/actuator/health",
            "/actuator/info");

    // As sub-rotas de health (/actuator/health/liveness e /readiness) sao
    // usadas por probe de container. Sao isentas pelo mesmo motivo que
    // /actuator/health: nao devolvem dado de tenant nenhum. Isento por
    // PREFIXO so aqui, e so para health - a lista acima continua sendo
    // comparacao exata, para que uma rota nova nao vire isenta por
    // acidente de nome parecido.
    private static final String PREFIXO_HEALTH_ISENTO = "/actuator/health/";

    private final RepositorioTenant repositorioTenant;
    private final ObjectMapper objectMapper;

    public FiltroTenant(RepositorioTenant repositorioTenant, ObjectMapper objectMapper) {
        this.repositorioTenant = repositorioTenant;
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
            UUID tenantId = resolverTenantIdDoCabecalho(request);
            confirmarTenantAtivo(tenantId);

            ContextoTenant.definir(tenantId);
            chain.doFilter(request, response);

        } catch (TenantNaoResolvidoException erro) {
            escreverErro(response, HttpServletResponse.SC_BAD_REQUEST, "tenant_nao_resolvido", erro.getMessage());
        } catch (TenantDesconhecidoException erro) {
            escreverErro(response, HttpServletResponse.SC_FORBIDDEN, "tenant_desconhecido", erro.getMessage());
        } finally {
            // SEMPRE limpa, mesmo nos caminhos de erro acima e mesmo se
            // chain.doFilter lancar. O container reutiliza a mesma thread
            // para requisicoes futuras: um tenant esquecido aqui vazaria
            // para o proximo request atendido por essa thread.
            ContextoTenant.limpar();
        }
    }

    private UUID resolverTenantIdDoCabecalho(HttpServletRequest request) {
        String valor = request.getHeader(CABECALHO_TENANT);

        if (valor == null || valor.isBlank()) {
            throw new TenantNaoResolvidoException("Cabecalho " + CABECALHO_TENANT + " ausente.");
        }

        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException erroDeFormato) {
            throw new TenantNaoResolvidoException("Cabecalho " + CABECALHO_TENANT + " nao e um UUID valido.");
        }
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
