package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de resposta de {@code POST /api/margem/periodo/consolidado} (tarefa
 * 32) - MESMO padrao de {@link RespostaMargemPeriodo}: formata para 2
 * casas NA BORDA DE SAIDA (secao 6.1/6.2 do documento fiscal), a partir de
 * numeros JA SOMADOS em escala de armazenamento (regra 1 da secao 6.4:
 * "o total exibido e round(soma exata, 2) - nunca a soma dos
 * arredondados"). {@link ServicoMargemPeriodoConsolidada} garante isso
 * somando os N0-N4 de CADA canal em 4 casas antes de qualquer
 * arredondamento; este DTO so arredonda o resultado JA FECHADO.
 */
public record RespostaMargemConsolidada(
        List<UUID> canaisIncluidos,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String escopoConsolidado,
        BigDecimal faturamentoBrutoN0,
        BigDecimal receitaLiquidaN1,
        BigDecimal margemContribuicaoN2,
        BigDecimal resultadoPeriodoN3,
        BigDecimal lucroOperacionalN4,
        Optional<BigDecimal> margemContribuicaoPercentual,
        Optional<BigDecimal> margemLiquidaPercentual,
        List<MemoriaCalculoBlocoPeriodo> decomposicao,
        List<Lacuna> lacunas,
        RotuloTeto rotulo,
        int quantidadePedidos,
        Set<UUID> idsPedidoUsados,
        Set<UUID> idsCustoUsados) {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    /** Constroi a resposta formatada a partir do resultado JA CALCULADO - nenhuma soma nova acontece aqui. */
    public static RespostaMargemConsolidada de(ResultadoMargemConsolidada resultado) {
        return new RespostaMargemConsolidada(
                resultado.canaisIncluidos(),
                resultado.inicio(),
                resultado.fim(),
                resultado.escopoConsolidado(),
                Apresentacao.paraExibicao(resultado.faturamentoBrutoN0()),
                Apresentacao.paraExibicao(resultado.receitaLiquidaN1()),
                Apresentacao.paraExibicao(resultado.margemContribuicaoN2()),
                Apresentacao.paraExibicao(resultado.resultadoPeriodoN3()),
                Apresentacao.paraExibicao(resultado.lucroOperacionalN4()),
                percentualParaExibicao(resultado.margemContribuicaoPercentual()),
                percentualParaExibicao(resultado.margemLiquidaPercentual()),
                decomposicaoParaExibicao(resultado.decomposicao()),
                resultado.lacunas(),
                resultado.rotulo(),
                resultado.quantidadePedidos(),
                resultado.idsPedidoUsados(),
                resultado.idsCustoUsados());
    }

    private static Optional<BigDecimal> percentualParaExibicao(Optional<BigDecimal> fracao) {
        return fracao.map(valor -> Apresentacao.paraExibicao(valor.multiply(CEM)));
    }

    private static List<MemoriaCalculoBlocoPeriodo> decomposicaoParaExibicao(List<MemoriaCalculoBlocoPeriodo> origem) {
        return origem.stream()
                .map(bloco -> new MemoriaCalculoBlocoPeriodo(
                        bloco.bloco(), Apresentacao.paraExibicao(bloco.valor()), bloco.contemEstimativa()))
                .toList();
    }
}
