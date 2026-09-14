import { test } from "node:test";
import assert from "node:assert/strict";

import {
  LIMITE_CARACTERES_PERGUNTA,
  atalhoDeAmbiguidadeDeMargem,
  contarCaracteres,
  estadoVisualParaTipo,
  limitarTexto,
} from "./pergunta.ts";

test("RESPOSTA mapeia para o estado visual 'resposta'", () => {
  assert.equal(estadoVisualParaTipo("RESPOSTA"), "resposta");
});

test("ESCLARECIMENTO mapeia para o estado visual 'esclarecimento'", () => {
  assert.equal(estadoVisualParaTipo("ESCLARECIMENTO"), "esclarecimento");
});

test("RECUSA mapeia para o estado visual 'recusa'", () => {
  assert.equal(estadoVisualParaTipo("RECUSA"), "recusa");
});

test("contagem de caracteres abaixo do limite não excede", () => {
  const texto = "Quanto sobrou no mês passado?";
  const contagem = contarCaracteres(texto);
  assert.equal(contagem.usados, texto.length);
  assert.equal(contagem.restantes, LIMITE_CARACTERES_PERGUNTA - texto.length);
  assert.equal(contagem.excedeu, false);
});

test("contagem de caracteres exatamente no limite não excede", () => {
  const texto = "a".repeat(LIMITE_CARACTERES_PERGUNTA);
  const contagem = contarCaracteres(texto);
  assert.equal(contagem.restantes, 0);
  assert.equal(contagem.excedeu, false);
});

test("contagem de caracteres acima do limite marca excedeu", () => {
  const texto = "a".repeat(LIMITE_CARACTERES_PERGUNTA + 1);
  const contagem = contarCaracteres(texto);
  assert.equal(contagem.restantes, -1);
  assert.equal(contagem.excedeu, true);
});

test("contagem de caracteres aceita um limite customizado", () => {
  const contagem = contarCaracteres("abcdef", 5);
  assert.equal(contagem.usados, 6);
  assert.equal(contagem.excedeu, true);
});

test("limitarTexto não corta texto dentro do limite", () => {
  assert.equal(limitarTexto("pergunta curta"), "pergunta curta");
});

test("limitarTexto corta texto acima do limite", () => {
  const texto = "a".repeat(510);
  const resultado = limitarTexto(texto);
  assert.equal(resultado.length, LIMITE_CARACTERES_PERGUNTA);
});

test("atalho de ambiguidade não existe para intenção fora de margem", () => {
  assert.equal(atalhoDeAmbiguidadeDeMargem("GARGALOS_DA_OPERACAO", {}), null);
});

test("atalho de ambiguidade não existe quando não há intenção (RECUSA)", () => {
  assert.equal(atalhoDeAmbiguidadeDeMargem(null, {}), null);
});

test("MARGEM_DO_PERIODO oferece atalho para LACUNAS_DA_MARGEM, reaproveitando canal e período", () => {
  const atalho = atalhoDeAmbiguidadeDeMargem("MARGEM_DO_PERIODO", {
    canal: "Mercado Livre",
    periodoInicio: "2026-08-01T00:00:00-03:00",
    periodoFim: "2026-09-01T00:00:00-03:00",
    periodoRelativo: "MES_PASSADO",
  });
  assert.ok(atalho);
  assert.equal(atalho.rotulo, "Ver o que falta para esse número ficar confiável");
  assert.match(atalho.perguntaSugerida, /faltando/);
  assert.match(atalho.perguntaSugerida, /Mercado Livre/);
  assert.match(atalho.perguntaSugerida, /mês passado/);
});

test("LACUNAS_DA_MARGEM oferece atalho para MARGEM_DO_PERIODO", () => {
  const atalho = atalhoDeAmbiguidadeDeMargem("LACUNAS_DA_MARGEM", { canal: "Shopee" });
  assert.ok(atalho);
  assert.equal(atalho.rotulo, "Ver o resultado do período");
  assert.match(atalho.perguntaSugerida, /Quanto sobrou/);
  assert.match(atalho.perguntaSugerida, /Shopee/);
});

test("atalho de ambiguidade funciona mesmo sem canal/período reconhecidos (ex.: veio de ESCLARECIMENTO)", () => {
  const atalho = atalhoDeAmbiguidadeDeMargem("MARGEM_DO_PERIODO", {});
  assert.ok(atalho);
  assert.equal(atalho.perguntaSugerida, "O que está faltando para calcular a margem com confiança?");
});

test("atalho de ambiguidade ignora periodoRelativo desconhecido em vez de inventar texto", () => {
  const atalho = atalhoDeAmbiguidadeDeMargem("MARGEM_DO_PERIODO", { periodoRelativo: "SEMANA_QUE_VEM" });
  assert.ok(atalho);
  assert.equal(atalho.perguntaSugerida, "O que está faltando para calcular a margem com confiança?");
});
