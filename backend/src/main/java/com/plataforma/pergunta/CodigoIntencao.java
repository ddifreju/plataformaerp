package com.plataforma.pergunta;

/**
 * Catálogo FECHADO de perguntas que o sistema sabe responder (decisão
 * 0030). Uma pergunta nova é código, nunca prompt - ver o Javadoc de
 * {@link CatalogoDePerguntas}.
 *
 * A ORDEM não importa aqui (ao contrário de {@code BlocoMargem}) - é
 * apenas a lista fechada. Adicionar um valor novo é decisão de produto
 * (o catálogo cresce por demanda observada, não por antecipação - decisão
 * 0030) e sempre vem acompanhado de uma nova entrada em
 * {@link CatalogoDePerguntas} e de um novo caminho em
 * {@link ServicoPergunta#responder(String)}.
 */
public enum CodigoIntencao {

    /** "Quanto sobrou no período X, no canal Y" - delega a {@code ServicoMargemPeriodo}. */
    MARGEM_DO_PERIODO,

    /** "O que falta para calcular a margem com confiança" - mesma consulta acima, foco nas lacunas. */
    LACUNAS_DA_MARGEM,

    /** "Onde a operação está travando" - delega a {@code ServicoPainelGestor.gargalos()}. */
    GARGALOS_DA_OPERACAO,

    /** "O que eu preciso resolver" - delega a {@code ServicoPainelAnalista.pendencias()}. */
    FILA_DE_PENDENCIAS,

    /** "Quais canais eu tenho cadastrados" - delega a {@code RepositorioCanal.findAllByOrderByNomeAsc()}. */
    CANAIS_DISPONIVEIS
}
