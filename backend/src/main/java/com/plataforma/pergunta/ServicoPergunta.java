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
import com.plataforma.margem.ConjuntoDeCanaisNaoDisjuntoException;
import com.plataforma.margem.Lacuna;
import com.plataforma.margem.ResultadoMargemConsolidada;
import com.plataforma.margem.ResultadoMargemPeriodo;
import com.plataforma.margem.ServicoMargemPeriodo;
import com.plataforma.margem.ServicoMargemPeriodoConsolidada;
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
 * auditoria que {@code ServicoMargemPeriodo.calcular}/
 * {@code ServicoMargemPeriodoConsolidada.calcular} já gravam por conta
 * própria (com {@code executadoPor} = o nome de cada um deles e
 * {@code pergunta = null}): aquela prova o NÚMERO; esta prova que ESTA
 * pergunta, com este texto, gerou esta resposta - são rastreabilidades
 * diferentes, cada uma com sua própria linha.
 *
 * <h2>MARGEM_DO_PERIODO/LACUNAS_DA_MARGEM sem canal nomeado (tarefa 33,
 * decisão 0033) - a decisão 0021 NÃO foi relaxada</h2>
 * Até a tarefa 33, canal ausente no texto da lojista virava ESCLARECIMENTO
 * sempre ("Para qual canal?") - era o único jeito de honrar a decisão
 * 0021 ("o endpoint de margem exige um canal, nunca uma lista"). Isso
 * continua exatamente igual: {@code POST /api/margem/periodo} continua
 * exigindo {@code canalId} único e obrigatório, e
 * {@link ServicoMargemPeriodo} nunca soma canal nenhum por conta própria -
 * nada nesta classe muda isso.
 * <p>
 * O que passou a existir é um SEGUNDO caminho, novo e separado:
 * {@link ServicoMargemPeriodoConsolidada}, que só soma um conjunto de
 * canais depois de provar que ele é comprovadamente disjunto (decisão
 * 0033). Pergunta sem canal nomeado tenta esse caminho sobre todos os
 * canais ATIVOS do tenant, via {@link #responderMargemSemCanalNomeado};
 * pergunta COM canal nomeado continua indo, sem nenhuma mudança de
 * comportamento, pelo caminho de canal único de sempre.
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

    /**
     * Acrescentada à mensagem de {@link ConjuntoDeCanaisNaoDisjuntoException}
     * (que já nomeia o canal/par bloqueado e a saída "declare o escopo") -
     * a SEGUNDA saída, que só faz sentido do ponto de vista da camada de
     * pergunta: perguntar de novo citando um único canal sempre funciona,
     * porque contorna qualquer problema de disjunção do conjunto (decisão
     * 0033, "esclarecimento útil, nunca recusa seca").
     */
    static final String SUGESTAO_PERGUNTAR_POR_CANAL =
            "Ou pergunte de novo citando o nome de um canal específico - respondo só sobre ele.";

    static final String SEM_CANAL_ATIVO_PARA_CONSOLIDAR = "Você ainda não tem nenhum canal ativo cadastrado. "
            + "Cadastre um canal e declare o escopo dele antes de perguntar sobre margem.";

    private final PortaModeloLinguagem portaModeloLinguagem;
    private final CatalogoDePerguntas catalogo;
    private final ValidadorDeParametros validador;
    private final ServicoMargemPeriodo servicoMargemPeriodo;
    private final ServicoMargemPeriodoConsolidada servicoMargemPeriodoConsolidada;
    private final ServicoPainelGestor servicoPainelGestor;
    private final ServicoPainelAnalista servicoPainelAnalista;
    private final RepositorioCanal repositorioCanal;
    private final RepositorioConsultaAuditada repositorioConsultaAuditada;

    public ServicoPergunta(PortaModeloLinguagem portaModeloLinguagem, CatalogoDePerguntas catalogo,
            ValidadorDeParametros validador, ServicoMargemPeriodo servicoMargemPeriodo,
            ServicoMargemPeriodoConsolidada servicoMargemPeriodoConsolidada,
            ServicoPainelGestor servicoPainelGestor, ServicoPainelAnalista servicoPainelAnalista,
            RepositorioCanal repositorioCanal, RepositorioConsultaAuditada repositorioConsultaAuditada) {
        this.portaModeloLinguagem = portaModeloLinguagem;
        this.catalogo = catalogo;
        this.validador = validador;
        this.servicoMargemPeriodo = servicoMargemPeriodo;
        this.servicoMargemPeriodoConsolidada = servicoMargemPeriodoConsolidada;
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
        ResultadoParametro<Periodo> periodoResultado = validador.validarPeriodo(deteccao.parametros());

        // "Canal nomeado" é decidido sobre o PARÂMETRO BRUTO, não sobre
        // ResultadoParametro<Canal> - a distinção que importa aqui é "a
        // lojista disse um canal" vs. "não disse nenhum", nunca "o canal
        // que ela disse existe". Um canal digitado errado/inexistente
        // continua indo pelo caminho de canal único (e vira ESCLARECIMENTO
        // "não encontrei esse canal" logo abaixo) - só a AUSÊNCIA do
        // parâmetro abre o caminho novo da tarefa 33.
        String canalNoTexto = deteccao.parametros() == null ? null : deteccao.parametros().get("canal");
        if (canalNoTexto == null || canalNoTexto.isBlank()) {
            return responderMargemSemCanalNomeado(textoDaPergunta, deteccao, codigo, focoLacunas, periodoResultado);
        }

        ResultadoParametro<Canal> canalResultado = validador.validarCanal(deteccao.parametros());
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
        return esclarecerComTexto(textoDaPergunta, deteccao, codigo, String.join(" ", mensagens));
    }

    private RespostaPergunta esclarecerComTexto(String textoDaPergunta, IntencaoDetectada deteccao,
            CodigoIntencao codigo, String texto) {
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
            texto = RespostaDeMargem.textoZeroPedidosUnico(canal, dataInicio, dataFim, escopo);
            numeros = List.of();
        } else if (focoLacunas) {
            texto = RespostaDeMargem.textoLacunasUnico(canal, dataInicio, dataFim, lacunasDescricao, escopo);
            numeros = List.of();
        } else {
            texto = RespostaDeMargem.textoMargemCompletaUnico(canal, dataInicio, dataFim, resultado, escopo,
                    lacunasDescricao);
            numeros = RespostaDeMargem.numerosUnico(resultado);
        }

        UUID auditoriaId = registrarAuditoriaMargem(textoDaPergunta, codigo, canal, periodo, resultado);

        return new RespostaPergunta(TipoResposta.RESPOSTA, texto, numeros, resultado.rotulo().name(),
                lacunasDescricao, auditoriaId, codigo.name(), parametrosUsados, List.of());
    }

    // ------------------------------------------------------------------
    // MARGEM_DO_PERIODO / LACUNAS_DA_MARGEM sem canal nomeado (tarefa 33)
    // ------------------------------------------------------------------

    /**
     * Caminho novo da tarefa 33 (decisão 0033) - ver o Javadoc da classe,
     * seção "a decisão 0021 NÃO foi relaxada", antes de mexer aqui.
     * Nenhum desfecho "assume um canal e responde": só RESPOSTA quando o
     * conjunto de canais ativos é comprovadamente disjunto, ESCLARECIMENTO
     * em todo outro caso.
     */
    private RespostaPergunta responderMargemSemCanalNomeado(String textoDaPergunta, IntencaoDetectada deteccao,
            CodigoIntencao codigo, boolean focoLacunas, ResultadoParametro<Periodo> periodoResultado) {
        if (!periodoResultado.valido()) {
            return esclarecerComTexto(textoDaPergunta, deteccao, codigo, periodoResultado.esclarecimento());
        }
        Periodo periodo = periodoResultado.valor();

        ResultadoMargemConsolidada resultado;
        try {
            // canaisPedidos = List.of() -> ServicoMargemPeriodoConsolidada
            // resolve sozinho "todos os canais ATIVOS do tenant" (decisão
            // 0033) - a mesma regra de negócio que o endpoint
            // /api/margem/periodo/consolidado usa quando "canais" vem
            // omitido do corpo, reaproveitada aqui, nunca reimplementada.
            resultado = servicoMargemPeriodoConsolidada.calcular(periodo.inicio(), periodo.fim(), List.of());
        } catch (ConjuntoDeCanaisNaoDisjuntoException erro) {
            // Reaproveita a mensagem que ServicoMargemPeriodoConsolidada já
            // monta (nomeia o canal/par bloqueado e já sugere "declare o
            // escopo") - nunca uma segunda versão escrita à mão da mesma
            // explicação. A segunda saída ("pergunte citando um canal") é
            // acrescentada aqui porque só faz sentido do ponto de vista
            // desta camada.
            String texto = erro.getMessage() + " " + SUGESTAO_PERGUNTAR_POR_CANAL;
            return esclarecerComTexto(textoDaPergunta, deteccao, codigo, texto);
        }

        if (resultado.canaisIncluidos().isEmpty()) {
            return esclarecerComTexto(textoDaPergunta, deteccao, codigo, SEM_CANAL_ATIVO_PARA_CONSOLIDAR);
        }

        return construirRespostaMargemConsolidada(textoDaPergunta, codigo, resultado, focoLacunas);
    }

    private RespostaPergunta construirRespostaMargemConsolidada(String textoDaPergunta, CodigoIntencao codigo,
            ResultadoMargemConsolidada resultado, boolean focoLacunas) {
        List<String> nomesCanais = nomesDosCanais(resultado.canaisIncluidos());
        String dataInicio = FormatadorDeTexto.data(resultado.inicio());
        String dataFim = FormatadorDeTexto.data(resultado.fim());
        String listaCanais = String.join(", ", nomesCanais);
        // Decisão 0017 ("nunca somar sem dizer o que somou"): o escopo
        // SEMPRE nomeia cada canal que entrou na soma, nunca só a contagem.
        String escopo = "Soma consolidada de " + nomesCanais.size() + " canal(is) comprovadamente disjunto(s): "
                + listaCanais + ". Para um canal só, pergunte de novo citando o nome dele.";
        List<String> lacunasDescricao = resultado.lacunas().stream().map(Lacuna::descricao).toList();
        Map<String, String> parametrosUsados = Map.of(
                "escopo", "CONSOLIDADO",
                "canais", listaCanais,
                "periodoInicio", resultado.inicio().toString(),
                "periodoFim", resultado.fim().toString());

        String texto;
        List<NumeroCitado> numeros;

        if (resultado.quantidadePedidos() == 0) {
            texto = RespostaDeMargem.textoZeroPedidosConsolidado(nomesCanais, dataInicio, dataFim, escopo);
            numeros = List.of();
        } else if (focoLacunas) {
            texto = RespostaDeMargem.textoLacunasConsolidado(nomesCanais, dataInicio, dataFim, lacunasDescricao,
                    escopo);
            numeros = List.of();
        } else {
            texto = RespostaDeMargem.textoMargemCompletaConsolidado(nomesCanais, dataInicio, dataFim, resultado,
                    escopo, lacunasDescricao);
            numeros = RespostaDeMargem.numerosConsolidado(resultado);
        }

        UUID auditoriaId = registrarAuditoriaMargemConsolidada(textoDaPergunta, codigo, resultado);

        return new RespostaPergunta(TipoResposta.RESPOSTA, texto, numeros, resultado.rotulo().name(),
                lacunasDescricao, auditoriaId, codigo.name(), parametrosUsados, List.of());
    }

    /**
     * Nomes dos canais consolidados, NA MESMA ORDEM de
     * {@code resultado.canaisIncluidos()} - o texto precisa nomear
     * explicitamente quais canais entraram na soma (decisão 0017).
     * {@code findAllById} já roda sob o mesmo predicado de tenant que todo
     * repositório com {@code @TenantId} recebe (decisão 0007), então um id
     * de outro tenant (que nunca deveria chegar aqui, já que
     * {@code canaisIncluidos} vem de uma consulta já escopada por tenant)
     * simplesmente não voltaria - daí o fallback para o próprio UUID em vez
     * de lançar exceção.
     */
    private List<String> nomesDosCanais(List<UUID> canaisIncluidos) {
        Map<UUID, String> nomePorId = repositorioCanal.findAllById(canaisIncluidos).stream()
                .collect(Collectors.toMap(Canal::getId, Canal::getNome));
        return canaisIncluidos.stream().map(id -> nomePorId.getOrDefault(id, id.toString())).toList();
    }

    private UUID registrarAuditoriaMargemConsolidada(String textoDaPergunta, CodigoIntencao codigo,
            ResultadoMargemConsolidada resultado) {
        Set<UUID> idsTotal = new LinkedHashSet<>();
        idsTotal.addAll(resultado.idsPedidoUsados());
        idsTotal.addAll(resultado.idsCustoUsados());

        String metodo = codigo == CodigoIntencao.LACUNAS_DA_MARGEM
                ? "responderLacunasDaMargemSemCanalNomeado" : "responderMargemSemCanalNomeado";
        String sqlExecutado = "ServicoPergunta." + metodo + ": delega a ServicoMargemPeriodoConsolidada.calcular("
                + "canais=" + resultado.canaisIncluidos() + ", periodo=[" + resultado.inicio() + ", "
                + resultado.fim() + ")) - consolidado sobre " + resultado.canaisIncluidos().size()
                + " canal(is) comprovadamente disjunto(s) (decisao 0033).";

        return registrarAuditoria(textoDaPergunta, sqlExecutado, idsTotal.toArray(new UUID[0]), idsTotal.size());
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
                        .map(item -> RotulosDeExibicao.de(item.status()) + ": " + item.quantidade())
                        .collect(Collectors.joining(", "));
        String devolucoesPorStatus = resultado.devolucoesPorStatus().isEmpty() ? "nenhuma devolução registrada"
                : resultado.devolucoesPorStatus().stream()
                        .map(item -> RotulosDeExibicao.de(item.status()) + ": " + item.quantidade())
                        .collect(Collectors.joining(", "));

        String texto = "Gargalos da operação. Pedidos por status: " + pedidosPorStatus + ". Devoluções por status: "
                + devolucoesPorStatus + ". Eventos de ingestão com erro: " + resultado.eventosIngestaoComErro()
                + ". Pedidos sem custo de mercadoria: " + resultado.pedidosSemCustoMercadoria() + ".";

        List<NumeroCitado> numeros = new ArrayList<>();
        resultado.pedidosPorStatus().forEach(item ->
                numeros.add(new NumeroCitado("Pedidos " + RotulosDeExibicao.de(item.status()),
                        String.valueOf(item.quantidade()))));
        resultado.devolucoesPorStatus().forEach(item ->
                numeros.add(new NumeroCitado("Devoluções " + RotulosDeExibicao.de(item.status()),
                        String.valueOf(item.quantidade()))));
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
