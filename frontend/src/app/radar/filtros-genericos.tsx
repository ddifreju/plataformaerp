"use client";

// Busca e filtros configuráveis, no mesmo padrão dos filtros de produtos e
// de anúncios: E entre campos, OU dentro de um campo com várias escolhas;
// sugestões na busca que viram filtro; chips removíveis; prévia de quantos
// aparecem; filtros salvos neste navegador. Cada página descreve seus campos
// numa configuração (ver contatos-filtros.tsx).

import { useEffect, useRef, useState } from "react";
import { gravar, ler, normal } from "./produtos-filtros";
import { cents, str, type Row } from "./ui";

export type Opcao = [string, string];

export type Campo =
  /** Várias escolhas; o registro passa se tiver qualquer uma delas. */
  | {
      tipo: "multi";
      chave: string;
      rotulo: string;
      opcoes: Opcao[];
      valor: (r: Row) => string | string[];
    }
  /** Texto livre, com sugestões dos valores que existem na lista. */
  | {
      tipo: "texto";
      chave: string;
      rotulo: string;
      placeholder?: string;
      valor: (r: Row) => unknown;
    }
  /** Faixa numérica (de / até). */
  | {
      tipo: "faixa";
      chave: string;
      rotulo: string;
      unidade: "R$" | "%" | "dias" | "";
      valor: (r: Row) => number | null;
    }
  /** Caixinha de marcar: passa quem atende à condição. */
  | { tipo: "marca"; chave: string; rotulo: string; teste: (r: Row) => boolean }
  /** Data: passa quem tem a data a partir do dia escolhido. */
  | { tipo: "data"; chave: string; rotulo: string; valor: (r: Row) => unknown }
  /** Medidas de um produto (cm): passa a embalagem em que ele cabe, em qualquer posição. */
  | {
      tipo: "cabe";
      chave: string;
      rotulo: string;
      valor: (r: Row) => [number, number, number] | null;
    }
  /** Período de datas (de / até, os dois dias inclusive). */
  | { tipo: "periodo"; chave: string; rotulo: string; valor: (r: Row) => unknown };

type Medidas = { c: string; l: string; a: string };
type Valor = string | string[] | boolean | { de: string; ate: string } | Medidas;

export type Filtro = { busca: string; ordem: string; v: Record<string, Valor> };

export type Atalho = { chaves: string[]; rotulo: string; aplicar: (f: Filtro) => Filtro };

export type Config = {
  /** Chave dos filtros salvos no navegador (uma por página). */
  salvos: string;
  plural: string;
  placeholder: string;
  busca: (r: Row) => unknown[];
  ordens: [string, string, (a: Row, b: Row) => number][];
  campos: Campo[];
  atalhos?: Atalho[];
};

export const filtroVazio = (cfg: Config): Filtro => ({
  busca: "",
  ordem: cfg.ordens[0][0],
  v: {},
});

const numero = (t: string, unidade: string) =>
  unidade === "R$"
    ? cents(t.replace(/\./g, "").replace(",", ".")) / 100
    : Number(t.replace(",", "."));

const ativo = (v: Valor | undefined) =>
  v !== undefined &&
  v !== false &&
  v !== "" &&
  !(Array.isArray(v) && v.length === 0) &&
  !(typeof v === "object" && !Array.isArray(v) && !Object.values(v).some(Boolean));

export function filtrar(lista: Row[], f: Filtro, cfg: Config): Row[] {
  const termo = normal(f.busca.trim());
  // Números com traço, ponto ou espaço (CEP, telefone, CPF, nota) batem com ou sem eles.
  const semSinais = (t: string) => t.replace(/[\s.\-/()]/g, "");
  const termoNumero = /\d/.test(termo) ? semSinais(termo) : "";
  const acha = (x: unknown) => {
    const v = normal(x);
    return v.includes(termo) || (!!termoNumero && semSinais(v).includes(termoNumero));
  };
  const passa = (r: Row) => {
    if (termo && !cfg.busca(r).some(acha)) return false;
    for (const c of cfg.campos) {
      const v = f.v[c.chave];
      if (!ativo(v)) continue;
      if (c.tipo === "multi") {
        const tem = c.valor(r);
        const lista = Array.isArray(tem) ? tem : [tem];
        if (!(v as string[]).some((x) => lista.includes(x))) return false;
      } else if (c.tipo === "texto") {
        if (!normal(c.valor(r)).includes(normal(v as string))) return false;
      } else if (c.tipo === "faixa") {
        const { de, ate } = v as { de: string; ate: string };
        const n = c.valor(r);
        if (n === null) return false;
        if (de && n < numero(de, c.unidade)) return false;
        if (ate && n > numero(ate, c.unidade)) return false;
      } else if (c.tipo === "marca") {
        if (!c.teste(r)) return false;
      } else if (c.tipo === "data") {
        const d = new Date(str(c.valor(r))).getTime();
        if (!d || d < new Date(`${v as string}T00:00:00`).getTime()) return false;
      } else if (c.tipo === "cabe") {
        const caixa = c.valor(r);
        if (!caixa) return false;
        const m = v as Medidas;
        // Ordena as duas pelo tamanho: o produto pode ser girado dentro da caixa.
        const produto = [m.c, m.l, m.a]
          .map((x) => Number(x.replace(",", ".")) || 0)
          .sort((a, b) => b - a);
        const dentro = [...caixa].sort((a, b) => b - a);
        if (produto.some((x, i) => x > dentro[i])) return false;
      } else if (c.tipo === "periodo") {
        const { de, ate } = v as { de: string; ate: string };
        const d = new Date(str(c.valor(r))).getTime();
        if (!d) return false;
        if (de && d < new Date(`${de}T00:00:00`).getTime()) return false;
        if (ate && d > new Date(`${ate}T23:59:59.999`).getTime()) return false;
      }
    }
    return true;
  };
  const ordem = cfg.ordens.find(([k]) => k === f.ordem) ?? cfg.ordens[0];
  return lista.filter(passa).sort(ordem[2]);
}

type Chip = { rotulo: string; tirar: (f: Filtro) => Filtro };

function chipsDe(f: Filtro, cfg: Config): Chip[] {
  const out: Chip[] = [];
  const sem = (f: Filtro, chave: string, v?: Valor): Filtro => ({
    ...f,
    v: { ...f.v, [chave]: v ?? "" },
  });
  if (f.busca.trim())
    out.push({ rotulo: `busca: "${f.busca.trim()}"`, tirar: (x) => ({ ...x, busca: "" }) });
  for (const c of cfg.campos) {
    const v = f.v[c.chave];
    if (!ativo(v)) continue;
    const nome = c.rotulo.toLowerCase();
    if (c.tipo === "multi")
      for (const k of v as string[])
        out.push({
          rotulo: `${nome}: ${(c.opcoes.find(([o]) => o === k)?.[1] ?? k).toLowerCase()}`,
          tirar: (x) =>
            sem(
              x,
              c.chave,
              ((x.v[c.chave] as string[]) ?? []).filter((y) => y !== k),
            ),
        });
    else if (c.tipo === "texto")
      out.push({ rotulo: `${nome}: ${v as string}`, tirar: (x) => sem(x, c.chave) });
    else if (c.tipo === "faixa") {
      const { de, ate } = v as { de: string; ate: string };
      const u = c.unidade === "R$" ? "R$ " : "";
      const s = c.unidade && c.unidade !== "R$" ? ` ${c.unidade}` : "";
      out.push({
        rotulo: `${nome}${de ? ` de ${u}${de}${s}` : ""}${ate ? ` até ${u}${ate}${s}` : ""}`,
        tirar: (x) => sem(x, c.chave, { de: "", ate: "" }),
      });
    } else if (c.tipo === "marca") out.push({ rotulo: nome, tirar: (x) => sem(x, c.chave, false) });
    else if (c.tipo === "data")
      out.push({
        rotulo: `${nome} ${(v as string).split("-").reverse().join("/")}`,
        tirar: (x) => sem(x, c.chave),
      });
    else if (c.tipo === "cabe") {
      const m = v as Medidas;
      out.push({
        rotulo: `${nome} ${[m.c, m.l, m.a].map((x) => x || "?").join(" × ")} cm`,
        tirar: (x) => sem(x, c.chave, { c: "", l: "", a: "" }),
      });
    } else if (c.tipo === "periodo") {
      const { de, ate } = v as { de: string; ate: string };
      const br = (d: string) => d.split("-").reverse().join("/");
      out.push({
        rotulo:
          de && de === ate
            ? `${nome}: ${br(de)}`
            : `${nome}${de ? ` de ${br(de)}` : ""}${ate ? ` até ${br(ate)}` : ""}`,
        tirar: (x) => sem(x, c.chave, { de: "", ate: "" }),
      });
    }
  }
  return out;
}

type Sugestao = { rotulo: string; aplicar: (f: Filtro) => Filtro };

/**
 * O texto digitado é comparado com as opções dos campos, com os valores que
 * existem na lista e com os atalhos da página. Clicar troca a busca por um
 * filtro de verdade. Regra fixa, sem IA.
 */
function sugestoes(texto: string, cfg: Config, base: Row[]): Sugestao[] {
  const t = normal(texto.trim());
  if (t.length < 2) return [];
  const limpa = (f: Filtro) => ({ ...f, busca: "" });
  const out: Sugestao[] = [];
  for (const a of cfg.atalhos ?? [])
    if (a.chaves.some((k) => k.startsWith(t) || t.includes(k)))
      out.push({ rotulo: a.rotulo, aplicar: (f) => limpa(a.aplicar(f)) });
  for (const c of cfg.campos) {
    if (c.tipo === "multi")
      for (const [k, r] of c.opcoes)
        if (normal(r).includes(t) || normal(k) === t)
          out.push({
            rotulo: `${c.rotulo}: ${r}`,
            aplicar: (f) =>
              limpa({
                ...f,
                v: {
                  ...f.v,
                  [c.chave]: [...new Set([...((f.v[c.chave] as string[]) ?? []), k])],
                },
              }),
          });
    if (c.tipo === "texto")
      for (const valor of [...new Set(base.map((r) => str(c.valor(r))).filter(Boolean))])
        if (normal(valor).includes(t))
          out.push({
            rotulo: `${c.rotulo}: ${valor}`,
            aplicar: (f) => limpa({ ...f, v: { ...f.v, [c.chave]: valor } }),
          });
    if (c.tipo === "marca" && normal(c.rotulo).includes(t))
      out.push({
        rotulo: c.rotulo,
        aplicar: (f) => limpa({ ...f, v: { ...f.v, [c.chave]: true } }),
      });
  }
  const combinada = combinar(t, cfg);
  if (combinada) out.unshift(combinada);
  const vistos = new Set<string>();
  return out
    .filter((s) => !vistos.has(normal(s.rotulo)) && vistos.add(normal(s.rotulo)))
    .slice(0, 8);
}

/**
 * Frase com mais de um filtro ("shopee cancelado sem nota"): reconhece cada
 * pedaço (opções dos campos e atalhos) e oferece aplicar todos de uma vez.
 * Uma opção conta quando o nome inteiro dela aparece na frase, ou a primeira
 * palavra dela, se tiver 4 letras ou mais.
 */
function combinar(t: string, cfg: Config): Sugestao | null {
  if (!t.includes(" ")) return null;
  const partes: { rotulo: string; aplicar: (f: Filtro) => Filtro }[] = [];
  // Palavra inteira, sem "não"/"sem" logo antes ("não enviado" não é "enviado").
  const contem = (frase: string, negavel = true) =>
    new RegExp(
      `(^|\\s)${negavel ? "(?<!(?:nao|sem)\\s)" : ""}${frase.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(\\s|$)`,
    ).test(t);
  for (const a of cfg.atalhos ?? [])
    if (a.chaves.some((k) => contem(k, !k.includes(" "))))
      partes.push({ rotulo: a.rotulo, aplicar: a.aplicar });
  for (const c of cfg.campos) {
    if (c.tipo !== "multi") continue;
    for (const [k, r] of c.opcoes) {
      const nome = normal(r);
      const primeira = nome.split(/[\s(]/)[0];
      if (!k || !(contem(nome) || (primeira.length >= 4 && contem(primeira)))) continue;
      partes.push({
        rotulo: `${c.rotulo.toLowerCase()}: ${r.toLowerCase()}`,
        aplicar: (f) => ({
          ...f,
          v: { ...f.v, [c.chave]: [...new Set([...((f.v[c.chave] as string[]) ?? []), k])] },
        }),
      });
    }
  }
  if (partes.length < 2) return null;
  return {
    rotulo: `Aplicar tudo: ${partes.map((p) => p.rotulo).join(" + ")}`,
    aplicar: (f) => partes.reduce((acc, p) => p.aplicar(acc), { ...f, busca: "" }),
  };
}

type Salvo = { nome: string; filtro: Filtro };

export default function FiltrosGenericos({
  cfg,
  filtro,
  setFiltro,
  base,
  total,
}: {
  cfg: Config;
  filtro: Filtro;
  setFiltro: (f: Filtro) => void;
  /** Registros antes dos filtros (para a prévia, a contagem e as sugestões). */
  base: Row[];
  total: number;
}) {
  const [aberto, setAberto] = useState<"" | "ordem" | "filtros">("");
  const [rascunho, setRascunho] = useState<Filtro>(filtro);
  const [focoBusca, setFocoBusca] = useState(false);
  const [salvos, setSalvos] = useState<Salvo[]>(() => ler<Salvo[]>(cfg.salvos, []));
  const raiz = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const fora = (e: MouseEvent) => {
      if (raiz.current && !raiz.current.contains(e.target as Node)) {
        setAberto("");
        setFocoBusca(false);
      }
    };
    document.addEventListener("mousedown", fora);
    return () => document.removeEventListener("mousedown", fora);
  }, []);

  const chips = chipsDe(filtro, cfg);
  const lista = focoBusca ? sugestoes(filtro.busca, cfg, base) : [];
  const previa = aberto === "filtros" ? filtrar(base, rascunho, cfg).length : 0;
  const ordemAtual = cfg.ordens.find(([k]) => k === filtro.ordem) ?? cfg.ordens[0];
  const muda = (chave: string, v: Valor) =>
    setRascunho({ ...rascunho, v: { ...rascunho.v, [chave]: v } });

  function abrir(qual: typeof aberto) {
    if (qual === "filtros") setRascunho(filtro);
    setAberto(aberto === qual ? "" : qual);
  }

  function salvarAtual() {
    const nome = window.prompt("Nome deste filtro:")?.trim();
    if (!nome) return;
    const novos = [...salvos.filter((s) => s.nome !== nome), { nome, filtro }];
    setSalvos(novos);
    gravar(cfg.salvos, novos);
  }

  function apagarSalvo(nome: string) {
    const novos = salvos.filter((s) => s.nome !== nome);
    setSalvos(novos);
    gravar(cfg.salvos, novos);
  }

  const campo = (c: Campo) => {
    const v = rascunho.v[c.chave];
    if (c.tipo === "multi") {
      const marcados = (v as string[]) ?? [];
      return (
        <fieldset key={c.chave} className="rd-filtro-grupo">
          <legend>{c.rotulo}</legend>
          {c.opcoes.length === 0 && <small>Nenhuma opção cadastrada.</small>}
          <div className={c.opcoes.length > 6 ? "rd-filtro-rolagem" : "rd-filtro-grupo"}>
            {c.opcoes.map(([k, r]) => (
              <label key={k} className="rd-check">
                <input
                  type="checkbox"
                  checked={marcados.includes(k)}
                  onChange={() =>
                    muda(
                      c.chave,
                      marcados.includes(k) ? marcados.filter((x) => x !== k) : [...marcados, k],
                    )
                  }
                />
                {r}
              </label>
            ))}
          </div>
        </fieldset>
      );
    }
    if (c.tipo === "texto") {
      const id = `rd-sug-${cfg.salvos}-${c.chave}`.replace(/\W/g, "-");
      return (
        <label key={c.chave}>
          {c.rotulo}
          <input
            list={id}
            placeholder={c.placeholder}
            value={(v as string) ?? ""}
            onChange={(e) => muda(c.chave, e.target.value)}
          />
          <datalist id={id}>
            {[...new Set(base.map((r) => str(c.valor(r))).filter(Boolean))].map((x) => (
              <option key={x} value={x} />
            ))}
          </datalist>
        </label>
      );
    }
    if (c.tipo === "faixa") {
      const faixa = (v as { de: string; ate: string }) ?? { de: "", ate: "" };
      const sufixo = c.unidade ? ` (${c.unidade})` : "";
      return (
        <div key={c.chave} className="rd-filtro-faixa">
          <label>
            {c.rotulo} de{sufixo}
            <input
              inputMode="decimal"
              value={faixa.de}
              onChange={(e) => muda(c.chave, { ...faixa, de: e.target.value })}
            />
          </label>
          <label>
            até{sufixo}
            <input
              inputMode="decimal"
              value={faixa.ate}
              onChange={(e) => muda(c.chave, { ...faixa, ate: e.target.value })}
            />
          </label>
        </div>
      );
    }
    if (c.tipo === "marca")
      return (
        <label key={c.chave} className="rd-check">
          <input
            type="checkbox"
            checked={v === true}
            onChange={(e) => muda(c.chave, e.target.checked)}
          />
          {c.rotulo}
        </label>
      );
    if (c.tipo === "periodo") {
      const p = (v as { de: string; ate: string }) ?? { de: "", ate: "" };
      return (
        <div key={c.chave} className="rd-filtro-faixa">
          <label>
            {c.rotulo} de
            <input
              type="date"
              value={p.de}
              onChange={(e) => muda(c.chave, { ...p, de: e.target.value })}
            />
          </label>
          <label>
            até
            <input
              type="date"
              value={p.ate}
              onChange={(e) => muda(c.chave, { ...p, ate: e.target.value })}
            />
          </label>
        </div>
      );
    }
    if (c.tipo === "cabe") {
      const m = (v as Medidas) ?? { c: "", l: "", a: "" };
      return (
        <fieldset key={c.chave} className="rd-filtro-grupo">
          <legend>{c.rotulo}</legend>
          <div className="rd-filtro-medidas">
            {(["c", "l", "a"] as const).map((k) => (
              <input
                key={k}
                inputMode="decimal"
                aria-label={{ c: "Comprimento (cm)", l: "Largura (cm)", a: "Altura (cm)" }[k]}
                placeholder={{ c: "C", l: "L", a: "A" }[k]}
                value={m[k]}
                onChange={(e) => muda(c.chave, { ...m, [k]: e.target.value })}
              />
            ))}
            <span>cm</span>
          </div>
        </fieldset>
      );
    }
    return (
      <label key={c.chave}>
        {c.rotulo}
        <input
          type="date"
          value={(v as string) ?? ""}
          onChange={(e) => muda(c.chave, e.target.value)}
        />
      </label>
    );
  };

  return (
    <div className="rd-filtros-produtos" ref={raiz}>
      <div className="rd-filtros-linha">
        <div className="rd-busca-produtos">
          <input
            aria-label={`Buscar ${cfg.plural}`}
            placeholder={cfg.placeholder}
            value={filtro.busca}
            onFocus={() => setFocoBusca(true)}
            onChange={(e) => setFiltro({ ...filtro, busca: e.target.value })}
            onKeyDown={(e) => e.key === "Escape" && setFocoBusca(false)}
          />
          <span className="rd-busca-lupa" aria-hidden="true">
            ⌕
          </span>
          {lista.length > 0 && (
            <ul className="rd-sugestoes" role="listbox" aria-label="Sugestões de filtro">
              <li className="rd-pop-titulo">Transformar em filtro</li>
              {lista.map((s) => (
                <li key={s.rotulo}>
                  <button
                    role="option"
                    aria-selected={false}
                    onClick={() => {
                      setFiltro(s.aplicar(filtro));
                      setFocoBusca(false);
                    }}
                  >
                    ⚲ {s.rotulo}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="rd-filtro-pop">
          <button
            type="button"
            className="rd-chip-filtro"
            aria-expanded={aberto === "ordem"}
            onClick={() => abrir("ordem")}
          >
            ⇅ {ordemAtual[1]}
          </button>
          {aberto === "ordem" && (
            <div className="rd-pop-chips">
              <small>Ordenar por</small>
              <div>
                {cfg.ordens.map(([k, r]) => (
                  <button
                    key={k}
                    className={`rd-chip-filtro${ordemAtual[0] === k ? " ativo" : ""}`}
                    onClick={() => {
                      setFiltro({ ...filtro, ordem: k });
                      setAberto("");
                    }}
                  >
                    {r}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
        <div className="rd-filtro-pop">
          <button
            type="button"
            className={`rd-chip-filtro${aberto === "filtros" ? " foco" : ""}`}
            aria-expanded={aberto === "filtros"}
            onClick={() => abrir("filtros")}
          >
            ⚲ filtros
            {chips.length > 0 && <span className="rd-chip-num">{chips.length}</span>}
          </button>
          {aberto === "filtros" && (
            <div
              className="rd-painel-filtros"
              role="dialog"
              aria-label={`Filtros de ${cfg.plural}`}
            >
              <div className="rd-painel-corpo">{cfg.campos.map(campo)}</div>
              <div className="rd-painel-rodape">
                <button
                  className="primary"
                  onClick={() => {
                    setFiltro(rascunho);
                    setAberto("");
                  }}
                >
                  aplicar · ver {previa}
                </button>
                <button onClick={() => setAberto("")}>cancelar</button>
              </div>
            </div>
          )}
        </div>
        <button
          type="button"
          className="rd-chip-limpar"
          disabled={chips.length === 0 && ordemAtual[0] === cfg.ordens[0][0]}
          onClick={() => setFiltro(filtroVazio(cfg))}
        >
          ⊖ limpar filtros
        </button>
      </div>
      {(chips.length > 0 || salvos.length > 0) && (
        <div className="rd-filtros-ativos">
          {chips.length > 0 && (
            <span className="rd-filtros-contagem">
              {total} de {base.length} {cfg.plural}
            </span>
          )}
          {chips.map((ch) => (
            <span key={ch.rotulo} className="rd-chip-ativo">
              {ch.rotulo}
              <button
                type="button"
                aria-label={`Tirar filtro ${ch.rotulo}`}
                onClick={() => setFiltro(ch.tirar(filtro))}
              >
                ✕
              </button>
            </span>
          ))}
          {chips.length > 0 && (
            <button type="button" className="rd-chip-salvar" onClick={salvarAtual}>
              ☆ salvar este filtro
            </button>
          )}
          {salvos.map((s) => (
            <span key={s.nome} className="rd-chip-salvo">
              <button type="button" onClick={() => setFiltro(s.filtro)}>
                ★ {s.nome}
              </button>
              <button
                type="button"
                aria-label={`Apagar filtro salvo ${s.nome}`}
                onClick={() => apagarSalvo(s.nome)}
              >
                ✕
              </button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
