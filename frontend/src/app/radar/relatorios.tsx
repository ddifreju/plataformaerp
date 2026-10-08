"use client";
/* eslint-disable react/jsx-key -- Table wraps each supplied cell in a keyed td; these arrays are table data, not rendered sibling lists. */

// Relatórios: todos os números vêm prontos do servidor (GET /api/radar/relatorios);
// a tela só formata e exporta. Nada é recalculado aqui.

import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Badge, Empty, Table, money, str, type Row } from "./ui";

type Relatorio = {
  periodo: { de: string; ate: string };
  resumo: Record<string, string | number | null>;
  porDia: Row[];
  porCanal: Row[];
  porCategoria: Row[];
  curvaAbc: Row[];
  porCliente: Row[];
  estoque: Row[];
};

function isoDiasAtras(dias: number) {
  const d = new Date();
  d.setDate(d.getDate() - dias);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function pct(v: unknown) {
  return v == null ? "—" : `${str(v).replace(".", ",")}%`;
}

// "C00001 · Ana Souza": o código separa clientes de mesmo nome.
function cliente(l: Row) {
  return [str(l.codigo), str(l.nome)].filter(Boolean).join(" · ");
}

// Exporta a tabela como CSV (separador ;, padrão do Excel em português).
function exportar(nome: string, cabecalho: string[], linhas: (string | number)[][]) {
  const escapar = (v: string | number) => `"${String(v).replace(/"/g, '""')}"`;
  const csv = [cabecalho, ...linhas].map((l) => l.map(escapar).join(";")).join("\n");
  const url = URL.createObjectURL(new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = `${nome}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

function Bloco({
  titulo,
  fonte,
  cabecalho,
  linhas,
  csv,
}: {
  titulo: string;
  fonte?: string;
  cabecalho: string[];
  linhas: ReactNode[][];
  csv: (string | number)[][];
}) {
  return (
    <section className="rd-card">
      <div className="rd-card-head">
        <h2>{titulo}</h2>
        {linhas.length > 0 && (
          <button
            onClick={() => exportar(titulo.toLowerCase().replace(/\s+/g, "-"), cabecalho, csv)}
          >
            Exportar CSV
          </button>
        )}
      </div>
      {linhas.length ? (
        <Table headers={cabecalho} rows={linhas} />
      ) : (
        <Empty text="Sem dados no período." />
      )}
      {fonte && <p className="rd-note">{fonte}</p>}
    </section>
  );
}

/** O relatório de antes: resumo do período, por canal e categoria, curva ABC e estoque. */
export function ResumoDoPeriodo() {
  const [de, setDe] = useState(isoDiasAtras(29));
  const [ate, setAte] = useState(isoDiasAtras(0));
  const [dados, setDados] = useState<Relatorio | null>(null);
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(true);

  const carregar = useCallback(async (inicio: string, fim: string) => {
    setCarregando(true);
    setErro("");
    try {
      const res = await fetch(`/api/radar/relatorios?de=${inicio}&ate=${fim}`, {
        credentials: "include",
      });
      const corpo = await res.json();
      if (!res.ok) throw new Error(corpo.mensagem ?? "Não foi possível gerar os relatórios.");
      setDados(corpo);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    // Carga inicial com o período padrão (últimos 30 dias).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    carregar(isoDiasAtras(29), isoDiasAtras(0));
  }, [carregar]);

  const r = dados?.resumo;

  return (
    <>
      <section className="rd-card rd-relatorio-periodo">
        <label>
          De
          <input id="relatorio-de" type="date" value={de} onChange={(e) => setDe(e.target.value)} />
        </label>
        <label>
          Até
          <input
            id="relatorio-ate"
            type="date"
            value={ate}
            onChange={(e) => setAte(e.target.value)}
          />
        </label>
        <button className="primary" disabled={carregando} onClick={() => carregar(de, ate)}>
          {carregando ? "Gerando…" : "Gerar relatórios"}
        </button>
        <Badge>Base local · valores informados, não homologados</Badge>
      </section>
      {erro && (
        <div className="rd-error" role="alert">
          {erro}
        </div>
      )}
      {dados && r && (
        <>
          <div className="rd-kpis">
            {[
              ["Receita", money(r.receita)],
              ["Resultado", money(r.resultado)],
              ["Margem", pct(r.margem)],
              ["Pedidos", str(r.pedidos)],
              ["Ticket médio", r.ticketMedio == null ? "—" : money(r.ticketMedio)],
            ].map(([rotulo, valor]) => (
              <div className="rd-kpi" key={rotulo}>
                <span>{rotulo}</span>
                <strong>{valor}</strong>
              </div>
            ))}
          </div>
          <p className="rd-note">{str(r.fonte)}</p>

          <Bloco
            titulo="Vendas por dia"
            cabecalho={["Dia", "Pedidos", "Receita", "Resultado"]}
            linhas={dados.porDia.map((l) => [
              str(l.dia).split("-").reverse().join("/"),
              str(l.pedidos),
              money(l.receita),
              money(l.resultado),
            ])}
            csv={dados.porDia.map((l) => [
              str(l.dia),
              str(l.pedidos),
              str(l.receita),
              str(l.resultado),
            ])}
          />
          <div className="rd-grid two">
            <Bloco
              titulo="Resultado por canal"
              cabecalho={["Canal", "Pedidos", "Receita", "Resultado", "Margem"]}
              linhas={dados.porCanal.map((l) => [
                str(l.nome),
                str(l.pedidos),
                money(l.receita),
                money(l.resultado),
                pct(l.margem),
              ])}
              csv={dados.porCanal.map((l) => [
                str(l.nome),
                str(l.pedidos),
                str(l.receita),
                str(l.resultado),
                str(l.margem),
              ])}
            />
            <Bloco
              titulo="Resultado por categoria"
              cabecalho={["Categoria", "Receita", "Resultado", "Margem"]}
              linhas={dados.porCategoria.map((l) => [
                str(l.nome),
                money(l.receita),
                money(l.resultado),
                pct(l.margem),
              ])}
              csv={dados.porCategoria.map((l) => [
                str(l.nome),
                str(l.receita),
                str(l.resultado),
                str(l.margem),
              ])}
            />
          </div>
          <Bloco
            titulo="Curva ABC de produtos"
            fonte="Classe A: produtos que somam até 80% da receita do período; B: até 95%; C: o restante."
            cabecalho={[
              "Classe",
              "SKU",
              "Produto",
              "Receita",
              "Participação",
              "Acumulado",
              "Margem",
            ]}
            linhas={dados.curvaAbc.map((l) => [
              <Badge tone={l.classe === "A" ? "green" : l.classe === "B" ? "amber" : "gray"}>
                {str(l.classe)}
              </Badge>,
              str(l.sku),
              str(l.nome),
              money(l.receita),
              pct(l.participacao),
              pct(l.acumulado),
              pct(l.margem),
            ])}
            csv={dados.curvaAbc.map((l) => [
              str(l.classe),
              str(l.sku),
              str(l.nome),
              str(l.receita),
              str(l.participacao),
              str(l.acumulado),
              str(l.margem),
            ])}
          />
          <div className="rd-grid two">
            <Bloco
              titulo="Principais clientes"
              cabecalho={["Cliente", "Pedidos", "Receita"]}
              linhas={dados.porCliente.map((l) => [cliente(l), str(l.pedidos), money(l.receita)])}
              csv={dados.porCliente.map((l) => [cliente(l), str(l.pedidos), str(l.receita)])}
            />
            <Bloco
              titulo="Estoque a custo por categoria"
              fonte="Posição atual do estoque, independente do período escolhido."
              cabecalho={["Categoria", "SKUs", "Unidades", "Valor a custo"]}
              linhas={dados.estoque.map((l) => [
                str(l.nome),
                str(l.skus),
                str(l.unidades),
                money(l.valor_custo),
              ])}
              csv={dados.estoque.map((l) => [
                str(l.nome),
                str(l.skus),
                str(l.unidades),
                str(l.valor_custo),
              ])}
            />
          </div>
        </>
      )}
    </>
  );
}
