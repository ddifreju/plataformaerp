package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fabricas para as lacunas do catalogo fechado da secao 9.1 do documento
 * fiscal (docs/fiscal/regras-de-margem.md) que este motor sabe DETECTAR
 * hoje. Cada metodo cita o numero da linha do catalogo no Javadoc, para
 * que seja facil conferir contra o documento daqui a alguns meses -
 * "contexto para o humano que revisar isto" (mesmo espirito da coluna
 * observacao de taxa_canal, V013).
 *
 * NAO e o catalogo inteiro: das 19 linhas da secao 9.1, so as que este
 * motor consegue detectar a partir do que ja existe no modelo (custo,
 * item_pedido, pedido) estao aqui. As demais (ICMS-ST #8, PIS/COFINS
 * monofasico #9, devolucao em curso #12...) dependem de campos que ainda
 * nao existem no schema (pendencias P1/P2 do documento fiscal, secao
 * 10.3) - relatadas, nao inventadas.
 */
public final class CatalogoLacunas {

    private CatalogoLacunas() {
        // classe utilitaria: sem instancia
    }

    /** #1 - custo do produto nao cadastrado. Falta a MAIOR parcela de custo. */
    public static Lacuna custoMercadoriaNaoCadastrado() {
        return new Lacuna(
                "custo_mercadoria_nao_cadastrado",
                "Nenhuma linha de custo MERCADORIA para este pedido: falta o custo do produto (a maior parcela "
                        + "de custo na maioria dos casos). Cadastre o custo_unitario_atual das variacoes vendidas.",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
    }

    /** #2 - item nao casado com o catalogo (decisao 0018). */
    public static Lacuna itemSemVariacao(int quantidadeItens) {
        return new Lacuna(
                "item_sem_variacao",
                quantidadeItens + " item(ns) deste pedido nao foram casados com nenhuma variacao do catalogo "
                        + "(item_pedido.variacao_id nulo) - sem CMV automatico para eles.",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
    }

    /**
     * #3 - taxa nao cadastrada para a vigencia (nivel 3 da hierarquia,
     * decisao 0019). Formato de mensagem sugerido pela secao 8.3, item 5:
     * "nao sei X para Y - cadastre e eu recalculo".
     */
    public static Lacuna taxaNaoCadastrada(String tipoTaxa, String categoriaCanal, String tipoAnuncio,
            OffsetDateTime dataConsultada) {
        return new Lacuna(
                "taxa_canal_nao_cadastrada:" + tipoTaxa,
                "Nao sei a taxa '" + tipoTaxa + "' para categoria '" + categoriaCanal + "', anuncio '" + tipoAnuncio
                        + "', vigente em " + dataConsultada + ". Cadastre e os pedidos afetados sao recalculados.",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
    }

    /** #6 - regime tributario nao configurado. Falta o imposto inteiro (decisao 0020). */
    public static Lacuna regimeTributarioNaoConfigurado() {
        return new Lacuna(
                "regime_tributario_nao_configurado",
                "Nenhuma linha de custo IMPOSTO para este pedido: o regime tributario do tenant nao esta "
                        + "configurado (decisao 0020) - falta o imposto inteiro (4% a 19% do faturamento).",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
    }

    /** #15 - canais sobrepostos (decisao 0017). Valor absoluto indeterminado, o percentual pode estar certo. */
    public static Lacuna canaisSobrepostos(List<UUID> canaisEnvolvidos) {
        return new Lacuna(
                "canais_sobrepostos",
                "A consulta abrangeria canais potencialmente sobrepostos (" + canaisEnvolvidos
                        + ") - a mesma venda pode estar contada em mais de um. Escolha um canal por consulta "
                        + "(decisao 0017).",
                DirecaoViesLacuna.INDETERMINADA);
    }

    /** #16 - repasse previsto diverge do esperado, para baixo, em valor CONHECIDO (secao 2.6). */
    public static Lacuna repasseDivergeQuantificavel(BigDecimal delta) {
        return new Lacuna(
                "repasse_diverge_quantificavel",
                "O repasse previsto pelo canal e R$ " + delta + " menor que a conferencia esperada (secao 2.6): "
                        + "existe custo do canal que nao estamos vendo, de valor conhecido.",
                DirecaoViesLacuna.SUPERESTIMA_MARGEM);
    }

    /** #17 - valor_repasse_previsto ausente. Sem conferencia externa, nenhuma garantia sobre a direcao do erro. */
    public static Lacuna repassePrevistoAusente() {
        return new Lacuna(
                "repasse_previsto_ausente",
                "pedido.valor_repasse_previsto e nulo: sem a conferencia externa da secao 2.6, nao ha garantia "
                        + "de que todos os custos do canal foram capturados.",
                DirecaoViesLacuna.INDETERMINADA);
    }
}
