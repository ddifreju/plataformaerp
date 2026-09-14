package com.plataforma.pergunta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.plataforma.canal.Canal;
import com.plataforma.canal.CategoriaCanal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitário puro (Mockito, sem Spring, sem banco) de
 * {@link ModeloHeuristico}. Prova três coisas da decisão 0030: casa as
 * cinco intenções do catálogo em frases escritas de formas diferentes das
 * do próprio catálogo; devolve confiança baixa em pergunta fora do domínio
 * ("conservador por construção", nunca um chute); e extrai {@code canal}
 * e {@code periodoRelativo} de texto livre de forma determinística, nunca
 * escolhendo quando o texto é ambíguo.
 *
 * Isolamento de tenant: {@link RepositorioCanal#findAllByOrderByNomeAsc()}
 * já filtra por tenant sozinho via {@code @TenantId}/RLS (decisão 0007) -
 * não é uma consulta nova introduzida aqui, então não é reexercitado com
 * Postgres real; a garantia estrutural já existe nos testes de isolamento
 * do pacote {@code canal}. O que ESTE teste garante é que, dado o que o
 * repositório (já filtrado) devolveu, a heurística nunca "escolhe" entre
 * dois canais que casaram - ver {@link #doisCanaisCitadosDeixaParametroAusente()}.
 */
class ModeloHeuristicoTest {

    private final RepositorioCanal repositorioCanal = mock(RepositorioCanal.class);
    private final ModeloHeuristico modelo = new ModeloHeuristico(repositorioCanal);
    private final CatalogoDePerguntas catalogo = new CatalogoDePerguntas();
    private final List<DescricaoIntencao> descricoes = catalogo.descricoes();

    private Canal canal(String codigo, String nome) {
        return new Canal(codigo, nome, TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, null, null, null);
    }

    @Test
    void casaMargemDoPeriodoEmFraseDiferenteDoCatalogo() {
        IntencaoDetectada deteccao = modelo.interpretar("quanto foi a margem do canal Amazon em setembro", descricoes);

        assertEquals(CodigoIntencao.MARGEM_DO_PERIODO.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaMargemDoPeriodoEmSegundaFrase() {
        IntencaoDetectada deteccao = modelo.interpretar("quero saber quanto ganhei no Mercado Livre ano passado", descricoes);

        assertEquals(CodigoIntencao.MARGEM_DO_PERIODO.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaLacunasDaMargemEmFraseDiferenteDoCatalogo() {
        IntencaoDetectada deteccao = modelo.interpretar(
                "quais informacoes estao faltando para fechar a margem da loja propria", descricoes);

        assertEquals(CodigoIntencao.LACUNAS_DA_MARGEM.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaLacunasDaMargemEmSegundaFrase() {
        IntencaoDetectada deteccao = modelo.interpretar(
                "estao faltando dados para eu fechar a margem do canal Amazon", descricoes);

        assertEquals(CodigoIntencao.LACUNAS_DA_MARGEM.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaGargalosDaOperacaoEmFraseDiferenteDoCatalogo() {
        IntencaoDetectada deteccao = modelo.interpretar("queria saber onde a operacao esta travando agora", descricoes);

        assertEquals(CodigoIntencao.GARGALOS_DA_OPERACAO.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaGargalosDaOperacaoEmSegundaFrase() {
        IntencaoDetectada deteccao = modelo.interpretar("tem algum pedido parado esperando processo?", descricoes);

        assertEquals(CodigoIntencao.GARGALOS_DA_OPERACAO.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaFilaDePendenciasEmFraseDiferenteDoCatalogo() {
        IntencaoDetectada deteccao = modelo.interpretar("o que eu tenho para resolver hoje na minha fila?", descricoes);

        assertEquals(CodigoIntencao.FILA_DE_PENDENCIAS.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaFilaDePendenciasEmSegundaFrase() {
        IntencaoDetectada deteccao = modelo.interpretar("existe alguma pendencia esperando resolver na fila?", descricoes);

        assertEquals(CodigoIntencao.FILA_DE_PENDENCIAS.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaCanaisDisponiveisEmFraseDiferenteDoCatalogo() {
        IntencaoDetectada deteccao = modelo.interpretar("quais canais de venda estao cadastrados aqui?", descricoes);

        assertEquals(CodigoIntencao.CANAIS_DISPONIVEIS.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void casaCanaisDisponiveisEmSegundaFrase() {
        IntencaoDetectada deteccao = modelo.interpretar("pode me listar os canais que eu tenho disponiveis?", descricoes);

        assertEquals(CodigoIntencao.CANAIS_DISPONIVEIS.name(), deteccao.codigoBruto());
        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) >= 0);
    }

    @Test
    void devolveConfiancaBaixaParaPerguntaForaDoDominio1() {
        IntencaoDetectada deteccao = modelo.interpretar("qual a capital da franca", descricoes);

        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) < 0,
                "pergunta totalmente fora do dominio nao deveria passar do limiar de confianca");
    }

    @Test
    void devolveConfiancaBaixaParaPerguntaForaDoDominio2() {
        IntencaoDetectada deteccao = modelo.interpretar("me manda o boleto", descricoes);

        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) < 0,
                "pergunta totalmente fora do dominio nao deveria passar do limiar de confianca");
    }

    @Test
    void perguntaVaziaDevolveConfiancaBaixaSemQuebrar() {
        IntencaoDetectada deteccao = modelo.interpretar("   ", descricoes);

        assertTrue(deteccao.confianca().compareTo(ServicoPergunta.CONFIANCA_MINIMA) < 0);
    }

    // ------------------------------------------------------------------
    // Extração de parâmetro - canal
    // ------------------------------------------------------------------

    @Test
    void extraiCanalPeloNomeComESemAcentoEEmCaixasDiferentes() {
        when(repositorioCanal.findAllByOrderByNomeAsc())
                .thenReturn(List.of(canal("mercado-livre-classico", "ML Clássico")));

        IntencaoDetectada semAcento = modelo.interpretar("quanto sobrou no ml classico mês passado?", descricoes);
        IntencaoDetectada comAcentoECaixaDiferente =
                modelo.interpretar("Quanto sobrou no ML Clássico mês passado?", descricoes);

        assertEquals("ML Clássico", semAcento.parametros().get("canal"));
        assertEquals("ML Clássico", comAcentoECaixaDiferente.parametros().get("canal"));
    }

    @Test
    void doisCanaisCitadosDeixaParametroAusente() {
        when(repositorioCanal.findAllByOrderByNomeAsc())
                .thenReturn(List.of(canal("mercado-livre-classico", "ML Clássico"), canal("shopee", "Shopee")));

        IntencaoDetectada deteccao =
                modelo.interpretar("quanto sobrou no ML Clássico ou no Shopee mês passado?", descricoes);

        assertFalse(deteccao.parametros().containsKey("canal"),
                "dois canais casaram na mesma pergunta - o modelo nao pode escolher por conta propria");
    }

    @Test
    void canalQueNaoExisteNoTenantDeixaParametroAusente() {
        when(repositorioCanal.findAllByOrderByNomeAsc()).thenReturn(List.of(canal("shopee", "Shopee")));

        IntencaoDetectada deteccao = modelo.interpretar("quanto sobrou na Amazon mês passado?", descricoes);

        assertFalse(deteccao.parametros().containsKey("canal"));
    }

    // ------------------------------------------------------------------
    // Extração de parâmetro - período
    // ------------------------------------------------------------------

    @Test
    void casaCadaExpressaoDePeriodoDaListaFechada() {
        Map<String, PeriodoRelativo> casos = new LinkedHashMap<>();
        casos.put("mês passado", PeriodoRelativo.MES_PASSADO);
        casos.put("mes passado", PeriodoRelativo.MES_PASSADO);
        casos.put("último mês", PeriodoRelativo.MES_PASSADO);
        casos.put("este mês", PeriodoRelativo.MES_ATUAL);
        casos.put("esse mês", PeriodoRelativo.MES_ATUAL);
        casos.put("mês atual", PeriodoRelativo.MES_ATUAL);
        casos.put("no mês", PeriodoRelativo.MES_ATUAL);
        casos.put("últimos 7 dias", PeriodoRelativo.ULTIMOS_7_DIAS);
        casos.put("última semana", PeriodoRelativo.ULTIMOS_7_DIAS);
        casos.put("7 dias", PeriodoRelativo.ULTIMOS_7_DIAS);
        casos.put("últimos 30 dias", PeriodoRelativo.ULTIMOS_30_DIAS);
        casos.put("30 dias", PeriodoRelativo.ULTIMOS_30_DIAS);
        casos.put("último mês corrido", PeriodoRelativo.ULTIMOS_30_DIAS);
        casos.put("últimos 90 dias", PeriodoRelativo.ULTIMOS_90_DIAS);
        casos.put("90 dias", PeriodoRelativo.ULTIMOS_90_DIAS);
        casos.put("trimestre", PeriodoRelativo.ULTIMOS_90_DIAS);
        casos.put("este ano", PeriodoRelativo.ANO_ATUAL);
        casos.put("esse ano", PeriodoRelativo.ANO_ATUAL);
        casos.put("ano atual", PeriodoRelativo.ANO_ATUAL);
        casos.put("no ano", PeriodoRelativo.ANO_ATUAL);

        for (Map.Entry<String, PeriodoRelativo> caso : casos.entrySet()) {
            IntencaoDetectada deteccao = modelo.interpretar("quanto sobrou " + caso.getKey() + "?", descricoes);

            assertEquals(caso.getValue().name(), deteccao.parametros().get("periodoRelativo"),
                    "esperava " + caso.getValue() + " para a expressao \"" + caso.getKey() + "\"");
        }
    }

    @Test
    void expressaoDePeriodoMaisEspecificaVenceAMaisCurtaQuandoAmbasCasam() {
        // "no mês" tambem casaria (MES_ATUAL) - "mês passado" e mais longa
        // e mais especifica, e tem que vencer.
        IntencaoDetectada deteccao = modelo.interpretar("quanto sobrou no mês passado?", descricoes);

        assertEquals(PeriodoRelativo.MES_PASSADO.name(), deteccao.parametros().get("periodoRelativo"));
    }

    @Test
    void nenhumaExpressaoDePeriodoConhecidaDeixaParametroAusente() {
        IntencaoDetectada deteccao = modelo.interpretar("quanto sobrou no Mercado Livre em setembro?", descricoes);

        assertFalse(deteccao.parametros().containsKey("periodoRelativo"),
                "data absoluta/nome de mes fica de fora de proposito - nao e para inventar padrao");
    }

    // ------------------------------------------------------------------
    // Pergunta realista com os dois parâmetros
    // ------------------------------------------------------------------

    @Test
    void perguntaRealistaExtraiIntencaoEOsDoisParametros() {
        when(repositorioCanal.findAllByOrderByNomeAsc())
                .thenReturn(List.of(canal("mercado-livre-classico", "ML Clássico"), canal("shopee", "Shopee")));

        IntencaoDetectada deteccao = modelo.interpretar("quanto sobrou no ML Clássico mês passado?", descricoes);

        assertEquals(CodigoIntencao.MARGEM_DO_PERIODO.name(), deteccao.codigoBruto());
        assertEquals("ML Clássico", deteccao.parametros().get("canal"));
        assertEquals(PeriodoRelativo.MES_PASSADO.name(), deteccao.parametros().get("periodoRelativo"));
    }
}
