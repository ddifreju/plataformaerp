import type { EscopoCanal, RespostaCanal } from "./api/tipos";

/**
 * Lógica pura da tela "Canais" (tarefa 34, decisão 0033) — extraída para
 * cá pelo mesmo motivo de `lacunas.ts`/`pergunta.ts`: nada aqui depende de
 * DOM nem de `fetch`, então testa como função pura, sem framework de teste
 * de UI.
 *
 * NADA neste arquivo decide um valor de escopo por heurística. As funções
 * abaixo só (a) filtram opções que o backend nunca aceitaria de qualquer
 * forma, ou (b) traduzem um estado já declarado para texto — nunca
 * adivinham a declaração em si (decisão 0033, "declarar é da lojista, não
 * nossa").
 */

/**
 * Canais que podem ser escolhidos como origem de uma declaração de
 * ESPELHO para `canalId`: nunca o próprio canal
 * (`ck_canal_nao_espelha_a_si_mesmo`) e nunca um canal que já é ESPELHO de
 * outro — o backend recusa "espelho de espelho" com 409
 * (`CadeiaDeEspelhoInvalidaException`, `ServicoEscopoDeCanal`).
 *
 * Isto NÃO é a heurística que a decisão 0033 proíbe: não estamos
 * decidindo QUAL canal é a fonte primária, só removendo da lista opções
 * que o backend rejeitaria de qualquer forma — o mesmo papel que um
 * `<select>` cumpre ao não listar o próprio registro sendo editado.
 */
export function candidatosParaEspelho(canais: RespostaCanal[], canalId: string): RespostaCanal[] {
  return canais.filter((canal) => canal.id !== canalId && canal.escopoDeclarado !== "ESPELHO");
}

/**
 * Nome do canal apontado por `espelhaCanalId` — a tela nunca mostra o
 * UUID cru para a lojista (instrução explícita da tarefa 34). Se o canal
 * não estiver (mais) na lista carregada, diz isso em vez de quebrar ou de
 * inventar um nome.
 */
export function nomeDoCanalPorId(canais: RespostaCanal[], id: string): string {
  const alvo = canais.find((canal) => canal.id === id);
  return alvo?.nome ?? "canal não encontrado nesta lista";
}

export interface TextoEstadoEscopo {
  /** Rótulo curto, para o cabeçalho do estado. */
  rotulo: string;
  /** Frase de uma linha explicando o que o rótulo significa agora. */
  detalhe: string;
}

/**
 * Texto do estado ATUAL de um canal — nunca o texto do formulário de
 * declaração (que fica em branco, sem pré-seleção; ver o componente da
 * tela). Para `ESPELHO`, `detalhe` nomeia o canal de origem pelo nome.
 */
export function textoDoEstadoDeEscopo(canal: RespostaCanal, canais: RespostaCanal[]): TextoEstadoEscopo {
  if (canal.escopoDeclarado === "NAO_DECLARADO") {
    return {
      rotulo: "Não declarado",
      detalhe: "Bloqueia a soma deste canal com outros até alguém declarar a origem dos pedidos.",
    };
  }

  if (canal.escopoDeclarado === "FONTE_PRIMARIA") {
    return {
      rotulo: "Fonte primária",
      detalhe: "Os pedidos deste canal nascem aqui.",
    };
  }

  const nomeOrigem = canal.espelhaCanalId ? nomeDoCanalPorId(canais, canal.espelhaCanalId) : "canal não informado";
  return {
    rotulo: "Espelho",
    detalhe: `Os pedidos daqui são cópia dos de ${nomeOrigem}. Este canal continua ingerindo e continua consultável sozinho — a declaração afeta só a soma entre canais.`,
  };
}

/** Discrimina, num `<select>`, qual opção de escopo a lojista escolheu — nunca pré-marcada. */
export type EscolhaEscopo = "" | Extract<EscopoCanal, "FONTE_PRIMARIA" | "ESPELHO">;
