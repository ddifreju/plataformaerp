package com.plataforma.autenticacao;

/**
 * Dominio de usuario.papel (migration V014, CONSTRAINT ck_usuario_papel).
 *
 * ROTULO DE INTERFACE, NAO AUTORIZACAO - repetido aqui de proposito, com
 * as mesmas palavras do comentario da coluna na V014: nenhum endpoint,
 * nenhuma policy de RLS e nenhum GRANT consulta este valor hoje. Ele so
 * diz para as telas (dono/gestor/analista) o que faz sentido mostrar. Um
 * ANALISTA que chamar a API diretamente e atendido como qualquer outro
 * usuario autenticado. Transformar isto em autorizacao de verdade e
 * tarefa futura, com matriz papel x operacao e teste de NEGACAO, nao so
 * de permissao.
 */
public enum PapelUsuario {
    DONO,
    GESTOR,
    ANALISTA
}
