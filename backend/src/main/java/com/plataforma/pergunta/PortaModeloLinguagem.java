package com.plataforma.pergunta;

import java.util.List;

/**
 * Fronteira entre texto livre e o catálogo fechado (decisão 0030,
 * arquitetura da camada de IA). Único ponto de contato com um modelo de
 * linguagem em todo o sistema - o modelo INTERPRETA a pergunta, nunca
 * calcula, consulta ou redige número (regra 5 do CLAUDE.md).
 *
 * Implementações hoje:
 * <ul>
 *   <li>{@link ModeloHeuristico} - casamento por palavra-chave, roda em
 *       produção enquanto não houver chave de LLM (é o que está registrado
 *       em {@code docs/PENDENCIAS.md}, "Chave de API de LLM").</li>
 *   <li>Um dublê gravado, específico de teste (fixture de pergunta →
 *       intenção, incluindo saída adversária) - responsabilidade da tarefa
 *       25, não criado aqui. A interface de um método só existe justamente
 *       para que esse dublê seja trivial de escrever.</li>
 * </ul>
 *
 * Quando a chave de LLM existir, entra {@code ModeloAnthropic} implementando
 * esta mesma interface, e nada mais no pacote muda - é a única alteração
 * (decisão 0030, seção "sem chave de LLM").
 */
public interface PortaModeloLinguagem {

    /**
     * @param perguntaDoUsuario texto livre digitado pela lojista
     * @param catalogo as intenções que o sistema sabe responder HOJE -
     *      passado explicitamente a cada chamada (em vez de a implementação
     *      conhecer o catálogo por fora) para que o catálogo continue sendo
     *      a única fonte de verdade de {@link CatalogoDePerguntas}
     * @return a intenção interpretada, nunca {@code null}
     */
    IntencaoDetectada interpretar(String perguntaDoUsuario, List<DescricaoIntencao> catalogo);
}
