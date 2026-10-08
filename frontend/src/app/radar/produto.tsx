"use client";
/* eslint-disable react/jsx-key -- Table wraps each supplied cell in a keyed td; these arrays are table data, not rendered sibling lists. */

// Cadastro completo de produto, em abas. Tudo é salvo de uma vez pela
// operação produto_salvar; imagens sobem depois que o produto existe.
// O servidor valida e calcula (custo do kit, GTIN, estoque); a tela só
// organiza o preenchimento e mostra o que falta para anunciar.

import { CANAIS, canaisCom, letras, palavras, regraDe } from "./canais";
import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  Badge,
  Empty,
  Table,
  cents,
  centMoney,
  money,
  ordenarVariacoes,
  str,
  type ModalSpec,
  type Row,
} from "./ui";
import { NO_RADAR, verNaCentral } from "./anuncios";

type Valores = Record<string, string | boolean>;
type Par = { nome: string; valor: string };
type LinhaGrade = {
  id?: string;
  atributos: Record<string, string>;
  sku: string;
  preco: string;
  custo: string;
  gtin: string;
  saldo: string;
  permite_venda: boolean;
};

export const ORIGENS: [string, string][] = [
  ["0", "0 - Nacional, exceto as indicadas nos códigos 3, 4, 5 e 8"],
  ["1", "1 - Estrangeira, importação direta"],
  ["2", "2 - Estrangeira, adquirida no mercado interno"],
  ["3", "3 - Nacional, conteúdo de importação acima de 40% e até 70%"],
  ["4", "4 - Nacional, produzida conforme processos produtivos básicos (PPB)"],
  ["5", "5 - Nacional, conteúdo de importação até 40%"],
  ["6", "6 - Estrangeira, importação direta, sem similar nacional (lista CAMEX)"],
  ["7", "7 - Estrangeira, mercado interno, sem similar nacional (lista CAMEX)"],
  ["8", "8 - Nacional, conteúdo de importação acima de 70%"],
];

export const UNIDADES: [string, string][] = [
  ["UN", "Unidade"],
  ["PC", "Peça"],
  ["CX", "Caixa"],
  ["PAR", "Par"],
  ["KIT", "Kit"],
  ["JG", "Jogo"],
  ["RL", "Rolo"],
  ["KG", "Quilograma"],
  ["G", "Grama"],
  ["L", "Litro"],
  ["ML", "Mililitro"],
  ["M", "Metro"],
  ["M2", "Metro quadrado"],
  ["M3", "Metro cúbico"],
];

const MOTIVOS_SEM_GTIN: [string, string][] = [
  ["", "Tenho código de barras"],
  ["PRODUTO_ARTESANAL", "Produto artesanal ou feito sob medida"],
  ["KIT_DA_LOJA", "Kit montado pela loja"],
  ["SEM_CODIGO_DO_FABRICANTE", "O fabricante não fornece código"],
  ["OUTRO", "Outro motivo"],
];

const TIPOS_VARIACAO = [
  "Cor",
  "Tamanho",
  "Voltagem",
  "Potência",
  "Peso",
  "Acabamento",
  "Kit",
  "Modelo",
  "Cobertura",
  "Polegadas",
  "Tipo de estilo",
  "Amperagem",
  "Cor da cobertura",
];

// As mesmas abas da visualização (produto-ver.tsx): cadastro novo, edição e visualização iguais.
const ABAS = [
  ["gerais", "Dados gerais"],
  ["descricao", "Descrição e imagens"],
  ["fiscal", "Fiscal"],
  ["anuncios", "Anúncios"],
  ["variacoes", "Variações / Kit"],
  ["precos", "Preço e promoções"],
  ["custos", "Custo e compras"],
  ["outros", "Fornecedores e observações"],
] as const;

export type Aba = (typeof ABAS)[number][0];

export type DadosProduto = {
  produtos: Row[];
  categorias: Row[];
  embalagens: Row[];
  fornecedores: Row[];
  anuncios: Row[];
  lojas: Row[];
  kitItens: Row[];
  produtoFornecedores: Row[];
  imagens: Row[];
};

type Props = {
  produto: Row | null;
  dados: DadosProduto;
  veCusto: boolean;
  podeAnunciar: boolean;
  /** Abre o passo a passo "Anunciar" para este produto. */
  anunciar: (id: string) => void;
  executar: (corpo: Record<string, unknown>) => Promise<Record<string, unknown> | null>;
  recarregar: () => Promise<void>;
  abrirModal: (m: ModalSpec) => void;
  voltar: () => void;
  /** Mensagem do último comando recusado (para abrir a aba do campo com problema). */
  erroDoServidor?: () => string;
  // Chamado após salvar: o pai recarrega a tela com o produto atualizado.
  aoSalvar: (id: string, aba: Aba) => void;
  abaInicial?: Aba;
  /** Configurações do cadastro de produtos (SKU automático e valores padrão). */
  config?: Record<string, unknown>;
};

const CAMPOS_TEXTO = [
  "nome",
  "sku",
  "gtin",
  "motivo_sem_gtin",
  "marca",
  "modelo",
  "condicao",
  "unidade",
  "origem",
  "ncm",
  "cest",
  "linha_produto",
  "descricao",
  "preco",
  "preco_promocional",
  "custo",
  "unidades_por_caixa",
  "garantia_tipo",
  "garantia_meses",
  "minimo",
  "maximo",
  "dias_preparacao",
  "largura_cm",
  "altura_cm",
  "comprimento_cm",
  "peso_liquido_kg",
  "peso_bruto_kg",
  "volumes",
  "formato_embalagem",
  "embalagem_id",
  "gtin_tributavel",
  "unidade_tributavel",
  "fator_conversao",
  "ipi_codigo_enquadramento",
  "ipi_enquadramento_legal",
  "ipi_valor_fixo",
  "ex_tipi",
  "is_aliquota_especifica",
  "qtd_monofasia",
  "qtd_monofasia_retencao",
  "keywords",
  "descricao_seo",
  "video_url",
  "observacoes_internas",
];

function valoresIniciais(p: Row | null, cfg: Record<string, unknown> = {}): Valores {
  const v: Valores = {
    tipo: str(p?.tipo) || "SIMPLES",
    controla_estoque: p ? p.controla_estoque !== false : true,
    sob_encomenda: p ? p.sob_encomenda === true : false,
    permite_venda: p ? p.permite_venda !== false : true,
    saldo: "0",
  };
  for (const c of CAMPOS_TEXTO) v[c] = p?.[c] == null ? "" : str(p[c]);
  if (!p) {
    // Valores padrão de Configurações → cadastros → Configurações do cadastro de produtos.
    v.unidade = str(cfg.unidade_padrao) || "UN";
    v.ncm = str(cfg.ncm_padrao);
    v.condicao = "NOVO";
    v.formato_embalagem = "PACOTE_CAIXA";
    v.volumes = "1";
    v.origem = str(cfg.origem_padrao) || "0";
    v.minimo = "5";
  }
  return v;
}

function lista<T>(v: unknown): T[] {
  if (Array.isArray(v)) return v as T[];
  if (typeof v === "string" && v.startsWith("[")) {
    try {
      return JSON.parse(v) as T[];
    } catch {
      return [];
    }
  }
  return [];
}

function slug(texto: string) {
  return texto
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "")
    .toUpperCase()
    .slice(0, 12);
}

/** Aba onde fica o campo citado na mensagem de erro do servidor (para abrir direto nela). */
function abaDoErro(mensagem: string): Aba | null {
  if (/NCM|CEST|GTIN|código de barras|origem/i.test(mensagem)) return "fiscal";
  if (/variação/i.test(mensagem)) return "variacoes";
  if (/promocional|Preço de venda/i.test(mensagem)) return "precos";
  if (/Descrição|imagem/i.test(mensagem)) return "descricao";
  if (/^Custo|em Custo/.test(mensagem)) return "custos";
  if (/Observaç|fornecedor/i.test(mensagem)) return "outros";
  if (/Nome|SKU|Peso|Largura|Altura|Comprimento|Estoque|Marca|Modelo|Medida/i.test(mensagem))
    return "gerais";
  return null;
}

function Campo({
  rotulo,
  dica,
  largo,
  obrigatorio,
  grupo,
  children,
}: {
  rotulo: string;
  dica?: string;
  largo?: boolean;
  /** Vários controles (rádios, select + número): grupo nomeado, não um rótulo só do primeiro. */
  grupo?: boolean;
  // Exigido pela nota fiscal ou pelos marketplaces: sem ele o produto fica como rascunho.
  obrigatorio?: boolean;
  children: ReactNode;
}) {
  const conteudo = (
    <>
      <span>
        {rotulo}
        {obrigatorio && <b className="rd-obrigatorio"> *</b>}
      </span>
      {children}
      {dica && <small className="rd-dica">{dica}</small>}
    </>
  );
  return grupo ? (
    <div role="group" aria-label={rotulo} className={`rd-campo-grupo${largo ? " wide" : ""}`}>
      {conteudo}
    </div>
  ) : (
    <label className={largo ? "wide" : ""}>{conteudo}</label>
  );
}

export default function ProdutoForm({
  produto,
  dados,
  veCusto,
  podeAnunciar,
  anunciar,
  executar,
  recarregar,
  abrirModal,
  voltar,
  erroDoServidor,
  aoSalvar,
  abaInicial = "gerais",
  config = {},
}: Props) {
  const novo = !produto;
  const id = produto ? str(produto.id) : null;
  const skuAutomatico = str(config.sku_modo || "MANUAL") !== "MANUAL";
  const [aba, setAba] = useState<Aba>(abaInicial);
  const [markupTexto, setMarkupTexto] = useState("");
  const [clonando, setClonando] = useState<{ imagens: boolean } | null>(null);
  const [v, setV] = useState<Valores>(() => valoresIniciais(produto, config));
  const [categoriaTexto, setCategoriaTexto] = useState(() =>
    str(dados.categorias.find((c) => c.id === produto?.categoria_id)?.nome),
  );
  const [embalagemNova, setEmbalagemNova] = useState<Valores | null>(null);
  const [tags, setTags] = useState(() => lista<string>(produto?.tags).join(", "));
  const [atributos, setAtributos] = useState<Par[]>(() => lista<Par>(produto?.atributos));
  const [campos, setCampos] = useState<Par[]>(() => lista<Par>(produto?.campos_adicionais));
  const [tiposVariacao, setTiposVariacao] = useState<string[]>(() =>
    lista<string>(produto?.tipos_variacao),
  );
  const filhas = produto ? dados.produtos.filter((p) => p.pai_id === produto.id) : [];
  const [valoresVariacao, setValoresVariacao] = useState<Record<string, string>>(() => {
    const out: Record<string, string> = {};
    for (const t of lista<string>(produto?.tipos_variacao)) {
      const vistos = new Set<string>();
      for (const f of filhas) {
        const a = (f.atributos_variacao ?? {}) as Record<string, string>;
        if (a[t]) vistos.add(a[t]);
      }
      out[t] = [...vistos].join(", ");
    }
    return out;
  });
  const [grade, setGrade] = useState<LinhaGrade[]>(() =>
    ordenarVariacoes(filhas, lista<string>(produto?.tipos_variacao)).map((f) => ({
      id: str(f.id),
      atributos: (f.atributos_variacao ?? {}) as Record<string, string>,
      sku: str(f.sku),
      preco: str(f.preco),
      custo: f.custo == null ? "" : str(f.custo),
      gtin: str(f.gtin),
      saldo: "",
      permite_venda: f.permite_venda !== false,
    })),
  );
  const [kit, setKit] = useState<{ componente_id: string; quantidade: string }[]>(() =>
    produto
      ? dados.kitItens
          .filter((k) => k.kit_id === produto.id)
          .map((k) => ({ componente_id: str(k.componente_id), quantidade: str(k.quantidade) }))
      : [],
  );
  const [fornecedores, setFornecedores] = useState<{ fornecedor_id: string; codigo: string }[]>(
    () =>
      produto
        ? dados.produtoFornecedores
            .filter((f) => f.produto_id === produto.id)
            .map((f) => ({
              fornecedor_id: str(f.fornecedor_id),
              codigo: str(f.codigo_no_fornecedor),
            }))
        : [],
  );
  const [salvando, setSalvando] = useState(false);
  // Foto do formulário ao abrir, para saber se há alteração não salva.
  const inicial = useRef<string | null>(null);
  const lidoEm = useRef(str(produto?.atualizado_em));
  const salvo = useRef(false);
  const [erro, setErro] = useState("");
  const [alvoImagem, setAlvoImagem] = useState("");

  const tipo = str(v.tipo);
  const set =
    (campo: string) =>
    (e: { target: { value: string; validity?: ValidityState; closest?: Element["closest"] } }) => {
      // Campo de número com letra: o navegador apaga o valor sem avisar; aqui avisa.
      if (e.target.validity?.badInput) {
        const rotulo = e.target.closest?.("label")?.querySelector("span")?.textContent ?? "";
        setErro(`Use só números${rotulo ? ` em “${rotulo.replace(" *", "")}”` : ""}.`);
      }
      setV((atual) => ({ ...atual, [campo]: e.target.value }));
    };
  const marca = (campo: string) => (e: { target: { checked: boolean } }) =>
    setV((atual) => ({ ...atual, [campo]: e.target.checked }));
  const entrada = (campo: string, props: Record<string, unknown> = {}) => (
    <input id={`produto-${campo}`} value={str(v[campo])} onChange={set(campo)} {...props} />
  );

  // Itens que podem entrar num kit: simples ou variações, nunca kits nem produtos pai.
  const componentesPossiveis = dados.produtos.filter(
    (p) => p.tipo === "SIMPLES" && p.id !== produto?.id,
  );
  const custoKit = kit.reduce((soma, k) => {
    const c = dados.produtos.find((p) => p.id === k.componente_id);
    return soma + (c ? cents(c.custo) * Number(k.quantidade || 0) : 0);
  }, 0);
  const custoAtual = tipo === "KIT" ? custoKit : cents(v.custo);
  const markup = custoAtual > 0 && cents(v.preco) > 0 ? cents(v.preco) / custoAtual : null;

  const imagensDo = (produtoId: string | null) =>
    produtoId ? dados.imagens.filter((i) => i.produto_id === produtoId) : [];
  const [avisoCentral, setAvisoCentral] = useState("");
  const anunciosDoProduto = produto
    ? dados.anuncios.filter(
        (a) => a.produto_id === produto.id || filhas.some((f) => f.id === a.produto_id),
      )
    : [];

  function gerarGrade() {
    const tipos = tiposVariacao.filter(Boolean);
    if (!tipos.length) {
      setErro("Escolha ao menos um tipo de variação.");
      return;
    }
    let combinacoes: Record<string, string>[] = [{}];
    for (const t of tipos) {
      const valores = (valoresVariacao[t] ?? "")
        .split(",")
        .map((x) => x.trim())
        .filter(Boolean);
      if (!valores.length) {
        setErro(`Informe os valores de ${t}, separados por vírgula.`);
        return;
      }
      combinacoes = combinacoes.flatMap((c) => valores.map((val) => ({ ...c, [t]: val })));
    }
    if (combinacoes.length > 300) {
      setErro("A grade passaria de 300 variações. Reduza os valores.");
      return;
    }
    setErro("");
    const chave = (a: Record<string, string>) =>
      tipos.map((t) => (a[t] ?? "").toLowerCase()).join("|");
    setGrade(
      combinacoes.map((a) => {
        const existente = grade.find((g) => chave(g.atributos) === chave(a));
        return (
          existente ?? {
            atributos: a,
            // Sem SKU no principal (SKU automático), o Radar gera o da variação ao salvar.
            sku: str(v.sku) ? [str(v.sku), ...tipos.map((t) => slug(a[t]))].join("-") : "",
            preco: "",
            custo: "",
            gtin: "",
            saldo: "0",
            permite_venda: true,
          }
        );
      }),
    );
  }

  function corpo(): Record<string, unknown> {
    const base: Record<string, unknown> = { op: "produto_salvar", ...v };
    if (id) base.id = id;
    // Quando o formulário abriu: o servidor recusa se outra pessoa salvou depois.
    if (id) base.versao_lida = lidoEm.current;
    if (!novo) delete base.saldo;
    if (embalagemNova) {
      base.embalagem_id = "";
      base.embalagem_nova = embalagemNova;
    }
    const categoria = dados.categorias.find(
      (c) => str(c.nome).toLowerCase() === categoriaTexto.trim().toLowerCase(),
    );
    base.categoria_id = categoria ? str(categoria.id) : "";
    base.categoria_nome = categoria ? "" : categoriaTexto.trim();
    base.tags = tags
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean);
    base.atributos = atributos;
    base.campos_adicionais = campos;
    base.fornecedores = fornecedores.filter((f) => f.fornecedor_id);
    if (tipo === "KIT") base.kit = kit.filter((k) => k.componente_id);
    if (tipo === "VARIACAO") {
      base.tipos_variacao = tiposVariacao.filter(Boolean);
      base.variacoes = grade.map((g) => {
        const linha: Record<string, unknown> = { ...g };
        if (g.id) delete linha.saldo;
        return linha;
      });
    }
    return base;
  }

  // Mesma regra do servidor (nota fiscal + marketplaces). Não impede salvar: o produto fica
  // como rascunho e a lista leva direto à aba de cada pendência.
  function faltando(): [string, Aba][] {
    const f: [string, Aba][] = [];
    const vazio = (k: string) => !str(v[k]).trim();
    if (vazio("gtin") && vazio("motivo_sem_gtin"))
      f.push(["Código de barras ou motivo de não ter", "gerais"]);
    if (vazio("marca")) f.push(["Marca", "gerais"]);
    if (vazio("origem")) f.push(["Origem (ICMS)", "fiscal"]);
    if (vazio("ncm")) f.push(["NCM", "fiscal"]);
    if (!categoriaTexto.trim()) f.push(["Categoria", "gerais"]);
    if (vazio("descricao")) f.push(["Descrição", "descricao"]);
    if (!(cents(v.preco) > 0)) f.push(["Preço de venda", "precos"]);
    if (vazio("peso_bruto_kg")) f.push(["Peso bruto", "gerais"]);
    const medidas = !vazio("largura_cm") && !vazio("altura_cm") && !vazio("comprimento_cm");
    if (!medidas && !v.embalagem_id && !embalagemNova)
      f.push(["Medidas (largura, altura e comprimento) ou embalagem", "gerais"]);
    return f;
  }

  async function salvar() {
    setErro("");
    if (!str(v.nome).trim()) {
      setErro("Dê um nome ao produto para salvar.");
      setAba("gerais");
      return;
    }
    if (!skuAutomatico && !str(v.sku).trim()) {
      setErro(
        "Informe o código (SKU). Para o Radar gerar sozinho, ligue o SKU automático em Configurações → cadastros.",
      );
      setAba("gerais");
      return;
    }
    setSalvando(true);
    try {
      const r = await executar(corpo());
      if (r) {
        salvo.current = true;
        aoSalvar(str(r.id), aba);
      } else {
        const destino = abaDoErro(erroDoServidor?.() ?? "");
        if (destino) setAba(destino);
      }
    } finally {
      setSalvando(false);
    }
  }

  async function enviarImagens(arquivos: FileList | null) {
    if (!arquivos || !id) return;
    const destino = alvoImagem || id;
    setErro("");
    for (const arquivo of Array.from(arquivos)) {
      if (arquivo.size > 2 * 1024 * 1024) {
        setErro(
          `${arquivo.name} tem ${(arquivo.size / 1048576).toLocaleString("pt-BR", {
            maximumFractionDigits: 1,
          })} MB (o limite é 2 MB). Reduza a imagem e tente de novo.`,
        );
        continue;
      }
      const form = new FormData();
      form.append("arquivo", arquivo);
      const res = await fetch(`/api/radar/produtos/${destino}/imagens`, {
        method: "POST",
        credentials: "include",
        headers: { "X-Radar-Request": "1" },
        body: form,
      });
      if (!res.ok) {
        const corpoErro = await res.json().catch(() => ({}));
        setErro(corpoErro.mensagem ?? `Não foi possível enviar ${arquivo.name}.`);
      }
    }
    await recarregar();
  }

  async function acaoImagem(imagemId: string, acao: "remover" | "principal") {
    const res = await fetch(
      acao === "remover"
        ? `/api/radar/imagens/${imagemId}`
        : `/api/radar/imagens/${imagemId}/principal`,
      {
        method: acao === "remover" ? "DELETE" : "POST",
        credentials: "include",
        headers: { "X-Radar-Request": "1" },
      },
    );
    if (!res.ok) setErro("Não foi possível alterar a imagem.");
    await recarregar();
  }

  // Conferência por marketplace: o cadastro completo (mesma regra que trava o anúncio e a nota) e
  // as regras de cada marketplace (canais.ts). O título conferido aqui é o nome do produto; no
  // "Anunciar" dá para usar outro título em cada loja.
  const totalImagens = imagensDo(id).length;
  const temPacote =
    !!v.embalagem_id || !!embalagemNova || (!!v.largura_cm && !!v.altura_cm && !!v.comprimento_cm);
  const comum: [string, boolean][] = [
    ["Preço de venda", cents(v.preco) > 0],
    ["Descrição", !!str(v.descricao).trim()],
    ["Peso bruto", !!v.peso_bruto_kg],
    ["Medidas da embalagem ou do produto", temPacote],
    ["Código de barras ou motivo para não ter", !!v.gtin || !!v.motivo_sem_gtin],
    ["Marca", !!str(v.marca).trim()],
    ["Categoria", !!categoriaTexto.trim()],
  ];
  const porCanal: Record<string, [string, boolean][]> = Object.fromEntries(
    CANAIS.map((canal) => {
      const r = regraDe(canal);
      const titulo = letras(v.nome);
      const lista: [string, boolean][] = [
        [`Pelo menos ${r.imagensMin} imagem(ns)`, totalImagens >= r.imagensMin],
        ...comum,
      ];
      if (r.tituloMax || r.tituloMin > 1)
        lista.push([
          r.tituloMax
            ? `Título de ${r.tituloMin} a ${r.tituloMax} letras`
            : `Título com pelo menos ${r.tituloMin} letras`,
          titulo >= r.tituloMin && (!r.tituloMax || titulo <= r.tituloMax),
        ]);
      if (r.descricaoMinPalavras)
        lista.push([
          `Descrição com ${r.descricaoMinPalavras} palavras ou mais`,
          palavras(v.descricao) >= r.descricaoMinPalavras,
        ]);
      if (r.descricaoMax)
        lista.push([
          `Descrição com até ${r.descricaoMax.toLocaleString("pt-BR")} letras`,
          str(v.descricao).length <= r.descricaoMax,
        ]);
      return [canal, lista];
    }),
  );

  // Pedaços do formulário, montados abaixo nas abas (as mesmas da visualização).
  const fiscalBase = (
    <div className="rd-form-grid">
      <Campo rotulo="Origem do produto conforme ICMS" obrigatorio largo>
        <select id="produto-origem" value={str(v.origem)} onChange={set("origem")}>
          {ORIGENS.map(([valor, rotulo]) => (
            <option key={valor} value={valor}>
              {rotulo}
            </option>
          ))}
        </select>
      </Campo>
      <Campo rotulo="NCM - Nomenclatura Comum do Mercosul" obrigatorio dica="Ex.: 6303.12.00">
        {entrada("ncm", { maxLength: 10, inputMode: "numeric" })}
      </Campo>
      <Campo rotulo="Código CEST" dica="Ex.: 10.045.01">
        {entrada("cest", { maxLength: 9, inputMode: "numeric" })}
      </Campo>
    </div>
  );
  const descricaoTexto = (
    <div className="rd-form-grid">
      <Campo rotulo="Descrição" obrigatorio largo>
        <textarea
          id="produto-descricao"
          rows={6}
          maxLength={20000}
          value={str(v.descricao)}
          onChange={set("descricao")}
        />
        <small className="rd-dica">
          {str(v.descricao).length.toLocaleString("pt-BR")} / 20.000 caracteres
        </small>
      </Campo>
    </div>
  );
  const custoCampo = (
    <div className="rd-form-grid">
      {veCusto && (
        <Campo
          rotulo="Custo (R$)"
          dica={tipo === "KIT" ? "Calculado pela soma dos componentes" : undefined}
        >
          {tipo === "KIT" ? (
            <input value={centMoney(custoKit)} disabled />
          ) : (
            entrada("custo", { type: "number", step: "0.01", min: "0" })
          )}
        </Campo>
      )}
    </div>
  );
  const outrosCampos = (
    <div className="rd-form-grid">
      <Campo rotulo="Unidades por caixa (itens por embalagem)">
        {entrada("unidades_por_caixa", { type: "number", min: "1", step: "1" })}
      </Campo>
      <Campo rotulo="Garantia" grupo>
        <div className="rd-inline">
          <select
            id="produto-garantia_tipo"
            value={str(v.garantia_tipo)}
            onChange={set("garantia_tipo")}
          >
            <option value="">Não informada</option>
            <option value="VENDEDOR">Do vendedor</option>
            <option value="FABRICANTE">De fábrica</option>
            <option value="SEM_GARANTIA">Sem garantia</option>
          </select>
          {entrada("garantia_meses", {
            type: "number",
            min: "0",
            step: "1",
            placeholder: "meses",
            "aria-label": "Meses de garantia",
          })}
        </div>
      </Campo>
      <label className="rd-check">
        <input
          type="checkbox"
          checked={v.permite_venda === true}
          onChange={marca("permite_venda")}
        />
        Permitir inclusão nas vendas
      </label>
      {produto && (
        <Campo rotulo="Data de criação">
          <input value={new Date(str(produto.criado_em)).toLocaleString("pt-BR")} disabled />
        </Campo>
      )}
    </div>
  );
  const estoqueBloco = (
    <>
      <h3 className="rd-secao">Estoque</h3>
      <div className="rd-form-grid">
        {novo && tipo === "SIMPLES" ? (
          <Campo
            rotulo="Estoque inicial"
            dica="Só no cadastro. Depois, o estoque muda pela tela de Estoque ou por compras."
          >
            {entrada("saldo", { type: "number", min: "0", step: "1" })}
          </Campo>
        ) : (
          <Campo rotulo="Estoque">
            <input
              value={
                tipo === "KIT"
                  ? "Calculado pelos componentes"
                  : tipo === "VARIACAO"
                    ? "Em cada variação"
                    : `${Number(produto?.fisico ?? 0) - Number(produto?.reservado ?? 0)} disponíveis (altere na tela de Estoque)`
              }
              disabled
            />
          </Campo>
        )}
        <label className="rd-check">
          <input
            type="checkbox"
            checked={v.controla_estoque === true}
            onChange={marca("controla_estoque")}
          />
          Controlar estoque
        </label>
        <Campo rotulo="Estoque mínimo">
          {entrada("minimo", { type: "number", min: "0", step: "1" })}
        </Campo>
        <Campo rotulo="Estoque máximo">
          {entrada("maximo", { type: "number", min: "0", step: "1" })}
        </Campo>
        <label className="rd-check">
          <input
            type="checkbox"
            checked={v.sob_encomenda === true}
            onChange={marca("sob_encomenda")}
          />
          Sob encomenda
        </label>
        <Campo rotulo="Dias para preparação">
          {entrada("dias_preparacao", { type: "number", min: "0", max: "90", step: "1" })}
        </Campo>
      </div>
    </>
  );
  const prontoBloco = (
    <>
      <section className="rd-card rd-pronto">
        <h3>Pronto para anunciar?</h3>
        <p className="rd-note">
          Requisitos comuns de cada canal. As regras exatas variam por categoria do marketplace.
        </p>
        <div className="rd-pronto-grade">
          {CANAIS.map((canal) => {
            const itens = porCanal[canal];
            const faltam = itens.filter(([, ok]) => !ok);
            return (
              <div key={canal}>
                <strong>{canal}</strong>
                <Badge tone={faltam.length ? "amber" : "green"}>
                  {faltam.length ? `Faltam ${faltam.length}` : "Completo"}
                </Badge>
                <ul>
                  {itens.map(([rotulo, ok]) => (
                    <li key={rotulo} className={ok ? "ok" : "falta"}>
                      {ok ? "✓" : "○"} {rotulo}
                    </li>
                  ))}
                </ul>
              </div>
            );
          })}
        </div>
      </section>
    </>
  );

  const blocos: Record<string, ReactNode> = {
    geral: (
      <div className="rd-form-grid">
        <Campo rotulo="Tipo do produto" largo grupo>
          <div className="rd-opcoes">
            {[
              ["SIMPLES", "Simples"],
              ["KIT", "Kit"],
              ["VARIACAO", "Com variação"],
            ].map(([valor, rotulo]) => (
              <label key={valor} className={tipo === valor ? "ativo" : ""}>
                <input
                  type="radio"
                  name="produto-tipo"
                  value={valor}
                  checked={tipo === valor}
                  disabled={!novo}
                  onChange={set("tipo")}
                />
                {rotulo}
              </label>
            ))}
          </div>
          {!novo && <small className="rd-dica">O tipo não muda depois de salvo.</small>}
        </Campo>
        <Campo rotulo="Nome do produto" obrigatorio largo>
          {entrada("nome", { maxLength: 250 })}
        </Campo>
        <Campo
          rotulo="Código (SKU)"
          obrigatorio={!skuAutomatico}
          dica={skuAutomatico && !str(v.sku) ? "Em branco, o Radar gera ao salvar." : undefined}
        >
          {entrada("sku", {
            maxLength: 80,
            placeholder: skuAutomatico ? "Automático" : undefined,
          })}
        </Campo>
        <Campo
          rotulo="Código de barras (GTIN/EAN)"
          obrigatorio={!v.motivo_sem_gtin}
          dica="8, 12, 13 ou 14 dígitos. Sem código, escolha o motivo ao lado."
        >
          {entrada("gtin", { inputMode: "numeric", maxLength: 14 })}
        </Campo>
        {!v.gtin && (
          <Campo rotulo="Sem código de barras? Motivo" obrigatorio>
            <select
              id="produto-motivo_sem_gtin"
              value={str(v.motivo_sem_gtin)}
              onChange={set("motivo_sem_gtin")}
            >
              {MOTIVOS_SEM_GTIN.map(([valor, rotulo]) => (
                <option key={valor} value={valor}>
                  {rotulo}
                </option>
              ))}
            </select>
          </Campo>
        )}
        <Campo rotulo="Marca" obrigatorio>
          {entrada("marca", { maxLength: 120 })}
        </Campo>
        <Campo rotulo="Modelo">{entrada("modelo", { maxLength: 120 })}</Campo>
        <Campo rotulo="Condição">
          <select id="produto-condicao" value={str(v.condicao)} onChange={set("condicao")}>
            <option value="NOVO">Novo</option>
            <option value="USADO">Usado</option>
            <option value="RECONDICIONADO">Recondicionado</option>
          </select>
        </Campo>
        <Campo rotulo="Unidade">
          <select id="produto-unidade" value={str(v.unidade)} onChange={set("unidade")}>
            {UNIDADES.map(([valor, rotulo]) => (
              <option key={valor} value={valor}>
                {valor} - {rotulo}
              </option>
            ))}
          </select>
        </Campo>
        <Campo
          rotulo="Categoria"
          obrigatorio
          dica="Escolha uma existente ou digite uma nova; ela entra no cadastro de categorias."
        >
          <input
            id="produto-categoria"
            list="produto-categorias"
            value={categoriaTexto}
            onChange={(e) => setCategoriaTexto(e.target.value)}
          />
          <datalist id="produto-categorias">
            {dados.categorias.map((c) => (
              <option key={str(c.id)} value={str(c.nome)} />
            ))}
          </datalist>
        </Campo>
        <Campo rotulo="Linha de produto">{entrada("linha_produto", { maxLength: 120 })}</Campo>
      </div>
    ),
    preco: (
      <>
        <div className="rd-form-grid">
          <Campo rotulo="Preço de venda (R$)" obrigatorio>
            {entrada("preco", { type: "number", step: "0.01", min: "0" })}
          </Campo>
          <Campo rotulo="Preço promocional (R$)" dica="Opcional">
            {entrada("preco_promocional", { type: "number", step: "0.01", min: "0" })}
          </Campo>
          {veCusto && (
            <Campo rotulo="Markup" dica="Preço de venda ÷ custo" grupo>
              <div className="rd-inline">
                <input value={markup ? markup.toFixed(2).replace(".", ",") + "×" : "—"} disabled />
                <input
                  id="produto-markup-novo"
                  aria-label="Markup desejado"
                  inputMode="decimal"
                  placeholder="Ex.: 2,5"
                  value={markupTexto}
                  onChange={(e) => setMarkupTexto(e.target.value)}
                />
                <button
                  type="button"
                  disabled={custoAtual <= 0 || !(Number(markupTexto.replace(",", ".")) > 0)}
                  onClick={() => {
                    const fator = Number(markupTexto.replace(",", "."));
                    // Preço sugerido = custo × markup, arredondado ao centavo.
                    setV((a) => ({
                      ...a,
                      preco: (Math.round(custoAtual * fator) / 100).toFixed(2),
                    }));
                  }}
                >
                  Aplicar
                </button>
              </div>
            </Campo>
          )}
        </div>
      </>
    ),
    dimensoes: (
      <>
        <h3 className="rd-secao">Dimensões e peso do produto (sem a embalagem)</h3>
        <p className="rd-dica">
          * Obrigatório: as três medidas do produto <strong>ou</strong> uma embalagem de envio
          (abaixo), e o peso bruto. O marketplace usa isso para calcular o frete.
        </p>
        <div className="rd-form-grid tres">
          <Campo rotulo="Largura (cm)">
            {entrada("largura_cm", { type: "number", step: "0.1", min: "0" })}
          </Campo>
          <Campo rotulo="Altura (cm)">
            {entrada("altura_cm", { type: "number", step: "0.1", min: "0" })}
          </Campo>
          <Campo rotulo="Comprimento (cm)">
            {entrada("comprimento_cm", { type: "number", step: "0.1", min: "0" })}
          </Campo>
          <Campo rotulo="Peso líquido (kg)">
            {entrada("peso_liquido_kg", { type: "number", step: "0.001", min: "0" })}
          </Campo>
          <Campo rotulo="Peso bruto (kg)" obrigatorio dica="Com embalagem; usado no frete">
            {entrada("peso_bruto_kg", { type: "number", step: "0.001", min: "0" })}
          </Campo>
          <Campo rotulo="Nº de volumes">
            {entrada("volumes", { type: "number", step: "1", min: "1" })}
          </Campo>
        </div>
        <h3 className="rd-secao">Embalagem de envio</h3>
        <div className="rd-form-grid">
          <Campo rotulo="Tipo da embalagem">
            <select
              id="produto-formato_embalagem"
              value={str(v.formato_embalagem)}
              onChange={set("formato_embalagem")}
            >
              <option value="PACOTE_CAIXA">Pacote / Caixa</option>
              <option value="ROLO_CILINDRO">Rolo / Cilindro</option>
              <option value="ENVELOPE">Envelope</option>
            </select>
          </Campo>
          <Campo rotulo="Embalagem">
            <select
              id="produto-embalagem"
              value={embalagemNova ? "__nova" : str(v.embalagem_id)}
              onChange={(e) => {
                if (e.target.value === "__nova") {
                  setEmbalagemNova({
                    nome: "",
                    largura_cm: "",
                    altura_cm: "",
                    comprimento_cm: "",
                    custo: "",
                  });
                  setV((a) => ({ ...a, embalagem_id: "" }));
                } else {
                  setEmbalagemNova(null);
                  setV((a) => ({ ...a, embalagem_id: e.target.value }));
                }
              }}
            >
              <option value="">Nenhuma</option>
              {dados.embalagens.map((e) => (
                <option key={str(e.id)} value={str(e.id)}>
                  {str(e.nome)}
                  {e.comprimento_cm
                    ? ` (${str(e.comprimento_cm)} × ${str(e.largura_cm)} × ${str(e.altura_cm)} cm)`
                    : ""}
                </option>
              ))}
              <option value="__nova">+ Embalagem customizada…</option>
            </select>
          </Campo>
        </div>
        {embalagemNova && (
          <div className="rd-form-grid tres rd-subform">
            <p className="wide rd-note">
              A embalagem customizada é salva no cadastro de embalagens e pode ser usada em outros
              produtos.
            </p>
            {(
              [
                ["nome", "Nome (ex.: Caixa 40×30×10)", "text"],
                ["largura_cm", "Largura (cm)", "number"],
                ["altura_cm", "Altura (cm)", "number"],
                ["comprimento_cm", "Comprimento (cm)", "number"],
                ["custo", "Custo por unidade (R$)", "number"],
              ] as const
            ).map(([campo, rotulo, tipoCampo]) => (
              <Campo key={campo} rotulo={rotulo}>
                <input
                  id={`embalagem-nova-${campo}`}
                  type={tipoCampo}
                  step={campo === "custo" ? "0.01" : "0.1"}
                  value={str(embalagemNova[campo])}
                  onChange={(e) => setEmbalagemNova({ ...embalagemNova, [campo]: e.target.value })}
                />
              </Campo>
            ))}
          </div>
        )}
      </>
    ),
    composicao:
      tipo === "VARIACAO" ? (
        <>
          <p className="rd-note">
            Até 3 tipos de variação. Informe os valores separados por vírgula e gere a grade. Cada
            linha vira um item com SKU, preço e estoque próprios.
          </p>
          <div className="rd-form-grid tres">
            {[0, 1, 2].map((i) => (
              <div key={i} className="rd-variacao-tipo">
                <Campo rotulo={`Tipo de variação ${i + 1}`}>
                  <input
                    id={`variacao-tipo-${i}`}
                    list="variacao-tipos"
                    placeholder={i === 0 ? "Ex.: Cor" : "Opcional"}
                    value={tiposVariacao[i] ?? ""}
                    onChange={(e) => {
                      const novos = [...tiposVariacao];
                      novos[i] = e.target.value;
                      setTiposVariacao(novos.slice(0, 3));
                    }}
                  />
                </Campo>
                {tiposVariacao[i] && (
                  <Campo rotulo={`Valores de ${tiposVariacao[i]}`}>
                    <input
                      id={`variacao-valores-${i}`}
                      placeholder="Ex.: Azul, Verde, Preto"
                      value={valoresVariacao[tiposVariacao[i]] ?? ""}
                      onChange={(e) =>
                        setValoresVariacao({
                          ...valoresVariacao,
                          [tiposVariacao[i]]: e.target.value,
                        })
                      }
                    />
                  </Campo>
                )}
              </div>
            ))}
            <datalist id="variacao-tipos">
              {TIPOS_VARIACAO.map((t) => (
                <option key={t} value={t} />
              ))}
            </datalist>
          </div>
          <div className="rd-actions">
            <button type="button" className="primary" onClick={gerarGrade}>
              Gerar grade
            </button>
          </div>
          {grade.length ? (
            <Table
              headers={[
                "Variação",
                "SKU",
                "Preço (vazio = do produto)",
                ...(veCusto ? ["Custo"] : []),
                "GTIN",
                "Estoque inicial",
                "À venda",
              ]}
              rows={grade.map((g, i) => {
                const mudar = (campo: keyof LinhaGrade, valor: string | boolean) =>
                  setGrade(grade.map((x, j) => (j === i ? { ...x, [campo]: valor } : x)));
                return [
                  <strong>{Object.values(g.atributos).join(" / ")}</strong>,
                  <input
                    aria-label="SKU da variação"
                    value={g.sku}
                    onChange={(e) => mudar("sku", e.target.value)}
                  />,
                  <input
                    aria-label="Preço da variação"
                    type="number"
                    step="0.01"
                    value={g.preco}
                    onChange={(e) => mudar("preco", e.target.value)}
                  />,
                  ...(veCusto
                    ? [
                        <input
                          aria-label="Custo da variação"
                          type="number"
                          step="0.01"
                          value={g.custo}
                          onChange={(e) => mudar("custo", e.target.value)}
                        />,
                      ]
                    : []),
                  <input
                    aria-label="GTIN da variação"
                    value={g.gtin}
                    onChange={(e) => mudar("gtin", e.target.value)}
                  />,
                  g.id ? (
                    <small>Pela tela de Estoque</small>
                  ) : (
                    <input
                      aria-label="Estoque inicial da variação"
                      type="number"
                      min="0"
                      step="1"
                      value={g.saldo}
                      onChange={(e) => mudar("saldo", e.target.value)}
                    />
                  ),
                  <input
                    aria-label="Variação à venda"
                    type="checkbox"
                    checked={g.permite_venda}
                    onChange={(e) => mudar("permite_venda", e.target.checked)}
                  />,
                ];
              })}
            />
          ) : (
            <Empty text="A grade aparece aqui depois de gerada." />
          )}
          <p className="rd-note">
            Para tirar uma variação de venda, desmarque “À venda” (ou tire o valor da lista e gere a
            grade de novo). Ela não é apagada, porque pode ter pedidos e estoque. Variações sem
            imagem própria usam as imagens do produto principal ao ir para o e-commerce.
          </p>
        </>
      ) : tipo === "KIT" ? (
        <>
          <p className="rd-note">
            O estoque do kit é calculado pelos componentes, e vender um kit baixa os componentes.
          </p>
          <Table
            headers={["Produto", "Quantidade no kit", ...(veCusto ? ["Custo"] : []), ""]}
            rows={kit.map((k, i) => {
              const c = dados.produtos.find((p) => p.id === k.componente_id);
              return [
                <select
                  aria-label="Produto do kit"
                  value={k.componente_id}
                  onChange={(e) =>
                    setKit(
                      kit.map((x, j) => (j === i ? { ...x, componente_id: e.target.value } : x)),
                    )
                  }
                >
                  <option value="">Escolha…</option>
                  {componentesPossiveis.map((p) => (
                    <option key={str(p.id)} value={str(p.id)}>
                      {str(p.sku)} · {str(p.nome)}
                    </option>
                  ))}
                </select>,
                <input
                  aria-label="Quantidade no kit"
                  type="number"
                  min="1"
                  step="1"
                  value={k.quantidade}
                  onChange={(e) =>
                    setKit(kit.map((x, j) => (j === i ? { ...x, quantidade: e.target.value } : x)))
                  }
                />,
                ...(veCusto
                  ? [c ? centMoney(cents(c.custo) * Number(k.quantidade || 0)) : "—"]
                  : []),
                <button type="button" onClick={() => setKit(kit.filter((_, j) => j !== i))}>
                  Remover
                </button>,
              ];
            })}
          />
          <div className="rd-actions">
            <button
              type="button"
              onClick={() => setKit([...kit, { componente_id: "", quantidade: "1" }])}
            >
              + Adicionar produto ao kit
            </button>
            {veCusto && <Badge>Custo do kit: {centMoney(custoKit)}</Badge>}
          </div>
        </>
      ) : (
        <Empty text="Produto simples não tem variações nem composição. Para usar, escolha o tipo Kit ou Com variação ao criar o produto." />
      ),
    imagens: id ? (
      <>
        <div className="rd-form-grid">
          {tipo === "VARIACAO" && filhas.length > 0 && (
            <Campo rotulo="Enviar imagens para">
              <select
                id="imagem-destino"
                value={alvoImagem}
                onChange={(e) => setAlvoImagem(e.target.value)}
              >
                <option value="">Produto principal</option>
                {filhas.map((f) => (
                  <option key={str(f.id)} value={str(f.id)}>
                    Variação{" "}
                    {Object.values((f.atributos_variacao ?? {}) as Record<string, string>).join(
                      " / ",
                    )}
                  </option>
                ))}
              </select>
            </Campo>
          )}
          <Campo
            rotulo="Adicionar imagens"
            dica="JPG, PNG ou WEBP até 2 MB cada, no máximo 12 por item."
          >
            <input
              id="produto-imagens"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              onChange={(e) => {
                enviarImagens(e.target.files);
                e.target.value = "";
              }}
            />
          </Campo>
        </div>
        {[
          { id, rotulo: "Produto principal" },
          ...filhas.map((f) => ({
            id: str(f.id),
            rotulo: `Variação ${Object.values((f.atributos_variacao ?? {}) as Record<string, string>).join(" / ")}`,
          })),
        ]
          .filter((alvo) => alvo.id === id || imagensDo(alvo.id).length > 0)
          .map((alvo) => (
            <div key={alvo.id}>
              <h3 className="rd-secao">{alvo.rotulo}</h3>
              {imagensDo(alvo.id).length ? (
                <div className="rd-galeria">
                  {imagensDo(alvo.id).map((img, i) => (
                    <figure key={str(img.id)}>
                      {/* eslint-disable-next-line @next/next/no-img-element -- imagem servida pela API autenticada */}
                      <img
                        src={`/api/radar/imagens/${str(img.id)}`}
                        alt={`Imagem ${i + 1} de ${alvo.rotulo}`}
                      />
                      <figcaption>
                        {i === 0 ? (
                          <Badge tone="green">Principal</Badge>
                        ) : (
                          <button
                            type="button"
                            onClick={() => acaoImagem(str(img.id), "principal")}
                          >
                            Tornar principal
                          </button>
                        )}
                        <button type="button" onClick={() => acaoImagem(str(img.id), "remover")}>
                          Remover
                        </button>
                      </figcaption>
                    </figure>
                  ))}
                </div>
              ) : (
                <Empty text="Nenhuma imagem ainda." />
              )}
            </div>
          ))}
      </>
    ) : (
      <Empty text="Salve o produto primeiro; depois as imagens podem ser enviadas aqui." />
    ),
    fiscal: (
      <div className="rd-form-grid">
        <Campo
          rotulo="GTIN/EAN tributável"
          dica="Só para caixa, fardo, lote etc. Sem GTIN tributável, deixe em branco."
        >
          {entrada("gtin_tributavel", { inputMode: "numeric", maxLength: 14 })}
        </Campo>
        <Campo rotulo="Unidade tributável">
          <select
            id="produto-unidade_tributavel"
            value={str(v.unidade_tributavel)}
            onChange={set("unidade_tributavel")}
          >
            <option value="">Igual à unidade comercial</option>
            {UNIDADES.map(([valor, rotulo]) => (
              <option key={valor} value={valor}>
                {valor} - {rotulo}
              </option>
            ))}
          </select>
        </Campo>
        <Campo rotulo="Fator de conversão" dica="Unidades comerciais por unidade tributável">
          {entrada("fator_conversao", { type: "number", step: "0.0001", min: "0" })}
        </Campo>
        <Campo rotulo="Código de enquadramento IPI">
          {entrada("ipi_codigo_enquadramento", { maxLength: 5 })}
        </Campo>
        <Campo
          rotulo="Código de enquadramento legal do IPI"
          dica="3 dígitos; 999 quando não houver"
        >
          {entrada("ipi_enquadramento_legal", { maxLength: 3, inputMode: "numeric" })}
        </Campo>
        <Campo
          rotulo="Valor do IPI fixo (R$)"
          dica="Somente para produtos com tributação específica"
        >
          {entrada("ipi_valor_fixo", { type: "number", step: "0.01", min: "0" })}
        </Campo>
        <Campo rotulo="EX TIPI">{entrada("ex_tipi", { maxLength: 3, inputMode: "numeric" })}</Campo>
        <Campo rotulo="Alíquota específica do Imposto Seletivo (%)">
          {entrada("is_aliquota_especifica", { type: "number", step: "0.0001", min: "0" })}
        </Campo>
        <Campo rotulo="Qtd. tributada na monofasia">
          {entrada("qtd_monofasia", { type: "number", step: "0.0001", min: "0" })}
        </Campo>
        <Campo rotulo="Qtd. tributada sujeita à retenção na monofasia">
          {entrada("qtd_monofasia_retencao", { type: "number", step: "0.0001", min: "0" })}
        </Campo>
      </div>
    ),
    anuncios: (
      <>
        <h3 className="rd-secao">Anúncios deste produto</h3>
        {avisoCentral && (
          <p className="rd-ok" role="status">
            {avisoCentral}
          </p>
        )}
        {id && podeAnunciar && (
          <div className="rd-actions">
            <button
              type="button"
              className="primary"
              onClick={() =>
                abrirModal({
                  title: "Novo anúncio",
                  op: "anuncio",
                  extra: { produto_id: id },
                  fields: [
                    {
                      key: "canal",
                      label: "Canal",
                      options: CANAIS.map((c) => ({ value: c, label: c })),
                    },
                    { key: "titulo", label: "Título no canal", value: str(v.nome) },
                    { key: "preco", label: "Preço", type: "number", value: str(v.preco) },
                  ],
                })
              }
            >
              + Adicionar anúncio
            </button>
            <button type="button" onClick={() => anunciar(id)}>
              ⇪ Anunciar nas minhas lojas
            </button>
          </div>
        )}
        {canaisCom(anunciosDoProduto).map((canal) => {
          const doCanal = anunciosDoProduto.filter((a) => a.canal === canal);
          return (
            <div key={canal} className="rd-anuncios-canal">
              <h4>
                {canal} <Badge>{doCanal.length}</Badge>
              </h4>
              {doCanal.length ? (
                <Table
                  headers={["Título", "Loja", "Preço", "Qtd.", "Situação", ""]}
                  rows={doCanal.map((a) => [
                    <div key="t">
                      {str(a.titulo)}
                      <br />
                      <small>
                        {a.id_externo
                          ? `Código no marketplace: ${str(a.id_externo)}`
                          : "Criado no Radar"}
                      </small>
                    </div>,
                    str(dados.lojas.find((l) => l.id === a.loja_id)?.nome) || "—",
                    money(a.preco),
                    a.estoque == null ? "—" : str(a.estoque),
                    <Badge tone={["SIMULADO", "PRONTO"].includes(str(a.estado)) ? "green" : "gray"}>
                      {NO_RADAR[str(a.estado)] ?? str(a.estado)}
                    </Badge>,
                    <span key="a" className="rd-row-actions">
                      <button
                        type="button"
                        title="Abre a central do vendedor e copia o código do anúncio"
                        onClick={() => setAvisoCentral(verNaCentral(a))}
                      >
                        Ver na central ↗
                      </button>
                      {podeAnunciar && (
                        <>
                          <button
                            type="button"
                            onClick={() =>
                              abrirModal({
                                title: "Situação do anúncio",
                                op: "anuncio_estado",
                                extra: { id: a.id },
                                fields: [
                                  {
                                    key: "estado",
                                    label: "Situação",
                                    value: str(a.estado),
                                    options: [
                                      { value: "RASCUNHO", label: "Rascunho" },
                                      { value: "SIMULADO", label: "Publicado (simulação)" },
                                      { value: "PAUSADO", label: "Pausado" },
                                    ],
                                  },
                                ],
                              })
                            }
                          >
                            Editar situação
                          </button>
                          <button
                            type="button"
                            onClick={() =>
                              abrirModal({
                                title: "Propor novo preço",
                                op: "propor_preco",
                                extra: { id: a.id },
                                fields: [
                                  {
                                    key: "preco",
                                    label: "Novo preço",
                                    type: "number",
                                    value: str(a.preco),
                                  },
                                  { key: "motivo", label: "Motivo" },
                                ],
                              })
                            }
                          >
                            Propor preço
                          </button>
                        </>
                      )}
                    </span>,
                  ])}
                />
              ) : (
                <p className="rd-note">Nenhum anúncio neste canal.</p>
              )}
            </div>
          );
        })}
        <p className="rd-note">
          Use “Anunciar nas minhas lojas” para criar anúncios conferidos, quantos quiser, em
          qualquer loja. “Adicionar anúncio” vincula à mão uma publicação que já existe.
        </p>
      </>
    ),
    seo: (
      <>
        <h3 className="rd-secao">SEO e classificação</h3>
        <div className="rd-form-grid">
          <Campo
            rotulo="Keywords"
            dica={`Palavras separadas por vírgula · ${str(v.keywords).length} / 500 caracteres`}
            largo
          >
            {entrada("keywords", { maxLength: 500 })}
          </Campo>
          <Campo rotulo="Descrição para SEO" dica="Até 320 caracteres" largo>
            <textarea
              id="produto-descricao_seo"
              rows={3}
              maxLength={320}
              value={str(v.descricao_seo)}
              onChange={set("descricao_seo")}
            />
          </Campo>
          <Campo
            rotulo="Tags"
            dica="Classificam os produtos (ex.: grupo, cor). Separe por vírgula."
            largo
          >
            <input id="produto-tags" value={tags} onChange={(e) => setTags(e.target.value)} />
          </Campo>
          <Campo rotulo="Vídeo (link)" dica="https://…">
            {entrada("video_url", { type: "url", maxLength: 500 })}
          </Campo>
        </div>
        <ParesEditor
          titulo="Atributos"
          pares={atributos}
          mudar={setAtributos}
          exemplo="Material / Linho"
        />
        <ParesEditor
          titulo="Campos adicionais"
          pares={campos}
          mudar={setCampos}
          exemplo="Instruções de lavagem / Lavar a seco"
        />
      </>
    ),
    fornecedores: (
      <>
        <Table
          headers={["Fornecedor", "Código no fornecedor", ""]}
          rows={fornecedores.map((f, i) => [
            <select
              aria-label="Fornecedor"
              value={f.fornecedor_id}
              onChange={(e) =>
                setFornecedores(
                  fornecedores.map((x, j) =>
                    j === i ? { ...x, fornecedor_id: e.target.value } : x,
                  ),
                )
              }
            >
              <option value="">Escolha…</option>
              {/* Fornecedor que já está em outra linha não aparece de novo. */}
              {dados.fornecedores
                .filter(
                  (x) =>
                    str(x.id) === f.fornecedor_id ||
                    !fornecedores.some((o, j) => j !== i && o.fornecedor_id === str(x.id)),
                )
                .map((x) => (
                  <option key={str(x.id)} value={str(x.id)}>
                    {str(x.nome)}
                  </option>
                ))}
            </select>,
            <input
              aria-label="Código no fornecedor"
              value={f.codigo}
              onChange={(e) =>
                setFornecedores(
                  fornecedores.map((x, j) => (j === i ? { ...x, codigo: e.target.value } : x)),
                )
              }
            />,
            <button
              type="button"
              onClick={() => setFornecedores(fornecedores.filter((_, j) => j !== i))}
            >
              Remover
            </button>,
          ])}
        />
        <div className="rd-actions">
          <button
            type="button"
            disabled={!dados.fornecedores.length}
            onClick={() => setFornecedores([...fornecedores, { fornecedor_id: "", codigo: "" }])}
          >
            + Adicionar fornecedor
          </button>
          {!dados.fornecedores.length && (
            <small className="rd-dica">Cadastre fornecedores em Cadastros → Fornecedores.</small>
          )}
        </div>
      </>
    ),
    observacoes: (
      <Campo
        rotulo="Observações gerais sobre o produto"
        dica="Uso interno. Não é exibido para o cliente."
        largo
      >
        <textarea
          id="produto-observacoes_internas"
          rows={8}
          maxLength={5000}
          value={str(v.observacoes_internas)}
          onChange={set("observacoes_internas")}
        />
        <small className="rd-dica">
          {str(v.observacoes_internas).length.toLocaleString("pt-BR")} / 5.000 caracteres
        </small>
      </Campo>
    ),
    historico: id ? (
      <Historico id={id} />
    ) : (
      <p className="rd-note">Salve o produto para começar o histórico.</p>
    ),
  };

  const conteudo: Record<Aba, ReactNode> = {
    gerais: (
      <>
        {blocos.geral}
        {blocos.dimensoes}
        {estoqueBloco}
      </>
    ),
    descricao: (
      <>
        {descricaoTexto}
        <h3 className="rd-secao">Imagens</h3>
        {blocos.imagens}
        {blocos.seo}
      </>
    ),
    fiscal: (
      <>
        {fiscalBase}
        <h3 className="rd-secao">Informações tributárias adicionais</h3>
        {blocos.fiscal}
      </>
    ),
    anuncios: (
      <>
        {prontoBloco}
        {blocos.anuncios}
      </>
    ),
    variacoes: blocos.composicao,
    precos: blocos.preco,
    custos: (
      <>
        {custoCampo}
        <p className="rd-dica">
          O custo médio muda sozinho a cada compra recebida (Suprimentos → Compras).
        </p>
        {id && <h3 className="rd-secao">Histórico de custos</h3>}
        {id && <Historico id={id} so="CUSTO" />}
      </>
    ),
    outros: (
      <>
        {outrosCampos}
        <h3 className="rd-secao">Fornecedores</h3>
        {blocos.fornecedores}
        <h3 className="rd-secao">Observações</h3>
        {blocos.observacoes}
        {id && <h3 className="rd-secao">Histórico de alterações</h3>}
        {blocos.historico}
      </>
    ),
  };
  const pendentes = faltando();

  // Sair com alteração não salva pergunta antes (voltar e fechar/recarregar a aba).
  const naoSalvo = () =>
    !salvo.current && inicial.current !== null && JSON.stringify(corpo()) !== inicial.current;
  function sair() {
    if (naoSalvo() && !window.confirm("Há alterações não salvas. Sair sem salvar?")) return;
    voltar();
  }
  useEffect(() => {
    if (inicial.current === null) inicial.current = JSON.stringify(corpo());
  });
  useEffect(() => {
    const aviso = (e: BeforeUnloadEvent) => {
      if (naoSalvo()) e.preventDefault();
    };
    window.addEventListener("beforeunload", aviso);
    return () => window.removeEventListener("beforeunload", aviso);
  });

  async function clonar() {
    if (!id || !clonando) return;
    const r = await executar({ op: "produto_clonar", id, imagens: clonando.imagens });
    setClonando(null);
    if (r) aoSalvar(str(r.id), "gerais");
  }

  return (
    <div className="rd-produto-form">
      <div className="rd-toolbar">
        <button type="button" onClick={sair}>
          ← Voltar para produtos
        </button>
        <span className="rd-produto-titulo">
          {str(v.nome) || (novo ? "Novo produto" : "Produto")}
          {str(v.sku) && <small> · {str(v.sku)}</small>}
        </span>
        {!novo && (
          <button type="button" onClick={() => setClonando({ imagens: true })}>
            ⧉ Clonar
          </button>
        )}
        <button type="button" className="primary" disabled={salvando} onClick={salvar}>
          {salvando ? "Salvando…" : pendentes.length ? "Salvar como rascunho" : "Salvar produto"}
        </button>
      </div>
      {erro && (
        <div className="rd-error" role="alert">
          {erro}
        </div>
      )}
      {pendentes.length > 0 && (
        <div className="rd-pendencias-produto" role="note">
          <strong>Rascunho.</strong> Dá para salvar assim; para anunciar e emitir nota, falta:{" "}
          {pendentes.map(([rotulo, destino], i) => (
            <span key={rotulo}>
              {i > 0 && ", "}
              <button type="button" className="text" onClick={() => setAba(destino)}>
                {rotulo}
              </button>
            </span>
          ))}
          .
        </div>
      )}
      {clonando && (
        <div className="rd-modal-backdrop" onClick={() => setClonando(null)}>
          <section
            className="rd-modal"
            role="dialog"
            aria-modal="true"
            aria-label="Clonar produto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rd-card-head">
              <h2>Clonar produto</h2>
              <button aria-label="Fechar" onClick={() => setClonando(null)}>
                ×
              </button>
            </div>
            <p>
              Cria uma cópia de <strong>{str(v.nome)}</strong> com SKU novo e estoque zero.
              Variações, composição do kit e fornecedores vêm junto. Pedidos, anúncios e histórico
              ficam no original.
            </p>
            <label className="rd-check">
              <input
                type="checkbox"
                checked={clonando.imagens}
                onChange={(e) => setClonando({ imagens: e.target.checked })}
              />
              Copiar as imagens também
            </label>
            <div className="rd-modal-foot">
              <button onClick={() => setClonando(null)}>Cancelar</button>
              <button className="primary" onClick={clonar}>
                Clonar
              </button>
            </div>
          </section>
        </div>
      )}
      <div className="rd-tabs" role="tablist" aria-label="Seções do cadastro do produto">
        {ABAS.filter(
          ([chave]) =>
            (chave !== "variacoes" || tipo !== "SIMPLES") && (chave !== "custos" || veCusto),
        ).map(([chave, rotulo]) => (
          <button
            key={chave}
            role="tab"
            aria-selected={aba === chave}
            className={aba === chave ? "active" : ""}
            onClick={() => setAba(chave)}
          >
            {chave === "variacoes" ? (tipo === "KIT" ? "Kit" : "Variações") : rotulo}
            {chave === "anuncios" &&
              anunciosDoProduto.length > 0 &&
              ` (${anunciosDoProduto.length})`}
          </button>
        ))}
      </div>
      <section className="rd-card">{conteudo[aba]}</section>
      <div className="rd-actions rd-produto-rodape">
        <button type="button" onClick={sair}>
          Voltar
        </button>
        <button type="button" className="primary" disabled={salvando} onClick={salvar}>
          {salvando ? "Salvando…" : pendentes.length ? "Salvar como rascunho" : "Salvar produto"}
        </button>
      </div>
    </div>
  );
}

function ParesEditor({
  titulo,
  pares,
  mudar,
  exemplo,
}: {
  titulo: string;
  pares: Par[];
  mudar: (p: Par[]) => void;
  exemplo: string;
}) {
  return (
    <div className="rd-pares">
      <h4>{titulo}</h4>
      {pares.map((p, i) => (
        <div key={i} className="rd-inline">
          <input
            aria-label={`${titulo}: nome`}
            placeholder={exemplo.split(" / ")[0]}
            value={p.nome}
            onChange={(e) =>
              mudar(pares.map((x, j) => (j === i ? { ...x, nome: e.target.value } : x)))
            }
          />
          <input
            aria-label={`${titulo}: valor`}
            placeholder={exemplo.split(" / ")[1]}
            value={p.valor}
            onChange={(e) =>
              mudar(pares.map((x, j) => (j === i ? { ...x, valor: e.target.value } : x)))
            }
          />
          <button type="button" onClick={() => mudar(pares.filter((_, j) => j !== i))}>
            Remover
          </button>
        </div>
      ))}
      <button type="button" onClick={() => mudar([...pares, { nome: "", valor: "" }])}>
        + Adicionar
      </button>
    </div>
  );
}

type Evento = {
  quando: string;
  tipo: string;
  titulo: string;
  detalhe: string;
  quem: string | null;
};

const TIPOS_EVENTO: Record<string, [string, string]> = {
  ESTOQUE: ["Estoque", "blue"],
  VENDA: ["Venda", "green"],
  COMPRA: ["Compra", "amber"],
  CADASTRO: ["Cadastro", "gray"],
  CUSTO: ["Custo", "purple"],
};

/**
 * Linha do tempo do produto: estoque, vendas, compras, custo e alterações, com quem fez. Com
 * {@code so}, mostra só um tipo (ex.: o histórico de custos na aba "Custo e compras").
 */
export function Historico({ id, so }: { id: string; so?: string }) {
  const [eventos, setEventos] = useState<Evento[] | null>(null);
  const [erro, setErro] = useState("");
  const [escolhido, setFiltro] = useState("");
  const filtro = so ?? escolhido;

  useEffect(() => {
    let vivo = true;
    fetch(`/api/radar/produtos/${id}/historico`, { credentials: "include" })
      .then(async (r) => ({ ok: r.ok, corpo: await r.json().catch(() => ({})) }))
      .then(({ ok, corpo }) => {
        if (!vivo) return;
        if (!ok) setErro(str(corpo.mensagem) || "Não foi possível carregar o histórico.");
        else setEventos((corpo.eventos ?? []) as Evento[]);
      });
    return () => {
      vivo = false;
    };
  }, [id]);

  if (erro) return <div className="rd-error">{erro}</div>;
  if (!eventos) return <p className="rd-note">Carregando o histórico…</p>;
  const visiveis = filtro ? eventos.filter((e) => e.tipo === filtro) : eventos;
  return (
    <div className="rd-historico">
      <div className="rd-opcoes" role="group" aria-label="Filtrar histórico" hidden={!!so}>
        {[["", "Tudo"], ...Object.entries(TIPOS_EVENTO).map(([k, [r]]) => [k, r])].map(([k, r]) => (
          <label key={k} className={filtro === k ? "ativo" : ""}>
            <input
              type="radio"
              name="historico-filtro"
              checked={filtro === k}
              onChange={() => setFiltro(k)}
            />
            {r}
          </label>
        ))}
      </div>
      {visiveis.length === 0 ? (
        <p className="rd-note">
          {so === "CUSTO"
            ? "Nenhuma mudança de custo registrada. Para começar, use “iniciar histórico de custos” no “mais ações” da lista de produtos."
            : "Nada registrado ainda."}
        </p>
      ) : (
        <ol className="rd-linha-tempo">
          {visiveis.map((e, i) => (
            <li key={i}>
              <time>{new Date(e.quando).toLocaleString("pt-BR")}</time>
              <div>
                <Badge tone={TIPOS_EVENTO[e.tipo]?.[1]}>
                  {TIPOS_EVENTO[e.tipo]?.[0] ?? e.tipo}
                </Badge>{" "}
                <strong>{e.titulo}</strong>
                {e.detalhe && <small>{e.detalhe}</small>}
                {e.quem && <small>por {e.quem}</small>}
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
