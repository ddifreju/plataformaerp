"use client";

// Ações em lote da lista de produtos: caixa de seleção com "⋯" em cada linha,
// barra embaixo quando há produtos marcados, com "editar dados em massa" e o
// menu "mais ações". O que depende de loja conectada aparece como "em breve".
// A tabela é desenhada por quem usa (render prop), recebendo a caixa do
// cabeçalho e a de cada linha.

import { useState, type ReactNode } from "react";
import { money, str, type Row } from "./ui";
import type { TipoRapido } from "./acoes-rapidas";

type Props = {
  produtos: Row[];
  linhas: Row[];
  marcados: string[];
  setMarcados: (f: (m: string[]) => string[]) => void;
  categorias: Row[];
  embalagens: Row[];
  kitItens: Row[];
  veCusto: boolean;
  disponivel: (p: Row) => number;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  /** Clona o produto (com imagens) e abre a cópia. */
  clonar?: (id: string) => void;
  /** Abre o passo a passo "Anunciar" (sem a função, o cargo não anuncia). */
  anunciar?: (ids: string[]) => void;
  /** Abre um painel de ação rápida na lateral (preços, estoque, histórico…). */
  rapido: (tipo: TipoRapido, ids: string[]) => void;
  /** Quem vê custo e quem pode mexer em custo. */
  podeCusto: boolean;
  ativo: boolean;
  children: (k: {
    cabecalho: ReactNode;
    celula: (p: Row) => ReactNode;
  }) => ReactNode;
};

type Acao =
  "EDITAR" | "TAGS" | "INATIVAR" | "ATIVAR" | "EXCLUIR_ANEXOS" | "EXCLUIR";

const CAMPOS: [string, string, string][] = [
  ["preco", "Preço de venda", "DINHEIRO"],
  ["preco_promocional", "Preço promocional", "DINHEIRO"],
  ["custo", "Custo", "DINHEIRO"],
  ["marca", "Marca", "TEXTO"],
  ["categoria_id", "Categoria", "CATEGORIA"],
  ["embalagem_id", "Embalagem de envio", "EMBALAGEM"],
  ["ncm", "NCM", "TEXTO"],
  ["cest", "CEST", "TEXTO"],
  ["origem", "Origem (ICMS)", "ORIGEM"],
  ["unidade", "Unidade", "UNIDADE"],
  ["condicao", "Condição", "CONDICAO"],
  ["peso_bruto_kg", "Peso bruto (kg)", "NUMERO"],
  ["peso_liquido_kg", "Peso líquido (kg)", "NUMERO"],
  ["largura_cm", "Largura (cm)", "NUMERO"],
  ["altura_cm", "Altura (cm)", "NUMERO"],
  ["comprimento_cm", "Comprimento (cm)", "NUMERO"],
  ["minimo", "Estoque mínimo", "NUMERO"],
  ["maximo", "Estoque máximo", "NUMERO"],
  ["dias_preparacao", "Dias de preparação", "NUMERO"],
  ["garantia_tipo", "Tipo de garantia", "GARANTIA"],
  ["garantia_meses", "Garantia (meses)", "NUMERO"],
  ["controla_estoque", "Controla estoque", "SIM_NAO"],
  ["permite_venda", "Disponível para venda", "SIM_NAO"],
];
const OPCOES: Record<string, [string, string][]> = {
  ORIGEM: [
    ["0", "0 - Nacional"],
    ["1", "1 - Estrangeira, importação direta"],
    ["2", "2 - Estrangeira, adquirida no mercado interno"],
    ["3", "3 - Nacional, conteúdo de importação acima de 40%"],
    ["4", "4 - Nacional, processos produtivos básicos"],
    ["5", "5 - Nacional, conteúdo de importação até 40%"],
    ["6", "6 - Estrangeira, importação direta sem similar nacional"],
    ["7", "7 - Estrangeira, mercado interno sem similar nacional"],
    ["8", "8 - Nacional, conteúdo de importação acima de 70%"],
  ],
  UNIDADE: [
    "UN",
    "PC",
    "CX",
    "KIT",
    "PAR",
    "JG",
    "KG",
    "G",
    "M",
    "M2",
    "M3",
    "L",
    "ML",
    "RL",
  ].map((u): [string, string] => [u, u]),
  CONDICAO: [
    ["NOVO", "Novo"],
    ["USADO", "Usado"],
    ["RECONDICIONADO", "Recondicionado"],
  ],
  GARANTIA: [
    ["", "Não informado"],
    ["VENDEDOR", "Do vendedor"],
    ["FABRICANTE", "Do fabricante"],
    ["SEM_GARANTIA", "Sem garantia"],
  ],
  SIM_NAO: [
    ["true", "Sim"],
    ["false", "Não"],
  ],
};
const MODOS: [string, string][] = [
  ["DEFINIR", "Definir o valor"],
  ["AUMENTAR_PCT", "Aumentar em %"],
  ["REDUZIR_PCT", "Reduzir em %"],
  ["SOMAR", "Somar R$"],
  ["SUBTRAIR", "Subtrair R$"],
];

export const esc = (t: unknown) =>
  str(t).replace(
    /[&<>"]/g,
    (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[ch]!,
  );

export function imprimir(titulo: string, corpo: string, estilo: string) {
  const janela = window.open("", "_blank");
  if (!janela) return false;
  janela.document
    .write(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
<title>${esc(titulo)}</title><style>body{font-family:Arial,sans-serif;margin:8mm;font-size:10pt}
${estilo}</style></head><body>${corpo}
<script>window.onload=function(){window.print()}<\/script></body></html>`);
  janela.document.close();
  return true;
}

// Planilha CSV com ";" e BOM: abre certo no Excel em português.
function baixarCsv(nome: string, linhas: (string | number)[][]) {
  const celula = (v: string | number) => `"${String(v).replace(/"/g, '""')}"`;
  const csv = linhas.map((l) => l.map(celula).join(";")).join("\n");
  const url = URL.createObjectURL(
    new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = `${nome}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

export default function ProdutosLote({
  produtos,
  linhas,
  marcados,
  setMarcados,
  categorias,
  embalagens,
  kitItens,
  veCusto,
  disponivel,
  executar,
  clonar,
  anunciar,
  rapido,
  podeCusto,
  ativo,
  children,
}: Props) {
  const [menu, setMenu] = useState(false);
  const [menuLinha, setMenuLinha] = useState<{
    id: string;
    x: number;
    y: number;
  } | null>(null);
  const [acao, setAcao] = useState<Acao | null>(null);
  // Produtos que a ação vale: os marcados (barra) ou um só (⋯ da linha).
  const [alvo, setAlvo] = useState<string[]>([]);
  const [campo, setCampo] = useState("preco");
  const [modo, setModo] = useState("DEFINIR");
  const [valor, setValor] = useState("");
  const [tags, setTags] = useState("");
  const [modoTags, setModoTags] = useState("ADICIONAR");
  const [aviso, setAviso] = useState("");

  const todos =
    linhas.length > 0 && linhas.every((p) => marcados.includes(str(p.id)));
  // Relatórios e planilhas levam também as variações dos escolhidos.
  const comVariacoes = (ids: string[]) =>
    produtos.filter(
      (p) => ids.includes(str(p.id)) || ids.includes(str(p.pai_id)),
    );
  const todosInativos =
    marcados.length > 0 &&
    produtos
      .filter((p) => marcados.includes(str(p.id)))
      .every((p) => p.permite_venda === false);
  const tipoCampo = CAMPOS.find(([c]) => c === campo)?.[2] ?? "TEXTO";
  const nomeCategoria = (id: unknown) =>
    str(categorias.find((c) => c.id === id)?.nome);

  function fecharMenus() {
    setMenu(false);
    setMenuLinha(null);
  }

  function abrir(a: Acao, ids: string[] = marcados) {
    fecharMenus();
    setAlvo(ids);
    setAviso("");
    setValor("");
    setTags("");
    setModo("DEFINIR");
    setAcao(a);
  }

  function enviarEcommerce() {
    const ids = menuLinha ? [menuLinha.id] : marcados;
    fecharMenus();
    if (anunciar) anunciar(ids);
    else setAviso("Seu cargo não permite anunciar.");
  }

  function relatorio(ids: string[] = marcados) {
    fecharMenus();
    const lista = comVariacoes(ids);
    const linhasHtml = lista
      .map(
        (
          p,
        ) => `<tr><td>${esc(p.sku)}</td><td>${esc(p.nome)}</td><td>${esc(p.marca)}</td>
<td>${esc(nomeCategoria(p.categoria_id))}</td><td>${esc(p.ncm)}</td>
${veCusto ? `<td class="n">${esc(money(p.custo))}</td>` : ""}<td class="n">${esc(money(p.preco))}</td>
<td class="n">${p.controla_estoque === false ? "—" : disponivel(p)}</td>
<td>${p.permite_venda === false ? "Inativo" : "Ativo"}</td></tr>`,
      )
      .join("");
    const ok = imprimir(
      "Relatório de produtos",
      `<h2>Relatório de produtos</h2><p>${lista.length} produto(s) · ${new Date().toLocaleString("pt-BR")}</p>
<table><thead><tr><th>SKU</th><th>Produto</th><th>Marca</th><th>Categoria</th><th>NCM</th>
${veCusto ? "<th>Custo</th>" : ""}<th>Preço</th><th>Disponível</th><th>Situação</th></tr></thead><tbody>${linhasHtml}</tbody></table>`,
      "table{width:100%;border-collapse:collapse}th,td{border-bottom:1px solid #ddd;padding:4px 6px;text-align:left}td.n{text-align:right}th{background:#f3f4f6}",
    );
    if (!ok)
      setAviso(
        "O navegador bloqueou a janela de impressão. Libere pop-ups para este site.",
      );
  }

  function etiquetas(ids: string[] = marcados) {
    fecharMenus();
    const html = comVariacoes(ids)
      .filter((p) => p.tipo !== "VARIACAO")
      .map(
        (
          p,
        ) => `<div class="etq"><strong>${esc(p.nome)}</strong><span>SKU ${esc(p.sku)}</span>
${p.gtin ? `<span class="gtin">${esc(p.gtin)}</span>` : ""}<span class="preco">${esc(money(p.preco))}</span></div>`,
      )
      .join("");
    const ok = imprimir(
      "Etiquetas de produtos",
      `<div class="grade">${html}</div>`,
      ".grade{display:grid;grid-template-columns:repeat(3,1fr);gap:3mm}.etq{border:1px dashed #999;border-radius:2mm;padding:3mm;display:flex;flex-direction:column;gap:1mm;break-inside:avoid}.gtin{font-family:monospace;letter-spacing:2px}.preco{font-size:13pt;font-weight:bold}",
    );
    if (!ok)
      setAviso(
        "O navegador bloqueou a janela de impressão. Libere pop-ups para este site.",
      );
  }

  function exportarProdutos(ids: string[] = marcados) {
    fecharMenus();
    const cab = [
      "SKU",
      "Nome",
      "Tipo",
      "Produto principal",
      "Marca",
      "Categoria",
      "GTIN",
      "NCM",
      "CEST",
      "Origem",
      "Unidade",
      ...(veCusto ? ["Custo"] : []),
      "Preço",
      "Peso bruto (kg)",
      "Largura (cm)",
      "Altura (cm)",
      "Comprimento (cm)",
      "Disponível",
      "Situação",
    ];
    const sku = (id: unknown) => str(produtos.find((p) => p.id === id)?.sku);
    baixarCsv("produtos", [
      cab,
      ...comVariacoes(ids).map((p) => [
        str(p.sku),
        str(p.nome),
        str(p.tipo),
        sku(p.pai_id),
        str(p.marca),
        nomeCategoria(p.categoria_id),
        str(p.gtin),
        str(p.ncm),
        str(p.cest),
        str(p.origem),
        str(p.unidade),
        ...(veCusto ? [str(p.custo).replace(".", ",")] : []),
        str(p.preco).replace(".", ","),
        str(p.peso_bruto_kg).replace(".", ","),
        str(p.largura_cm).replace(".", ","),
        str(p.altura_cm).replace(".", ","),
        str(p.comprimento_cm).replace(".", ","),
        p.controla_estoque === false ? "" : disponivel(p),
        p.permite_venda === false ? "Inativo" : "Ativo",
      ]),
    ]);
  }

  function exportarKits(ids: string[] = marcados) {
    fecharMenus();
    const kits = produtos.filter(
      (p) => ids.includes(str(p.id)) && p.tipo === "KIT",
    );
    if (!kits.length) {
      setAviso("Nenhum kit entre os produtos marcados.");
      return;
    }
    const prod = (id: unknown) => produtos.find((p) => p.id === id);
    baixarCsv("composicao-de-kits", [
      ["SKU do kit", "Kit", "SKU do componente", "Componente", "Quantidade"],
      ...kits.flatMap((k) =>
        kitItens
          .filter((i) => i.kit_id === k.id)
          .map((i) => [
            str(k.sku),
            str(k.nome),
            str(prod(i.componente_id)?.sku),
            str(prod(i.componente_id)?.nome),
            str(i.quantidade),
          ]),
      ),
    ]);
  }

  async function confirmar() {
    const corpo: Record<string, unknown> = {
      op: "produtos_lote",
      acao,
      ids: alvo,
    };
    if (acao === "EDITAR") {
      corpo.campo = campo;
      corpo.valor = valor.trim().replace(",", ".");
      if (tipoCampo === "DINHEIRO") corpo.modo = modo;
      if (tipoCampo === "TEXTO" && (campo === "ncm" || campo === "cest"))
        corpo.valor = valor.replace(/\D/g, "");
    }
    if (acao === "TAGS") {
      corpo.modo = modoTags;
      corpo.tags = tags
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean);
    }
    const ok = await executar(corpo);
    setAcao(null);
    if (ok) setMarcados((m) => m.filter((id) => !alvo.includes(id)));
  }

  const emBreve = (rotulo: string, motivo: string) => (
    <li>
      <button role="menuitem" disabled title={motivo}>
        {rotulo} <small>({motivo.toLowerCase()})</small>
      </button>
    </li>
  );
  // Ação rápida: fecha o menu e abre o painel lateral com os produtos da linha ou os marcados.
  const rapidoItem = (rotulo: string, tipo: TipoRapido, ids: string[]) =>
    item(rotulo, () => {
      fecharMenus();
      rapido(tipo, ids);
    });
  const item = (rotulo: string, onClick: () => void) => (
    <li>
      <button role="menuitem" onClick={onClick}>
        {rotulo}
      </button>
    </li>
  );
  const titulos: Record<Acao, string> = {
    EDITAR: "Editar dados em massa",
    TAGS: "Alterar tags",
    INATIVAR: "Inativar produtos",
    ATIVAR: "Ativar produtos",
    EXCLUIR_ANEXOS: "Excluir anexos dos produtos",
    EXCLUIR: "Mover para a lixeira",
  };

  const campoValor = () => {
    const opcoes =
      tipoCampo === "CATEGORIA"
        ? [
            ["", "Escolha"],
            ...categorias.map((c): [string, string] => [
              str(c.id),
              str(c.nome),
            ]),
          ]
        : tipoCampo === "EMBALAGEM"
          ? [
              ["", "Nenhuma"],
              ...embalagens.map((e): [string, string] => [
                str(e.id),
                str(e.nome),
              ]),
            ]
          : OPCOES[tipoCampo];
    if (opcoes)
      return (
        <select value={valor} onChange={(e) => setValor(e.target.value)}>
          {tipoCampo !== "CATEGORIA" &&
            tipoCampo !== "EMBALAGEM" &&
            tipoCampo !== "GARANTIA" && <option value="">Escolha</option>}
          {opcoes.map(([v, r]) => (
            <option key={v} value={v}>
              {r}
            </option>
          ))}
        </select>
      );
    return (
      <input
        inputMode={
          tipoCampo === "TEXTO" && campo === "marca" ? "text" : "decimal"
        }
        placeholder={
          tipoCampo === "DINHEIRO" && modo.endsWith("PCT")
            ? "Ex.: 10"
            : tipoCampo === "DINHEIRO"
              ? "Ex.: 49,90"
              : ""
        }
        value={valor}
        onChange={(e) => setValor(e.target.value)}
      />
    );
  };

  const caixa = (p: Row) => {
    const id = str(p.id);
    return (
      <span className="rd-celula-lote">
        <input
          type="checkbox"
          aria-label={`Selecionar ${str(p.nome)}`}
          checked={marcados.includes(id)}
          onChange={(e) =>
            setMarcados((m) =>
              e.target.checked ? [...m, id] : m.filter((x) => x !== id),
            )
          }
        />
        <button
          type="button"
          className="rd-linha-mais"
          aria-label={`Ações de ${str(p.nome)}`}
          aria-expanded={menuLinha?.id === id}
          onClick={(e) => {
            const r = e.currentTarget.getBoundingClientRect();
            setMenu(false);
            // Perto do rodapé o menu abre para cima, para não sair da tela.
            const altura = Math.min(420, window.innerHeight * 0.6);
            const y =
              r.bottom + altura > window.innerHeight
                ? Math.max(8, r.top - altura)
                : r.bottom + 4;
            setMenuLinha(menuLinha?.id === id ? null : { id, x: r.left, y });
          }}
        >
          ⋯
        </button>
      </span>
    );
  };
  const cabecalho = (
    <input
      type="checkbox"
      aria-label="Marcar todos da lista"
      title="Marcar todos da lista"
      checked={todos}
      onChange={(e) =>
        setMarcados(() =>
          e.target.checked ? linhas.map((p) => str(p.id)) : [],
        )
      }
    />
  );
  const produtoLinha =
    menuLinha && produtos.find((p) => str(p.id) === menuLinha.id);

  return (
    <>
      {children({
        cabecalho: ativo ? cabecalho : "",
        celula: (p) => (ativo ? caixa(p) : ""),
      })}
      {menuLinha && produtoLinha && (
        <>
          <div className="rd-menu-fundo" onClick={() => setMenuLinha(null)} />
          <ul
            role="menu"
            className="rd-menu-linha"
            style={{ left: menuLinha.x, top: menuLinha.y }}
            aria-label={`Ações de ${str(produtoLinha.nome)}`}
          >
            {item("⇪ Enviar para o e-commerce", enviarEcommerce)}
            {rapidoItem("$ Enviar preços para o e-commerce", "precos", [
              menuLinha.id,
            ])}
            {rapidoItem("▦ Enviar estoque ao e-commerce", "estoque", [
              menuLinha.id,
            ])}
            {rapidoItem("▤ Enviar dados fiscais para o e-commerce", "fiscais", [
              menuLinha.id,
            ])}
            <li className="rd-menu-sep" />
            {produtoLinha.tipo !== "KIT" &&
              rapidoItem("▦ Gerenciar estoque", "gerenciar-estoque", [
                menuLinha.id,
              ])}
            {rapidoItem("⌕ Consultar estoque multiempresa", "multiempresa", [
              menuLinha.id,
            ])}
            {item("🏷 Imprimir etiqueta", () => etiquetas([menuLinha.id]))}
            {rapidoItem("↙ Visualizar histórico de compras", "compras", [
              menuLinha.id,
            ])}
            {rapidoItem("↗ Visualizar histórico de vendas", "vendas", [
              menuLinha.id,
            ])}
            <li className="rd-menu-sep" />
            {item("✎ Editar dados", () => abrir("EDITAR", [menuLinha.id]))}
            {clonar &&
              item("⧉ Clonar produto", () => {
                const id = menuLinha.id;
                setMenuLinha(null);
                clonar(id);
              })}
            {item("# Alterar tags", () => abrir("TAGS", [menuLinha.id]))}
            {produtoLinha.tipo === "VARIACAO" &&
              emBreve("⇄ Tornar produto simples", "Entra com o Bloco 2")}
            {emBreve("⇪ Enviar produto para empresas", "Grupo de empresas")}
            {produtoLinha.tipo === "VARIACAO" &&
              podeCusto &&
              rapidoItem("$ Atualizar custo das variações", "custo-variacoes", [
                menuLinha.id,
              ])}
            <li className="rd-menu-sep" />
            {item("🖨 Imprimir relatório", () => relatorio([menuLinha.id]))}
            {item("⇩ Exportar para planilha", () =>
              exportarProdutos([menuLinha.id]),
            )}
            {produtoLinha.tipo === "KIT" &&
              item("⇩ Exportar composição do kit", () =>
                exportarKits([menuLinha.id]),
              )}
            <li className="rd-menu-sep" />
            {produtoLinha.permite_venda === false
              ? item("✓ Ativar produto", () => abrir("ATIVAR", [menuLinha.id]))
              : item("⊘ Inativar produto", () =>
                  abrir("INATIVAR", [menuLinha.id]),
                )}
            {item("🗑 Excluir anexos", () =>
              abrir("EXCLUIR_ANEXOS", [menuLinha.id]),
            )}
            {item("🗑 Mover para a lixeira", () =>
              abrir("EXCLUIR", [menuLinha.id]),
            )}
          </ul>
        </>
      )}
      {aviso && (
        <div className="rd-error" role="alert">
          {aviso}
        </div>
      )}
      {ativo && marcados.length > 0 && (
        <div
          className="rd-barra-lote"
          role="region"
          aria-label="Ações para os produtos marcados"
        >
          <span className="rd-barra-qtd rd-pilula">
            <span aria-hidden="true">↥</span>
            {String(marcados.length).padStart(2, "0")} de {linhas.length} itens
            <button
              type="button"
              aria-label="Limpar seleção"
              title="Limpar seleção"
              onClick={() => setMarcados(() => [])}
            >
              ✕
            </button>
          </span>
          <button type="button" className="primary" onClick={enviarEcommerce}>
            ⇪ Enviar para o e-commerce
          </button>
          <button type="button" onClick={() => abrir("EDITAR")}>
            ✎ Editar dados em massa
          </button>
          <div className="rd-mais-acoes">
            <button
              type="button"
              aria-expanded={menu}
              onClick={() => {
                setMenuLinha(null);
                setMenu(!menu);
              }}
            >
              Mais ações{" "}
              <span className="rd-circulo" aria-hidden="true">
                ⋯
              </span>
            </button>
            {menu && (
              <ul role="menu" className="rd-menu-cima rd-menu-longo">
                {item("⇪ Enviar para o e-commerce", enviarEcommerce)}
                {rapidoItem(
                  "$ Enviar preços para o e-commerce",
                  "precos",
                  marcados,
                )}
                {rapidoItem(
                  "▦ Enviar estoque ao e-commerce",
                  "estoque",
                  marcados,
                )}
                {rapidoItem(
                  "▤ Enviar dados fiscais para o e-commerce",
                  "fiscais",
                  marcados,
                )}
                {rapidoItem(
                  "↗ Visualizar histórico de vendas",
                  "vendas",
                  marcados,
                )}
                <li className="rd-menu-sep" />
                {item("🖨 Imprimir relatório", () => relatorio())}
                {item("⇩ Exportar produtos para planilha", () =>
                  exportarProdutos(),
                )}
                {item("⇩ Exportar composição de kits para planilha", () =>
                  exportarKits(),
                )}
                {emBreve(
                  "⇩ Exportar estrutura de fabricados para planilha",
                  "Entra junto com o cadastro de produção (fabricados)",
                )}
                <li className="rd-menu-sep" />
                {item("✎ Editar dados em massa", () => abrir("EDITAR"))}
                {item("🏷 Imprimir etiquetas", () => etiquetas())}
                {item("# Alterar tags", () => abrir("TAGS"))}
                {emBreve(
                  "⇆ Unificar cadastros",
                  "Precisa juntar estoque e histórico; vem numa próxima etapa",
                )}
                <li className="rd-menu-sep" />
                {emBreve(
                  "⤨ Transformar em variações",
                  "Vem numa próxima etapa",
                )}
                {emBreve("⊞ Transformar em kits", "Vem numa próxima etapa")}
                <li className="rd-menu-sep" />
                {emBreve(
                  "⇪ Enviar produtos para outras empresas",
                  "Quando a conta tiver mais de uma empresa",
                )}
                {emBreve(
                  "✦ Sugerir NCM",
                  "Sugestão por IA, sempre revisada por você; vem numa próxima etapa",
                )}
                <li className="rd-menu-sep" />
                {todosInativos
                  ? item("✓ Ativar produtos", () => abrir("ATIVAR"))
                  : item("⊘ Inativar produtos", () => abrir("INATIVAR"))}
                {item("🗑 Excluir anexos dos produtos", () =>
                  abrir("EXCLUIR_ANEXOS"),
                )}
                {item("🗑 Mover para a lixeira", () => abrir("EXCLUIR"))}
              </ul>
            )}
          </div>
        </div>
      )}
      {acao && (
        <div
          className="rd-modal-backdrop lateral"
          onClick={() => setAcao(null)}
        >
          <section
            className="rd-modal lateral"
            role="dialog"
            aria-modal="true"
            aria-label={titulos[acao]}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rd-card-head">
              <h2>{titulos[acao]}</h2>
              <button aria-label="Fechar" onClick={() => setAcao(null)}>
                ×
              </button>
            </div>
            <p className="rd-note">
              {alvo.length === 1
                ? `Produto: ${str(produtos.find((p) => str(p.id) === alvo[0])?.nome)}.`
                : `${alvo.length} produtos marcados.`}{" "}
              Vale também para as variações.
            </p>
            {acao === "EDITAR" && (
              <div className="rd-form-grid">
                <label>
                  Campo
                  <select
                    value={campo}
                    onChange={(e) => {
                      setCampo(e.target.value);
                      setValor("");
                      setModo("DEFINIR");
                    }}
                  >
                    {CAMPOS.filter(([c]) => veCusto || c !== "custo").map(
                      ([c, r]) => (
                        <option key={c} value={c}>
                          {r}
                        </option>
                      ),
                    )}
                  </select>
                </label>
                {tipoCampo === "DINHEIRO" && (
                  <label>
                    Como mudar
                    <select
                      value={modo}
                      onChange={(e) => setModo(e.target.value)}
                    >
                      {MODOS.map(([m, r]) => (
                        <option key={m} value={m}>
                          {r}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                <label className="wide">
                  {tipoCampo === "DINHEIRO" && modo.endsWith("PCT")
                    ? "Percentual"
                    : "Novo valor"}
                  {campoValor()}
                  <small className="rd-dica">
                    {tipoCampo === "DINHEIRO" && modo !== "DEFINIR"
                      ? "O resultado é arredondado para centavos. Kits não mudam de custo: ele vem dos componentes."
                      : "Em branco limpa o campo, quando ele não é obrigatório."}
                  </small>
                </label>
              </div>
            )}
            {acao === "TAGS" && (
              <div className="rd-form-grid">
                <label>
                  O que fazer
                  <select
                    value={modoTags}
                    onChange={(e) => setModoTags(e.target.value)}
                  >
                    <option value="ADICIONAR">Adicionar estas tags</option>
                    <option value="REMOVER">Remover estas tags</option>
                    <option value="SUBSTITUIR">Trocar todas por estas</option>
                  </select>
                </label>
                <label className="wide">
                  Tags
                  <input
                    placeholder="ex.: blackout, sala, promoção"
                    value={tags}
                    onChange={(e) => setTags(e.target.value)}
                  />
                  <small className="rd-dica">Separe por vírgula.</small>
                </label>
              </div>
            )}
            {acao === "INATIVAR" && (
              <p>
                Os produtos saem da venda (novos pedidos e anúncios). O
                histórico continua.
              </p>
            )}
            {acao === "ATIVAR" && (
              <p>Os produtos voltam a ficar disponíveis para venda.</p>
            )}
            {acao === "EXCLUIR_ANEXOS" && (
              <p>
                Remove todas as imagens dos produtos marcados. Não dá para
                desfazer.
              </p>
            )}
            {acao === "EXCLUIR" && (
              <p>
                Mover {alvo.length} produto(s) para a lixeira? Eles saem da
                venda e das listas, mas pedidos e histórico continuam. Dá para
                restaurar quando quiser, em &quot;Lixeira&quot;.
              </p>
            )}
            <div className="rd-modal-foot">
              <button onClick={() => setAcao(null)}>Cancelar</button>
              <button
                className={
                  acao === "EXCLUIR" || acao === "EXCLUIR_ANEXOS"
                    ? "perigo"
                    : "primary"
                }
                onClick={confirmar}
              >
                {acao === "EXCLUIR"
                  ? "Mover para a lixeira"
                  : acao === "EXCLUIR_ANEXOS"
                    ? "Excluir"
                    : "Confirmar"}
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
