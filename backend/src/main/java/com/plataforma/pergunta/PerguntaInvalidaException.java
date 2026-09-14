package com.plataforma.pergunta;

/**
 * Texto de pergunta vazio ou maior que
 * {@link ServicoPergunta#TAMANHO_MAXIMO_PERGUNTA} caracteres. Tipada,
 * nunca {@code IllegalArgumentException} genérica saindo direto do
 * controller - a tradução para {@code 400} fica em
 * {@code com.plataforma.comum.web.TratadorGlobalDeErros} (tarefa 24; não
 * criada aqui de propósito - ver relatório final da tarefa).
 */
public class PerguntaInvalidaException extends RuntimeException {

    public PerguntaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
