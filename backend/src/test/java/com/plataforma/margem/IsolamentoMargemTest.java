package com.plataforma.margem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.plataforma.canal.Canal;
import com.plataforma.canal.CategoriaCanal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.custo.RepositorioCusto;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.RepositorioPedido;
import com.plataforma.pedido.StatusPedido;
import com.plataforma.suporte.PostgresDeTeste;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolamento de tenant COMPORTAMENTAL nas tres consultas novas do motor de
 * margem (achado ALTO da auditoria de seguranca da Fase 2 - regra 1 do
 * CLAUDE.md: "todo PR que toca query precisa de teste de isolamento").
 * {@link com.plataforma.comum.tenant.RlsAtivoEmTodasAsTabelasTest} ja cobre
 * {@code taxa_canal} ESTRUTURALMENTE (RLS ligado, FORCE ligado, quatro
 * policies existem, por descoberta generica de catalogo) - isso NAO e
 * refeito aqui. O que faltava, e este arquivo fecha, e provar o
 * COMPORTAMENTO das tres consultas novas contra dado real: mesma diferenca
 * entre "a moldura existe" e "a moldura funciona" que motivou
 * {@link com.plataforma.comum.tenant.IsolamentoFaseUmTest} (leia o Javadoc
 * de la primeiro - mesma arquitetura, nao repito aqui).
 *
 * <h2>As tres consultas cobertas</h2>
 * <ul>
 *   <li>{@link RepositorioTaxaCanal#buscarCandidatas} - JPQL (nao SQL
 *       nativo) exatamente para manter o predicado automatico de
 *       {@code @TenantId} (decisao 0007, camada 3) - ver o Javadoc da
 *       propria interface antes de mexer nela.</li>
 *   <li>{@link RepositorioPedido#findByCanalIdAndFeitoEmGreaterThanEqualAndFeitoEmLessThan}
 *       - metodo derivado do Spring Data, mesma protecao automatica.</li>
 *   <li>{@link RepositorioCusto#buscarCustoDePeriodoDoCanal} - idem
 *       {@code buscarCandidatas}, JPQL com {@code @Query}.</li>
 * </ul>
 *
 * <h2>DIFERENCA DELIBERADA em relacao a IsolamentoFaseUmTest: aqui e via
 * repositorio JPA, nao SQL cru</h2>
 * IsolamentoFaseUmTest exercita RLS diretamente com {@code PreparedStatement}
 * cru contra o {@code DataSource} do contexto Spring. Aqui o alvo declarado
 * da tarefa e o COMPORTAMENTO DO METODO DE REPOSITORIO em si (a assinatura
 * que {@code SelecaoTaxaCanal}, {@code ServicoMargemPeriodo} e
 * {@code ResolvedorCustoPedido} realmente chamam) - por isso os tres
 * repositorios (mais {@code RepositorioCanal}, so para montar a FK composta
 * exigida por {@code canal_id}) sao {@code @Autowired} e chamados
 * diretamente, do mesmo jeito que {@code ServicoIngestaoTest} ja faz para
 * {@code RepositorioPedido}/{@code RepositorioCanal}. A conexao usada por
 * tras continua sendo a mesma pilha de producao inteira
 * (FiltroTenant -&gt; ContextoTenant -&gt; DataSourceComTenant -&gt; RLS),
 * simulada aqui via {@link ContextoTenant#definir}.
 * {@link PostgresDeTeste#novaConexaoDono()} so aparece para provar, "por
 * fora", que a linha do outro tenant existe de verdade antes de provar que
 * ela nao vaza - nunca para testar se o isolamento funciona (mesma regra de
 * PostgresDeTeste e de IsolamentoFaseUmTest).
 *
 * <h2>O cenario que o auditor destacou como o mais perigoso</h2>
 * Para as tres consultas, alem do caso "cada tenant so ve o proprio dado",
 * ha um segundo teste especifico: A consulta no contexto do tenant A
 * passando o {@code canalId} DE B. O resultado esperado e SEMPRE lista
 * vazia - nunca excecao, nunca o dado de B - porque o predicado de tenant
 * (camada 3 via {@code @TenantId}, mais camada 4 via RLS) e aplicado
 * INDEPENDENTEMENTE do valor de {@code canalId} passado como parametro; o
 * {@code canal_id} de B so faz a consulta nao encontrar NADA no contexto de
 * A, nunca encontrar a linha de B. Este e o cenario mais importante deste
 * arquivo pelo motivo explicado na proxima secao.
 *
 * <h2>Por que o cenario do "canalId de B" e o unico que prova alguma coisa
 * aqui, e o cenario "cada um ve so o proprio canal" NAO prova nada sozinho</h2>
 * {@code canal_id} e um UUID global (gerado por {@code gen_random_uuid()},
 * decisao 0015) e a FK composta {@code fk_taxa_canal_canal} /
 * {@code fk_pedido_canal} / {@code fk_custo_canal} garante que o
 * {@code canal_id} de A e o de B NUNCA colidem. Isso significa que, no
 * cenario "A consulta com o PROPRIO {@code canalId}", o filtro
 * {@code canal_id = :canalId} das tres consultas JA segrega os dados
 * corretamente MESMO SE o predicado de tenant estivesse completamente
 * ausente (@TenantId removido, RLS desligado, os dois) - o teste passaria
 * do mesmo jeito, por um motivo que nao tem nada a ver com isolamento de
 * tenant. Por isso esse cenario, sozinho, seria decorativo. O cenario
 * "canalId de B" e diferente: ele testa exatamente o caso em que o
 * {@code canal_id} passado JA e valido e JA aponta para uma linha real -
 * e so o predicado de tenant pode impedir que ela apareca. E o UNICO dos
 * dois cenarios que de fato exercita a defesa de tenant. Os dois sao
 * mantidos no arquivo mesmo assim: o primeiro documenta o comportamento
 * esperado do dia a dia (o motor de margem so pergunta pelo proprio canal),
 * o segundo e a prova de isolamento propriamente dita.
 *
 * <h2>Como verificar que este teste nao e decorativo</h2>
 * Tres sabotagens concretas. Leia as duas primeiras juntas: elas contam
 * uma historia so, e separa-las sem o contexto uma da outra seria
 * enganoso.
 * <ol>
 *   <li><b>Converter {@link RepositorioTaxaCanal#buscarCandidatas} para
 *       {@code @Query(value = "...", nativeQuery = true)}</b>, mantendo o
 *       MESMO texto de consulta (que, sendo JPQL sem parametro de tenant,
 *       depende inteiramente do predicado automatico de {@code @TenantId}
 *       para filtrar por tenant). Isto e o que o auditor apontou como o
 *       risco mais silencioso: o predicado de tenant desaparece sem
 *       nenhuma mudanca visivel na consulta em si.
 *       <br><br>
 *       <b>EFEITO HONESTO, e nao o que se esperaria a primeira vista:</b>
 *       como este arquivo testa o COMPORTAMENTO fim-a-fim contra
 *       {@code app_aplicacao} (que NAO e superusuario, NAO tem
 *       {@code BYPASSRLS}, e {@code taxa_canal} tem
 *       {@code FORCE ROW LEVEL SECURITY} - V013), o GUC
 *       {@code app.tenant_id} continua sendo setado por
 *       {@code DataSourceComTenant} em TODA conexao entregue pelo pool,
 *       independente da consulta rodar como JPQL ou SQL nativo (RLS e
 *       decidido pelo Postgres, nao pelo Hibernate). Ou seja: esta
 *       sabotagem ISOLADA muito provavelmente NAO deixa nenhum teste deste
 *       arquivo vermelho - a camada 2 (RLS, decisao 0007) continua
 *       segurando sozinha. Isso NAO e uma falha deste teste: e a camada 2
 *       fazendo exatamente o que a decisao 0007 promete (defesa em
 *       profundidade). O perigo real desta sabotagem e justamente ficar
 *       invisivel: ela remove a camada 1 sem deixar rastro em NENHUM teste
 *       ate que a camada 2 tambem falhe - e e disso que trata a proxima
 *       sabotagem.</li>
 *   <li><b>Trocar o {@code USING} de {@code taxa_canal_select} (V013) para
 *       {@code USING (true)}</b> - com ou sem a sabotagem 1 acima, o
 *       resultado e o mesmo, porque esta e a que efetivamente derruba a
 *       ULTIMA linha de defesa. Efeito esperado:
 *       {@link #buscarCandidatasComCanalIdDeOutroTenantDevolveListaVazia()}
 *       fica vermelho - a consulta no contexto de A, com o {@code canalId}
 *       real de B, passa a encontrar e devolver a linha de B (a asserção
 *       {@code assertTrue(resultado.isEmpty())} falha, e a verificacao
 *       inline de {@code tenantId} no outro teste dispararia se a linha
 *       aparecesse la). {@link #buscarCandidatasNaoVeTaxaDeOutroTenant()}
 *       (cenario "canal proprio") continua VERDE mesmo com esta sabotagem -
 *       exatamente pelo motivo explicado na secao acima: {@code canal_id}
 *       ja segrega os dados nesse cenario, com ou sem RLS. Combine as duas
 *       sabotagens (1 e 2) para ver a cadeia completa: camada 1 removida
 *       silenciosamente (nenhum teste acusa), camada 2 removida em seguida
 *       (agora sim, {@code buscarCandidatasComCanalIdDeOutroTenantDevolveListaVazia}
 *       acusa) - e o cenario exato que a defesa em profundidade existe
 *       para tornar improvavel, nao impossivel.</li>
 *   <li><b>Trocar o {@code USING} de {@code pedido_select} (V008) para
 *       {@code USING (true)}.</b> Efeito esperado:
 *       {@link #buscaPorCanalEPeriodoComCanalIdDeOutroTenantDevolveListaVazia()}
 *       fica vermelho, pelo mesmo mecanismo da sabotagem 2 - a consulta com
 *       {@code canalId} real de B, no contexto de A, passa a devolver o
 *       pedido de B. Prova que o padrao "canalId de B" nao e peculiaridade
 *       de {@code taxa_canal}: vale para qualquer consulta que filtre por
 *       {@code canal_id} sem verificar de quem e o canal antes.
 *       {@code RepositorioCusto.buscarCustoDePeriodoDoCanal} nao tem uma
 *       sabotagem descrita a parte porque o mecanismo e identico
 *       ({@code custo_select} - V010) e repetir a explicacao nao ensinaria
 *       nada novo; o teste equivalente para {@code custo}
 *       ({@link #buscarCustoDePeriodoDoCanalComCanalIdDeOutroTenantDevolveListaVazia()})
 *       reagiria da mesma forma a mesma sabotagem em {@code custo_select}.</li>
 * </ol>
 *
 * <h2>RESSALVA HONESTA sobre o que este arquivo NAO prova</h2>
 * <ul>
 *   <li>Nao prova que o predicado de {@code @TenantId} (camada 1/3)
 *       funciona isoladamente, SEM RLS por baixo - as duas camadas so sao
 *       distinguiveis testando com RLS desligado de proposito, o que este
 *       arquivo nao faz (fazer isso trocaria o alvo do teste: deixaria de
 *       testar o comportamento de producao, que roda com RLS sempre
 *       ligado, para testar uma configuracao que nunca existe fora de um
 *       teste). A secao "Como verificar" acima documenta essa limitacao em
 *       detalhe: e possivel remover a camada 1 inteira sem que UM SO teste
 *       deste arquivo acuse, e isso e esperado, nao uma lacuna escondida.</li>
 *   <li>Nao cobre INSERT/UPDATE com {@code tenant_id} forjado para as tres
 *       tabelas (o padrao {@code *InsertComTenantAlheioERejeitado} /
 *       {@code *UpdateNaoConsegueMoverLinhaEntreTenants} de
 *       IsolamentoFaseUmTest) - o achado da auditoria que motivou esta
 *       tarefa era especificamente sobre as tres CONSULTAS novas, nao sobre
 *       escrita. {@code taxa_canal_insert}/{@code pedido_insert}/
 *       {@code custo_insert} sao policies genericas do mesmo molde
 *       (decisao 0010) das ja exercitadas em IsolamentoFaseUmTest; nao
 *       repetido aqui.</li>
 *   <li>Nao cobre {@link RepositorioTaxaCanal#contarTaxasMaisEspecificasQueCuringa}
 *       (a "guarda do curinga") - metodo auxiliar de leitura com o MESMO
 *       predicado automatico de {@code @TenantId} que {@code buscarCandidatas},
 *       fora do escopo que a tarefa definiu como "as tres queries novas".</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IsolamentoMargemTest {

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.configurarPropriedades(registro);
    }

    @Autowired
    private RepositorioTaxaCanal repositorioTaxaCanal;

    @Autowired
    private RepositorioPedido repositorioPedido;

    @Autowired
    private RepositorioCusto repositorioCusto;

    @Autowired
    private RepositorioCanal repositorioCanal;

    @AfterEach
    void limparContexto() {
        // Mesma defesa contra vazamento de tenant ENTRE TESTES usada em
        // IsolamentoFaseUmTest/ServicoIngestaoTest.
        ContextoTenant.limpar();
    }

    // ==================================================================
    // RepositorioTaxaCanal.buscarCandidatas
    // ==================================================================

    @Test
    void buscarCandidatasNaoVeTaxaDeOutroTenant() throws SQLException {
        UUID tenantA = criarTenant("margem-taxa-a");
        UUID tenantB = criarTenant("margem-taxa-b");
        UUID canalA = criarCanalComoTenant(tenantA, "canal-taxa-a");
        UUID canalB = criarCanalComoTenant(tenantB, "canal-taxa-b");

        OffsetDateTime momento = OffsetDateTime.now();
        UUID idTaxaA = inserirTaxaComoTenant(tenantA, canalA, momento.minusDays(30), new BigDecimal("0.130000"));
        UUID idTaxaB = inserirTaxaComoTenant(tenantB, canalB, momento.minusDays(30), new BigDecimal("0.170000"));

        // Prova que ha o que vazar ANTES de provar que nao vazou (mesmo
        // racional de IsolamentoFaseUmTest#canalNaoVeDadoAlheio).
        assertEquals(1, contarComoPrivilegiado("taxa_canal", tenantB, idTaxaB),
                "setup falhou: taxa de B nao foi gravada de verdade");

        ContextoTenant.definir(tenantA);
        List<TaxaCanal> candidatas;
        try {
            candidatas = repositorioTaxaCanal.buscarCandidatas(canalA, TipoTaxaCanal.COMISSAO, momento,
                    TaxaCanal.CURINGA, TaxaCanal.CURINGA, new BigDecimal("100.0000"));
        } finally {
            ContextoTenant.limpar();
        }

        assertEquals(1, candidatas.size(),
                "no contexto de A, buscarCandidatas para o proprio canal deveria devolver EXATAMENTE a taxa de A");
        TaxaCanal encontrada = candidatas.get(0);
        assertEquals(idTaxaA, encontrada.getId());
        assertEquals(tenantA, encontrada.getTenantId(),
                "VAZAMENTO: a linha devolvida no contexto de A pertence a outro tenant");
        // BigDecimal: compareTo, nunca equals (escalas diferentes seriam
        // "iguais" numericamente mas equals() as trataria como distintas).
        assertTrue(new BigDecimal("0.130000").compareTo(encontrada.getPercentual()) == 0,
                "percentual devolvido deveria ser exatamente o de A (0,13), nao o de B (0,17)");
    }

    /**
     * O cenario que o auditor apontou como o mais perigoso: consulta no
     * contexto de A, mas com o {@code canalId} REAL de B. Isto e o que
     * de fato prova que o predicado de tenant e aplicado ANTES do filtro
     * por canal - ver a secao do Javadoc da classe sobre por que este
     * cenario, e nao o anterior, e quem prova isolamento aqui. Resultado
     * esperado: lista vazia. Nunca excecao. Nunca a taxa de B.
     */
    @Test
    void buscarCandidatasComCanalIdDeOutroTenantDevolveListaVazia() throws SQLException {
        UUID tenantA = criarTenant("margem-taxa-canal-alheio-a");
        UUID tenantB = criarTenant("margem-taxa-canal-alheio-b");
        UUID canalB = criarCanalComoTenant(tenantB, "canal-taxa-alheio-b");

        OffsetDateTime momento = OffsetDateTime.now();
        UUID idTaxaB = inserirTaxaComoTenant(tenantB, canalB, momento.minusDays(30), new BigDecimal("0.190000"));

        assertEquals(1, contarComoPrivilegiado("taxa_canal", tenantB, idTaxaB),
                "setup falhou: taxa de B nao foi gravada de verdade");

        ContextoTenant.definir(tenantA);
        List<TaxaCanal> candidatas;
        try {
            // canalB e um canal_id REAL e VALIDO - de proposito: se a busca
            // devolvesse vazio so porque canalB "nao existe", nao provaria
            // nada sobre isolamento de tenant. Ela devolve vazio porque o
            // predicado de tenant (tenant_id = A) e aplicado independente
            // do canal_id pedido, e nenhuma linha de taxa_canal tem
            // tenant_id = A E canal_id = canalB ao mesmo tempo.
            candidatas = repositorioTaxaCanal.buscarCandidatas(canalB, TipoTaxaCanal.COMISSAO, momento,
                    TaxaCanal.CURINGA, TaxaCanal.CURINGA, new BigDecimal("100.0000"));
        } finally {
            ContextoTenant.limpar();
        }

        assertTrue(candidatas.isEmpty(),
                "VAZAMENTO: consulta no contexto de A com canalId de B deveria devolver lista vazia, "
                        + "nao a taxa de B");
    }

    // ==================================================================
    // RepositorioPedido.findByCanalIdAndFeitoEmGreaterThanEqualAndFeitoEmLessThan
    // ==================================================================

    @Test
    void buscaPorCanalEPeriodoNaoVeDadoDeOutroTenant() throws SQLException {
        UUID tenantA = criarTenant("margem-pedido-a");
        UUID tenantB = criarTenant("margem-pedido-b");
        UUID canalA = criarCanalComoTenant(tenantA, "canal-pedido-a");
        UUID canalB = criarCanalComoTenant(tenantB, "canal-pedido-b");

        OffsetDateTime inicio = OffsetDateTime.now().minusDays(10);
        OffsetDateTime fim = OffsetDateTime.now().plusDays(10);
        OffsetDateTime feitoEm = OffsetDateTime.now();

        UUID idPedidoA = inserirPedidoComoTenant(tenantA, canalA, feitoEm, new BigDecimal("199.9000"));
        UUID idPedidoB = inserirPedidoComoTenant(tenantB, canalB, feitoEm, new BigDecimal("299.9000"));

        assertEquals(1, contarComoPrivilegiado("pedido", tenantB, idPedidoB),
                "setup falhou: pedido de B nao foi gravado de verdade");

        ContextoTenant.definir(tenantA);
        List<Pedido> encontrados;
        try {
            encontrados = repositorioPedido.findByCanalIdAndFeitoEmGreaterThanEqualAndFeitoEmLessThan(
                    canalA, inicio, fim);
        } finally {
            ContextoTenant.limpar();
        }

        assertEquals(1, encontrados.size(),
                "no contexto de A, a busca por canal+periodo deveria devolver EXATAMENTE o pedido de A");
        Pedido encontrado = encontrados.get(0);
        assertEquals(idPedidoA, encontrado.getId());
        assertEquals(tenantA, encontrado.getTenantId(),
                "VAZAMENTO: o pedido devolvido no contexto de A pertence a outro tenant");
        assertTrue(new BigDecimal("199.9000").compareTo(encontrado.getValorTotalPedido()) == 0,
                "valor devolvido deveria ser exatamente o de A, nao o de B");
    }

    /**
     * Mesmo cenario perigoso de {@link #buscarCandidatasComCanalIdDeOutroTenantDevolveListaVazia()},
     * agora para pedido: contexto de A, {@code canalId} REAL de B.
     */
    @Test
    void buscaPorCanalEPeriodoComCanalIdDeOutroTenantDevolveListaVazia() throws SQLException {
        UUID tenantA = criarTenant("margem-pedido-canal-alheio-a");
        UUID tenantB = criarTenant("margem-pedido-canal-alheio-b");
        UUID canalB = criarCanalComoTenant(tenantB, "canal-pedido-alheio-b");

        OffsetDateTime inicio = OffsetDateTime.now().minusDays(10);
        OffsetDateTime fim = OffsetDateTime.now().plusDays(10);
        OffsetDateTime feitoEm = OffsetDateTime.now();

        UUID idPedidoB = inserirPedidoComoTenant(tenantB, canalB, feitoEm, new BigDecimal("399.9000"));

        assertEquals(1, contarComoPrivilegiado("pedido", tenantB, idPedidoB),
                "setup falhou: pedido de B nao foi gravado de verdade");

        ContextoTenant.definir(tenantA);
        List<Pedido> encontrados;
        try {
            // canalB e um canal_id REAL e VALIDO de B - mesmo racional da
            // taxa: prova que o predicado de tenant, nao a "sorte" de o
            // canal nao existir, e quem impede o vazamento.
            encontrados = repositorioPedido.findByCanalIdAndFeitoEmGreaterThanEqualAndFeitoEmLessThan(
                    canalB, inicio, fim);
        } finally {
            ContextoTenant.limpar();
        }

        assertTrue(encontrados.isEmpty(),
                "VAZAMENTO: consulta no contexto de A com canalId de B deveria devolver lista vazia, "
                        + "nao o pedido de B");
    }

    // ==================================================================
    // RepositorioCusto.buscarCustoDePeriodoDoCanal
    // ==================================================================

    @Test
    void buscarCustoDePeriodoDoCanalNaoVeDadoDeOutroTenant() throws SQLException {
        UUID tenantA = criarTenant("margem-custo-a");
        UUID tenantB = criarTenant("margem-custo-b");
        UUID canalA = criarCanalComoTenant(tenantA, "canal-custo-a");
        UUID canalB = criarCanalComoTenant(tenantB, "canal-custo-b");

        OffsetDateTime inicio = OffsetDateTime.now().minusDays(5);
        OffsetDateTime fim = OffsetDateTime.now().plusDays(5);
        OffsetDateTime competenciaEm = OffsetDateTime.now();

        UUID idCustoA = inserirCustoDePeriodoComoTenant(tenantA, canalA, competenciaEm, new BigDecimal("49.9000"));
        UUID idCustoB = inserirCustoDePeriodoComoTenant(tenantB, canalB, competenciaEm, new BigDecimal("89.9000"));

        assertEquals(1, contarComoPrivilegiado("custo", tenantB, idCustoB),
                "setup falhou: custo de B nao foi gravado de verdade");

        ContextoTenant.definir(tenantA);
        List<Custo> encontrados;
        try {
            encontrados = repositorioCusto.buscarCustoDePeriodoDoCanal(canalA, inicio, fim);
        } finally {
            ContextoTenant.limpar();
        }

        assertEquals(1, encontrados.size(),
                "no contexto de A, buscarCustoDePeriodoDoCanal deveria devolver EXATAMENTE o custo de A");
        Custo encontrado = encontrados.get(0);
        assertEquals(idCustoA, encontrado.getId());
        assertEquals(tenantA, encontrado.getTenantId(),
                "VAZAMENTO: o custo devolvido no contexto de A pertence a outro tenant");
        assertTrue(new BigDecimal("49.9000").compareTo(encontrado.getValor()) == 0,
                "valor devolvido deveria ser exatamente o de A, nao o de B");
    }

    /**
     * Mesmo cenario perigoso das duas consultas anteriores, agora para
     * custo: contexto de A, {@code canalId} REAL de B.
     */
    @Test
    void buscarCustoDePeriodoDoCanalComCanalIdDeOutroTenantDevolveListaVazia() throws SQLException {
        UUID tenantA = criarTenant("margem-custo-canal-alheio-a");
        UUID tenantB = criarTenant("margem-custo-canal-alheio-b");
        UUID canalB = criarCanalComoTenant(tenantB, "canal-custo-alheio-b");

        OffsetDateTime inicio = OffsetDateTime.now().minusDays(5);
        OffsetDateTime fim = OffsetDateTime.now().plusDays(5);
        OffsetDateTime competenciaEm = OffsetDateTime.now();

        UUID idCustoB = inserirCustoDePeriodoComoTenant(tenantB, canalB, competenciaEm, new BigDecimal("129.9000"));

        assertEquals(1, contarComoPrivilegiado("custo", tenantB, idCustoB),
                "setup falhou: custo de B nao foi gravado de verdade");

        ContextoTenant.definir(tenantA);
        List<Custo> encontrados;
        try {
            // canalB e um canal_id REAL e VALIDO de B.
            encontrados = repositorioCusto.buscarCustoDePeriodoDoCanal(canalB, inicio, fim);
        } finally {
            ContextoTenant.limpar();
        }

        assertTrue(encontrados.isEmpty(),
                "VAZAMENTO: consulta no contexto de A com canalId de B deveria devolver lista vazia, "
                        + "nao o custo de B");
    }

    // ------------------------------------------------------------------
    // Auxiliares - criacao de tenant (identico a IsolamentoFaseUmTest /
    // ServicoIngestaoTest).
    // ------------------------------------------------------------------

    private UUID criarTenant(String rotulo) throws SQLException {
        UUID id = UUID.randomUUID();
        String slug = "tenant-" + rotulo + "-" + id.toString().substring(0, 8);
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO tenant (id, nome, slug) VALUES (?, ?, ?)")) {
            comando.setObject(1, id);
            comando.setString(2, "Tenant de teste " + rotulo);
            comando.setString(3, slug);
            comando.executeUpdate();
        }
        return id;
    }

    // ------------------------------------------------------------------
    // Auxiliares - insercao "legitima" via repositorio JPA (como o proprio
    // tenant, exercitando a pilha completa: ContextoTenant ->
    // DataSourceComTenant -> RLS -> @TenantId), usadas para montar dado
    // real a ser lido nos testes. Mesmo padrao de
    // ServicoIngestaoTest#criarCanalMercadoLivre.
    // ------------------------------------------------------------------

    private UUID criarCanalComoTenant(UUID tenantId, String marcador) {
        ContextoTenant.definir(tenantId);
        try {
            Canal canal = repositorioCanal.save(new Canal(
                    // codigo precisa bater com ck_canal_codigo_formato (V005)
                    // e ser unico por tenant - fragmento de UUID garante as
                    // duas coisas entre chamadas, mesmo padrao de
                    // IsolamentoFaseUmTest#inserirCanalComoTenant.
                    "canal-" + UUID.randomUUID().toString().substring(0, 8),
                    marcador, TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, null, null, null));
            return canal.getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID inserirTaxaComoTenant(UUID tenantId, UUID canalId, OffsetDateTime vigenciaInicio, BigDecimal percentual) {
        ContextoTenant.definir(tenantId);
        try {
            TaxaCanal taxa = new TaxaCanal(canalId, TipoTaxaCanal.COMISSAO, NaturezaCusto.COMISSAO_CANAL,
                    BaseIncidencia.VALOR_TOTAL_ITEM,
                    TaxaCanal.CURINGA, TaxaCanal.CURINGA, null, null,
                    vigenciaInicio, null, percentual, null, null, null, "BRL",
                    ConfiancaTaxa.INFORMADO_PELO_LOJISTA, OrigemTaxa.CADASTRO_MANUAL,
                    null, null, null, null, null);
            return repositorioTaxaCanal.save(taxa).getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID inserirPedidoComoTenant(UUID tenantId, UUID canalId, OffsetDateTime feitoEm, BigDecimal valorTotalPedido) {
        ContextoTenant.definir(tenantId);
        try {
            Pedido pedido = new Pedido(canalId, null, "id-externo-" + UUID.randomUUID(),
                    "pedido-margem-" + UUID.randomUUID().toString().substring(0, 8),
                    StatusPedido.PAGO, null, feitoEm,
                    valorTotalPedido, BigDecimal.ZERO, BigDecimal.ZERO, valorTotalPedido, null, "BRL",
                    null, null, null, null, null, null);
            return repositorioPedido.save(pedido).getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID inserirCustoDePeriodoComoTenant(UUID tenantId, UUID canalId, OffsetDateTime competenciaEm, BigDecimal valor) {
        ContextoTenant.definir(tenantId);
        try {
            // Mesma forma exigida por RepositorioCusto.buscarCustoDePeriodoDoCanal
            // (Javadoc do metodo): canalId preenchido, pedidoId e
            // rateadoDeCustoId NULOS - custo de PERIODO ligado a um canal,
            // nao rateado e sem pedido (ex.: mensalidade de conta do ML).
            Custo custo = new Custo(NaturezaCusto.TARIFA_ADMINISTRATIVA, null, null, null,
                    valor, "BRL", competenciaEm, false,
                    null, null, null, null,
                    "custo-periodo-canal-" + UUID.randomUUID().toString().substring(0, 8),
                    canalId, null, null);
            return repositorioCusto.save(custo).getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ------------------------------------------------------------------
    // Auxiliar - caminho privilegiado (fora do RLS), so para verificar o
    // estado real do banco antes de provar isolamento - nunca para testar
    // se o isolamento funciona. Ver Javadoc de PostgresDeTeste.
    // ------------------------------------------------------------------

    private long contarComoPrivilegiado(String tabela, UUID tenantId, UUID id) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM " + tabela + " WHERE tenant_id = ? AND id = ?")) {
            comando.setObject(1, tenantId);
            comando.setObject(2, id);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }
}
