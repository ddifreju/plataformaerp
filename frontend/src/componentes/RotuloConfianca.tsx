import type { Lacuna, RotuloTeto } from "@/lib/api/tipos";
import { formatarDinheiro, formatarPercentual } from "@/lib/dinheiro";
import { BadgeConfianca } from "./BadgeConfianca";
import { ListaLacunas } from "./ListaLacunas";

interface PropriedadesRotuloConfianca {
  rotulo: RotuloTeto;
  lacunas: Lacuna[];
  /** Valor citado no texto de COM_TETO — usamos N2 (Margem por pedido/período), como no exemplo do guia. */
  valorTeto: string;
  percentualTeto: string | null;
}

/**
 * Os três rótulos da "regra do teto" (decisão 0019, guia seção 3.2).
 * Texto copiado literalmente do guia, com um único ajuste marcado no
 * relatório da tarefa: a frase de CALCULADA fala de "pedido" no guia —
 * aqui trocado por "período" porque esta tela agrega um período inteiro,
 * não um pedido isolado.
 */
export function RotuloConfianca({ rotulo, lacunas, valorTeto, percentualTeto }: PropriedadesRotuloConfianca) {
  if (rotulo === "CALCULADA") {
    return (
      <div className="rounded border border-borda bg-background p-4">
        <BadgeConfianca rotulo={rotulo} />
        <p className="mt-2 text-base italic text-texto-secundario">Nenhum dado faltando neste período.</p>
      </div>
    );
  }

  if (rotulo === "COM_TETO") {
    const percentualTexto = percentualTeto ? ` (${formatarPercentual(percentualTeto)})` : "";
    return (
      <div className="rounded border border-dado-estimado bg-dado-estimado-bg p-4">
        <BadgeConfianca rotulo={rotulo} />
        <p className="mt-2 text-base font-medium text-foreground">
          {formatarDinheiro(valorTeto)}
          {percentualTexto} é o teto — a margem real é menor.
        </p>
        <p className="mt-2 text-sm text-texto-secundario">Faltam:</p>
        <ListaLacunas lacunas={lacunas} />
      </div>
    );
  }

  return (
    <div className="rounded border border-dado-ausente bg-dado-ausente-bg p-4">
      <BadgeConfianca rotulo={rotulo} />
      <p className="mt-2 text-base font-medium text-foreground">Não dá para calcular esta margem com confiança.</p>
      <p className="mt-2 text-sm text-texto-secundario">
        Faltam (abaixo). Alguns desses fazem o número subir, outros descer — por isso não existe um teto seguro
        para mostrar aqui.
      </p>
      <ListaLacunas lacunas={lacunas} />
    </div>
  );
}
