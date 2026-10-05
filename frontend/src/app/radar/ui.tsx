import type { ReactNode } from "react";

export type Row = Record<string, string | number | boolean | Record<string, unknown>>;
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
export function Table({ headers, rows }: { headers: ReactNode[]; rows: ReactNode[][] }) {
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
    <Empty />
  );
}
