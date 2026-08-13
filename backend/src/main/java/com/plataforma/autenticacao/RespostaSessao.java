package com.plataforma.autenticacao;

import java.util.UUID;

/**
 * Resposta de {@code GET /api/sessao} (tarefa 17, item 7): o que o
 * frontend precisa saber sobre quem esta logado para decidir o que
 * mostrar. DE PROPOSITO nao tem {@code senhaHash} nem qualquer campo
 * derivado dele - este record e construido a partir de
 * {@link UsuarioAutenticado} pegando so os quatro campos que fazem
 * sentido numa tela, nunca o objeto de autenticacao inteiro.
 */
public record RespostaSessao(String email, String nome, PapelUsuario papel, UUID tenantId, String tenantNome) {
}
