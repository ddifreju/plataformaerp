import { useEffect, useRef, type ReactNode } from "react";

/**
 * Menu aberto fecha com clique (ou toque) em qualquer lugar fora dele e com Esc. Devolve a ref
 * para pôr no elemento que envolve o botão e o menu.
 */
export function useFecharFora<T extends HTMLElement>(aberto: boolean, fechar: () => void) {
  const ref = useRef<T>(null);
  const fecharAtual = useRef(fechar);
  useEffect(() => {
    fecharAtual.current = fechar;
  });
  useEffect(() => {
    if (!aberto) return;
    const fora = (e: Event) => {
      if (ref.current && !ref.current.contains(e.target as Node)) fecharAtual.current();
    };
    const esc = (e: KeyboardEvent) => e.key === "Escape" && fecharAtual.current();
    document.addEventListener("pointerdown", fora);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("pointerdown", fora);
      document.removeEventListener("keydown", esc);
    };
  }, [aberto]);
  return ref;
}

export type Row = Record<string, string | number | boolean | Record<string, unknown>>;

// Tamanhos de roupa na ordem de quem veste; o resto em ordem alfabética (com números).
const TAMANHOS = ["PP", "P", "M", "G", "GG", "XG", "XGG", "EG", "EGG"];
const compararValor = (a: string, b: string) => {
  const ta = TAMANHOS.indexOf(a.toUpperCase()),
    tb = TAMANHOS.indexOf(b.toUpperCase());
  if (ta >= 0 && tb >= 0) return ta - tb;
  return a.localeCompare(b, "pt-BR", { numeric: true, sensitivity: "base" });
};

/** Variações sempre na mesma ordem (Azul/P, Azul/M, Verde/P…), pelos tipos de variação. */
export function ordenarVariacoes<T extends { atributos?: unknown; atributos_variacao?: unknown }>(
  lista: T[],
  tipos: string[],
): T[] {
  // Produto vem com atributos_variacao (o seu "atributos" é outra coisa, uma lista); a linha da
  // grade do formulário vem com atributos.
  const attr = (x: T) => {
    const a = x.atributos_variacao ?? x.atributos;
    return (a && typeof a === "object" && !Array.isArray(a) ? a : {}) as Record<string, string>;
  };
  return [...lista].sort((x, y) => {
    for (const t of tipos) {
      const c = compararValor(String(attr(x)[t] ?? ""), String(attr(y)[t] ?? ""));
      if (c) return c;
    }
    return 0; // empate: sort é estável, mantém a ordem recebida
  });
}
export type Field = {
  key: string;
  label: string;
  type?: string;
  value?: string;
  options?: { value: string; label: string }[];
  required?: boolean;
};
export type ModalSpec = {
  title: string;
  fields: Field[];
  op: string;
  extra?: Record<string, unknown>;
};

export function cents(v: unknown) {
  const s = String(v ?? "0");
  const [i, f = ""] = s.split(".");
  return Number(i) * 100 + (i.startsWith("-") ? -1 : 1) * Number(f.padEnd(2, "0").slice(0, 2));
}
export function money(v: unknown) {
  return (cents(v) / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}
export function centMoney(v: number) {
  return (v / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}
export function date(v: unknown) {
  return new Date(String(v)).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}
export function str(v: unknown) {
  return String(v ?? "");
}
export function Badge({ children, tone = "gray" }: { children: ReactNode; tone?: string }) {
  return <span className={`rd-badge ${tone}`}>{children}</span>;
}
export function Empty({
  text = "Ainda não há registros.",
  action,
}: {
  text?: string;
  action?: ReactNode;
}) {
  return (
    <div className="rd-empty">
      <span>◇</span>
      <h3>{text}</h3>
      <p>Comece com um cadastro ou explore o guia do Radar.</p>
      {action}
    </div>
  );
}
export function Table({
  headers,
  rows,
  vazio,
}: {
  headers: ReactNode[];
  rows: ReactNode[][];
  /** Texto quando não há linhas (ex.: filtro sem resultado). */
  vazio?: string;
}) {
  return rows.length ? (
    <div className="rd-table-wrap">
      <table>
        <thead>
          <tr>
            {headers.map((h, j) => (
              <th key={j}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {r.map((c, j) => (
                <td key={j}>{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ) : (
    <Empty text={vazio} />
  );
}
