package com.plataforma.margem;

import java.util.ArrayList;
import java.util.List;

import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.pedido.ItemPedido;

/**
 * Deteccao das lacunas do catalogo (secao 9.1) que sao verificaveis
 * diretamente a partir do que ja existe no modelo canonico (custo,
 * item_pedido) - sem depender de campos que ainda nao existem no schema
 * (ICMS-ST, PIS/COFINS monofasico: pendencias P1/P2, fora de escopo desta
 * fase).
 *
 * NAO detecta: taxa nao cadastrada (#3 - e responsabilidade de quem
 * RESOLVE a hierarquia, tarefa 14, porque so ali sabe-se qual tipo_taxa
 * foi procurado e nao encontrado); repasse ausente/divergente (#16/#17 -
 * ver {@link ConferenciaRepasse}, que precisa do valor de repasse alem da
 * lista de custo); canais sobrepostos (#15 - decisao de ESCOPO da
 * consulta, nao do pedido individual, ver {@code ServicoMargemPeriodo}).
 *
 * NAO DETECTA E AINDA NAO TEM DONO — antecipacao de recebivel (#11).
 *
 * Esta e a unica lacuna do catalogo §9.1 que nao e detectada aqui, nem
 * delegada a outra classe, nem coberta por decisao registrada. Fica dita
 * em voz alta porque o documento fiscal a classifica como "a lacuna mais
 * silenciosa": diferente das outras, ela NAO deixa rastro em campo
 * nenhum. Um custo que nao existe no payload e que nao tem taxa
 * cadastrada e indistinguivel, para o codigo, de um custo que
 * legitimamente nao se aplica.
 *
 * Consequencia pratica: para um lojista que antecipa recebiveis e nao
 * cadastrou a taxa, a margem sai SUPERESTIMADA e o sistema NAO avisa.
 * Detectar exige saber que aquele tenant antecipa - informacao que hoje
 * nao existe em lugar nenhum do modelo. Enquanto nao existir, esta
 * ausencia esta registrada em docs/ESTADO.md, nao escondida aqui.
 */
public final class DetectorDeLacunas {

    private DetectorDeLacunas() {
        // classe utilitaria: sem instancia
    }

    public static List<Lacuna> detectar(List<Custo> custos, List<ItemPedido> itens) {
        List<Lacuna> lacunas = new ArrayList<>();

        boolean temMercadoria = custos.stream().anyMatch(custo -> custo.getNatureza() == NaturezaCusto.MERCADORIA);
        if (!temMercadoria) {
            // #1: nenhuma linha MERCADORIA para o pedido inteiro. Falta a
            // maior parcela de custo na maioria dos casos.
            lacunas.add(CatalogoLacunas.custoMercadoriaNaoCadastrado());
        }

        boolean temImposto = custos.stream().anyMatch(custo -> custo.getNatureza() == NaturezaCusto.IMPOSTO);
        if (!temImposto) {
            // #6: sem linha IMPOSTO, o regime tributario nao esta
            // configurado (decisao 0020: imposto e sempre lacuna declarada
            // ate a tabela de regime existir).
            lacunas.add(CatalogoLacunas.regimeTributarioNaoConfigurado());
        }

        long itensSemVariacao = (itens != null)
                ? itens.stream().filter(item -> item.getVariacaoId() == null).count()
                : 0;
        if (itensSemVariacao > 0) {
            // #2 (decisao 0018): item nao casado com o catalogo. Fica de
            // fora do CMV automatico mesmo quando MERCADORIA existe para
            // outros itens do mesmo pedido.
            lacunas.add(CatalogoLacunas.itemSemVariacao((int) itensSemVariacao));
        }

        return lacunas;
    }
}
