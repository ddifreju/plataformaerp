package com.plataforma.comum.tenant;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.autenticacao.PapelUsuario;
import com.plataforma.autenticacao.RepositorioUsuario;
import com.plataforma.autenticacao.UsuarioAutenticado;
import com.plataforma.autenticacao.UsuarioParaLogin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Teste da camada 1 da decisao 0007 (FiltroTenant), reescrito pela tarefa
 * 17 (decisao 0023): o tenant agora sai do usuario autenticado, nunca
 * mais de um cabecalho. {@code X-Tenant-Id} foi REMOVIDO - este arquivo
 * nao testa mais nada relacionado a ele, de proposito: "rota isenta
 * continua isenta" e "o filtro nao aceita mais header" sao regras de
 * sessao que dao para testar sem banco (pedido explicito da tarefa 17).
 *
 * DELIBERADAMENTE nao usa {@code @WebMvcTest} nem sobe um contexto
 * Spring: FiltroTenant nao e {@code @Component} (ver o comentario na
 * propria classe), entao um slice test padrao nao o encontraria sem
 * configuracao extra. Chamar o filtro diretamente contra
 * {@link MockHttpServletRequest}/{@link MockHttpServletResponse} e mais
 * simples, mais rapido e testa exatamente a mesma logica que MockMvc
 * testaria.
 *
 * RepositorioTenant/RepositorioUsuario sao mockados com Mockito puro (sem
 * Spring, sem banco): o que importa aqui e o COMPORTAMENTO do filtro
 * diante de cada resposta possivel desses repositorios, nao a query real
 * (isso e coberto pelos testes de isolamento, que outro agente escreve
 * contra Testcontainers).
 */
class FiltroTenantTest {

    private final RepositorioTenant repositorioTenant = mock(RepositorioTenant.class);
    private final RepositorioUsuario repositorioUsuario = mock(RepositorioUsuario.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FiltroTenant filtro = new FiltroTenant(repositorioTenant, repositorioUsuario, objectMapper);

    @AfterEach
    void limparContexto() {
        // Mesmo motivo do @AfterEach em ContextoTenantTest: ThreadLocal
        // estatica compartilhada entre classes de teste na mesma thread.
        // SecurityContextHolder usa o mesmo padrao (ThreadLocal por
        // padrao), entao limpa tambem.
        ContextoTenant.limpar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void semAutenticacaoRetorna400ENaoChamaRepositorios() throws ServletException, java.io.IOException {
        // SecurityContextHolder vazio simula exatamente o que aconteceria
        // se authorizeHttpRequests().anyRequest().authenticated() (a
        // camada de verdade) fosse quebrada por engano - a defesa em
        // profundidade que o Javadoc de FiltroTenant descreve.
        MockHttpServletRequest request = requisicao("/qualquer-rota");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertEquals(400, response.getStatus());
        assertEquals("tenant_nao_resolvido", campoErro(response));
        assertFalse(chain.chamado, "cadeia de filtros nao deveria ter sido chamada sem usuario autenticado");
        verifyNoInteractions(repositorioTenant);
        verifyNoInteractions(repositorioUsuario);
    }

    @Test
    void tenantDoUsuarioDesconhecidoOuInativoRetorna403() throws ServletException, java.io.IOException {
        UsuarioAutenticado usuario = autenticar(UUID.randomUUID(), UUID.randomUUID());
        when(repositorioTenant.existsByIdAndAtivoTrue(usuario.tenantId())).thenReturn(false);

        MockHttpServletRequest request = requisicao("/qualquer-rota");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("tenant_desconhecido", campoErro(response));
        assertFalse(chain.chamado);
        verify(repositorioTenant, times(1)).existsByIdAndAtivoTrue(usuario.tenantId());
        // O tenant estava inativo: o filtro nem chega a revalidar o
        // usuario - falha no primeiro problema encontrado.
        verifyNoInteractions(repositorioUsuario);
    }

    @Test
    void usuarioDesativadoAposLoginRetorna401EInvalidaSessao() throws ServletException, java.io.IOException {
        UsuarioAutenticado usuario = autenticar(UUID.randomUUID(), UUID.randomUUID());
        when(repositorioTenant.existsByIdAndAtivoTrue(usuario.tenantId())).thenReturn(true);
        // Simula a revalidacao por requisicao (armadilha 6 da V014)
        // descobrindo que o usuario foi desativado DEPOIS que a sessao
        // foi aberta - o cenario "desligar o acesso do funcionario que
        // saiu" citado na decisao 0023.
        when(repositorioUsuario.existsByIdAndAtivoTrue(usuario.usuarioId())).thenReturn(false);

        MockHttpServletRequest request = requisicao("/qualquer-rota");
        request.setSession(new org.springframework.mock.web.MockHttpSession());
        Object idDaSessaoAntes = request.getSession().getId();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("sessao_invalida", campoErro(response));
        assertFalse(chain.chamado);
        assertNull(request.getSession(false), "a sessao HTTP precisa ser invalidada quando o usuario nao esta mais ativo");
    }

    @Test
    void actuatorHealthPassaSemAutenticacaoENaoChamaRepositorios() throws ServletException, java.io.IOException {
        MockHttpServletRequest request = requisicao("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertTrue(chain.chamado, "/actuator/health e rota isenta (allowlist) e precisa passar sem usuario autenticado");
        verifyNoInteractions(repositorioTenant);
        verifyNoInteractions(repositorioUsuario);
    }

    @Test
    void rotaDeLoginEIsentaMesmoSemAutenticacao() throws ServletException, java.io.IOException {
        // /api/login precisa estar na allowlist do proprio FiltroTenant
        // (item 5 da tarefa 17): no instante em que o login acontece nao
        // ha Authentication nenhuma no SecurityContext ainda.
        MockHttpServletRequest request = requisicao("/api/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FiltroChainDeTeste chain = new FiltroChainDeTeste();

        filtro.doFilterInternal(request, response, chain);

        assertTrue(chain.chamado, "/api/login e isenta e precisa passar sem usuario autenticado");
        verifyNoInteractions(repositorioTenant);
        verifyNoInteractions(repositorioUsuario);
    }

    @Test
    void usuarioValidoDefineContextoDuranteAChamadaELimpaDepois() throws ServletException, java.io.IOException {
        UsuarioAutenticado usuario = autenticar(UUID.randomUUID(), UUID.randomUUID());
        when(repositorioTenant.existsByIdAndAtivoTrue(usuario.tenantId())).thenReturn(true);
        when(repositorioUsuario.existsByIdAndAtivoTrue(usuario.usuarioId())).thenReturn(true);

        MockHttpServletRequest request = requisicao("/qualquer-rota");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Em vez de um mock generico, este chain CAPTURA o que
        // ContextoTenant.atual() devolve NO MOMENTO em que a cadeia e
        // executada - prova que o tenant estava de fato definido durante
        // o processamento do request, nao so antes/depois.
        AtomicReference<UUID> tenantVistoDentroDaCadeia = new AtomicReference<>();
        FilterChain chain = (req, res) -> tenantVistoDentroDaCadeia.set(ContextoTenant.atual());

        filtro.doFilterInternal(request, response, chain);

        assertEquals(usuario.tenantId(), tenantVistoDentroDaCadeia.get(),
                "o resto do pipeline deveria enxergar o tenant do usuario autenticado");
        assertTrue(ContextoTenant.atualOuVazio().isEmpty(),
                "o finally do filtro precisa limpar o ContextoTenant mesmo no caminho de sucesso, "
                        + "senao o tenant vazaria para a proxima requisicao atendida por esta thread");
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
     * Coloca um {@link UsuarioAutenticado} no {@code SecurityContextHolder},
     * simulando o que a cadeia do Spring Security ja teria feito antes
     * deste filtro rodar (decisao 0023 - "a ordem dos filtros muda").
     */
    private static UsuarioAutenticado autenticar(UUID usuarioId, UUID tenantId) {
        UsuarioParaLogin dados = new UsuarioParaLogin(
                usuarioId, tenantId, "dona@loja.com.br", "hash-nunca-usado-neste-teste",
                "Dona da Loja", PapelUsuario.DONO, true, true);
        UsuarioAutenticado usuario = new UsuarioAutenticado(dados);

        var token = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
        return usuario;
    }

    /**
     * FilterChain de teste simples: so registra se foi chamada. Usado
     * nos casos em que o teste nao precisa inspecionar o ContextoTenant
     * durante a chamada (ver usuarioValidoDefineContextoDuranteAChamadaELimpaDepois
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
