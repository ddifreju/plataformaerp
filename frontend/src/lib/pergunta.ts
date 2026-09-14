import type { TipoResposta } from "./api/tipos";

/**
 * Lógica pura da tela "Perguntar" (tarefa 26) — extraída para cá porque
 * não é testável como componente sem framework novo de teste de UI
 * (ver `CLAUDE.md`/instrução da tarefa: "se a lógica não for testável
 * como função pura, extraia-a").
 */

/** Mesmo limite de `RequisicaoPergunta.java` (`@Size(max = 500)`) e de `ServicoPergunta.TAMANHO_MAXIMO_PERGUNTA`. */
export const LIMITE_CARACTERES_PERGUNTA = 500;

/**
 * Os três tipos de resposta (decisão 0030) mapeados para o estado visual
 * que a tela usa para decidir QUAL bloco desenhar — nenhum dos três é
 * "erro": RECUSA e ESCLARECIMENTO são caminhos de primeira classe, só
 * COM APARÊNCIA diferente de RESPOSTA.
 */
export type EstadoVisualResposta = "resposta" | "esclarecimento" | "recusa";

export function estadoVisualParaTipo(tipo: TipoResposta): EstadoVisualResposta {
  switch (tipo) {
    case "RESPOSTA":
      return "resposta";
    case "ESCLARECIMENTO":
      return "esclarecimento";
    case "RECUSA":
      return "recusa";
  }
}

export interface ContagemCaracteres {
  usados: number;
  restantes: number;
  excedeu: boolean;
}

/** Contador discreto do campo de pergunta — nunca soma/reformata o texto, só mede `length`. */
export function contarCaracteres(
  texto: string,
  limite: number = LIMITE_CARACTERES_PERGUNTA,
): ContagemCaracteres {
  const usados = texto.length;
  return { usados, restantes: limite - usados, excedeu: usados > limite };
}

/** Corta o texto no limite — defesa para preencher o campo a partir de uma sugestão/atalho, nunca usado no que o backend recebe sem passar de novo pelo `<textarea maxLength>`. */
export function limitarTexto(texto: string, limite: number = LIMITE_CARACTERES_PERGUNTA): string {
  return texto.length > limite ? texto.slice(0, limite) : texto;
}

const TEXTO_PERIODO_RELATIVO: Record<string, string> = {
  MES_ATUAL: "neste mês",
  MES_PASSADO: "no mês passado",
  ULTIMOS_7_DIAS: "nos últimos 7 dias",
  ULTIMOS_30_DIAS: "nos últimos 30 dias",
  ULTIMOS_90_DIAS: "nos últimos 90 dias",
  ANO_ATUAL: "este ano",
};

export interface AtalhoAmbiguidadeMargem {
  rotulo: string;
  perguntaSugerida: string;
}

/**
 * `docs/avaliacao-camada-de-ia.md`: MARGEM_DO_PERIODO e LACUNAS_DA_MARGEM
 * compartilham quase todo o vocabulário e a interpretação às vezes troca
 * uma pela outra com confiança alta. Mitigação de tela: sempre que a
 * resposta for uma dessas duas, oferecer um atalho de um clique para a
 * outra, reaproveitando o canal/período já reconhecidos (nunca inventando
 * um novo) — se a interpretação trocou uma pela outra, o custo de corrigir
 * vira um clique em vez de uma resposta errada sem saída.
 */
export function atalhoDeAmbiguidadeDeMargem(
  intencao: string | null,
  parametrosUsados: Record<string, string>,
): AtalhoAmbiguidadeMargem | null {
  if (intencao !== "MARGEM_DO_PERIODO" && intencao !== "LACUNAS_DA_MARGEM") {
    return null;
  }

  const canal = parametrosUsados.canal;
  const trechoCanal = canal ? ` do canal ${canal}` : "";
  const periodoRelativo = parametrosUsados.periodoRelativo;
  const trechoPeriodo = periodoRelativo && TEXTO_PERIODO_RELATIVO[periodoRelativo]
    ? ` ${TEXTO_PERIODO_RELATIVO[periodoRelativo]}`
    : "";

  if (intencao === "MARGEM_DO_PERIODO") {
    return {
      rotulo: "Ver o que falta para esse número ficar confiável",
      perguntaSugerida: `O que está faltando para calcular a margem${trechoCanal}${trechoPeriodo} com confiança?`,
    };
  }

  return {
    rotulo: "Ver o resultado do período",
    perguntaSugerida: `Quanto sobrou${trechoCanal}${trechoPeriodo}?`,
  };
}
