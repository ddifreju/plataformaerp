/**
 * Formatação de dinheiro e percentual — a ponta da regra 2 do CLAUDE.md
 * ("dinheiro nunca é float") e da decisão 0022 ("dinheiro nunca vira
 * `number` do JavaScript"). Toda função aqui recebe STRING decimal (o que
 * `parseJsonPreservandoNumeros` devolve, ver `lib/api/jsonSeguro.ts`) e
 * devolve STRING formatada. Nenhuma soma, nenhuma subtração — o motor de
 * margem já fechou a conta, aqui só troca a aparência.
 *
 * `parseFloat`/`Number` só aparecem para VALIDAR o formato do texto
 * (dígito por dígito, sem interpretar o valor) ou para decidir sinal
 * (negativo/positivo/zero) comparando strings — nunca para fazer conta.
 */

const PADRAO_DECIMAL = /^-?\d+(\.\d+)?$/;

function normalizarSinalETexto(valorDecimal: string): {
  negativo: boolean;
  parteInteira: string;
  parteDecimal: string;
} {
  if (!PADRAO_DECIMAL.test(valorDecimal.trim())) {
    throw new Error(`"${valorDecimal}" não é um decimal válido para formatação monetária.`);
  }
  const texto = valorDecimal.trim();
  const negativo = texto.startsWith("-");
  const semSinal = negativo ? texto.slice(1) : texto;
  const [parteInteira, parteDecimal = ""] = semSinal.split(".");
  return { negativo, parteInteira, parteDecimal };
}

function agruparMilhares(digitos: string): string {
  let resultado = "";
  for (let i = 0; i < digitos.length; i += 1) {
    const posicaoDaDireita = digitos.length - i;
    if (i > 0 && posicaoDaDireita % 3 === 0) {
      resultado += ".";
    }
    resultado += digitos[i];
  }
  return resultado;
}

function arredondarParaDuasCasas(parteInteira: string, parteDecimal: string): { inteira: string; decimal: string } {
  // O backend já entrega os totais com 2 casas na borda de saída
  // (Apresentacao.paraExibicao). Este arredondamento é só uma defesa para
  // um valor que chegue com mais casas (ex.: escala de armazenamento, 4
  // casas) — feito em texto, dígito a dígito, para não converter em
  // `number` no processo.
  const decimalCompleto = (parteDecimal + "00").slice(0, 3); // 2 casas + 1 de referência para arredondar
  const duasCasas = decimalCompleto.slice(0, 2);
  const casaDeArredondamento = decimalCompleto[2] ?? "0";

  if (casaDeArredondamento < "5") {
    return { inteira: parteInteira, decimal: duasCasas };
  }

  // Soma 1 na última casa decimal, propagando "vai um" em texto.
  const digitos = (parteInteira + duasCasas).split("");
  let i = digitos.length - 1;
  let carregar = true;
  while (i >= 0 && carregar) {
    if (digitos[i] === ".") {
      i -= 1;
      continue;
    }
    const proximo = Number(digitos[i]) + 1;
    if (proximo === 10) {
      digitos[i] = "0";
    } else {
      digitos[i] = String(proximo);
      carregar = false;
    }
    i -= 1;
  }
  const combinado = (carregar ? "1" : "") + digitos.join("");
  return {
    inteira: combinado.slice(0, combinado.length - 2),
    decimal: combinado.slice(combinado.length - 2),
  };
}

/**
 * "44.9600" ou "-44.96" → "44,96" — SEMPRE o valor absoluto, sem sinal.
 * Existe separado de `formatarDinheiro`/`formatarPercentual` para quem
 * precisar montar o próprio layout (ex.: sinal e "R$" em elementos HTML
 * diferentes, para estilizar cada um). Onde não houver esse motivo,
 * prefira `formatarDinheiro`/`formatarPercentual`.
 */
export function formatarNumeroDecimalAbsoluto(valorDecimal: string): string {
  const { parteInteira, parteDecimal } = normalizarSinalETexto(valorDecimal);
  const { inteira, decimal } = arredondarParaDuasCasas(parteInteira, parteDecimal);
  const inteiraSemZerosEsquerda = inteira.replace(/^0+(?=\d)/, "") || "0";
  return `${agruparMilhares(inteiraSemZerosEsquerda)},${decimal}`;
}

// Sinal de menos tipográfico (U+2212), não o hífen do teclado — a mesma
// distinção que o guia pede para tudo o mais: sinal explícito, nunca só
// a cor (seção 2.3 do guia de interface).
const SINAL_NEGATIVO = "−";

/** "44.9600" → "R$ 44,96"; "-44.9600" → "−R$ 44,96" (sinal explícito antes do número, seção 2.3 do guia). */
export function formatarDinheiro(valorDecimal: string): string {
  const prefixo = ehNegativo(valorDecimal) ? SINAL_NEGATIVO : "";
  return `${prefixo}R$ ${formatarNumeroDecimalAbsoluto(valorDecimal)}`;
}

/** "22.4900" (fração já convertida em ponto percentual pelo backend) → "22,49%"; negativo → "−22,49%". */
export function formatarPercentual(valorDecimal: string): string {
  const prefixo = ehNegativo(valorDecimal) ? SINAL_NEGATIVO : "";
  return `${prefixo}${formatarNumeroDecimalAbsoluto(valorDecimal)}%`;
}

/** true se o texto decimal representa um valor menor que zero — sem conversão para `number`. */
export function ehNegativo(valorDecimal: string): boolean {
  return valorDecimal.trim().startsWith("-");
}

/** true se o texto decimal representa exatamente zero (qualquer quantidade de casas/zeros). */
export function ehZero(valorDecimal: string): boolean {
  const { parteInteira, parteDecimal } = normalizarSinalETexto(valorDecimal);
  return /^0*$/.test(parteInteira) && /^0*$/.test(parteDecimal);
}
