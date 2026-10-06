"use client";

// Busca e filtros da lista de anúncios de uma loja (vale para todos os
// marketplaces). Mesma lógica dos filtros de produtos: E entre campos, OU
// dentro de um campo com várias escolhas; chips removíveis; prévia de quantos
// aparecem; sugestões na busca; filtros salvos neste navegador.

import { useEffect, useRef, useState } from "react";
import { gravar, ler, normal } from "./produtos-filtros";
import { cents, str, type Row } from "./ui";

export type FiltroAnuncios = {
  busca: string;
  ordem: "TITULO" | "RECENTES" | "ATUALIZACAO" | "MAIOR_PRECO" | "MENOR_PRECO";
  produto: string;
  situacoes: string[];
  ecommerce: string[];
  alertas: string[];
  origens: string[];
  categorias: string[];
  marca: string;
  precoDe: string;
  precoAte: string;
  precoDiferente: boolean;
  produtoInativo: boolean;
  atualizadoDesde: string;
};

export const ANUNCIOS_VAZIO: FiltroAnuncios = {
  busca: "",
  ordem: "TITULO",
  produto: "",
  situacoes: [],
  ecommerce: [],
  alertas: [],
  origens: [],
  categorias: [],
  marca: "",
  precoDe: "",
  precoAte: "",
  precoDiferente: false,
  produtoInativo: false,
  atualizadoDesde: "",
};

const ORDENS: [FiltroAnuncios["ordem"], string][] = [
  ["TITULO", "título"],
  ["RECENTES", "mais recentes"],
  ["ATUALIZACAO", "data de atualização"],
  ["MAIOR_PRECO", "maior preço"],
  ["MENOR_PRECO", "menor preço"],
];
const SITUACOES: [string, string][] = [
  ["RASCUNHO", "Rascunho"],
  ["SIMULADO", "Simulado"],
  ["PAUSADO", "Pausado"],
];
const ECOMMERCE: [string, string][] = [
  ["NAO_PUBLICADO", "Não publicado"],
  ["ATIVO", "Ativo"],
  ["PAUSADO", "Pausado"],
  ["REJEITADO", "Rejeitado"],
  ["ENCERRADO", "Encerrado"],
];
const ORIGENS: [string, string][] = [
  ["IMPORTACAO", "Importado do marketplace"],
  ["MANUAL", "Criado no Radar"],
];
// Cada alerta do anúncio (ver "Necessitam atenção") vira uma opção de filtro.
const ALERTAS: [string, string, string][] = [
  ["SEM_PRODUTO", "Sem produto vinculado", "Sem produto vinculado"],
  ["INCOMPLETO", "Produto com cadastro incompleto", "Produto com cadastro incompleto"],
  ["SEM_IMAGEM", "Produto sem imagem", "Produto sem imagem"],
  ["SEM_CATEGORIA", "Produto sem categoria", "Produto sem categoria"],
  ["SEM_VINCULO", "Categoria sem vínculo com o marketplace", "Categoria sem vínculo"],
  ["REJEITADO", "Rejeitado pelo marketplace", "Rejeitado pelo marketplace"],
  ["ERRO", "Erro de integração", "Erro de integração"],
];

export type ContextoAnuncios = {
  produtoDe: (id: unknown) => Row | undefined;
  motivos: (a: Row) => string[];
  categorias: Row[];
};

const codigosDeAlerta = (motivos: string[]) =>
  ALERTAS.filter(([, , prefixo]) => motivos.some((m) => m.startsWith(prefixo))).map(([k]) => k);

/** Busca, filtros e ordem sobre os anúncios (a aba já foi aplicada antes). */
export function filtrarAnuncios(lista: Row[], f: FiltroAnuncios, c: ContextoAnuncios): Row[] {
  const termo = normal(f.busca.trim());
  const prod = normal(f.produto.trim());
  const marca = normal(f.marca.trim());
  const precoDe = f.precoDe ? cents(f.precoDe.replace(",", ".")) : null;
  const precoAte = f.precoAte ? cents(f.precoAte.replace(",", ".")) : null;
  const desde = f.atualizadoDesde ? new Date(`${f.atualizadoDesde}T00:00:00`).getTime() : null;

  const passa = (a: Row) => {
    const p = c.produtoDe(a.produto_id);
    // Busca: título no marketplace, número/código do anúncio, SKU do anúncio
    // e, do produto vinculado, nome, SKU e GTIN.
    if (
      termo &&
      ![a.titulo, a.id_externo, a.sku_externo, p?.nome, p?.sku, p?.gtin].some((v) =>
        normal(v).includes(termo),
      )
    )
      return false;
    if (prod && ![p?.nome, p?.sku, p?.gtin].some((v) => normal(v).includes(prod))) return false;
    if (f.situacoes.length && !f.situacoes.includes(str(a.estado))) return false;
    if (f.ecommerce.length && !f.ecommerce.includes(str(a.situacao_ecommerce))) return false;
    if (f.alertas.length) {
      const codigos = codigosDeAlerta(c.motivos(a));
      if (!f.alertas.some((k) => codigos.includes(k))) return false;
    }
    if (f.origens.length && !f.origens.includes(str(a.origem) || "MANUAL")) return false;
    if (f.categorias.length && !f.categorias.includes(str(p?.categoria_id))) return false;
    if (marca && !normal(p?.marca).includes(marca)) return false;
    if (precoDe !== null && cents(a.preco) < precoDe) return false;
    if (precoAte !== null && cents(a.preco) > precoAte) return false;
    if (f.precoDiferente && (!p || cents(a.preco) === cents(p.preco))) return false;
    if (f.produtoInativo && p?.permite_venda !== false) return false;
    if (desde && new Date(str(a.atualizado_em || a.criado_em)).getTime() < desde) return false;
    return true;
  };

  const data = (v: unknown) => new Date(str(v)).getTime() || 0;
  return lista.filter(passa).sort((a, b) => {
    switch (f.ordem) {
      case "RECENTES":
        return data(b.criado_em) - data(a.criado_em);
      case "ATUALIZACAO":
        return data(b.atualizado_em) - data(a.atualizado_em);
      case "MAIOR_PRECO":
        return cents(b.preco) - cents(a.preco);
      case "MENOR_PRECO":
        return cents(a.preco) - cents(b.preco);
      default:
        return str(a.titulo).localeCompare(str(b.titulo), "pt-BR");
    }
  });
}

type Chip = { rotulo: string; tirar: (f: FiltroAnuncios) => FiltroAnuncios };

function chipsDe(f: FiltroAnuncios, c: ContextoAnuncios): Chip[] {
  const out: Chip[] = [];
  const nome = (lista: [string, string, ...string[]][], k: string) =>
    lista.find(([v]) => v === k)?.[1] ?? k;
  if (f.busca.trim())
    out.push({ rotulo: `busca: "${f.busca.trim()}"`, tirar: (x) => ({ ...x, busca: "" }) });
  if (f.produto.trim())
    out.push({ rotulo: `produto: ${f.produto.trim()}`, tirar: (x) => ({ ...x, produto: "" }) });
  for (const k of f.situacoes)
    out.push({
      rotulo: `no Radar: ${nome(SITUACOES, k).toLowerCase()}`,
      tirar: (x) => ({ ...x, situacoes: x.situacoes.filter((v) => v !== k) }),
    });
  for (const k of f.ecommerce)
    out.push({
      rotulo: `no marketplace: ${nome(ECOMMERCE, k).toLowerCase()}`,
      tirar: (x) => ({ ...x, ecommerce: x.ecommerce.filter((v) => v !== k) }),
    });
  for (const k of f.alertas)
    out.push({
      rotulo: nome(ALERTAS, k).toLowerCase(),
      tirar: (x) => ({ ...x, alertas: x.alertas.filter((v) => v !== k) }),
    });
  for (const k of f.origens)
    out.push({
      rotulo: nome(ORIGENS, k).toLowerCase(),
      tirar: (x) => ({ ...x, origens: x.origens.filter((v) => v !== k) }),
    });
  for (const k of f.categorias)
    out.push({
      rotulo: `categoria: ${str(c.categorias.find((x) => str(x.id) === k)?.nome) || "?"}`,
      tirar: (x) => ({ ...x, categorias: x.categorias.filter((v) => v !== k) }),
    });
  if (f.marca.trim())
    out.push({ rotulo: `marca: ${f.marca.trim()}`, tirar: (x) => ({ ...x, marca: "" }) });
  if (f.precoDe || f.precoAte)
    out.push({
      rotulo: `preço ${f.precoDe ? `de R$ ${f.precoDe}` : ""}${f.precoAte ? ` até R$ ${f.precoAte}` : ""}`,
      tirar: (x) => ({ ...x, precoDe: "", precoAte: "" }),
    });
  if (f.precoDiferente)
    out.push({
      rotulo: "preço diferente do produto",
      tirar: (x) => ({ ...x, precoDiferente: false }),
    });
  if (f.produtoInativo)
    out.push({ rotulo: "produto inativo", tirar: (x) => ({ ...x, produtoInativo: false }) });
  if (f.atualizadoDesde)
    out.push({
      rotulo: `atualizados desde ${f.atualizadoDesde.split("-").reverse().join("/")}`,
      tirar: (x) => ({ ...x, atualizadoDesde: "" }),
    });
  return out;
}

type Sugestao = { rotulo: string; aplicar: (f: FiltroAnuncios) => FiltroAnuncios };

/** Texto digitado que bate com uma opção conhecida vira sugestão de filtro. Regra fixa. */
function sugestoes(texto: string, c: ContextoAnuncios, produtos: Row[]): Sugestao[] {
  const t = normal(texto.trim());
  if (t.length < 2) return [];
  const limpa = (f: FiltroAnuncios) => ({ ...f, busca: "" });
  const junta = (lista: string[], v: string) => [...new Set([...lista, v])];
  const out: Sugestao[] = [];
  for (const [k, r] of ECOMMERCE)
    if (normal(r).includes(t))
      out.push({
        rotulo: `No marketplace: ${r.toLowerCase()}`,
        aplicar: (f) => limpa({ ...f, ecommerce: junta(f.ecommerce, k) }),
      });
  for (const [k, r] of SITUACOES)
    if (normal(r).includes(t))
      out.push({
        rotulo: `No Radar: ${r.toLowerCase()}`,
        aplicar: (f) => limpa({ ...f, situacoes: junta(f.situacoes, k) }),
      });
  for (const [k, r] of ALERTAS)
    if (normal(r).includes(t))
      out.push({ rotulo: r, aplicar: (f) => limpa({ ...f, alertas: junta(f.alertas, k) }) });
  const atalhos: [string[], string, (f: FiltroAnuncios) => FiltroAnuncios][] = [
    [
      ["preco diferente", "preco errado", "divergente"],
      "Preço diferente do produto",
      (f) => ({ ...f, precoDiferente: true }),
    ],
    [["inativo", "produto inativo"], "Produto inativo", (f) => ({ ...f, produtoInativo: true })],
    [
      ["importado", "importacao"],
      "Importado do marketplace",
      (f) => ({ ...f, origens: junta(f.origens, "IMPORTACAO") }),
    ],
    [
      ["criado no radar", "manual"],
      "Criado no Radar",
      (f) => ({ ...f, origens: junta(f.origens, "MANUAL") }),
    ],
    [
      ["foto", "imagem"],
      "Produto sem imagem",
      (f) => ({ ...f, alertas: junta(f.alertas, "SEM_IMAGEM") }),
    ],
  ];
  for (const [chaves, rotulo, aplicar] of atalhos)
    if (chaves.some((k) => k.startsWith(t) || t.includes(k)))
      out.push({ rotulo, aplicar: (f) => limpa(aplicar(f)) });
  for (const cat of c.categorias)
    if (normal(cat.nome).includes(t))
      out.push({
        rotulo: `Categoria do produto: ${str(cat.nome)}`,
        aplicar: (f) => limpa({ ...f, categorias: junta(f.categorias, str(cat.id)) }),
      });
  for (const m of [...new Set(produtos.map((p) => str(p.marca)).filter(Boolean))])
    if (normal(m).includes(t))
      out.push({ rotulo: `Marca: ${m}`, aplicar: (f) => limpa({ ...f, marca: m }) });
  return out.slice(0, 7);
}

type Salvo = { nome: string; filtro: FiltroAnuncios };
const CHAVE_SALVOS = "radar.anuncios.filtrosSalvos";

export default function FiltrosAnuncios({
  filtro,
  setFiltro,
  contexto,
  produtos,
  base,
  total,
}: {
  filtro: FiltroAnuncios;
  setFiltro: (f: FiltroAnuncios) => void;
  contexto: ContextoAnuncios;
  produtos: Row[];
  /** Anúncios da aba atual, antes dos filtros (para a prévia e a contagem). */
  base: Row[];
  total: number;
}) {
  const [aberto, setAberto] = useState<"" | "ordem" | "filtros">("");
  const [rascunho, setRascunho] = useState<FiltroAnuncios>(filtro);
  const [focoBusca, setFocoBusca] = useState(false);
  const [salvos, setSalvos] = useState<Salvo[]>(() => ler<Salvo[]>(CHAVE_SALVOS, []));
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

  const chips = chipsDe(filtro, contexto);
  const lista = focoBusca ? sugestoes(filtro.busca, contexto, produtos) : [];
  const previa = aberto === "filtros" ? filtrarAnuncios(base, rascunho, contexto).length : 0;
  const categorias = [...contexto.categorias].sort((a, b) =>
    str(a.nome).localeCompare(str(b.nome), "pt-BR"),
  );
  const alternar = (l: string[], v: string) =>
    l.includes(v) ? l.filter((x) => x !== v) : [...l, v];

  function abrir(qual: typeof aberto) {
    if (qual === "filtros") setRascunho(filtro);
    setAberto(aberto === qual ? "" : qual);
  }

  function salvarAtual() {
    const nome = window.prompt("Nome deste filtro (ex.: Rejeitados sem imagem):")?.trim();
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

  const grupo = (
    rotulo: string,
    campo: "situacoes" | "ecommerce" | "alertas" | "origens",
    opcoes: [string, string, ...string[]][],
  ) => (
    <fieldset className="rd-filtro-grupo">
      <legend>{rotulo}</legend>
      {opcoes.map(([k, r]) => (
        <label key={k} className="rd-check">
          <input
            type="checkbox"
            checked={rascunho[campo].includes(k)}
            onChange={() => setRascunho({ ...rascunho, [campo]: alternar(rascunho[campo], k) })}
          />
          {r}
        </label>
      ))}
    </fieldset>
  );

  return (
    <div className="rd-filtros-produtos" ref={raiz}>
      <div className="rd-filtros-linha">
        <div className="rd-busca-produtos">
          <input
            aria-label="Buscar anúncio"
            placeholder="Pesquise por identificador, título ou nome do produto"
            title="Busca no título e no número do anúncio no marketplace, no SKU do anúncio e no nome, SKU e GTIN do produto vinculado"
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
            className={`rd-chip-filtro${aberto === "filtros" ? " foco" : ""}`}
            aria-expanded={aberto === "filtros"}
            onClick={() => abrir("filtros")}
          >
            ⚲ filtros
            {chips.length > 0 && <span className="rd-chip-num">{chips.length}</span>}
          </button>
          {aberto === "filtros" && (
            <div className="rd-painel-filtros" role="dialog" aria-label="Filtros de anúncios">
              <div className="rd-painel-corpo">
                <label>
                  Produto no Sistema ERP
                  <input
                    placeholder="Descrição, SKU ou GTIN"
                    value={rascunho.produto}
                    onChange={(e) => setRascunho({ ...rascunho, produto: e.target.value })}
                  />
                </label>
                {grupo("Situação (no Radar)", "situacoes", SITUACOES)}
                {grupo("Situação no e-commerce", "ecommerce", ECOMMERCE)}
                {grupo("Precisa de atenção por", "alertas", ALERTAS)}
                {grupo("Origem", "origens", ORIGENS)}
                <fieldset className="rd-filtro-grupo">
                  <legend>Categoria do produto</legend>
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
                  Marca do produto
                  <input
                    list="rd-lista-marcas-anuncio"
                    placeholder="Pesquise pelo nome da marca"
                    value={rascunho.marca}
                    onChange={(e) => setRascunho({ ...rascunho, marca: e.target.value })}
                  />
                  <datalist id="rd-lista-marcas-anuncio">
                    {[...new Set(produtos.map((p) => str(p.marca)).filter(Boolean))].map((m) => (
                      <option key={m} value={m} />
                    ))}
                  </datalist>
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
                  <legend>Conferências</legend>
                  <label className="rd-check">
                    <input
                      type="checkbox"
                      checked={rascunho.precoDiferente}
                      onChange={(e) =>
                        setRascunho({ ...rascunho, precoDiferente: e.target.checked })
                      }
                    />
                    Preço do anúncio diferente do preço do produto
                  </label>
                  <label className="rd-check">
                    <input
                      type="checkbox"
                      checked={rascunho.produtoInativo}
                      onChange={(e) =>
                        setRascunho({ ...rascunho, produtoInativo: e.target.checked })
                      }
                    />
                    Produto inativo no Radar
                  </label>
                </fieldset>
                <label>
                  Anúncios atualizados a partir de:
                  <input
                    type="date"
                    value={rascunho.atualizadoDesde}
                    onChange={(e) => setRascunho({ ...rascunho, atualizadoDesde: e.target.value })}
                  />
                </label>
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
          disabled={chips.length === 0 && filtro.ordem === "TITULO"}
          onClick={() => setFiltro({ ...ANUNCIOS_VAZIO })}
        >
          ⊖ limpar filtros
        </button>
      </div>
      {(chips.length > 0 || salvos.length > 0) && (
        <div className="rd-filtros-ativos">
          {chips.length > 0 && (
            <span className="rd-filtros-contagem">
              {total} de {base.length} anúncios
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
              <button type="button" onClick={() => setFiltro({ ...ANUNCIOS_VAZIO, ...s.filtro })}>
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
