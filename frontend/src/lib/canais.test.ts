import { test } from "node:test";
import assert from "node:assert/strict";

import { candidatosParaEspelho, nomeDoCanalPorId, textoDoEstadoDeEscopo } from "./canais.ts";
import type { RespostaCanal } from "./api/tipos.ts";

function canal(sobrescritas: Partial<RespostaCanal> & { id: string; nome: string }): RespostaCanal {
  return {
    id: sobrescritas.id,
    codigo: sobrescritas.codigo ?? sobrescritas.id,
    nome: sobrescritas.nome,
    tipo: sobrescritas.tipo ?? "MERCADO_LIVRE",
    categoria: sobrescritas.categoria ?? "MARKETPLACE",
    ativo: sobrescritas.ativo ?? true,
    escopoDeclarado: sobrescritas.escopoDeclarado ?? "NAO_DECLARADO",
    espelhaCanalId: sobrescritas.espelhaCanalId ?? null,
    escopoDeclaradoEm: sobrescritas.escopoDeclaradoEm ?? null,
  };
}

test("candidatosParaEspelho exclui o próprio canal", () => {
  const ml = canal({ id: "1", nome: "ML Clássico" });
  const bling = canal({ id: "2", nome: "Bling" });
  const candidatos = candidatosParaEspelho([ml, bling], "1");
  assert.deepEqual(candidatos.map((c) => c.id), ["2"]);
});

test("candidatosParaEspelho exclui canais que já são ESPELHO de outro (evita espelho de espelho)", () => {
  const ml = canal({ id: "1", nome: "ML Clássico" });
  const bling = canal({ id: "2", nome: "Bling", escopoDeclarado: "ESPELHO", espelhaCanalId: "1" });
  const shopee = canal({ id: "3", nome: "Shopee", escopoDeclarado: "FONTE_PRIMARIA" });

  const candidatos = candidatosParaEspelho([ml, bling, shopee], "4");

  assert.deepEqual(
    candidatos.map((c) => c.id).sort(),
    ["1", "3"],
  );
});

test("candidatosParaEspelho não exclui NAO_DECLARADO nem FONTE_PRIMARIA", () => {
  const naoDeclarado = canal({ id: "1", nome: "Canal novo" });
  const primaria = canal({ id: "2", nome: "ML Clássico", escopoDeclarado: "FONTE_PRIMARIA" });
  const candidatos = candidatosParaEspelho([naoDeclarado, primaria], "3");
  assert.equal(candidatos.length, 2);
});

test("nomeDoCanalPorId resolve o UUID para o nome", () => {
  const bling = canal({ id: "2", nome: "Bling" });
  assert.equal(nomeDoCanalPorId([bling], "2"), "Bling");
});

test("nomeDoCanalPorId nunca devolve o UUID cru quando o canal não está na lista", () => {
  const bling = canal({ id: "2", nome: "Bling" });
  const nome = nomeDoCanalPorId([bling], "id-que-nao-existe-mais");
  assert.notEqual(nome, "id-que-nao-existe-mais");
  assert.equal(nome, "canal não encontrado nesta lista");
});

test("textoDoEstadoDeEscopo de NAO_DECLARADO explica o bloqueio", () => {
  const canalNaoDeclarado = canal({ id: "1", nome: "Canal novo" });
  const estado = textoDoEstadoDeEscopo(canalNaoDeclarado, [canalNaoDeclarado]);
  assert.equal(estado.rotulo, "Não declarado");
  assert.match(estado.detalhe, /Bloqueia a soma/);
});

test("textoDoEstadoDeEscopo de FONTE_PRIMARIA não menciona nenhum outro canal", () => {
  const primaria = canal({ id: "1", nome: "ML Clássico", escopoDeclarado: "FONTE_PRIMARIA" });
  const estado = textoDoEstadoDeEscopo(primaria, [primaria]);
  assert.equal(estado.rotulo, "Fonte primária");
  assert.match(estado.detalhe, /nascem aqui/);
});

test("textoDoEstadoDeEscopo de ESPELHO nomeia o canal de origem pelo NOME, nunca pelo UUID", () => {
  const ml = canal({ id: "1", nome: "ML Clássico", escopoDeclarado: "FONTE_PRIMARIA" });
  const bling = canal({ id: "2", nome: "Bling", escopoDeclarado: "ESPELHO", espelhaCanalId: "1" });

  const estado = textoDoEstadoDeEscopo(bling, [ml, bling]);

  assert.equal(estado.rotulo, "Espelho");
  assert.match(estado.detalhe, /ML Clássico/);
  assert.doesNotMatch(estado.detalhe, /^1$/);
});

test("textoDoEstadoDeEscopo de ESPELHO nunca sugere perda ou fusão de dado", () => {
  const ml = canal({ id: "1", nome: "ML Clássico", escopoDeclarado: "FONTE_PRIMARIA" });
  const bling = canal({ id: "2", nome: "Bling", escopoDeclarado: "ESPELHO", espelhaCanalId: "1" });

  const estado = textoDoEstadoDeEscopo(bling, [ml, bling]);

  assert.doesNotMatch(estado.detalhe, /apaga/i);
  assert.doesNotMatch(estado.detalhe, /funde|fundir/i);
});
