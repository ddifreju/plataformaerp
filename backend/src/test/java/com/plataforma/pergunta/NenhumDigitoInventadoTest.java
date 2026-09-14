package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.plataforma.auditoria.ConsultaAuditada;
import com.plataforma.auditoria.RepositorioConsultaAuditada;
import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.devolucao.ContagemPorStatusDevolucao;
import com.plataforma.devolucao.StatusDevolucao;
import com.plataforma.margem.DirecaoViesLacuna;
import com.plataforma.margem.Lacuna;
import com.plataforma.margem.ResultadoMargemPeriodo;
import com.plataforma.margem.RotuloTeto;
import com.plataforma.margem.ServicoMargemPeriodo;
import com.plataforma.painel.ItemEventoComErro;
import com.plataforma.painel.ItemPedidoSemVariacao;
import com.plataforma.painel.ItemVariacaoSemCusto;
import com.plataforma.painel.RespostaFilaPendencias;
import com.plataforma.painel.RespostaGargalosProcesso;
import com.plataforma.painel.ServicoPainelAnalista;
import com.plataforma.painel.ServicoPainelGestor;
import com.plataforma.pedido.ContagemPorStatusPedido;
import com.plataforma.pedido.StatusPedido;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A TRAVA da decisão 0030 ("nenhum dígito da resposta vem do modelo").
 * Para cada caminho de {@link TipoResposta#RESPOSTA}, extrai toda
 * sequência de dígitos do texto final e prova que cada uma aparece: no
 * resultado da consulta (formatado do MESMO jeito que
 * {@link ServicoPergunta} formata, via {@link FormatadorDeTexto}), no
 * texto da própria pergunta do usuário, ou numa allowlist curta e
 * explícita de literais estruturais (os rótulos N0..N4). Se este teste
 * ficasse verde permitindo um dígito de fora dessas três origens, a
 * arquitetura da decisão 0030 - "o modelo interpreta, nunca redige
 * número" - estaria furada sem que nenhum outro teste do pacote acusasse.
 */
class NenhumDigitoInventadoTest {

    private static final Pattern DIGITOS = Pattern.compile("\\d+");

    // Únicos literais numéricos que o template pode escrever sem vir do
    // resultado da consulta: os rótulos N0..N4 (BlocoMargem/ResultadoMargemPeriodo
    // não tem "N0" como valor, é texto fixo do template de apresentação).
    private static final Set<String> ALLOWLIST_ESTRUTURAL = Set.of("0", "1", "2", "3", "4");

    private static final UUID CANAL_ID = UUID.randomUUID();
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-08-01T00:00:00-03:00");
    private static final OffsetDateTime FIM = OffsetDateTime.parse("2026-09-01T00:00:00-03:00");

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

    private Set<String> digitosDe(String texto) {
        Set<String> sequencias = new HashSet<>();
        Matcher matcher = DIGITOS.matcher(texto);
        while (matcher.find()) {
            sequencias.add(matcher.group());
        }
        return sequencias;
    }

    private void assertTodoDigitoRastreavel(String texto, String pergunta, Set<String> poolDaConsulta) {
        Set<String> poolCompleto = new HashSet<>(poolDaConsulta);
        poolCompleto.addAll(digitosDe(pergunta));
        for (String digito : digitosDe(texto)) {
            assertTrue(poolCompleto.contains(digito) || ALLOWLIST_ESTRUTURAL.contains(digito),
                    "digito '" + digito + "' no texto de resposta nao rastreia a nenhuma origem permitida. "
                            + "Texto: " + texto);
        }
    }

    // ------------------------------------------------------------------
    // MARGEM_DO_PERIODO
    // ------------------------------------------------------------------

    @Test
    void margemDoPeriodoNaoInventaDigito() {
        String pergunta = "quanto sobrou no Mercado Livre no mes passado?";
        PortaModeloLinguagem porta = (p, cat) -> new IntencaoDetectada(CodigoIntencao.MARGEM_DO_PERIODO.name(),
                Map.of("canal", "Mercado Livre", "periodoRelativo", "MES_PASSADO"), new BigDecimal("0.90"));

        Canal canal = DublesDeTeste.canal("ml-classico", "Mercado Livre");
        when(validador.validarCanal(any())).thenReturn(ResultadoParametro.valido(canal));
        when(validador.validarPeriodo(any())).thenReturn(ResultadoParametro.valido(new Periodo(INICIO, FIM)));

        BigDecimal n0 = new BigDecimal("12345.6700");
        BigDecimal n1 = new BigDecimal("11000.1200");
        BigDecimal n2 = new BigDecimal("5000.5000");
        BigDecimal n3 = new BigDecimal("4321.0900");
        BigDecimal n4 = new BigDecimal("4000.0000");
        Optional<BigDecimal> pctContribuicao = Optional.of(new BigDecimal("0.404500"));
        Optional<BigDecimal> pctLiquida = Optional.of(new BigDecimal("0.350100"));
        ResultadoMargemPeriodo resultado = DublesDeTeste.margem(CANAL_ID, INICIO, FIM, n0, n1, n2, n3, n4,
                pctContribuicao, pctLiquida, List.of(), RotuloTeto.CALCULADA, 17);
        when(servicoMargemPeriodo.calcular(INICIO, FIM, canal.getId())).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder(pergunta);

        Set<String> pool = new HashSet<>();
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n0)));
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n1)));
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n2)));
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n3)));
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n4)));
        pctContribuicao.ifPresent(p -> pool.addAll(digitosDe(FormatadorDeTexto.percentual(p))));
        pctLiquida.ifPresent(p -> pool.addAll(digitosDe(FormatadorDeTexto.percentual(p))));
        pool.addAll(digitosDe(String.valueOf(resultado.quantidadePedidos())));
        pool.addAll(digitosDe(FormatadorDeTexto.data(resultado.inicio())));
        pool.addAll(digitosDe(FormatadorDeTexto.data(resultado.fim())));

        assertTodoDigitoRastreavel(resposta.texto(), pergunta, pool);
    }

    @Test
    void margemDoPeriodoComZeroPedidosNaoInventaDigito() {
        String pergunta = "quanto sobrou no Mercado Livre no mes passado?";
        PortaModeloLinguagem porta = (p, cat) -> new IntencaoDetectada(CodigoIntencao.MARGEM_DO_PERIODO.name(),
                Map.of("canal", "Mercado Livre", "periodoRelativo", "MES_PASSADO"), new BigDecimal("0.90"));

        Canal canal = DublesDeTeste.canal("ml-classico", "Mercado Livre");
        when(validador.validarCanal(any())).thenReturn(ResultadoParametro.valido(canal));
        when(validador.validarPeriodo(any())).thenReturn(ResultadoParametro.valido(new Periodo(INICIO, FIM)));

        ResultadoMargemPeriodo resultado = DublesDeTeste.margem(CANAL_ID, INICIO, FIM,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Optional.empty(), Optional.empty(), List.of(), RotuloTeto.CALCULADA, 0);
        when(servicoMargemPeriodo.calcular(INICIO, FIM, canal.getId())).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder(pergunta);

        Set<String> pool = new HashSet<>();
        pool.addAll(digitosDe(FormatadorDeTexto.data(resultado.inicio())));
        pool.addAll(digitosDe(FormatadorDeTexto.data(resultado.fim())));

        assertTodoDigitoRastreavel(resposta.texto(), pergunta, pool);
        assertFalse(resposta.texto().contains("R$ 0,00"));
    }

    @Test
    void margemDoPeriodoComTetoNaoInventaDigito() {
        String pergunta = "quanto sobrou na Loja Propria mes passado?";
        PortaModeloLinguagem porta = (p, cat) -> new IntencaoDetectada(CodigoIntencao.MARGEM_DO_PERIODO.name(),
                Map.of("canal", "Loja Propria", "periodoRelativo", "MES_PASSADO"), new BigDecimal("0.90"));

        Canal canal = DublesDeTeste.canal("loja-propria", "Loja Propria");
        when(validador.validarCanal(any())).thenReturn(ResultadoParametro.valido(canal));
        when(validador.validarPeriodo(any())).thenReturn(ResultadoParametro.valido(new Periodo(INICIO, FIM)));

        Lacuna lacuna = new Lacuna("custo-produto-ausente", "Custo do produto SKU-42 nao cadastrado.",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
        BigDecimal n0 = new BigDecimal("999.0000");
        BigDecimal n1 = new BigDecimal("888.0000");
        BigDecimal n2 = new BigDecimal("777.0000");
        BigDecimal n3 = new BigDecimal("666.0000");
        BigDecimal n4 = new BigDecimal("555.0000");
        ResultadoMargemPeriodo resultado = DublesDeTeste.margem(CANAL_ID, INICIO, FIM, n0, n1, n2, n3, n4,
                Optional.empty(), Optional.empty(), List.of(lacuna), RotuloTeto.COM_TETO, 2);
        when(servicoMargemPeriodo.calcular(INICIO, FIM, canal.getId())).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder(pergunta);

        Set<String> pool = new HashSet<>();
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n0)));
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n1)));
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n2)));
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n3)));
        pool.addAll(digitosDe(FormatadorDeTexto.moeda(n4)));
        pool.addAll(digitosDe(String.valueOf(resultado.quantidadePedidos())));
        pool.addAll(digitosDe(FormatadorDeTexto.data(resultado.inicio())));
        pool.addAll(digitosDe(FormatadorDeTexto.data(resultado.fim())));
        pool.addAll(digitosDe(lacuna.descricao())); // "SKU-42" - digito vem da propria lacuna do resultado

        assertTodoDigitoRastreavel(resposta.texto(), pergunta, pool);
    }

    // ------------------------------------------------------------------
    // LACUNAS_DA_MARGEM
    // ------------------------------------------------------------------

    @Test
    void lacunasDaMargemNaoInventaDigito() {
        String pergunta = "o que falta para calcular a margem do Mercado Livre no mes passado?";
        PortaModeloLinguagem porta = (p, cat) -> new IntencaoDetectada(CodigoIntencao.LACUNAS_DA_MARGEM.name(),
                Map.of("canal", "Mercado Livre", "periodoRelativo", "MES_PASSADO"), new BigDecimal("0.90"));

        Canal canal = DublesDeTeste.canal("ml-classico", "Mercado Livre");
        when(validador.validarCanal(any())).thenReturn(ResultadoParametro.valido(canal));
        when(validador.validarPeriodo(any())).thenReturn(ResultadoParametro.valido(new Periodo(INICIO, FIM)));

        Lacuna lacuna1 = new Lacuna("custo-produto-ausente", "Custo do produto SKU-7 nao cadastrado.",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
        Lacuna lacuna2 = new Lacuna("taxa-canal-ausente", "Comissao do canal nao cadastrada para categoria 12.",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
        ResultadoMargemPeriodo resultado = DublesDeTeste.margem(CANAL_ID, INICIO, FIM,
                new BigDecimal("500.0000"), new BigDecimal("480.0000"), new BigDecimal("300.0000"),
                new BigDecimal("250.0000"), new BigDecimal("200.0000"),
                Optional.empty(), Optional.empty(), List.of(lacuna1, lacuna2), RotuloTeto.COM_TETO, 4);
        when(servicoMargemPeriodo.calcular(INICIO, FIM, canal.getId())).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder(pergunta);

        Set<String> pool = new HashSet<>();
        pool.addAll(digitosDe(FormatadorDeTexto.data(resultado.inicio())));
        pool.addAll(digitosDe(FormatadorDeTexto.data(resultado.fim())));
        pool.addAll(digitosDe(String.valueOf(resultado.lacunas().size())));
        pool.addAll(digitosDe(lacuna1.descricao()));
        pool.addAll(digitosDe(lacuna2.descricao()));

        assertTodoDigitoRastreavel(resposta.texto(), pergunta, pool);
    }

    // ------------------------------------------------------------------
    // GARGALOS_DA_OPERACAO
    // ------------------------------------------------------------------

    @Test
    void gargalosDaOperacaoNaoInventaDigito() {
        String pergunta = "onde a operacao esta travando?";
        PortaModeloLinguagem porta = (p, cat) -> new IntencaoDetectada(
                CodigoIntencao.GARGALOS_DA_OPERACAO.name(), Map.of(), new BigDecimal("0.90"));

        RespostaGargalosProcesso resultado = new RespostaGargalosProcesso(
                List.of(new ContagemPorStatusPedido(StatusPedido.PAGO, 10),
                        new ContagemPorStatusPedido(StatusPedido.ENVIADO, 3)),
                List.of(new ContagemPorStatusDevolucao(StatusDevolucao.ABERTA, 2)),
                5L, 7L);
        when(servicoPainelGestor.gargalos()).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder(pergunta);

        Set<String> pool = new HashSet<>();
        resultado.pedidosPorStatus().forEach(item -> pool.addAll(digitosDe(String.valueOf(item.quantidade()))));
        resultado.devolucoesPorStatus().forEach(item -> pool.addAll(digitosDe(String.valueOf(item.quantidade()))));
        pool.addAll(digitosDe(String.valueOf(resultado.eventosIngestaoComErro())));
        pool.addAll(digitosDe(String.valueOf(resultado.pedidosSemCustoMercadoria())));

        assertTodoDigitoRastreavel(resposta.texto(), pergunta, pool);
    }

    // ------------------------------------------------------------------
    // FILA_DE_PENDENCIAS
    // ------------------------------------------------------------------

    @Test
    void filaDePendenciasNaoInventaDigito() {
        String pergunta = "o que eu preciso resolver hoje?";
        PortaModeloLinguagem porta = (p, cat) -> new IntencaoDetectada(
                CodigoIntencao.FILA_DE_PENDENCIAS.name(), Map.of(), new BigDecimal("0.90"));

        RespostaFilaPendencias resultado = new RespostaFilaPendencias(
                List.of(itemEventoComErro(), itemEventoComErro()),
                List.of(itemPedidoSemVariacao()),
                List.of(itemVariacaoSemCusto(), itemVariacaoSemCusto(), itemVariacaoSemCusto()),
                List.of());
        when(servicoPainelAnalista.pendencias()).thenReturn(resultado);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder(pergunta);

        Set<String> pool = new HashSet<>();
        pool.addAll(digitosDe(String.valueOf(resultado.eventosComErro().size())));
        pool.addAll(digitosDe(String.valueOf(resultado.itensSemVariacao().size())));
        pool.addAll(digitosDe(String.valueOf(resultado.variacoesSemCusto().size())));
        pool.addAll(digitosDe(String.valueOf(resultado.devolucoesAbertas().size())));

        assertTodoDigitoRastreavel(resposta.texto(), pergunta, pool);
    }

    private ItemEventoComErro itemEventoComErro() {
        return new ItemEventoComErro(UUID.randomUUID(), UUID.randomUUID(), "PEDIDO_CRIADO", "ext-1",
                "erro de teste", OffsetDateTime.now(), 1, "acao");
    }

    private ItemPedidoSemVariacao itemPedidoSemVariacao() {
        return new ItemPedidoSemVariacao(UUID.randomUUID(), UUID.randomUUID(), "sku-1", "titulo",
                OffsetDateTime.now(), "acao");
    }

    private ItemVariacaoSemCusto itemVariacaoSemCusto() {
        return new ItemVariacaoSemCusto(UUID.randomUUID(), "sku-1", "descricao", OffsetDateTime.now(), "acao");
    }

    // ------------------------------------------------------------------
    // CANAIS_DISPONIVEIS
    // ------------------------------------------------------------------

    @Test
    void canaisDisponiveisNaoInventaDigito() {
        String pergunta = "quais canais eu tenho cadastrados?";
        PortaModeloLinguagem porta = (p, cat) -> new IntencaoDetectada(
                CodigoIntencao.CANAIS_DISPONIVEIS.name(), Map.of(), new BigDecimal("0.90"));

        List<Canal> canais = List.of(
                DublesDeTeste.canal("ml-classico", "Mercado Livre Classico"),
                DublesDeTeste.canal("shopee", "Shopee"));
        when(repositorioCanal.findAllByOrderByNomeAsc()).thenReturn(canais);

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder(pergunta);

        Set<String> pool = new HashSet<>();
        pool.addAll(digitosDe(String.valueOf(canais.size())));

        assertTodoDigitoRastreavel(resposta.texto(), pergunta, pool);
    }

    @Test
    void canaisDisponiveisSemCanalCadastradoNaoInventaDigito() {
        String pergunta = "quais canais eu tenho cadastrados?";
        PortaModeloLinguagem porta = (p, cat) -> new IntencaoDetectada(
                CodigoIntencao.CANAIS_DISPONIVEIS.name(), Map.of(), new BigDecimal("0.90"));

        when(repositorioCanal.findAllByOrderByNomeAsc()).thenReturn(List.of());

        ServicoPergunta servico = novoServico(porta);
        RespostaPergunta resposta = servico.responder(pergunta);

        assertTodoDigitoRastreavel(resposta.texto(), pergunta, Set.of());
    }
}
