package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Única saída de {@link PortaModeloLinguagem} (decisão 0030) - o modelo
 * classifica intenção e extrai parâmetro, nunca calcula nem redige número.
 *
 * {@code codigoBruto} é {@code String}, de propósito: o modelo pode
 * devolver um código que não existe no catálogo (alucinação, versão nova
 * de prompt, saída adversária de teste), e {@link ServicoPergunta} precisa
 * registrar o que ele disse em vez de mascarar isso convertendo direto
 * para {@link CodigoIntencao}. Quem decide se {@code codigoBruto} é válido
 * é {@link CatalogoDePerguntas#resolver(String)}, nunca este record.
 *
 * {@code confianca} em [0,1] - {@code BigDecimal} porque a regra do
 * dinheiro não se aplica aqui, mas a coerência de não usar {@code double}
 * sim, e porque o limiar de {@link ServicoPergunta#CONFIANCA_MINIMA} é
 * comparado com {@code compareTo}, onde precisão binária de double
 * poderia surpreender.
 */
public record IntencaoDetectada(String codigoBruto, Map<String, String> parametros, BigDecimal confianca) {

    public IntencaoDetectada {
        Objects.requireNonNull(codigoBruto, "codigoBruto não pode ser nulo - use string vazia/marcador, nunca null");
        Objects.requireNonNull(confianca, "confianca é obrigatória");
        if (confianca.compareTo(BigDecimal.ZERO) < 0 || confianca.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confianca precisa estar em [0,1], recebeu " + confianca);
        }
        parametros = parametros == null ? Map.of() : Map.copyOf(parametros);
    }
}
