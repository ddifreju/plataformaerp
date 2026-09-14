package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.plataforma.auditoria.ConsultaAuditada;
import com.plataforma.auditoria.RepositorioConsultaAuditada;
import com.plataforma.canal.Canal;
import com.plataforma.canal.CategoriaCanal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;
import com.plataforma.comum.tenant.ContextoTenant;
import com.plataforma.margem.ServicoMargemPeriodo;
import com.plataforma.painel.ServicoPainelAnalista;
import com.plataforma.painel.ServicoPainelGestor;
import com.plataforma.suporte.PostgresDeTeste;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolamento de tenant COMPORTAMENTAL de {@link ServicoPergunta} - regra 1
 * do CLAUDE.md ("todo PR que toca query precisa de teste de isolamento").
 * Mesma arquitetura de {@link com.plataforma.margem.IsolamentoMargemTest}
 * (leia o Javadoc de lá primeiro - não repito aqui a explicação de
 * "conexão de dono só prova que a linha existe, nunca testa isolamento").
 *
 * <h2>Por que {@link ServicoPergunta} é construído à mão aqui, e não
 * {@code @Autowired} direto</h2>
 * O único {@link PortaModeloLinguagem} registrado no contexto Spring é
 * {@link ModeloHeuristico}, que hoje extrai {@code canal} e
 * {@code periodoRelativo} do texto casando contra os canais do PRÓPRIO
 * tenant do contexto - não dá para forçar, a partir do texto da pergunta,
 * o cenário adversário que este arquivo precisa provar ("canal de OUTRO
 * tenant nomeado no texto"), porque {@code ModeloHeuristico} nunca veria o
 * nome de um canal de outro tenant para casar contra ele. Por isso cada
 * teste monta o próprio dublê
 * {@link PortaModeloLinguagem} (lambda, mesma técnica de
 * {@code ServicoPerguntaTest}) e constrói {@link ServicoPergunta} manualmente,
 * injetando os DEMAIS colaboradores ({@link CatalogoDePerguntas},
 * {@link ValidadorDeParametros}, {@code ServicoMargemPeriodo}, os dois
 * painéis, {@link RepositorioCanal}, {@link RepositorioConsultaAuditada})
 * como beans REAIS do contexto Spring - a pilha de tenant inteira
 * (ContextoTenant -&gt; DataSourceComTenant -&gt; RLS -&gt; @TenantId) roda de
 * verdade a partir daí. Só o "modelo de linguagem" é dublê; tudo que toca
 * banco é produção.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IsolamentoPerguntaTest {

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registro) {
        PostgresDeTeste.configurarPropriedades(registro);
    }

    @Autowired
    private CatalogoDePerguntas catalogo;

    @Autowired
    private ValidadorDeParametros validador;

    @Autowired
    private ServicoMargemPeriodo servicoMargemPeriodo;

    @Autowired
    private ServicoPainelGestor servicoPainelGestor;

    @Autowired
    private ServicoPainelAnalista servicoPainelAnalista;

    @Autowired
    private RepositorioCanal repositorioCanal;

    @Autowired
    private RepositorioConsultaAuditada repositorioConsultaAuditada;

    @AfterEach
    void limparContexto() {
        // Mesma defesa contra vazamento de tenant ENTRE TESTES usada em
        // IsolamentoMargemTest/IsolamentoFaseUmTest.
        ContextoTenant.limpar();
    }

    private ServicoPergunta novoServico(PortaModeloLinguagem porta) {
        return new ServicoPergunta(porta, catalogo, validador, servicoMargemPeriodo, servicoPainelGestor,
                servicoPainelAnalista, repositorioCanal, repositorioConsultaAuditada);
    }

    private static PortaModeloLinguagem perguntaCanaisDisponiveis() {
        return (pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.CANAIS_DISPONIVEIS.name(), Map.of(), new BigDecimal("0.90"));
    }

    // ==================================================================
    // 1. consulta_auditada gravada por ServicoPergunta nasce com o tenant
    //    do contexto e nao e visivel do outro tenant.
    // ==================================================================

    @Test
    void consultaAuditadaGravadaPorServicoPerguntaNasceComTenantDoContextoENaoVazaParaOutroTenant()
            throws SQLException {
        UUID tenantA = criarTenant("pergunta-auditoria-a");
        UUID tenantB = criarTenant("pergunta-auditoria-b");

        ContextoTenant.definir(tenantA);
        UUID consultaAuditadaId;
        try {
            ServicoPergunta servico = novoServico(perguntaCanaisDisponiveis());
            RespostaPergunta resposta = servico.responder("Quais canais eu tenho cadastrados?");
            consultaAuditadaId = resposta.consultaAuditadaId();
        } finally {
            ContextoTenant.limpar();
        }

        assertNotNull(consultaAuditadaId, "ServicoPergunta.responder deveria sempre gravar consulta_auditada");
        // Prova que ha o que vazar ANTES de provar que nao vazou (mesmo
        // racional de IsolamentoMargemTest/IsolamentoFaseUmTest).
        assertEquals(1, contarComoPrivilegiado("consulta_auditada", tenantA, consultaAuditadaId),
                "setup falhou: a consulta_auditada de A nao foi gravada de verdade, ou nao nasceu com tenant_id = A");

        ContextoTenant.definir(tenantB);
        Optional<ConsultaAuditada> encontradaNoContextoDeB;
        try {
            encontradaNoContextoDeB = repositorioConsultaAuditada.findById(consultaAuditadaId);
        } finally {
            ContextoTenant.limpar();
        }

        assertTrue(encontradaNoContextoDeB.isEmpty(),
                "VAZAMENTO: tenant B conseguiu ler, pelo repositorio, a consulta_auditada gravada por A");
    }

    // ==================================================================
    // 2. O cenario mais importante deste arquivo: um parametro "canal" que
    //    nomeia um canal DE OUTRO TENANT (o texto vem do usuario - e a
    //    superficie de ataque real desta camada) nunca resolve esse canal.
    //    Vira ESCLARECIMENTO, jamais uma RESPOSTA com dado alheio.
    // ==================================================================

    @Test
    void perguntaComCanalDeOutroTenantNoTextoViraEsclarecimentoNuncaRespostaComDadoAlheio() throws SQLException {
        UUID tenantA = criarTenant("pergunta-canal-alheio-a");
        UUID tenantB = criarTenant("pergunta-canal-alheio-b");
        criarCanalComoTenant(tenantA, "Canal Legitimo De A");
        UUID canalBId = criarCanalComoTenant(tenantB, "Canal Secreto De B");

        assertEquals(1, contarComoPrivilegiado("canal", tenantB, canalBId),
                "setup falhou: canal de B nao foi gravado de verdade");

        // O dublê simula EXATAMENTE o que um modelo de linguagem real faria
        // ao extrair o parametro "canal" da pergunta digitada: devolve o
        // texto literal que o usuario escreveu, sem saber (nem precisar
        // saber) a quem esse canal pertence - quem decide isso e
        // ValidadorDeParametros.validarCanal, contra RepositorioCanal do
        // TENANT DO CONTEXTO.
        PortaModeloLinguagem porta = (pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.MARGEM_DO_PERIODO.name(),
                Map.of("canal", "Canal Secreto De B", "periodoRelativo", "ULTIMOS_30_DIAS"),
                new BigDecimal("0.90"));

        ContextoTenant.definir(tenantA);
        RespostaPergunta resposta;
        try {
            ServicoPergunta servico = novoServico(porta);
            resposta = servico.responder("Quanto sobrou no Canal Secreto De B nos ultimos 30 dias?");
        } finally {
            ContextoTenant.limpar();
        }

        assertEquals(TipoResposta.ESCLARECIMENTO, resposta.tipo(),
                "VAZAMENTO: pergunta citando canal de outro tenant nao deveria virar RESPOSTA");
        assertNotNull(resposta.consultaAuditadaId());
        // A lista de "canais existentes" no esclarecimento e SEMPRE a do
        // tenant do contexto (A) - nunca deveria mencionar o canal de B.
        assertTrue(resposta.texto().contains("Canal Legitimo De A"),
                "esclarecimento deveria listar os canais do proprio tenant (A)");
        assertFalse(resposta.texto().contains(canalBId.toString()),
                "VAZAMENTO: id do canal de outro tenant apareceu no texto da resposta");
        // A unica mencao possivel a "Canal Secreto De B" no texto e o ECO do
        // que o PROPRIO USUARIO digitou ("Nao encontrei o canal \"...\""),
        // nunca um dado lido da linha de B - por isso a asserção acima (nao
        // conter o id de B) e quem prova isolamento aqui, nao a ausencia do
        // nome no texto.
    }

    // ==================================================================
    // 3. CANAIS_DISPONIVEIS lista so os canais do tenant do contexto.
    // ==================================================================

    @Test
    void canaisDisponiveisListaSoOsCanaisDoTenantDoContexto() throws SQLException {
        UUID tenantA = criarTenant("pergunta-canais-disponiveis-a");
        UUID tenantB = criarTenant("pergunta-canais-disponiveis-b");
        criarCanalComoTenant(tenantA, "Canal A Um");
        criarCanalComoTenant(tenantA, "Canal A Dois");
        UUID canalBId = criarCanalComoTenant(tenantB, "Canal Exclusivo De B");

        assertEquals(1, contarComoPrivilegiado("canal", tenantB, canalBId),
                "setup falhou: canal de B nao foi gravado de verdade");

        ContextoTenant.definir(tenantA);
        RespostaPergunta resposta;
        try {
            ServicoPergunta servico = novoServico(perguntaCanaisDisponiveis());
            resposta = servico.responder("Quais canais eu tenho cadastrados?");
        } finally {
            ContextoTenant.limpar();
        }

        assertEquals(TipoResposta.RESPOSTA, resposta.tipo());
        assertTrue(resposta.texto().contains("Canal A Um"));
        assertTrue(resposta.texto().contains("Canal A Dois"));
        assertFalse(resposta.texto().contains("Canal Exclusivo De B"),
                "VAZAMENTO: canal de outro tenant apareceu na lista de canais disponiveis");
        assertFalse(resposta.texto().contains(canalBId.toString()),
                "VAZAMENTO: id do canal de outro tenant apareceu no texto da resposta");

        List<NumeroCitado> canaisCadastrados = resposta.numeros().stream()
                .filter(numero -> numero.nome().equals("Canais cadastrados"))
                .toList();
        assertEquals(1, canaisCadastrados.size());
        assertEquals("2", canaisCadastrados.get(0).valor(),
                "contagem deveria ser EXATAMENTE os 2 canais de A, nunca incluir o de B");
    }

    // ------------------------------------------------------------------
    // Auxiliares - criacao de tenant (identico a IsolamentoMargemTest).
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
    // Auxiliar - insercao "legitima" via repositorio JPA (exercitando a
    // pilha completa: ContextoTenant -> DataSourceComTenant -> RLS ->
    // @TenantId), mesmo padrao de IsolamentoMargemTest#criarCanalComoTenant.
    // ------------------------------------------------------------------

    private UUID criarCanalComoTenant(UUID tenantId, String nome) {
        ContextoTenant.definir(tenantId);
        try {
            Canal canal = repositorioCanal.save(new Canal(
                    "canal-" + UUID.randomUUID().toString().substring(0, 8),
                    nome, TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, null, null, null));
            return canal.getId();
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
