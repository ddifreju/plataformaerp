package com.plataforma.pergunta;

/**
 * Resultado da validação determinística de UM parâmetro (canal ou
 * período - decisão 0030: "parâmetro é validado, nunca aceito"). Ou o
 * parâmetro é válido e {@link #valor()} vem preenchido, ou não é e
 * {@link #esclarecimento()} traz a pergunta a devolver à lojista - nunca
 * os dois, nunca nenhum dos dois.
 *
 * Modelado com campos simples (não {@code Optional<Optional<T>>>}) de
 * propósito: é o jeito mais óbvio de ler "ou/ou" sem esconder a checagem
 * de nulidade atrás de uma segunda camada de wrapper.
 */
public record ResultadoParametro<T>(T valor, String esclarecimento) {

    public static <T> ResultadoParametro<T> valido(T valor) {
        if (valor == null) {
            throw new IllegalArgumentException("valor não pode ser nulo quando o parâmetro é válido");
        }
        return new ResultadoParametro<>(valor, null);
    }

    public static <T> ResultadoParametro<T> esclarecimento(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            throw new IllegalArgumentException("mensagem de esclarecimento não pode ser vazia");
        }
        return new ResultadoParametro<>(null, mensagem);
    }

    public boolean valido() {
        return valor != null;
    }
}
