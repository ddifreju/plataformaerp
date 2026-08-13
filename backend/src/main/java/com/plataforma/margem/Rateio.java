package com.plataforma.margem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Algoritmo de maior resto (largest remainder) da secao 6.3 do documento
 * fiscal, com o desempate deterministico exigido ali: sem ele, dois
 * recalculos do MESMO pedido dariam distribuicoes diferentes e a memoria
 * de calculo (regra 3 do CLAUDE.md) deixaria de ser reproduzivel.
 *
 * Garantia central, testada em {@code RateioTest}: {@code Σ quota[i] ==
 * total} EXATAMENTE, sempre - positivo, negativo (estorno) ou zero, com
 * qualquer numero de itens e qualquer distribuicao de pesos.
 *
 * Classe utilitaria pura: sem estado, sem I/O, sem Spring. E o que
 * permite testar o algoritmo com JUnit puro, sem banco.
 */
public final class Rateio {

    /** Escala do calculo intermediario (secao 6.2: "8 casas, muito alem da escala de saida"). */
    private static final int ESCALA_INTERMEDIARIA = 8;

    private Rateio() {
        // classe utilitaria: sem instancia
    }

    /**
     * Um item a ratear: identidade (usada so no desempate final, criterio
     * 3) e peso (>= 0 - peso negativo nao tem significado neste algoritmo,
     * que ja trata o SINAL do total separadamente no passo 0).
     */
    public record ItemRateio(UUID id, BigDecimal peso) {
        public ItemRateio {
            Objects.requireNonNull(id, "id do item de rateio nao pode ser nulo - e o ultimo criterio de desempate");
            Objects.requireNonNull(peso, "peso do item de rateio nao pode ser nulo");
            if (peso.signum() < 0) {
                throw new IllegalArgumentException(
                        "peso do item de rateio nao pode ser negativo (id=" + id + ", peso=" + peso + ")");
            }
        }
    }

    /** A parte deste item no total, ja com o sinal do total restaurado (passo 6). */
    public record QuotaRateio(UUID id, BigDecimal quota) {
    }

    /**
     * Executa o algoritmo de maior resto descrito na secao 6.3.
     *
     * @param total       o valor a distribuir, positivo, negativo (estorno) ou zero
     * @param itens       os itens a ratear; nunca vazio
     * @param escalaSaida a escala de armazenamento (4) ou de apresentacao (2) - secao 6.1
     * @return uma quota por item, na MESMA ordem de {@code itens}, com soma exatamente igual a {@code total}
     * @throws RateioImpossivelException se {@code itens} estiver vazio, se a soma dos pesos for zero
     *         (passo 0 do algoritmo), ou se a asserção final (soma das quotas == total) falhar - o que
     *         so aconteceria por bug deste metodo, nunca por dado de entrada valido
     */
    public static List<QuotaRateio> ratear(BigDecimal total, List<ItemRateio> itens, int escalaSaida) {
        Objects.requireNonNull(total, "total nao pode ser nulo");
        if (itens == null || itens.isEmpty()) {
            throw new RateioImpossivelException("Nao ha itens para ratear.");
        }

        BigDecimal somaPesos = itens.stream().map(ItemRateio::peso).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somaPesos.signum() == 0) {
            // Passo 0 do algoritmo: aborta, nunca divide igualmente "porque
            // da na mesma". Peso zero significa que nao sabemos como
            // dividir - isso e lacuna, nao decisao arbitraria do motor.
            throw new RateioImpossivelException(
                    "Soma dos pesos e zero: nao existe rateio com peso zero (secao 6.3, passo 0). "
                            + "Nao se distribui igualmente por falta de criterio - reporte como lacuna.");
        }

        // Passo 0: trabalha em modulo, restaura o sinal so no passo 6. Sem
        // isso, RoundingMode.DOWN trunca "em direcao ao zero" e o residuo
        // inverteria de sinal na primeira devolucao (nota do Exemplo C).
        int sinal = total.signum();
        BigDecimal totalAbsoluto = total.abs();
        BigDecimal unidade = BigDecimal.ONE.movePointLeft(escalaSaida); // 10^-E

        record Parcial(UUID id, BigDecimal peso, BigDecimal piso, BigDecimal resto) {
        }

        List<Parcial> parciais = new ArrayList<>(itens.size());
        for (ItemRateio item : itens) {
            // Passo 1: bruto com precisao folgada.
            BigDecimal bruto = totalAbsoluto.multiply(item.peso())
                    .divide(somaPesos, ESCALA_INTERMEDIARIA, RoundingMode.HALF_UP);
            // Passo 2: piso trunca PARA BAIXO (DOWN, nao HALF_UP) - com DOWN
            // em modulo, Σ piso[i] <= T sempre.
            BigDecimal piso = bruto.setScale(escalaSaida, RoundingMode.DOWN);
            BigDecimal resto = bruto.subtract(piso);
            parciais.add(new Parcial(item.id(), item.peso(), piso, resto));
        }

        // Passo 3: quanto falta distribuir.
        BigDecimal somaPiso = parciais.stream().map(Parcial::piso).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal residuo = totalAbsoluto.subtract(somaPiso);

        // Passo 4: quantas unidades da ultima casa faltam.
        int n = residuo.divide(unidade, 0, RoundingMode.HALF_UP).intValueExact();
        if (n < 0 || n > parciais.size()) {
            // O proprio algoritmo garante 0 <= n < quantidade de itens
            // quando os passos 1/2 estao corretos - se isso disparar, e
            // bug de implementacao, nao dado de entrada ruim.
            throw new RateioImpossivelException(
                    "Rateio inconsistente: n=" + n + " fora do intervalo [0, " + parciais.size()
                            + "]. Isto e bug no calculo do bruto/piso, nao dado de entrada - nao grave nada.");
        }

        // Passo 5: ordena por resto DESC, peso DESC, id ASC - o desempate
        // deterministico e OBRIGATORIO (ver Javadoc da classe).
        List<Parcial> ordenados = parciais.stream()
                .sorted(Comparator.comparing(Parcial::resto, Comparator.reverseOrder())
                        .thenComparing(Parcial::peso, Comparator.reverseOrder())
                        .thenComparing(Parcial::id))
                .toList();

        Set<UUID> recebemUnidadeExtra = new HashSet<>(n);
        for (int i = 0; i < n; i++) {
            recebemUnidadeExtra.add(ordenados.get(i).id());
        }

        List<QuotaRateio> resultado = new ArrayList<>(parciais.size());
        BigDecimal somaConferencia = BigDecimal.ZERO;
        BigDecimal fatorSinal = BigDecimal.valueOf(sinal);
        for (Parcial parcial : parciais) {
            BigDecimal quotaAbsoluta = recebemUnidadeExtra.contains(parcial.id())
                    ? parcial.piso().add(unidade)
                    : parcial.piso();
            // Passo 6: restaura o sinal do total (estorno vira quota negativa).
            BigDecimal quota = quotaAbsoluta.multiply(fatorSinal);
            resultado.add(new QuotaRateio(parcial.id(), quota));
            somaConferencia = somaConferencia.add(quota);
        }

        // Passo 7: ASSERT Σ quota[i] == total, EXATAMENTE - compareTo, nao
        // tolerancia. Falhou aqui e falhou por bug nosso; "nao grave nada"
        // vira, em Java, "lance excecao antes de devolver".
        if (somaConferencia.compareTo(total) != 0) {
            throw new RateioImpossivelException(
                    "Rateio nao fechou: soma das quotas (" + somaConferencia + ") difere do total (" + total
                            + "). Bug no algoritmo - nao foi gravado nada.");
        }

        return resultado;
    }
}
