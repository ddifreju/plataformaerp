package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.plataforma.canal.Canal;
import com.plataforma.margem.ResultadoMargemConsolidada;
import com.plataforma.margem.ResultadoMargemPeriodo;
import com.plataforma.margem.RotuloTeto;

/**
 * Templates determinísticos de texto para {@code MARGEM_DO_PERIODO}/
 * {@code LACUNAS_DA_MARGEM} (decisão 0030: "nenhum dígito da resposta vem
 * do modelo") - extraído de {@link ServicoPergunta} na tarefa 33.
 *
 * <h2>Por que foi extraído agora</h2>
 * A dívida registrada em {@link ServicoPergunta} dizia para extrair o
 * bloco de margem quando o CATÁLOGO passasse de 5 itens - isso não
 * aconteceu aqui, o catálogo continua com 5. O gatilho real foi outro: a
 * tarefa 33 (decisão 0033) dobrou o tamanho do bloco de margem dentro de
 * {@link ServicoPergunta} ao acrescentar um SEGUNDO caminho inteiro
 * (consolidado sobre vários canais, ao lado do caminho de canal único que
 * já existia) - texto, números e auditoria, cada um com sua própria
 * variante. Deixar as ~90 linhas novas dentro de {@code ServicoPergunta}
 * levaria aquela classe a uns 550-600 linhas misturando ORQUESTRAÇÃO
 * (validação de parâmetro, decidir qual caminho seguir, chamar o serviço
 * de domínio certo, gravar auditoria) com FORMATAÇÃO DE TEXTO (como
 * escrever cada frase). Esta classe fica só com a segunda responsabilidade
 * - puramente funcional, sem dependência nenhuma (nem
 * {@code RepositorioConsultaAuditada}, nem os serviços de margem) - o que
 * também a deixa testável sem mock nenhum, se um dia isso valer a pena.
 * {@link ServicoPergunta} continua dona de QUANDO chamar cada método
 * daqui, e de tudo que envolve auditoria/tenant.
 *
 * <h2>Por que {@code ResultadoMargemPeriodo} e {@code ResultadoMargemConsolidada}
 * têm um método "unico"/"consolidado" cada, em vez de um método só</h2>
 * Os dois records compartilham quase todos os nomes de campo (N0..N4,
 * percentuais, quantidade de pedidos), mas não têm um supertipo em comum -
 * criar uma interface só para os métodos desta classe conseguirem tratá-los
 * de forma uniforme acoplaria dois módulos de domínio (margem de canal
 * único e margem consolidada) só para evitar um punhado de linhas
 * duplicadas aqui. A duplicação pontual (dois métodos curtos e óbvios) é
 * mais barata de manter do que essa abstração - "não crie abstração para
 * um caso só" vale também para dois casos quando o terceiro não está à
 * vista.
 */
final class RespostaDeMargem {

    /**
     * Frase fixa sobre N4 - igual nos dois caminhos (único e consolidado):
     * a ressalva sobre custo de período não rateado vale tanto por canal
     * quanto pela soma deles.
     */
    private static final String NOTA_N4 = "O lucro operacional do período (N4) não é a soma simples do Resultado "
            + "do pedido (N3) - ele também desconta custo de período que não foi rateado entre pedidos. ";

    private RespostaDeMargem() {
        // classe utilitaria: sem instancia
    }

    // ------------------------------------------------------------------
    // Números citados (NumeroCitado) - decisão 0026, valor sempre String.
    // ------------------------------------------------------------------

    static List<NumeroCitado> numerosUnico(ResultadoMargemPeriodo resultado) {
        List<NumeroCitado> numeros = new ArrayList<>();
        numeros.add(new NumeroCitado("Faturamento bruto (N0)", FormatadorDeTexto.moeda(resultado.faturamentoBrutoN0())));
        numeros.add(new NumeroCitado("Receita líquida (N1)", FormatadorDeTexto.moeda(resultado.receitaLiquidaN1())));
        numeros.add(new NumeroCitado("Margem por pedido (N2)", FormatadorDeTexto.moeda(resultado.margemContribuicaoN2())));
        numeros.add(new NumeroCitado("Resultado do pedido (N3)", FormatadorDeTexto.moeda(resultado.resultadoPeriodoN3())));
        numeros.add(new NumeroCitado("Lucro operacional do período (N4)", FormatadorDeTexto.moeda(resultado.lucroOperacionalN4())));
        resultado.margemContribuicaoPercentual()
                .ifPresent(p -> numeros.add(new NumeroCitado("Margem por pedido (%)", FormatadorDeTexto.percentual(p))));
        resultado.margemLiquidaPercentual()
                .ifPresent(p -> numeros.add(new NumeroCitado("Margem líquida (%)", FormatadorDeTexto.percentual(p))));
        numeros.add(new NumeroCitado("Pedidos no período", String.valueOf(resultado.quantidadePedidos())));
        return numeros;
    }

    static List<NumeroCitado> numerosConsolidado(ResultadoMargemConsolidada resultado) {
        List<NumeroCitado> numeros = new ArrayList<>();
        numeros.add(new NumeroCitado("Faturamento bruto (N0)", FormatadorDeTexto.moeda(resultado.faturamentoBrutoN0())));
        numeros.add(new NumeroCitado("Receita líquida (N1)", FormatadorDeTexto.moeda(resultado.receitaLiquidaN1())));
        numeros.add(new NumeroCitado("Margem por pedido (N2)", FormatadorDeTexto.moeda(resultado.margemContribuicaoN2())));
        numeros.add(new NumeroCitado("Resultado do pedido (N3)", FormatadorDeTexto.moeda(resultado.resultadoPeriodoN3())));
        numeros.add(new NumeroCitado("Lucro operacional do período (N4)", FormatadorDeTexto.moeda(resultado.lucroOperacionalN4())));
        resultado.margemContribuicaoPercentual()
                .ifPresent(p -> numeros.add(new NumeroCitado("Margem por pedido (%)", FormatadorDeTexto.percentual(p))));
        resultado.margemLiquidaPercentual()
                .ifPresent(p -> numeros.add(new NumeroCitado("Margem líquida (%)", FormatadorDeTexto.percentual(p))));
        numeros.add(new NumeroCitado("Pedidos no período", String.valueOf(resultado.quantidadePedidos())));
        return numeros;
    }

    // ------------------------------------------------------------------
    // Zero pedido - regra dura: nunca "R$ 0,00" apresentado como resultado.
    // ------------------------------------------------------------------

    static String textoZeroPedidosUnico(Canal canal, String dataInicio, String dataFim, String escopo) {
        return "Não há pedido no canal " + canal.getNome() + " entre " + dataInicio + " e " + dataFim + ". " + escopo;
    }

    static String textoZeroPedidosConsolidado(List<String> nomesCanais, String dataInicio, String dataFim,
            String escopo) {
        return "Não há pedido nos canais " + String.join(", ", nomesCanais) + " entre " + dataInicio + " e "
                + dataFim + ". " + escopo;
    }

    // ------------------------------------------------------------------
    // LACUNAS_DA_MARGEM
    // ------------------------------------------------------------------

    static String textoLacunasUnico(Canal canal, String dataInicio, String dataFim, List<String> lacunasDescricao,
            String escopo) {
        if (lacunasDescricao.isEmpty()) {
            return "Não encontrei nenhuma lacuna: a margem do canal " + canal.getNome() + " entre " + dataInicio
                    + " e " + dataFim + " foi calculada sem dado faltando. " + escopo;
        }
        return "Faltam " + lacunasDescricao.size() + " coisa(s) para calcular com confiança a margem do canal "
                + canal.getNome() + " entre " + dataInicio + " e " + dataFim + ": "
                + String.join("; ", lacunasDescricao) + ". " + escopo;
    }

    static String textoLacunasConsolidado(List<String> nomesCanais, String dataInicio, String dataFim,
            List<String> lacunasDescricao, String escopo) {
        String canais = String.join(", ", nomesCanais);
        if (lacunasDescricao.isEmpty()) {
            return "Não encontrei nenhuma lacuna: a margem consolidada dos canais " + canais + " entre " + dataInicio
                    + " e " + dataFim + " foi calculada sem dado faltando. " + escopo;
        }
        return "Faltam " + lacunasDescricao.size() + " coisa(s) para calcular com confiança a margem consolidada "
                + "dos canais " + canais + " entre " + dataInicio + " e " + dataFim + ": "
                + String.join("; ", lacunasDescricao) + ". " + escopo;
    }

    // ------------------------------------------------------------------
    // MARGEM_DO_PERIODO completa
    // ------------------------------------------------------------------

    static String textoMargemCompletaUnico(Canal canal, String dataInicio, String dataFim,
            ResultadoMargemPeriodo resultado, String escopo, List<String> lacunasDescricao) {
        StringBuilder texto = new StringBuilder();
        texto.append("Canal ").append(canal.getNome()).append(", de ").append(dataInicio).append(" a ")
                .append(dataFim).append(": ");
        for (NumeroCitado numero : numerosUnico(resultado)) {
            texto.append(numero.nome()).append(": ").append(numero.valor()).append(". ");
        }
        texto.append(NOTA_N4);
        texto.append(escopo);
        apendarBlocoDeConfianca(texto, resultado.rotulo(), resultado.margemContribuicaoN2(),
                resultado.margemContribuicaoPercentual(), lacunasDescricao);
        return texto.toString();
    }

    static String textoMargemCompletaConsolidado(List<String> nomesCanais, String dataInicio, String dataFim,
            ResultadoMargemConsolidada resultado, String escopo, List<String> lacunasDescricao) {
        StringBuilder texto = new StringBuilder();
        texto.append("Canais ").append(String.join(", ", nomesCanais)).append(", de ").append(dataInicio)
                .append(" a ").append(dataFim).append(": ");
        for (NumeroCitado numero : numerosConsolidado(resultado)) {
            texto.append(numero.nome()).append(": ").append(numero.valor()).append(". ");
        }
        texto.append(NOTA_N4);
        texto.append(escopo);
        apendarBlocoDeConfianca(texto, resultado.rotulo(), resultado.margemContribuicaoN2(),
                resultado.margemContribuicaoPercentual(), lacunasDescricao);
        return texto.toString();
    }

    /**
     * Bloco de "margem é teto"/"margem indeterminada" - texto IDÊNTICO nos
     * dois caminhos (a ressalva sobre confiança do número vale igual para
     * um canal ou para a soma deles), por isso compartilhado aqui em vez de
     * duplicado nos dois métodos acima.
     */
    private static void apendarBlocoDeConfianca(StringBuilder texto, RotuloTeto rotulo,
            BigDecimal margemContribuicaoN2, Optional<BigDecimal> margemContribuicaoPercentual,
            List<String> lacunasDescricao) {
        if (rotulo == RotuloTeto.COM_TETO) {
            String percentualTexto = margemContribuicaoPercentual
                    .map(p -> " (" + FormatadorDeTexto.percentual(p) + ")").orElse("");
            texto.append(" ").append(FormatadorDeTexto.moeda(margemContribuicaoN2)).append(percentualTexto)
                    .append(" é o teto - a margem real é menor. Faltam: ")
                    .append(String.join("; ", lacunasDescricao)).append(".");
        } else if (rotulo == RotuloTeto.INDETERMINADA) {
            texto.append(" Não dá para calcular esta margem com confiança. Faltam: ")
                    .append(String.join("; ", lacunasDescricao))
                    .append(". Alguns desses fazem o número subir, outros descer - por isso não existe um teto "
                            + "seguro para mostrar aqui.");
        }
    }
}
