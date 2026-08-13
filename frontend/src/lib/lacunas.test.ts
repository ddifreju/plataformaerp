import { test } from "node:test";
import assert from "node:assert/strict";

import { acaoParaLacuna } from "./lacunas.ts";
import type { Lacuna } from "./api/tipos.ts";

function lacuna(codigo: string): Lacuna {
  return { codigo, descricao: "descrição qualquer vinda do backend", direcaoVies: "INDETERMINADA" };
}

test("código conhecido (custo de mercadoria) casa a ação de cadastrar custo", () => {
  const acao = acaoParaLacuna(lacuna("custo_mercadoria_nao_cadastrado"));
  assert.equal(acao.rotulo, "Cadastrar custo");
});

test("código conhecido (item sem variação) casa a mesma ação de cadastrar custo", () => {
  const acao = acaoParaLacuna(lacuna("item_sem_variacao"));
  assert.equal(acao.rotulo, "Cadastrar custo");
});

test("taxa de antecipação (startsWith + includes) casa a ação específica de antecipação", () => {
  const acao = acaoParaLacuna(lacuna("taxa_canal_nao_cadastrada_ANTECIPACAO_MERCADO_LIVRE"));
  assert.equal(acao.rotulo, "Configurar antecipação");
  assert.match(acao.texto, /antecipa recebíveis/);
});

test("taxa de canal comum (sem ANTECIPACAO) casa a ação genérica de cadastrar taxa", () => {
  const acao = acaoParaLacuna(lacuna("taxa_canal_nao_cadastrada_COMISSAO"));
  assert.equal(acao.rotulo, "Cadastrar taxa");
});

test("regime tributário não configurado casa a ação de configurar regime", () => {
  const acao = acaoParaLacuna(lacuna("regime_tributario_nao_configurado"));
  assert.equal(acao.rotulo, "Configurar regime");
});

test("canais sobrepostos casa a ação de 'nenhuma ação necessária'", () => {
  const acao = acaoParaLacuna(lacuna("canais_sobrepostos"));
  assert.equal(acao.rotulo, "Nenhuma ação necessária aqui");
});

test("repasse divergente e repasse ausente casam a mesma ação de revisar repasse", () => {
  assert.equal(acaoParaLacuna(lacuna("repasse_diverge_quantificavel")).rotulo, "Revisar repasse");
  assert.equal(acaoParaLacuna(lacuna("repasse_previsto_ausente")).rotulo, "Revisar repasse");
});

test("código desconhecido cai no padrão geral, sem quebrar e sem inventar rótulo específico", () => {
  const acao = acaoParaLacuna(lacuna("codigo_que_o_catalogo_ainda_nao_tem"));
  assert.equal(acao.rotulo, "Sem ação disponível");
  assert.equal(acao.texto, "Revise esta pendência — ela ainda não tem um cadastro dedicado nesta tela.");
});
