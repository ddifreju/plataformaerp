package com.plataforma.margem;

import java.util.List;

/**
 * Le o resultado da consulta de selecao de taxa (cabecalho da V013) e
 * decide entre as tres saidas de {@link ResultadoSelecaoTaxa}. Classe
 * PURA: nao consulta banco - recebe a lista JA filtrada pelo WHERE da
 * consulta (canal, tipo_taxa, vigencia, categoria/tipo_anuncio com
 * curinga, faixa de valor - tudo isso e responsabilidade do
 * {@code RepositorioTaxaCanal}) e ordenada por especificidade DESC (a
 * MESMA ordem que a coluna GENERATED da V013 garante).
 *
 * "NAO acrescente um segundo criterio ao ORDER BY para resolver o
 * empate" (cabecalho da V013): por isso esta classe olha SO a
 * especificidade das duas primeiras linhas para decidir empate - nunca
 * data de cadastro, nunca UUID, nunca "a primeira que veio".
 */
public final class SelecaoTaxaCanal {

    private SelecaoTaxaCanal() {
        // classe utilitaria: sem instancia
    }

    /**
     * @param candidatas resultado da consulta do cabecalho da V013, ordenado por especificidade DESC.
     *                   Espera-se no maximo 2 linhas (a consulta usa {@code LIMIT 2}), mas este metodo
     *                   so olha as duas primeiras de qualquer lista recebida.
     */
    public static ResultadoSelecaoTaxa selecionar(List<TaxaCanal> candidatas) {
        if (candidatas == null || candidatas.isEmpty()) {
            return new ResultadoSelecaoTaxa.NaoEncontrada();
        }
        if (candidatas.size() == 1) {
            return new ResultadoSelecaoTaxa.Encontrada(candidatas.get(0));
        }

        TaxaCanal primeira = candidatas.get(0);
        TaxaCanal segunda = candidatas.get(1);
        if (primeira.getEspecificidade() == segunda.getEspecificidade()) {
            return new ResultadoSelecaoTaxa.Ambigua(primeira.getId(), segunda.getId(), primeira.getEspecificidade());
        }
        return new ResultadoSelecaoTaxa.Encontrada(primeira);
    }
}
