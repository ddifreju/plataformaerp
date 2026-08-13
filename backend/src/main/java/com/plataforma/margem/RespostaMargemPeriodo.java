package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de resposta de {@code GET /api/margem/periodo} (tarefa 16) -
 * FORMATADO NA BORDA DE SAIDA (secao 6.1/6.2 do documento fiscal:
 * "arredonde para 2, HALF_UP, apenas na borda de saida - nunca antes").
 *
 * {@link ResultadoMargemPeriodo} (o que {@link ServicoMargemPeriodo}
 * calcula) guarda os numeros em ESCALA DE ARMAZENAMENTO (4 casas) porque e
 * dali que a proxima soma/consulta parte. Este DTO existe so para o
 * ULTIMO passo, a resposta HTTP: pega os numeros JA FECHADOS e aplica
 * {@link Apresentacao#paraExibicao(BigDecimal)} por cima - NAO RECALCULA
 * nada, so arredonda o que ja esta certo. Ver docs/ESTADO.md: "o endpoint
 * devolve os valores com 4 casas, nao 2 - formatar na borda de saida
 * quando a Fase 3 ligar a tela".
 *
 * Percentuais: a fonte guarda FRACAO decimal (0,2564...); a apresentacao
 * converte para PONTO PERCENTUAL (25,64) antes de arredondar - e o formato
 * que o exemplo numerico da secao 2.5 usa ("25,64%", nunca "0,26%").
 */
public record RespostaMargemPeriodo(
        UUID canalId,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String escopoCanal,
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
    public static RespostaMargemPeriodo de(ResultadoMargemPeriodo resultado) {
        return new RespostaMargemPeriodo(
                resultado.canalId(),
                resultado.inicio(),
                resultado.fim(),
                resultado.escopoCanal(),
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
