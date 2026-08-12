package com.plataforma.suporte;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Infraestrutura de banco compartilhada por toda a suite de testes que
 * precisa de Postgres de verdade (decisao 0008: "Testcontainers para
 * todo teste que toca banco" — H2 e mock nao servem para provar RLS).
 *
 * <h2>Por que singleton estatico, e nao {@code @Container} por classe</h2>
 * Subir o container e aplicar as quatro migrations custa segundos. Um
 * container por classe de teste multiplicaria esse custo por classe sem
 * ganho de isolamento (cada teste ja cria seus proprios tenants com UUID
 * aleatorio — ver {@code criarTenant} em IsolamentoDeTenantTest — entao
 * dados de um teste nunca colidem com os de outro mesmo compartilhando
 * o container). O container e iniciado uma unica vez, na primeira classe
 * de teste que tocar esta classe (carregamento de classe Java e
 * naturalmente idempotente e thread-safe para blocos {@code static}), e
 * nunca e parado explicitamente: o Ryuk do Testcontainers derruba o
 * container quando a JVM do test runner termina.
 *
 * <h2>O ponto critico: dois papeis de banco diferentes</h2>
 * Esta classe abre conexoes com DOIS papeis, para dois propositos que
 * NUNCA podem ser confundidos:
 * <ul>
 *   <li>{@link #novaConexaoDono()} — o usuario que o container cria
 *       (superusuario, por construcao: a imagem oficial do Postgres
 *       sempre cria o {@code POSTGRES_USER} configurado como
 *       superusuario via {@code initdb}). Usado SOMENTE para (a) aplicar
 *       as migrations, que exigem DDL, e (b) como "caminho privilegiado"
 *       nos testes de isolamento, para prova provar que uma linha existe
 *       de fato no banco por fora do RLS. Superusuario ignora RLS SEMPRE,
 *       com ou sem FORCE (FORCE so importa para o dono quando o dono NAO
 *       e superusuario — ver a ressalva no Javadoc de
 *       IsolamentoDeTenantTest). NUNCA use esta conexao para testar se o
 *       isolamento de tenant funciona: o resultado seria enganoso por
 *       construcao.</li>
 *   <li>{@link #configurarPropriedades} / {@link #novaConexaoAppAplicacao()}
 *       — o papel {@code app_aplicacao}, criado sem privilegio de DDL e
 *       sem BYPASSRLS pela migration V004. E o papel que a aplicacao usa
 *       em producao, e o unico com que faz sentido testar isolamento:
 *       testar como dono/superusuario "passaria falsamente" mesmo se o
 *       RLS estivesse quebrado (decisao 0010, armadilha registrada).</li>
 * </ul>
 *
 * <h2>Por que a senha de app_aplicacao e gerada aqui, e nao lida de env</h2>
 * A V004 cria o papel deliberadamente SEM senha (comentario da propria
 * migration: segredo de producao nunca fica em migration versionada).
 * Este ambiente de teste roda inteiramente dentro de um container
 * efemero, acessivel so de localhost pela porta que o Testcontainers
 * escolhe aleatoriamente — nao existe "vazamento" possivel de um valor
 * gerado em memoria, usado uma vez, e descartado quando a JVM do teste
 * termina. Por isso {@code ALTER ROLE ... PASSWORD '<literal>'} aqui usa
 * concatenacao de string em vez de bind parameter (o comando ALTER ROLE
 * do Postgres so aceita a senha como literal na gramatica, nao como
 * parametro do protocolo estendido). Isso e seguro apenas porque o
 * literal e um UUID gerado por nos mesmos (nunca contem aspas ou
 * caracteres de controle) — o mesmo NAO valeria para o GUC
 * app.tenant_id, que carrega valor efetivamente "externo" (vem do
 * ContextoTenant) e por isso, em DataSourceComTenant, usa sempre
 * set_config com bind parameter (ver comentario la).
 */
public final class PostgresDeTeste {

    private static final String IMAGEM = "pgvector/pgvector:pg16";

    private static final PostgreSQLContainer<?> CONTAINER;
    private static final String SENHA_APP_APLICACAO;

    static {
        CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse(IMAGEM).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("plataforma_teste")
                // Nome do usuario "dono" deliberadamente diferente de
                // app_aplicacao: sao papeis distintos, com privilegios
                // distintos, exatamente como em producao (pom.xml separa
                // FLYWAY_USUARIO de SPRING_DATASOURCE_USERNAME pelo mesmo
                // motivo — ver o comentario do plugin do Flyway no pom.xml).
                .withUsername("dono_teste")
                .withPassword("dono_teste");
        CONTAINER.start();

        SENHA_APP_APLICACAO = UUID.randomUUID().toString();

        aplicarMigrations();
        definirSenhaDeAppAplicacao();
    }

    private PostgresDeTeste() {
        // classe utilitaria: sem instancia
    }

    /**
     * Aplica as migrations do Flyway (mesmo diretorio que "make migrate"
     * usa em dev/producao — ver pom.xml) contra o container, conectado
     * como o dono (unico papel com privilegio de DDL nesta configuracao
     * de teste).
     *
     * "classpath:db/migration" (em vez de um caminho de arquivo) porque
     * o plugin de recursos do Maven copia
     * backend/src/main/resources/db/migration para target/classes, que
     * esta no classpath de teste (dependencias "runtime" e "compile" do
     * artefato principal fazem parte do classpath de teste) — isso
     * funciona independente do diretorio de onde o Maven/IDE dispara os
     * testes.
     */
    private static void aplicarMigrations() {
        Flyway.configure()
                .dataSource(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    /**
     * V004 cria app_aplicacao sem senha (login por senha impossivel ate
     * ser definida — ver comentario da propria migration). Sem este
     * passo, nenhuma conexao de teste conseguiria autenticar como
     * app_aplicacao, e toda a suite acabaria testando isolamento contra
     * o dono por engano — exatamente o resultado enganoso que a decisao
     * 0008 e o comentario desta classe alertam para nunca acontecer.
     */
    private static void definirSenhaDeAppAplicacao() {
        try (Connection conexao = novaConexaoDono();
                Statement comando = conexao.createStatement()) {
            comando.execute("ALTER ROLE app_aplicacao WITH LOGIN PASSWORD '" + SENHA_APP_APLICACAO + "'");
        } catch (SQLException erro) {
            throw new IllegalStateException(
                    "Nao foi possivel definir a senha do papel app_aplicacao no container de teste. "
                            + "Isso normalmente significa que a migration V004 nao rodou ou renomeou o papel.",
                    erro);
        }
    }

    /**
     * Conexao como o dono do schema (na pratica, superusuario do
     * container). Uso restrito a: aplicar migration, e servir de
     * "caminho privilegiado fora do RLS" nos testes de isolamento
     * (contar/ler linhas para provar que existem de verdade, independente
     * do que o RLS deixa a aplicacao enxergar). NUNCA use para testar se
     * o isolamento funciona.
     */
    public static Connection novaConexaoDono() throws SQLException {
        return DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
    }

    /**
     * Conexao crua (sem o decorador DataSourceComTenant, sem GUC setado
     * automaticamente) como app_aplicacao. Existe para testes que
     * queiram controlar o GUC app.tenant_id manualmente via SQL, em vez
     * de passar pelo ContextoTenant/Spring — hoje nao usada por nenhum
     * teste desta suite (IsolamentoDeTenantTest usa o bean DataSource do
     * proprio contexto Spring, que E o DataSourceComTenant, para exercitar
     * a pilha completa de producao), mas exposta aqui porque a tarefa
     * pede explicitamente um DataSource/conexao para app_aplicacao.
     */
    public static Connection novaConexaoAppAplicacao() throws SQLException {
        return DriverManager.getConnection(CONTAINER.getJdbcUrl(), "app_aplicacao", SENHA_APP_APLICACAO);
    }

    /**
     * Aponta o contexto Spring do teste para este container, autenticado
     * como app_aplicacao — nunca como dono. E a unica forma correta de
     * testar a pilha real (FiltroTenant -> ContextoTenant ->
     * DataSourceComTenant -> RLS) de ponta a ponta.
     *
     * spring.flyway.enabled ja e false no application.yml principal (a
     * aplicacao nunca migra sozinha — ver o comentario la), entao nao
     * precisa ser repetido aqui; migration ja foi aplicada pelo bloco
     * static acima, antes do contexto Spring subir.
     */
    public static void configurarPropriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", CONTAINER::getJdbcUrl);
        registro.add("spring.datasource.username", () -> "app_aplicacao");
        registro.add("spring.datasource.password", () -> SENHA_APP_APLICACAO);
    }

    public static PostgreSQLContainer<?> container() {
        return CONTAINER;
    }
}
