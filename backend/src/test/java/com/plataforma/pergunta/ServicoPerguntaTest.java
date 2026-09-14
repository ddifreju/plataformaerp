package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.plataforma.auditoria.ConsultaAuditada;
import com.plataforma.auditoria.RepositorioConsultaAuditada;
import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.margem.DirecaoViesLacuna;
import com.plataforma.margem.Lacuna;
import com.plataforma.margem.ResultadoMargemPeriodo;
import com.plataforma.margem.RotuloTeto;
import com.plataforma.margem.ServicoMargemPeriodo;
import com.plataforma.painel.ServicoPainelAnalista;
import com.plataforma.painel.ServicoPainelGestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitário puro (sem Spring, sem banco) de {@link ServicoPergunta} -
 * o executor do pipeline da decisão 0030. {@link PortaModeloLinguagem} é um
 * dublê manual (lambda, já que a interface tem um método só); os serviços
 * de domínio são mocks Mockito, como o resto do projeto já faz
 * ({@code ServicoPainelGestorTest}).
 */
class ServicoPerguntaTest {

    private static final UUID CANAL_ID = UUID.randomUUID();
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-09-01T00:00:00-03:00");
    private static final OffsetDateTime FIM = OffsetDateTime.parse("2026-09-14T00:00:00-03:00");

    private final CatalogoDePerguntas catalogo = new CatalogoDePerguntas();
    private final ValidadorDeParametros validador = mock(ValidadorDeParametros.class);
    private final ServicoMargemPeriodo servicoMargemPeriodo = mock(ServicoMargemPeriodo.class);
    private final ServicoPainelGestor servicoPainelGestor = mock(ServicoPainelGestor.class);
    private final ServicoPainelAnalista servicoPainelAnalista = mock(ServicoPainelAnalista.class);
    private final RepositorioCanal repositorioCanal = mock(RepositorioCanal.class);
    private final RepositorioConsultaAuditada repositorioConsultaAuditada = mock(RepositorioConsultaAuditada.class);

    private ServicoPergunta novoServico(PortaModeloLinguagem porta) {
        when(repositorioConsultaAuditada.save(any(ConsultaAuditada.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));
        return new ServicoPergunta(porta, catalogo, validador, servicoMargemPeriodo, servicoPainelGestor,
                servicoPainelAnalista, repositorioCanal, repositorioConsultaAuditada);
    }

    // ------------------------------------------------------------------
    // (a) confianca abaixo do limiar -> RECUSA, com auditoria gravada
    // ------------------------------------------------------------------

    @Test
    void confiancaAbaixoDoLimiarViraRecusaEGravaAuditoria() {
        PortaModeloLinguagem porta = (pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.MARGEM_DO_PERIODO.name(), Map.of(), new BigDecimal("0.10"));
        ServicoPergunta servico = novoServico(porta);

        RespostaPergunta resposta = servico.responder("quanto sobrou no mes passado?");

        assertEquals(TipoResposta.RECUSA, resposta.tipo());
        assertNotNull(resposta.consultaAuditadaId());
        assertFalse(resposta.perguntasQueSeiResponder().isEmpty());
        verify(repositorioConsultaAuditada, times(1)).save(any(ConsultaAuditada.class));
    }

    // ------------------------------------------------------------------
    // (b) codigo fora do catalogo -> RECUSA, mesmo com confianca alta
    // ------------------------------------------------------------------

    @Test
    void codigoForaDoCatalogoViraRecusaMesmoComConfiancaAlta() {
        PortaModeloLinguagem porta = (pergunta, cat) -> new IntencaoDetectada(
                "PREVISAO_DE_VENDAS_FUTURAS", Map.of(), new BigDecimal("0.99"));
        ServicoPergunta servico = novoServico(porta);

        RespostaPergunta resposta = servico.responder("quanto vou vender ano que vem?");

        assertEquals(TipoResposta.RECUSA, resposta.tipo());
        assertNotNull(resposta.consultaAuditadaId());
    }

    // ------------------------------------------------------------------
    // (c) parametro faltando -> ESCLARECIMENTO, com auditoria gravada
    // ------------------------------------------------------------------

    @Test
    void parametroFaltandoViraEsclarecimentoEGravaAuditoria() {
        PortaModeloLinguagem porta = (pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.MARGEM_DO_PERIODO.name(), Map.of(), new BigDecimal("0.90"));
        when(validador.validarCanal(any())).thenReturn(
                ResultadoParametro.esclarecimento("Para qual canal? Canais existentes: Mercado Livre, Shopee."));
        when(validador.validarPeriodo(any())).thenReturn(ResultadoParametro.valido(new Periodo(INICIO, FIM)));
        ServicoPergunta servico = novoServico(porta);

        RespostaPergunta resposta = servico.responder("quanto sobrou no mes passado?");

        assertEquals(TipoResposta.ESCLARECIMENTO, resposta.tipo());
        assertTrue(resposta.texto().contains("Para qual canal?"));
        assertNotNull(resposta.consultaAuditadaId());
        assertFalse(resposta.perguntasQueSeiResponder().isEmpty());
        verify(repositorioConsultaAuditada, times(1)).save(any(ConsultaAuditada.class));
    }

    // ------------------------------------------------------------------
    // (d) caminho feliz de margem -> RESPOSTA
    // ------------------------------------------------------------------

    @Test
    void caminhoFelizDeMargemDevolveRespostaComNumerosDoResultado() {
        PortaModeloLinguagem porta = (pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.MARGEM_DO_PERIODO.name(),
                Map.of("canal", "Mercado Livre", "periodoRelativo", "MES_PASSADO"), new BigDecimal("0.90"));

        Canal canal = DublesDeTeste.canal("ml-classico", "Mercado Livre");
        when(validador.validarCanal(any())).thenReturn(ResultadoParametro.valido(canal));
        when(validador.validarPeriodo(any())).thenReturn(ResultadoParametro.valido(new Periodo(INICIO, FIM)));

        ResultadoMargemPeriodo resultado = DublesDeTeste.margem(CANAL_ID, INICIO, FIM,
                new BigDecimal("1000.0000"), new BigDecimal("900.0000"), new BigDecimal("500.0000"),
                new BigDecimal("400.0000"), new BigDecimal("350.0000"),
                java.util.Optional.empty(), java.util.Optional.empty(),
                List.of(), RotuloTeto.CALCULADA, 3);
        when(servicoMargemPeriodo.calcular(INICIO, FIM, canal.getId())).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder("quanto sobrou no Mercado Livre mes passado?");

        assertEquals(TipoResposta.RESPOSTA, resposta.tipo());
        assertNotNull(resposta.consultaAuditadaId());
        assertEquals("MARGEM_DO_PERIODO", resposta.intencao());
        assertTrue(resposta.perguntasQueSeiResponder().isEmpty());
        assertTrue(resposta.texto().contains("R$ 1000,00"), "esperava o N0 formatado no texto: " + resposta.texto());
        assertTrue(resposta.texto().contains("R$ 350,00"), "esperava o N4 formatado no texto: " + resposta.texto());
        assertTrue(resposta.numeros().stream().anyMatch(n -> n.valor().equals("R$ 1000,00")));
        assertTrue(resposta.numeros().stream().anyMatch(n -> n.valor().equals("3")));
    }

    // ------------------------------------------------------------------
    // (e) zero pedidos -> texto de ausencia, nunca "R$ 0,00"
    // ------------------------------------------------------------------

    @Test
    void zeroPedidosDevolveTextoDeAusenciaNuncaValorZero() {
        PortaModeloLinguagem porta = (pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.MARGEM_DO_PERIODO.name(),
                Map.of("canal", "Mercado Livre", "periodoRelativo", "MES_PASSADO"), new BigDecimal("0.90"));

        Canal canal = DublesDeTeste.canal("ml-classico", "Mercado Livre");
        when(validador.validarCanal(any())).thenReturn(ResultadoParametro.valido(canal));
        when(validador.validarPeriodo(any())).thenReturn(ResultadoParametro.valido(new Periodo(INICIO, FIM)));

        ResultadoMargemPeriodo resultado = DublesDeTeste.margem(CANAL_ID, INICIO, FIM,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                java.util.Optional.empty(), java.util.Optional.empty(),
                List.of(), RotuloTeto.CALCULADA, 0);
        when(servicoMargemPeriodo.calcular(INICIO, FIM, canal.getId())).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder("quanto sobrou no Mercado Livre mes passado?");

        assertEquals(TipoResposta.RESPOSTA, resposta.tipo());
        assertTrue(resposta.texto().toLowerCase(java.util.Locale.ROOT).contains("não há pedido")
                || resposta.texto().toLowerCase(java.util.Locale.ROOT).contains("nao ha pedido"));
        assertFalse(resposta.texto().contains("R$ 0,00"), "zero pedido nao pode ser apresentado como R$ 0,00");
        assertTrue(resposta.numeros().isEmpty());
    }

    // ------------------------------------------------------------------
    // (f) rotulo com teto -> texto menciona a limitacao
    // ------------------------------------------------------------------

    @Test
    void rotuloComTetoMencionaALimitacaoNoTexto() {
        PortaModeloLinguagem porta = (pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.MARGEM_DO_PERIODO.name(),
                Map.of("canal", "Mercado Livre", "periodoRelativo", "MES_PASSADO"), new BigDecimal("0.90"));

        Canal canal = DublesDeTeste.canal("ml-classico", "Mercado Livre");
        when(validador.validarCanal(any())).thenReturn(ResultadoParametro.valido(canal));
        when(validador.validarPeriodo(any())).thenReturn(ResultadoParametro.valido(new Periodo(INICIO, FIM)));

        Lacuna lacuna = new Lacuna("custo-produto-ausente", "Custo do produto SKU-1 não cadastrado.",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
        ResultadoMargemPeriodo resultado = DublesDeTeste.margem(CANAL_ID, INICIO, FIM,
                new BigDecimal("1000.0000"), new BigDecimal("900.0000"), new BigDecimal("500.0000"),
                new BigDecimal("400.0000"), new BigDecimal("350.0000"),
                java.util.Optional.empty(), java.util.Optional.empty(),
                List.of(lacuna), RotuloTeto.COM_TETO, 3);
        when(servicoMargemPeriodo.calcular(INICIO, FIM, canal.getId())).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder("quanto sobrou no Mercado Livre mes passado?");

        assertEquals(TipoResposta.RESPOSTA, resposta.tipo());
        assertEquals(RotuloTeto.COM_TETO.name(), resposta.rotuloConfianca());
        assertTrue(resposta.texto().toLowerCase(java.util.Locale.ROOT).contains("teto"));
        assertTrue(resposta.texto().contains("Custo do produto SKU-1 não cadastrado."));
        assertEquals(List.of("Custo do produto SKU-1 não cadastrado."), resposta.lacunas());
    }

    // ------------------------------------------------------------------
    // (g) texto invalido -> PerguntaInvalidaException
    // ------------------------------------------------------------------

    @Test
    void textoBlankLancaPerguntaInvalida() {
        ServicoPergunta servico = novoServico((pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.CANAIS_DISPONIVEIS.name(), Map.of(), new BigDecimal("0.90")));

        assertThrows(PerguntaInvalidaException.class, () -> servico.responder("   "));
    }

    @Test
    void textoComMaisDe500CaracteresLancaPerguntaInvalida() {
        ServicoPergunta servico = novoServico((pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.CANAIS_DISPONIVEIS.name(), Map.of(), new BigDecimal("0.90")));

        String textoGigante = "a".repeat(501);

        assertThrows(PerguntaInvalidaException.class, () -> servico.responder(textoGigante));
    }
}
