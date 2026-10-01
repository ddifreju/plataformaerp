"use client";

// Telas de cadastro simples (clientes, fornecedores, categorias, embalagens).
// Cada uma é só configuração: colunas da lista e campos do formulário.

import { useState, type ReactNode } from "react";
import { Empty, Table, money, str, type Field, type ModalSpec, type Row } from "./ui";

type Coluna = { titulo: string; valor: (r: Row) => ReactNode };

type Config = {
  op: string; // operação de criação; a edição usa `${op}_atualizar`
  singular: string;
  colunas: Coluna[];
  campos: Field[];
  vazio: string;
};

const UFS =
  "AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split(" ");

const opcional = (key: string, label: string, type?: string): Field => ({
  key,
  label,
  type,
  required: false,
});

export const CONFIG: Record<string, Config> = {
  clientes: {
    op: "cliente",
    singular: "cliente",
    vazio: "Nenhum cliente cadastrado ainda.",
    colunas: [
      { titulo: "Nome", valor: (r) => <strong>{str(r.nome)}</strong> },
      { titulo: "Contato", valor: (r) => [r.email, r.telefone].filter(Boolean).join(" · ") || "—" },
      { titulo: "Cidade", valor: (r) => (r.cidade ? `${str(r.cidade)} / ${str(r.uf)}` : "—") },
    ],
    campos: [
      { key: "nome", label: "Nome" },
      opcional("email", "E-mail", "email"),
      opcional("telefone", "Telefone"),
      opcional("cidade", "Cidade"),
      {
        key: "uf",
        label: "UF",
        required: false,
        options: [{ value: "", label: "—" }, ...UFS.map((u) => ({ value: u, label: u }))],
      },
      opcional("observacao", "Observações", "textarea"),
    ],
  },
  fornecedores: {
    op: "fornecedor",
    singular: "fornecedor",
    vazio: "Nenhum fornecedor cadastrado ainda.",
    colunas: [
      { titulo: "Nome", valor: (r) => <strong>{str(r.nome)}</strong> },
      { titulo: "Contato", valor: (r) => str(r.contato) || "—" },
      {
        titulo: "E-mail / telefone",
        valor: (r) => [r.email, r.telefone].filter(Boolean).join(" · ") || "—",
      },
      {
        titulo: "Prazo de entrega",
        valor: (r) => (r.prazo_entrega_dias == null ? "—" : `${str(r.prazo_entrega_dias)} dias`),
      },
    ],
    campos: [
      { key: "nome", label: "Nome ou razão social" },
      opcional("documento", "CNPJ ou CPF"),
      opcional("contato", "Pessoa de contato"),
      opcional("email", "E-mail", "email"),
      opcional("telefone", "Telefone"),
      opcional("prazo_entrega_dias", "Prazo de entrega (dias)", "integer"),
      opcional("observacao", "Observações", "textarea"),
    ],
  },
  categorias: {
    op: "categoria",
    singular: "categoria",
    vazio: "Nenhuma categoria criada ainda.",
    colunas: [
      { titulo: "Categoria", valor: (r) => <strong>{str(r.nome)}</strong> },
      { titulo: "Descrição", valor: (r) => str(r.descricao) || "—" },
    ],
    campos: [{ key: "nome", label: "Nome" }, opcional("descricao", "Descrição", "textarea")],
  },
  embalagens: {
    op: "embalagem",
    singular: "embalagem",
    vazio: "Nenhuma embalagem cadastrada ainda.",
    colunas: [
      { titulo: "Embalagem", valor: (r) => <strong>{str(r.nome)}</strong> },
      { titulo: "Custo", valor: (r) => ("custo" in r ? money(r.custo) : "Sem acesso") },
      {
        titulo: "Medidas (C × L × A)",
        valor: (r) =>
          r.comprimento_cm
            ? `${str(r.comprimento_cm)} × ${str(r.largura_cm)} × ${str(r.altura_cm)} cm`
            : "—",
      },
      { titulo: "Peso", valor: (r) => (r.peso_g == null ? "—" : `${str(r.peso_g)} g`) },
    ],
    campos: [
      { key: "nome", label: "Nome (ex.: Caixa P)" },
      { key: "custo", label: "Custo por unidade", type: "number", value: "0" },
      opcional("comprimento_cm", "Comprimento (cm)", "number"),
      opcional("largura_cm", "Largura (cm)", "number"),
      opcional("altura_cm", "Altura (cm)", "number"),
      opcional("peso_g", "Peso (g)", "integer"),
    ],
  },
};

type Props = {
  tipo: keyof typeof CONFIG;
  linhas: Row[];
  podeEditar: boolean;
  abrirModal: (m: ModalSpec) => void;
};

export default function Cadastro({ tipo, linhas, podeEditar, abrirModal }: Props) {
  const c = CONFIG[tipo];
  const [busca, setBusca] = useState("");
  const filtradas = linhas.filter((l) =>
    JSON.stringify(l).toLowerCase().includes(busca.toLowerCase()),
  );

  function novo() {
    abrirModal({ title: `Cadastrar ${c.singular}`, op: c.op, fields: c.campos });
  }

  function editar(r: Row) {
    abrirModal({
      title: `Editar ${c.singular}`,
      op: `${c.op}_atualizar`,
      extra: { id: r.id },
      fields: c.campos.map((f) => ({ ...f, value: r[f.key] == null ? "" : str(r[f.key]) })),
    });
  }

  return (
    <section className="rd-card">
      <div className="rd-card-head">
        <h2>
          {linhas.length} {linhas.length === 1 ? c.singular : tipo}
        </h2>
        <div className="rd-heading-actions">
          <input
            id={`busca-${tipo}`}
            className="rd-search"
            placeholder="Buscar…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          {podeEditar && (
            <button className="primary" onClick={novo}>
              + Cadastrar {c.singular}
            </button>
          )}
        </div>
      </div>
      {linhas.length ? (
        <Table
          headers={[...c.colunas.map((x) => x.titulo), ""]}
          rows={filtradas.map((r) => [
            ...c.colunas.map((x) => x.valor(r)),
            podeEditar ? <button onClick={() => editar(r)}>Editar</button> : "",
          ])}
        />
      ) : (
        <Empty text={c.vazio} />
      )}
    </section>
  );
}
