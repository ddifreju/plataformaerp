package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.plataforma.suporte.LeitorDeFixture;

/**
 * Dublê de {@link PortaModeloLinguagem} - responsabilidade de teste da
 * tarefa 25, prevista no Javadoc de {@link PortaModeloLinguagem} e na
 * decisão 0030 ("ModeloGravado"). Devolve {@link IntencaoDetectada}
 * GRAVADAS em fixture JSON, indexadas pela pergunta normalizada. É o que
 * permite alimentar de propósito saídas que um LLM real produziria de
 * errado (código de intenção inexistente, confiança alta com parâmetro
 * lixo, canal de outro tenant, período invertido...) e provar por
 * asserção dura que as travas determinísticas seguram - ver
 * {@code InterpretacaoAdversariaTest}. A suíte de avaliação com limiar
 * (regra 4 do CLAUDE.md) usa {@link ModeloHeuristico} de verdade, não
 * este dublê - ver {@code AvaliacaoDeInterpretacaoTest}.
 *
 * <h2>Formato da fixture</h2>
 * Um array JSON de objetos, cada um com:
 * <pre>
 * {
 *   "pergunta": "texto exatamente como o teste vai perguntar",
 *   "codigoBruto": "MARGEM_DO_PERIODO",   // ou "", nunca omitido
 *   "confianca": "0.90",                  // string, parseada como BigDecimal
 *   "parametros": { "canal": "...", "periodoRelativo": "..." }  // opcional
 * }
 * </pre>
 * {@code codigoBruto} nunca é JSON {@code null}: {@link IntencaoDetectada}
 * proíbe {@code codigoBruto} nulo por contrato (ver o Javadoc dela - "use
 * string vazia/marcador, nunca null"), então uma fixture que precise
 * simular "o modelo não devolveu nenhum código" usa {@code ""}. O caso de
 * um código de intenção LITERALMENTE {@code null} é testado direto contra
 * {@link CatalogoDePerguntas#resolver(String)} (já coberto por
 * {@code CatalogoDePerguntasTest#rejeitaCodigoNulo}), porque não existe
 * como uma implementação de {@link PortaModeloLinguagem} produzir isso
 * sem violar o record.
 *
 * <h2>Por que {@link LeitorDeFixture} serve aqui, sem alteração</h2>
 * {@link LeitorDeFixture#ler(String)} resolve o caminho no classpath e
 * devolve a fixture como {@code String} - exatamente a entrada que esta
 * classe precisa para desserializar com Jackson (já uma dependência
 * transitiva do projeto via Spring Boot, mesma biblioteca que
 * {@code com.plataforma.integracao.SuporteJson} usa para os adaptadores).
 * Nenhuma responsabilidade nova cabe em {@code LeitorDeFixture}: ele só lê
 * bytes do classpath, e quem entende o formato pergunta→intenção é este
 * dublê, específico do pacote {@code pergunta}.
 *
 * <h2>Pergunta sem gravação: falha explícita e barulhenta</h2>
 * {@link #interpretar(String, List)} para uma pergunta que não bate (após
 * normalização) com nenhuma entrada gravada lança
 * {@link IllegalStateException} imediatamente, citando a pergunta recebida
 * e onde gravar a fixture que falta. NUNCA um fallback silencioso (baixa
 * confiança, código vazio, o que for) - um teste que dependa desse dublê e
 * receba essa exceção tem uma fixture faltando, não um bug do sistema sob
 * teste, e mascarar isso com um valor default esconderia exatamente o tipo
 * de bug que esta suíte existe para pegar.
 */
public final class ModeloGravado implements PortaModeloLinguagem {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, IntencaoDetectada> gravacoes;

    /**
     * @param caminhosDeFixtureNoClasspath um ou mais caminhos absolutos de
     *      classpath (ex.: {@code "/fixtures/pergunta/adversario.json"}) -
     *      cada um é um array JSON no formato descrito no Javadoc da classe.
     */
    public ModeloGravado(String... caminhosDeFixtureNoClasspath) {
        if (caminhosDeFixtureNoClasspath == null || caminhosDeFixtureNoClasspath.length == 0) {
            throw new IllegalArgumentException("ModeloGravado precisa de pelo menos um caminho de fixture");
        }
        Map<String, IntencaoDetectada> mapa = new LinkedHashMap<>();
        for (String caminho : caminhosDeFixtureNoClasspath) {
            carregarArquivo(caminho, mapa);
        }
        this.gravacoes = Map.copyOf(mapa);
    }

    @Override
    public IntencaoDetectada interpretar(String perguntaDoUsuario, List<DescricaoIntencao> catalogo) {
        String chave = normalizar(perguntaDoUsuario);
        IntencaoDetectada gravada = gravacoes.get(chave);
        if (gravada == null) {
            throw new IllegalStateException(
                    "ModeloGravado: nenhuma fixture gravada para a pergunta [" + perguntaDoUsuario
                            + "] (chave normalizada: [" + chave + "]). Grave uma entrada em "
                            + "backend/src/test/resources/fixtures/pergunta/ antes de usar esta pergunta em teste - "
                            + "um fallback silencioso aqui esconderia o proprio bug que o teste deveria pegar.");
        }
        return gravada;
    }

    private void carregarArquivo(String caminho, Map<String, IntencaoDetectada> mapa) {
        String conteudo = LeitorDeFixture.ler(caminho);
        JsonNode raiz;
        try {
            raiz = MAPPER.readTree(conteudo);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("Fixture de pergunta invalida (JSON malformado): " + caminho, erro);
        }
        if (!raiz.isArray()) {
            throw new IllegalStateException("Fixture de pergunta precisa ser um array JSON: " + caminho);
        }
        for (JsonNode entrada : raiz) {
            registrarEntrada(entrada, caminho, mapa);
        }
    }

    private void registrarEntrada(JsonNode entrada, String caminho, Map<String, IntencaoDetectada> mapa) {
        JsonNode perguntaNode = entrada.get("pergunta");
        if (perguntaNode == null || perguntaNode.asText("").isBlank()) {
            throw new IllegalStateException("Entrada sem campo 'pergunta' em " + caminho + ": " + entrada);
        }
        String pergunta = perguntaNode.asText();
        String chave = normalizar(pergunta);

        if (!entrada.has("codigoBruto") || entrada.get("codigoBruto").isNull()) {
            throw new IllegalStateException("Entrada sem 'codigoBruto' em " + caminho + " para pergunta [" + pergunta
                    + "] - use \"\" explicito para simular ausencia de codigo, nunca omita o campo.");
        }
        String codigoBruto = entrada.get("codigoBruto").asText();

        if (!entrada.has("confianca") || entrada.get("confianca").asText("").isBlank()) {
            throw new IllegalStateException(
                    "Entrada sem 'confianca' em " + caminho + " para pergunta [" + pergunta + "]");
        }
        BigDecimal confianca = new BigDecimal(entrada.get("confianca").asText());

        Map<String, String> parametros = new LinkedHashMap<>();
        JsonNode parametrosNode = entrada.get("parametros");
        if (parametrosNode != null && parametrosNode.isObject()) {
            parametrosNode.fields().forEachRemaining(campo -> parametros.put(campo.getKey(), campo.getValue().asText()));
        }

        IntencaoDetectada intencao = new IntencaoDetectada(codigoBruto, parametros, confianca);
        IntencaoDetectada existente = mapa.putIfAbsent(chave, intencao);
        if (existente != null) {
            throw new IllegalStateException("Fixture de pergunta duplicada (mesma pergunta normalizada) em " + caminho
                    + ": [" + pergunta + "]");
        }
    }

    /**
     * Mesma normalização usada pelo restante do pacote para casamento de
     * texto (minúsculo, sem acento) - ver {@code ModeloHeuristico.normalizar}
     * - mais colapso de espaço em branco, para que a formatação do JSON da
     * fixture (indentação, quebra de linha) nunca importe no casamento.
     */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
