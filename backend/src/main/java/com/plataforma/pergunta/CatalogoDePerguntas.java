package com.plataforma.pergunta;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Lista finita e versionada de perguntas que o sistema sabe responder
 * (decisão 0030). Cada entrada aqui é a única fonte de verdade sobre o
 * que existe - {@link ModeloHeuristico} usa a {@code descricao} e os
 * {@code exemplosDePergunta} como vocabulário, e a UI (quando existir)
 * lista {@link #descricoes()} para mostrar "o que eu sei responder".
 *
 * {@link #resolver(String)} é ESTRITO de propósito: só aceita o nome EXATO
 * de um {@link CodigoIntencao} (mesma grafia, sem espaço). Um código que
 * não bate byte a byte é tratado como "fora do catálogo", nunca corrigido
 * com boa vontade (decisão 0030: "código de intenção que não existe no
 * catálogo é rejeitado, não interpretado com boa vontade") - normalização
 * de texto livre (acento, caixa) é comportamento de {@link ModeloHeuristico}
 * e de {@link ValidadorDeParametros} sobre PARÂMETRO, nunca sobre o código
 * de intenção em si.
 */
@Component
public class CatalogoDePerguntas {

    private final List<DescricaoIntencao> descricoes = List.of(
            new DescricaoIntencao(
                    CodigoIntencao.MARGEM_DO_PERIODO,
                    "Calcula os números da margem (faturamento bruto, receita líquida, margem por pedido, "
                            + "resultado do período e lucro operacional) para um canal e um período específicos.",
                    List.of("canal", "periodo"),
                    List.of(),
                    List.of(
                            "Quanto sobrou no mês passado no Mercado Livre?",
                            "Qual foi a margem da Loja Própria nos últimos 30 dias?",
                            "Quanto lucrei no canal Shopee este mês?")),
            new DescricaoIntencao(
                    CodigoIntencao.LACUNAS_DA_MARGEM,
                    "Lista o que está faltando para calcular a margem com confiança, num canal e período: "
                            + "taxa não cadastrada, custo de produto ausente e outras lacunas.",
                    List.of("canal", "periodo"),
                    List.of(),
                    List.of(
                            "O que está faltando para calcular a margem do Mercado Livre no mês atual?",
                            "Quais dados faltam para fechar o resultado da Loja Própria este mês?",
                            "Por que a margem do canal ML Clássico está incompleta?")),
            new DescricaoIntencao(
                    CodigoIntencao.GARGALOS_DA_OPERACAO,
                    "Mostra onde a operação está travando: pedidos e devoluções agrupados por status, "
                            + "eventos de ingestão com erro e pedidos sem custo de mercadoria.",
                    List.of(),
                    List.of(),
                    List.of(
                            "Onde a operação está travando?",
                            "Quais são os gargalos do processo hoje?",
                            "Tem pedido parado em algum status?")),
            new DescricaoIntencao(
                    CodigoIntencao.FILA_DE_PENDENCIAS,
                    "Lista o que precisa ser resolvido para os números ficarem mais confiáveis: evento de "
                            + "ingestão com erro, item de pedido sem variação, variação sem custo e devolução em aberto.",
                    List.of(),
                    List.of(),
                    List.of(
                            "O que eu preciso resolver hoje?",
                            "Quais pendências estão na minha fila?",
                            "Tem alguma variação sem custo cadastrado?")),
            new DescricaoIntencao(
                    CodigoIntencao.CANAIS_DISPONIVEIS,
                    "Lista os canais de venda cadastrados neste tenant.",
                    List.of(),
                    List.of(),
                    List.of(
                            "Quais canais eu tenho cadastrados?",
                            "Me lista os canais disponíveis.",
                            "Quais são os meus canais de venda?")));

    public List<DescricaoIntencao> descricoes() {
        return descricoes;
    }

    /**
     * @param codigoBruto o que a interpretação devolveu, literal - ver
     *      {@link IntencaoDetectada#codigoBruto()}
     * @return o código do catálogo, ou vazio se {@code codigoBruto} não
     *      bater EXATAMENTE com nenhum valor de {@link CodigoIntencao}
     */
    public Optional<CodigoIntencao> resolver(String codigoBruto) {
        if (codigoBruto == null) {
            return Optional.empty();
        }
        return Arrays.stream(CodigoIntencao.values())
                .filter(codigo -> codigo.name().equals(codigoBruto))
                .findFirst();
    }
}
