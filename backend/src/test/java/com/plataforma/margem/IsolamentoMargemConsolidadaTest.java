package com.plataforma.margem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.plataforma.canal.Canal;
import com.plataforma.canal.CategoriaCanal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.suporte.PostgresDeTeste;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Isolamento de tenant COMPORTAMENTAL de
 * {@link RepositorioDisjuncaoDeCanais#verificar} (tarefa 32) - regra 1 do
 * CLAUDE.md ("todo PR que toca query precisa de teste de isolamento"),
 * reforcada aqui porque esta consulta e SQL NATIVO: ela NAO recebe o
 * predicado automatico de {@code @TenantId} (decisao 0007, camada 3) que
 * protege toda consulta JPQL/derivada deste sistema. A UNICA linha de
 * defesa por dentro da propria consulta e o {@code c.tenant_id = ?}
 * explicito do LEFT JOIN - e RLS (camada 2) por baixo dele. Ver o Javadoc
 * de {@link RepositorioDisjuncaoDeCanais} antes de mexer nela.
 *
 * <h2>Molde</h2>
 * Mesma arquitetura de {@link IsolamentoMargemTest} (leia o Javadoc dela
 * primeiro): {@code @SpringBootTest(webEnvironment = NONE)}, fixture
 * montada pelo caminho legitimo ({@link ContextoTenant#definir} em volta
 * de cada {@code save}, exercitando ContextoTenant -&gt;
 * DataSourceComTenant -&gt; RLS -&gt; @TenantId), e
 * {@link PostgresDeTeste#novaConexaoDono()} usado SO para provar que a
 * linha do outro tenant existe de verdade antes de provar que ela nao
 * vaza - nunca para testar o isolamento em si.
 *
 * <h2>O cenario que importa</h2>
 * Consulta no "tenant" de A (bind explicito, ja que este metodo recebe o
 * tenant como PARAMETRO, nunca do {@link ContextoTenant} - ver o Javadoc
 * do metodo), pedindo o {@code canalId} REAL de B, que E declarado
 * FONTE_PRIMARIA de verdade (nao um UUID que "nao existe por sorte" - se
 * fosse assim, o teste nao provaria isolamento nenhum, so a obviedade de
 * que um UUID aleatorio nao bate com nada). Resultado esperado:
 * {@link MotivoBloqueioDeSoma#CANAL_INEXISTENTE} - o MESMO motivo que um
 * canal que nunca existiu produziria, e {@code codigo}/{@code espelhaCanalId}
 * NULOS - nunca o codigo ou o estado real do canal de B.
 *
 * <h2>Como verificar que este teste nao e decorativo</h2>
 * Troque o {@code LEFT JOIN canal c} da consulta em
 * {@link RepositorioDisjuncaoDeCanais} por {@code INNER JOIN}. Efeito:
 * {@link #canalDeOutroTenantVemComoCanalInexistenteSemVazarCodigoOuEspelho()}
 * fica vermelho de um jeito enganoso ao primeiro olhar - o canal de B
 * SOME da lista de resultado (em vez de aparecer com motivo
 * CANAL_INEXISTENTE), e a asserção de tamanho da lista (2) falha. Ou
 * seja: com INNER JOIN, o id de B nem aparece no relatorio de bloqueio -
 * exatamente o "disjunto por omissao" que o Javadoc da classe alerta
 * como o pior desfecho possivel, porque QUEM CHAMA {@link ServicoMargemPeriodoConsolidada}
 * decidiria, ao ver so uma linha sem bloqueio, que o conjunto e seguro.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IsolamentoMargemConsolidadaTest {

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.configurarPropriedades(registro);
    }

    @Autowired
    private RepositorioCanal repositorioCanal;

    @Autowired
    private RepositorioDisjuncaoDeCanais repositorioDisjuncaoDeCanais;

    @AfterEach
    void limparContexto() {
        ContextoTenant.limpar();
    }

    @Test
    void canalDeOutroTenantVemComoCanalInexistenteSemVazarCodigoOuEspelho() throws SQLException {
        UUID tenantA = criarTenant("consolidada-a");
        UUID tenantB = criarTenant("consolidada-b");
        UUID canalA = criarCanalFontePrimariaComoTenant(tenantA, "canal-consolidada-a");
        UUID canalB = criarCanalFontePrimariaComoTenant(tenantB, "canal-consolidada-b");

        // Prova que ha o que vazar ANTES de provar que nao vazou (mesmo
        // racional de IsolamentoMargemTest).
        assertEquals(1, contarComoPrivilegiado(tenantB, canalB),
                "setup falhou: canal de B nao foi gravado de verdade");

        // ContextoTenant.definir(tenantA) ANTES de chamar verificar():
        // igual a producao, onde ServicoMargemPeriodoConsolidada SEMPRE
        // roda dentro de uma requisicao que o FiltroTenant ja tenant-
        // scopou. Sem isto, DataSourceComTenant setaria o GUC
        // app.tenant_id como VAZIO na conexao, e a policy RLS de canal
        // (camada 2, USING tenant_id = current_setting(...)) esconderia
        // TODAS as linhas de canal da conexao - inclusive a do proprio
        // tenantA - fazendo ate canalA aparecer como CANAL_INEXISTENTE.
        // Isso NAO seria um bug da consulta: seria o teste chamando o
        // repositorio fora do contexto em que ele sempre roda de verdade.
        ContextoTenant.definir(tenantA);
        List<LinhaDisjuncaoCanal> linhas;
        try {
            linhas = repositorioDisjuncaoDeCanais.verificar(tenantA, List.of(canalA, canalB));
        } finally {
            ContextoTenant.limpar();
        }

        assertEquals(2, linhas.size(), "uma linha por canalId pedido, mesmo o de outro tenant (LEFT JOIN)");

        LinhaDisjuncaoCanal linhaA = linhaDoCanal(linhas, canalA);
        assertNull(linhaA.motivoDoBloqueio(), "canalA e FONTE_PRIMARIA no proprio tenant - nao deveria bloquear");
        assertNotNull(linhaA.codigo(), "canal do PROPRIO tenant deveria vir com o codigo preenchido");

        LinhaDisjuncaoCanal linhaB = linhaDoCanal(linhas, canalB);
        assertEquals(MotivoBloqueioDeSoma.CANAL_INEXISTENTE, linhaB.motivoDoBloqueio(),
                "VAZAMENTO: canal de outro tenant deveria aparecer como CANAL_INEXISTENTE, nunca com o escopo real");
        assertNull(linhaB.codigo(), "VAZAMENTO: o codigo do canal de outro tenant nao pode aparecer");
        assertNull(linhaB.espelhaCanalId(), "VAZAMENTO: o espelhaCanalId do canal de outro tenant nao pode aparecer");
    }

    @Test
    void canalIdInexistenteDeVerdadeTambemVemComoCanalInexistente() {
        UUID tenantA = criarTenant("consolidada-inexistente-a");
        UUID canalFantasma = UUID.randomUUID();

        ContextoTenant.definir(tenantA);
        List<LinhaDisjuncaoCanal> linhas;
        try {
            linhas = repositorioDisjuncaoDeCanais.verificar(tenantA, List.of(canalFantasma));
        } finally {
            ContextoTenant.limpar();
        }

        assertEquals(1, linhas.size());
        assertEquals(MotivoBloqueioDeSoma.CANAL_INEXISTENTE, linhas.get(0).motivoDoBloqueio(),
                "mesmo motivo de canal de outro tenant - decisao deliberada (nao dar pista de qual dos dois e)");
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private UUID criarTenant(String rotulo) {
        UUID id = UUID.randomUUID();
        String slug = "tenant-" + rotulo + "-" + id.toString().substring(0, 8);
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO tenant (id, nome, slug) VALUES (?, ?, ?)")) {
            comando.setObject(1, id);
            comando.setString(2, "Tenant de teste " + rotulo);
            comando.setString(3, slug);
            comando.executeUpdate();
        } catch (SQLException erro) {
            throw new IllegalStateException("falha ao criar tenant de teste", erro);
        }
        return id;
    }

    private UUID criarCanalFontePrimariaComoTenant(UUID tenantId, String marcador) {
        ContextoTenant.definir(tenantId);
        try {
            Canal canal = new Canal("canal-" + UUID.randomUUID().toString().substring(0, 8),
                    marcador, TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, null, null, null);
            canal.declararFontePrimaria(null);
            return repositorioCanal.save(canal).getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private long contarComoPrivilegiado(UUID tenantId, UUID canalId) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM canal WHERE tenant_id = ? AND id = ? AND escopo_declarado = 'FONTE_PRIMARIA'")) {
            comando.setObject(1, tenantId);
            comando.setObject(2, canalId);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }

    private static LinhaDisjuncaoCanal linhaDoCanal(List<LinhaDisjuncaoCanal> linhas, UUID canalId) {
        return linhas.stream()
                .filter(linha -> linha.canalId().equals(canalId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("linha de " + canalId + " nao encontrada no resultado"));
    }
}
