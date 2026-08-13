package com.plataforma.autenticacao;

/**
 * Corpo JSON aceito por {@code POST /api/login}: {@code {"email": "...",
 * "senha": "..."}}. Usado somente por {@link FiltroLoginJson} para
 * desserializar a requisicao - nunca logado, nunca ecoado numa resposta.
 */
public record CredenciaisLogin(String email, String senha) {
}
