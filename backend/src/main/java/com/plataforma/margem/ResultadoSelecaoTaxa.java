package com.plataforma.margem;

import java.util.UUID;

/**
 * As TRES saidas possiveis da consulta de selecao de taxa (cabecalho da
 * V013 / secao 8.2 do documento fiscal). Modelado como {@code sealed
 * interface} (nao string/enum solto) para que o compilador force quem
 * consome isto a tratar as tres - inclusive a ambigua, que e o caso que
 * mais importa nunca ser esquecido.
 */
public sealed interface ResultadoSelecaoTaxa {

    /** 1 linha (ou 2 com especificidade diferente): a taxa a usar. */
    record Encontrada(TaxaCanal taxa) implements ResultadoSelecaoTaxa {
    }

    /** 0 linhas: nivel 3 da hierarquia (decisao 0019) - lacuna declarada, nenhum custo criado. */
    record NaoEncontrada() implements ResultadoSelecaoTaxa {
    }

    /**
     * 2 linhas com a MESMA especificidade: erro de cadastro. "Nao escolhe
     * a primeira, a mais recente nem a menor" (cabecalho da V013) - o
     * calculo desta taxa e abortado e as duas linhas sao reportadas.
     */
    record Ambigua(UUID idTaxaA, UUID idTaxaB, int especificidade) implements ResultadoSelecaoTaxa {
    }
}
