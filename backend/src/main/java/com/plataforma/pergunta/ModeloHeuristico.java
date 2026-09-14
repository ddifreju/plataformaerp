package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

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
 * <h2>O que esta classe NÃO faz</h2>
 * Não extrai parâmetro (canal, período) do texto - sempre devolve
 * {@code parametros} vazio. Extrair "Mercado Livre" ou "mês passado" de
 * texto livre é interpretação, e uma heurística de palavra-chave errando
 * um parâmetro silenciosamente violaria a regra 5 do CLAUDE.md com risco
 * maior que simplesmente pedir esclarecimento. Um modelo de linguagem de
 * verdade ({@code ModeloAnthropic}, quando a chave chegar) é quem tem
 * capacidade real de extrair isso com segurança.
 */
@Component
public class ModeloHeuristico implements PortaModeloLinguagem {

    static final String CODIGO_SEM_INTENCAO = "NENHUMA_INTENCAO_IDENTIFICADA";

    private static final BigDecimal CONFIANCA_MATCH_UNICO = new BigDecimal("0.85");
    private static final BigDecimal CONFIANCA_EMPATE = new BigDecimal("0.30");
    private static final BigDecimal CONFIANCA_SEM_MATCH = new BigDecimal("0.10");

    private static final Pattern SEPARADORES = Pattern.compile("[^a-z0-9]+");

    // Palavras de ligação em português: não carregam sinal de intenção
    // nenhuma, e deixá-las contar pontuaria toda pergunta bem formada por
    // acidente. Lista curta e explícita de propósito - cresce sob demanda,
    // não por antecipação.
    private static final Set<String> PALAVRAS_IGNORADAS = Set.of(
            "a", "o", "as", "os", "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas",
            "um", "uma", "uns", "umas", "e", "ou", "foi", "sao", "ser", "estao",
            "para", "por", "com", "sem", "que", "qual", "quais", "como", "onde", "quando",
            "este", "esta", "esse", "essa", "isso", "isto", "meu", "minha", "meus", "minhas",
            "seu", "sua", "seus", "suas", "eu", "voce", "tem", "ha", "me", "eles", "elas",
            "num", "numa", "ao", "aos", "mais", "muito", "tenho");

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

        return new IntencaoDetectada(empatados.get(0).name(), Map.of(), CONFIANCA_MATCH_UNICO);
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
