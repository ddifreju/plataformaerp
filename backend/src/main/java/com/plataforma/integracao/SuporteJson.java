package com.plataforma.integracao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Leitura de JSON de fonte compartilhada pelos adaptadores
 * (AdaptadorMercadoLivre, AdaptadorBling). Pacote-privada de proposito:
 * nao e API publica do modulo de integracao, so um utilitario interno.
 *
 * ---------------------------------------------------------------------
 * REGRA 2 DO CLAUDE.md ("dinheiro nunca e float") - O PONTO MAIS
 * IMPORTANTE DESTA CLASSE
 * ---------------------------------------------------------------------
 * {@link #MAPPER} e configurado com
 * {@code DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS}. Isso muda
 * como o PARSER do Jackson le um numero de ponto flutuante no JSON: em
 * vez de construir um {@code double} primeiro (perdendo a representacao
 * decimal exata por arredondamento binario) e so depois converter para
 * BigDecimal, o parser constroi o BigDecimal DIRETO dos digitos do texto
 * JSON, via maquina de estados sobre o buffer de caracteres - o double
 * nunca existe nesse caminho.
 *
 * E POR CAUSA DESTA UNICA LINHA que {@link #decimal(JsonNode, String)}
 * pode chamar {@code node.decimalValue()} com seguranca. Sem ela,
 * {@code node.decimalValue()} devolveria um BigDecimal construido a
 * partir de {@code node.asDouble()} (ou o Jackson entregaria um
 * DoubleNode em vez de DecimalNode) - o EXATO caminho que a regra 2
 * proíbe. Ver "Armadilhas de dinheiro" nos dois documentos de mapeamento
 * (docs/integracoes/mapeamento-mercadolivre.md e mapeamento-bling.md).
 */
final class SuporteJson {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    /** Escala e arredondamento de TODO valor monetario deste modulo (regra 2 do CLAUDE.md: declarar os dois, sempre). */
    static final int ESCALA_MONETARIA = 4;
    static final RoundingMode ARREDONDAMENTO_MONETARIO = RoundingMode.HALF_UP;

    private SuporteJson() {
        // classe utilitaria: sem instancia
    }

    static JsonNode lerArvore(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new PayloadInvalidoException("Payload vazio ou nulo - nao ha o que traduzir.");
        }
        try {
            return MAPPER.readTree(payloadJson);
        } catch (JsonProcessingException erro) {
            throw new PayloadInvalidoException("Payload nao e um JSON valido: " + erro.getOriginalMessage(), erro);
        }
    }

    static String texto(JsonNode pai, String campo) {
        if (pai == null) {
            return null;
        }
        JsonNode no = pai.get(campo);
        return (no == null || no.isNull()) ? null : no.asText();
    }

    static Integer inteiro(JsonNode pai, String campo) {
        if (pai == null) {
            return null;
        }
        JsonNode no = pai.get(campo);
        return (no == null || no.isNull()) ? null : no.asInt();
    }

    /**
     * Le um campo numerico (inteiro OU float) do JSON como BigDecimal,
     * seguro pela configuracao de {@link #MAPPER} descrita no javadoc da
     * classe. Devolve {@code null} quando o campo esta ausente ou
     * explicitamente {@code null} - CHAMADOR decide se isso vira
     * {@link CampoAusente} ou um {@code ZERO} default, nunca esta classe
     * (ela nao inventa dado, so le).
     */
    static BigDecimal decimal(JsonNode pai, String campo) {
        if (pai == null) {
            return null;
        }
        JsonNode no = pai.get(campo);
        if (no == null || no.isNull()) {
            return null;
        }
        if (!no.isNumber()) {
            // Reporta o NOME do campo e o TIPO recebido, nunca o CONTEUDO.
            // O no poderia ser um objeto aninhado inteiro do payload do
            // marketplace, com nome, endereco e contato do consumidor
            // final. Uma excecao costuma acabar em log ou em corpo de
            // resposta, e ai o dado pessoal ja vazou. A excecao nasce
            // limpa para nao depender de quem a captura lembrar disso.
            throw new PayloadInvalidoException(
                    "Campo '" + campo + "' deveria ser numerico e veio do tipo " + no.getNodeType() + ".");
        }
        return no.decimalValue();
    }

    /** Escala/arredondamento explicitos (regra 2 do CLAUDE.md) para todo valor monetario derivado ou lido. */
    static BigDecimal normalizar(BigDecimal valor) {
        return (valor == null) ? null : valor.setScale(ESCALA_MONETARIA, ARREDONDAMENTO_MONETARIO);
    }

    static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(ESCALA_MONETARIA, ARREDONDAMENTO_MONETARIO);
    }

    static OffsetDateTime dataHora(JsonNode pai, String campo) {
        String texto = texto(pai, campo);
        if (texto == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(texto);
        } catch (DateTimeParseException erro) {
            throw new PayloadInvalidoException(
                    "Campo de data/hora '" + campo + "' com formato inesperado: '" + texto + "'", erro);
        }
    }

    static ObjectNode objeto() {
        return MAPPER.createObjectNode();
    }

    static String textoJson(ObjectNode no) {
        try {
            return MAPPER.writeValueAsString(no);
        } catch (JsonProcessingException erro) {
            // Nao deveria acontecer: estamos serializando uma arvore que
            // o proprio adaptador construiu, nao dado externo arbitrario.
            throw new IllegalStateException("Falha inesperada serializando extensao para dados_origem", erro);
        }
    }
}
