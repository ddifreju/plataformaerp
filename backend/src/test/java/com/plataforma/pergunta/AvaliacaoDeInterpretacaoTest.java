package com.plataforma.pergunta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AVALIAÇÃO com limiar (regra 4 do CLAUDE.md: "saída de LLM: avaliação
 * com threshold, nunca asserção") de {@link ModeloHeuristico} - o piso de
 * qualidade contra o qual {@code ModeloAnthropic} vai ser comparado quando
 * a chave de LLM existir (decisão 0030). NENHUM caso individual deste
 * arquivo pode fazer o teste falhar sozinho: o teste falha só pelos TRÊS
 * limiares declarados no final da classe, medidos sobre o corpus inteiro.
 *
 * <h2>Por que {@link ModeloHeuristico} de verdade, e não {@link ModeloGravado}</h2>
 * {@link ModeloGravado} é para PROVAR travas determinísticas com saída
 * ADVERSÁRIA construída de propósito ({@code InterpretacaoAdversariaTest}).
 * Esta suíte mede a QUALIDADE DE INTERPRETAÇÃO de verdade - por isso roda
 * contra a implementação real, com um dublê só de {@link RepositorioCanal}
 * (para não depender de banco).
 *
 * <h2>O corpus</h2>
 * Escrito como a lojista escreveria de verdade: curto, sem acento, com
 * erro de digitação, com gíria de e-commerce. Inclui perguntas que DEVEM
 * ser recusadas (fora do catálogo) - recusar corretamente conta como
 * acerto, é um comportamento correto da decisão 0030 ("recusa é caminho
 * de primeira classe"), não uma lacuna do corpus.
 *
 * <h2>As três métricas</h2>
 * <ul>
 *   <li><b>Taxa de intenção correta</b>: inclui RECUSA correta como acerto
 *       (obtido == esperado, os dois podendo ser "nenhum").</li>
 *   <li><b>Taxa de parâmetro correto</b>: só contada quando a intenção já
 *       bateu certo E o caso declara um parâmetro esperado - perguntas que
 *       não citam canal/período de propósito (a intenção pode ser inferida
 *       sem eles) não entram nesse denominador.</li>
 *   <li><b>Taxa de erro perigoso</b>: confiança ALTA (a heurística NÃO
 *       recusaria) combinada com intenção ERRADA - o pior caso possível,
 *       porque o sistema responde convicto e errado. Confiança baixa com
 *       intenção errada não é "perigoso": vira recusa, caminho seguro.</li>
 * </ul>
 */
class AvaliacaoDeInterpretacaoTest {

    private record CasoAvaliacao(String pergunta, CodigoIntencao intencaoEsperada, Optional<String> canalEsperado,
            Optional<PeriodoRelativo> periodoEsperado) {

        static CasoAvaliacao comParametros(String pergunta, CodigoIntencao intencao, String canal,
                PeriodoRelativo periodo) {
            return new CasoAvaliacao(pergunta, intencao, Optional.ofNullable(canal), Optional.ofNullable(periodo));
        }

        static CasoAvaliacao semParametro(String pergunta, CodigoIntencao intencao) {
            return new CasoAvaliacao(pergunta, intencao, Optional.empty(), Optional.empty());
        }

        static CasoAvaliacao recusa(String pergunta) {
            return new CasoAvaliacao(pergunta, null, Optional.empty(), Optional.empty());
        }
    }

    private record ResultadoCaso(CasoAvaliacao caso, CodigoIntencao obtido, java.math.BigDecimal confianca,
            boolean intencaoCorreta, Boolean parametroCorreto, boolean perigoso) {
    }

    // ------------------------------------------------------------------
    // Corpus - no minimo 40 perguntas exigido pela tarefa 25; 51 aqui.
    // ------------------------------------------------------------------

    private static final List<CasoAvaliacao> CORPUS = List.of(
            // ---------------- MARGEM_DO_PERIODO ----------------
            CasoAvaliacao.comParametros("quanto sobrou no mes passado no mercado livre",
                    CodigoIntencao.MARGEM_DO_PERIODO, "Mercado Livre", PeriodoRelativo.MES_PASSADO),
            CasoAvaliacao.comParametros("quanto sobrou na loja propria esse mes",
                    CodigoIntencao.MARGEM_DO_PERIODO, "Loja Própria", PeriodoRelativo.MES_ATUAL),
            CasoAvaliacao.comParametros("quanto entrou limpo no shopee nos ultimos 30 dias",
                    CodigoIntencao.MARGEM_DO_PERIODO, "Shopee", PeriodoRelativo.ULTIMOS_30_DIAS),
            CasoAvaliacao.comParametros("qual foi meu lucro na loja propria esse ano",
                    CodigoIntencao.MARGEM_DO_PERIODO, "Loja Própria", PeriodoRelativo.ANO_ATUAL),
            CasoAvaliacao.comParametros("quanto ganhei de verdade no mercado livre nos ultimos 90 dias",
                    CodigoIntencao.MARGEM_DO_PERIODO, "Mercado Livre", PeriodoRelativo.ULTIMOS_90_DIAS),
            CasoAvaliacao.comParametros("resultado do shopee no mes passado",
                    CodigoIntencao.MARGEM_DO_PERIODO, "Shopee", PeriodoRelativo.MES_PASSADO),
            CasoAvaliacao.comParametros("margem do mercado livre nos ultimos 7 dias",
                    CodigoIntencao.MARGEM_DO_PERIODO, "Mercado Livre", PeriodoRelativo.ULTIMOS_7_DIAS),
            CasoAvaliacao.comParametros("faturamento bruto do shopee esse mes",
                    CodigoIntencao.MARGEM_DO_PERIODO, "Shopee", PeriodoRelativo.MES_ATUAL),

            // ---------------- LACUNAS_DA_MARGEM ----------------
            CasoAvaliacao.comParametros("o que ta faltando pra fechar a margem do mercado livre esse mes",
                    CodigoIntencao.LACUNAS_DA_MARGEM, "Mercado Livre", PeriodoRelativo.MES_ATUAL),
            CasoAvaliacao.comParametros("quais dados faltam pra loja propria mes passado",
                    CodigoIntencao.LACUNAS_DA_MARGEM, "Loja Própria", PeriodoRelativo.MES_PASSADO),
            CasoAvaliacao.comParametros("pq a margem do shopee ta incompleta esse mes",
                    CodigoIntencao.LACUNAS_DA_MARGEM, "Shopee", PeriodoRelativo.MES_ATUAL),
            CasoAvaliacao.semParametro("que informacao falta pra eu confiar na margem do mercado livre",
                    CodigoIntencao.LACUNAS_DA_MARGEM),
            CasoAvaliacao.semParametro("o que falta cadastrar pra fechar o resultado da loja propria",
                    CodigoIntencao.LACUNAS_DA_MARGEM),
            CasoAvaliacao.comParametros("pq nao da pra confiar na margem do shopee ultimos 30 dias",
                    CodigoIntencao.LACUNAS_DA_MARGEM, "Shopee", PeriodoRelativo.ULTIMOS_30_DIAS),
            CasoAvaliacao.comParametros("tem taxa faltando no mercado livre esse mes",
                    CodigoIntencao.LACUNAS_DA_MARGEM, "Mercado Livre", PeriodoRelativo.MES_ATUAL),
            CasoAvaliacao.comParametros("o que preciso resolver pra fechar a margem da loja propria este ano",
                    CodigoIntencao.LACUNAS_DA_MARGEM, "Loja Própria", PeriodoRelativo.ANO_ATUAL),
            CasoAvaliacao.semParametro("pq a margem ta com teto", CodigoIntencao.LACUNAS_DA_MARGEM),

            // ---------------- GARGALOS_DA_OPERACAO ----------------
            CasoAvaliacao.semParametro("onde a operacao ta travando", CodigoIntencao.GARGALOS_DA_OPERACAO),
            CasoAvaliacao.semParametro("tem pedido parado", CodigoIntencao.GARGALOS_DA_OPERACAO),
            CasoAvaliacao.semParametro("cade os pedidos que sumiram", CodigoIntencao.GARGALOS_DA_OPERACAO),
            CasoAvaliacao.semParametro("quais sao os gargalos de hoje", CodigoIntencao.GARGALOS_DA_OPERACAO),
            CasoAvaliacao.semParametro("o que ta travado no processo", CodigoIntencao.GARGALOS_DA_OPERACAO),
            CasoAvaliacao.semParametro("tem devolucao parada em algum status", CodigoIntencao.GARGALOS_DA_OPERACAO),
            CasoAvaliacao.semParametro("onde esta o gargalo da operacao", CodigoIntencao.GARGALOS_DA_OPERACAO),
            CasoAvaliacao.semParametro("que porcentagem de pedido ta sem custo", CodigoIntencao.GARGALOS_DA_OPERACAO),

            // ---------------- FILA_DE_PENDENCIAS ----------------
            CasoAvaliacao.semParametro("o que eu preciso resolver hoje", CodigoIntencao.FILA_DE_PENDENCIAS),
            CasoAvaliacao.semParametro("quais pendencias estao na minha fila", CodigoIntencao.FILA_DE_PENDENCIAS),
            CasoAvaliacao.semParametro("tem alguma variacao sem custo", CodigoIntencao.FILA_DE_PENDENCIAS),
            CasoAvaliacao.semParametro("o que ta pendente pra eu resolver", CodigoIntencao.FILA_DE_PENDENCIAS),
            CasoAvaliacao.semParametro("tem item de pedido sem variacao", CodigoIntencao.FILA_DE_PENDENCIAS),
            CasoAvaliacao.semParametro("quais devolucoes estao em aberto", CodigoIntencao.FILA_DE_PENDENCIAS),
            CasoAvaliacao.semParametro("tem evento de ingestao com erro", CodigoIntencao.FILA_DE_PENDENCIAS),
            CasoAvaliacao.semParametro("o que falta eu resolver na fila hoje", CodigoIntencao.FILA_DE_PENDENCIAS),

            // ---------------- CANAIS_DISPONIVEIS ----------------
            CasoAvaliacao.semParametro("cade meus canais", CodigoIntencao.CANAIS_DISPONIVEIS),
            CasoAvaliacao.semParametro("quais canais eu tenho cadastrado", CodigoIntencao.CANAIS_DISPONIVEIS),
            CasoAvaliacao.semParametro("me mostra os canais de venda", CodigoIntencao.CANAIS_DISPONIVEIS),
            CasoAvaliacao.semParametro("quantos canais eu tenho", CodigoIntencao.CANAIS_DISPONIVEIS),
            CasoAvaliacao.semParametro("lista os canais disponiveis", CodigoIntencao.CANAIS_DISPONIVEIS),
            CasoAvaliacao.semParametro("quais sao meus canais", CodigoIntencao.CANAIS_DISPONIVEIS),
            CasoAvaliacao.semParametro("canais que eu tenho hoje", CodigoIntencao.CANAIS_DISPONIVEIS),
            CasoAvaliacao.semParametro("me fala quais canais existem", CodigoIntencao.CANAIS_DISPONIVEIS),

            // ---------------- RECUSA (fora do catalogo) ----------------
            CasoAvaliacao.recusa("qual a previsao do tempo hoje"),
            CasoAvaliacao.recusa("me manda o boleto"),
            CasoAvaliacao.recusa("qual a capital da franca"),
            CasoAvaliacao.recusa("voce pode me ligar"),
            CasoAvaliacao.recusa("quanto custa o frete dos correios"),
            CasoAvaliacao.recusa("qual o cnpj da empresa"),
            CasoAvaliacao.recusa("to perdendo dinheiro em que"),
            CasoAvaliacao.recusa("vc pode criar um relatorio em pdf"),
            CasoAvaliacao.recusa("qual a cotacao do dolar hoje"),
            CasoAvaliacao.recusa("manda um oi pro meu cliente"));

    // ------------------------------------------------------------------
    // LIMIARES - o teste falha só aqui, nunca por caso individual.
    //
    // CALIBRADOS pela execução real desta suíte contra ModeloHeuristico
    // (rode `./mvnw -Dtest=AvaliacaoDeInterpretacaoTest test` e leia a
    // tabela impressa no console). Medido nesta versão do corpus:
    // intenção correta 82,4% (42/51), parâmetro correto 100% (12/12),
    // erro perigoso 3,9% (2/51). Os limiares abaixo ficam LOGO ABAIXO
    // (intenção/parâmetro) ou LOGO ACIMA (erro perigoso) do medido - uma
    // margem pequena, não uma meta confortável - para o teste continuar
    // determinístico (a heurística não tem nenhuma fonte de aleatoriedade)
    // e ainda assim FALHAR se qualquer mudança piorar a qualidade de
    // verdade.
    //
    // São um PISO A SUBIR quando ModeloAnthropic existir, nunca uma meta
    // atingida - a heurística é casamento de palavra-chave sem
    // sinônimo/stemming (decisão 0030: "conservador por construção"),
    // então errar em gíria que usa um verbo/plural diferente do catálogo,
    // ou em pergunta ambígua entre MARGEM_DO_PERIODO e LACUNAS_DA_MARGEM
    // (vocabulário parecido: "margem", nome de canal, "mês atual"), é o
    // comportamento ESPERADO desta implementação, não um bug a esconder
    // subindo o limiar. Se baixar estes números, a suíte deve falhar - é
    // o que prova que a heurística não regrediu.
    // ------------------------------------------------------------------

    private static final double LIMIAR_TAXA_INTENCAO_CORRETA = 0.80;
    private static final double LIMIAR_TAXA_PARAMETRO_CORRETO = 0.90;
    private static final double LIMIAR_MAXIMO_TAXA_ERRO_PERIGOSO = 0.06;

    @Test
    void heuristicaAtendeOsLimiaresDeQualidadeDeclarados() {
        RepositorioCanal repositorioCanal = mock(RepositorioCanal.class);
        when(repositorioCanal.findAllByOrderByNomeAsc()).thenReturn(List.of(
                canal("mercado-livre", "Mercado Livre"),
                canal("shopee", "Shopee"),
                canal("loja-propria", "Loja Própria")));

        ModeloHeuristico modelo = new ModeloHeuristico(repositorioCanal);
        CatalogoDePerguntas catalogo = new CatalogoDePerguntas();
        List<DescricaoIntencao> descricoes = catalogo.descricoes();

        List<ResultadoCaso> resultados = new ArrayList<>();
        for (CasoAvaliacao caso : CORPUS) {
            resultados.add(avaliar(caso, modelo, catalogo, descricoes));
        }

        imprimirRelatorio(resultados);

        long intencoesCorretas = resultados.stream().filter(ResultadoCaso::intencaoCorreta).count();
        double taxaIntencaoCorreta = (double) intencoesCorretas / resultados.size();

        List<ResultadoCaso> comParametroAvaliavel = resultados.stream()
                .filter(r -> r.parametroCorreto() != null)
                .toList();
        long parametrosCorretos = comParametroAvaliavel.stream().filter(ResultadoCaso::parametroCorreto).count();
        double taxaParametroCorreto = comParametroAvaliavel.isEmpty() ? 1.0
                : (double) parametrosCorretos / comParametroAvaliavel.size();

        long perigosos = resultados.stream().filter(ResultadoCaso::perigoso).count();
        double taxaErroPerigoso = (double) perigosos / resultados.size();

        System.out.printf(Locale.ROOT,
                "%n=== RESUMO DA AVALIACAO (ModeloHeuristico, corpus de %d perguntas) ===%n", resultados.size());
        System.out.printf(Locale.ROOT, "Taxa de intencao correta:  %.1f%% (limiar minimo: %.1f%%)%n",
                taxaIntencaoCorreta * 100, LIMIAR_TAXA_INTENCAO_CORRETA * 100);
        System.out.printf(Locale.ROOT, "Taxa de parametro correto: %.1f%% (limiar minimo: %.1f%%, n=%d casos)%n",
                taxaParametroCorreto * 100, LIMIAR_TAXA_PARAMETRO_CORRETO * 100, comParametroAvaliavel.size());
        System.out.printf(Locale.ROOT, "Taxa de erro perigoso:     %.1f%% (limiar maximo: %.1f%%)%n%n",
                taxaErroPerigoso * 100, LIMIAR_MAXIMO_TAXA_ERRO_PERIGOSO * 100);

        assertTrue(taxaIntencaoCorreta >= LIMIAR_TAXA_INTENCAO_CORRETA,
                "taxa de intencao correta caiu abaixo do piso declarado: " + taxaIntencaoCorreta);
        assertTrue(taxaParametroCorreto >= LIMIAR_TAXA_PARAMETRO_CORRETO,
                "taxa de parametro correto caiu abaixo do piso declarado: " + taxaParametroCorreto);
        assertTrue(taxaErroPerigoso <= LIMIAR_MAXIMO_TAXA_ERRO_PERIGOSO,
                "taxa de erro perigoso subiu acima do teto declarado: " + taxaErroPerigoso);
    }

    private ResultadoCaso avaliar(CasoAvaliacao caso, ModeloHeuristico modelo, CatalogoDePerguntas catalogo,
            List<DescricaoIntencao> descricoes) {
        IntencaoDetectada deteccao = modelo.interpretar(caso.pergunta(), descricoes);
        boolean confiancaAlta = deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0;
        CodigoIntencao obtido = confiancaAlta ? catalogo.resolver(deteccao.codigoBruto()).orElse(null) : null;

        boolean intencaoCorreta = Objects.equals(obtido, caso.intencaoEsperada());

        Boolean parametroCorreto = null;
        if (intencaoCorreta && caso.intencaoEsperada() != null
                && (caso.canalEsperado().isPresent() || caso.periodoEsperado().isPresent())) {
            boolean canalOk = caso.canalEsperado()
                    .map(esperado -> esperado.equals(deteccao.parametros().get("canal")))
                    .orElse(true);
            boolean periodoOk = caso.periodoEsperado()
                    .map(esperado -> esperado.name().equals(deteccao.parametros().get("periodoRelativo")))
                    .orElse(true);
            parametroCorreto = canalOk && periodoOk;
        }

        // Erro perigoso: confianca alta o bastante pra NAO recusar, e
        // intencao errada - o sistema responderia convicto e errado.
        boolean perigoso = confiancaAlta && !intencaoCorreta;

        return new ResultadoCaso(caso, obtido, deteccao.confianca(), intencaoCorreta, parametroCorreto, perigoso);
    }

    private void imprimirRelatorio(List<ResultadoCaso> resultados) {
        System.out.println();
        System.out.printf(Locale.ROOT, "%-62s | %-20s | %-20s | %6s | %-4s | %s%n",
                "pergunta", "esperado", "obtido", "conf.", "ok?", "perigoso?");
        System.out.println("-".repeat(140));
        for (ResultadoCaso r : resultados) {
            String esperado = r.caso().intencaoEsperada() == null ? "RECUSA" : r.caso().intencaoEsperada().name();
            String obtido = r.obtido() == null ? "RECUSA" : r.obtido().name();
            System.out.printf(Locale.ROOT, "%-62s | %-20s | %-20s | %6s | %-4s | %s%n",
                    truncar(r.caso().pergunta(), 62), esperado, obtido, r.confianca().toPlainString(),
                    r.intencaoCorreta() ? "OK" : "ERRO", r.perigoso() ? "SIM" : "-");
        }
        System.out.println();
    }

    private String truncar(String texto, int tamanho) {
        // Reticências ASCII de proposito ("...", nunca "…"): o console de
        // alguns terminais Windows nao usa UTF-8 por padrao e rende o
        // caractere unicode como "?" - puramente cosmetico no relatorio,
        // mas confunde quem esta lendo pra achar erro de digitacao.
        return texto.length() <= tamanho ? texto : texto.substring(0, tamanho - 3) + "...";
    }

    private Canal canal(String codigo, String nome) {
        return new Canal(codigo, nome, com.plataforma.canal.TipoCanal.MERCADO_LIVRE,
                com.plataforma.canal.CategoriaCanal.MARKETPLACE, null, null, null);
    }
}
