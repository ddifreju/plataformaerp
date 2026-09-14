package com.plataforma.pergunta;

import java.util.List;
import java.util.Objects;

/**
 * Uma entrada do catálogo fechado (decisão 0030): o que a intenção faz,
 * quais parâmetros ela exige, e exemplos de como a lojista escreveria a
 * pergunta. {@code exemplosDePergunta} tem dois usos - alimenta
 * {@link ModeloHeuristico} (vocabulário de casamento por palavra-chave) e
 * vira {@code perguntasQueSeiResponder} na resposta de RECUSA/ESCLARECIMENTO
 * ({@link RespostaPergunta}), então precisa ser escrito do jeito que a
 * lojista fala, não em juridiquês de sistema.
 */
public record DescricaoIntencao(
        CodigoIntencao codigo,
        String descricao,
        List<String> parametrosObrigatorios,
        List<String> parametrosOpcionais,
        List<String> exemplosDePergunta) {

    public DescricaoIntencao {
        Objects.requireNonNull(codigo, "codigo é obrigatório");
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("descricao não pode ser vazia");
        }
        parametrosObrigatorios = List.copyOf(parametrosObrigatorios);
        parametrosOpcionais = List.copyOf(parametrosOpcionais);
        exemplosDePergunta = List.copyOf(exemplosDePergunta);
    }
}
