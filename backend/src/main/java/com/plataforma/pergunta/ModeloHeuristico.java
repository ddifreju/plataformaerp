package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;

/**
 * Implementação de {@link PortaModeloLinguagem} que roda em produção
 * ENQUANTO NÃO HOUVER chave de LLM (ver {@code docs/PENDENCIAS.md}, "Chave
 * de API de LLM"). Quando a chave existir, entra {@code ModeloAnthropic} e
 * nada mais no pacote muda - trocar o adaptador é a única alteração
 * (decisão 0030).
 *
 * <h2>Como casa</h2>
 * Casamento por PALAVRA-CHAVE SOBRE O CATÁLOGO: o vocabulário de cada
 * intenção vem da própria {@code descricao} e dos {@code exemplosDePergunta}
 * de {@link DescricaoIntencao} - não existe uma segunda lista de palavras-chave
 * mantida à parte. Isso significa que o catálogo continua sendo a única
 * fonte de verdade (CLAUDE.md, "sem lógica de negócio espalhada") e que
 * escrever um exemplo de pergunta melhor no catálogo automaticamente
 * melhora o casamento, sem tocar esta classe.
 *
 * A pontuação de uma intenção é o número de palavras significativas da
 * pergunta que também aparecem no vocabulário dela (acento e caixa
 * normalizados, stopwords removidas).
 *
 * <h2>Conservador por construção (decisão 0030)</h2>
 * <ul>
 *   <li>Nenhuma intenção pontuou (todas com pontuação zero) → confiança
 *       BAIXA, {@code codigoBruto} marca "nenhuma intenção identificada".</li>
 *   <li>Duas ou mais intenções empatam na maior pontuação → confiança
 *       BAIXA, mesmo que a pontuação empatada seja alta. Um empate aqui
 *       significa "a pergunta parece com mais de uma coisa que eu sei
 *       responder", e adivinhar qual delas seria exatamente o "chute
 *       aproximado" que a decisão 0030 proíbe.</li>
 *   <li>Uma única intenção tem a maior pontuação → confiança ALTA.</li>
 * </ul>
 * A recusa (via {@link ServicoPergunta#CONFIANCA_MINIMA}) é o comportamento
 * correto nos dois primeiros casos, não uma falha desta classe.
 *
 * <h2>Extração de parâmetro (canal, período) - determinística, não IA</h2>
 * Esta classe casa {@code canal} e {@code periodoRelativo} contra um
 * conjunto FECHADO de expressões (nomes de canal do tenant e frases fixas
 * de período em português). Isso não é "a heurística virou um modelo de
 * linguagem": é casamento de texto puro, sem ambiguidade tolerada - se não
 * casa exatamente, o parâmetro fica AUSENTE e é {@link ValidadorDeParametros}
 * quem pede esclarecimento (nunca um valor chutado, regra 5 do CLAUDE.md).
 * Sem isso, {@code MARGEM_DO_PERIODO} e {@code LACUNAS_DA_MARGEM} nunca
 * chegariam a {@code RESPOSTA} de verdade nesta fase sem chave de LLM
 * (decisão 0029) - o catálogo de perguntas ficaria inútil na prática.
 *
 * <h3>Por que este componente pode ver os nomes dos canais do tenant</h3>
 * {@link RepositorioCanal#findAllByOrderByNomeAsc()} já roda dentro da
 * requisição autenticada do tenant (o filtro de tenant e o RLS do banco
 * garantem isso antes mesmo desta classe existir - decisão 0007), e o
 * resultado nunca sai desta JVM: é comparado em memória contra o texto da
 * pergunta e descartado. Não é o mesmo risco de mandar o nome do canal para
 * FORA da máquina.
 *
 * <b>Nota para quando {@code ModeloAnthropic} existir:</b> colocar nomes de
 * canal (dado do tenant) dentro de um prompt significa enviar esse dado a
 * um provedor de IA externo. Isso é decisão da fundadora, não do agente que
 * escreve código - ver {@code docs/CONTEXTO-HANDOFF.md} ("provedores de IA
 * externos") e o item correspondente em {@code docs/PENDENCIAS.md}.
 *
 * <h3>O que fica de fora, de propósito</h3>
 * Datas absolutas ("de 1 a 15 de agosto") e nomes de mês não são
 * interpretados aqui - resolver "agosto" para um ano exigiria inventar um
 * dado que o texto não deu (regra 5 do CLAUDE.md). Isso é o tipo de
 * ambiguidade que um modelo de linguagem de verdade resolve perguntando ou
 * usando contexto de conversa; a heurística prefere pedir esclarecimento.
 */
@Component
public class ModeloHeuristico implements PortaModeloLinguagem {

    static final String CODIGO_SEM_INTENCAO = "NENHUMA_INTENCAO_IDENTIFICADA";

    private static final BigDecimal CONFIANCA_MATCH_UNICO = new BigDecimal("0.85");
    private static final BigDecimal CONFIANCA_EMPATE = new BigDecimal("0.30");
    private static final BigDecimal CONFIANCA_SEM_MATCH = new BigDecimal("0.10");

    private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");

    /**
     * Conjunto FECHADO de expressões de período (já sem acento, comparadas
     * contra o texto normalizado da pergunta) - decisão 0030, "adivinhar
     * padrão novo é pior que pedir esclarecimento". Cresce sob demanda
     * observada (lojista usando uma frase que não está aqui), nunca por
     * antecipação. A ORDEM não importa: a escolha entre casamentos múltiplos
     * é sempre pela expressão mais LONGA (mais específica), calculada em
     * tempo de busca - ver {@link #extrairPeriodo(String)}.
     */
    private static final List<Map.Entry<String, PeriodoRelativo>> EXPRESSOES_DE_PERIODO = List.of(
            Map.entry("mes passado", PeriodoRelativo.MES_PASSADO),
            Map.entry("ultimo mes", PeriodoRelativo.MES_PASSADO),
            Map.entry("este mes", PeriodoRelativo.MES_ATUAL),
            Map.entry("esse mes", PeriodoRelativo.MES_ATUAL),
            Map.entry("mes atual", PeriodoRelativo.MES_ATUAL),
            Map.entry("no mes", PeriodoRelativo.MES_ATUAL),
            Map.entry("ultimos 7 dias", PeriodoRelativo.ULTIMOS_7_DIAS),
            Map.entry("ultima semana", PeriodoRelativo.ULTIMOS_7_DIAS),
            Map.entry("7 dias", PeriodoRelativo.ULTIMOS_7_DIAS),
            Map.entry("ultimo mes corrido", PeriodoRelativo.ULTIMOS_30_DIAS),
            Map.entry("ultimos 30 dias", PeriodoRelativo.ULTIMOS_30_DIAS),
            Map.entry("30 dias", PeriodoRelativo.ULTIMOS_30_DIAS),
            Map.entry("ultimos 90 dias", PeriodoRelativo.ULTIMOS_90_DIAS),
            Map.entry("90 dias", PeriodoRelativo.ULTIMOS_90_DIAS),
            Map.entry("trimestre", PeriodoRelativo.ULTIMOS_90_DIAS),
            Map.entry("este ano", PeriodoRelativo.ANO_ATUAL),
            // "esse ano" faltava aqui - assimetria com o grupo de "mes"
            // (que tem "este mes" E "esse mes") encontrada pela suite de
            // avaliacao com limiar (AvaliacaoDeInterpretacaoTest, tarefa
            // 25): "qual foi meu lucro na loja propria esse ano" extraia a
            // intencao certa mas ficava sem periodoRelativo. Mesmo
            // racional do par de "mes": "esse" e "este" sao intercambiaveis
            // no portugues falado pela lojista, nenhum dos dois e mais
            // correto que o outro.
            Map.entry("esse ano", PeriodoRelativo.ANO_ATUAL),
            Map.entry("ano atual", PeriodoRelativo.ANO_ATUAL),
            Map.entry("no ano", PeriodoRelativo.ANO_ATUAL));

    // Palavras de ligação em português: não carregam sinal de intenção
    // nenhuma, e deixá-las contar pontuaria toda pergunta bem formada por
    // acidente. Lista curta e explícita de propósito - cresce sob demanda,
    // não por antecipação.
    //
    // NOTA (AvaliacaoDeInterpretacaoTest, tarefa 25): "quanto" foi
    // deliberadamente NÃO adicionado aqui, apesar de ser a mesma classe
    // gramatical de "qual"/"quais"/"como"/"onde"/"quando" (todos acima) e
    // de causar um falso positivo medido ("quanto custa o frete dos
    // correios" bate confiança alta em MARGEM_DO_PERIODO, só por causa de
    // "quanto"). A tentativa de ignorá-lo foi reangolada: MARGEM_DO_PERIODO
    // e LACUNAS_DA_MARGEM compartilham quase todo o resto do vocabulário
    // (nome de canal, "mês", "atual", a palavra "margem" em si), e é
    // exatamente a contagem extra de "quanto" - presente nos exemplos de
    // MARGEM_DO_PERIODO e ausente dos de LACUNAS_DA_MARGEM - que hoje
    // desempata a favor de MARGEM_DO_PERIODO em várias frases realistas
    // (inclusive em ModeloHeuristicoTest, já verde). Ignorar "quanto"
    // troca um falso positivo ocasional (pergunta fora do domínio
    // respondida com confiança) por um aumento real de EMPATE entre as
    // duas intenções mais parecidas do catálogo - não é uma correção
    // isolada, é uma escolha de design sobre como desempatar essas duas
    // intenções, documentada como gap conhecido em
    // docs/avaliacao-camada-de-ia.md em vez de decidida por conta própria
    // aqui.
    private static final Set<String> PALAVRAS_IGNORADAS = Set.of(
            "a", "o", "as", "os", "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas",
            "um", "uma", "uns", "umas", "e", "ou", "foi", "sao", "ser", "estao",
            "para", "por", "com", "sem", "que", "qual", "quais", "como", "onde", "quando",
            "este", "esta", "esse", "essa", "isso", "isto", "meu", "minha", "meus", "minhas",
            "seu", "sua", "seus", "suas", "eu", "voce", "tem", "ha", "me", "eles", "elas",
            "num", "numa", "ao", "aos", "mais", "muito", "tenho");

    private final RepositorioCanal repositorioCanal;

    public ModeloHeuristico(RepositorioCanal repositorioCanal) {
        this.repositorioCanal = repositorioCanal;
    }

    @Override
    public IntencaoDetectada interpretar(String perguntaDoUsuario, List<DescricaoIntencao> catalogo) {
        Set<String> palavrasDaPergunta = palavrasSignificativas(perguntaDoUsuario);

        Map<CodigoIntencao, Integer> pontuacaoPorIntencao = new EnumMap<>(CodigoIntencao.class);
        for (DescricaoIntencao intencao : catalogo) {
            Set<String> vocabulario = vocabularioDaIntencao(intencao);
            int pontos = (int) palavrasDaPergunta.stream().filter(vocabulario::contains).count();
            pontuacaoPorIntencao.put(intencao.codigo(), pontos);
        }

        int maiorPontuacao = pontuacaoPorIntencao.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maiorPontuacao == 0) {
            return new IntencaoDetectada(CODIGO_SEM_INTENCAO, Map.of(), CONFIANCA_SEM_MATCH);
        }

        List<CodigoIntencao> empatados = pontuacaoPorIntencao.entrySet().stream()
                .filter(entrada -> entrada.getValue() == maiorPontuacao)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (empatados.size() > 1) {
            String codigoBruto = empatados.stream().map(Enum::name).collect(Collectors.joining("|"));
            return new IntencaoDetectada(codigoBruto, Map.of(), CONFIANCA_EMPATE);
        }

        // Extração de parâmetro só faz sentido quando existe UMA intenção
        // resolvida - em empate/sem-match não há para onde mandar o
        // parâmetro, e a confiança já resolveu o caso. Parâmetro
        // faltando aqui não muda a confiança (fica CONFIANCA_MATCH_UNICO
        // de qualquer forma): ausência de parâmetro é esclarecimento,
        // não dúvida sobre a intenção - são problemas diferentes.
        Map<String, String> parametros = extrairParametros(perguntaDoUsuario);
        return new IntencaoDetectada(empatados.get(0).name(), parametros, CONFIANCA_MATCH_UNICO);
    }

    /**
     * Extrai {@code canal} e {@code periodoRelativo} do texto livre, os
     * dois únicos parâmetros do catálogo hoje (ver
     * {@link CatalogoDePerguntas}). Nenhum dos dois é obrigatório aqui: um
     * mapa parcial (ou vazio) é o resultado correto quando o texto não dá
     * base segura para preencher - {@link ValidadorDeParametros} é quem
     * decide se falta pedir esclarecimento.
     */
    private Map<String, String> extrairParametros(String perguntaDoUsuario) {
        String perguntaNormalizada = normalizarComEspacos(perguntaDoUsuario);
        Map<String, String> parametros = new HashMap<>();

        String canal = extrairCanal(perguntaNormalizada);
        if (canal != null) {
            parametros.put("canal", canal);
        }

        PeriodoRelativo periodo = extrairPeriodo(perguntaNormalizada);
        if (periodo != null) {
            parametros.put("periodoRelativo", periodo.name());
        }

        return parametros;
    }

    /**
     * Casa nome e código de cada canal DO TENANT (ver Javadoc da classe,
     * "por que este componente pode ver os nomes dos canais") contra o
     * texto normalizado da pergunta. Casamento por substring simples de
     * propósito - o vocabulário é pequeno (canais cadastrados de um único
     * tenant) e a lista fechada de teste cobre o caso real ("no ml
     * classico"). Se MAIS DE UM canal casar, devolve {@code null}: não
     * escolhe, deixa o validador pedir para a lojista especificar.
     */
    private String extrairCanal(String perguntaNormalizada) {
        List<Canal> canais = repositorioCanal.findAllByOrderByNomeAsc();
        List<Canal> casados = new ArrayList<>();
        for (Canal canal : canais) {
            String nomeNormalizado = normalizarComEspacos(canal.getNome());
            String codigoNormalizado = normalizarComEspacos(canal.getCodigo());
            boolean casaPorNome = !nomeNormalizado.isBlank() && contemComoTrecho(perguntaNormalizada, nomeNormalizado);
            boolean casaPorCodigo = !codigoNormalizado.isBlank()
                    && contemComoTrecho(perguntaNormalizada, codigoNormalizado);
            if (casaPorNome || casaPorCodigo) {
                casados.add(canal);
            }
        }
        if (casados.size() == 1) {
            return casados.get(0).getNome();
        }
        return null;
    }

    /**
     * Casa a expressão de período MAIS LONGA (mais específica) dentre as
     * que aparecem no texto - "mes passado" vence "no mes" quando os dois
     * casam na mesma pergunta. Nenhum casamento devolve {@code null}, nunca
     * um palpite. Datas absolutas e nomes de mês ficam de fora de
     * propósito - ver Javadoc da classe.
     */
    private PeriodoRelativo extrairPeriodo(String perguntaNormalizada) {
        Map.Entry<String, PeriodoRelativo> melhorCasamento = null;
        for (Map.Entry<String, PeriodoRelativo> candidato : EXPRESSOES_DE_PERIODO) {
            if (!contemComoTrecho(perguntaNormalizada, candidato.getKey())) {
                continue;
            }
            if (melhorCasamento == null || candidato.getKey().length() > melhorCasamento.getKey().length()) {
                melhorCasamento = candidato;
            }
        }
        return melhorCasamento == null ? null : melhorCasamento.getValue();
    }

    /**
     * {@code contains} simples casaria "no mes" dentro de "no mesmo" -
     * palavra errada, período errado. Delimita o trecho com fronteira de
     * palavra ({@code \b}) dos dois lados para que só case quando o trecho
     * aparece como palavra(s) inteira(s) no texto, nunca como pedaço de
     * uma palavra maior.
     */
    private boolean contemComoTrecho(String textoNormalizado, String trecho) {
        return Pattern.compile("\\b" + Pattern.quote(trecho) + "\\b").matcher(textoNormalizado).find();
    }

    /**
     * Normalização usada para CASAMENTO DE TRECHO (canal, período): remove
     * acento e caixa como {@link #normalizar(String)}, mas troca hífen e
     * underscore por espaço e colapsa espaços repetidos - um código de
     * canal como {@code ml-classico} precisa casar com a lojista
     * escrevendo "ml classico". Deliberadamente diferente de
     * {@link #normalizar(String)}, que preserva o texto para tokenização
     * por {@link #palavrasSignificativas(String)}.
     */
    private String normalizarComEspacos(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = normalizar(texto);
        String semSeparador = semAcento.replaceAll("[-_]", " ");
        return semSeparador.replaceAll("\\s+", " ").trim();
    }

    private Set<String> vocabularioDaIntencao(DescricaoIntencao intencao) {
        Set<String> vocabulario = new java.util.HashSet<>(palavrasSignificativas(intencao.descricao()));
        for (String exemplo : intencao.exemplosDePergunta()) {
            vocabulario.addAll(palavrasSignificativas(exemplo));
        }
        return vocabulario;
    }

    private Set<String> palavrasSignificativas(String texto) {
        if (texto == null) {
            return Set.of();
        }
        String normalizado = normalizar(texto);
        List<String> tokens = new ArrayList<>(List.of(SEPARADORES.split(normalizado)));
        Set<String> resultado = new java.util.HashSet<>();
        for (String token : tokens) {
            if (token.isBlank() || token.length() <= 1) {
                continue;
            }
            if (PALAVRAS_IGNORADAS.contains(token)) {
                continue;
            }
            if (token.chars().allMatch(Character::isDigit)) {
                // Numero puro (data, quantidade) nao carrega vocabulario de
                // intencao - "30" em "ultimos 30 dias" nao deve competir
                // com nenhuma palavra do catalogo.
                continue;
            }
            resultado.add(token);
        }
        return resultado;
    }

    private String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(java.util.Locale.ROOT);
    }
}
