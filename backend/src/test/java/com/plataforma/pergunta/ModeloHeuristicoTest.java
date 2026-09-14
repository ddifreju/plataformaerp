package com.plataforma.pergunta;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste unitário puro (sem Spring, sem banco) de {@link ModeloHeuristico}.
 * Prova as duas metades da decisão 0030: casa as cinco intenções do
 * catálogo em frases escritas de formas diferentes das do próprio
 * catálogo, E devolve confiança baixa em pergunta fora do domínio -
 * "conservador por construção", nunca um chute.
 */
class ModeloHeuristicoTest {

    private final ModeloHeuristico modelo = new ModeloHeuristico();
    private final CatalogoDePerguntas catalogo = new CatalogoDePerguntas();
    private final List<DescricaoIntencao> descricoes = catalogo.descricoes();

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
}
