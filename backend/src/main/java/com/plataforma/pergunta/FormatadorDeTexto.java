package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.plataforma.margem.Apresentacao;

/**
 * Formatação de número e data PARA TEXTO de resposta (borda de saída da
 * camada de pergunta). Escala e arredondamento de dinheiro continuam
 * sendo decisão exclusiva de {@link Apresentacao#paraExibicao(BigDecimal)}
 * - esta classe só troca o separador decimal de ponto para vírgula e
 * agrega o símbolo/sufixo. Nunca formate um valor monetário sem passar
 * por aqui, e nunca invente uma segunda rotina de arredondamento.
 */
final class FormatadorDeTexto {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FormatadorDeTexto() {
        // classe utilitaria: sem instancia
    }

    static String moeda(BigDecimal valor) {
        return "R$ " + Apresentacao.paraExibicao(valor).toPlainString().replace('.', ',');
    }

    /** @param fracao a margem como FRAÇÃO decimal (0,2249) - convertida aqui para ponto percentual (22,49%). */
    static String percentual(BigDecimal fracao) {
        return Apresentacao.paraExibicao(fracao.multiply(CEM)).toPlainString().replace('.', ',') + "%";
    }

    static String data(OffsetDateTime momento) {
        return momento.format(FORMATO_DATA);
    }
}
