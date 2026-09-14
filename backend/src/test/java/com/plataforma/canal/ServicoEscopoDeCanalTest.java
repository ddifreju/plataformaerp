package com.plataforma.canal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste UNITARIO PURO (sem Spring, sem banco) do nucleo de verificacao de
 * cadeia/ciclo de {@link ServicoEscopoDeCanal#verificarCadeiaSemCiclo} -
 * tarefa 31, secao 6 do cabecalho da migration V016.
 *
 * <h2>Por que os canais aqui sao montados em memoria, nunca persistidos</h2>
 * {@link Canal#declararEspelhoDe} (a entidade) so valida o QUE DEPENDE DO
 * PROPRIO OBJETO (alvo informado, alvo diferente de si mesmo) - ela NAO
 * cruza-valida contra o escopo de outros canais, porque uma entidade
 * isolada nao tem como enxergar outra linha. Isso e o que permite montar,
 * de proposito, um grafo A-&gt;B-&gt;C-&gt;A totalmente em memoria para
 * este teste: e exatamente o estado que a MIGRATION V016 documenta como
 * "o banco nao pega" (ciclo de tres ou mais) e que
 * {@link ServicoEscopoDeCanal} existe para recusar ANTES de chegar no
 * banco.
 *
 * Em uso real (atraves de {@link ServicoEscopoDeCanal#declarar}), um
 * ciclo destes e estruturalmente impossivel de se formar: toda declaracao
 * de ESPELHO passa por este mesmo metodo, que recusa qualquer alvo cujo
 * escopo ja seja ESPELHO. Este teste prova o que acontece SE, ainda
 * assim, uma cadeia cilica chegar a este metodo (dado corrompido, import
 * em massa, ou um futuro caminho de codigo que esqueca de chamar a
 * validacao) - a V016 e explicita que o BANCO nao impede isto sozinho,
 * entao a aplicacao precisa.
 */
class ServicoEscopoDeCanalTest {

    @Test
    void alvoFontePrimariaNaoLancaNada() {
        Canal alvo = novoCanal("alvo");
        alvo.declararFontePrimaria(null);

        Map<UUID, Canal> grafo = mapaDe(alvo);

        assertDoesNotThrow(() -> ServicoEscopoDeCanal.verificarCadeiaSemCiclo(
                UUID.randomUUID(), alvo.getId(), grafo::get, 50));
    }

    @Test
    void alvoNaoDeclaradoNaoLancaNada() {
        Canal alvo = novoCanal("alvo-nao-declarado");

        Map<UUID, Canal> grafo = mapaDe(alvo);

        assertDoesNotThrow(() -> ServicoEscopoDeCanal.verificarCadeiaSemCiclo(
                UUID.randomUUID(), alvo.getId(), grafo::get, 50));
    }

    /**
     * "Espelho de espelho": o alvo direto ja e ESPELHO de outra coisa
     * (que por sua vez e uma fonte primaria valida - a cadeia RESOLVERIA
     * normalmente se fosse permitida). Precisa ser recusado mesmo assim -
     * a tarefa 31 e explicita: "espelho de espelho: recuse", nao "recuse
     * so se formar ciclo".
     */
    @Test
    void alvoJaEspelhoDeOutroCanalLancaCadeiaInvalidaComACadeiaNaMensagem() {
        Canal raiz = novoCanal("raiz");
        raiz.declararFontePrimaria(null);
        Canal alvo = novoCanal("alvo-ja-espelho");
        alvo.declararEspelhoDe(raiz.getId(), null);

        Map<UUID, Canal> grafo = mapaDe(raiz, alvo);
        UUID origemId = UUID.randomUUID();

        CadeiaDeEspelhoInvalidaException erro = assertThrows(CadeiaDeEspelhoInvalidaException.class,
                () -> ServicoEscopoDeCanal.verificarCadeiaSemCiclo(origemId, alvo.getId(), grafo::get, 50));

        assertTrue(erro.getMessage().contains(alvo.getId().toString()),
                "a mensagem deveria nomear o canal que ja e espelho");
        assertTrue(erro.getMessage().contains(raiz.getId().toString()),
                "a mensagem deveria nomear a fonte primaria da cadeia, para a lojista saber para onde apontar");
    }

    /**
     * O CASO CENTRAL desta tarefa: ciclo de TRES. A declara-se espelho de
     * B, B de C, C de A - os tres pares (A,B), (B,C), (C,A) sao
     * DISTINTOS, entao {@code uq_canal_espelho_reciproco} (indice sobre o
     * par NAO ORDENADO, que so pega ciclo de DOIS) nao acusaria nada no
     * banco. Sem limite de profundidade explicito, caminhar B-&gt;C-&gt;A-&gt;B-&gt;C-&gt;...
     * entraria em loop infinito - este teste prova que
     * {@code verificarCadeiaSemCiclo} termina e recusa.
     */
    @Test
    void cicloDeTresCanaisELancaCadeiaInvalidaSemEntrarEmLacoInfinito() {
        Canal canalA = novoCanal("ciclo-a");
        Canal canalB = novoCanal("ciclo-b");
        Canal canalC = novoCanal("ciclo-c");
        // Bypassa deliberadamente ServicoEscopoDeCanal (ver o Javadoc da
        // classe): a entidade sozinha nao cruza-valida contra as outras.
        canalA.declararEspelhoDe(canalB.getId(), null);
        canalB.declararEspelhoDe(canalC.getId(), null);
        canalC.declararEspelhoDe(canalA.getId(), null);

        Map<UUID, Canal> grafo = mapaDe(canalA, canalB, canalC);

        // Simula "declarar canalA como espelho de canalB" (a aresta que,
        // combinada com as duas ja existentes, fecha o ciclo).
        CadeiaDeEspelhoInvalidaException erro = assertThrows(CadeiaDeEspelhoInvalidaException.class,
                () -> ServicoEscopoDeCanal.verificarCadeiaSemCiclo(canalA.getId(), canalB.getId(), grafo::get, 50));

        assertTrue(erro.getMessage().toUpperCase().contains("CICLO"),
                "a mensagem deveria dizer explicitamente que e um ciclo, nao so uma cadeia longa");
        assertTrue(erro.getMessage().contains(canalA.getId().toString())
                        && erro.getMessage().contains(canalB.getId().toString())
                        && erro.getMessage().contains(canalC.getId().toString()),
                "a mensagem deveria nomear os TRES canais do ciclo, para a lojista conseguir corrigir");
    }

    /**
     * Profundidade maxima EXPLICITA (V016, secao 6: "sem ele, um ciclo
     * que o banco aceita vira laco infinito"). Cadeia bem mais longa que
     * o limite passado, SEM ciclo (todo espelho aponta para o proximo,
     * numa linha reta) - ainda assim precisa ser recusada, e recusada
     * SEM percorrer alem do limite.
     */
    @Test
    void cadeiaMaiorQueOLimiteDeProfundidadeELancaSemPercorrerAlemDele() {
        int profundidadeMaxima = 3;
        List<Canal> cadeia = new ArrayList<>();
        for (int i = 0; i < profundidadeMaxima + 5; i++) {
            cadeia.add(novoCanal("cadeia-" + i));
        }
        // cadeia[0] -> cadeia[1] -> ... -> cadeia[ultimo] (ESPELHO ate o
        // penultimo; o ultimo fica NAO_DECLARADO, entao a cadeia TERIA
        // uma raiz valida se pudesse ser percorrida ate o fim - mas o
        // limite de profundidade precisa recusar antes disso).
        for (int i = 0; i < cadeia.size() - 1; i++) {
            cadeia.get(i).declararEspelhoDe(cadeia.get(i + 1).getId(), null);
        }

        Map<UUID, Canal> grafo = mapaDe(cadeia.toArray(new Canal[0]));
        UUID origemId = UUID.randomUUID();

        CadeiaDeEspelhoInvalidaException erro = assertThrows(CadeiaDeEspelhoInvalidaException.class,
                () -> ServicoEscopoDeCanal.verificarCadeiaSemCiclo(
                        origemId, cadeia.get(0).getId(), grafo::get, profundidadeMaxima));

        assertTrue(erro.getMessage().contains(String.valueOf(profundidadeMaxima)),
                "a mensagem deveria citar o limite de profundidade explicito usado");
    }

    @Test
    void alvoInexistenteNoGrafoLancaCadeiaInvalida() {
        Map<UUID, Canal> grafoVazio = new HashMap<>();

        assertThrows(CadeiaDeEspelhoInvalidaException.class,
                () -> ServicoEscopoDeCanal.verificarCadeiaSemCiclo(
                        UUID.randomUUID(), UUID.randomUUID(), grafoVazio::get, 50));
    }

    private static Canal novoCanal(String marcador) {
        return new Canal("canal-" + marcador + "-" + UUID.randomUUID().toString().substring(0, 8),
                marcador, TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, null, null, null);
    }

    private static Map<UUID, Canal> mapaDe(Canal... canais) {
        Map<UUID, Canal> mapa = new HashMap<>();
        for (Canal canal : canais) {
            mapa.put(canal.getId(), canal);
        }
        return mapa;
    }
}
