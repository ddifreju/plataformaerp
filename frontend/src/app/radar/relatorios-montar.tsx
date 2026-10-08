"use client";

// Montar relatório: a cliente escolhe a fonte (pedidos, produtos, estoque, compras, contas…), as
// colunas (já cruzadas com as outras áreas), filtra, agrupa e soma, salva o modelo e exporta.
// As linhas vêm do servidor (GET /api/radar/relatorios/fonte), já sem o que o cargo não vê;
// aqui só se filtra, agrupa e soma. Dinheiro é somado em centavos inteiros, nunca em float.

import { useEffect, useMemo, useState } from "react";
import { imprimir } from "./produtos-lote";
import { cents, centMoney, str, type Row } from "./ui";

export type Coluna = { id: string; rotulo: string; tipo: string };
export type Fonte = { nome: string; rotulo: string; usaPeriodo: boolean; colunas: Coluna[] };
export type Filtro = { coluna: string; op: string; valor: string };
export type Config = {
  fonte: string;
  colunas: string[];
  filtros: Filtro[];
  agrupar: string;
  ordenar: { coluna: string; desc: boolean } | null;
  dias: number | null;
  /** Dias à frente de hoje (contas a vencer). */
  adiante?: number;
  de?: string;
  ate?: string;
};

type Resposta = {
  colunas: Coluna[];
  linhas: Row[];
  cortado: boolean;
  limite: number;
  periodo: { de: string; ate: string };
};

const OPERADORES: [string, string, string[]][] = [
  ["contem", "contém", ["texto", "data"]],
  ["igual", "é igual a", ["texto", "data", "inteiro", "dinheiro", "percentual"]],
  ["diferente", "é diferente de", ["texto", "data", "inteiro", "dinheiro", "percentual"]],
  ["min", "a partir de", ["inteiro", "dinheiro", "percentual", "data"]],
  ["max", "até", ["inteiro", "dinheiro", "percentual", "data"]],
  ["vazio", "está vazio", ["texto", "data", "inteiro", "dinheiro", "percentual"]],
];
const PERIODOS: [number, string][] = [
  [7, "Últimos 7 dias"],
  [30, "Últimos 30 dias"],
  [90, "Últimos 90 dias"],
  [365, "Últimos 12 meses"],
];
// Margem de um grupo = soma do lucro ÷ soma da receita (média de margens daria número errado).
const RAZOES: Record<string, [string, string]> = { margem: ["resultado", "receita_bruta"] };
const MOSTRAR = 500;

const somavel = (tipo: string) => tipo === "inteiro" || tipo === "dinheiro";
const normal = (t: unknown) => str(t).normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
const dia = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
// Número para comparar e somar: dinheiro em centavos; percentual em décimos.
const numero = (tipo: string, v: unknown) =>
  v == null || v === ""
    ? null
    : tipo === "dinheiro"
      ? cents(v)
      : tipo === "percentual"
        ? Math.round(Number(v) * 10)
        : Number(v);
const entradaNumero = (tipo: string, t: string) => {
  const limpo = t.replace(/\./g, "").replace(",", ".");
  return tipo === "dinheiro"
    ? cents(limpo)
    : tipo === "percentual"
      ? Math.round(Number(limpo) * 10)
      : Number(limpo);
};

export function mostrar(tipo: string, v: unknown) {
  if (v == null || v === "") return "—";
  if (tipo === "dinheiro") return centMoney(cents(v));
  if (tipo === "percentual") return `${str(v).replace(".", ",")}%`;
  if (tipo === "inteiro") return Number(v).toLocaleString("pt-BR");
  if (tipo === "data" && /^\d{4}-\d{2}-\d{2}$/.test(str(v)))
    return str(v).split("-").reverse().join("/");
  return str(v);
}

export function configVazia(fonte: Fonte): Config {
  return {
    fonte: fonte.nome,
    colunas: fonte.colunas.slice(0, 6).map((c) => c.id),
    filtros: [],
    agrupar: "",
    ordenar: null,
    dias: 30,
  };
}

type Props = {
  catalogo: Fonte[];
  inicial: Config;
  titulo: string;
  salvar: (nome: string, config: Config) => Promise<boolean>;
};

export default function MontarRelatorio({ catalogo, inicial, titulo, salvar }: Props) {
  const [cfg, setCfg] = useState<Config>(inicial);
  // Cada resposta guarda de qual pedido veio (fonte + período): enquanto não chega a do pedido
  // atual, a tela mostra "Carregando…".
  const [resposta, setResposta] = useState<{ chave: string; dados?: Resposta; erro?: string }>();
  const [nomeSalvar, setNomeSalvar] = useState<string | null>(null);
  const fonte = catalogo.find((f) => f.nome === cfg.fonte) ?? catalogo[0];
  const colunaDe = (id: string) => fonte?.colunas.find((c) => c.id === id);

  const de = cfg.dias ? dia(cfg.dias - 1) : (cfg.de ?? dia(29));
  const ate = cfg.dias ? dia(-(cfg.adiante ?? 0)) : (cfg.ate ?? dia(0));
  const chave = `${cfg.fonte}|${de}|${ate}`;

  // Fonte e período mudam os dados do servidor; colunas, filtros e agrupamento só a tela.
  useEffect(() => {
    let vivo = true;
    const consulta = new URLSearchParams({ nome: cfg.fonte, de, ate });
    fetch(`/api/radar/relatorios/fonte?${consulta}`, {
      credentials: "include",
    })
      .then(async (res) => {
        const corpo = await res.json();
        if (!res.ok) throw new Error(corpo.mensagem ?? "Não foi possível gerar o relatório.");
        if (vivo) setResposta({ chave, dados: corpo });
      })
      .catch((e) => vivo && setResposta({ chave, erro: (e as Error).message }));
    return () => {
      vivo = false;
    };
  }, [chave, cfg.fonte, de, ate]);
  const carregando = resposta?.chave !== chave;
  const dados = carregando ? null : (resposta?.dados ?? null);
  const erro = carregando ? "" : (resposta?.erro ?? "");

  const mudar = (parcial: Partial<Config>) => setCfg((c) => ({ ...c, ...parcial }));

  // Colunas que existem para este cargo (um modelo salvo pode citar coluna que ele não vê).
  const visiveis = cfg.colunas.filter((id) => colunaDe(id));

  const filtradas = useMemo(() => {
    if (!dados) return [];
    return dados.linhas.filter((l) =>
      cfg.filtros.every((f) => {
        const col = colunaDe(f.coluna);
        if (!col) return true;
        const v = l[f.coluna];
        if (f.op === "vazio") return v == null || v === "";
        if (f.valor === "") return true;
        if (f.op === "contem") return normal(v).includes(normal(f.valor));
        if (col.tipo === "texto" || (col.tipo === "data" && !["min", "max"].includes(f.op))) {
          // "Cancelado, Devolvido" vale um ou outro.
          const igual = f.valor.split(",").some((x) => normal(v) === normal(x.trim()));
          return f.op === "igual" ? igual : !igual;
        }
        if (col.tipo === "data") {
          const alvo = f.valor.includes("/") ? f.valor.split("/").reverse().join("-") : f.valor;
          return f.op === "min" ? str(v) >= alvo : str(v) <= alvo;
        }
        const n = numero(col.tipo, v);
        const alvo = entradaNumero(col.tipo, f.valor);
        if (n == null || Number.isNaN(alvo)) return false;
        if (f.op === "igual") return n === alvo;
        if (f.op === "diferente") return n !== alvo;
        return f.op === "min" ? n >= alvo : n <= alvo;
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps -- colunaDe depende só de fonte
  }, [dados, cfg.filtros, fonte]);

  // Agrupado: uma linha por valor da coluna escolhida, somando inteiros e dinheiro.
  const { cabecalho, linhas } = useMemo(() => {
    const cols = visiveis.map((id) => colunaDe(id)!);
    if (!cfg.agrupar || !colunaDe(cfg.agrupar)) return { cabecalho: cols, linhas: filtradas };
    const grupo = colunaDe(cfg.agrupar)!;
    const somas = cols.filter(
      (c) => c.id !== grupo.id && (somavel(c.tipo) || (RAZOES[c.id] && colunaDe(RAZOES[c.id][0]))),
    );
    const mapa = new Map<string, Row>();
    for (const l of filtradas) {
      const chave = str(l[grupo.id]) || "(vazio)";
      const g = mapa.get(chave) ?? { [grupo.id]: chave, _linhas: 0 };
      g._linhas = Number(g._linhas) + 1;
      for (const c of somas.filter((x) => somavel(x.tipo)))
        g[c.id] = Number(g[c.id] ?? 0) + (numero(c.tipo, l[c.id]) ?? 0);
      for (const [id, [a, b]] of Object.entries(RAZOES)) {
        g[`_${id}_a`] = Number(g[`_${id}_a`] ?? 0) + (cents(l[a]) || 0);
        g[`_${id}_b`] = Number(g[`_${id}_b`] ?? 0) + (cents(l[b]) || 0);
      }
      mapa.set(chave, g);
    }
    const saida = [...mapa.values()].map((g) => {
      const r: Row = { [grupo.id]: g[grupo.id], _linhas: g._linhas };
      for (const c of somas) {
        if (RAZOES[c.id]) {
          const b = Number(g[`_${c.id}_b`]);
          r[c.id] = b ? ((Number(g[`_${c.id}_a`]) * 100) / b).toFixed(1) : "";
        } else r[c.id] = c.tipo === "dinheiro" ? (Number(g[c.id]) / 100).toFixed(2) : g[c.id];
      }
      return r;
    });
    return {
      cabecalho: [grupo, ...somas, { id: "_linhas", rotulo: "Linhas", tipo: "inteiro" }],
      linhas: saida,
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- colunaDe depende só de fonte
  }, [filtradas, cfg.agrupar, cfg.colunas, fonte]);

  const ordenadas = useMemo(() => {
    if (!cfg.ordenar) return linhas;
    const col = cabecalho.find((c) => c.id === cfg.ordenar!.coluna);
    if (!col) return linhas;
    const f = cfg.ordenar.desc ? -1 : 1;
    return [...linhas].sort((a, b) => {
      const x =
        somavel(col.tipo) || col.tipo === "percentual"
          ? numero(col.tipo, a[col.id])
          : normal(a[col.id]);
      const y =
        somavel(col.tipo) || col.tipo === "percentual"
          ? numero(col.tipo, b[col.id])
          : normal(b[col.id]);
      if (x == null) return 1;
      if (y == null) return -1;
      return x < y ? -f : x > y ? f : 0;
    });
  }, [linhas, cabecalho, cfg.ordenar]);

  // Totais do que se soma (margem total = lucro ÷ receita de tudo o que está filtrado).
  const totais = useMemo(() => {
    const t: Row = {};
    for (const c of cabecalho) {
      if (RAZOES[c.id]) {
        const [a, b] = RAZOES[c.id];
        const sa = filtradas.reduce((s, l) => s + (cents(l[a]) || 0), 0);
        const sb = filtradas.reduce((s, l) => s + (cents(l[b]) || 0), 0);
        t[c.id] = sb ? ((sa * 100) / sb).toFixed(1) : "";
      } else if (somavel(c.tipo)) {
        const s = linhas.reduce((x, l) => x + (numero(c.tipo, l[c.id]) ?? 0), 0);
        t[c.id] = c.tipo === "dinheiro" ? (s / 100).toFixed(2) : s;
      }
    }
    return t;
  }, [cabecalho, linhas, filtradas]);

  function exportarCsv() {
    // Texto que começa com = + - @ vira fórmula no Excel (ex.: mensagem de cliente): leva um '.
    const escapar = (v: unknown, texto = false) => {
      const t = str(v);
      return `"${(texto && /^[=+\-@\t\r]/.test(t) ? `'${t}` : t).replace(/"/g, '""')}"`;
    };
    const corpo = [
      cabecalho.map((c) => escapar(c.rotulo)).join(";"),
      ...ordenadas.map((l) =>
        cabecalho
          .map((c) =>
            escapar(
              c.tipo === "dinheiro" || c.tipo === "percentual"
                ? str(l[c.id]).replace(".", ",")
                : c.tipo === "data"
                  ? mostrar("data", l[c.id])
                  : l[c.id],
              c.tipo === "texto",
            ),
          )
          .join(";"),
      ),
    ].join("\n");
    const url = URL.createObjectURL(new Blob(["﻿" + corpo], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `${titulo.replace(/[^\p{L}\p{N}]+/gu, "-").toLowerCase()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  function exportarPdf() {
    const esc = (v: string) => v.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    const th = cabecalho.map((c) => `<th>${esc(c.rotulo)}</th>`).join("");
    const tr = ordenadas
      .map(
        (l) =>
          `<tr>${cabecalho
            .map(
              (c) =>
                `<td class="${somavel(c.tipo) || c.tipo === "percentual" ? "n" : ""}">${esc(mostrar(c.tipo, l[c.id]))}</td>`,
            )
            .join("")}</tr>`,
      )
      .join("");
    const tot = `<tr class="t">${cabecalho
      .map((c, i) => `<td class="n">${i === 0 ? "Total" : t(c)}</td>`)
      .join("")}</tr>`;
    function t(c: Coluna) {
      return c.id in totais ? esc(mostrar(c.tipo, totais[c.id])) : "";
    }
    imprimir(
      titulo,
      `<h2>${esc(titulo)}</h2><p>${esc(periodoTexto())} · ${ordenadas.length} linha(s)</p><table><thead><tr>${th}</tr></thead><tbody>${tr}${tot}</tbody></table>`,
      "table{width:100%;border-collapse:collapse;font-size:11px}th,td{border-bottom:1px solid #ddd;padding:4px 6px;text-align:left}td.n{text-align:right}th{background:#f3f4f6}tr.t td{font-weight:700}",
    );
  }

  function periodoTexto() {
    if (!dados || !fonte?.usaPeriodo) return "Sem período";
    return `${mostrar("data", dados.periodo.de)} a ${mostrar("data", dados.periodo.ate)}`;
  }

  if (!fonte) return <p className="rd-note">Seu cargo não tem relatórios disponíveis.</p>;
  const agrupaveis = fonte.colunas.filter((c) => c.tipo === "texto" || c.tipo === "data");
  return (
    <section className="rd-card rd-montar">
      <div className="rd-card-head">
        <h2>{titulo}</h2>
        <div className="rd-actions">
          <button onClick={exportarCsv} disabled={!ordenadas.length}>
            ⇩ Excel (CSV)
          </button>
          <button onClick={exportarPdf} disabled={!ordenadas.length}>
            🖨 PDF
          </button>
          <button className="primary" onClick={() => setNomeSalvar(titulo)}>
            Salvar relatório
          </button>
        </div>
      </div>
      {nomeSalvar != null && (
        <form
          className="rd-montar-salvar"
          onSubmit={async (e) => {
            e.preventDefault();
            if (await salvar(nomeSalvar.trim(), cfg)) setNomeSalvar(null);
          }}
        >
          <label>
            Nome do relatório
            <input
              autoFocus
              maxLength={80}
              value={nomeSalvar}
              onChange={(e) => setNomeSalvar(e.target.value)}
            />
          </label>
          <button className="primary" disabled={!nomeSalvar.trim()}>
            Salvar em Meus relatórios
          </button>
          <button type="button" onClick={() => setNomeSalvar(null)}>
            Cancelar
          </button>
        </form>
      )}

      <div className="rd-montar-barra">
        <label>
          Dados de
          <select
            value={cfg.fonte}
            onChange={(e) => {
              const nova = catalogo.find((f) => f.nome === e.target.value)!;
              setCfg({ ...configVazia(nova), dias: cfg.dias, de: cfg.de, ate: cfg.ate });
            }}
          >
            {catalogo.map((f) => (
              <option key={f.nome} value={f.nome}>
                {f.rotulo}
              </option>
            ))}
          </select>
        </label>
        {fonte.usaPeriodo && (
          <label>
            Período
            <select
              value={cfg.dias ?? "personalizado"}
              onChange={(e) =>
                e.target.value === "personalizado"
                  ? mudar({ dias: null, de: dia(29), ate: dia(0) })
                  : mudar({ dias: Number(e.target.value) })
              }
            >
              {PERIODOS.map(([d, r]) => (
                <option key={d} value={d}>
                  {r}
                </option>
              ))}
              <option value="personalizado">Escolher datas</option>
            </select>
          </label>
        )}
        {fonte.nome === "contas" && cfg.dias != null && (
          <label>
            E os próximos
            <select
              value={cfg.adiante ?? 0}
              onChange={(e) => mudar({ adiante: Number(e.target.value) })}
            >
              {/* O período inteiro vai até um ano. */}
              {[0, 30, 60, 90]
                .filter((d) => (cfg.dias ?? 0) + d <= 366)
                .map((d) => (
                  <option key={d} value={d}>
                    {d ? `${d} dias` : "nenhum dia"}
                  </option>
                ))}
            </select>
          </label>
        )}
        {fonte.usaPeriodo && cfg.dias == null && (
          <>
            <label>
              De
              <input
                type="date"
                value={cfg.de ?? ""}
                onChange={(e) => mudar({ de: e.target.value })}
              />
            </label>
            <label>
              Até
              <input
                type="date"
                value={cfg.ate ?? ""}
                onChange={(e) => mudar({ ate: e.target.value })}
              />
            </label>
          </>
        )}
        <label>
          Agrupar por
          <select value={cfg.agrupar} onChange={(e) => mudar({ agrupar: e.target.value })}>
            <option value="">Não agrupar (uma linha por registro)</option>
            {agrupaveis.map((c) => (
              <option key={c.id} value={c.id}>
                {c.rotulo}
              </option>
            ))}
          </select>
        </label>
      </div>

      <details className="rd-montar-colunas" open={!inicial.colunas.length}>
        <summary>
          Colunas ({visiveis.length} de {fonte.colunas.length})
        </summary>
        <div>
          {fonte.colunas.map((c) => (
            <label key={c.id} className={cfg.colunas.includes(c.id) ? "ativo" : ""}>
              <input
                type="checkbox"
                checked={cfg.colunas.includes(c.id)}
                onChange={(e) =>
                  mudar({
                    colunas: e.target.checked
                      ? fonte.colunas
                          .map((x) => x.id)
                          .filter((id) => id === c.id || cfg.colunas.includes(id))
                      : cfg.colunas.filter((id) => id !== c.id),
                  })
                }
              />
              {c.rotulo}
            </label>
          ))}
        </div>
      </details>

      <div className="rd-montar-filtros">
        {cfg.filtros.map((f, i) => {
          const col = colunaDe(f.coluna);
          const muda = (p: Partial<Filtro>) =>
            mudar({ filtros: cfg.filtros.map((x, j) => (j === i ? { ...x, ...p } : x)) });
          return (
            <div key={i}>
              <select
                aria-label="Coluna do filtro"
                value={f.coluna}
                onChange={(e) => muda({ coluna: e.target.value, op: "contem", valor: "" })}
              >
                {fonte.colunas.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.rotulo}
                  </option>
                ))}
              </select>
              <select
                aria-label="Condição do filtro"
                value={f.op}
                onChange={(e) => muda({ op: e.target.value })}
              >
                {OPERADORES.filter(([, , tipos]) => !col || tipos.includes(col.tipo)).map(
                  ([op, r]) => (
                    <option key={op} value={op}>
                      {r}
                    </option>
                  ),
                )}
              </select>
              {f.op !== "vazio" && (
                <input
                  aria-label="Valor do filtro"
                  value={f.valor}
                  placeholder={col?.tipo === "data" ? "aaaa-mm-dd" : "valor"}
                  onChange={(e) => muda({ valor: e.target.value })}
                />
              )}
              <button
                type="button"
                aria-label="Tirar filtro"
                onClick={() => mudar({ filtros: cfg.filtros.filter((_, j) => j !== i) })}
              >
                ×
              </button>
            </div>
          );
        })}
        <button
          type="button"
          className="text"
          onClick={() =>
            mudar({
              filtros: [...cfg.filtros, { coluna: fonte.colunas[0].id, op: "contem", valor: "" }],
            })
          }
        >
          + Filtro
        </button>
      </div>

      {erro && (
        <p className="rd-error" role="alert">
          {erro}
        </p>
      )}
      {dados?.cortado && (
        <p className="rd-error" role="alert">
          O período tem mais de {dados.limite.toLocaleString("pt-BR")} registros: o relatório mostra
          só os {dados.limite.toLocaleString("pt-BR")} mais recentes. Escolha um período menor para
          ver tudo.
        </p>
      )}
      <p className="rd-dica">
        {carregando
          ? "Carregando…"
          : `${periodoTexto()} · ${ordenadas.length.toLocaleString("pt-BR")} linha(s)${
              cfg.agrupar ? ` (de ${filtradas.length.toLocaleString("pt-BR")} registros)` : ""
            }`}
        {cfg.agrupar && " · ao agrupar, ficam a coluna do grupo e as que se somam"}
      </p>
      <div className="rd-table-wrap">
        <table className="rd-table">
          <thead>
            <tr>
              {cabecalho.map((c) => (
                <th key={c.id}>
                  <button
                    type="button"
                    className="rd-montar-ordenar"
                    onClick={() =>
                      mudar({
                        ordenar: {
                          coluna: c.id,
                          desc: cfg.ordenar?.coluna === c.id ? !cfg.ordenar.desc : somavel(c.tipo),
                        },
                      })
                    }
                  >
                    {c.rotulo}
                    {cfg.ordenar?.coluna === c.id && (cfg.ordenar.desc ? " ↓" : " ↑")}
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ordenadas.slice(0, MOSTRAR).map((l, i) => (
              <tr key={i}>
                {cabecalho.map((c) => (
                  <td key={c.id} className={somavel(c.tipo) || c.tipo === "percentual" ? "n" : ""}>
                    {mostrar(c.tipo, l[c.id])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
          {ordenadas.length > 0 && (
            <tfoot>
              <tr>
                {cabecalho.map((c, i) => (
                  <td key={c.id} className="n">
                    {i === 0 ? "Total" : c.id in totais ? mostrar(c.tipo, totais[c.id]) : ""}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
      {ordenadas.length > MOSTRAR && (
        <p className="rd-dica">
          Mostrando {MOSTRAR} de {ordenadas.length.toLocaleString("pt-BR")} linhas. A exportação
          leva todas.
        </p>
      )}
    </section>
  );
}
