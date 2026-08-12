package com.plataforma.comum.tenant;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Teste da camada 1 da decisao 0007 (FiltroTenant): resolucao do tenant
 * a partir do cabecalho X-Tenant-Id, sem tocar banco de verdade.
 *
 * DELIBERADAMENTE nao usa {@code @WebMvcTest} nem sobe um contexto
 * Spring: FiltroTenant nao e {@code @Component} (e registrado a mao em
 * ConfiguracaoTenant, ver o comentario la sobre o porque), entao um
 * slice test padrao nao o encontraria sem configuracao extra. Como
 * {@code doFilterInternal} e {@code protected} e esta classe esta no
 * MESMO pacote de FiltroTenant, o acesso e legitimo em Java sem
 * subclasse nem reflection. Chamar o filtro diretamente contra
 * {@link MockHttpServletRequest}/{@link MockHttpServletResponse} e mais
 * simples, mais rapido e testa exatamente a mesma logica que MockMvc
 * testaria — sem a complexidade extra de simular um DispatcherServlet
 * para um filtro que roda ANTES dele.
 *
 * RepositorioTenant e mockado com Mockito puro (sem Spring): esta classe
 * so estende JpaRepository, e o que importa aqui e o comportamento de
 * existsByIdAndAtivoTrue, nao a query real (isso e coberto indiretamente
 * pelos testes de isolamento, que usam o repositorio de verdade).
 */
class FiltroTenantTest {

    private static final String CABECALHO_TENANT = "X-Tenant-Id";

    private final RepositorioTenant repositorioTenant = mock(RepositorioTenant.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FiltroTenant filtro = new FiltroTenant(repositorioTenant, objectMapper);

    @AfterEach
    void limparContexto() {
        // Mesmo motivo do @AfterEach em ContextoTenantTest e
        // IsolamentoDeTenantTest: ThreadLocal estatica compartilhada
        // entre classes de teste na mesma thread.
        ContextoTenant.limpar();
    }

    @Test
    void semCabecalhoRetorna400ENaoChamaRepositorio() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = requisicao("/qualquer-rota");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertEquals(400, response.getStatus());
        assertEquals("tenant_nao_resolvido", campoErro(response));
        assertFalse(chain.chamado, "cadeia de filtros nao deveria ter sido chamada sem tenant resolvido");
        verifyNoInteractions(repositorioTenant);
    }

    @Test
    void cabecalhoMalformadoRetorna400ENaoChamaRepositorio() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = requisicao("/qualquer-rota");
        request.addHeader(CABECALHO_TENANT, "isto-nao-e-um-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertEquals(400, response.getStatus());
        assertEquals("tenant_nao_resolvido", campoErro(response));
        assertFalse(chain.chamado);
        // O parse do UUID falha antes de qualquer consulta ao catalogo
        // de tenants - o repositorio nem deveria ser tocado.
        verify(repositorioTenant, never()).existsByIdAndAtivoTrue(any());
    }

    @Test
    void tenantDesconhecidoOuInativoRetorna403() throws ServletException, java.io.IOException {
        UUID tenantId = UUID.randomUUID();
        when(repositorioTenant.existsByIdAndAtivoTrue(tenantId)).thenReturn(false);

        MockHttpServletRequest request = requisicao("/qualquer-rota");
        request.addHeader(CABECALHO_TENANT, tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("tenant_desconhecido", campoErro(response));
        assertFalse(chain.chamado);
        verify(repositorioTenant, times(1)).existsByIdAndAtivoTrue(tenantId);
    }

    @Test
    void actuatorHealthPassaSemCabecalhoENaoChamaRepositorio() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = requisicao("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertTrue(chain.chamado, "/actuator/health e rota isenta (allowlist) e precisa passar sem tenant");
        verifyNoInteractions(repositorioTenant);
    }

    @Test
    void tenantValidoDefineContextoDuranteAChamadaELimpaDepois() throws ServletException, java.io.IOException {
        UUID tenantId = UUID.randomUUID();
        when(repositorioTenant.existsByIdAndAtivoTrue(tenantId)).thenReturn(true);

        MockHttpServletRequest request = requisicao("/qualquer-rota");
        request.addHeader(CABECALHO_TENANT, tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Em vez de um mock generico, este chain CAPTURA o que
        // ContextoTenant.atual() devolve NO MOMENTO em que a cadeia e
        // executada - prova que o tenant estava de fato definido durante
        // o processamento do request, nao so antes/depois.
        AtomicReference<UUID> tenantVistoDentroDaCadeia = new AtomicReference<>();
        FilterChain chain = (req, res) -> tenantVistoDentroDaCadeia.set(ContextoTenant.atual());

        filtro.doFilterInternal(request, response, chain);

        assertEquals(tenantId, tenantVistoDentroDaCadeia.get(),
                "o resto do pipeline deveria enxergar o tenant resolvido pelo filtro");
        assertTrue(ContextoTenant.atualOuVazio().isEmpty(),
                "o finally do filtro precisa limpar o ContextoTenant mesmo no caminho de sucesso, "
                        + "senao o tenant vazaria para a proxima requisicao atendida pela mesma thread");
    }

    @Test
    void corpoDeErroNaoContemStacktraceNemSql() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = requisicao("/qualquer-rota");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        String corpo = response.getContentAsString();
        JsonNode json = objectMapper.readTree(corpo);

        // Formato estavel e minimo: so "erro" e "mensagem" (ver ErroApi).
        assertEquals(2, contarCampos(json));
        assertTrue(json.has("erro"));
        assertTrue(json.has("mensagem"));

        String corpoEmMinusculas = corpo.toLowerCase();
        assertFalse(corpoEmMinusculas.contains("stacktrace"));
        assertFalse(corpoEmMinusculas.contains(".java:"));
        assertFalse(corpoEmMinusculas.contains("exception"));
        assertFalse(corpoEmMinusculas.contains("select "));
        assertFalse(corpoEmMinusculas.contains("com.plataforma"));
    }

    private static int contarCampos(JsonNode json) {
        int total = 0;
        var it = json.fieldNames();
        while (it.hasNext()) {
            it.next();
            total++;
        }
        return total;
    }

    private static MockHttpServletRequest requisicao(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        // contextPath default do MockHttpServletRequest ja e "" - deixado
        // explicito aqui porque FiltroTenant.doFilterInternal calcula o
        // caminho isento como requestURI.substring(contextPath.length()).
        request.setContextPath("");
        return request;
    }

    private String campoErro(MockHttpServletResponse response) throws java.io.IOException {
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        JsonNode erro = json.get("erro");
        assertNotNull(erro, "campo 'erro' ausente no corpo: " + response.getContentAsString());
        return erro.asText();
    }

    /**
     * FilterChain de teste simples: so registra se foi chamada. Usado
     * nos casos em que o teste nao precisa inspecionar o ContextoTenant
     * durante a chamada (ver tenantValidoDefineContextoDuranteAChamadaELimpaDepois
     * para o caso que precisa).
     */
    private static final class FiltroChainDeTeste implements FilterChain {
        boolean chamado = false;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            chamado = true;
        }
    }
}
