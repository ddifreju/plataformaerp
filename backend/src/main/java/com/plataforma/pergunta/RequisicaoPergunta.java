package com.plataforma.pergunta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo JSON aceito por {@code POST /api/pergunta}:
 * {@code {"texto": "..."}}.
 *
 * As duas restrições aqui são a MESMA validação de tamanho que
 * {@link ServicoPergunta#validarTexto} já faz (branco e maior que
 * {@link ServicoPergunta#TAMANHO_MAXIMO_PERGUNTA}), só que na borda HTTP:
 * rejeitar aqui, via {@code @Valid}, devolve {@code 400} ANTES de o texto
 * entrar no controller/serviço - {@link PerguntaInvalidaException} continua
 * existindo e coberta por {@link com.plataforma.comum.web.TratadorGlobalDeErros}
 * para quem chamar {@link ServicoPergunta#responder(String)} por fora do
 * HTTP (nenhum caso hoje, mas a classe não depende de "vir de controller"
 * para se proteger sozinha).
 */
public record RequisicaoPergunta(

        @NotBlank(message = "O texto da pergunta não pode ser vazio.")
        @Size(max = 500, message = "O texto da pergunta não pode passar de 500 caracteres.")
        String texto) {
}
