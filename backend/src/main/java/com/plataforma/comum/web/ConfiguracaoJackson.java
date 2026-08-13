package com.plataforma.comum.web;

import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Serializa TODO {@link BigDecimal} da API como STRING JSON, nunca como
 * numero JSON.
 *
 * PORQUE ISTO EXISTE (regra 2 do CLAUDE.md, "dinheiro nunca e float"):
 *
 * O padrao do Jackson e emitir BigDecimal como numero literal:
 * {@code {"valor": 199.90}}. O problema nao esta no Java - esta do outro
 * lado do fio. O {@code JSON.parse} do JavaScript converte todo literal
 * numerico para {@code double} de 64 bits ANTES de qualquer codigo do
 * cliente rodar, inclusive antes de um {@code reviver}. Ou seja: a
 * precisao que o backend guardou com tanto cuidado em NUMERIC(18,4) e
 * destruida na desserializacao, e nao ha nada que o frontend possa fazer
 * depois - o estrago ja aconteceu.
 *
 * Emitindo como string ({@code {"valor": "199.9000"}}), o valor chega
 * intacto e o frontend so precisa formatar para exibir.
 *
 * A decisao 0022 promete: "valor monetario chega como string decimal".
 * Sem esta classe, essa promessa era falsa no fio e so se sustentava
 * porque o frontend fazia a propria defesa (um parser que preserva os
 * literais como texto). Defesa em profundidade e boa, mas o CONTRATO
 * precisa dizer a verdade sozinho - senao o proximo cliente da API
 * (aplicativo, planilha, integracao do lojista) herda o bug silencioso.
 *
 * NAO usa toString(): {@link BigDecimal#toString()} pode emitir notacao
 * cientifica (ex.: 1E+2) para valores com expoente. toPlainString()
 * garante sempre a forma decimal simples, que e o que qualquer cliente
 * espera conseguir ler.
 */
@Configuration
public class ConfiguracaoJackson {

    @Bean
    public SimpleModule moduloDinheiroComoTexto() {
        SimpleModule modulo = new SimpleModule("dinheiro-como-texto");
        modulo.addSerializer(BigDecimal.class, new SerializadorDecimalComoTexto());
        return modulo;
    }

    private static final class SerializadorDecimalComoTexto extends JsonSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal valor, JsonGenerator gerador, SerializerProvider provedor)
                throws IOException {
            if (valor == null) {
                gerador.writeNull();
                return;
            }
            // toPlainString preserva a escala (199.9000 continua com 4
            // casas) e nunca usa notacao cientifica.
            gerador.writeString(valor.toPlainString());
        }
    }
}
