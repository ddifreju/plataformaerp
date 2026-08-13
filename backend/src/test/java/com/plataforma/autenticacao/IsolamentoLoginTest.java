package com.plataforma.autenticacao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.suporte.PostgresDeTeste;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolamento de tenant COMPORTAMENTAL da tabela {@code usuario} (V014), com
 * foco exclusivo na quinta policy, {@code usuario_select_login} - a excecao
 * deliberada ao molde da decisao 0010, registrada em
 * {@code docs/decisoes/0024-policy-de-login-e-email-global.md} e explicada
 * nos blocos 1 e 2 do cabecalho de {@code V014__usuario.sql}.
 *
 * <p>Este arquivo NAO repete a garantia estrutural de
 * {@link com.plataforma.comum.tenant.RlsAtivoEmTodasAsTabelasTest} (RLS
 * ligado, FORCE ligado, uma policy por comando obrigatorio existe - essa
 * verificacao, por ser generica, ja cobre {@code usuario} contando as
 * CINCO policies da tabela sem exigir um numero fixo por comando). Tambem
 * NAO repete o padrao INSERT/UPDATE com tenant forjado de
 * {@link com.plataforma.comum.tenant.IsolamentoFaseUmTest} - {@code
 * usuario_insert}/{@code usuario_update} sao do MESMO molde generico ja
 * exercitado la (USING + WITH CHECK contra {@code app_current_tenant_id()}),
 * sem nada especifico de {@code usuario} que justifique repetir. O que
 * falta, e que este arquivo fecha, e o COMPORTAMENTO da UNICA policy desta
 * tabela que nao segue o molde: a fresta de login.
 *
 * <h2>O caso que o arquiteto marcou como o que nao pode faltar</h2>
 * {@link #loginDeOutroTenantDentroDeSessaoDevolveZeroLinhas()}: contexto no
 * tenant A, {@code app.login_email} apontando para um usuario REAL do
 * tenant B. A leitura tem que devolver ZERO linhas. E o unico teste deste
 * arquivo que prova que a fresta de login (pensada para o instante SEM
 * tenant) nao vira travessia lateral DENTRO de uma sessao ja autenticada -
 * o cenario mais perigoso possivel para esta policy, porque e exatamente o
 * momento em que ela deveria estar inteiramente desligada
 * ({@code app_current_tenant_id() IS NULL} e falso).
 *
 * <h2>Por que raw JDBC contra a tabela, e nao via {@link RepositorioLoginUsuario}</h2>
 * {@code app_usuario_para_login} (a funcao que a aplicacao de fato chama)
 * RECUSA rodar com tenant ja resolvido - ela lanca excecao de proposito
 * (guarda de coerencia no corpo da funcao, V014). Isso significa que o
 * cenario mais perigoso ("tenant A no contexto, GUC de login apontando
 * para B") NAO E ALCANCAVEL atraves do caminho de producao - e por isso
 * mesmo que ele precisa ser testado direto contra a tabela: a guarda da
 * funcao impede o MAU USO por quem ja sabe que nao devia chamar a funcao
 * ali, mas a POLICY e quem realmente decide o que uma consulta qualquer
 * (com ou sem a funcao no meio) pode enxergar. Testar so a funcao provaria
 * a guarda, nunca a policy - e a policy e a camada que protege mesmo
 * contra codigo que ignore a funcao e vá direto ao SQL (algo que a propria
 * tabela permite: GRANT SELECT em {@code usuario} e concedido a
 * {@code app_aplicacao} por inteiro, nao so atraves da funcao).
 *
 * <p>Os testes que fixam o GUC {@code app.login_email} manualmente
 * (fora de {@code app_usuario_para_login}) usam {@code set_config(...,
 * false)} - SESSAO, nao LOCAL - de proposito: eles setam o GUC numa
 * instrucao e leem noutra, e precisam que o valor sobreviva entre as duas.
 * A producao NUNCA faz isso (so a funcao mexe neste GUC, e sempre como
 * LOCAL); e exatamente essa diferenca que
 * {@link #gucDeLoginELocalENaoSobreviveAProximaInstrucao()} testa, chamando
 * a funcao de verdade.
 *
 * <h2>Como verificar que este teste nao e decorativo</h2>
 * Tres sabotagens concretas em {@code V014__usuario.sql}, cada uma lida e
 * confirmada linha a linha antes de ser listada aqui (nao presumida).
 * <ol>
 *   <li><b>Remover {@code app_current_tenant_id() IS NULL AND} do
 *       {@code USING} de {@code usuario_select_login}</b>, deixando so
 *       {@code email = lower(nullif(btrim(current_setting('app.login_email', true)), ''))}.
 *       Esta e A sabotagem que corresponde exatamente ao risco que o
 *       arquiteto apontou. Efeito:
 *       {@link #loginDeOutroTenantDentroDeSessaoDevolveZeroLinhas()} fica
 *       vermelho - com o GUC apontando para o e-mail de B, a policy
 *       (agora incondicional) passa a bater na linha de B independente do
 *       contexto ser A, e como as policies permissivas se somam com OU,
 *       {@code usuario_select} (que continua exigindo {@code tenant_id = A}
 *       e bloquearia sozinha) deixa de ser a unica palavra final - a linha
 *       de B aparece. Os demais testes deste arquivo NAO sao afetados por
 *       esta sabotagem isolada: todos os outros rodam sem tenant no
 *       contexto, onde a clausula removida ja era verdadeira mesmo antes.</li>
 *   <li><b>Trocar {@code is_local => true} por {@code is_local => false}</b>
 *       na chamada {@code set_config('app.login_email', v_email, true)}
 *       dentro de {@code app_usuario_para_login}. Efeito:
 *       {@link #gucDeLoginELocalENaoSobreviveAProximaInstrucao()} fica
 *       vermelho - o GUC passa a valer para o resto da SESSAO (conexao), e
 *       a segunda instrucao deste teste (uma nova transacao implicita, na
 *       MESMA conexao, sem tenant no contexto) volta a enxergar a linha
 *       porque {@code app.login_email} ainda esta setado. E exatamente o
 *       vazamento para o pool que a decisao 0010 documenta para {@code SET}
 *       de sessao, agora provado no caminho especifico desta tabela. Os
 *       outros testes nao quebram: nenhum outro depende de o GUC EXPIRAR
 *       sozinho.</li>
 *   <li><b>Trocar {@code email = ...} por
 *       {@code email LIKE ... || '%'}</b> no {@code USING} de
 *       {@code usuario_select_login} (comparacao de prefixo em vez de
 *       igualdade exata). Efeito:
 *       {@link #prefixoOuCoringaNoGucDeLoginNaoCasaNenhumaLinha()} fica
 *       vermelho nas duas partes - tanto o prefixo (parte local do e-mail
 *       sem o dominio) quanto o coringa {@code '%'} passam a CASAR a linha
 *       real, porque {@code '%'} e qualquer prefixo válido de um LIKE
 *       batem contra qualquer sufixo. {@link #gucDeLoginAbreExatamenteUmaLinhaDoUsuarioCorreto()}
 *       continua verde com esta sabotagem isolada (o GUC la e o e-mail
 *       EXATO, que continua batendo igual com {@code =} ou com
 *       {@code LIKE} sem coringa) - o que mostra por que o teste de
 *       enumeracao PRECISA existir separado do teste de "abre uma linha":
 *       nenhum dos dois, sozinho, prova o outro.</li>
 * </ol>
 *
 * <h2>RESSALVA HONESTA sobre o que este arquivo NAO prova</h2>
 * <ul>
 *   <li>Nao prova que {@code usuario_insert}/{@code usuario_update}/
 *       {@code usuario_delete} seguram um {@code tenant_id} forjado - essas
 *       tres seguem o MESMO molde generico (decisao 0010) ja exercitado
 *       exaustivamente contra {@code canal}/{@code pedido}/{@code cliente}
 *       em {@link com.plataforma.comum.tenant.IsolamentoFaseUmTest}; nao
 *       ha texto novo de policy nelas que justifique repetir o padrao
 *       aqui.</li>
 *   <li>Nao prova que {@link ServicoUserDetails}/{@link ServicoLogin}
 *       de fato RECUSAM o login quando {@code tenant_ativo} vem falso -
 *       isso e comportamento de APLICACAO (Java), nao de isolamento de
 *       banco, e pertenceria a um teste da camada de autenticacao, nao a
 *       este arquivo. O que este arquivo prova, no teste de tenant
 *       inativo, e que o BANCO entrega o dado correto para a aplicacao
 *       decidir - se a aplicacao ignorar esse dado, e um bug que este
 *       arquivo nao pega.</li>
 *   <li>Nao prova que a guarda de coerencia de {@code app_usuario_para_login}
 *       (RAISE EXCEPTION quando chamada com tenant ja resolvido) de fato
 *       lanca - isso seria um teste a parte, sobre a FUNCAO; este arquivo
 *       testa deliberadamente a POLICY por baixo dela, que e o que continua
 *       de pe mesmo se a guarda da funcao for removida ou contornada (ver
 *       a secao acima sobre por que raw JDBC contra a tabela).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IsolamentoLoginTest {

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.configurarPropriedades(registro);
    }

    @Autowired
    private DataSource dataSource;

    // Hash BCrypt "de mentira" (nao gerado por BCryptPasswordEncoder de
    // verdade) - so precisa satisfazer ck_usuario_senha_hash_formato
    // (V014): $2[aby]$<custo 12-99>$<53 chars validos>. Nenhum teste deste
    // arquivo autentica com senha; a coluna so precisa existir e ser
    // valida para o INSERT passar pelo CHECK.
    private static final String SENHA_HASH_DE_TESTE = "$2a$12$" + "A".repeat(53);

    @AfterEach
    void limparContexto() {
        // Mesma defesa contra vazamento de tenant ENTRE TESTES usada em
        // IsolamentoFaseUmTest/IsolamentoMargemTest.
        ContextoTenant.limpar();
    }

    // ==================================================================
    // 1. O caso que o arquiteto exigiu
    // ==================================================================

    /**
     * Contexto no tenant A, {@code app.login_email} apontando para um
     * usuario REAL do tenant B: a leitura tem que devolver ZERO linhas.
     * A fresta so pode abrir QUANDO NAO HA tenant no contexto - dentro de
     * uma sessao ja autenticada, ela precisa continuar completamente
     * inerte. E o caso que prova que a fresta nao vira travessia lateral.
     */
    @Test
    void loginDeOutroTenantDentroDeSessaoDevolveZeroLinhas() throws SQLException {
        UUID tenantA = criarTenant("login-cruzado-a", true);
        UUID tenantB = criarTenant("login-cruzado-b", true);
        String emailB = "usuario-b-" + UUID.randomUUID() + "@loja-b.com.br";
        UUID idUsuarioB = inserirUsuarioComoTenant(tenantB, emailB, "DONO");

        // Prova que ha o que vazar ANTES de provar que nao vazou (mesmo
        // racional de IsolamentoFaseUmTest#canalNaoVeDadoAlheio).
        assertEquals(1, contarComoPrivilegiado(tenantB, idUsuarioB),
                "setup falhou: usuario de B nao foi gravado de verdade");

        ContextoTenant.definir(tenantA);
        try (Connection conexao = dataSource.getConnection()) {
            try {
                definirGucLoginEmail(conexao, emailB);
                try (PreparedStatement consulta = conexao.prepareStatement(
                        "SELECT id, tenant_id FROM usuario WHERE email = ?")) {
                    consulta.setString(1, emailB);
                    try (ResultSet resultado = consulta.executeQuery()) {
                        assertFalse(resultado.next(),
                                "TRAVESSIA LATERAL: contexto no tenant A + app.login_email apontando para "
                                        + "um usuario de B deveria devolver ZERO linhas - a fresta de login so "
                                        + "pode abrir fora de sessao (app_current_tenant_id() IS NULL)");
                    }
                }
            } finally {
                // Limpa o GUC manual ANTES da conexao voltar ao pool do
                // Hikari - DataSourceComTenant so reseta app.tenant_id a
                // cada getConnection(), nunca ouviu falar de
                // app.login_email. Sem este reset, a proxima requisicao
                // (neste teste, o proximo metodo de teste) que reutilizasse
                // esta MESMA conexao fisica sem tenant no contexto herdaria
                // este GUC ja setado - nao e uma falha do RLS, e disciplina
                // de teste para nao contaminar o pool.
                definirGucLoginEmail(conexao, "");
            }
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ==================================================================
    // 2. Fail-closed sem o GUC
    // ==================================================================

    /**
     * Sem {@code app.login_email} setado e sem tenant no contexto, a
     * leitura de {@code usuario} devolve zero linhas - a comparacao vira
     * {@code email = NULL}, que o Postgres trata como falso (mesma
     * falha fechada da V001, agora na policy de login).
     */
    @Test
    void semGucDeLoginEDeTenantALeituraDevolveZeroLinhas() throws SQLException {
        UUID tenant = criarTenant("fail-closed", true);
        String email = "usuario-fail-closed-" + UUID.randomUUID() + "@loja.com.br";
        UUID idUsuario = inserirUsuarioComoTenant(tenant, email, "GESTOR");

        assertEquals(1, contarComoPrivilegiado(tenant, idUsuario),
                "setup falhou: usuario nao foi gravado de verdade");

        // Conexao NOVA via DriverManager (novaConexaoAppAplicacao): nenhum
        // GUC foi setado nesta sessao ainda - nem app.tenant_id nem
        // app.login_email. current_setting(..., true) devolve NULL para
        // os dois, que e exatamente o estado "fora de sessao, GUC de login
        // nunca setado de proposito" que a policy precisa tratar como
        // fechado.
        try (Connection conexao = PostgresDeTeste.novaConexaoAppAplicacao();
                PreparedStatement consulta = conexao.prepareStatement(
                        "SELECT id FROM usuario WHERE email = ?")) {
            consulta.setString(1, email);
            try (ResultSet resultado = consulta.executeQuery()) {
                assertFalse(resultado.next(),
                        "FAIL-OPEN: sem app.login_email setado (e sem tenant no contexto), a leitura "
                                + "deveria devolver ZERO linhas - a comparacao email = NULL precisa ser tratada "
                                + "como falsa, nunca como \"passa liberado\"");
            }
        }
    }

    // ==================================================================
    // 3. A fresta abre uma linha so
    // ==================================================================

    /**
     * Com o GUC no e-mail exato e sem tenant no contexto, a leitura volta
     * EXATAMENTE 1 linha - a daquele usuario - e nao os demais usuarios do
     * MESMO tenant. A consulta e {@code SELECT * FROM usuario} SEM
     * predicado nenhum de proposito: assim, quem restringe o resultado a
     * uma linha so e a policy, nunca um WHERE do teste disfarçando o
     * resultado.
     */
    @Test
    void gucDeLoginAbreExatamenteUmaLinhaDoUsuarioCorreto() throws SQLException {
        UUID tenant = criarTenant("fresta-uma-linha", true);
        String emailAlvo = "alvo-" + UUID.randomUUID() + "@loja.com.br";
        String emailOutro = "outro-" + UUID.randomUUID() + "@loja.com.br";
        UUID idAlvo = inserirUsuarioComoTenant(tenant, emailAlvo, "DONO");
        UUID idOutro = inserirUsuarioComoTenant(tenant, emailOutro, "ANALISTA");

        assertEquals(1, contarComoPrivilegiado(tenant, idAlvo), "setup falhou: usuario alvo nao foi gravado");
        assertEquals(1, contarComoPrivilegiado(tenant, idOutro), "setup falhou: usuario outro nao foi gravado");

        try (Connection conexao = PostgresDeTeste.novaConexaoAppAplicacao()) {
            definirGucLoginEmail(conexao, emailAlvo);
            try (PreparedStatement consulta = conexao.prepareStatement("SELECT id FROM usuario");
                    ResultSet resultado = consulta.executeQuery()) {
                List<UUID> idsVistos = new ArrayList<>();
                while (resultado.next()) {
                    idsVistos.add((UUID) resultado.getObject("id"));
                }
                assertEquals(List.of(idAlvo), idsVistos,
                        "com o GUC no e-mail exato, a leitura da tabela INTEIRA (sem WHERE) deveria "
                                + "devolver SO a linha daquele usuario - nem zero linhas, nem os demais "
                                + "usuarios do mesmo tenant (idOutro = " + idOutro + ")");
            }
        }
    }

    // ==================================================================
    // 4. Nao permite enumeracao
    // ==================================================================

    /**
     * Um e-mail parcial (prefixo, sem o dominio) ou um coringa {@code '%'}
     * nao casam nada - prova que a policy compara IGUALDADE EXATA, nunca
     * prefixo ou padrao. E o que torna esta fresta um oraculo restrito
     * (so responde para quem ja sabe o e-mail inteiro), nao uma ferramenta
     * de descoberta de contas.
     */
    @Test
    void prefixoOuCoringaNoGucDeLoginNaoCasaNenhumaLinha() throws SQLException {
        UUID tenant = criarTenant("sem-enumeracao", true);
        String emailReal = "pessoa-" + UUID.randomUUID() + "@dominio-real.com.br";
        UUID idReal = inserirUsuarioComoTenant(tenant, emailReal, "GESTOR");
        assertEquals(1, contarComoPrivilegiado(tenant, idReal), "setup falhou: usuario nao foi gravado");

        String parteLocalSemDominio = emailReal.substring(0, emailReal.indexOf('@'));
        String coringa = "%";

        try (Connection conexao = PostgresDeTeste.novaConexaoAppAplicacao()) {
            definirGucLoginEmail(conexao, parteLocalSemDominio);
            assertEquals(0, contarLinhasSemPredicado(conexao),
                    "ENUMERACAO: a parte local do e-mail (sem o dominio, \"" + parteLocalSemDominio
                            + "\") nao deveria casar nada - a policy compara igualdade exata, nao prefixo");

            definirGucLoginEmail(conexao, coringa);
            assertEquals(0, contarLinhasSemPredicado(conexao),
                    "ENUMERACAO: um coringa '%' nao deveria casar nada - a policy nao faz LIKE/padrao");
        }
    }

    // ==================================================================
    // 5. O GUC e LOCAL e nao vaza
    // ==================================================================

    /**
     * Chama o UNICO caminho real de leitura pre-sessao,
     * {@code app_usuario_para_login} - ela seta {@code app.login_email}
     * como GUC LOCAL e le na MESMA instrucao. Depois que a chamada
     * termina, uma instrucao SEPARADA na MESMA conexao (uma nova transacao
     * implicita, ja que a conexao roda em autocommit) NAO enxerga mais a
     * linha. E o mecanismo que impede a fresta de voltar aberta ao pool
     * do HikariCP - o risco que a decisao 0010 documenta sobre {@code SET}
     * de sessao.
     */
    @Test
    void gucDeLoginELocalENaoSobreviveAProximaInstrucao() throws SQLException {
        UUID tenant = criarTenant("guc-local", true);
        String email = "local-" + UUID.randomUUID() + "@loja.com.br";
        UUID idUsuario = inserirUsuarioComoTenant(tenant, email, "DONO");
        assertEquals(1, contarComoPrivilegiado(tenant, idUsuario), "setup falhou: usuario nao foi gravado");

        try (Connection conexao = PostgresDeTeste.novaConexaoAppAplicacao()) {
            // Instrucao 1: o caminho real de producao. Sem tenant no
            // contexto desta conexao nova, app_usuario_para_login deveria
            // achar a linha.
            try (PreparedStatement chamada = conexao.prepareStatement(
                    "SELECT usuario_id FROM app_usuario_para_login(?)")) {
                chamada.setString(1, email);
                try (ResultSet resultado = chamada.executeQuery()) {
                    assertTrue(resultado.next(),
                            "setup do teste: app_usuario_para_login deveria achar o usuario recem-criado "
                                    + "(sem isso o restante do teste nao prova nada)");
                    assertEquals(idUsuario, resultado.getObject("usuario_id"));
                }
            }

            // Instrucao 2: SEPARADA, mesma conexao, ainda sem tenant no
            // contexto. Se o GUC fosse mesmo LOCAL, ja foi descartado no
            // fim da instrucao 1 (autocommit = uma transacao implicita por
            // instrucao).
            try (PreparedStatement consulta = conexao.prepareStatement(
                    "SELECT id FROM usuario WHERE email = ?")) {
                consulta.setString(1, email);
                try (ResultSet resultado = consulta.executeQuery()) {
                    assertFalse(resultado.next(),
                            "VAZAMENTO DE GUC: depois que app_usuario_para_login retornou, uma instrucao "
                                    + "SEPARADA na MESMA conexao ainda enxergou a linha - o GUC deveria ter "
                                    + "sido LOCAL (is_local => true) e ja descartado no fim da instrucao "
                                    + "anterior, nao sobreviver como valor de SESSAO");
                }
            }
        }
    }

    // ==================================================================
    // 6. Usuario de tenant inativo
    // ==================================================================

    /**
     * Se a loja esta desativada, o login nao deve prosseguir. O que este
     * teste prova e a METADE que pertence ao banco: {@code
     * app_usuario_para_login} devolve {@code tenant_ativo = false} para um
     * usuario ativo cujo tenant foi desativado - o dado que a aplicacao
     * PRECISA consultar para recusar o login (armadilha 7 da V014). A
     * outra metade - a aplicacao de fato recusando - e comportamento de
     * {@link ServicoUserDetails}/Spring Security, fora do escopo de um
     * teste de isolamento de banco (ver a ressalva no Javadoc da classe).
     */
    @Test
    void usuarioDeTenantInativoDevolveTenantAtivoFalse() throws SQLException {
        UUID tenantInativo = criarTenant("tenant-inativo", false);
        String email = "pessoa-tenant-inativo-" + UUID.randomUUID() + "@loja.com.br";
        // usuario_insert (V014) so confere tenant_id = GUC, nunca t.ativo -
        // um tenant desativado ainda aceita INSERT de usuario. Correto:
        // desativar a loja nao deveria travar cadastro pre-existente.
        UUID idUsuario = inserirUsuarioComoTenant(tenantInativo, email, "GESTOR");
        assertEquals(1, contarComoPrivilegiado(tenantInativo, idUsuario), "setup falhou: usuario nao foi gravado");

        try (Connection conexao = PostgresDeTeste.novaConexaoAppAplicacao();
                PreparedStatement chamada = conexao.prepareStatement(
                        "SELECT usuario_ativo, tenant_ativo FROM app_usuario_para_login(?)")) {
            chamada.setString(1, email);
            try (ResultSet resultado = chamada.executeQuery()) {
                assertTrue(resultado.next(),
                        "o usuario deveria ser encontrado - a loja inativa nao esconde a linha, "
                                + "so sinaliza o motivo para a aplicacao recusar o login");
                assertTrue(resultado.getBoolean("usuario_ativo"),
                        "o usuario em si esta ativo - so a loja (tenant) nao esta, e sao flags distintas");
                assertFalse(resultado.getBoolean("tenant_ativo"),
                        "loja desativada: app_usuario_para_login precisa sinalizar tenant_ativo = false "
                                + "para que quem chama recuse o login (a funcao nao filtra sozinha por "
                                + "proposito - ver bloco da funcao e armadilha 7 da V014)");
            }
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares - criacao de tenant (mesmo padrao de IsolamentoFaseUmTest
    // / IsolamentoMargemTest, com o parametro "ativo" a mais que o caso 6
    // exige).
    // ------------------------------------------------------------------

    private UUID criarTenant(String rotulo, boolean ativo) throws SQLException {
        UUID id = UUID.randomUUID();
        String slug = "tenant-" + rotulo + "-" + id.toString().substring(0, 8);
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO tenant (id, nome, slug, ativo) VALUES (?, ?, ?, ?)")) {
            comando.setObject(1, id);
            comando.setString(2, "Tenant de teste " + rotulo);
            comando.setString(3, slug);
            comando.setBoolean(4, ativo);
            comando.executeUpdate();
        }
        return id;
    }

    // ------------------------------------------------------------------
    // Auxiliar - insercao "legitima" via app_aplicacao/RLS (como o proprio
    // tenant faria), usada para montar dado real a ser lido nos testes.
    // Mesmo padrao de inserirCanalComoTenant em IsolamentoFaseUmTest.
    // ------------------------------------------------------------------

    private UUID inserirUsuarioComoTenant(UUID tenantId, String email, String papel) throws SQLException {
        ContextoTenant.definir(tenantId);
        try (Connection conexao = dataSource.getConnection();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO usuario (tenant_id, email, senha_hash, nome, papel) "
                                + "VALUES (?, ?, ?, ?, ?) RETURNING id")) {
            comando.setObject(1, tenantId);
            comando.setString(2, email);
            comando.setString(3, SENHA_HASH_DE_TESTE);
            comando.setString(4, "Pessoa de teste");
            comando.setString(5, papel);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return (UUID) resultado.getObject("id");
            }
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares - GUC manual de app.login_email, SESSAO (nao LOCAL) DE
    // PROPOSITO: estes helpers setam o GUC numa instrucao e leem noutra, e
    // precisam que o valor sobreviva entre as duas. A producao nunca faz
    // isto (so app_usuario_para_login mexe neste GUC, sempre como LOCAL -
    // ver gucDeLoginELocalENaoSobreviveAProximaInstrucao, que chama a
    // funcao de verdade em vez deste helper).
    // ------------------------------------------------------------------

    private void definirGucLoginEmail(Connection conexao, String valor) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT set_config('app.login_email', ?, false)")) {
            comando.setString(1, valor);
            comando.execute();
        }
    }

    private long contarLinhasSemPredicado(Connection conexao) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("SELECT count(*) FROM usuario");
                ResultSet resultado = comando.executeQuery()) {
            resultado.next();
            return resultado.getLong(1);
        }
    }

    // ------------------------------------------------------------------
    // Auxiliar - caminho privilegiado (fora do RLS), so para verificar o
    // estado real do banco antes de provar isolamento - nunca para testar
    // se o isolamento funciona. Ver Javadoc de PostgresDeTeste.
    // ------------------------------------------------------------------

    private long contarComoPrivilegiado(UUID tenantId, UUID usuarioId) throws SQLException {
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "SELECT count(*) FROM usuario WHERE tenant_id = ? AND id = ?")) {
            comando.setObject(1, tenantId);
            comando.setObject(2, usuarioId);
            try (ResultSet resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getLong(1);
            }
        }
    }
}
