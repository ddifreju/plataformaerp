import { ValorMonetario } from "./ValorMonetario";

interface PropriedadesCartaoNumero {
  rotulo: string;
  fraseDeApoio: string;
  valorDecimal: string;
  aplicarCorDeSinal?: boolean;
}

/**
 * Um dos quatro números (N0-N3, guia seção 3.1): rótulo visível ao lado
 * do valor, frase de apoio sempre presente — "se o espaço não comporta
 * rótulo + frase de apoio + valor, o valor não cabe naquele espaço".
 */
export function CartaoNumero({ rotulo, fraseDeApoio, valorDecimal, aplicarCorDeSinal = false }: PropriedadesCartaoNumero) {
  return (
    <div className="rounded border border-borda bg-background p-4">
      <h3 className="text-sm font-medium text-texto-secundario">{rotulo}</h3>
      <p className="mt-1 text-2xl font-medium">
        <ValorMonetario valorDecimal={valorDecimal} aplicarCorDeSinal={aplicarCorDeSinal} />
      </p>
      <p className="mt-1 text-sm text-texto-secundario">{fraseDeApoio}</p>
    </div>
  );
}
