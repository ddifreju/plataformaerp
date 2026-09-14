import type { RotuloTeto } from "@/lib/api/tipos";

/**
 * O selo dos três rótulos de confiança (decisão 0019, guia de interface
 * seção 3.2) — extraído de `RotuloConfianca` para poder ser usado sozinho
 * em telas que não têm o formato completo de `RespostaMargemPeriodo`
 * (ex.: a tela "Perguntar", tarefa 26: `RespostaPergunta.rotuloConfianca`
 * chega como rótulo solto, sem o `Lacuna[]`/`valorTeto` que
 * `RotuloConfianca` também precisa). Aqui é só o selo — a frase de cada
 * rótulo (COM_TETO, INDETERMINADA) já vem embutida em
 * `RespostaPergunta.texto` na origem, então não é repetida aqui para não
 * duplicar informação de duas fontes diferentes.
 */
export function BadgeConfianca({ rotulo }: { rotulo: RotuloTeto }) {
  if (rotulo === "CALCULADA") {
    return (
      <span className="inline-block rounded-full border border-valor-positivo px-3 py-1 text-sm font-medium text-valor-positivo">
        Calculada
      </span>
    );
  }

  if (rotulo === "COM_TETO") {
    return (
      <span className="inline-block rounded-full border border-dado-estimado px-3 py-1 text-sm font-medium text-dado-estimado">
        Com teto
      </span>
    );
  }

  return (
    <span className="inline-block rounded-full border border-dado-ausente px-3 py-1 text-sm font-medium text-dado-ausente">
      Indeterminada
    </span>
  );
}
