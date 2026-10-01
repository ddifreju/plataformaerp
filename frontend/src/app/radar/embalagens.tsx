"use client";

// Embalagens: já vêm sugeridas (caixas, envelopes, sacos, tubos, papelão) e a
// lojista edita ou cadastra novas. Tipo e tags servem para a calculadora de
// preços escolher a embalagem depois; o custo de cada uma é dela.

import { useEffect, useRef, useState } from "react";
import { Badge, Empty, Table, money, str, type Row } from "./ui";

type Props = {
  embalagens: Row[];
  veCusto: boolean;
  podeEditar: boolean;
  busy: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<Record<string, unknown> | null>;
};

export const TIPOS_EMBALAGEM: [string, string][] = [
  ["CAIXA", "Caixa de papelão"],
  ["ENVELOPE", "Envelope"],
  ["ENVELOPE_BOLHA", "Envelope com plástico bolha"],
  ["SACO", "Saco plástico"],
  ["TUBO", "Tubo"],
  ["PAPELAO", "Chapa de papelão"],
  ["OUTRO", "Outro"],
];
const rotuloTipo = (t: unknown) => TIPOS_EMBALAGEM.find(([v]) => v === t)?.[1] ?? "Outro";

// "13.5" vira "13,5" e "27.0" vira "27".
const cm = (v: unknown) => Number(v).toLocaleString("pt-BR", { maximumFractionDigits: 1 });

function tags(e: Row): string[] {
  return Array.isArray(e.tags) ? (e.tags as unknown as string[]) : [];
}

export default function Embalagens(props: Props) {
  const [aberta, setAberta] = useState<Row | "nova" | null>(null);
  const [tipo, setTipo] = useState("");
  const [busca, setBusca] = useState("");
  const pediuSugeridas = useRef(false);

  // As sugeridas vêm "de fábrica": na primeira visita de quem pode editar,
  // entram sozinhas. Repetir não duplica.
  const temSugeridas = props.embalagens.some((e) => e.sugerida === true);
  useEffect(() => {
    if (props.podeEditar && !temSugeridas && !pediuSugeridas.current) {
      pediuSugeridas.current = true;
      props.executar({ op: "embalagens_sugeridas" });
    }
  }, [props, temSugeridas]);

  const semCusto = props.veCusto
    ? props.embalagens.filter((e) => Number(e.custo ?? 0) === 0).length
    : 0;
  const termo = busca.trim().toLowerCase();
  const linhas = props.embalagens.filter(
    (e) =>
      (!tipo || e.tipo === tipo) &&
      (!termo ||
        str(e.nome).toLowerCase().includes(termo) ||
        tags(e).some((t) => t.includes(termo))),
  );

  return (
    <>
      {semCusto > 0 && (
        <div className="rd-alerta-anuncios" role="alert">
          <strong>⚠ {semCusto} embalagem(ns) sem custo.</strong> Informe quanto você paga em cada
          uma: a calculadora de preços usa esse valor.
        </div>
      )}
      <section className="rd-card">
        <div className="rd-card-head">
          <h2>{props.embalagens.length} embalagens</h2>
          <input
            aria-label="Buscar embalagem"
            className="rd-search"
            placeholder="Buscar por nome ou tag…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          {props.podeEditar && (
            <button className="primary" onClick={() => setAberta("nova")}>
              + Nova embalagem
            </button>
          )}
        </div>
        <div className="rd-tabs" role="tablist" aria-label="Tipo de embalagem">
          {[["", "Todas"] as [string, string], ...TIPOS_EMBALAGEM].map(([v, r]) => {
            const n = v
              ? props.embalagens.filter((e) => e.tipo === v).length
              : props.embalagens.length;
            if (v && n === 0) return null;
            return (
              <button
                key={v || "todas"}
                role="tab"
                aria-selected={tipo === v}
                className={tipo === v ? "active" : ""}
                onClick={() => setTipo(v)}
              >
                {r} ({n})
              </button>
            );
          })}
        </div>
        {props.embalagens.length === 0 ? (
          <Empty text="Carregando as embalagens sugeridas…" />
        ) : (
          <Table
            headers={["Embalagem", "Medidas (C × L × A)", "Peso", "Custo", "Tags", ""]}
            rows={linhas.map((e) => [
              <div key="n">
                <strong>{str(e.nome)}</strong>
                <br />
                <small>
                  {rotuloTipo(e.tipo)}
                  {e.sugerida === true && " · sugerida"}
                </small>
              </div>,
              e.comprimento_cm
                ? `${cm(e.comprimento_cm)} × ${cm(e.largura_cm)} × ${cm(e.altura_cm)} cm`
                : "—",
              e.peso_g == null ? "—" : `${str(e.peso_g)} g`,
              !props.veCusto ? (
                "Sem acesso"
              ) : Number(e.custo ?? 0) === 0 ? (
                <Badge key="c" tone="amber">
                  Informe o custo
                </Badge>
              ) : (
                money(e.custo)
              ),
              <div key="t" className="rd-tags">
                {tags(e).map((t) => (
                  <span key={t}>{t}</span>
                ))}
              </div>,
              props.podeEditar ? (
                <button key="e" onClick={() => setAberta(e)}>
                  Editar
                </button>
              ) : (
                ""
              ),
            ])}
          />
        )}
        <p className="rd-note">
          Medidas sugeridas são tamanhos comuns de mercado; confira com as do seu fornecedor. O peso
          da embalagem soma no frete.
        </p>
      </section>
      {aberta && (
        <EmbalagemForm
          {...props}
          embalagem={aberta === "nova" ? null : aberta}
          fechar={() => setAberta(null)}
        />
      )}
    </>
  );
}

function EmbalagemForm({
  embalagem,
  veCusto,
  busy,
  executar,
  fechar,
}: Props & { embalagem: Row | null; fechar: () => void }) {
  const [v, setV] = useState<Record<string, string>>(() => ({
    nome: str(embalagem?.nome),
    tipo: str(embalagem?.tipo) || "CAIXA",
    custo: embalagem?.custo == null ? "" : str(embalagem.custo),
    comprimento_cm: str(embalagem?.comprimento_cm),
    largura_cm: str(embalagem?.largura_cm),
    altura_cm: str(embalagem?.altura_cm),
    peso_g: embalagem?.peso_g == null ? "" : str(embalagem.peso_g),
    tags: embalagem ? tags(embalagem).join(", ") : "",
  }));
  const set = (k: string) => (e: { target: { value: string } }) =>
    setV({ ...v, [k]: e.target.value });

  async function salvar() {
    const corpo: Record<string, unknown> = {
      op: embalagem ? "embalagem_atualizar" : "embalagem",
      ...v,
      custo: v.custo || "0",
      tags: v.tags
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean),
    };
    if (embalagem) corpo.id = embalagem.id;
    if (await executar(corpo)) fechar();
  }

  return (
    <div className="rd-modal-backdrop" onClick={() => !busy && fechar()}>
      <section
        className="rd-modal"
        role="dialog"
        aria-modal="true"
        aria-label="Embalagem"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="rd-card-head">
          <h2>{embalagem ? "Editar embalagem" : "Nova embalagem"}</h2>
          <button aria-label="Fechar" onClick={fechar}>
            ×
          </button>
        </div>
        <div className="rd-form-grid">
          <label className="wide">
            Nome *
            <input id="embalagem-nome" maxLength={120} value={v.nome} onChange={set("nome")} />
          </label>
          <label>
            Tipo
            <select id="embalagem-tipo" value={v.tipo} onChange={set("tipo")}>
              {TIPOS_EMBALAGEM.map(([valor, r]) => (
                <option key={valor} value={valor}>
                  {r}
                </option>
              ))}
            </select>
          </label>
          {veCusto && (
            <label>
              Custo unitário (R$)
              <input
                id="embalagem-custo"
                inputMode="decimal"
                placeholder="0.00"
                value={v.custo}
                onChange={set("custo")}
              />
            </label>
          )}
          <label>
            Comprimento (cm)
            <input inputMode="decimal" value={v.comprimento_cm} onChange={set("comprimento_cm")} />
          </label>
          <label>
            Largura (cm)
            <input inputMode="decimal" value={v.largura_cm} onChange={set("largura_cm")} />
          </label>
          <label>
            Altura (cm)
            <input inputMode="decimal" value={v.altura_cm} onChange={set("altura_cm")} />
          </label>
          <label>
            Peso da embalagem (g)
            <input inputMode="numeric" value={v.peso_g} onChange={set("peso_g")} />
          </label>
          <label className="wide">
            Tags
            <input
              id="embalagem-tags"
              placeholder="ex.: caixa, frágil, cortina"
              value={v.tags}
              onChange={set("tags")}
            />
            <small className="rd-dica">
              Separe por vírgula. Ajudam a achar a embalagem certa na calculadora de preços.
            </small>
          </label>
        </div>
        <div className="rd-modal-foot">
          <button onClick={fechar}>Cancelar</button>
          <button className="primary" disabled={busy || !v.nome.trim()} onClick={salvar}>
            {busy ? "Salvando…" : "Salvar embalagem"}
          </button>
        </div>
      </section>
    </div>
  );
}
