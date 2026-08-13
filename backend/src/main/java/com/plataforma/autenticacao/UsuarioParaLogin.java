package com.plataforma.autenticacao;

import java.util.UUID;

/**
 * O que {@code app_usuario_para_login} (migration V014) devolve - no
 * MAXIMO uma linha, e so quando a policy {@code usuario_select_login}
 * abre a fresta pre-sessao (ver bloco 1/2 do cabecalho da V014).
 *
 * Inclui {@code senhaHash} DE PROPOSITO, ao contrario de {@link Usuario}:
 * este e o UNICO objeto do sistema que carrega o hash em memoria, e so
 * pelo tempo necessario para o {@link org.springframework.security.crypto.password.PasswordEncoder}
 * comparar contra a senha informada (dentro de {@link UsuarioAutenticado}).
 * Nunca serialize este record numa resposta HTTP nem escreva ele num log.
 */
public record UsuarioParaLogin(
        UUID id,
        UUID tenantId,
        String email,
        String senhaHash,
        String nome,
        PapelUsuario papel,
        boolean ativo,
        boolean tenantAtivo) {
}
