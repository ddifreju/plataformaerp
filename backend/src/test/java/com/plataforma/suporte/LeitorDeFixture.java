package com.plataforma.suporte;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Le fixtures gravadas em src/test/resources/fixtures/** como String, para
 * os testes de adaptador (tradução, puros) e de ingestão (pipeline, com
 * banco) nao duplicarem a mesma leitura de classpath resource.
 */
public final class LeitorDeFixture {

    private LeitorDeFixture() {
        // classe utilitaria: sem instancia
    }

    /**
     * @param caminhoNoClasspath caminho absoluto no classpath, ex.:
     *                           "/fixtures/mercadolivre/pedido-completo.json"
     */
    public static String ler(String caminhoNoClasspath) {
        try (InputStream entrada = LeitorDeFixture.class.getResourceAsStream(caminhoNoClasspath)) {
            if (entrada == null) {
                throw new IllegalArgumentException("Fixture nao encontrada no classpath: " + caminhoNoClasspath);
            }
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException erro) {
            throw new UncheckedIOException("Falha lendo fixture " + caminhoNoClasspath, erro);
        }
    }
}
