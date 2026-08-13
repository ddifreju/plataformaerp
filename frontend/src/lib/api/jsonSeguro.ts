/**
 * Parser de JSON que nunca passa um número monetário pelo tipo `number`
 * do JavaScript — nem de relance.
 *
 * O motivo de existir: `JSON.parse` nativo tokeniza todo literal numérico
 * como `number` (float64) ANTES de qualquer código nosso ver o valor —
 * mesmo um `reviver` recebe o número já convertido, tarde demais para
 * "nunca virar number" (decisão 0022, regra 2; CLAUDE.md, regra "dinheiro
 * nunca é float"). O backend expõe `BigDecimal` sem um serializador
 * dedicado (ver o relatório desta tarefa) — hoje ele sai como número JSON
 * puro, não como string. Este parser é a defesa do frontend nesse ponto:
 * todo literal numérico do payload vira `string`, preservando o texto
 * exatamente como veio na resposta, e cabe a quem consome decidir (via
 * `lib/dinheiro.ts`) como formatar. Contagens inteiras pequenas
 * (quantidade de pedidos, tentativas) são convertidas de volta para
 * `number` no ponto de uso, nunca aqui — porque ali não é dinheiro.
 */
export function parseJsonPreservandoNumeros(texto: string): unknown {
  let indice = 0;

  function erroDeSintaxe(mensagem: string): never {
    throw new SyntaxError(`JSON inválido (posição ${indice}): ${mensagem}`);
  }

  function pularEspacos(): void {
    while (indice < texto.length && /\s/.test(texto[indice])) {
      indice += 1;
    }
  }

  function esperarLiteral(literal: string): void {
    if (texto.startsWith(literal, indice)) {
      indice += literal.length;
      return;
    }
    erroDeSintaxe(`esperava "${literal}"`);
  }

  function parseValor(): unknown {
    pularEspacos();
    const caractere = texto[indice];
    if (caractere === "{") return parseObjeto();
    if (caractere === "[") return parseArray();
    if (caractere === '"') return parseTexto();
    if (caractere === "t") {
      esperarLiteral("true");
      return true;
    }
    if (caractere === "f") {
      esperarLiteral("false");
      return false;
    }
    if (caractere === "n") {
      esperarLiteral("null");
      return null;
    }
    if (caractere === "-" || (caractere >= "0" && caractere <= "9")) {
      return parseNumeroComoTexto();
    }
    erroDeSintaxe(`caractere inesperado "${caractere}"`);
  }

  function parseObjeto(): Record<string, unknown> {
    const resultado: Record<string, unknown> = {};
    indice += 1; // consome "{"
    pularEspacos();
    if (texto[indice] === "}") {
      indice += 1;
      return resultado;
    }
    for (;;) {
      pularEspacos();
      if (texto[indice] !== '"') erroDeSintaxe("esperava chave entre aspas");
      const chave = parseTexto();
      pularEspacos();
      if (texto[indice] !== ":") erroDeSintaxe('esperava ":"');
      indice += 1;
      resultado[chave] = parseValor();
      pularEspacos();
      if (texto[indice] === ",") {
        indice += 1;
        continue;
      }
      if (texto[indice] === "}") {
        indice += 1;
        return resultado;
      }
      erroDeSintaxe('esperava "," ou "}"');
    }
  }

  function parseArray(): unknown[] {
    const resultado: unknown[] = [];
    indice += 1; // consome "["
    pularEspacos();
    if (texto[indice] === "]") {
      indice += 1;
      return resultado;
    }
    for (;;) {
      resultado.push(parseValor());
      pularEspacos();
      if (texto[indice] === ",") {
        indice += 1;
        continue;
      }
      if (texto[indice] === "]") {
        indice += 1;
        return resultado;
      }
      erroDeSintaxe('esperava "," ou "]"');
    }
  }

  function parseTexto(): string {
    indice += 1; // consome a aspa de abertura
    let resultado = "";
    for (;;) {
      const caractere = texto[indice];
      if (caractere === undefined) erroDeSintaxe("string não fechada");
      if (caractere === '"') {
        indice += 1;
        return resultado;
      }
      if (caractere === "\\") {
        const proximo = texto[indice + 1];
        switch (proximo) {
          case '"':
            resultado += '"';
            break;
          case "\\":
            resultado += "\\";
            break;
          case "/":
            resultado += "/";
            break;
          case "b":
            resultado += "\b";
            break;
          case "f":
            resultado += "\f";
            break;
          case "n":
            resultado += "\n";
            break;
          case "r":
            resultado += "\r";
            break;
          case "t":
            resultado += "\t";
            break;
          case "u": {
            const hex = texto.slice(indice + 2, indice + 6);
            resultado += String.fromCharCode(parseInt(hex, 16));
            indice += 4;
            break;
          }
          default:
            erroDeSintaxe(`escape inválido "\\${proximo}"`);
        }
        indice += 2;
        continue;
      }
      resultado += caractere;
      indice += 1;
    }
  }

  /** Devolve o literal numérico exatamente como está no texto de origem, como `string`. */
  function parseNumeroComoTexto(): string {
    const inicio = indice;
    if (texto[indice] === "-") indice += 1;
    while (texto[indice] >= "0" && texto[indice] <= "9") indice += 1;
    if (texto[indice] === ".") {
      indice += 1;
      while (texto[indice] >= "0" && texto[indice] <= "9") indice += 1;
    }
    if (texto[indice] === "e" || texto[indice] === "E") {
      indice += 1;
      if (texto[indice] === "+" || texto[indice] === "-") indice += 1;
      while (texto[indice] >= "0" && texto[indice] <= "9") indice += 1;
    }
    if (indice === inicio) erroDeSintaxe("número mal formado");
    return texto.slice(inicio, indice);
  }

  const valor = parseValor();
  pularEspacos();
  if (indice !== texto.length) erroDeSintaxe("conteúdo além do JSON válido");
  return valor;
}
