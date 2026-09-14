package com.plataforma.margem;

import java.util.UUID;

/**
 * Uma linha do resultado da consulta nativa de
 * {@link RepositorioDisjuncaoDeCanais} - um canal pedido, o que se sabe
 * dele, e por que ele bloquearia a soma (ou {@code null} em
 * {@code motivoDoBloqueio} se ele nao bloqueia).
 *
 * {@code codigo} e {@code espelhaCanalId} vem do proprio canal (NULL
 * quando o canal nem existe - {@link MotivoBloqueioDeSoma#CANAL_INEXISTENTE}) -
 * usados para montar uma mensagem de recusa que NOMEIA o canal, nao so o
 * UUID dele.
 */
public record LinhaDisjuncaoCanal(UUID canalId, String codigo, UUID espelhaCanalId, MotivoBloqueioDeSoma motivoDoBloqueio) {

    public boolean bloqueiaASoma() {
        return motivoDoBloqueio != null;
    }
}
