package com.plataforma.margem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Aplica uma {@link TaxaCanal} (nivel 2 da hierarquia, decisao 0019) sobre
 * uma base de incidencia, produzindo o valor a gravar em {@code custo}.
 * Classe PURA: nao consulta banco, nao decide qual taxa usar (isso e
 * {@link SelecaoTaxaCanal}) - so faz a conta.
 *
 * Regras de arredondamento (secao 6.2): a multiplicacao percentual x base
 * NAO e arredondada no meio da cadeia; so o resultado FINAL vai para
 * escala de armazenamento (4, HALF_UP), uma vez, no fim.
 */
public final class AplicadorTaxa {

    private static final int ESCALA_ARMAZENAMENTO = 4;

    private AplicadorTaxa() {
        // classe utilitaria: sem instancia
    }

    /**
     * @param valor            valor final a gravar em {@code custo.valor}, ja em escala de armazenamento
     * @param baseCalculo      o que foi usado como base (grava em {@code custo.base_calculo})
     * @param aliquotaAplicada fracao decimal usada, ou {@code null} quando a taxa e de valor fixo
     *                         (grava em {@code custo.aliquota_aplicada})
     */
    public record ResultadoAplicacao(BigDecimal valor, BigDecimal baseCalculo, BigDecimal aliquotaAplicada) {
    }

    public static ResultadoAplicacao aplicar(TaxaCanal taxa, BigDecimal baseIncidencia) {
        Objects.requireNonNull(taxa, "taxa nao pode ser nula");
        Objects.requireNonNull(baseIncidencia, "baseIncidencia nao pode ser nula");

        BigDecimal aliquotaAplicada = null;
        BigDecimal valorBruto;
        if (taxa.getPercentual() != null) {
            aliquotaAplicada = taxa.getPercentual();
            // Nao arredonda aqui - secao 6.2: "entre uma multiplicacao e a
            // soma seguinte [...] mantenha as casas ate o fim da cadeia".
            valorBruto = baseIncidencia.multiply(aliquotaAplicada);
        } else {
            valorBruto = taxa.getValorFixo();
        }

        // Piso e teto do valor RESULTANTE (V013: "so fazem sentido com
        // percentual" - mas o clamp em si e inofensivo aplicar sempre,
        // porque valor_minimo/valor_maximo so vem preenchidos quando
        // percentual tambem esta, por causa da CHECK
        // ck_taxa_canal_piso_teto_exigem_percentual).
        if (taxa.getValorMinimo() != null && valorBruto.compareTo(taxa.getValorMinimo()) < 0) {
            valorBruto = taxa.getValorMinimo();
        }
        if (taxa.getValorMaximo() != null && valorBruto.compareTo(taxa.getValorMaximo()) > 0) {
            valorBruto = taxa.getValorMaximo();
        }

        BigDecimal valor = valorBruto.setScale(ESCALA_ARMAZENAMENTO, RoundingMode.HALF_UP);
        BigDecimal baseCalculo = baseIncidencia.setScale(ESCALA_ARMAZENAMENTO, RoundingMode.HALF_UP);
        return new ResultadoAplicacao(valor, baseCalculo, aliquotaAplicada);
    }
}
