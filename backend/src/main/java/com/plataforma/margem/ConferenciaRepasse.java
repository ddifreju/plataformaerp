package com.plataforma.margem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;

/**
 * A conferencia externa da secao 2.6 do documento fiscal:
 * {@code pedido.valor_repasse_previsto} e a UNICA checagem que temos
 * contra o mundo. Formula:
 *
 * <pre>
 * repasse_esperado = N0 - C(B1) - C(B3) - C(B4 ∩ {FRETE, FRETE_REVERSO}) - C(B5)
 * delta            = valor_repasse_previsto - repasse_esperado
 * </pre>
 *
 * Note que {@code repasse_esperado} usa so uma FATIA de B4 (FRETE e
 * FRETE_REVERSO, nunca EMBALAGEM): "inclui apenas o que o canal desconta
 * na fonte". Por isso este calculo NAO reaproveita as somas por bloco de
 * {@link MotorMargemPedido} (que agregam B4 inteiro) - ele soma por
 * NATUREZA diretamente sobre a lista de {@code custo}.
 */
public final class ConferenciaRepasse {

    /** |delta| &lt;= 0,01 -&gt; custos do canal completos (secao 2.6). */
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.0100");
    private static final int ESCALA_ARMAZENAMENTO = 4;

    private ConferenciaRepasse() {
        // classe utilitaria: sem instancia
    }

    /**
     * @param lacuna  presente quando {@code delta < -tolerancia}: existe custo do canal que nao vemos, de
     *                valor CONHECIDO (catalogo #16). Ausente quando o repasse esta ausente - nesse caso
     *                {@code delta} e {@code repasseEsperado} tambem ficam vazios (ver
     *                {@link #semRepasseInformado()}).
     * @param alerta  presente quando {@code delta > tolerancia}: "isto e bug, nao dado" (secao 2.6) -
     *                cobramos custo A MAIS do lojista (dupla contagem, taxa errada, estorno nao lancado).
     *                NAO vira Lacuna: lacuna e "nao sei", isto e "sei que esta errado".
     */
    public record ResultadoConferencia(
            Optional<BigDecimal> repasseEsperado,
            Optional<BigDecimal> delta,
            Optional<Lacuna> lacuna,
            Optional<String> alerta) {

        private static ResultadoConferencia semRepasseInformado() {
            return new ResultadoConferencia(Optional.empty(), Optional.empty(),
                    Optional.of(CatalogoLacunas.repassePrevistoAusente()), Optional.empty());
        }
    }

    public static ResultadoConferencia conferir(BigDecimal n0, List<Custo> custos, BigDecimal valorRepassePrevisto) {
        if (valorRepassePrevisto == null) {
            // valor_repasse_previsto IS NULL: "sem conferencia. O grau de
            // confianca do pedido cai" (secao 2.6, ultimo item).
            return ResultadoConferencia.semRepasseInformado();
        }

        BigDecimal b1 = somaNaturezas(custos, NaturezaCusto.REEMBOLSO, NaturezaCusto.DESCONTO_CONCEDIDO);
        BigDecimal b3 = somaNaturezas(custos, NaturezaCusto.COMISSAO_CANAL, NaturezaCusto.TARIFA_FIXA_CANAL);
        BigDecimal freteEReverso = somaNaturezas(custos, NaturezaCusto.FRETE, NaturezaCusto.FRETE_REVERSO);
        BigDecimal b5 = somaNaturezas(custos, NaturezaCusto.TAXA_PAGAMENTO, NaturezaCusto.TAXA_PARCELAMENTO,
                NaturezaCusto.TAXA_ANTECIPACAO);

        BigDecimal repasseEsperado = n0.subtract(b1).subtract(b3).subtract(freteEReverso).subtract(b5)
                .setScale(ESCALA_ARMAZENAMENTO, RoundingMode.HALF_UP);
        BigDecimal delta = valorRepassePrevisto.subtract(repasseEsperado).setScale(ESCALA_ARMAZENAMENTO, RoundingMode.HALF_UP);

        if (delta.abs().compareTo(TOLERANCIA) <= 0) {
            return new ResultadoConferencia(Optional.of(repasseEsperado), Optional.of(delta), Optional.empty(), Optional.empty());
        }
        if (delta.signum() < 0) {
            return new ResultadoConferencia(Optional.of(repasseEsperado), Optional.of(delta),
                    Optional.of(CatalogoLacunas.repasseDivergeQuantificavel(delta.abs())), Optional.empty());
        }
        // delta > tolerancia, positivo: bug nosso (custo cobrado a mais do
        // lojista), nao lacuna do mundo - ver secao 2.6.
        return new ResultadoConferencia(Optional.of(repasseEsperado), Optional.of(delta), Optional.empty(),
                Optional.of("Repasse previsto (" + valorRepassePrevisto + ") maior que o esperado (" + repasseEsperado
                        + ") em " + delta + ". Isto e bug (dupla contagem, taxa cadastrada errada ou estorno "
                        + "nao lancado), nao dado a reportar como lacuna - investigar."));
    }

    private static BigDecimal somaNaturezas(List<Custo> custos, NaturezaCusto... naturezas) {
        Set<NaturezaCusto> alvo = Set.of(naturezas);
        return custos.stream()
                .filter(custo -> alvo.contains(custo.getNatureza()))
                .map(Custo::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
