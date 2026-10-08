"use client";

// Busca e filtros da lista de produtos. Tudo combina (E entre os campos, OU
// dentro de um campo com várias escolhas): ex. "na Shopee OU no Mercado Livre"
// E "sem NCM" E "kit". Além do desenho:
// - sugestões na própria busca ("Filtrar marca: Casa Clara");
// - cada filtro ativo vira um chip removível acima da lista;
// - o botão Aplicar já mostra quantos produtos vão aparecer;
// - filtros salvos com nome (neste navegador) e o último filtro lembrado.

import { useEffect, useRef, useState } from "react";
import { cents, str, type Row } from "./ui";

export type FiltroProdutos = {
  busca: string;
  campo: "TUDO" | "NOME" | "SKU" | "GTIN" | "NCM" | "MARCA";
  ordem: "NOME" | "RECENTES" | "ATUALIZACAO" | "SKU" | "PRECO" | "ESTOQUE";
  situacao: "ATIVOS" | "INATIVOS" | "TODOS";
  noCanal: string[];
  foraCanal: string[];
  categorias: string[];
  fornecedor: string;
  marca: string;
  ncm: string;
  semNcm: boolean;
  tags: string[];
  atualizadoDesde: string;
  tipos: string[];
  estoque: "" | "SEM" | "ABAIXO_MINIMO" | "COM";
  precoDe: string;
  precoAte: string;
  pendencias: string[];
};

export const FILTRO_VAZIO: FiltroProdutos = {
  busca: "",
  campo: "TUDO",
  ordem: "NOME",
  situacao: "ATIVOS",
  noCanal: [],
  foraCanal: [],
  categorias: [],
  fornecedor: "",
  marca: "",
  ncm: "",
  semNcm: false,
  tags: [],
  atualizadoDesde: "",
  tipos: [],
  estoque: "",
  precoDe: "",
  precoAte: "",
  pendencias: [],
};

// Só os marketplaces que o Radar já sabe ler. Os outros entram com as integrações.
export { CANAIS as CANAIS_FILTRO } from "./canais";
import { CANAIS as CANAIS_FILTRO } from "./canais";
const SEM_ANUNCIO = "SEM";

const CAMPOS: [FiltroProdutos["campo"], string][] = [
  ["TUDO", "Nome, SKU, GTIN/EAN, marca e NCM"],
  ["NOME", "Só no nome"],
  ["SKU", "Só no código (SKU)"],
  ["GTIN", "Só no GTIN/EAN"],
  ["NCM", "Só no NCM"],
  ["MARCA", "Só na marca"],
];
const ORDENS: [FiltroProdutos["ordem"], string][] = [
  ["NOME", "nome"],
  ["RECENTES", "mais recentes"],
  ["ATUALIZACAO", "data de atualização"],
  ["SKU", "código (sku)"],
  ["PRECO", "maior preço"],
  ["ESTOQUE", "menor estoque"],
];
const SITUACOES: [FiltroProdutos["situacao"], string][] = [
  ["ATIVOS", "produtos ativos"],
  ["INATIVOS", "produtos inativos"],
  ["TODOS", "ativos e inativos"],
];
const TIPOS: [string, string][] = [
  ["SIMPLES", "Simples"],
  ["VARIACAO", "Com variações"],
  ["KIT", "Kit"],
];
const ESTOQUES: [FiltroProdutos["estoque"], string][] = [
  ["", "Qualquer"],
  ["SEM", "Sem estoque disponível"],
  ["ABAIXO_MINIMO", "Abaixo do mínimo"],
  ["COM", "Com estoque disponível"],
];
const PENDENCIAS: [string, string][] = [
  ["INCOMPLETO", "Cadastro incompleto"],
  ["SEM_IMAGEM", "Sem imagem"],
  ["SEM_CATEGORIA", "Sem categoria"],
  ["SEM_GTIN", "Sem GTIN/EAN e sem motivo"],
  ["SEM_PESO", "Sem peso bruto"],
  ["FISCAL", "Com problema fiscal"],
];

const digitos = (v: unknown) => str(v).replace(/\D/g, "");
/** NCM sem 8 dígitos, sem origem, sem código de barras (nem motivo) ou CEST fora do formato. */
export const problemaFiscal = (p: Row) =>
  digitos(p.ncm).length !== 8 ||
  str(p.origem) === "" ||
  (!p.gtin && !p.motivo_sem_gtin) ||
  (!!p.cest && digitos(p.cest).length !== 7);

/** O que os filtros precisam saber além do próprio produto. */
export type ContextoFiltro = {
  produtos: Row[];
  anuncios: Row[];
  categorias: Row[];
  fornecedores: Row[];
  produtoFornecedores: Row[];
  imagens: Row[];
  disponivel: (p: Row) => number;
};

export const normal = (t: unknown) => str(t).toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");

/**
 * Aplica busca, filtros e ordem à lista de produtos principais. Variação conta
 * para o produto pai: buscar o SKU de uma cor mostra o produto, e anúncio de
 * uma variação faz o produto aparecer "na Shopee".
 */
export function filtrarProdutos(lista: Row[], f: FiltroProdutos, c: ContextoFiltro): Row[] {
  const filhos = new Map<string, Row[]>();
  for (const p of c.produtos)
    if (p.pai_id) filhos.set(str(p.pai_id), [...(filhos.get(str(p.pai_id)) ?? []), p]);
  const familia = (p: Row) => [p, ...(filhos.get(str(p.id)) ?? [])];
  const canaisDe = new Map<string, Set<string>>();
  for (const a of c.anuncios) {
    const s = canaisDe.get(str(a.produto_id)) ?? new Set<string>();
    s.add(str(a.canal));
    canaisDe.set(str(a.produto_id), s);
  }
  const canais = (p: Row) => {
    const s = new Set<string>();
    for (const x of familia(p)) canaisDe.get(str(x.id))?.forEach((v) => s.add(v));
    return s;
  };
  const termo = normal(f.busca.trim());
  const marca = normal(f.marca.trim());
  const ncm = f.ncm.replace(/\D/g, "");
  const fornecedorTermo = normal(f.fornecedor.trim());
  const fornecedoresOk = fornecedorTermo
    ? new Set(
        c.fornecedores
          .filter((x) => normal(`${str(x.nome)} ${str(x.fantasia)}`).includes(fornecedorTermo))
          .map((x) => str(x.id)),
      )
    : null;
  const precoDe = f.precoDe ? cents(f.precoDe.replace(",", ".")) : null;
  const precoAte = f.precoAte ? cents(f.precoAte.replace(",", ".")) : null;
  const desde = f.atualizadoDesde ? new Date(`${f.atualizadoDesde}T00:00:00`).getTime() : null;

  const passa = (p: Row) => {
    if (f.situacao === "ATIVOS" && p.permite_venda === false) return false;
    if (f.situacao === "INATIVOS" && p.permite_venda !== false) return false;
    if (termo) {
      const campos = (x: Row) =>
        f.campo === "NOME"
          ? [x.nome]
          : f.campo === "SKU"
            ? [x.sku]
            : f.campo === "GTIN"
              ? [x.gtin]
              : f.campo === "NCM"
                ? [x.ncm]
                : f.campo === "MARCA"
                  ? [x.marca]
                  : [x.nome, x.sku, x.gtin, x.marca, x.ncm];
      if (!familia(p).some((x) => campos(x).some((v) => normal(v).includes(termo)))) return false;
    }
    if (f.noCanal.length) {
      const s = canais(p);
      const ok = f.noCanal.some((k) => (k === SEM_ANUNCIO ? s.size === 0 : s.has(k)));
      if (!ok) return false;
    }
    if (f.foraCanal.length) {
      const s = canais(p);
      if (f.foraCanal.some((k) => (k === SEM_ANUNCIO ? s.size === 0 : s.has(k)))) return false;
    }
    if (f.categorias.length && !f.categorias.includes(str(p.categoria_id))) return false;
    if (fornecedoresOk) {
      const ids = familia(p).map((x) => str(x.id));
      if (
        !c.produtoFornecedores.some(
          (v) => ids.includes(str(v.produto_id)) && fornecedoresOk.has(str(v.fornecedor_id)),
        )
      )
        return false;
    }
    if (marca && !normal(p.marca).includes(marca)) return false;
    // NCM por começo: "6303" acha o capítulo inteiro de cortinas.
    if (ncm && !familia(p).some((x) => str(x.ncm).startsWith(ncm))) return false;
    if (f.semNcm && str(p.ncm)) return false;
    if (f.tags.length) {
      const t = Array.isArray(p.tags) ? (p.tags as string[]).map(normal) : [];
      if (!f.tags.some((x) => t.includes(normal(x)))) return false;
    }
    if (desde && new Date(str(p.atualizado_em || p.criado_em)).getTime() < desde) return false;
    if (f.tipos.length && !f.tipos.includes(str(p.tipo) || "SIMPLES")) return false;
    if (f.estoque) {
      if (p.controla_estoque === false) return false;
      const d = c.disponivel(p);
      if (f.estoque === "SEM" && d > 0) return false;
      if (f.estoque === "COM" && d <= 0) return false;
      if (f.estoque === "ABAIXO_MINIMO" && d >= Number(p.minimo ?? 0)) return false;
    }
    if (precoDe !== null && cents(p.preco) < precoDe) return false;
    if (precoAte !== null && cents(p.preco) > precoAte) return false;
    for (const k of f.pendencias) {
      if (k === "INCOMPLETO" && p.incompleto !== true) return false;
      if (
        k === "SEM_IMAGEM" &&
        familia(p).some((x) => c.imagens.some((i) => str(i.produto_id) === str(x.id)))
      )
        return false;
      if (k === "SEM_CATEGORIA" && p.categoria_id) return false;
      if (k === "SEM_GTIN" && (p.gtin || p.motivo_sem_gtin)) return false;
      if (k === "SEM_PESO" && Number(p.peso_bruto_kg ?? 0) > 0) return false;
      if (k === "FISCAL" && !problemaFiscal(p)) return false;
    }
    return true;
  };

  const ordenada = lista.filter(passa);
  const data = (v: unknown) => new Date(str(v)).getTime() || 0;
  ordenada.sort((a, b) => {
    switch (f.ordem) {
      case "RECENTES":
        return data(b.criado_em) - data(a.criado_em);
      case "ATUALIZACAO":
        return data(b.atualizado_em) - data(a.atualizado_em);
      case "SKU":
        return str(a.sku).localeCompare(str(b.sku), "pt-BR", { numeric: true });
      case "PRECO":
        return cents(b.preco) - cents(a.preco);
      case "ESTOQUE":
        return c.disponivel(a) - c.disponivel(b);
      default:
        return str(a.nome).localeCompare(str(b.nome), "pt-BR");
    }
  });
  return ordenada;
}

type Chip = { rotulo: string; tirar: (f: FiltroProdutos) => FiltroProdutos };

/** Cada filtro ativo vira um chip com o "x" que tira só ele. */
function chipsDe(f: FiltroProdutos, c: ContextoFiltro): Chip[] {
  const out: Chip[] = [];
  const nomeCanal = (k: string) => (k === SEM_ANUNCIO ? "sem anúncio" : k);
  if (f.busca.trim())
    out.push({ rotulo: `busca: "${f.busca.trim()}"`, tirar: (x) => ({ ...x, busca: "" }) });
  if (f.situacao !== "ATIVOS")
    out.push({
      rotulo: SITUACOES.find(([k]) => k === f.situacao)![1],
      tirar: (x) => ({ ...x, situacao: "ATIVOS" }),
    });
  for (const k of f.noCanal)
    out.push({
      rotulo: `está em: ${nomeCanal(k)}`,
      tirar: (x) => ({ ...x, noCanal: x.noCanal.filter((v) => v !== k) }),
    });
  for (const k of f.foraCanal)
    out.push({
      rotulo: `não está em: ${nomeCanal(k)}`,
      tirar: (x) => ({ ...x, foraCanal: x.foraCanal.filter((v) => v !== k) }),
    });
  for (const k of f.categorias)
    out.push({
      rotulo: `categoria: ${str(c.categorias.find((x) => str(x.id) === k)?.nome) || "?"}`,
      tirar: (x) => ({ ...x, categorias: x.categorias.filter((v) => v !== k) }),
    });
  if (f.fornecedor.trim())
    out.push({
      rotulo: `fornecedor: ${f.fornecedor.trim()}`,
      tirar: (x) => ({ ...x, fornecedor: "" }),
    });
  if (f.marca.trim())
    out.push({ rotulo: `marca: ${f.marca.trim()}`, tirar: (x) => ({ ...x, marca: "" }) });
  if (f.ncm.trim())
    out.push({ rotulo: `NCM: ${f.ncm.trim()}…`, tirar: (x) => ({ ...x, ncm: "" }) });
  if (f.semNcm) out.push({ rotulo: "sem NCM", tirar: (x) => ({ ...x, semNcm: false }) });
  for (const k of f.tags)
    out.push({
      rotulo: `tag: ${k}`,
      tirar: (x) => ({ ...x, tags: x.tags.filter((v) => v !== k) }),
    });
  if (f.atualizadoDesde)
    out.push({
      rotulo: `atualizados desde ${f.atualizadoDesde.split("-").reverse().join("/")}`,
      tirar: (x) => ({ ...x, atualizadoDesde: "" }),
    });
  for (const k of f.tipos)
    out.push({
      rotulo: `tipo: ${TIPOS.find(([v]) => v === k)?.[1] ?? k}`,
      tirar: (x) => ({ ...x, tipos: x.tipos.filter((v) => v !== k) }),
    });
  if (f.estoque)
    out.push({
      rotulo: ESTOQUES.find(([k]) => k === f.estoque)![1].toLowerCase(),
      tirar: (x) => ({ ...x, estoque: "" }),
    });
  if (f.precoDe || f.precoAte)
    out.push({
      rotulo: `preço ${f.precoDe ? `de R$ ${f.precoDe}` : ""}${f.precoAte ? ` até R$ ${f.precoAte}` : ""}`,
      tirar: (x) => ({ ...x, precoDe: "", precoAte: "" }),
    });
  for (const k of f.pendencias)
    out.push({
      rotulo: (PENDENCIAS.find(([v]) => v === k)?.[1] ?? k).toLowerCase(),
      tirar: (x) => ({ ...x, pendencias: x.pendencias.filter((v) => v !== k) }),
    });
  return out;
}

type Sugestao = { rotulo: string; aplicar: (f: FiltroProdutos) => FiltroProdutos };

/**
 * Sugestões enquanto digita: o texto é comparado com canais, categorias,
 * marcas, tags, fornecedores e atalhos conhecidos. Clicar troca a busca de
 * texto por um filtro de verdade. Regra fixa, sem IA.
 */
function sugestoes(texto: string, c: ContextoFiltro): Sugestao[] {
  const t = normal(texto.trim());
  if (t.length < 2) return [];
  const limpa = (f: FiltroProdutos) => ({ ...f, busca: "" });
  const out: Sugestao[] = [];
  const atalhos: [string[], string, (f: FiltroProdutos) => FiltroProdutos][] = [
    [["sem ncm", "ncm"], "Sem NCM", (f) => ({ ...f, semNcm: true })],
    [
      ["sem imagem", "sem foto", "foto"],
      "Sem imagem",
      (f) => ({ ...f, pendencias: [...new Set([...f.pendencias, "SEM_IMAGEM"])] }),
    ],
    [
      ["incompleto", "pendente"],
      "Cadastro incompleto",
      (f) => ({ ...f, pendencias: [...new Set([...f.pendencias, "INCOMPLETO"])] }),
    ],
    [
      ["sem estoque", "zerado", "esgotado"],
      "Sem estoque disponível",
      (f) => ({ ...f, estoque: "SEM" }),
    ],
    [
      ["estoque baixo", "minimo", "repor"],
      "Estoque abaixo do mínimo",
      (f) => ({ ...f, estoque: "ABAIXO_MINIMO" }),
    ],
    [["kit"], "Tipo: kit", (f) => ({ ...f, tipos: [...new Set([...f.tipos, "KIT"])] })],
    [
      ["variacao", "variacoes", "cor", "tamanho"],
      "Tipo: com variações",
      (f) => ({ ...f, tipos: [...new Set([...f.tipos, "VARIACAO"])] }),
    ],
    [
      ["inativo", "inativos", "parado"],
      "Produtos inativos",
      (f) => ({ ...f, situacao: "INATIVOS" }),
    ],
    [
      ["sem anuncio", "nao anunciado", "sem canal"],
      "Sem anúncio em nenhum canal",
      (f) => ({ ...f, noCanal: [SEM_ANUNCIO] }),
    ],
  ];
  for (const [chaves, rotulo, aplicar] of atalhos)
    if (chaves.some((k) => k.startsWith(t) || t.startsWith(k) || t.includes(k)))
      out.push({ rotulo, aplicar: (f) => limpa(aplicar(f)) });
  for (const canal of CANAIS_FILTRO)
    if (normal(canal).includes(t))
      out.push({
        rotulo: `Está no ${canal}`,
        aplicar: (f) => limpa({ ...f, noCanal: [...new Set([...f.noCanal, canal])] }),
      });
  for (const cat of c.categorias)
    if (normal(cat.nome).includes(t))
      out.push({
        rotulo: `Categoria: ${str(cat.nome)}`,
        aplicar: (f) => limpa({ ...f, categorias: [...new Set([...f.categorias, str(cat.id)])] }),
      });
  const marcas = [...new Set(c.produtos.map((p) => str(p.marca)).filter(Boolean))];
  for (const m of marcas)
    if (normal(m).includes(t))
      out.push({ rotulo: `Marca: ${m}`, aplicar: (f) => limpa({ ...f, marca: m }) });
  const tags = [
    ...new Set(c.produtos.flatMap((p) => (Array.isArray(p.tags) ? (p.tags as string[]) : []))),
  ];
  for (const tag of tags)
    if (normal(tag).includes(t))
      out.push({
        rotulo: `Tag: ${tag}`,
        aplicar: (f) => limpa({ ...f, tags: [...new Set([...f.tags, tag])] }),
      });
  for (const fo of c.fornecedores)
    if (normal(`${str(fo.nome)} ${str(fo.fantasia)}`).includes(t))
      out.push({
        rotulo: `Fornecedor: ${str(fo.fantasia) || str(fo.nome)}`,
        aplicar: (f) => limpa({ ...f, fornecedor: str(fo.fantasia) || str(fo.nome) }),
      });
  if (/^\d{4,8}$/.test(t))
    out.push({ rotulo: `NCM começando com ${t}`, aplicar: (f) => limpa({ ...f, ncm: t }) });
  return out.slice(0, 7);
}

const CHAVE_SALVOS = "radar.produtos.filtrosSalvos";
const CHAVE_ULTIMO = "radar.produtos.ultimoFiltro";

export function ler<T>(chave: string, padrao: T): T {
  try {
    const v = window.localStorage.getItem(chave);
    return v ? (JSON.parse(v) as T) : padrao;
  } catch {
    return padrao;
  }
}
export function gravar(chave: string, valor: unknown) {
  try {
    window.localStorage.setItem(chave, JSON.stringify(valor));
  } catch {
    // Navegador sem armazenamento (aba anônima, bloqueio): só não lembra.
  }
}

/** Lê o último filtro usado neste navegador (para abrir a lista como estava). */
export function filtroInicial(): FiltroProdutos {
  if (typeof window === "undefined") return FILTRO_VAZIO;
  return { ...FILTRO_VAZIO, ...ler<Partial<FiltroProdutos>>(CHAVE_ULTIMO, {}) };
}

type Salvo = { nome: string; filtro: FiltroProdutos };

export default function FiltrosProdutos({
  filtro,
  setFiltro,
  contexto,
  principais,
  total,
}: {
  filtro: FiltroProdutos;
  setFiltro: (f: FiltroProdutos) => void;
  contexto: ContextoFiltro;
  principais: Row[];
  total: number;
}) {
  const [aberto, setAberto] = useState<"" | "ordem" | "situacao" | "campo" | "filtros">("");
  const [rascunho, setRascunho] = useState<FiltroProdutos>(filtro);
  const [focoBusca, setFocoBusca] = useState(false);
  const [salvos, setSalvos] = useState<Salvo[]>(() =>
    typeof window === "undefined" ? [] : ler<Salvo[]>(CHAVE_SALVOS, []),
  );
  const raiz = useRef<HTMLDivElement>(null);

  useEffect(() => gravar(CHAVE_ULTIMO, filtro), [filtro]);

  // Clique fora fecha o que estiver aberto (o rascunho do painel é descartado).
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

  const chips = chipsDe(filtro, contexto);
  const lista = focoBusca ? sugestoes(filtro.busca, contexto) : [];
  const previa = aberto === "filtros" ? filtrarProdutos(principais, rascunho, contexto).length : 0;
  const tags = [
    ...new Set(
      contexto.produtos.flatMap((p) => (Array.isArray(p.tags) ? (p.tags as string[]) : [])),
    ),
  ].sort((a, b) => a.localeCompare(b, "pt-BR"));
  const categorias = [...contexto.categorias].sort((a, b) =>
    str(a.nome).localeCompare(str(b.nome), "pt-BR"),
  );

  function abrir(qual: typeof aberto) {
    if (qual === "filtros") setRascunho(filtro);
    setAberto(aberto === qual ? "" : qual);
  }

  function alternar(lista: string[], v: string) {
    return lista.includes(v) ? lista.filter((x) => x !== v) : [...lista, v];
  }

  function salvarAtual() {
    const nome = window.prompt("Nome deste filtro (ex.: Shopee sem NCM):")?.trim();
    if (!nome) return;
    const novos = [...salvos.filter((s) => s.nome !== nome), { nome, filtro }];
    setSalvos(novos);
    gravar(CHAVE_SALVOS, novos);
  }

  function apagarSalvo(nome: string) {
    const novos = salvos.filter((s) => s.nome !== nome);
    setSalvos(novos);
    gravar(CHAVE_SALVOS, novos);
  }

  const marcaCanal = (campo: "noCanal" | "foraCanal", rotulo: string) => (
    <fieldset className="rd-filtro-grupo">
      <legend>{rotulo}</legend>
      <small className="rd-filtro-sub">Marketplace</small>
      {CANAIS_FILTRO.map((k) => (
        <label key={k} className="rd-check">
          <input
            type="checkbox"
            checked={rascunho[campo].includes(k)}
            onChange={() => setRascunho({ ...rascunho, [campo]: alternar(rascunho[campo], k) })}
          />
          {k}
        </label>
      ))}
      <label className="rd-check">
        <input
          type="checkbox"
          checked={rascunho[campo].includes(SEM_ANUNCIO)}
          onChange={() =>
            setRascunho({ ...rascunho, [campo]: alternar(rascunho[campo], SEM_ANUNCIO) })
          }
        />
        Sem integração (sem anúncio)
      </label>
    </fieldset>
  );

  return (
    <div className="rd-filtros-produtos" ref={raiz}>
      <div className="rd-filtros-linha">
        <div className="rd-busca-produtos">
          <input
            aria-label="Buscar produto"
            placeholder="Pesquise por nome, código (SKU) ou GTIN/EAN"
            value={filtro.busca}
            onFocus={() => setFocoBusca(true)}
            onChange={(e) => setFiltro({ ...filtro, busca: e.target.value })}
            onKeyDown={(e) => e.key === "Escape" && setFocoBusca(false)}
          />
          <span className="rd-busca-lupa" aria-hidden="true">
            ⌕
          </span>
          <div className="rd-filtro-pop">
            <button
              type="button"
              className="rd-busca-campo"
              aria-label="Onde procurar"
              title={`Onde procurar: ${CAMPOS.find(([k]) => k === filtro.campo)![1]}`}
              aria-expanded={aberto === "campo"}
              onClick={() => abrir("campo")}
            >
              ⫶ ▾
            </button>
            {aberto === "campo" && (
              <ul role="menu" className="rd-pop-lista">
                <li className="rd-pop-titulo">Procurar em</li>
                {CAMPOS.map(([k, r]) => (
                  <li key={k}>
                    <button
                      role="menuitemradio"
                      aria-checked={filtro.campo === k}
                      className={filtro.campo === k ? "ativo" : ""}
                      onClick={() => {
                        setFiltro({ ...filtro, campo: k });
                        setAberto("");
                      }}
                    >
                      {r}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
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
            ⇅ {ORDENS.find(([k]) => k === filtro.ordem)![1]}
          </button>
          {aberto === "ordem" && (
            <div className="rd-pop-chips">
              <small>Ordenar por</small>
              <div>
                {ORDENS.map(([k, r]) => (
                  <button
                    key={k}
                    className={`rd-chip-filtro${filtro.ordem === k ? " ativo" : ""}`}
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
            className="rd-chip-filtro ativo"
            aria-expanded={aberto === "situacao"}
            onClick={() => abrir("situacao")}
          >
            {SITUACOES.find(([k]) => k === filtro.situacao)![1]}
          </button>
          {aberto === "situacao" && (
            <div className="rd-pop-chips">
              <small>Mostrar</small>
              <div>
                {SITUACOES.map(([k, r]) => (
                  <button
                    key={k}
                    className={`rd-chip-filtro${filtro.situacao === k ? " ativo" : ""}`}
                    onClick={() => {
                      setFiltro({ ...filtro, situacao: k });
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
            <div className="rd-painel-filtros" role="dialog" aria-label="Filtros de produtos">
              <div className="rd-painel-corpo">
                {marcaCanal("noCanal", "Que estão no e-commerce")}
                {marcaCanal("foraCanal", "Que não estão no e-commerce")}
                <fieldset className="rd-filtro-grupo">
                  <legend>Categoria</legend>
                  {categorias.length === 0 && <small>Nenhuma categoria cadastrada.</small>}
                  <div className="rd-filtro-rolagem">
                    {categorias.map((cat) => (
                      <label key={str(cat.id)} className="rd-check">
                        <input
                          type="checkbox"
                          checked={rascunho.categorias.includes(str(cat.id))}
                          onChange={() =>
                            setRascunho({
                              ...rascunho,
                              categorias: alternar(rascunho.categorias, str(cat.id)),
                            })
                          }
                        />
                        {str(cat.nome)}
                      </label>
                    ))}
                  </div>
                </fieldset>
                <label>
                  Fornecedor
                  <input
                    list="rd-lista-fornecedores"
                    placeholder="Razão social ou nome fantasia"
                    value={rascunho.fornecedor}
                    onChange={(e) => setRascunho({ ...rascunho, fornecedor: e.target.value })}
                  />
                  <datalist id="rd-lista-fornecedores">
                    {contexto.fornecedores.map((fo) => (
                      <option key={str(fo.id)} value={str(fo.fantasia) || str(fo.nome)} />
                    ))}
                  </datalist>
                </label>
                <label>
                  Marca
                  <input
                    list="rd-lista-marcas"
                    placeholder="Pesquise pelo nome da marca"
                    value={rascunho.marca}
                    onChange={(e) => setRascunho({ ...rascunho, marca: e.target.value })}
                  />
                  <datalist id="rd-lista-marcas">
                    {[...new Set(contexto.produtos.map((p) => str(p.marca)).filter(Boolean))].map(
                      (m) => (
                        <option key={m} value={m} />
                      ),
                    )}
                  </datalist>
                </label>
                <label>
                  Buscar por NCM
                  <input
                    inputMode="numeric"
                    placeholder="Pesquise pelo NCM (o começo já serve)"
                    value={rascunho.ncm}
                    onChange={(e) =>
                      setRascunho({
                        ...rascunho,
                        ncm: e.target.value.replace(/\D/g, "").slice(0, 8),
                      })
                    }
                  />
                </label>
                <label className="rd-check">
                  <input
                    type="checkbox"
                    checked={rascunho.semNcm}
                    onChange={(e) => setRascunho({ ...rascunho, semNcm: e.target.checked })}
                  />
                  Buscar por produtos sem NCM
                </label>
                <fieldset className="rd-filtro-grupo">
                  <legend>Tags de produtos</legend>
                  {tags.length === 0 && <small>Nenhuma tag usada ainda.</small>}
                  <div className="rd-filtro-tags">
                    {tags.map((t) => (
                      <button
                        key={t}
                        type="button"
                        className={`rd-chip-filtro${rascunho.tags.includes(t) ? " ativo" : ""}`}
                        onClick={() =>
                          setRascunho({ ...rascunho, tags: alternar(rascunho.tags, t) })
                        }
                      >
                        {t}
                      </button>
                    ))}
                  </div>
                </fieldset>
                <label>
                  Produtos atualizados a partir de:
                  <input
                    type="date"
                    value={rascunho.atualizadoDesde}
                    onChange={(e) => setRascunho({ ...rascunho, atualizadoDesde: e.target.value })}
                  />
                </label>
                <fieldset className="rd-filtro-grupo">
                  <legend>Tipo</legend>
                  <div className="rd-filtro-tags">
                    {TIPOS.map(([k, r]) => (
                      <button
                        key={k}
                        type="button"
                        className={`rd-chip-filtro${rascunho.tipos.includes(k) ? " ativo" : ""}`}
                        onClick={() =>
                          setRascunho({ ...rascunho, tipos: alternar(rascunho.tipos, k) })
                        }
                      >
                        {r}
                      </button>
                    ))}
                  </div>
                </fieldset>
                <label>
                  Estoque
                  <select
                    value={rascunho.estoque}
                    onChange={(e) =>
                      setRascunho({
                        ...rascunho,
                        estoque: e.target.value as FiltroProdutos["estoque"],
                      })
                    }
                  >
                    {ESTOQUES.map(([k, r]) => (
                      <option key={k} value={k}>
                        {r}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="rd-filtro-faixa">
                  <label>
                    Preço de (R$)
                    <input
                      inputMode="decimal"
                      placeholder="0,00"
                      value={rascunho.precoDe}
                      onChange={(e) => setRascunho({ ...rascunho, precoDe: e.target.value })}
                    />
                  </label>
                  <label>
                    até (R$)
                    <input
                      inputMode="decimal"
                      placeholder="0,00"
                      value={rascunho.precoAte}
                      onChange={(e) => setRascunho({ ...rascunho, precoAte: e.target.value })}
                    />
                  </label>
                </div>
                <fieldset className="rd-filtro-grupo">
                  <legend>Pendências do cadastro</legend>
                  {PENDENCIAS.map(([k, r]) => (
                    <label key={k} className="rd-check">
                      <input
                        type="checkbox"
                        checked={rascunho.pendencias.includes(k)}
                        onChange={() =>
                          setRascunho({ ...rascunho, pendencias: alternar(rascunho.pendencias, k) })
                        }
                      />
                      {r}
                    </label>
                  ))}
                </fieldset>
              </div>
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
          disabled={chips.length === 0 && filtro.ordem === "NOME"}
          onClick={() => setFiltro({ ...FILTRO_VAZIO })}
        >
          ⊖ limpar filtros
        </button>
      </div>
      {(chips.length > 0 || salvos.length > 0) && (
        <div className="rd-filtros-ativos">
          {chips.length > 0 && (
            <span className="rd-filtros-contagem">
              {total} de {principais.length} produtos
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
              <button type="button" onClick={() => setFiltro({ ...FILTRO_VAZIO, ...s.filtro })}>
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
