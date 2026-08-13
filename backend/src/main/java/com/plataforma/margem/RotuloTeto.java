package com.plataforma.margem;

import java.util.Collection;

/**
 * Os tres rotulos da "regra do teto" (docs/fiscal/regras-de-margem.md,
 * secao 9.2). NENHUM numero de margem existe sem um destes tres -
 * modelado como enum, nunca string solta, exatamente como a tarefa exige:
 * "nenhuma tela pode dizer so 'lucro'" vira aqui "nenhum resultado sai sem
 * rotulo verificado pelo compilador".
 */
public enum RotuloTeto {

    /** Nenhuma lacuna. O numero e o numero, sem qualificador. */
    CALCULADA,

    /**
     * Todas as lacunas presentes tem vies {@link DirecaoViesLacuna#SUPERESTIMA_MARGEM}.
     * O numero calculado e um LIMITE SUPERIOR honesto: "sua margem e de
     * no maximo X; a real e menor".
     */
    COM_TETO,

    /**
     * Ha lacunas de vies oposto ({@link DirecaoViesLacuna#SUBESTIMA_MARGEM})
     * ou de direcao desconhecida ({@link DirecaoViesLacuna#INDETERMINADA})
     * misturadas as demais. Nao existe teto honesto a declarar.
     */
    INDETERMINADA;

    /**
     * A escolha mecanica da secao 9.2:
     * <ol>
     *   <li>sem lacunas -&gt; {@link #CALCULADA}</li>
     *   <li>todas as lacunas empurram para cima -&gt; {@link #COM_TETO}</li>
     *   <li>qualquer lacuna de vies oposto ou desconhecido -&gt; {@link #INDETERMINADA}</li>
     * </ol>
     */
    public static RotuloTeto calcular(Collection<Lacuna> lacunas) {
        if (lacunas == null || lacunas.isEmpty()) {
            return CALCULADA;
        }
        boolean todasParaCima = lacunas.stream()
                .allMatch(lacuna -> lacuna.direcaoVies() == DirecaoViesLacuna.SUPERESTIMA_MARGEM);
        return todasParaCima ? COM_TETO : INDETERMINADA;
    }
}
