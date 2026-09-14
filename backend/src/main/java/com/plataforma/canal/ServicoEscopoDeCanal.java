package com.plataforma.canal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.plataforma.ingestao.CanalDesconhecidoException;

/**
 * Tarefa 31 (decisao 0033): a lojista declara o ESCOPO de um canal -
 * {@link EscopoCanal#FONTE_PRIMARIA}, {@link EscopoCanal#ESPELHO}
 * (apontando para outro canal) ou de volta para
 * {@link EscopoCanal#NAO_DECLARADO}. Nenhuma heuristica preenche isto
 * sozinha (0033, "declarar e da lojista, nao nossa") - este servico so
 * aplica a intencao que a lojista escolheu, com as validacoes que o BANCO
 * NAO cobre sozinho (secao 6 do cabecalho da migration V016).
 *
 * <h2>O que o banco ja garante sozinho (nao repetido aqui)</h2>
 * As quatro colunas coerentes entre si (CHECK da V016), o ciclo de DOIS
 * (indice unico parcial sobre o par nao ordenado), e a FK composta que
 * impede espelhar canal de outro tenant (decisao 0015) - ver {@link Canal}
 * e o cabecalho da V016.
 *
 * <h2>O que so a aplicacao pode garantir</h2>
 * <ul>
 *   <li>Mensagem de erro clara em vez de deixar estourar violacao de
 *       FK/indice unico direto do banco (o CHECK/indice nao tem como
 *       "explicar" nada a um humano - ver o comentario da secao 6 da
 *       V016 sobre "duplicate key value violates...").</li>
 *   <li>Espelho de espelho e cadeia/ciclo de tres ou mais (o banco
 *       literalmente nao enxerga isso - secao 6 do cabecalho da V016):
 *       {@link #verificarCadeiaSemCiclo} caminha {@code espelha_canal_id}
 *       com PROFUNDIDADE MAXIMA explicita
 *       ({@link #PROFUNDIDADE_MAXIMA_CADEIA}) - sem o limite, um ciclo que
 *       o banco aceita vira laco infinito aqui.</li>
 * </ul>
 */
@Service
public class ServicoEscopoDeCanal {

    /**
     * "Canais na casa das dezenas por tenant" (V016, secao 6) - 50 e
     * generoso o bastante para nunca recusar uma cadeia legitima (que,
     * pela regra abaixo, nunca deveria ter mais de 1 nivel de qualquer
     * forma) e pequeno o bastante para nunca demorar perceptivelmente.
     */
    static final int PROFUNDIDADE_MAXIMA_CADEIA = 50;

    private final RepositorioCanal repositorioCanal;

    public ServicoEscopoDeCanal(RepositorioCanal repositorioCanal) {
        this.repositorioCanal = repositorioCanal;
    }

    /**
     * Aplica a declaracao. Idempotente: declarar o MESMO escopo duas vezes
     * produz o mesmo estado final (e um novo {@code escopoDeclaradoEm}/
     * {@code escopoDeclaradoPor}, que e o esperado - e uma nova
     * declaracao, mesmo que repita o valor anterior).
     */
    @Transactional
    public Canal declarar(UUID canalId, EscopoCanal escopo, UUID espelhaCanalId, UUID usuarioId) {
        Canal canal = buscarDoTenant(canalId);

        switch (escopo) {
            case FONTE_PRIMARIA -> canal.declararFontePrimaria(usuarioId);
            case NAO_DECLARADO -> canal.limparDeclaracao();
            case ESPELHO -> declararEspelho(canal, espelhaCanalId, usuarioId);
        }

        return repositorioCanal.save(canal);
    }

    private void declararEspelho(Canal canal, UUID espelhaCanalId, UUID usuarioId) {
        if (espelhaCanalId == null) {
            throw new CadeiaDeEspelhoInvalidaException(
                    "Para declarar ESPELHO e obrigatorio informar espelhaCanalId (o canal de quem este e copia).");
        }
        if (espelhaCanalId.equals(canal.getId())) {
            throw new CadeiaDeEspelhoInvalidaException(
                    "Canal " + canal.getCodigo() + " (" + canal.getId() + ") nao pode ser declarado espelho de "
                            + "si mesmo.");
        }

        // O alvo precisa existir E pertencer a este tenant. findById ja e
        // restrito por @TenantId (decisao 0007, camada 3) - "nao
        // encontrou" cobre os dois casos ao mesmo tempo, sem comparar
        // tenant a mao (mesmo padrao de ServicoIngestao.ingerir).
        Canal alvo = buscarDoTenant(espelhaCanalId);

        verificarCadeiaSemCiclo(canal.getId(), alvo.getId(),
                id -> repositorioCanal.findById(id).orElse(null), PROFUNDIDADE_MAXIMA_CADEIA);

        canal.declararEspelhoDe(alvo.getId(), usuarioId);
    }

    private Canal buscarDoTenant(UUID canalId) {
        return repositorioCanal.findById(canalId)
                .orElseThrow(() -> new CanalDesconhecidoException(
                        "Canal " + canalId + " nao existe ou nao pertence a este tenant."));
    }

    /**
     * Nucleo da verificacao de cadeia (secao 6 do cabecalho da V016).
     * Pacote-privado e ESTATICO, sem repositorio nem transacao, de
     * proposito: {@code buscar} e uma funcao injetada (na aplicacao real,
     * {@code RepositorioCanal::findById} mais unwrap do {@code Optional};
     * em teste, um {@code Map} em memoria) - isto permite testar o
     * algoritmo de caminhamento/ciclo com um grafo pequeno e controlado,
     * SEM subir Spring nem banco (CLAUDE.md: "regra de negocio: teste
     * unitario com assercao dura").
     *
     * <h2>A regra, em uma frase</h2>
     * O ALVO DIRETO de uma declaracao de ESPELHO precisa ser
     * {@link EscopoCanal#FONTE_PRIMARIA} (ou, no minimo, nao ser ele
     * proprio {@link EscopoCanal#ESPELHO}) - nunca outro espelho. Se o
     * alvo direto ja e ESPELHO, isto e "espelho de espelho" por
     * definicao, e e SEMPRE recusado (tarefa 31) - independente de a
     * cadeia, mais adiante, eventualmente chegar numa fonte primaria
     * valida ou fechar um ciclo. O restante deste metodo, depois de
     * decidir que vai recusar, so caminha para poder DIZER QUAL E A
     * CADEIA na mensagem, e para nunca entrar em loop infinito se essa
     * cadeia contiver um ciclo - daí a profundidade maxima explicita.
     *
     * @param origemId id do canal que esta sendo declarado ESPELHO
     * @param alvoDiretoId id do canal apontado por {@code espelhaCanalId}
     *         na declaracao proposta
     * @param buscar resolve um id de canal para o {@link Canal}
     *         correspondente, ou {@code null} se nao existir
     * @param profundidadeMaxima quantos niveis a mais, alem do alvo
     *         direto, este metodo caminha antes de desistir e recusar por
     *         "cadeia longa demais" - protege contra laco infinito num
     *         ciclo que o banco aceita (V016, secao 6)
     * @throws CadeiaDeEspelhoInvalidaException se o alvo direto ja for
     *         ESPELHO (com a cadeia inteira, ate onde foi possivel
     *         caminhar, na mensagem)
     */
    static void verificarCadeiaSemCiclo(UUID origemId, UUID alvoDiretoId, Function<UUID, Canal> buscar,
            int profundidadeMaxima) {
        Canal alvoDireto = buscar.apply(alvoDiretoId);
        if (alvoDireto == null) {
            // Defensivo: o chamador real ja teria lancado CanalDesconhecidoException
            // antes de chegar aqui (ver declararEspelho) - isto so protege
            // o nucleo puro contra uso incorreto em teste/futuro chamador.
            throw new CadeiaDeEspelhoInvalidaException("Canal espelhado " + alvoDiretoId + " nao existe.");
        }
        if (alvoDireto.getEscopoDeclarado() != EscopoCanal.ESPELHO) {
            return; // caminho feliz: alvo direto e FONTE_PRIMARIA (ou NAO_DECLARADO)
        }

        // A partir daqui, o alvo direto JA e ESPELHO: por definicao, isto
        // e "espelho de espelho", e SEMPRE sera recusado. O loop abaixo so
        // caminha para montar a mensagem e para detectar, defensivamente,
        // um ciclo de tres ou mais (que o banco nao impede - secao 6 da
        // V016) sem nunca rodar mais que profundidadeMaxima vezes.
        List<UUID> caminho = new ArrayList<>();
        caminho.add(alvoDiretoId);
        UUID atualId = alvoDireto.getEspelhaCanalId();

        for (int profundidade = 1; profundidade <= profundidadeMaxima; profundidade++) {
            caminho.add(atualId);

            if (atualId.equals(origemId)) {
                throw new CadeiaDeEspelhoInvalidaException(
                        "Declarar " + origemId + " como espelho de " + alvoDiretoId + " fecharia um CICLO: "
                                + formatarCaminho(origemId, caminho) + ". Nenhum destes canais entraria em soma "
                                + "nenhuma se isto fosse permitido - aponte diretamente para a fonte primaria.");
            }

            Canal atual = buscar.apply(atualId);
            if (atual == null) {
                throw new CadeiaDeEspelhoInvalidaException(
                        "Canal " + alvoDiretoId + " ja e declarado ESPELHO, e a cadeia dele aponta para um canal "
                                + "inexistente: " + formatarCaminho(origemId, caminho) + ". Nao e permitido "
                                + "declarar espelho de um espelho - aponte " + origemId + " diretamente para a "
                                + "fonte primaria.");
            }
            if (atual.getEscopoDeclarado() != EscopoCanal.ESPELHO) {
                throw new CadeiaDeEspelhoInvalidaException(
                        "Canal " + alvoDiretoId + " ja e declarado ESPELHO de " + atualId + " (fonte primaria). "
                                + "Nao e permitido declarar " + origemId + " como espelho de um espelho - cadeia: "
                                + formatarCaminho(origemId, caminho) + ". Aponte " + origemId + " diretamente "
                                + "para " + atualId + ".");
            }
            atualId = atual.getEspelhaCanalId();
        }

        throw new CadeiaDeEspelhoInvalidaException(
                "A cadeia de espelho a partir de " + alvoDiretoId + " passa de " + profundidadeMaxima
                        + " niveis sem chegar numa fonte primaria: " + formatarCaminho(origemId, caminho)
                        + " -> ... Isto e tratado como espelho de espelho invalido - aponte " + origemId
                        + " diretamente para a fonte primaria.");
    }

    private static String formatarCaminho(UUID origemId, List<UUID> caminho) {
        return origemId + " -> " + caminho.stream().map(UUID::toString).collect(Collectors.joining(" -> "));
    }
}
