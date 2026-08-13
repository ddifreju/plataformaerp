import { ehNegativo, ehZero, formatarDinheiro } from "@/lib/dinheiro";

interface PropriedadesValorMonetario {
  /** String decimal, nunca `number` — ver decisão 0022. */
  valorDecimal: string;
  /**
   * true para número que veio direto do motor de margem (N0-N3): aplica
   * verde/vermelho por sinal. Falso para linha de bloco/decomposição —
   * guia, seção 2.2: as cores de sinal só valem para números medidos do
   * motor, não para toda soma da tela.
   */
  aplicarCorDeSinal?: boolean;
  /** true quando `eh_estimativa = true` na origem: prefixo "~", sublinhado tracejado, cor âmbar (seção 3.4 do guia). */
  estimado?: boolean;
  /** Texto do tooltip quando `estimado`. Sem isso, cai numa frase genérica. */
  origemEstimativa?: string;
  className?: string;
}

/**
 * Único ponto que decide a aparência de um valor monetário na tela:
 * sinal explícito (nunca só cor), marcação de estimativa (nunca só cor),
 * fonte tabular. Ver seções 2.3 e 3.4 do guia de interface.
 */
export function ValorMonetario({
  valorDecimal,
  aplicarCorDeSinal = false,
  estimado = false,
  origemEstimativa,
  className = "",
}: PropriedadesValorMonetario) {
  const textoFormatado = formatarDinheiro(valorDecimal);

  let corClasse = "";
  if (aplicarCorDeSinal && !ehZero(valorDecimal)) {
    corClasse = ehNegativo(valorDecimal) ? "text-valor-negativo" : "text-valor-positivo";
  }

  if (estimado) {
    return (
      <span
        className={`tabular-nums border-b border-dashed border-dado-estimado text-dado-estimado ${className}`}
        title={
          origemEstimativa ??
          "Estimado com a taxa cadastrada ou por rateio — o canal não informou o valor exato deste componente."
        }
      >
        ~{textoFormatado}
      </span>
    );
  }

  return <span className={`tabular-nums ${corClasse} ${className}`}>{textoFormatado}</span>;
}
