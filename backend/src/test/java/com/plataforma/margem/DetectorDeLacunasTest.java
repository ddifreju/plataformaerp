package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.plataforma.custo.Custo;
import com.plataforma.custo.NaturezaCusto;
import com.plataforma.pedido.ItemPedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes PUROS de {@link DetectorDeLacunas}.
 */
class DetectorDeLacunasTest {

    private static final UUID PEDIDO_ID = UUID.randomUUID();
    private static final OffsetDateTime FEITO_EM = OffsetDateTime.parse("2026-03-14T15:00:00-03:00");

    @Test
    void semMercadoriaSemImpostoGeraAsDuasLacunas() {
        List<Lacuna> lacunas = DetectorDeLacunas.detectar(List.of(), List.of());

        Set<String> codigos = lacunas.stream().map(Lacuna::codigo).collect(Collectors.toSet());
        assertEquals(2, lacunas.size());
        assertTrue(codigos.contains("custo_mercadoria_nao_cadastrado"));
        assertTrue(codigos.contains("regime_tributario_nao_configurado"));
    }

    @Test
    void comMercadoriaEImpostoNenhumaLacunaDessasDuas() {
        List<Custo> custos = List.of(
                custo(NaturezaCusto.MERCADORIA, new BigDecimal("50.0000")),
                custo(NaturezaCusto.IMPOSTO, new BigDecimal("5.0000")));

        List<Lacuna> lacunas = DetectorDeLacunas.detectar(custos, List.of());

        assertTrue(lacunas.isEmpty());
    }

    @Test
    void itemSemVariacaoGeraLacunaComContagemCorreta() {
        ItemPedido comVariacao = new ItemPedido(PEDIDO_ID, UUID.randomUUID(), "SKU-1", "Produto 1",
                BigDecimal.ONE, new BigDecimal("10.0000"), BigDecimal.ZERO, new BigDecimal("10.0000"), "ext-1", "{}");
        ItemPedido semVariacao1 = new ItemPedido(PEDIDO_ID, null, "SKU-2", "Produto 2",
                BigDecimal.ONE, new BigDecimal("20.0000"), BigDecimal.ZERO, new BigDecimal("20.0000"), "ext-2", "{}");
        ItemPedido semVariacao2 = new ItemPedido(PEDIDO_ID, null, "SKU-3", "Produto 3",
                BigDecimal.ONE, new BigDecimal("30.0000"), BigDecimal.ZERO, new BigDecimal("30.0000"), "ext-3", "{}");

        List<Lacuna> lacunas = DetectorDeLacunas.detectar(List.of(), List.of(comVariacao, semVariacao1, semVariacao2));

        Lacuna lacunaItem = lacunas.stream()
                .filter(l -> l.codigo().equals("item_sem_variacao"))
                .findFirst().orElseThrow();
        assertTrue(lacunaItem.descricao().contains("2 item"), "descricao deveria citar a contagem (2), nao so 'existe'");
        assertEquals(DirecaoViesLacuna.SUPERESTIMA_MARGEM, lacunaItem.direcaoVies());
    }

    private static Custo custo(NaturezaCusto natureza, BigDecimal valor) {
        return new Custo(natureza, PEDIDO_ID, null, null, valor, "BRL", FEITO_EM, false,
                null, null, null, null, "teste", null, null, "{}");
    }
}
