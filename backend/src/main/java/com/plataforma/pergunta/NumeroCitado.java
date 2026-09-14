package com.plataforma.pergunta;

/**
 * Um número citado no texto de {@link RespostaPergunta}, já formatado
 * (decisão 0026: valor sempre {@code String}, nunca tipo numérico solto -
 * a apresentação já decidiu escala e formato, quem lê não deveria
 * reformatar por cima). {@code valor} sai sempre de
 * {@link FormatadorDeTexto}, nunca escrito à mão dentro de um template.
 */
public record NumeroCitado(String nome, String valor) {
}
