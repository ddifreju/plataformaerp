package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.plataforma.auditoria.ConsultaAuditada;
import com.plataforma.auditoria.RepositorioConsultaAuditada;
import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.margem.DirecaoViesLacuna;
import com.plataforma.margem.Lacuna;
import com.plataforma.margem.ResultadoMargemPeriodo;
import com.plataforma.margem.RotuloTeto;
import com.plataforma.margem.ServicoMargemPeriodo;
import com.plataforma.painel.RespostaFilaPendencias;
import com.plataforma.painel.ItemDevolucaoAberta;
import com.plataforma.painel.ItemEventoComErro;
import com.plataforma.painel.ItemPedidoSemVariacao;
import com.plataforma.painel.ItemVariacaoSemCusto;
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

        String textoDaPergunta = "quanto sobrou no mes passado?";
        RespostaPergunta resposta = servico.responder(textoDaPergunta);

        assertEquals(TipoResposta.RECUSA, resposta.tipo());
        assertNotNull(resposta.consultaAuditadaId());
        assertFalse(resposta.perguntasQueSeiResponder().isEmpty());

        // Conteúdo da auditoria, não só a existência dela (regra 3 do
        // CLAUDE.md): mesmo em RECUSA a pergunta é gravada, o sqlExecutado
        // descreve a recusa (não fica vazio nem genérico), e não há ID nem
        // linha nenhuma - não houve consulta de domínio nenhuma para provar.
        ArgumentCaptor<ConsultaAuditada> captor = ArgumentCaptor.forClass(ConsultaAuditada.class);
        verify(repositorioConsultaAuditada, times(1)).save(captor.capture());
        ConsultaAuditada auditoria = captor.getValue();
        assertEquals(textoDaPergunta, auditoria.getPergunta(),
                "a pergunta recusada precisa ser gravada mesmo assim - alimenta a decisao 0031");
        assertTrue(auditoria.getSqlExecutado().contains("PerguntaRecusada"),
                "sqlExecutado deveria descrever a recusa: " + auditoria.getSqlExecutado());
        assertEquals(0, auditoria.getIdsRetornados().length);
        assertEquals(0, auditoria.getLinhasRetornadas());
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
        String textoDaPergunta = "quanto sobrou no Mercado Livre mes passado?";
        RespostaPergunta resposta = servico.responder(textoDaPergunta);

        assertEquals(TipoResposta.RESPOSTA, resposta.tipo());
        assertNotNull(resposta.consultaAuditadaId());
        assertEquals("MARGEM_DO_PERIODO", resposta.intencao());
        assertTrue(resposta.perguntasQueSeiResponder().isEmpty());
        assertTrue(resposta.texto().contains("R$ 1000,00"), "esperava o N0 formatado no texto: " + resposta.texto());
        assertTrue(resposta.texto().contains("R$ 350,00"), "esperava o N4 formatado no texto: " + resposta.texto());
        assertTrue(resposta.numeros().stream().anyMatch(n -> n.valor().equals("R$ 1000,00")));
        assertTrue(resposta.numeros().stream().anyMatch(n -> n.valor().equals("3")));

        // Conteúdo da auditoria, não só a existência dela (regra 3 do
        // CLAUDE.md): os ids gravados são exatamente a união de
        // idsPedidoUsados/idsCustoUsados do RESULTADO da consulta (nunca um
        // subconjunto, nunca ids inventados), linhasRetornadas bate com o
        // tamanho dessa união, a pergunta gravada é o texto do usuário, e
        // executadoPor é sempre "ServicoPergunta" (nunca o usuário, decisão 0012).
        ArgumentCaptor<ConsultaAuditada> captor = ArgumentCaptor.forClass(ConsultaAuditada.class);
        verify(repositorioConsultaAuditada, times(1)).save(captor.capture());
        ConsultaAuditada auditoria = captor.getValue();
        Set<UUID> idsEsperados = new HashSet<>(resultado.idsPedidoUsados());
        idsEsperados.addAll(resultado.idsCustoUsados());
        assertEquals(idsEsperados, Set.of(auditoria.getIdsRetornados()));
        assertEquals(idsEsperados.size(), auditoria.getLinhasRetornadas());
        assertEquals(textoDaPergunta, auditoria.getPergunta());
        assertEquals("ServicoPergunta", auditoria.getExecutadoPor());
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
    // (g) fila de pendencias -> RESPOSTA, auditoria com os ids dos itens
    // ------------------------------------------------------------------

    @Test
    void filaDePendenciasGravaAuditoriaComOsIdsDosItensRetornados() {
        PortaModeloLinguagem porta = (pergunta, cat) -> new IntencaoDetectada(
                CodigoIntencao.FILA_DE_PENDENCIAS.name(), Map.of(), new BigDecimal("0.90"));

        ItemEventoComErro eventoComErro = DublesDeTeste.itemEventoComErro();
        ItemPedidoSemVariacao itemSemVariacao = DublesDeTeste.itemPedidoSemVariacao();
        ItemVariacaoSemCusto variacaoSemCusto = DublesDeTeste.itemVariacaoSemCusto();
        ItemDevolucaoAberta devolucaoAberta = DublesDeTeste.itemDevolucaoAberta();
        RespostaFilaPendencias resultado = new RespostaFilaPendencias(
                List.of(eventoComErro), List.of(itemSemVariacao), List.of(variacaoSemCusto),
                List.of(devolucaoAberta));
        when(servicoPainelAnalista.pendencias()).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        String textoDaPergunta = "o que eu preciso resolver hoje?";
        RespostaPergunta resposta = servico.responder(textoDaPergunta);

        assertEquals(TipoResposta.RESPOSTA, resposta.tipo());
        assertEquals(CodigoIntencao.FILA_DE_PENDENCIAS.name(), resposta.intencao());

        // Conteúdo da auditoria, não só a existência dela (regra 3 do
        // CLAUDE.md): os ids gravados são exatamente os ids dos QUATRO itens
        // devolvidos (um de cada categoria da fila), linhasRetornadas bate
        // com essa contagem, a pergunta gravada é o texto do usuário, e
        // executadoPor é sempre "ServicoPergunta".
        ArgumentCaptor<ConsultaAuditada> captor = ArgumentCaptor.forClass(ConsultaAuditada.class);
        verify(repositorioConsultaAuditada, times(1)).save(captor.capture());
        ConsultaAuditada auditoria = captor.getValue();
        Set<UUID> idsEsperados = Set.of(
                eventoComErro.id(), itemSemVariacao.id(), variacaoSemCusto.id(), devolucaoAberta.id());
        assertEquals(idsEsperados, Set.of(auditoria.getIdsRetornados()));
        assertEquals(4, auditoria.getLinhasRetornadas());
        assertEquals(textoDaPergunta, auditoria.getPergunta());
        assertEquals("ServicoPergunta", auditoria.getExecutadoPor());
    }

    // ------------------------------------------------------------------
    // (h) texto invalido -> PerguntaInvalidaException
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
