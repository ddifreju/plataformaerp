package com.plataforma.contrato;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.plataforma.autenticacao.PapelUsuario;
import com.plataforma.autenticacao.UsuarioAutenticado;
import com.plataforma.autenticacao.UsuarioParaLogin;
import com.plataforma.canal.Canal;
import com.plataforma.canal.CategoriaCanal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;
import com.plataforma.catalogo.Produto;
import com.plataforma.catalogo.RepositorioProduto;
import com.plataforma.catalogo.RepositorioVariacao;
import com.plataforma.catalogo.Variacao;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.custo.RepositorioCusto;
import com.plataforma.devolucao.Devolucao;
import com.plataforma.devolucao.MotivoDevolucao;
import com.plataforma.devolucao.RepositorioDevolucao;
import com.plataforma.devolucao.StatusDevolucao;
import com.plataforma.devolucao.TipoDevolucao;
import com.plataforma.margem.BaseIncidencia;
import com.plataforma.margem.ConfiancaTaxa;
import com.plataforma.margem.OrigemTaxa;
import com.plataforma.margem.RepositorioTaxaCanal;
import com.plataforma.margem.TaxaCanal;
import com.plataforma.margem.TipoTaxaCanal;
import com.plataforma.pedido.ItemPedido;
import com.plataforma.pedido.Pedido;
import com.plataforma.pedido.RepositorioItemPedido;
import com.plataforma.pedido.RepositorioPedido;
import com.plataforma.pedido.StatusPedido;
import com.plataforma.pergunta.CodigoIntencao;
import com.plataforma.pergunta.IntencaoDetectada;
import com.plataforma.pergunta.PortaModeloLinguagem;
import com.plataforma.suporte.PostgresDeTeste;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validacao ponta a ponta do CONTRATO HTTP da API (nao mais um teste
 * unitario de servico isolado): {@code @AutoConfigureMockMvc} com a stack
 * web MOCK ligada (o default de {@code @SpringBootTest} sem
 * {@code webEnvironment} explicito) - filtros, Spring Security e
 * serializacao Jackson rodam de verdade, so sem abrir socket TCP. Ver o
 * comentario da tarefa que criou este arquivo: o Tomcat nao sobe nesta
 * maquina (NIO/loopback), e MockMvc e o caminho mais proximo do real que
 * roda e fica versionado.
 *
 * DIFERENCA DELIBERADA em relacao a {@code IsolamentoFaseUmTest} e
 * {@code IsolamentoMargemTest} (ambos {@code webEnvironment = NONE}): este
 * arquivo PRECISA da cadeia de seguranca ligada, porque o que ele prova e
 * justamente "sem sessao, tudo e 401" e "login funciona/nao vaza hash" -
 * nada disso existe fora da stack web.
 *
 * <h2>Como os dados de teste sao montados</h2>
 * NAO depende de {@code infra/dados-demo.sql} (aquele script e para o
 * banco de desenvolvimento, com as duas travas descritas no proprio
 * cabecalho dele). Este arquivo monta o MESMO CENARIO (replicado com
 * numeros proprios, tenant aleatorio por execucao) usando os construtores
 * de entidade + repositorio JPA existentes, pelo MESMO caminho legitimo
 * que {@code IsolamentoMargemTest} ja usa ({@link ContextoTenant#definir}
 * em volta de cada {@code save}, exercitando a pilha real
 * ContextoTenant -&gt; DataSourceComTenant -&gt; RLS -&gt; @TenantId) - com
 * DUAS excecoes que PRECISAM de SQL cru contra
 * {@link PostgresDeTeste#novaConexaoDono()}:
 * <ul>
 *   <li>{@code usuario}: a entidade Java NAO TEM construtor publico de
 *       proposito (ver o Javadoc de {@code Usuario} - "esta aplicacao nao
 *       cria usuario"). Provisionar por SQL, como conexao privilegiada
 *       setando o hash BCrypt gerado pelo {@code PasswordEncoder} do
 *       proprio contexto Spring, e exatamente a "armadilha 8" documentada
 *       no cabecalho da V014.</li>
 *   <li>{@code evento_ingerido} em status ERRO: o construtor de
 *       {@code EventoIngerido} so cria eventos {@code RECEBIDO} (o estado
 *       ERRO/erro_mensagem/tentativas so existe apos um pipeline de
 *       reprocessamento que e trabalho de outra tarefa) - inserido cru
 *       para chegar direto no estado final que a fila do analista precisa
 *       mostrar.</li>
 * </ul>
 * As duas excecoes usam a conexao de DONO (superusuario, ignora RLS por
 * construcao - ver o Javadoc de {@link PostgresDeTeste}), o mesmo uso
 * "provisionamento de fixture" que {@code IsolamentoFaseUmTest}/
 * {@code IsolamentoMargemTest} ja fazem para {@code tenant}. NUNCA usada
 * para testar se isolamento funciona - so para montar dado real a
 * consultar depois pela API HTTP de verdade.
 *
 * <h2>O cenario, e por que os numeros batem com o documento fiscal</h2>
 * Pedido "CALCULADA" replica o exemplo numerico da secao 2.5 do documento
 * fiscal (os mesmos sete custos de {@code infra/dados-demo.sql}): N0
 * 199,9000 / N3 44,9636 -> 199.90 / 44.96 na borda de 2 casas. DIFERENCA
 * DELIBERADA em relacao ao dados-demo.sql: este fixture PREENCHE
 * {@code valor_repasse_previsto} com o repasse esperado exato (secao 2.6,
 * {@link com.plataforma.margem.ConferenciaRepasse}) para os dois pedidos -
 * o dados-demo.sql deixa essa coluna NULL, o que (lido o codigo de
 * ConferenciaRepasse.conferir) adiciona a lacuna #17
 * ("repasse_previsto_ausente", vies INDETERMINADA) e faria QUALQUER pedido
 * sair com rotulo INDETERMINADA, nunca CALCULADA/COM_TETO - contradizendo
 * o proprio comentario do script. Sem preencher esse campo aqui, o cenario
 * pedido para provar "rotulo vem CALCULADA"/"rotulo vem COM_TETO" nao
 * teria como funcionar contra o motor real. Isto e relatado como achado no
 * resumo final da tarefa, nao corrigido silenciosamente no dados-demo.sql
 * (fora do escopo deste arquivo).
 *
 * <h2>ACHADO: {@code POST /api/login} nao deixa sessao persistida (achado
 * critico, nao corrigido aqui - ver o resumo final da tarefa)</h2>
 * {@link #loginComCredenciaisValidasEstabeleceSessaoComCookie()} fica
 * VERMELHO contra o codigo atual: a resposta e 204 (autenticacao aceita),
 * mas {@code request.getSession(false)} volta {@code null} logo depois, e
 * nenhum cookie {@code JSESSIONID} e emitido. Causa raiz, confirmada por
 * leitura + desmontagem do bytecode de
 * {@code AbstractAuthenticationProcessingFilter} (spring-security-web
 * 6.3.10): {@link com.plataforma.autenticacao.FiltroLoginJson} e
 * construido com {@code new FiltroLoginJson(...)}
 * ({@link com.plataforma.autenticacao.ConfiguracaoSeguranca#cadeiaDeSeguranca}),
 * NUNCA atraves do DSL {@code http.formLogin(...)} - e e so atraves desse
 * DSL que o Spring Security troca o {@code securityContextRepository}
 * padrao do filtro pelo mesmo {@code HttpSessionSecurityContextRepository}
 * compartilhado que o resto da cadeia usa. Sem essa troca, o filtro fica
 * com o default de fabrica da classe-mae,
 * {@code RequestAttributeSecurityContextRepository} - que grava o
 * {@code SecurityContext} so como ATRIBUTO DA REQUISICAO ATUAL, nunca na
 * {@code HttpSession}. Resultado: {@code successfulAuthentication()} roda,
 * o login "da certo", mas o {@code SecurityContext} autenticado morre no
 * fim da MESMA requisicao - nenhuma chamada seguinte, nem com o mesmo
 * cookie de sessao (que sequer chega a existir), estaria autenticada.
 * Isto nao e peculiaridade do MockMvc/ambiente mock: e o mesmo objeto
 * {@code securityContextRepository} usado em producao, contra Tomcat real.
 * <p>
 * PARA NAO DEIXAR OITO TESTES A MAIS PRESOS A ESTE UNICO BUG (a fila
 * inteira depende de "estar logado" como pre-condicao, e cada um deles
 * prova uma coisa DIFERENTE do resto do contrato), os testes 3 a 7 usam
 * {@link #sessaoDoDonoA()} em vez de um login de verdade:
 * um {@code MockHttpSession} com o {@code SecurityContext} gravado
 * diretamente na chave {@code HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY}
 * - exatamente o que {@code HttpSessionSecurityContextRepository.saveContext}
 * teria feito se o filtro de login estivesse fiado corretamente. Isto e um
 * DESVIO DELIBERADO E ISOLADO, documentado onde acontece; ele NAO esconde o
 * bug (que continua provado, vermelho, isolado num unico teste) e permite
 * que o resto do arquivo continue validando o resto do contrato.
 *
 * <h2>Como verificar que este teste nao e decorativo</h2>
 * Tres sabotagens concretas, cada uma verificada por LEITURA do codigo
 * antes de ser listada aqui (nao por suposicao):
 * <ol>
 *   <li><b>Em {@link com.plataforma.comum.web.ConfiguracaoJackson}, remova
 *       {@code modulo.addSerializer(BigDecimal.class, ...)}</b> (ou pare de
 *       registrar o bean {@code moduloDinheiroComoTexto}). Efeito
 *       verificado no proprio Javadoc da classe: sem o serializador
 *       customizado, Jackson volta ao padrao e emite {@code BigDecimal}
 *       como NUMERO JSON literal. {@link #margemPeriodoPedidoCalculadaTrazValoresMonetariosComoStringComDuasCasas()}
 *       fica vermelho: {@code jsonPath("$.faturamentoBrutoN0").isString()}
 *       deixa de casar (o valor passa a ser {@code NUMBER}, nao
 *       {@code STRING}) - exatamente o ponto que a decisao 0026 exige e que
 *       uma asserção so de VALOR (sem checar o tipo) deixaria passar.</li>
 *   <li><b>Em {@link com.plataforma.autenticacao.RespostaSessao}, acrescente
 *       um componente {@code senhaHash} ao record e, em
 *       {@link com.plataforma.autenticacao.ServicoSessao#sessaoAtual}, passe
 *       {@code usuario.getPassword()}</b> (o metodo que
 *       {@link com.plataforma.autenticacao.UsuarioAutenticado} ja expoe,
 *       porque {@code DaoAuthenticationProvider} precisa dele - ver o
 *       Javadoc daquela classe). Efeito: {@link #sessaoAutenticadaDevolveNomePapelTenantENuncaSenhaHash()}
 *       fica vermelho - {@code jsonPath("$.senhaHash").doesNotExist()}
 *       passa a encontrar o hash BCrypt no corpo de
 *       {@code GET /api/sessao}. Records do Java sao serializados por
 *       Jackson usando o nome do componente como chave (mesmo mecanismo que
 *       ja faz {@code RespostaCanal}/{@code RespostaMargemPeriodo} baterem
 *       campo a campo com {@code frontend/src/lib/api/tipos.ts}), entao o
 *       campo apareceria com esse nome exato.</li>
 *   <li><b>Isolamento pela API ({@code canalId} de outro tenant) - a
 *       COMBINACAO que efetivamente derruba, no MESMO padrao ja provado por
 *       {@code IsolamentoMargemTest#buscaPorCanalEPeriodoComCanalIdDeOutroTenantDevolveListaVazia}:</b>
 *       (a) converta
 *       {@code RepositorioPedido.findByCanalIdAndFeitoEmGreaterThanEqualAndFeitoEmLessThan}
 *       para {@code @Query(nativeQuery = true)} com o MESMO texto (perde o
 *       predicado automatico de {@code @TenantId} - camada 3 da decisao
 *       0007), E (b) troque o {@code USING} da policy {@code pedido_select}
 *       (V008) para {@code USING (true)} (derruba a camada 2, RLS). SO AS
 *       DUAS JUNTAS derrubam
 *       {@link #margemPeriodoComCanalIdDeOutroTenantDevolveVazioNuncaDadoDeB()}:
 *       fazer so (a) NAO deixa nenhum teste vermelho, porque o GUC
 *       {@code app.tenant_id} continua sendo setado em toda conexao do pool
 *       (decisao 0007) e a policy RLS (camada 2, intacta) segura sozinha -
 *       o mesmo raciocinio, verificado por leitura, que
 *       {@code IsolamentoMargemTest} ja registra sobre esta MESMA consulta.
 *       Repetir aqui, pela API HTTP em vez de pelo repositorio direto, prova
 *       que nenhuma camada do controller/servico (validacao de parametro,
 *       serializacao) esconde ou disfarca uma quebra de isolamento nas
 *       camadas de baixo - e o "caminho que um atacante real usaria",
 *       citado na tarefa, de ponta a ponta.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratoApiTest {

    private static final String SENHA_DONO_A = "SenhaForteDeTeste-123!";

    /**
     * Marcador que a pergunta de teste embute no texto para escolher a
     * intenção, e marcadores para os dois parâmetros. Ver o Javadoc de
     * {@link ConfiguracaoDubleDeModeloDeLinguagem} sobre por que este
     * arquivo PRECISA de um dublê aqui, em vez do {@code ModeloHeuristico}
     * real que roda em produção.
     */
    private static final String MARCADOR_INTENCAO = "__INTENCAO";
    private static final Pattern PADRAO_INTENCAO = Pattern.compile("__INTENCAO\\[(.*?)\\]__");
    private static final Pattern PADRAO_CANAL = Pattern.compile("__CANAL\\[(.*?)\\]__");
    private static final Pattern PADRAO_PERIODO_RELATIVO = Pattern.compile("__PERIODO_RELATIVO\\[(.*?)\\]__");

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.configurarPropriedades(registro);
    }

    /**
     * Substitui {@code ModeloHeuristico} (o único {@link PortaModeloLinguagem}
     * registrado em produção) por um dublê determinístico, SÓ para os casos
     * de {@code POST /api/pergunta} deste arquivo.
     *
     * <h2>Por que isto é necessário, e não um atalho preguiçoso</h2>
     * {@code ModeloHeuristico} hoje extrai {@code canal} e
     * {@code periodoRelativo}, mas só casando contra um conjunto FECHADO de
     * expressões de período e contra os canais REALMENTE cadastrados do
     * tenant (ver o Javadoc dele) - não dá para escrever, no texto da
     * pergunta, um valor arbitrário de canal ou período e ter certeza de
     * qual vai casar sem depender do estado exato do seed de canal deste
     * teste. Um teste de CONTRATO (decisão 0026, o caso mais importante
     * desta seção) precisa de controle total e explícito sobre o parâmetro
     * que chega em {@link ServicoPergunta}, independente de qualquer canal
     * cadastrado - por isso o dublê aqui continua sendo o {@code Primary},
     * mesmo com o {@code ModeloHeuristico} real já sabendo extrair.
     *
     * O dublê é PROPOSITALMENTE bobo: só reconhece marcadores literais
     * ({@code __INTENCAO[...]__}, {@code __CANAL[...]__},
     * {@code __PERIODO_RELATIVO[...]__}) que os próprios testes escrevem no
     * texto da pergunta - nenhuma tentativa de interpretar português. Ele
     * prova o CONTRATO (rota, serialização, formato de erro, isolamento),
     * não a qualidade de interpretação de linguagem natural - essa
     * responsabilidade é de {@code ModeloHeuristico}/{@code ModeloAnthropic}
     * e dos testes deles próprios ({@code ModeloHeuristicoTest}).
     */
    @TestConfiguration
    static class ConfiguracaoDubleDeModeloDeLinguagem {

        @Bean
        @Primary
        PortaModeloLinguagem modeloDeLinguagemDeContrato() {
            return (perguntaDoUsuario, catalogo) -> {
                Matcher intencaoMatcher = PADRAO_INTENCAO.matcher(perguntaDoUsuario);
                if (!perguntaDoUsuario.contains(MARCADOR_INTENCAO) || !intencaoMatcher.find()) {
                    // Nenhum marcador reconhecido: mesmo comportamento de
                    // "nenhuma intenção identificada" do ModeloHeuristico
                    // real - confiança baixa, cai em RECUSA.
                    return new IntencaoDetectada("NENHUMA_INTENCAO_IDENTIFICADA_TESTE", Map.of(),
                            new BigDecimal("0.05"));
                }

                Map<String, String> parametros = new HashMap<>();
                Matcher canalMatcher = PADRAO_CANAL.matcher(perguntaDoUsuario);
                if (canalMatcher.find()) {
                    parametros.put("canal", canalMatcher.group(1));
                }
                Matcher periodoMatcher = PADRAO_PERIODO_RELATIVO.matcher(perguntaDoUsuario);
                if (periodoMatcher.find()) {
                    parametros.put("periodoRelativo", periodoMatcher.group(1));
                }

                return new IntencaoDetectada(intencaoMatcher.group(1), parametros, new BigDecimal("0.95"));
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RepositorioCanal repositorioCanal;

    @Autowired
    private RepositorioProduto repositorioProduto;

    @Autowired
    private RepositorioVariacao repositorioVariacao;

    @Autowired
    private RepositorioTaxaCanal repositorioTaxaCanal;

    @Autowired
    private RepositorioPedido repositorioPedido;

    @Autowired
    private RepositorioItemPedido repositorioItemPedido;

    @Autowired
    private RepositorioCusto repositorioCusto;

    @Autowired
    private RepositorioDevolucao repositorioDevolucao;

    // Estado do cenario, montado uma unica vez em iniciarCenario() e
    // reaproveitado (SOMENTE LEITURA daqui em diante) por todos os testes -
    // ATUALIZADO na tarefa 31/32: os testes de escopo de canal SAO
    // excecao (POST altera estado por definicao). Cada um deles cria e
    // usa um canal PROPRIO (nunca um da fixture compartilhada aqui) -
    // exatamente para que esta afirmacao continue valendo para TODOS OS
    // OUTROS testes: nenhum @Test muta um dado que outro @Test tambem lê,
    // entao compartilhar a fixture entre eles (via @TestInstance(PER_CLASS))
    // continua seguro.
    private String emailDonoA;
    private UUID usuarioDonoAId;
    private String nomeDonoA;
    private UUID tenantAId;
    private UUID tenantBId;
    private UUID canalAId;
    private String nomeCanalA;
    private UUID canalBId;
    private String skuVariacaoSemCusto;
    private String idExternoEventoErro;
    private UUID pedidoCalculadaId;
    private OffsetDateTime feitoEmCalculada;
    private UUID pedidoComTetoId;
    private OffsetDateTime feitoEmComTeto;
    private UUID pedidoDevolucaoId;

    // Escopo declarado (tarefa 31/32, decisao 0033). canalAId vira
    // FONTE_PRIMARIA (reaproveita os dois pedidos ja fixturados: soma
    // consolidada com canalSecundarioId prova soma ENTRE canais de
    // verdade). canalSecundarioId e um segundo FONTE_PRIMARIA sem pedido
    // proprio (contribui zero, so para provar que DOIS canais entraram na
    // soma). canalEspelhoId e ESPELHO de canalAId (par para o caminho de
    // recusa). canalNaoDeclaradoId nunca e declarado (o outro caminho de
    // recusa). Todos declarados UMA VEZ aqui, nunca por um @Test - os
    // testes de leitura desta classe nao escrevem dado (ver o Javadoc da
    // classe); so declararEscopoFontePrimariaAtualizaCanal escreve, e usa
    // um canal PROPRIO, criado dentro do proprio teste.
    private UUID canalSecundarioId;
    private UUID canalEspelhoId;
    private UUID canalNaoDeclaradoId;

    @BeforeAll
    void iniciarCenario() throws SQLException {
        String marcador = UUID.randomUUID().toString().substring(0, 8);

        tenantAId = criarTenant("contrato-a-" + marcador);
        tenantBId = criarTenant("contrato-b-" + marcador);

        emailDonoA = "dono-" + marcador + "@contrato.teste";
        nomeDonoA = "Dona da Loja de Teste";
        usuarioDonoAId = criarUsuario(tenantAId, emailDonoA, SENHA_DONO_A, nomeDonoA, "DONO");

        nomeCanalA = "Canal Contrato A " + marcador;
        canalAId = criarCanal(tenantAId, nomeCanalA,
                "cred-secreta-nunca-deveria-vazar-" + marcador);
        canalBId = criarCanal(tenantBId, "Canal Contrato B " + marcador, "cred-do-tenant-b-" + marcador);

        UUID produtoId = criarProduto(tenantAId, "Camiseta Basica Algodao " + marcador);
        UUID variacaoComCustoId = criarVariacao(tenantAId, produtoId, "CAM-BAS-P-AZUL-" + marcador,
                new BigDecimal("82.5000"));
        skuVariacaoSemCusto = "CAM-BAS-M-PRETA-" + marcador;
        UUID variacaoSemCustoId = criarVariacao(tenantAId, produtoId, skuVariacaoSemCusto, null);

        criarTaxaComissao(tenantAId, canalAId, new BigDecimal("0.130000"));

        // --------------------------------------------------------------
        // Pedido 1 - replica o exemplo numerico da secao 2.5 do documento
        // fiscal (mesmos sete custos de infra/dados-demo.sql). valorRepasse
        // e o repasse ESPERADO exato (ver Javadoc da classe: sem isto o
        // pedido sairia INDETERMINADA, nunca CALCULADA).
        // --------------------------------------------------------------
        feitoEmCalculada = OffsetDateTime.now().minusDays(10);
        pedidoCalculadaId = criarPedido(tenantAId, canalAId, "ML-CONTRATO-CALC-" + marcador, StatusPedido.ENTREGUE,
                feitoEmCalculada, new BigDecimal("199.9000"), new BigDecimal("149.0130"));
        pedidoDevolucaoId = pedidoCalculadaId;
        UUID itemCalculadaId = criarItemPedido(tenantAId, pedidoCalculadaId, variacaoComCustoId,
                "CAM-BAS-P-AZUL-" + marcador, "Camiseta Basica Algodao P Azul", new BigDecimal("199.9000"));
        criarCusto(tenantAId, pedidoCalculadaId, itemCalculadaId, NaturezaCusto.MERCADORIA,
                new BigDecimal("82.5000"), feitoEmCalculada, false);
        criarCusto(tenantAId, pedidoCalculadaId, null, NaturezaCusto.COMISSAO_CANAL,
                new BigDecimal("25.9870"), feitoEmCalculada, false);
        criarCusto(tenantAId, pedidoCalculadaId, null, NaturezaCusto.FRETE,
                new BigDecimal("24.9000"), feitoEmCalculada, false);
        criarCusto(tenantAId, pedidoCalculadaId, null, NaturezaCusto.EMBALAGEM,
                new BigDecimal("1.8000"), feitoEmCalculada, true);
        criarCusto(tenantAId, pedidoCalculadaId, null, NaturezaCusto.IMPOSTO,
                new BigDecimal("13.4494"), feitoEmCalculada, true);
        criarCusto(tenantAId, pedidoCalculadaId, null, NaturezaCusto.TAXA_ANTECIPACAO,
                BigDecimal.ZERO, feitoEmCalculada, false);
        criarCusto(tenantAId, pedidoCalculadaId, null, NaturezaCusto.ADS,
                new BigDecimal("6.3000"), feitoEmCalculada, true);

        // --------------------------------------------------------------
        // Pedido 2 - sai COM_TETO: falta MERCADORIA (variacao sem custo
        // cadastrado) e falta IMPOSTO (regime nao configurado) - as duas
        // lacunas do catalogo tem vies SUPERESTIMA_MARGEM (ver
        // CatalogoLacunas), entao RotuloTeto.calcular fecha em COM_TETO, nao
        // INDETERMINADA. valorRepasse tambem preenchido com o esperado
        // exato, pelo mesmo motivo do pedido 1.
        // --------------------------------------------------------------
        feitoEmComTeto = OffsetDateTime.now().minusDays(5);
        pedidoComTetoId = criarPedido(tenantAId, canalAId, "ML-CONTRATO-TETO-" + marcador, StatusPedido.ENVIADO,
                feitoEmComTeto, new BigDecimal("149.9000"), new BigDecimal("107.9130"));
        criarItemPedido(tenantAId, pedidoComTetoId, variacaoSemCustoId, skuVariacaoSemCusto,
                "Camiseta Basica Algodao M Preta", new BigDecimal("149.9000"));
        criarCusto(tenantAId, pedidoComTetoId, null, NaturezaCusto.COMISSAO_CANAL,
                new BigDecimal("19.4870"), feitoEmComTeto, false);
        criarCusto(tenantAId, pedidoComTetoId, null, NaturezaCusto.FRETE,
                new BigDecimal("22.5000"), feitoEmComTeto, false);

        // Evento de ingestao em ERRO (fila do analista) e devolucao aberta
        // (fila do analista, ligada ao pedido CALCULADA - mesmo cenario de
        // infra/dados-demo.sql).
        idExternoEventoErro = "ML-CONTRATO-QUEBRADO-" + marcador;
        criarEventoIngeridoComErro(tenantAId, canalAId, idExternoEventoErro);
        criarDevolucaoAberta(tenantAId, pedidoCalculadaId);

        // --------------------------------------------------------------
        // Escopo declarado (tarefa 31/32, decisao 0033) - ver o comentario
        // do campo acima sobre por que isto e feito aqui, uma vez so.
        // --------------------------------------------------------------
        declararFontePrimariaComoTenantA(canalAId);
        canalSecundarioId = criarCanal(tenantAId, "Canal Secundario Consolidado " + marcador,
                "cred-secundario-" + marcador);
        declararFontePrimariaComoTenantA(canalSecundarioId);
        canalEspelhoId = criarCanal(tenantAId, "Canal Espelho Consolidado " + marcador,
                "cred-espelho-" + marcador);
        declararEspelhoComoTenantA(canalEspelhoId, canalAId);
        canalNaoDeclaradoId = criarCanal(tenantAId, "Canal Nao Declarado " + marcador,
                "cred-nao-declarado-" + marcador);
    }

    // ==================================================================
    // 1. Sem sessao, tudo e 401 - e actuator/health continua aberto
    // ==================================================================

    @Test
    void semSessaoTodasAsRotasProtegidasDevolvem401() throws Exception {
        String[] rotasProtegidasGet = {
                "/api/sessao",
                "/api/canais",
                "/api/painel/gestor",
                "/api/painel/analista"
        };

        for (String rota : rotasProtegidasGet) {
            mockMvc.perform(get(rota))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.erro").value("nao_autenticado"))
                    .andExpect(jsonPath("$.mensagem").isString());
        }

        // /api/pergunta e /api/margem/periodo sao POST (ver o Javadoc de
        // PerguntaController e, para margem, a decisao 0034), entao precisam
        // de corpo e content-type - sem sessao, o
        // FiltroTenant/PontoEntradaNaoAutenticado barra ANTES de qualquer
        // validacao de corpo rodar, entao um corpo minimo valido basta para
        // isolar o que este teste quer provar (401, nunca 400).
        mockMvc.perform(post("/api/pergunta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPergunta("Quais canais eu tenho cadastrados?")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("nao_autenticado"))
                .andExpect(jsonPath("$.mensagem").isString());

        mockMvc.perform(post("/api/margem/periodo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMargemPeriodo(OffsetDateTime.now().minusDays(1), OffsetDateTime.now(),
                                UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("nao_autenticado"))
                .andExpect(jsonPath("$.mensagem").isString());

        // Tarefa 31/32: as duas rotas novas, mesmo padrao acima (POST,
        // corpo minimo valido so para isolar "401", nunca "400").
        mockMvc.perform(post("/api/margem/periodo/consolidado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMargemConsolidada(OffsetDateTime.now().minusDays(1), OffsetDateTime.now(),
                                null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("nao_autenticado"))
                .andExpect(jsonPath("$.mensagem").isString());

        mockMvc.perform(post("/api/canais/" + UUID.randomUUID() + "/escopo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeclaracaoEscopo("FONTE_PRIMARIA", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("nao_autenticado"))
                .andExpect(jsonPath("$.mensagem").isString());
    }

    @Test
    void actuatorHealthContinuaAbertoSemAutenticacao() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ==================================================================
    // 2. Login: funciona, devolve sessao, e falha nunca distingue email
    //    inexistente de senha errada
    // ==================================================================

    /**
     * LIMITE DESTE TESTE, dito para nao gerar confianca falsa:
     * ele NAO prova que o cabecalho {@code Set-Cookie: JSESSIONID} sai no
     * fio, nem os atributos HttpOnly/SameSite/Secure do cookie.
     *
     * O MockMvc nao tem container: ele carrega a sessao numa
     * MockHttpSession e nunca escreve o Set-Cookie que o Tomcat
     * escreveria. Verificar o cookie aqui so produziria um teste que
     * falha sempre, por um motivo que nao e defeito do sistema.
     *
     * O que da para provar aqui, e e o que importa para o contrato:
     * a sessao do lado do SERVIDOR e criada, e ela carrega o usuario
     * autenticado - e sobre ela que todos os outros casos deste arquivo
     * se apoiam para chamar endpoint protegido.
     *
     * Os atributos do cookie ficam cobertos por inspecao da configuracao
     * (application.yml) e so serao observaveis de verdade quando a
     * aplicacao subir num container - hoje bloqueado nesta maquina por
     * uma falha de loopback do Tomcat, registrada em docs/PENDENCIAS.md.
     */
    @Test
    void loginComCredenciaisValidasEstabeleceSessaoNoServidor() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(emailDonoA, SENHA_DONO_A)))
                .andExpect(status().isNoContent())
                .andReturn();

        assertNotNull(resultado.getRequest().getSession(false),
                "login bem-sucedido deveria estabelecer uma sessao HTTP do lado do servidor");
    }

    @Test
    void loginComSenhaErradaESenhaComEmailInexistenteDevolvemAMesmaResposta() throws Exception {
        MvcResult senhaErrada = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(emailDonoA, "senha-completamente-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("credenciais_invalidas"))
                .andReturn();

        MvcResult emailInexistente = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin("fantasma-" + UUID.randomUUID() + "@contrato.teste", "qualquer-senha")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("credenciais_invalidas"))
                .andReturn();

        String corpoSenhaErrada = senhaErrada.getResponse().getContentAsString();
        String corpoEmailInexistente = emailInexistente.getResponse().getContentAsString();

        assertEquals(corpoSenhaErrada, corpoEmailInexistente,
                "senha errada e e-mail inexistente precisam devolver EXATAMENTE a mesma resposta - "
                        + "senao o endpoint vira oraculo publico de contas (armadilha 5 da V014)");
        assertFalse(corpoSenhaErrada.toLowerCase().contains("$2a$"),
                "corpo de erro de login nunca pode conter fragmento de hash BCrypt");
        assertFalse(corpoSenhaErrada.toLowerCase().contains("hash"),
                "corpo de erro de login nunca deveria mencionar a palavra hash");
    }

    // ==================================================================
    // 3. GET /api/sessao autenticado
    // ==================================================================

    @Test
    void sessaoAutenticadaDevolveNomePapelTenantENuncaSenhaHash() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();

        mockMvc.perform(get("/api/sessao").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailDonoA))
                .andExpect(jsonPath("$.nome").value("Dona da Loja de Teste"))
                .andExpect(jsonPath("$.papel").value("DONO"))
                .andExpect(jsonPath("$.tenantId").value(tenantAId.toString()))
                .andExpect(jsonPath("$.tenantNome").isString())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.senha_hash").doesNotExist());
    }

    // ==================================================================
    // 4. GET /api/canais
    // ==================================================================

    @Test
    void canaisListaOCanalDoTenantSemExporChaveCredencial() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();

        MvcResult resultado = mockMvc.perform(get("/api/canais").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(canalAId.toString()))
                .andExpect(jsonPath("$[0].codigo").isString())
                .andExpect(jsonPath("$[0].nome").isString())
                .andExpect(jsonPath("$[0].tipo").value("MERCADO_LIVRE"))
                .andExpect(jsonPath("$[0].categoria").value("MARKETPLACE"))
                .andExpect(jsonPath("$[0].ativo").value(true))
                .andExpect(jsonPath("$[0].chaveCredencial").doesNotExist())
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertFalse(corpo.contains("cred-secreta-nunca-deveria-vazar"),
                "a credencial do canal jamais pode aparecer em qualquer lugar do corpo da resposta");
    }

    // ==================================================================
    // 5. POST /api/margem/periodo (decisao 0034) - o ponto mais importante
    //    do arquivo: todo valor monetario e STRING no JSON cru, nunca numero
    // ==================================================================

    @Test
    void margemPeriodoPedidoCalculadaTrazValoresMonetariosComoStringComDuasCasas() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        OffsetDateTime inicio = feitoEmCalculada.minusMinutes(1);
        OffsetDateTime fim = feitoEmCalculada.plusMinutes(1);

        mockMvc.perform(post("/api/margem/periodo")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMargemPeriodo(inicio, fim, canalAId)))
                .andExpect(status().isOk())
                // O ponto central: isString(), NUNCA so o valor - conferir
                // so o valor deixaria passar um numero JSON com o mesmo
                // texto (ver decisao 0026 e o Javadoc de ConfiguracaoJackson).
                .andExpect(jsonPath("$.faturamentoBrutoN0", org.hamcrest.Matchers.isA(String.class)))
                .andExpect(jsonPath("$.faturamentoBrutoN0").isString())
                .andExpect(jsonPath("$.faturamentoBrutoN0").value("199.90"))
                .andExpect(jsonPath("$.resultadoPeriodoN3").isString())
                .andExpect(jsonPath("$.resultadoPeriodoN3").value("44.96"))
                .andExpect(jsonPath("$.receitaLiquidaN1").isString())
                .andExpect(jsonPath("$.margemContribuicaoN2").isString())
                .andExpect(jsonPath("$.lucroOperacionalN4").isString())
                .andExpect(jsonPath("$.margemContribuicaoPercentual").isString())
                .andExpect(jsonPath("$.margemLiquidaPercentual").isString())
                .andExpect(jsonPath("$.decomposicao[0].valor").isString())
                .andExpect(jsonPath("$.rotulo").value("CALCULADA"))
                .andExpect(jsonPath("$.lacunas").isEmpty())
                // quantidadePedidos e int no backend (nao BigDecimal):
                // continua NUMERO no JSON, a assimetria e proposital.
                .andExpect(jsonPath("$.quantidadePedidos").isNumber())
                .andExpect(jsonPath("$.quantidadePedidos").value(1))
                .andExpect(jsonPath("$.idsPedidoUsados[0]").value(pedidoCalculadaId.toString()));
    }

    @Test
    void margemPeriodoPedidoComTetoTrazLacunasEORotuloCorreto() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        OffsetDateTime inicio = feitoEmComTeto.minusMinutes(1);
        OffsetDateTime fim = feitoEmComTeto.plusMinutes(1);

        mockMvc.perform(post("/api/margem/periodo")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMargemPeriodo(inicio, fim, canalAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamentoBrutoN0").isString())
                .andExpect(jsonPath("$.faturamentoBrutoN0").value("149.90"))
                .andExpect(jsonPath("$.rotulo").value("COM_TETO"))
                .andExpect(jsonPath("$.lacunas").isNotEmpty())
                .andExpect(jsonPath("$.lacunas[?(@.codigo == 'custo_mercadoria_nao_cadastrado')]").exists())
                .andExpect(jsonPath("$.quantidadePedidos").value(1))
                .andExpect(jsonPath("$.idsPedidoUsados[0]").value(pedidoComTetoId.toString()));
    }

    // ==================================================================
    // 6. Paineis do gestor e do analista
    // ==================================================================

    @Test
    void painelGestorMostraOEventoEmErroEOPedidoSemCustoDeMercadoria() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();

        mockMvc.perform(get("/api/painel/gestor").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventosIngestaoComErro").isNumber())
                .andExpect(jsonPath("$.eventosIngestaoComErro", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.pedidosSemCustoMercadoria",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void painelAnalistaMostraEventoErroVariacaoSemCustoEDevolucaoAberta() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();

        mockMvc.perform(get("/api/painel/analista").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventosComErro[?(@.idExterno == '" + idExternoEventoErro + "')]").exists())
                .andExpect(jsonPath("$.variacoesSemCusto[?(@.sku == '" + skuVariacaoSemCusto + "')]").exists())
                .andExpect(jsonPath("$.devolucoesAbertas[?(@.pedidoId == '" + pedidoDevolucaoId + "')]").exists());
    }

    // ==================================================================
    // 7. Isolamento pela API: canalId de outro tenant nunca devolve dado
    //    de B - o caminho que um atacante real usaria
    // ==================================================================

    @Test
    void margemPeriodoComCanalIdDeOutroTenantDevolveVazioNuncaDadoDeB() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        // Janela ampla o bastante para cobrir os dois pedidos de A, para
        // que "veio vazio" nao possa ser explicado por coincidencia de
        // datas - se o isolamento falhasse, um pedido apareceria mesmo
        // assim, so que seria (impossivel, ja que so existe canal B, sem
        // pedido nenhum) o de B.
        OffsetDateTime inicio = OffsetDateTime.now().minusDays(30);
        OffsetDateTime fim = OffsetDateTime.now().plusDays(1);

        mockMvc.perform(post("/api/margem/periodo")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        // canalBId e um canal_id REAL e VALIDO - pertence a
                        // tenant B, nao a A. De proposito: se a resposta
                        // saisse vazia so porque o canal "nao existe", isto
                        // nao provaria isolamento nenhum.
                        .content(corpoMargemPeriodo(inicio, fim, canalBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidadePedidos").value(0))
                .andExpect(jsonPath("$.idsPedidoUsados").isEmpty())
                .andExpect(jsonPath("$.faturamentoBrutoN0").value("0.00"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(pedidoCalculadaId.toString()))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(pedidoComTetoId.toString()))));
    }

    // ==================================================================
    // 8. POST /api/pergunta (tarefa 24) - ver Javadoc de
    //    ConfiguracaoDubleDeModeloDeLinguagem sobre por que estes casos
    //    dependem do dublê de PortaModeloLinguagem, e nao do
    //    ModeloHeuristico real.
    // ==================================================================

    /**
     * (a) Pergunta que casa com margem devolve 200, {@code tipo=RESPOSTA},
     * {@code consultaAuditadaId} preenchido, e TODO valor monetário como
     * STRING no JSON cru (decisão 0026) - mesma técnica {@code isString()}
     * já usada em {@link #margemPeriodoPedidoCalculadaTrazValoresMonetariosComoStringComDuasCasas()}.
     *
     * NUANCE que vale registrar: aqui a garantia de "string" NÃO vem do
     * serializador Jackson de {@code BigDecimal} (
     * {@code ConfiguracaoJackson}, o mecanismo do teste de
     * {@code /api/margem/periodo}) - {@link com.plataforma.pergunta.RespostaPergunta}
     * não carrega nenhum campo {@code BigDecimal}. Ela vem de
     * {@code ServicoPergunta} já ter formatado cada número como
     * {@code String} (via {@code FormatadorDeTexto}) antes de montar
     * {@code NumeroCitado} - ver o Javadoc de {@code RespostaPergunta}. É um
     * mecanismo DIFERENTE chegando na mesma garantia; por isso este caso é
     * testado separadamente, não reaproveitado do teste de margem.
     *
     * Janela ULTIMOS_30_DIAS (em vez de MES_PASSADO) de propósito: os dois
     * pedidos do cenário (calculada = agora-10 dias, com teto = agora-5
     * dias) sempre caem dentro dela, qualquer que seja o dia em que a suíte
     * rodar - MES_PASSADO dependeria de em que dia do mês o teste executa.
     */
    @Test
    void perguntaDeMargemDevolveRespostaComValoresMonetariosComoString() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        String pergunta = "__INTENCAO[MARGEM_DO_PERIODO]__ __CANAL[" + nomeCanalA + "]__ "
                + "__PERIODO_RELATIVO[ULTIMOS_30_DIAS]__ quanto sobrou?";

        mockMvc.perform(post("/api/pergunta")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPergunta(pergunta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("RESPOSTA"))
                .andExpect(jsonPath("$.consultaAuditadaId").isString())
                .andExpect(jsonPath("$.intencao").value("MARGEM_DO_PERIODO"))
                // Filtro JSONPath com predicado ("$..[?(@.nome == 'x')].valor")
                // sempre devolve um ARRAY (mesmo casando uma linha so) -
                // Matchers.contains(...) e quem confere "exatamente um
                // elemento, e esse elemento e String" ao mesmo tempo; usar
                // isString() aqui compararia tipo errado (array, nao string).
                // Os dois pedidos do cenario usam canalA e caem nos ultimos
                // 30 dias - contagem exata, nao "pelo menos um".
                .andExpect(jsonPath("$.numeros[?(@.nome == 'Pedidos no período')].valor")
                        .value(org.hamcrest.Matchers.contains("2")))
                .andExpect(jsonPath("$.numeros[?(@.nome == 'Faturamento bruto (N0)')].valor")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.instanceOf(String.class))))
                .andExpect(jsonPath("$.numeros[?(@.nome == 'Resultado do pedido (N3)')].valor")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.instanceOf(String.class))))
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("R$")))
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    /**
     * (b) Pergunta fora do domínio (nenhum marcador reconhecido pelo dublê,
     * equivalente a "nenhuma intenção identificada" do modelo real) devolve
     * {@code tipo=RECUSA}, com {@code perguntasQueSeiResponder} não vazia -
     * a recusa é caminho de primeira classe (decisão 0030), nunca um erro
     * HTTP.
     */
    @Test
    void perguntaForaDoDominioDevolveRecusaComSugestoesDePergunta() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();

        mockMvc.perform(post("/api/pergunta")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPergunta("Qual é a capital da Bulgária?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("RECUSA"))
                .andExpect(jsonPath("$.consultaAuditadaId").isString())
                .andExpect(jsonPath("$.perguntasQueSeiResponder").isNotEmpty())
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    /**
     * (c) Corpo com {@code texto} vazio devolve 400 no formato
     * {@link com.plataforma.comum.web.ErroApi} - {@code @Valid} em
     * {@code RequisicaoPergunta} barra na borda HTTP, antes de
     * {@code ServicoPergunta.responder} rodar (ver
     * {@code TratadorGlobalDeErros.tratarCorpoInvalido}).
     */
    @Test
    void perguntaComTextoVazioDevolve400NoFormatoErroApi() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();

        mockMvc.perform(post("/api/pergunta")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPergunta("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("requisicao_invalida"))
                .andExpect(jsonPath("$.mensagem").isString());
    }

    /**
     * (d) A resposta de {@code /api/pergunta} nunca traz {@code tenantId}
     * nem a credencial de canal - mesma checagem de vazamento que
     * {@link #canaisListaOCanalDoTenantSemExporChaveCredencial()} já faz
     * para {@code /api/canais}, repetida aqui porque
     * {@code responderCanaisDisponiveis} monta a resposta de um jeito
     * diferente (texto livre + {@link com.plataforma.pergunta.NumeroCitado}),
     * nao {@code RespostaCanal}).
     */
    @Test
    void perguntaDeCanaisDisponiveisNuncaExpoeTenantIdOuCredencial() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();

        MvcResult resultado = mockMvc.perform(post("/api/pergunta")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPergunta("__INTENCAO[CANAIS_DISPONIVEIS]__ quais canais eu tenho?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("RESPOSTA"))
                .andExpect(jsonPath("$.tenantId").doesNotExist())
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertFalse(corpo.contains("cred-secreta-nunca-deveria-vazar"),
                "a credencial do canal jamais pode aparecer na resposta de /api/pergunta");
        assertFalse(corpo.contains(tenantAId.toString()),
                "o id do tenant jamais pode aparecer na resposta de /api/pergunta");
    }

    // ==================================================================
    // 9. POST /api/canais/{id}/escopo (tarefa 31, decisao 0033)
    // ==================================================================

    /**
     * Caminho feliz: declara FONTE_PRIMARIA num canal PROPRIO deste teste
     * (nunca um da fixture compartilhada - ver o comentario dos campos
     * canalSecundarioId&amp;cia: nenhum outro teste desta classe pode
     * depender do estado de um canal que um @Test mutou).
     */
    @Test
    void declararEscopoFontePrimariaAtualizaCanal() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        UUID canalProprioId = criarCanal(tenantAId, "Canal Declaracao Teste " + UUID.randomUUID(),
                "cred-declaracao-teste");

        mockMvc.perform(post("/api/canais/" + canalProprioId + "/escopo")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeclaracaoEscopo("FONTE_PRIMARIA", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(canalProprioId.toString()))
                .andExpect(jsonPath("$.escopoDeclarado").value("FONTE_PRIMARIA"))
                .andExpect(jsonPath("$.espelhaCanalId").doesNotExist())
                .andExpect(jsonPath("$.escopoDeclaradoEm").isString())
                .andExpect(jsonPath("$.escopoDeclaradoPor").doesNotExist());
    }

    /**
     * Espelho de espelho (tarefa 31): declarar um canal como ESPELHO de
     * {@code canalEspelhoId} (que ja e ESPELHO de {@code canalAId}) tem
     * que ser recusado - 409, formato {@code ErroApi}, mensagem nomeando
     * a cadeia.
     */
    @Test
    void declararEspelhoDeUmEspelhoDevolve409ComACadeiaNaMensagem() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        UUID canalProprioId = criarCanal(tenantAId, "Canal Cadeia Teste " + UUID.randomUUID(),
                "cred-cadeia-teste");

        mockMvc.perform(post("/api/canais/" + canalProprioId + "/escopo")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDeclaracaoEscopo("ESPELHO", canalEspelhoId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("cadeia_de_espelho_invalida"))
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString(canalEspelhoId.toString())));
    }

    // ==================================================================
    // 10. POST /api/margem/periodo/consolidado (tarefa 32, decisao 0033)
    // ==================================================================

    /**
     * Caminho feliz: canalAId (FONTE_PRIMARIA, com os dois pedidos da
     * fixture) + canalSecundarioId (FONTE_PRIMARIA, sem pedido - contribui
     * zero) somados. Prova DUAS coisas ao mesmo tempo: a soma bate com o
     * que se sabe dos dois pedidos de canalAId (349.80 = 199.90 + 149.90),
     * e {@code canaisIncluidos} nomeia OS DOIS canais que entraram, nao so
     * o que tinha pedido.
     */
    @Test
    void margemPeriodoConsolidadoSomaDoisCanaisDisjuntosENomeiaOsDois() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        OffsetDateTime inicio = feitoEmCalculada.minusMinutes(1);
        OffsetDateTime fim = feitoEmComTeto.plusMinutes(1);

        mockMvc.perform(post("/api/margem/periodo/consolidado")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMargemConsolidada(inicio, fim, List.of(canalAId, canalSecundarioId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamentoBrutoN0").isString())
                .andExpect(jsonPath("$.faturamentoBrutoN0").value("349.80"))
                .andExpect(jsonPath("$.rotulo").value("COM_TETO"))
                .andExpect(jsonPath("$.quantidadePedidos").value(2))
                .andExpect(jsonPath("$.canaisIncluidos", org.hamcrest.Matchers.containsInAnyOrder(
                        canalAId.toString(), canalSecundarioId.toString())))
                .andExpect(jsonPath("$.idsPedidoUsados", org.hamcrest.Matchers.containsInAnyOrder(
                        pedidoCalculadaId.toString(), pedidoComTetoId.toString())));
    }

    /**
     * Caminho de recusa 1: um dos canais pedidos esta NAO_DECLARADO.
     * Nunca um numero - 409, formato {@code ErroApi}, mensagem nomeando o
     * canal bloqueado.
     */
    @Test
    void margemPeriodoConsolidadoComCanalNaoDeclaradoDevolve409SemSomarNada() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        OffsetDateTime inicio = feitoEmCalculada.minusMinutes(1);
        OffsetDateTime fim = feitoEmComTeto.plusMinutes(1);

        mockMvc.perform(post("/api/margem/periodo/consolidado")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMargemConsolidada(inicio, fim, List.of(canalAId, canalNaoDeclaradoId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("conjunto_de_canais_nao_disjunto"))
                .andExpect(jsonPath("$.mensagem",
                        org.hamcrest.Matchers.containsString(canalNaoDeclaradoId.toString())))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("349.80"))));
    }

    /**
     * Caminho de recusa 2: o par espelho/primaria dentro do MESMO
     * conjunto pedido ({@code canalAId} e FONTE_PRIMARIA,
     * {@code canalEspelhoId} e ESPELHO dele). A mensagem precisa nomear os
     * DOIS lados do par - e o ramo ESPELHADO_POR_OUTRO_DO_CONJUNTO da
     * consulta de disjuncao (ver o Javadoc de
     * {@code RepositorioDisjuncaoDeCanais}) que faz isso.
     */
    @Test
    void margemPeriodoConsolidadoComParEspelhoEPrimariaDevolve409NomeandoOsDoisLados() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        OffsetDateTime inicio = feitoEmCalculada.minusMinutes(1);
        OffsetDateTime fim = feitoEmComTeto.plusMinutes(1);

        mockMvc.perform(post("/api/margem/periodo/consolidado")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMargemConsolidada(inicio, fim, List.of(canalAId, canalEspelhoId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("conjunto_de_canais_nao_disjunto"))
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString(canalAId.toString())))
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString(canalEspelhoId.toString())));
    }

    /**
     * Isolamento pela API: canal de outro tenant na lista de
     * {@code POST /api/margem/periodo/consolidado} vira
     * {@code CANAL_INEXISTENTE} (recusa), nunca soma nem vaza dado de B -
     * mesmo padrao de {@link #margemPeriodoComCanalIdDeOutroTenantDevolveVazioNuncaDadoDeB()},
     * agora para a consulta de disjuncao nativa (SQL cru, sem o predicado
     * automatico de {@code @TenantId} - ver o Javadoc de
     * {@code RepositorioDisjuncaoDeCanais}).
     */
    @Test
    void margemPeriodoConsolidadoComCanalIdDeOutroTenantDevolve409ComoCanalInexistente() throws Exception {
        MockHttpSession sessao = sessaoDoDonoA();
        OffsetDateTime inicio = feitoEmCalculada.minusMinutes(1);
        OffsetDateTime fim = feitoEmComTeto.plusMinutes(1);

        mockMvc.perform(post("/api/margem/periodo/consolidado")
                        .session(sessao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMargemConsolidada(inicio, fim, List.of(canalAId, canalBId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("conjunto_de_canais_nao_disjunto"))
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString(canalBId.toString())))
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("nao existe")));
    }

    // ==================================================================
    // Auxiliares - autenticacao
    // ==================================================================

    private MockHttpSession autenticarComoDonoA() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(emailDonoA, SENHA_DONO_A)))
                .andExpect(status().isNoContent())
                .andReturn();
        MockHttpSession sessao = (MockHttpSession) resultado.getRequest().getSession(false);
        assertNotNull(sessao, "login deveria ter estabelecido uma sessao HTTP");
        return sessao;
    }

    /**
     * Desvio DELIBERADO em torno do bug documentado no Javadoc da classe
     * ({@code POST /api/login} nao persiste sessao, porque
     * {@code FiltroLoginJson} e construido a mao e fica com
     * {@code RequestAttributeSecurityContextRepository}, nunca trocado pelo
     * {@code HttpSessionSecurityContextRepository} que o resto da cadeia
     * usa). Monta a MESMA coisa que
     * {@code HttpSessionSecurityContextRepository.saveContext} teria
     * gravado se o login persistisse - {@link SecurityContextImpl} com a
     * {@link UsernamePasswordAuthenticationToken} do
     * {@link UsuarioAutenticado}, na chave
     * {@link HttpSessionSecurityContextRepository#SPRING_SECURITY_CONTEXT_KEY} -
     * para que {@code FiltroTenant} (que so olha
     * {@code SecurityContextHolder}/sessao, nunca o mecanismo que colocou o
     * dado la) autentique a requisicao normalmente daqui em diante.
     * {@code usuarioDonoAId} precisa ser uma linha REAL de {@code usuario}
     * (nao um UUID qualquer) porque {@code FiltroTenant} revalida
     * {@code existsByIdAndAtivoTrue} contra o banco a cada requisicao
     * (armadilha 6 da V014) - um id inventado cairia em
     * {@code sessao_invalida} (401) tao previsivelmente quanto um id real
     * cairia em sucesso, entao usar o id de verdade e o que faz este atalho
     * exercitar a MESMA revalidacao que a producao faz, so pulando a parte
     * que esta comprovadamente quebrada (persistir a sessao no login).
     */
    /**
     * Faz login DE VERDADE e devolve a sessao resultante.
     *
     * Esta versao substituiu um atalho que montava o SecurityContext a
     * mao. O atalho existiu por um motivo legitimo e temporario: quando
     * este arquivo foi escrito, o login estava QUEBRADO - o
     * {@code FiltroLoginJson} nao recebia um
     * {@code SecurityContextRepository}, entao a autenticacao morria no
     * fim da propria requisicao. Sem o atalho, os outros casos ficariam
     * todos vermelhos por um unico defeito e nao provariam nada.
     *
     * Corrigido o defeito (ver ConfiguracaoSeguranca), o atalho passou a
     * ser divida: ele PULAVA justamente o pedaco que quebrou. Um teste de
     * contrato que nao usa o login real nao prova o contrato do login.
     * Agora cada caso autenticado atravessa o fluxo inteiro - filtro de
     * login, verificacao de senha, criacao de sessao, FiltroTenant,
     * ContextoTenant, RLS.
     */
    private MockHttpSession fazerLogin(String email, String senha) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(email, senha)))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession sessao = (MockHttpSession) resultado.getRequest().getSession(false);
        assertNotNull(sessao, "o login precisa criar sessao para os casos autenticados rodarem");
        return sessao;
    }

    private MockHttpSession sessaoDoDonoA() throws Exception {
        return fazerLogin(emailDonoA, SENHA_DONO_A);
    }

    private String corpoLogin(String email, String senha) throws Exception {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("email", email);
        corpo.put("senha", senha);
        return objectMapper.writeValueAsString(corpo);
    }

    private String corpoPergunta(String texto) throws Exception {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("texto", texto);
        return objectMapper.writeValueAsString(corpo);
    }

    /**
     * Corpo de {@code POST /api/margem/periodo} (decisao 0034 - os
     * parametros saem da query string e vao para o corpo). Mesmo formato de
     * {@link com.plataforma.margem.RequisicaoMargemPeriodo}.
     */
    private String corpoMargemPeriodo(OffsetDateTime inicio, OffsetDateTime fim, UUID canalId) throws Exception {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("inicio", inicio.toString());
        corpo.put("fim", fim.toString());
        corpo.put("canalId", canalId.toString());
        return objectMapper.writeValueAsString(corpo);
    }

    /**
     * Corpo de {@code POST /api/margem/periodo/consolidado} (tarefa 32).
     * {@code canais} pode ser {@code null} - omitido do JSON quando for o
     * caso, ao inves de serializar a chave com valor null, para exercitar
     * o mesmo corpo que um cliente real mandaria ao pedir "todos os
     * canais ativos" (ver {@code RequisicaoMargemConsolidada}).
     */
    private String corpoMargemConsolidada(OffsetDateTime inicio, OffsetDateTime fim, List<UUID> canais)
            throws Exception {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("inicio", inicio.toString());
        corpo.put("fim", fim.toString());
        if (canais != null) {
            corpo.put("canais", canais.stream().map(UUID::toString).toList());
        }
        return objectMapper.writeValueAsString(corpo);
    }

    /** Corpo de {@code POST /api/canais/{id}/escopo} (tarefa 31). */
    private String corpoDeclaracaoEscopo(String escopo, UUID espelhaCanalId) throws Exception {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("escopo", escopo);
        if (espelhaCanalId != null) {
            corpo.put("espelhaCanalId", espelhaCanalId.toString());
        }
        return objectMapper.writeValueAsString(corpo);
    }

    // ==================================================================
    // Auxiliares - fixture via repositorio JPA (caminho legitimo, exercita
    // ContextoTenant -> DataSourceComTenant -> RLS -> @TenantId), mesmo
    // padrao de IsolamentoMargemTest#inserirPedidoComoTenant e vizinhos.
    // ==================================================================

    private UUID criarCanal(UUID tenantId, String nomeMarcador, String chaveCredencial) {
        ContextoTenant.definir(tenantId);
        try {
            Canal canal = repositorioCanal.save(new Canal(
                    "canal-" + UUID.randomUUID().toString().substring(0, 8),
                    nomeMarcador, TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE,
                    null, chaveCredencial, null));
            return canal.getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    /**
     * Declara FONTE_PRIMARIA via a MESMA pilha legitima que
     * {@code ServicoEscopoDeCanal} usa (findById + metodo de intencao +
     * save), sempre no tenant A - as chamadas de setup deste arquivo so
     * precisam desse unico tenant.
     */
    private void declararFontePrimariaComoTenantA(UUID canalId) {
        ContextoTenant.definir(tenantAId);
        try {
            Canal canal = repositorioCanal.findById(canalId).orElseThrow();
            canal.declararFontePrimaria(usuarioDonoAId);
            repositorioCanal.save(canal);
        } finally {
            ContextoTenant.limpar();
        }
    }

    private void declararEspelhoComoTenantA(UUID canalId, UUID espelhaCanalId) {
        ContextoTenant.definir(tenantAId);
        try {
            Canal canal = repositorioCanal.findById(canalId).orElseThrow();
            canal.declararEspelhoDe(espelhaCanalId, usuarioDonoAId);
            repositorioCanal.save(canal);
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID criarProduto(UUID tenantId, String titulo) {
        ContextoTenant.definir(tenantId);
        try {
            Produto produto = repositorioProduto.save(new Produto(
                    null, null, titulo, null, null, null, null, null, null));
            return produto.getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID criarVariacao(UUID tenantId, UUID produtoId, String sku, BigDecimal custoUnitarioAtual) {
        ContextoTenant.definir(tenantId);
        try {
            Variacao variacao = repositorioVariacao.save(new Variacao(
                    produtoId, sku, null, "Camiseta Basica Algodao - " + sku, null, false,
                    null, custoUnitarioAtual, "BRL", null, null, null, null));
            return variacao.getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private void criarTaxaComissao(UUID tenantId, UUID canalId, BigDecimal percentual) {
        ContextoTenant.definir(tenantId);
        try {
            repositorioTaxaCanal.save(new TaxaCanal(canalId, TipoTaxaCanal.COMISSAO, NaturezaCusto.COMISSAO_CANAL,
                    BaseIncidencia.VALOR_TOTAL_PEDIDO, TaxaCanal.CURINGA, TaxaCanal.CURINGA, null, null,
                    OffsetDateTime.now().minusDays(180), null, percentual, null, null, null, "BRL",
                    ConfiancaTaxa.INFORMADO_PELO_LOJISTA, OrigemTaxa.CADASTRO_MANUAL, null, null, null, null, null));
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID criarPedido(UUID tenantId, UUID canalId, String idExterno, StatusPedido status,
            OffsetDateTime feitoEm, BigDecimal valorTotalPedido, BigDecimal valorRepassePrevisto) {
        ContextoTenant.definir(tenantId);
        try {
            Pedido pedido = new Pedido(canalId, null, idExterno, null, status, null, feitoEm,
                    valorTotalPedido, BigDecimal.ZERO, BigDecimal.ZERO, valorTotalPedido,
                    valorRepassePrevisto, "BRL", null, null, null, null, null, null);
            return repositorioPedido.save(pedido).getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private UUID criarItemPedido(UUID tenantId, UUID pedidoId, UUID variacaoId, String skuOrigem,
            String tituloOrigem, BigDecimal valorTotalLinha) {
        ContextoTenant.definir(tenantId);
        try {
            ItemPedido item = new ItemPedido(pedidoId, variacaoId, skuOrigem, tituloOrigem,
                    BigDecimal.ONE, valorTotalLinha, BigDecimal.ZERO, valorTotalLinha, null, null);
            return repositorioItemPedido.save(item).getId();
        } finally {
            ContextoTenant.limpar();
        }
    }

    private void criarCusto(UUID tenantId, UUID pedidoId, UUID itemPedidoId, NaturezaCusto natureza,
            BigDecimal valor, OffsetDateTime competenciaEm, boolean ehEstimativa) {
        ContextoTenant.definir(tenantId);
        try {
            Custo custo = new Custo(natureza, pedidoId, itemPedidoId, null, valor, "BRL", competenciaEm,
                    ehEstimativa, null, null, null, null, "custo de teste - " + natureza, null, null, null);
            repositorioCusto.save(custo);
        } finally {
            ContextoTenant.limpar();
        }
    }

    private void criarDevolucaoAberta(UUID tenantId, UUID pedidoId) {
        ContextoTenant.definir(tenantId);
        try {
            Devolucao devolucao = new Devolucao(pedidoId, null, null, TipoDevolucao.PARCIAL,
                    StatusDevolucao.ABERTA, null, MotivoDevolucao.PRODUTO_COM_DEFEITO, null, false, null,
                    OffsetDateTime.now().minusDays(1), null, BigDecimal.ZERO, null, null, "BRL", null);
            repositorioDevolucao.save(devolucao);
        } finally {
            ContextoTenant.limpar();
        }
    }

    // ==================================================================
    // Auxiliares - SQL cru contra a conexao de DONO (privilegiada, ignora
    // RLS por construcao - ver Javadoc de PostgresDeTeste). Uso restrito a
    // PROVISIONAR fixture para as duas entidades sem caminho JPA legitimo
    // (usuario nao tem construtor publico; evento_ingerido em ERRO nao tem
    // construtor que aceite esse estado) - NUNCA para testar isolamento.
    // ==================================================================

    private UUID criarTenant(String slugMarcador) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO tenant (id, nome, slug) VALUES (?, ?, ?)")) {
            comando.setObject(1, id);
            comando.setString(2, "Tenant de teste " + slugMarcador);
            comando.setString(3, "tenant-" + slugMarcador);
            comando.executeUpdate();
        }
        return id;
    }

    private UUID criarUsuario(UUID tenantId, String email, String senhaPlana, String nome, String papel)
            throws SQLException {
        UUID id = UUID.randomUUID();
        String hashBcrypt = passwordEncoder.encode(senhaPlana);
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO usuario (id, tenant_id, email, senha_hash, nome, papel, ativo) "
                                + "VALUES (?, ?, ?, ?, ?, ?, true)")) {
            comando.setObject(1, id);
            comando.setObject(2, tenantId);
            comando.setString(3, email);
            comando.setString(4, hashBcrypt);
            comando.setString(5, nome);
            comando.setString(6, papel);
            comando.executeUpdate();
        }
        return id;
    }

    private void criarEventoIngeridoComErro(UUID tenantId, UUID canalId, String idExterno) throws SQLException {
        String payload = "{\"marcador\":\"" + idExterno + "\"}";
        try (Connection conexao = PostgresDeTeste.novaConexaoDono();
                PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO evento_ingerido (id, tenant_id, canal_id, tipo_evento, id_externo, "
                                + "hash_payload, payload_bruto, status, erro_mensagem, tentativas, recebido_em) "
                                + "VALUES (?, ?, ?, 'PEDIDO', ?, ?, ?::jsonb, 'ERRO', ?, 3, now())")) {
            comando.setObject(1, UUID.randomUUID());
            comando.setObject(2, tenantId);
            comando.setObject(3, canalId);
            comando.setString(4, idExterno);
            comando.setString(5, sha256Hex(payload));
            comando.setString(6, payload);
            comando.setString(7, "PayloadInvalidoException: campo obrigatorio ausente no payload de origem (fixture de teste)");
            comando.executeUpdate();
        }
    }

    private static String sha256Hex(String texto) {
        try {
            MessageDigest digestor = MessageDigest.getInstance("SHA-256");
            byte[] digest = digestor.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossivel) {
            throw new IllegalStateException("SHA-256 deveria estar sempre disponivel na JVM", impossivel);
        }
    }
}
