"use client";

// Ações em lote da lista de produtos: barra embaixo quando há produtos
// marcados, com "editar dados em massa" e o menu "mais ações". O que depende
// de loja conectada (enviar ao e-commerce) aparece como "em breve".

import { useState } from "react";
import { money, str, type Row } from "./ui";

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
};

type Acao = "EDITAR" | "TAGS" | "INATIVAR" | "ATIVAR" | "EXCLUIR_ANEXOS" | "EXCLUIR";

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
    "CM",
    "L",
    "ML",
    "RL",
    "PCT",
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

const esc = (t: unknown) =>
  str(t).replace(
    /[&<>"]/g,
    (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[ch]!,
  );

function imprimir(titulo: string, corpo: string, estilo: string) {
  const janela = window.open("", "_blank");
  if (!janela) return false;
  janela.document.write(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
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
  const url = URL.createObjectURL(new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8" }));
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
}: Props) {
  const [menu, setMenu] = useState(false);
  const [acao, setAcao] = useState<Acao | null>(null);
  const [campo, setCampo] = useState("preco");
  const [modo, setModo] = useState("DEFINIR");
  const [valor, setValor] = useState("");
  const [tags, setTags] = useState("");
  const [modoTags, setModoTags] = useState("ADICIONAR");
  const [aviso, setAviso] = useState("");

  const todos = linhas.length > 0 && linhas.every((p) => marcados.includes(str(p.id)));
  const selecionados = produtos.filter((p) => marcados.includes(str(p.id)));
  // Relatórios e planilhas levam também as variações dos marcados.
  const comVariacoes = produtos.filter(
    (p) => marcados.includes(str(p.id)) || marcados.includes(str(p.pai_id)),
  );
  const tipoCampo = CAMPOS.find(([c]) => c === campo)?.[2] ?? "TEXTO";
  const nomeCategoria = (id: unknown) => str(categorias.find((c) => c.id === id)?.nome);

  function abrir(a: Acao) {
    setMenu(false);
    setAviso("");
    setValor("");
    setTags("");
    setModo("DEFINIR");
    setAcao(a);
  }

  function relatorio() {
    setMenu(false);
    const linhasHtml = comVariacoes
      .map(
        (p) => `<tr><td>${esc(p.sku)}</td><td>${esc(p.nome)}</td><td>${esc(p.marca)}</td>
<td>${esc(nomeCategoria(p.categoria_id))}</td><td>${esc(p.ncm)}</td>
${veCusto ? `<td class="n">${esc(money(p.custo))}</td>` : ""}<td class="n">${esc(money(p.preco))}</td>
<td class="n">${p.controla_estoque === false ? "—" : disponivel(p)}</td>
<td>${p.permite_venda === false ? "Inativo" : "Ativo"}</td></tr>`,
      )
      .join("");
    const ok = imprimir(
      "Relatório de produtos",
      `<h2>Relatório de produtos</h2><p>${comVariacoes.length} produto(s) · ${new Date().toLocaleString("pt-BR")}</p>
<table><thead><tr><th>SKU</th><th>Produto</th><th>Marca</th><th>Categoria</th><th>NCM</th>
${veCusto ? "<th>Custo</th>" : ""}<th>Preço</th><th>Disponível</th><th>Situação</th></tr></thead><tbody>${linhasHtml}</tbody></table>`,
      "table{width:100%;border-collapse:collapse}th,td{border-bottom:1px solid #ddd;padding:4px 6px;text-align:left}td.n{text-align:right}th{background:#f3f4f6}",
    );
    if (!ok) setAviso("O navegador bloqueou a janela de impressão. Libere pop-ups para este site.");
  }

  function etiquetas() {
    setMenu(false);
    const html = comVariacoes
      .filter((p) => p.tipo !== "VARIACAO")
      .map(
        (p) => `<div class="etq"><strong>${esc(p.nome)}</strong><span>SKU ${esc(p.sku)}</span>
${p.gtin ? `<span class="gtin">${esc(p.gtin)}</span>` : ""}<span class="preco">${esc(money(p.preco))}</span></div>`,
      )
      .join("");
    const ok = imprimir(
      "Etiquetas de produtos",
      `<div class="grade">${html}</div>`,
      ".grade{display:grid;grid-template-columns:repeat(3,1fr);gap:3mm}.etq{border:1px dashed #999;border-radius:2mm;padding:3mm;display:flex;flex-direction:column;gap:1mm;break-inside:avoid}.gtin{font-family:monospace;letter-spacing:2px}.preco{font-size:13pt;font-weight:bold}",
    );
    if (!ok) setAviso("O navegador bloqueou a janela de impressão. Libere pop-ups para este site.");
  }

  function exportarProdutos() {
    setMenu(false);
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
      ...comVariacoes.map((p) => [
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

  function exportarKits() {
    setMenu(false);
    const kits = selecionados.filter((p) => p.tipo === "KIT");
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
    const corpo: Record<string, unknown> = { op: "produtos_lote", acao, ids: marcados };
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
    if (ok) setMarcados(() => []);
  }

  const emBreve = (rotulo: string, motivo: string) => (
    <li>
      <button role="menuitem" disabled title={motivo}>
        {rotulo} <small>(em breve)</small>
      </button>
    </li>
  );
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
    EXCLUIR: "Excluir produtos",
  };

  const campoValor = () => {
    const opcoes =
      tipoCampo === "CATEGORIA"
        ? [["", "Escolha"], ...categorias.map((c): [string, string] => [str(c.id), str(c.nome)])]
        : tipoCampo === "EMBALAGEM"
          ? [["", "Nenhuma"], ...embalagens.map((e): [string, string] => [str(e.id), str(e.nome)])]
          : OPCOES[tipoCampo];
    if (opcoes)
      return (
        <select value={valor} onChange={(e) => setValor(e.target.value)}>
          {tipoCampo !== "CATEGORIA" && tipoCampo !== "EMBALAGEM" && tipoCampo !== "GARANTIA" && (
            <option value="">Escolha</option>
          )}
          {opcoes.map(([v, r]) => (
            <option key={v} value={v}>
              {r}
            </option>
          ))}
        </select>
      );
    return (
      <input
        inputMode={tipoCampo === "TEXTO" && campo === "marca" ? "text" : "decimal"}
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

  return (
    <>
      {linhas.length > 0 && (
        <label className="rd-check rd-marcar-todos">
          <input
            type="checkbox"
            checked={todos}
            onChange={(e) =>
              setMarcados(() => (e.target.checked ? linhas.map((p) => str(p.id)) : []))
            }
          />
          Marcar todos da lista ({linhas.length})
        </label>
      )}
      {aviso && (
        <div className="rd-error" role="alert">
          {aviso}
        </div>
      )}
      {marcados.length > 0 && (
        <div className="rd-barra-lote" role="region" aria-label="Ações para os produtos marcados">
          <span className="rd-barra-qtd">
            <button
              type="button"
              aria-label="Limpar seleção"
              title="Limpar seleção"
              onClick={() => setMarcados(() => [])}
            >
              ✕
            </button>
            {String(marcados.length).padStart(2, "0")} de {linhas.length} itens
          </span>
          <button
            type="button"
            className="primary"
            disabled
            title="Entra quando a loja for conectada"
          >
            ⇪ Enviar para o e-commerce
          </button>
          <button type="button" onClick={() => abrir("EDITAR")}>
            ✎ Editar dados em massa
          </button>
          <div className="rd-mais-acoes">
            <button type="button" aria-expanded={menu} onClick={() => setMenu(!menu)}>
              Mais ações ⋯
            </button>
            {menu && (
              <ul role="menu" className="rd-menu-cima rd-menu-longo">
                {emBreve("⇪ Enviar para o e-commerce", "Entra quando a loja for conectada")}
                {emBreve("$ Enviar preços para o e-commerce", "Entra quando a loja for conectada")}
                {emBreve("▦ Enviar estoque ao e-commerce", "Entra quando a loja for conectada")}
                {emBreve(
                  "▤ Enviar dados fiscais para o e-commerce",
                  "Entra quando a loja for conectada",
                )}
                <li className="rd-menu-sep" />
                {item("🖨 Imprimir relatório", relatorio)}
                {item("⇩ Exportar produtos para planilha", exportarProdutos)}
                {item("⇩ Exportar composição de kits para planilha", exportarKits)}
                {emBreve(
                  "⇩ Exportar estrutura de fabricados",
                  "Entra junto com o cadastro de produção (fabricados)",
                )}
                <li className="rd-menu-sep" />
                {item("✎ Editar dados em massa", () => abrir("EDITAR"))}
                {item("🏷 Imprimir etiquetas", etiquetas)}
                {item("# Alterar tags", () => abrir("TAGS"))}
                {emBreve(
                  "⇆ Unificar cadastros",
                  "Precisa juntar estoque e histórico; vem numa próxima etapa",
                )}
                <li className="rd-menu-sep" />
                {emBreve("⤨ Transformar em variações", "Vem numa próxima etapa")}
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
                {item("✓ Ativar produtos", () => abrir("ATIVAR"))}
                {item("✕ Inativar produtos", () => abrir("INATIVAR"))}
                {item("🗑 Excluir anexos dos produtos", () => abrir("EXCLUIR_ANEXOS"))}
                {item("🗑 Excluir produto", () => abrir("EXCLUIR"))}
              </ul>
            )}
          </div>
        </div>
      )}
      {acao && (
        <div className="rd-modal-backdrop" onClick={() => setAcao(null)}>
          <section
            className="rd-modal"
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
              {marcados.length} produto(s) marcado(s). Vale também para as variações deles.
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
                    {CAMPOS.filter(([c]) => veCusto || c !== "custo").map(([c, r]) => (
                      <option key={c} value={c}>
                        {r}
                      </option>
                    ))}
                  </select>
                </label>
                {tipoCampo === "DINHEIRO" && (
                  <label>
                    Como mudar
                    <select value={modo} onChange={(e) => setModo(e.target.value)}>
                      {MODOS.map(([m, r]) => (
                        <option key={m} value={m}>
                          {r}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                <label className="wide">
                  {tipoCampo === "DINHEIRO" && modo.endsWith("PCT") ? "Percentual" : "Novo valor"}
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
                  <select value={modoTags} onChange={(e) => setModoTags(e.target.value)}>
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
              <p>Os produtos saem da venda (novos pedidos e anúncios). O histórico continua.</p>
            )}
            {acao === "ATIVAR" && <p>Os produtos voltam a ficar disponíveis para venda.</p>}
            {acao === "EXCLUIR_ANEXOS" && (
              <p>Remove todas as imagens dos produtos marcados. Não dá para desfazer.</p>
            )}
            {acao === "EXCLUIR" && (
              <p>
                Excluir {marcados.length} produto(s)? Não dá para desfazer. Produto com pedido,
                anúncio, estoque ou que faz parte de kit não é excluído: inative em vez disso.
              </p>
            )}
            <div className="rd-modal-foot">
              <button onClick={() => setAcao(null)}>Cancelar</button>
              <button
                className={acao === "EXCLUIR" || acao === "EXCLUIR_ANEXOS" ? "perigo" : "primary"}
                onClick={confirmar}
              >
                {acao === "EXCLUIR" || acao === "EXCLUIR_ANEXOS" ? "Excluir" : "Confirmar"}
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
