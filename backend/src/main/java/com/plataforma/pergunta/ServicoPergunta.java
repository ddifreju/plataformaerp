package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plataforma.auditoria.ConsultaAuditada;
import com.plataforma.auditoria.RepositorioConsultaAuditada;
import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.margem.Lacuna;
import com.plataforma.margem.ResultadoMargemPeriodo;
import com.plataforma.margem.RotuloTeto;
import com.plataforma.margem.ServicoMargemPeriodo;
import com.plataforma.painel.RespostaFilaPendencias;
import com.plataforma.painel.RespostaGargalosProcesso;
import com.plataforma.painel.ServicoPainelAnalista;
import com.plataforma.painel.ServicoPainelGestor;

/**
 * Executor do pipeline da decisão 0030:
 *
 * <pre>
 * texto livre -&gt; PortaModeloLinguagem.interpretar -&gt; validação determinística
 *             -&gt; executa o serviço já existente -&gt; template determinístico
 * </pre>
 *
 * Nenhum número deste serviço é gerado aqui - todo número em
 * {@link RespostaPergunta#texto()} vem de {@link FormatadorDeTexto} aplicado
 * em cima de um campo do resultado de {@code ServicoMargemPeriodo},
 * {@code ServicoPainelGestor}, {@code ServicoPainelAnalista} ou
 * {@code RepositorioCanal} - nunca calculado ou redigido dentro de um
 * template (ver {@code NenhumDigitoInventadoTest}, a trava desta regra).
 *
 * <h2>Auditoria (regra 3 do CLAUDE.md)</h2>
 * TODA chamada a {@link #responder(String)} grava uma
 * {@link ConsultaAuditada} própria, com {@code pergunta} = texto da
 * lojista e {@code executadoPor} = {@code "ServicoPergunta"} (nunca o
 * usuário - decisão 0012) - inclusive em RECUSA e ESCLARECIMENTO, onde não
 * há nenhum serviço de domínio para consultar. Isto é ADICIONAL à
 * auditoria que {@code ServicoMargemPeriodo.calcular} já grava por conta
 * própria (com {@code executadoPor = "ServicoMargemPeriodo"} e
 * {@code pergunta = null}): aquela prova o NÚMERO; esta prova que ESTA
 * pergunta, com este texto, gerou esta resposta - são rastreabilidades
 * diferentes, cada uma com sua própria linha.
 */
@Service
public class ServicoPergunta {

    /**
     * Limiar de confiança abaixo do qual a interpretação é tratada como
     * recusa (decisão 0030, "recusa é caminho de primeira classe").
     * CALIBRÁVEL: começa em 0,60 por julgamento de engenharia, não por
     * medição - é o {@code especialista-testes}, com a suíte de avaliação
     * contra corpus real (decisão 0030, "avaliação com limiar"), quem deve
     * ajustar este valor com dado, não achismo.
     */
    static final BigDecimal CONFIANCA_MINIMA = new BigDecimal("0.60");

    static final int TAMANHO_MAXIMO_PERGUNTA = 500;

    private static final String TEXTO_RECUSA = "Ainda não sei responder isso.";

    private final PortaModeloLinguagem portaModeloLinguagem;
    private final CatalogoDePerguntas catalogo;
    private final ValidadorDeParametros validador;
    private final ServicoMargemPeriodo servicoMargemPeriodo;
    private final ServicoPainelGestor servicoPainelGestor;
    private final ServicoPainelAnalista servicoPainelAnalista;
    private final RepositorioCanal repositorioCanal;
    private final RepositorioConsultaAuditada repositorioConsultaAuditada;

    public ServicoPergunta(PortaModeloLinguagem portaModeloLinguagem, CatalogoDePerguntas catalogo,
            ValidadorDeParametros validador, ServicoMargemPeriodo servicoMargemPeriodo,
            ServicoPainelGestor servicoPainelGestor, ServicoPainelAnalista servicoPainelAnalista,
            RepositorioCanal repositorioCanal, RepositorioConsultaAuditada repositorioConsultaAuditada) {
        this.portaModeloLinguagem = portaModeloLinguagem;
        this.catalogo = catalogo;
        this.validador = validador;
        this.servicoMargemPeriodo = servicoMargemPeriodo;
        this.servicoPainelGestor = servicoPainelGestor;
        this.servicoPainelAnalista = servicoPainelAnalista;
        this.repositorioCanal = repositorioCanal;
        this.repositorioConsultaAuditada = repositorioConsultaAuditada;
    }

    @Transactional
    public RespostaPergunta responder(String textoDaPergunta) {
        validarTexto(textoDaPergunta);

        IntencaoDetectada deteccao = portaModeloLinguagem.interpretar(textoDaPergunta, catalogo.descricoes());
        Optional<CodigoIntencao> codigoResolvido = catalogo.resolver(deteccao.codigoBruto());

        if (deteccao.confianca().compareTo(CONFIANCA_MINIMA) < 0 || codigoResolvido.isEmpty()) {
            return recusar(textoDaPergunta, deteccao);
        }

        CodigoIntencao codigo = codigoResolvido.get();
        return switch (codigo) {
            case MARGEM_DO_PERIODO -> responderMargem(textoDaPergunta, deteccao, codigo, false);
            case LACUNAS_DA_MARGEM -> responderMargem(textoDaPergunta, deteccao, codigo, true);
            case GARGALOS_DA_OPERACAO -> responderGargalos(textoDaPergunta);
            case FILA_DE_PENDENCIAS -> responderFilaDePendencias(textoDaPergunta);
            case CANAIS_DISPONIVEIS -> responderCanaisDisponiveis(textoDaPergunta);
        };
    }

    private void validarTexto(String textoDaPergunta) {
        if (textoDaPergunta == null || textoDaPergunta.isBlank()) {
            throw new PerguntaInvalidaException("A pergunta não pode ser vazia.");
        }
        if (textoDaPergunta.length() > TAMANHO_MAXIMO_PERGUNTA) {
            throw new PerguntaInvalidaException("A pergunta tem " + textoDaPergunta.length()
                    + " caracteres; o máximo é " + TAMANHO_MAXIMO_PERGUNTA + ".");
        }
    }

    // ------------------------------------------------------------------
    // RECUSA
    // ------------------------------------------------------------------

    private RespostaPergunta recusar(String textoDaPergunta, IntencaoDetectada deteccao) {
        String sqlExecutado = "PerguntaRecusada: nenhuma intencao do catalogo casou (codigoBruto="
                + deteccao.codigoBruto() + ", confianca=" + deteccao.confianca() + ")";
        UUID auditoriaId = registrarAuditoria(textoDaPergunta, sqlExecutado, new UUID[0], 0);

        return new RespostaPergunta(TipoResposta.RECUSA, TEXTO_RECUSA, List.of(), null, List.of(),
                auditoriaId, null, Map.of(), todosOsExemplos());
    }

    // ------------------------------------------------------------------
    // MARGEM_DO_PERIODO / LACUNAS_DA_MARGEM
    // ------------------------------------------------------------------

    private RespostaPergunta responderMargem(String textoDaPergunta, IntencaoDetectada deteccao,
            CodigoIntencao codigo, boolean focoLacunas) {
        ResultadoParametro<Canal> canalResultado = validador.validarCanal(deteccao.parametros());
        ResultadoParametro<Periodo> periodoResultado = validador.validarPeriodo(deteccao.parametros());

        if (!canalResultado.valido() || !periodoResultado.valido()) {
            return esclarecer(textoDaPergunta, deteccao, codigo, canalResultado, periodoResultado);
        }

        Canal canal = canalResultado.valor();
        Periodo periodo = periodoResultado.valor();
        ResultadoMargemPeriodo resultado = servicoMargemPeriodo.calcular(periodo.inicio(), periodo.fim(), canal.getId());

        return construirRespostaMargem(textoDaPergunta, codigo, canal, periodo, resultado, focoLacunas);
    }

    private RespostaPergunta esclarecer(String textoDaPergunta, IntencaoDetectada deteccao, CodigoIntencao codigo,
            ResultadoParametro<Canal> canalResultado, ResultadoParametro<Periodo> periodoResultado) {
        List<String> mensagens = new ArrayList<>();
        if (!canalResultado.valido()) {
            mensagens.add(canalResultado.esclarecimento());
        }
        if (!periodoResultado.valido()) {
            mensagens.add(periodoResultado.esclarecimento());
        }
        String texto = String.join(" ", mensagens);

        String sqlExecutado = "PerguntaComEsclarecimento: intencao=" + codigo.name() + "; motivo=" + texto;
        UUID auditoriaId = registrarAuditoria(textoDaPergunta, sqlExecutado, new UUID[0], 0);

        return new RespostaPergunta(TipoResposta.ESCLARECIMENTO, texto, List.of(), null, List.of(),
                auditoriaId, codigo.name(), deteccao.parametros(), exemplosPara(codigo));
    }

    private RespostaPergunta construirRespostaMargem(String textoDaPergunta, CodigoIntencao codigo, Canal canal,
            Periodo periodo, ResultadoMargemPeriodo resultado, boolean focoLacunas) {
        String dataInicio = FormatadorDeTexto.data(resultado.inicio());
        String dataFim = FormatadorDeTexto.data(resultado.fim());
        String escopo = "Este resultado vale só para o canal " + canal.getNome()
                + " - para outro canal, pergunte de novo citando o nome dele.";
        List<String> lacunasDescricao = resultado.lacunas().stream().map(Lacuna::descricao).toList();
        Map<String, String> parametrosUsados = Map.of(
                "canal", canal.getNome(),
                "periodoInicio", resultado.inicio().toString(),
                "periodoFim", resultado.fim().toString());

        String texto;
        List<NumeroCitado> numeros;

        if (resultado.quantidadePedidos() == 0) {
            // Regra dura: zero pedido nunca vira "R$ 0,00" apresentado como
            // resultado - é ausência declarada, não um valor calculado.
            texto = "Não há pedido no canal " + canal.getNome() + " entre " + dataInicio + " e " + dataFim + ". "
                    + escopo;
            numeros = List.of();
        } else if (focoLacunas) {
            texto = construirTextoLacunas(canal, dataInicio, dataFim, lacunasDescricao, escopo);
            numeros = List.of();
        } else {
            texto = construirTextoMargemCompleta(canal, dataInicio, dataFim, resultado, escopo, lacunasDescricao);
            numeros = numerosDaMargem(resultado);
        }

        UUID auditoriaId = registrarAuditoriaMargem(textoDaPergunta, codigo, canal, periodo, resultado);

        return new RespostaPergunta(TipoResposta.RESPOSTA, texto, numeros, resultado.rotulo().name(),
                lacunasDescricao, auditoriaId, codigo.name(), parametrosUsados, List.of());
    }

    private String construirTextoLacunas(Canal canal, String dataInicio, String dataFim,
            List<String> lacunasDescricao, String escopo) {
        if (lacunasDescricao.isEmpty()) {
            return "Não encontrei nenhuma lacuna: a margem do canal " + canal.getNome() + " entre " + dataInicio
                    + " e " + dataFim + " foi calculada sem dado faltando. " + escopo;
        }
        return "Faltam " + lacunasDescricao.size() + " coisa(s) para calcular com confiança a margem do canal "
                + canal.getNome() + " entre " + dataInicio + " e " + dataFim + ": "
                + String.join("; ", lacunasDescricao) + ". " + escopo;
    }

    private String construirTextoMargemCompleta(Canal canal, String dataInicio, String dataFim,
            ResultadoMargemPeriodo resultado, String escopo, List<String> lacunasDescricao) {
        StringBuilder texto = new StringBuilder();
        texto.append("Canal ").append(canal.getNome()).append(", de ").append(dataInicio).append(" a ")
                .append(dataFim).append(": ");
        for (NumeroCitado numero : numerosDaMargem(resultado)) {
            texto.append(numero.nome()).append(": ").append(numero.valor()).append(". ");
        }
        texto.append(escopo);

        if (resultado.rotulo() == RotuloTeto.COM_TETO) {
            texto.append(" Atenção: este valor é um teto - a margem real é menor. Faltam: ")
                    .append(String.join("; ", lacunasDescricao)).append(".");
        } else if (resultado.rotulo() == RotuloTeto.INDETERMINADA) {
            texto.append(" Atenção: não dá para calcular esta margem com confiança. Faltam: ")
                    .append(String.join("; ", lacunasDescricao)).append(".");
        }
        return texto.toString();
    }

    private List<NumeroCitado> numerosDaMargem(ResultadoMargemPeriodo resultado) {
        List<NumeroCitado> numeros = new ArrayList<>();
        numeros.add(new NumeroCitado("Faturamento bruto (N0)", FormatadorDeTexto.moeda(resultado.faturamentoBrutoN0())));
        numeros.add(new NumeroCitado("Receita líquida (N1)", FormatadorDeTexto.moeda(resultado.receitaLiquidaN1())));
        numeros.add(new NumeroCitado("Margem por pedido (N2)", FormatadorDeTexto.moeda(resultado.margemContribuicaoN2())));
        numeros.add(new NumeroCitado("Resultado do período (N3)", FormatadorDeTexto.moeda(resultado.resultadoPeriodoN3())));
        numeros.add(new NumeroCitado("Lucro operacional (N4)", FormatadorDeTexto.moeda(resultado.lucroOperacionalN4())));
        resultado.margemContribuicaoPercentual()
                .ifPresent(p -> numeros.add(new NumeroCitado("Margem por pedido (%)", FormatadorDeTexto.percentual(p))));
        resultado.margemLiquidaPercentual()
                .ifPresent(p -> numeros.add(new NumeroCitado("Margem líquida (%)", FormatadorDeTexto.percentual(p))));
        numeros.add(new NumeroCitado("Pedidos no período", String.valueOf(resultado.quantidadePedidos())));
        return numeros;
    }

    private UUID registrarAuditoriaMargem(String textoDaPergunta, CodigoIntencao codigo, Canal canal, Periodo periodo,
            ResultadoMargemPeriodo resultado) {
        Set<UUID> idsTotal = new LinkedHashSet<>();
        idsTotal.addAll(resultado.idsPedidoUsados());
        idsTotal.addAll(resultado.idsCustoUsados());

        String metodo = codigo == CodigoIntencao.LACUNAS_DA_MARGEM ? "responderLacunasDaMargem" : "responderMargem";
        String sqlExecutado = "ServicoPergunta." + metodo + ": delega a ServicoMargemPeriodo.calcular(canalId="
                + canal.getId() + ", periodo=[" + periodo.inicio() + ", " + periodo.fim() + ")).";

        return registrarAuditoria(textoDaPergunta, sqlExecutado, idsTotal.toArray(new UUID[0]), idsTotal.size());
    }

    // ------------------------------------------------------------------
    // GARGALOS_DA_OPERACAO
    // ------------------------------------------------------------------

    private RespostaPergunta responderGargalos(String textoDaPergunta) {
        RespostaGargalosProcesso resultado = servicoPainelGestor.gargalos();

        String pedidosPorStatus = resultado.pedidosPorStatus().isEmpty() ? "nenhum pedido registrado"
                : resultado.pedidosPorStatus().stream()
                        .map(item -> item.status().name() + ": " + item.quantidade())
                        .collect(Collectors.joining(", "));
        String devolucoesPorStatus = resultado.devolucoesPorStatus().isEmpty() ? "nenhuma devolução registrada"
                : resultado.devolucoesPorStatus().stream()
                        .map(item -> item.status().name() + ": " + item.quantidade())
                        .collect(Collectors.joining(", "));

        String texto = "Gargalos da operação. Pedidos por status: " + pedidosPorStatus + ". Devoluções por status: "
                + devolucoesPorStatus + ". Eventos de ingestão com erro: " + resultado.eventosIngestaoComErro()
                + ". Pedidos sem custo de mercadoria: " + resultado.pedidosSemCustoMercadoria() + ".";

        List<NumeroCitado> numeros = new ArrayList<>();
        resultado.pedidosPorStatus().forEach(item ->
                numeros.add(new NumeroCitado("Pedidos " + item.status().name(), String.valueOf(item.quantidade()))));
        resultado.devolucoesPorStatus().forEach(item ->
                numeros.add(new NumeroCitado("Devoluções " + item.status().name(), String.valueOf(item.quantidade()))));
        numeros.add(new NumeroCitado("Eventos de ingestão com erro", String.valueOf(resultado.eventosIngestaoComErro())));
        numeros.add(new NumeroCitado("Pedidos sem custo de mercadoria", String.valueOf(resultado.pedidosSemCustoMercadoria())));

        String sqlExecutado = "ServicoPergunta.responderGargalos: delega a ServicoPainelGestor.gargalos().";
        int linhasRetornadas = resultado.pedidosPorStatus().size() + resultado.devolucoesPorStatus().size();
        UUID auditoriaId = registrarAuditoria(textoDaPergunta, sqlExecutado, new UUID[0], linhasRetornadas);

        return new RespostaPergunta(TipoResposta.RESPOSTA, texto, numeros, null, List.of(), auditoriaId,
                CodigoIntencao.GARGALOS_DA_OPERACAO.name(), Map.of(), List.of());
    }

    // ------------------------------------------------------------------
    // FILA_DE_PENDENCIAS
    // ------------------------------------------------------------------

    private RespostaPergunta responderFilaDePendencias(String textoDaPergunta) {
        RespostaFilaPendencias resultado = servicoPainelAnalista.pendencias();

        int eventosComErro = resultado.eventosComErro().size();
        int itensSemVariacao = resultado.itensSemVariacao().size();
        int variacoesSemCusto = resultado.variacoesSemCusto().size();
        int devolucoesAbertas = resultado.devolucoesAbertas().size();

        String texto = "Fila de pendências: " + eventosComErro + " evento(s) de ingestão com erro, "
                + itensSemVariacao + " item(ns) de pedido sem variação, " + variacoesSemCusto
                + " variação(ões) sem custo cadastrado, " + devolucoesAbertas + " devolução(ões) em aberto.";

        List<NumeroCitado> numeros = List.of(
                new NumeroCitado("Eventos com erro", String.valueOf(eventosComErro)),
                new NumeroCitado("Itens sem variação", String.valueOf(itensSemVariacao)),
                new NumeroCitado("Variações sem custo", String.valueOf(variacoesSemCusto)),
                new NumeroCitado("Devoluções abertas", String.valueOf(devolucoesAbertas)));

        List<UUID> ids = new ArrayList<>();
        resultado.eventosComErro().forEach(item -> ids.add(item.id()));
        resultado.itensSemVariacao().forEach(item -> ids.add(item.id()));
        resultado.variacoesSemCusto().forEach(item -> ids.add(item.id()));
        resultado.devolucoesAbertas().forEach(item -> ids.add(item.id()));

        String sqlExecutado = "ServicoPergunta.responderFilaDePendencias: delega a ServicoPainelAnalista.pendencias().";
        UUID auditoriaId = registrarAuditoria(textoDaPergunta, sqlExecutado, ids.toArray(new UUID[0]), ids.size());

        return new RespostaPergunta(TipoResposta.RESPOSTA, texto, numeros, null, List.of(), auditoriaId,
                CodigoIntencao.FILA_DE_PENDENCIAS.name(), Map.of(), List.of());
    }

    // ------------------------------------------------------------------
    // CANAIS_DISPONIVEIS
    // ------------------------------------------------------------------

    private RespostaPergunta responderCanaisDisponiveis(String textoDaPergunta) {
        List<Canal> canais = repositorioCanal.findAllByOrderByNomeAsc();

        String texto;
        List<NumeroCitado> numeros;
        if (canais.isEmpty()) {
            texto = "Você ainda não tem nenhum canal cadastrado.";
            numeros = List.of();
        } else {
            String nomes = canais.stream().map(Canal::getNome).collect(Collectors.joining(", "));
            texto = "Você tem " + canais.size() + " canal(is) cadastrado(s): " + nomes + ".";
            numeros = List.of(new NumeroCitado("Canais cadastrados", String.valueOf(canais.size())));
        }

        String sqlExecutado = "ServicoPergunta.responderCanaisDisponiveis: RepositorioCanal.findAllByOrderByNomeAsc().";
        UUID[] ids = canais.stream().map(Canal::getId).toArray(UUID[]::new);
        UUID auditoriaId = registrarAuditoria(textoDaPergunta, sqlExecutado, ids, canais.size());

        return new RespostaPergunta(TipoResposta.RESPOSTA, texto, numeros, null, List.of(), auditoriaId,
                CodigoIntencao.CANAIS_DISPONIVEIS.name(), Map.of(), List.of());
    }

    // ------------------------------------------------------------------
    // Auxiliares comuns
    // ------------------------------------------------------------------

    private List<String> todosOsExemplos() {
        return catalogo.descricoes().stream()
                .flatMap(intencao -> intencao.exemplosDePergunta().stream())
                .toList();
    }

    private List<String> exemplosPara(CodigoIntencao codigo) {
        return catalogo.descricoes().stream()
                .filter(intencao -> intencao.codigo() == codigo)
                .findFirst()
                .map(DescricaoIntencao::exemplosDePergunta)
                .orElse(List.of());
    }

    /**
     * Grava a linha de {@code consulta_auditada} DESTA CAMADA - ver a nota
     * na classe sobre por que ela é adicional à de {@code ServicoMargemPeriodo}.
     * {@code executadoPor} é sempre {@code "ServicoPergunta"}, nunca o
     * usuário (decisão 0012).
     */
    private UUID registrarAuditoria(String pergunta, String sqlExecutado, UUID[] idsRetornados, int linhasRetornadas) {
        ConsultaAuditada consultaAuditada = new ConsultaAuditada(
                pergunta, sqlExecutado, idsRetornados, linhasRetornadas, "ServicoPergunta");
        return repositorioConsultaAuditada.save(consultaAuditada).getId();
    }
}
