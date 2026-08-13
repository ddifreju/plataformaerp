import type { Lacuna } from "@/lib/api/tipos";
import { acaoParaLacuna } from "@/lib/lacunas";

/**
 * Lacuna como pendência acionável (nunca `R$ 0,00`, nunca linha
 * omitida) — decisão 0022, guia seção 3.3: "frase 1 diz o que falta,
 * frase 2 diz o que fazer".
 */
export function ListaLacunas({ lacunas }: { lacunas: Lacuna[] }) {
  if (lacunas.length === 0) {
    return null;
  }

  return (
    <ul className="mt-2 flex flex-col gap-3">
      {lacunas.map((lacuna) => {
        const acao = acaoParaLacuna(lacuna);
        return (
          <li key={lacuna.codigo} className="border-l-2 border-dado-ausente bg-dado-ausente-bg p-3 text-sm">
            <p className="text-foreground">{lacuna.descricao}</p>
            <p className="mt-1 font-medium text-foreground">
              {acao.texto} <span className="font-normal text-texto-secundario">({acao.rotulo})</span>
            </p>
          </li>
        );
      })}
    </ul>
  );
}
