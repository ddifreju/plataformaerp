import { test } from "node:test";
import assert from "node:assert/strict";

import {
  ehNegativo,
  ehZero,
  formatarDinheiro,
  formatarNumeroDecimalAbsoluto,
  formatarPercentual,
} from "./dinheiro.ts";

// O mesmo caractere U+2212 que dinheiro.ts usa como sinal negativo — o
// teste precisa do valor exato, não de um hífen "parecido".
const SINAL_NEGATIVO = "−";

test("arredondamento propaga o 'vai um' através do ponto decimal (99.995 -> 100,00)", () => {
  assert.equal(formatarNumeroDecimalAbsoluto("99.995"), "100,00");
  assert.equal(formatarDinheiro("99.995"), "R$ 100,00");
});

test("arredondamento propaga o 'vai um' por várias casas (10.999 -> 11,00)", () => {
  assert.equal(formatarNumeroDecimalAbsoluto("10.999"), "11,00");
});

test("arredondamento para baixo não altera a parte inteira (10.001 -> 10,00)", () => {
  assert.equal(formatarNumeroDecimalAbsoluto("10.001"), "10,00");
});

test("valor negativo usa o sinal de menos tipográfico (U+2212), nunca hífen", () => {
  const resultado = formatarDinheiro("-44.9600");
  assert.equal(resultado, `${SINAL_NEGATIVO}R$ 44,96`);
  assert.ok(resultado.startsWith(SINAL_NEGATIVO));
  assert.ok(!resultado.includes("-"), "não deve conter o hífen do teclado");
});

test("valor positivo não recebe nenhum prefixo de sinal", () => {
  assert.equal(formatarDinheiro("44.96"), "R$ 44,96");
});

test("zero é formatado sem sinal e com duas casas", () => {
  assert.equal(formatarDinheiro("0"), "R$ 0,00");
  assert.equal(formatarDinheiro("0.00"), "R$ 0,00");
});

test("ehZero reconhece zero com qualquer quantidade de casas decimais", () => {
  assert.equal(ehZero("0"), true);
  assert.equal(ehZero("0.00"), true);
  assert.equal(ehZero("0.0000"), true);
  assert.equal(ehZero("0.01"), false);
});

test("ehNegativo decide pelo sinal do texto, sem converter para number", () => {
  assert.equal(ehNegativo("-0.01"), true);
  assert.equal(ehNegativo("0.01"), false);
  assert.equal(ehNegativo("0"), false);
});

test("valor com mais de duas casas decimais é arredondado (escala de armazenamento, 4 casas)", () => {
  assert.equal(formatarNumeroDecimalAbsoluto("199.9000"), "199,90");
  assert.equal(formatarNumeroDecimalAbsoluto("199.9049"), "199,90");
  assert.equal(formatarNumeroDecimalAbsoluto("199.9050"), "199,91");
});

test("string decimal grande demais para 'number' sem perder precisão nem agrupamento de milhar", () => {
  // Number("12345678901234567890.12") perde precisão (vira 12345678901234567000);
  // o formatador nunca converte para number, então o texto sai intacto.
  assert.equal(
    formatarNumeroDecimalAbsoluto("12345678901234567890.12"),
    "12.345.678.901.234.567.890,12",
  );
});

test("formatarPercentual aplica o mesmo sinal explícito e o sufixo %", () => {
  assert.equal(formatarPercentual("22.4900"), "22,49%");
  assert.equal(formatarPercentual("-22.4900"), `${SINAL_NEGATIVO}22,49%`);
});

test("texto que não é um decimal válido lança erro, em vez de formatar algo inventado", () => {
  assert.throws(() => formatarDinheiro("abc"));
  assert.throws(() => formatarDinheiro(""));
});
