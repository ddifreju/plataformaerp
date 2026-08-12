package com.plataforma.integracao;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Teste unitario puro (sem Spring, sem banco) da regra 2 do CLAUDE.md
 * ("dinheiro nunca e float") aplicada na leitura de JSON de fonte. Este e
 * O teste que prova que valor monetario nao perde centavo - o ponto do
 * float, pedido explicitamente pela tarefa 12.
 */
class SuporteJsonTest {

    @Test
    void decimalLeValorMonetarioSemPerderPrecisao() {
        // 19.9 e o exemplo classico onde double falha: o double mais
        // proximo de 19.9 e, na verdade,
        // 19.899999999999998578914528479799628257751464843750 (50 casas
        // de erro de representacao binaria). Um adaptador que lesse o
        // token JSON como double antes de construir o BigDecimal (via
        // node.asDouble() ou BigDecimal.valueOf(double) alimentado por um
        // double ja calculado) reproduziria esse erro. SuporteJson.decimal
        // nao: o valor sai EXATAMENTE como a fonte mandou.
        JsonNode no = SuporteJson.lerArvore("{\"valor\": 19.90, \"outro\": 100.30, \"terceiro\": 0.10}");

        BigDecimal valor = SuporteJson.decimal(no, "valor");
        assertEquals(0, valor.compareTo(new BigDecimal("19.90")), "valor deveria ser EXATAMENTE 19.90, sem ruido de double");

        BigDecimal outro = SuporteJson.decimal(no, "outro");
        assertEquals(0, outro.compareTo(new BigDecimal("100.30")));

        BigDecimal terceiro = SuporteJson.decimal(no, "terceiro");
        assertEquals(0, terceiro.compareTo(new BigDecimal("0.10")));

        // Prova documental de que o caminho ERRADO (passar por double)
        // produziria um numero visivelmente diferente - se algum dia
        // SuporteJson regredir para node.asDouble(), este contraste seria
        // exatamente o motivo do bug.
        BigDecimal viaDoubleErrado = new BigDecimal(19.90d);
        assertNotEquals(0, viaDoubleErrado.compareTo(new BigDecimal("19.90")),
                "este assert documenta o BUG que estamos evitando: new BigDecimal(double) NAO bate com o valor exato");
    }

    @Test
    void normalizarAplicaEscalaQuatroComArredondamentoExplicito() {
        BigDecimal normalizado = SuporteJson.normalizar(new BigDecimal("10.005"));
        assertEquals(4, normalizado.scale());
        // HALF_UP: 10.0050 fica 10.0050 mesmo (ja tem 3 casas, scale so
        // acrescenta zero) - o arredondamento so entra em jogo com mais
        // de 4 casas decimais na entrada.
        assertEquals(0, normalizado.compareTo(new BigDecimal("10.0050")));

        BigDecimal comCincoDecimais = SuporteJson.normalizar(new BigDecimal("10.00051"));
        assertEquals(0, comCincoDecimais.compareTo(new BigDecimal("10.0005")));
    }

    @Test
    void decimalDevolveNuloQuandoCampoAusente() {
        JsonNode no = SuporteJson.lerArvore("{\"outroCampo\": 1}");
        assertNull(SuporteJson.decimal(no, "valorQueNaoExiste"));
    }

    @Test
    void lerArvoreLancaPayloadInvalidoParaJsonMalformado() {
        assertThrows(PayloadInvalidoException.class, () -> SuporteJson.lerArvore("{isto nao e json"));
    }

    @Test
    void lerArvoreLancaPayloadInvalidoParaPayloadVazio() {
        assertThrows(PayloadInvalidoException.class, () -> SuporteJson.lerArvore(""));
        assertThrows(PayloadInvalidoException.class, () -> SuporteJson.lerArvore(null));
    }
}
