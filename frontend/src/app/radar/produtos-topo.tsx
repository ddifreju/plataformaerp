"use client";

// Topo da lista de produtos: o "mais ações" da página (modelo pedido pela Jéssica, diferente do
// "mais ações" do lote) e a escolha das colunas visíveis. As ações valem para os produtos
// marcados ou, sem marcação, para os que estão na lista (com os filtros aplicados).

import { useState, type RefObject } from "react";
import { Gaveta } from "./acoes-rapidas";
import { gravar, ler } from "./produtos-filtros";
import type { ControleLote } from "./produtos-lote";

export const COLUNAS: [string, string][] = [
  ["imagem", "Imagem"],
  ["sku", "Código (SKU)"],
  ["gtin", "GTIN/EAN"],
  ["unidade", "Unidade"],
  ["ncm", "NCM"],
  ["tipo", "Tipo"],
  ["preco", "Preço"],
  ["custo", "Custo"],
  ["marca", "Marca"],
  ["fornecedor", "Cód. fornecedor"],
  ["fisico", "Estoque físico"],
  ["disponivel", "Estoque disponível"],
  ["anuncios", "Anúncios"],
  ["cadastro", "Cadastro"],
];
const PADRAO = ["imagem", "sku", "tipo", "preco", "custo", "disponivel", "anuncios", "cadastro"];
const CHAVE = "radar.produtos.colunas";

/** Colunas escolhidas, lembradas neste navegador. */
export function useColunas() {
  const [colunas, setColunas] = useState<string[]>(() =>
    typeof window === "undefined" ? PADRAO : ler(CHAVE, PADRAO),
  );
  return [
    colunas,
    (c: string[]) => {
      setColunas(c);
      gravar(CHAVE, c);
    },
  ] as const;
}

export function ColunasVisiveis(props: {
  colunas: string[];
  financeiro: boolean;
  aplicar: (c: string[]) => void;
  fechar: () => void;
}) {
  const [escolha, setEscolha] = useState(props.colunas);
  const opcoes = COLUNAS.filter(([c]) => props.financeiro || c !== "custo");
  const todas = opcoes.every(([c]) => escolha.includes(c));
  const alternar = (c: string) =>
    setEscolha(escolha.includes(c) ? escolha.filter((x) => x !== c) : [...escolha, c]);
  return (
    <Gaveta
      titulo="Informações visíveis"
      fechar={props.fechar}
      rodape={
        <>
          <button
            className="primary"
            onClick={() => {
              props.aplicar(escolha);
              props.fechar();
            }}
          >
            aplicar
          </button>
          <button onClick={props.fechar}>cancelar</button>
        </>
      }
    >
      <p className="rd-dica">Escolha as colunas que aparecem na lista de produtos.</p>
      <ul className="rd-colunas">
        <li>
          <label>
            <input
              type="checkbox"
              checked={todas}
              onChange={() => setEscolha(todas ? [] : opcoes.map(([c]) => c))}
            />
            <span>Colunas</span>
          </label>
        </li>
        <li>
          <label>
            <input type="checkbox" className="rd-switch" checked disabled />
            <span>Descrição (nome do produto)</span>
          </label>
        </li>
        {opcoes.map(([c, rotulo]) => (
          <li key={c}>
            <label>
              <input
                type="checkbox"
                className="rd-switch"
                checked={escolha.includes(c)}
                onChange={() => alternar(c)}
              />
              <span>{rotulo}</span>
            </label>
          </li>
        ))}
      </ul>
    </Gaveta>
  );
}

export function MaisAcoesProdutos(props: {
  controle: RefObject<ControleLote | null>;
  /** Os marcados ou, sem marcação, os que estão na lista. */
  ids: () => string[];
  podeEditar: boolean;
  podeAnuncios: boolean;
  /** Quem vê e cuida de custo inicia o histórico de custos. */
  iniciarCustos?: (ids: string[]) => void;
  receber: () => void;
  problemasFiscais: () => void;
  ir: (pagina: string) => void;
}) {
  const [aberto, setAberto] = useState(false);
  const item = (rotulo: string, acao: () => void) => (
    <li>
      <button
        role="menuitem"
        onClick={() => {
          setAberto(false);
          acao();
        }}
      >
        {rotulo}
      </button>
    </li>
  );
  const emBreve = (rotulo: string, motivo: string) => (
    <li>
      <button role="menuitem" disabled title={motivo}>
        {rotulo} <small>({motivo})</small>
      </button>
    </li>
  );
  const lista = () => props.controle.current;
  return (
    <div className="rd-mais-acoes">
      <button type="button" aria-expanded={aberto} onClick={() => setAberto(!aberto)}>
        mais ações{" "}
        <span className="rd-circulo" aria-hidden="true">
          ⋯
        </span>
      </button>
      {aberto && (
        <>
          <div className="rd-menu-fundo" onClick={() => setAberto(false)} />
          <ul role="menu" className="rd-menu-longo">
            {item("⇩ receber produtos do e-commerce", props.receber)}
            <li className="rd-menu-sep" />
            {item("🖨 imprimir relatório", () => lista()?.relatorio(props.ids()))}
            {props.iniciarCustos &&
              item("☰ iniciar histórico de custos", () => props.iniciarCustos?.(props.ids()))}
            {props.podeEditar &&
              item("$ reajustar preço dos produtos", () =>
                lista()?.editarCampo("preco", "AUMENTAR_PCT", props.ids()),
              )}
            {props.podeEditar &&
              item("✎ editar CEST dos produtos em lote", () =>
                lista()?.editarCampo("cest", "DEFINIR", props.ids()),
              )}
            {item("▤ produtos com problemas fiscais", props.problemasFiscais)}
            {props.podeAnuncios &&
              item("$ gerenciar preços dos anúncios", () => props.ir("anuncios"))}
            {emBreve("✦ consultar último lote de sugestão de NCM", "Vem com a sugestão de NCM")}
            <li className="rd-menu-sep" />
            {item("⇧ exportar produtos para planilha", () =>
              lista()?.exportarProdutos(props.ids()),
            )}
            {item("⇧ exportar composição de kits para planilha", () =>
              lista()?.exportarKits(props.ids()),
            )}
            {emBreve(
              "⇧ exportar estrutura de fabricados para planilha",
              "Vem com o cadastro de fabricados",
            )}
            {props.podeEditar &&
              item("⇩ importar produtos de uma planilha", () => props.ir("importar"))}
          </ul>
        </>
      )}
    </div>
  );
}
