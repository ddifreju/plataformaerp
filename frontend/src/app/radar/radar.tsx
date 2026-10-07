"use client";
/* eslint-disable react/jsx-key -- Table wraps each supplied cell in a keyed td; these arrays are table data, not rendered sibling lists. */

import { useCallback, useEffect, useState, type FormEvent } from "react";
import "./radar.css";
import {
  Badge,
  Empty,
  Table,
  cents,
  centMoney,
  date,
  money,
  str,
  type Field,
  type ModalSpec,
  type Row,
} from "./ui";
import Mercado from "./mercado";
import Promocoes, { situacao } from "./promocoes";
import Relatorios from "./relatorios";
import ProdutoForm, { type Aba as AbaProduto } from "./produto";
import Clientes from "./cliente";
import Vendedores from "./vendedor";
import ProdutosLote from "./produtos-lote";
import PedidosLote from "./pedidos-lote";
import Configuracoes from "./configuracoes";
import { configPedidos } from "./pedidos-filtros";
import FiltrosGenericos, { filtrar, filtroVazio, type Filtro } from "./filtros-genericos";
import FiltrosProdutos, {
  filtrarProdutos,
  filtroInicial,
  type FiltroProdutos,
} from "./produtos-filtros";
import Pendencias from "./pendencias";
import Anuncios from "./anuncios";
import Categorias from "./categorias";
import Embalagens from "./embalagens";

type Data = {
  usuario: { id: string; nome: string; papel: string };
  configuracoes?: Record<string, Record<string, unknown>>;
  empresa?: Row;
  usuarios?: Row[];
  financeiroPermitido: boolean;
  porCanal: Row[];
  porSku: Row[];
  divergencias: Row[];
  produtos: Row[];
  anuncios: Row[];
  pedidos: Row[];
  movimentos: Row[];
  lancamentos: Row[];
  titulos: Row[];
  acoes: Row[];
  registros: Row[];
  auditoria: Row[];
  agentes: { nome: string; estado: string; descricao: string }[];
  resumo: Record<string, string>;
  clientes: Row[];
  vendedores: Row[];
  categoriaCanais: Row[];
  fornecedores: Row[];
  usuariosSistema: Row[];
  categorias: Row[];
  embalagens: Row[];
  promocoes: Row[];
  kitItens: Row[];
  produtoFornecedores: Row[];
  imagens: Row[];
};
const canais = ["Mercado Livre", "Shopee", "TikTok Shop", "SHEIN"];
// "Loja da Ana" vira "LA"; um nome só vira as duas primeiras letras.
function iniciaisEmpresa(nome: string) {
  const partes = nome.split(/\s+/).filter((p) => p.length > 2 || /^[A-Z0-9]/.test(p));
  const letras = partes.length > 1 ? partes[0][0] + partes[1][0] : nome.slice(0, 2);
  return letras.toUpperCase();
}
const nav = [
  ["visao", "Painel", "⌂"],
  ["missao", "Central de ações", "◎"],
  ["produtos", "Produtos", "▣"],
  ["importar", "Importar catálogo", "↥"],
  ["anuncios", "Anúncios", "▤"],
  ["clientes", "Clientes", "☺"],
  ["fornecedores", "Fornecedores", "⇲"],
  ["vendedores", "Vendedores", "◍"],
  ["categorias", "Categorias", "#"],
  ["embalagens", "Embalagens", "▢"],
  ["pedidos", "Pedidos", "▢"],
  ["promocoes", "Promoções", "%"],
  ["estoque", "Estoque", "▦"],
  ["compras", "Compras", "↙"],
  ["fiscal", "Notas fiscais", "▧"],
  ["financeiro", "Financeiro", "◈"],
  ["precos", "Precificação", "↗"],
  ["relatorios", "Relatórios", "▥"],
  ["inbox", "Atendimento", "☏"],
  ["studio", "Brand Studio", "✧"],
  ["mercado", "Mercado", "◉"],
  ["ia", "Radar AI", "✳"],
  ["configuracoes", "Configurações", "⚙"],
  ["integracoes", "Integrações", "⇄"],
  ["auditoria", "Auditoria", "≡"],
  ["guia", "Como usar", "?"],
];
// Menu lateral em grupos. Páginas fora daqui (Central de ações, Importar
// catálogo) continuam acessíveis pelos botões dentro de Painel e Produtos.
const grupos: { id: string; rotulo: string; icone: string; itens: string[] }[] = [
  { id: "painel", rotulo: "Painel", icone: "⌂", itens: ["visao"] },
  {
    id: "cadastros",
    rotulo: "Cadastros",
    icone: "▣",
    itens: [
      "produtos",
      "anuncios",
      "clientes",
      "fornecedores",
      "vendedores",
      "categorias",
      "embalagens",
    ],
  },
  { id: "vendas", rotulo: "Vendas", icone: "▢", itens: ["pedidos", "inbox", "promocoes"] },
  { id: "suprimentos", rotulo: "Suprimentos", icone: "▦", itens: ["estoque", "compras", "fiscal"] },
  { id: "financas", rotulo: "Finanças", icone: "◈", itens: ["financeiro", "precos", "relatorios"] },
  { id: "marketing", rotulo: "Marketing", icone: "✧", itens: ["studio"] },
  { id: "mercado", rotulo: "Mercado", icone: "◉", itens: ["mercado"] },
  { id: "ia", rotulo: "Radar AI", icone: "✳", itens: ["ia"] },
];
const rodape = ["configuracoes", "guia"];
const titles: Record<string, [string, string]> = {
  visao: ["Sua operação, em um só lugar", "Acompanhe o que importa e encontre seu próximo passo."],
  missao: ["O que precisa de você", "Prioridades com contexto, responsáveis e ações."],
  produtos: [
    "Seu catálogo central",
    "Cadastre uma vez. Prepare seus produtos para todos os canais.",
  ],
  importar: ["Traga seu catálogo", "Mapeie as colunas, confira a prévia e importe com segurança."],
  anuncios: ["Uma vitrine, vários canais", "Gerencie os anúncios vinculados aos seus produtos."],
  pedidos: ["Do pedido à entrega", "Acompanhe reserva, separação e expedição local."],
  estoque: [
    "Cada unidade tem uma história",
    "Saldo disponível, reservas e movimentos rastreáveis.",
  ],
  compras: ["Compre com mais clareza", "Fornecedores, sugestões de reposição e planejamento."],
  fiscal: [
    "Notas e conformidade",
    "Prepare o cadastro fiscal e acompanhe a configuração do emissor.",
  ],
  financeiro: [
    "Entenda cada centavo",
    "Resultado local com memória de cálculo e registros financeiros.",
  ],
  precos: ["Preço com intenção", "Simule a margem antes de propor qualquer alteração."],
  inbox: ["Conversas com contexto", "Prepare respostas e consulte a operação no mesmo lugar."],
  studio: ["Sua marca, consistente", "Identidade e rascunhos de conteúdo para seus produtos."],
  mercado: [
    "Inteligência de Mercado",
    "Acompanhe preços, ofertas, posicionamento, avaliações, tendências e sinais do mercado para tomar decisões melhores sobre seus produtos.",
  ],
  ia: [
    "Pergunte. Entenda. Decida.",
    "Seu copiloto consulta os dados locais com regras verificáveis.",
  ],
  integracoes: [
    "Conecte sua operação",
    "Veja o que está disponível e o que depende de homologação.",
  ],
  auditoria: ["Histórico que dá confiança", "Quem fez, o que mudou e quando aconteceu."],
  configuracoes: ["Configurações", "Ajuste o Radar ao jeito da sua empresa."],
  guia: ["Conheça seu Radar", "Um passeio simples pelo trabalho do dia a dia."],
  clientes: ["Seus clientes", "Quem compra de você, com contato e histórico em um só lugar."],
  fornecedores: [
    "Fornecedores e transportadores",
    "O mesmo cadastro do cliente, para quem abastece e para quem entrega.",
  ],
  vendedores: ["Seus vendedores", "Dados, acesso ao sistema e comissão de quem vende por você."],
  categorias: ["Categorias", "Organize o catálogo do jeito que seu cliente procura."],
  embalagens: ["Embalagens", "Custos e medidas das embalagens que você usa nos envios."],
  promocoes: ["Promoções", "Planeje descontos por produto, canal e período, vendo a margem antes."],
  relatorios: [
    "Relatórios",
    "Vendas, resultado, curva ABC e estoque do período, prontos para exportar.",
  ],
};
async function call(path: string, body?: unknown, key?: string) {
  const res = await fetch("/api/radar" + path, {
    method: body ? "POST" : "GET",
    credentials: "include",
    headers: body
      ? {
          "Content-Type": "application/json",
          "X-Radar-Request": "1",
          ...(key ? { "Idempotency-Key": key } : {}),
        }
      : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401) throw new Error("Entre novamente para continuar.");
  const data = await res.json();
  if (!res.ok) throw new Error(data.mensagem ?? "Não foi possível concluir. Confira os dados.");
  return data;
}

export default function Radar({ initialPage = "visao" }: { initialPage?: string }) {
  const [data, setData] = useState<Data | null>(null),
    [page, setPage] = useState(initialPage),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [notice, setNotice] = useState(""),
    [error, setError] = useState(""),
    [modal, setModal] = useState<ModalSpec | null>(null),
    [tour, setTour] = useState(0),
    [abertos, setAbertos] = useState<string[]>([]),
    // Produto aberto no formulário completo: id null = novo produto.
    [editando, setEditando] = useState<{
      id: string | null;
      aba: AbaProduto;
      versao: number;
    } | null>(null),
    [menu, setMenu] = useState(false),
    // Produtos marcados na lista para as ações em lote.
    [marcadosProdutos, setMarcadosProdutos] = useState<string[]>([]),
    [marcadosPedidos, setMarcadosPedidos] = useState<string[]>([]),
    // null até a lista de produtos abrir: aí lê o último filtro deste navegador.
    [filtroProdutosSalvo, setFiltroProdutos] = useState<FiltroProdutos | null>(null),
    [verLixeira, setVerLixeira] = useState(false),
    [filtroPedidosAtual, setFiltroPedidos] = useState<Filtro | null>(null);
  const refresh = useCallback(async () => {
    const d = await call("");
    setData(d);
  }, []);
  useEffect(() => {
    let live = true;
    call("")
      .then((d) => {
        if (live) setData(d);
      })
      .catch(() => {
        if (live) setData(null);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, []);
  async function command(body: Record<string, unknown>) {
    setError("");
    setBusy(true);
    try {
      const r = await call("/comandos", body, crypto.randomUUID());
      setNotice(r.mensagem);
      await refresh();
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    } finally {
      setBusy(false);
    }
  }
  // Como command, mas devolve a resposta (para quem precisa do id gerado).
  async function commandResult(body: Record<string, unknown>) {
    setError("");
    setBusy(true);
    try {
      const r = await call("/comandos", body, crypto.randomUUID());
      setNotice(r.mensagem);
      await refresh();
      return r as Record<string, unknown>;
    } catch (e) {
      setError((e as Error).message);
      return null;
    } finally {
      setBusy(false);
    }
  }
  function go(p: string) {
    setEditando(null);
    setPage(p);
    setMenu(false);
    setNotice("");
    setError("");
  }
  const can = (...roles: string[]) => !!data && roles.includes(data.usuario.papel);
  const products = data?.produtos ?? [],
    orders = data?.pedidos ?? [],
    listings = data?.anuncios ?? [];
  const prod = (id: unknown) => products.find((x) => x.id === id);
  // Produto com variações (pai) não é vendido nem estocado: as variações são.
  // Kit é vendido, mas o estoque fica nos componentes.
  // Produtos na lixeira continuam em `products` (pedidos e anúncios antigos mostram o nome),
  // mas saem das listas, das escolhas e do cadastro.
  const cadastrados = products.filter((p) => !p.excluido_em);
  const naLixeira = products.filter((p) => p.excluido_em && !p.pai_id);
  const principais = cadastrados.filter((p) => !p.pai_id);
  const vendaveis = cadastrados.filter((p) => p.tipo !== "VARIACAO" && p.permite_venda !== false);
  const estocaveis = cadastrados.filter((p) => p.tipo !== "VARIACAO" && p.tipo !== "KIT");
  const prodOptions = vendaveis.map((p) => ({ value: str(p.id), label: `${p.sku} · ${p.nome}` }));
  const estoqueOptions = estocaveis.map((p) => ({
    value: str(p.id),
    label: `${p.sku} · ${p.nome}`,
  }));
  const disponivel = (p: Row): number => {
    if (p.tipo === "VARIACAO")
      return products
        .filter((f) => f.pai_id === p.id)
        .reduce((s, f) => s + Number(f.fisico) - Number(f.reservado), 0);
    if (p.tipo === "KIT") {
      const itens = (data?.kitItens ?? []).filter((k) => k.kit_id === p.id);
      if (!itens.length) return 0;
      return Math.min(
        ...itens.map((k) => {
          const c = products.find((x) => x.id === k.componente_id);
          return c
            ? Math.floor((Number(c.fisico) - Number(c.reservado)) / Number(k.quantidade))
            : 0;
        }),
      );
    }
    return Number(p.fisico) - Number(p.reservado);
  };
  const channelField: Field = {
    key: "canal",
    label: "Canal",
    options: canais.map((x) => ({ value: x, label: x })),
  };
  const productField: Field = { key: "produto_id", label: "Produto", options: prodOptions };
  const amount = (key: string, label: string, value = "0"): Field => ({
    key,
    label,
    type: "number",
    value,
  });
  const resultados = (o: Row) =>
    cents(o.preco) * Number(o.quantidade) -
    cents(o.custo_unitario) * Number(o.quantidade) -
    ["comissao", "frete", "imposto", "ads", "embalagem", "desconto"].reduce(
      (a, k) => a + cents(o[k]),
      0,
    );
  const negativos = orders.filter(
    (o) => !["CANCELADO", "DEVOLVIDO"].includes(str(o.estado)) && resultados(o) < 0,
  );
  const baixos = estocaveis.filter(
    (p) =>
      p.controla_estoque !== false && Number(p.fisico) - Number(p.reservado) <= Number(p.minimo),
  );
  const pendentes = data?.acoes.filter((a) => a.estado === "PENDENTE") ?? [];
  const records = (t: string) => data?.registros.filter((r) => r.tipo === t) ?? [];
  function pedidoModal() {
    setModal({
      title: "Novo pedido local",
      op: "pedido",
      fields: [
        productField,
        channelField,
        {
          key: "cliente_id",
          label: "Cliente cadastrado",
          required: false,
          options: [
            { value: "", label: "Cliente novo ou não encontrado na lista" },
            ...(data?.clientes ?? []).map((c) => ({
              value: str(c.id),
              label: `${str(c.codigo)} · ${str(c.nome)}`,
            })),
          ],
        },
        { key: "cliente", label: "Nome do cliente (se for novo)", required: false },
        {
          key: "cliente_documento",
          label: "CPF/CNPJ (com ele, o pedido vai para o cliente já cadastrado)",
          required: false,
        },
        { key: "quantidade", label: "Quantidade", type: "integer", value: "1" },
        amount("preco", "Preço unitário"),
        amount("comissao", "Comissão total"),
        amount("frete", "Frete total"),
        amount("imposto", "Imposto total informado"),
        amount("ads", "Ads atribuídos"),
        amount("embalagem", "Embalagem"),
        amount("desconto", "Desconto total (sem promoção)"),
        {
          key: "promocao_id",
          label: "Promoção",
          required: false,
          options: [
            { value: "", label: "Nenhuma" },
            ...(data?.promocoes ?? [])
              .filter((p) => situacao(p).vale)
              .map((p) => ({ value: str(p.id), label: str(p.nome) })),
          ],
        },
      ],
    });
  }
  async function seed() {
    setBusy(true);
    try {
      for (const [sku, nome, custo, preco, saldo] of [
        ["PERS-01", "Persiana blackout areia", "42.00", "89.90", 32],
        ["PERS-02", "Persiana romana cinza", "68.00", "149.90", 4],
        ["CORT-01", "Cortina linho natural", "85.00", "189.90", 18],
        ["TRIL-01", "Trilho duplo 2 metros", "22.00", "59.90", 48],
      ])
        await call(
          "/comandos",
          {
            op: "produto",
            sku,
            nome,
            custo,
            preco,
            saldo,
            marca: "Casa Clara",
            descricao: "Produto fictício para explorar o Radar.",
          },
          crypto.randomUUID(),
        );
      await refresh();
      setNotice(
        "Catálogo de exemplo criado. Agora você pode preparar anúncios e criar pedidos locais.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (loading)
    return (
      <div className="rd-loading">
        <div className="rd-logo">
          r<span>◌</span>
        </div>
        <p>Preparando seu Radar…</p>
      </div>
    );
  if (!data) return <Login onLogin={refresh} />;
  const filtroProdutos = filtroProdutosSalvo ?? filtroInicial();
  const contextoFiltro = {
    produtos: cadastrados,
    anuncios: listings,
    categorias: data.categorias,
    fornecedores: data.fornecedores ?? [],
    produtoFornecedores: data.produtoFornecedores ?? [],
    imagens: data.imagens,
    disponivel,
  };
  const listaProdutos =
    page === "produtos" ? filtrarProdutos(principais, filtroProdutos, contextoFiltro) : [];
  const cfgPedidos = configPedidos(
    {
      produtos: products,
      clientes: data.clientes ?? [],
      vendedores: data.vendedores ?? [],
      categorias: data.categorias,
      promocoes: data.promocoes ?? [],
      veFinanceiro: data.financeiroPermitido,
    },
    orders,
  );
  const filtroPedidos = filtroPedidosAtual ?? filtroVazio(cfgPedidos);
  const listaPedidos = page === "pedidos" ? filtrar(orders, filtroPedidos, cfgPedidos) : [];
  const liberadas = new Set(
    nav
      .map(([p]) => p)
      .filter(
        (p) => !["financeiro", "precos", "relatorios"].includes(p) || data.financeiroPermitido,
      )
      .filter((p) => p !== "auditoria" || can("DONO"))
      .filter(
        (p) => p !== "clientes" || can("DONO", "GESTOR", "ATENDIMENTO", "FINANCEIRO", "ANALISTA"),
      )
      .filter(
        (p) =>
          p !== "fornecedores" ||
          can("DONO", "GESTOR", "ESTOQUE", "FINANCEIRO", "ATENDIMENTO", "ANALISTA"),
      )
      .filter(
        (p) => p !== "vendedores" || can("DONO", "GESTOR", "ATENDIMENTO", "FINANCEIRO", "ANALISTA"),
      ),
  );
  const rotuloDe = (id: string) => nav.find((x) => x[0] === id)?.[1] ?? id;
  const nomeEmpresa =
    str(data.empresa?.nome_fantasia) || str(data.empresa?.razao_social) || "Meu espaço Radar";
  return (
    <div className="radar-app">
      <aside className={`rd-sidebar ${menu ? "open" : ""}`}>
        <a className="rd-brand" href="/radar">
          <span className="rd-brand-icon">◉</span>radar<span className="rd-brand-dot">.</span>
        </a>
        <div className="rd-company">
          <div>{iniciaisEmpresa(nomeEmpresa)}</div>
          <span>
            {nomeEmpresa}
            <small>Operação local</small>
          </span>
          <b>⌄</b>
        </div>
        <p className="rd-nav-label">SEU COMMERCE OS</p>
        <nav>
          {grupos.map((g) => {
            const itens = g.itens.filter((id) => liberadas.has(id));
            if (!itens.length) return null;
            const ativo = itens.includes(page) || (g.id === "painel" && page === "missao");
            if (itens.length === 1 && g.itens.length === 1)
              return (
                <button
                  key={g.id}
                  className={`${ativo ? "active" : ""} ${g.id === "ia" ? "rd-nav-ai" : ""}`}
                  onClick={() => go(itens[0])}
                >
                  <span className="rd-nav-icon">{g.icone}</span>
                  {g.rotulo}
                  {g.id === "painel" && pendentes.length > 0 && <b>{pendentes.length}</b>}
                </button>
              );
            const aberto = ativo || abertos.includes(g.id);
            return (
              <div key={g.id} className="rd-nav-group">
                <button
                  className={ativo ? "rd-nav-parent current" : "rd-nav-parent"}
                  aria-expanded={aberto}
                  onClick={() =>
                    setAbertos(
                      abertos.includes(g.id)
                        ? abertos.filter((x) => x !== g.id)
                        : [...abertos, g.id],
                    )
                  }
                >
                  <span className="rd-nav-icon">{g.icone}</span>
                  {g.rotulo}
                  <i>{aberto ? "⌃" : "⌄"}</i>
                </button>
                {aberto &&
                  itens.map((id) => (
                    <button
                      key={id}
                      className={`rd-nav-child ${page === id ? "active" : ""}`}
                      onClick={() => go(id)}
                    >
                      {rotuloDe(id)}
                    </button>
                  ))}
              </div>
            );
          })}
        </nav>
        <nav className="rd-nav-footer">
          {rodape
            .filter((id) => liberadas.has(id))
            .map((id) => (
              <button
                key={id}
                className={
                  page === id ||
                  (id === "configuracoes" && ["integracoes", "auditoria"].includes(page))
                    ? "active"
                    : ""
                }
                onClick={() => go(id)}
              >
                <span className="rd-nav-icon">{nav.find((x) => x[0] === id)?.[2]}</span>
                {rotuloDe(id)}
              </button>
            ))}
        </nav>
        <div className="rd-user">
          <div className="rd-avatar">{data.usuario.nome.slice(0, 1)}</div>
          <span>
            {data.usuario.nome}
            <small>{data.usuario.papel.toLowerCase()}</small>
          </span>
          <button
            aria-label="Sair"
            onClick={async () => {
              await fetch("/api/logout", { method: "POST" });
              setData(null);
            }}
          >
            ↪
          </button>
        </div>
      </aside>
      <div className="rd-main">
        <header className="rd-topbar">
          <button className="rd-menu" aria-label="Abrir menu" onClick={() => setMenu(!menu)}>
            ☰
          </button>
          <span>
            Meu espaço <span className="rd-sep">/</span>{" "}
            <strong>{nav.find((x) => x[0] === page)?.[1]}</strong>
          </span>
          <div>
            <Badge tone="green">● Salvo no banco local</Badge>
            <button
              className="rd-icon-btn"
              title="Atualizar"
              onClick={() => refresh().catch((e) => setError(e.message))}
            >
              ↻
            </button>
            <button className="rd-ai-button" onClick={() => go("ia")}>
              ✳ Pergunte ao Radar
            </button>
          </div>
        </header>
        <div className="rd-local-banner">
          <span>◌</span>
          <strong>Ambiente local de avaliação</strong>
          <span>
            Marketplaces, NF-e e envios externos não conectados. As ações ficam neste computador.
          </span>
        </div>
        <main className="rd-content">
          <div className="rd-page-heading">
            <div>
              <div className="rd-eyebrow">
                RADAR ·{" "}
                {page === "visao"
                  ? "COMMERCE OPERATING SYSTEM"
                  : nav.find((x) => x[0] === page)?.[1].toUpperCase()}
              </div>
              <h1>{titles[page][0]}</h1>
              <p>{titles[page][1]}</p>
            </div>
            <div className="rd-heading-actions">
              {page === "produtos" && can("DONO", "GESTOR") && (
                <>
                  <button onClick={() => go("importar")}>↥ Importar</button>
                  <button
                    className="primary"
                    onClick={() => setEditando({ id: null, aba: "geral", versao: Date.now() })}
                  >
                    + Novo produto
                  </button>
                </>
              )}
              {page === "pedidos" && can("DONO", "GESTOR") && (
                <button className="primary" onClick={pedidoModal}>
                  + Pedido local
                </button>
              )}
              {page === "visao" && <button onClick={() => go("guia")}>▷ Conhecer o Radar</button>}
            </div>
          </div>
          {notice && (
            <div className="rd-toast" role="status">
              ✓ {notice}
              <button aria-label="Fechar aviso" onClick={() => setNotice("")}>
                ×
              </button>
            </div>
          )}
          {error && (
            <div className="rd-error" role="alert">
              {error}
              <button onClick={() => setError("")}>×</button>
            </div>
          )}
          {page === "visao" && (
            <>
              <div className="rd-welcome">
                <div>
                  <Badge>SEU DIA COM MAIS CLAREZA</Badge>
                  <h2>
                    Menos abas.
                    <br />
                    Mais controle da sua operação.
                  </h2>
                  <p>Do produto ao resultado, conecte cada etapa do seu trabalho.</p>
                  <button onClick={() => go(products.length ? "missao" : "produtos")}>
                    {products.length ? "Ver minhas prioridades" : "Começar pelo catálogo"} →
                  </button>
                </div>
                <div className="rd-orbit">
                  <div className="rd-orbit-ring">
                    <span>◈</span>
                    <span>▦</span>
                    <span>✳</span>
                    <span>▤</span>
                  </div>
                  <div className="rd-orbit-core">
                    radar<span>commerce OS</span>
                  </div>
                </div>
              </div>
              <div className="rd-kpis">
                {(data.financeiroPermitido
                  ? [
                      ["Receita registrada", money(data.resumo.bruto), "Pedidos locais"],
                      [
                        "Resultado gerencial",
                        money(data.resumo.resultado),
                        "Custos informados · não homologado",
                      ],
                      [
                        "Estoque a custo",
                        money(data.resumo.estoque),
                        `${products.length} SKUs cadastrados`,
                      ],
                      [
                        "Pedidos em operação",
                        orders.filter((x) => ["RESERVADO", "SEPARADO"].includes(str(x.estado)))
                          .length,
                        "Aguardando expedição",
                      ],
                    ]
                  : [
                      ["Produtos", products.length, "Catálogo local"],
                      ["Pedidos", orders.length, "Histórico local"],
                      ["Estoque baixo", baixos.length, "SKUs no mínimo"],
                      ["Anúncios", listings.length, "Rascunhos e simulações"],
                    ]
                ).map(([label, value, note]) => (
                  <div className="rd-kpi" key={str(label)}>
                    <span>{label}</span>
                    <strong>{value}</strong>
                    <small>{note}</small>
                  </div>
                ))}
              </div>
              <div className="rd-grid two">
                <section className="rd-card">
                  <div className="rd-card-head">
                    <h2>Seu fluxo de trabalho</h2>
                    <Badge tone="blue">Ponta a ponta</Badge>
                  </div>
                  <div className="rd-flow">
                    {[
                      ["produtos", "01", "Cadastre", "Produto e SKU"],
                      ["anuncios", "02", "Prepare", "Anúncios por canal"],
                      ["pedidos", "03", "Opere", "Pedidos e estoque"],
                      ["financeiro", "04", "Entenda", "Lucro auditável"],
                    ].map(([p, n, t, s]) => (
                      <button key={n} onClick={() => go(p)}>
                        <span>{n}</span>
                        <strong>{t}</strong>
                        <small>{s}</small>
                      </button>
                    ))}
                  </div>
                </section>
                <section className="rd-card">
                  <div className="rd-card-head">
                    <h2>Precisa da sua atenção</h2>
                    <button className="text" onClick={() => go("missao")}>
                      Ver tudo →
                    </button>
                  </div>
                  <div className="rd-task" onClick={() => go("estoque")}>
                    <i className="amber">▦</i>
                    <div>
                      <strong>{baixos.length} produtos no estoque mínimo</strong>
                      <small>Confira disponibilidade e reposição</small>
                    </div>
                    →
                  </div>
                  <div className="rd-task" onClick={() => go("missao")}>
                    <i className="purple">✓</i>
                    <div>
                      <strong>{pendentes.length} alterações aguardando aprovação</strong>
                      <small>Você decide antes de aplicar</small>
                    </div>
                    →
                  </div>
                  <div className="rd-task" onClick={() => go("integracoes")}>
                    <i className="blue">⇄</i>
                    <div>
                      <strong>Conecte seus canais quando estiver pronto</strong>
                      <small>Integrações externas ainda não habilitadas</small>
                    </div>
                    →
                  </div>
                </section>
              </div>
              {!products.length && (
                <section className="rd-card rd-start">
                  <div>
                    <h2>Quer explorar com um exemplo?</h2>
                    <p>
                      Crie quatro produtos fictícios da loja Casa Clara. Nenhuma venda ou publicação
                      externa será feita.
                    </p>
                  </div>
                  {can("DONO", "GESTOR") && (
                    <button disabled={busy} className="primary" onClick={seed}>
                      Carregar catálogo de exemplo
                    </button>
                  )}
                </section>
              )}
              <section className="rd-card">
                <div className="rd-card-head">
                  <h2>Canais da operação</h2>
                  <button className="text" onClick={() => go("integracoes")}>
                    Gerenciar →
                  </button>
                </div>
                <div className="rd-channels">
                  {canais.map((c, i) => (
                    <div key={c}>
                      <span className={`rd-channel c${i}`}>
                        {c
                          .split(" ")
                          .map((s) => s[0])
                          .join("")}
                      </span>
                      <strong>{c}</strong>
                      <small>{listings.filter((a) => a.canal === c).length} anúncios locais</small>
                      <Badge>Não conectado</Badge>
                    </div>
                  ))}
                </div>
              </section>
            </>
          )}
          {page === "produtos" && editando && (
            <ProdutoForm
              key={`${editando.id ?? "novo"}-${editando.versao}`}
              produto={editando.id ? (products.find((p) => p.id === editando.id) ?? null) : null}
              abaInicial={editando.aba}
              config={data.configuracoes?.produtos}
              dados={{
                produtos: cadastrados,
                categorias: data.categorias,
                embalagens: data.embalagens,
                fornecedores: data.fornecedores,
                anuncios: listings,
                kitItens: data.kitItens,
                produtoFornecedores: data.produtoFornecedores,
                imagens: data.imagens,
              }}
              veCusto={data.financeiroPermitido}
              podeAnunciar={can("DONO", "GESTOR", "MARKETING")}
              executar={commandResult}
              recarregar={refresh}
              abrirModal={setModal}
              voltar={() => setEditando(null)}
              aoSalvar={(id, aba) => setEditando({ id, aba, versao: Date.now() })}
            />
          )}
          {page === "produtos" && !editando && can("DONO", "GESTOR") && (
            <Pendencias
              produtos={cadastrados}
              imagens={data.imagens}
              categorias={data.categorias}
              fornecedores={data.fornecedores ?? []}
              produtoFornecedores={data.produtoFornecedores}
              executar={command}
              recarregar={refresh}
              marcarParaLote={setMarcadosProdutos}
            />
          )}
          {page === "produtos" && !editando && verLixeira && (
            <Lixeira
              itens={naLixeira}
              podeEditar={can("DONO", "GESTOR")}
              executar={command}
              voltar={() => setVerLixeira(false)}
            />
          )}
          {page === "produtos" && !editando && !verLixeira && (
            <section className="rd-card">
              <div className="rd-card-head">
                <h2>{principais.length} produtos</h2>
                {naLixeira.length > 0 && (
                  <button type="button" className="text" onClick={() => setVerLixeira(true)}>
                    🗑 Lixeira ({naLixeira.length})
                  </button>
                )}
              </div>
              <FiltrosProdutos
                filtro={filtroProdutos}
                setFiltro={setFiltroProdutos}
                contexto={contextoFiltro}
                principais={principais}
                total={listaProdutos.length}
              />
              <ProdutosLote
                ativo={can("DONO", "GESTOR")}
                produtos={cadastrados}
                clonar={
                  can("DONO", "GESTOR")
                    ? async (id) => {
                        const r = await commandResult({ op: "produto_clonar", id, imagens: true });
                        if (r) setEditando({ id: str(r.id), aba: "geral", versao: Date.now() });
                      }
                    : undefined
                }
                linhas={listaProdutos}
                marcados={marcadosProdutos}
                setMarcados={setMarcadosProdutos}
                categorias={data.categorias}
                embalagens={data.embalagens}
                kitItens={data.kitItens}
                veCusto={data.financeiroPermitido}
                disponivel={disponivel}
                executar={command}
              >
                {(lote) => (
                  <Table
                    headers={[
                      lote.cabecalho,
                      "Produto",
                      "SKU",
                      "Tipo",
                      "Custo / preço",
                      "Disponível",
                      "Cadastro",
                      "Ações",
                    ]}
                    rows={listaProdutos.map((p) => {
                      const capa = data.imagens.find((i) => i.produto_id === p.id);
                      const variacoes = products.filter((f) => f.pai_id === p.id).length;
                      return [
                        lote.celula(p),
                        <div className="rd-product-name">
                          {capa ? (
                            // eslint-disable-next-line @next/next/no-img-element -- imagem servida pela API autenticada
                            <img
                              className="rd-product-thumb"
                              src={`/api/radar/imagens/${str(capa.id)}`}
                              alt=""
                            />
                          ) : (
                            <span className="rd-product-thumb">▥</span>
                          )}
                          <div>
                            <strong>{str(p.nome)}</strong>
                            <small>{str(p.marca) || "Sem marca"}</small>
                          </div>
                        </div>,
                        str(p.sku),
                        p.tipo === "KIT" ? (
                          <Badge tone="purple">Kit</Badge>
                        ) : p.tipo === "VARIACAO" ? (
                          <Badge tone="blue">{variacoes} variações</Badge>
                        ) : (
                          <Badge>Simples</Badge>
                        ),
                        <>
                          {data.financeiroPermitido && <small>{money(p.custo)} / </small>}
                          {money(p.preco)}
                        </>,
                        p.controla_estoque === false ? "Sem controle" : disponivel(p),
                        <Badge tone={p.ncm ? "green" : "amber"}>
                          {p.ncm ? "NCM preenchido" : "Falta NCM"}
                        </Badge>,
                        <div className="rd-row-actions">
                          <button
                            onClick={() =>
                              setEditando({ id: str(p.id), aba: "geral", versao: Date.now() })
                            }
                          >
                            {can("DONO", "GESTOR") ? "Editar" : "Ver"}
                          </button>
                          {can("DONO", "GESTOR", "MARKETING") && (
                            <button
                              disabled={busy}
                              onClick={() => command({ op: "anuncios_lote", produto_id: p.id })}
                            >
                              Preparar 4 canais
                            </button>
                          )}
                        </div>,
                      ];
                    })}
                  />
                )}
              </ProdutosLote>
            </section>
          )}
          {page === "importar" && (
            <Importer busy={busy} onImport={(items) => command({ op: "importar", itens: items })} />
          )}
          {page === "anuncios" && (
            <Anuncios
              anuncios={listings}
              produtos={products}
              podeEditar={can("DONO", "GESTOR", "MARKETING")}
              busy={busy}
              executar={command}
              abrirModal={setModal}
              categorias={data.categorias}
              categoriaCanais={data.categoriaCanais ?? []}
              imagens={data.imagens}
              abrirProduto={(id) => {
                setPage("produtos");
                setEditando({ id, aba: "geral", versao: Date.now() });
              }}
            />
          )}
          {page === "pedidos" && (
            <section className="rd-card">
              <FiltrosGenericos
                cfg={cfgPedidos}
                filtro={filtroPedidos}
                setFiltro={setFiltroPedidos}
                base={orders}
                total={listaPedidos.length}
              />
              <PedidosLote
                ativo={can("DONO", "GESTOR")}
                pedidos={orders}
                linhas={listaPedidos}
                produtos={products}
                marcados={marcadosPedidos}
                setMarcados={setMarcadosPedidos}
                veFinanceiro={data.financeiroPermitido}
                executar={command}
              >
                {(lote) => (
                  <Table
                    headers={[
                      lote.cabecalho,
                      "Pedido / cliente",
                      "Produto",
                      "Canal",
                      "Valor bruto",
                      "Estado",
                      "Próxima etapa",
                    ]}
                    vazio={orders.length ? "Nenhum pedido com essa busca ou filtros." : undefined}
                    rows={listaPedidos.map((o) => [
                      lote.celula(o),
                      <>
                        <strong>{str(o.numero)}</strong>
                        <small>{str(o.cliente)}</small>
                        {(o.numero_externo || o.nota_fiscal_numero) && (
                          <small>
                            {o.numero_externo ? `Marketplace: ${str(o.numero_externo)}` : ""}
                            {o.numero_externo && o.nota_fiscal_numero ? " · " : ""}
                            {o.nota_fiscal_numero ? `NF ${str(o.nota_fiscal_numero)}` : ""}
                          </small>
                        )}
                        {Array.isArray(o.marcadores) && o.marcadores.length > 0 && (
                          <span className="rd-marcadores">
                            {(o.marcadores as string[]).map((m) => (
                              <span key={m}>{m}</span>
                            ))}
                          </span>
                        )}
                      </>,
                      `${o.quantidade} × ${prod(o.produto_id)?.nome}`,
                      str(o.canal),
                      centMoney(cents(o.preco) * Number(o.quantidade)),
                      <Badge tone={o.estado === "EXPEDIDO" ? "green" : "blue"}>
                        {str(o.estado)}
                      </Badge>,
                      <div className="rd-row-actions">
                        {can("DONO", "GESTOR", "ESTOQUE") &&
                          ["RESERVADO", "SEPARADO"].includes(str(o.estado)) && (
                            <button
                              disabled={busy}
                              onClick={() =>
                                o.estado === "RESERVADO"
                                  ? command({ op: "pedido_estado", id: o.id, estado: "SEPARADO" })
                                  : setModal({
                                      title: "Simular expedição local",
                                      op: "pedido_estado",
                                      extra: {
                                        id: o.id,
                                        estado: "EXPEDIDO",
                                        confirmar_simulacao: true,
                                      },
                                      fields: [
                                        {
                                          key: "confirmacao",
                                          label:
                                            "Esta simulação baixa o estoque. Não emite NF-e nem contrata frete.",
                                          value: "Confirmar somente no ambiente local",
                                        },
                                      ],
                                    })
                              }
                            >
                              {o.estado === "RESERVADO" ? "Separar" : "Simular expedição"}
                            </button>
                          )}
                        {can("DONO", "GESTOR") &&
                          ["RESERVADO", "SEPARADO"].includes(str(o.estado)) && (
                            <button
                              onClick={() =>
                                command({ op: "pedido_estado", id: o.id, estado: "CANCELADO" })
                              }
                            >
                              Cancelar
                            </button>
                          )}
                        {can("DONO", "GESTOR") && o.estado === "EXPEDIDO" && (
                          <button
                            onClick={() =>
                              setModal({
                                title: "Registrar devolução completa local",
                                op: "pedido_estado",
                                extra: { id: o.id, estado: "DEVOLVIDO" },
                                fields: [
                                  {
                                    key: "retornar_estoque",
                                    label: "Produto inspecionado e apto para revenda?",
                                    options: [
                                      { value: "false", label: "Não — registrar perda" },
                                      { value: "true", label: "Sim — devolver ao disponível" },
                                    ],
                                  },
                                ],
                              })
                            }
                          >
                            Devolver
                          </button>
                        )}
                      </div>,
                    ])}
                  />
                )}
              </PedidosLote>
            </section>
          )}
          {page === "estoque" && (
            <>
              <div className="rd-kpis">
                {[
                  ["SKUs", estocaveis.length],
                  ["Unidades físicas", estocaveis.reduce((s, p) => s + Number(p.fisico), 0)],
                  ["Reservadas", estocaveis.reduce((s, p) => s + Number(p.reservado), 0)],
                  ["No estoque mínimo", baixos.length],
                ].map(([l, v]) => (
                  <div className="rd-kpi" key={l}>
                    <span>{l}</span>
                    <strong>{v}</strong>
                  </div>
                ))}
              </div>
              <section className="rd-card">
                <Table
                  headers={["Produto", "Físico", "Reservado", "Disponível", "Ajustar"]}
                  rows={estocaveis.map((p) => [
                    <>
                      <strong>{str(p.nome)}</strong>
                      <small>{str(p.sku)}</small>
                    </>,
                    str(p.fisico),
                    str(p.reservado),
                    <Badge
                      tone={
                        Number(p.fisico) - Number(p.reservado) <= Number(p.minimo)
                          ? "amber"
                          : "green"
                      }
                    >
                      {Number(p.fisico) - Number(p.reservado)}
                    </Badge>,
                    can("DONO", "GESTOR", "ESTOQUE") ? (
                      <button
                        onClick={() =>
                          setModal({
                            title: "Movimentar estoque",
                            op: "estoque",
                            extra: { produto_id: p.id },
                            fields: [
                              {
                                key: "quantidade",
                                label: "Quantidade (positiva para entrada; negativa para saída)",
                                type: "integer",
                                value: "1",
                              },
                              { key: "motivo", label: "Motivo / documento de origem" },
                            ],
                          })
                        }
                      >
                        + Movimento
                      </button>
                    ) : (
                      "Consulta"
                    ),
                  ])}
                />
              </section>
              <section className="rd-card">
                <div className="rd-card-head">
                  <h2>Histórico de movimentos</h2>
                  <Badge>Rastreável</Badge>
                </div>
                <Table
                  headers={["Data", "SKU", "Movimento", "Físico", "Reserva", "Motivo"]}
                  rows={data.movimentos.map((m) => [
                    date(m.criado_em),
                    str(prod(m.produto_id)?.sku),
                    str(m.tipo),
                    str(m.fisico_delta),
                    str(m.reserva_delta),
                    str(m.motivo),
                  ])}
                />
              </section>
            </>
          )}
          {page === "compras" && (
            <>
              <div className="rd-grid two">
                <section className="rd-card">
                  <div className="rd-card-head">
                    <h2>Reposição sugerida</h2>
                    <Badge tone="amber">Regra de mínimo</Badge>
                  </div>
                  {baixos.length ? (
                    baixos.map((p) => (
                      <div className="rd-task" key={str(p.id)}>
                        <i className="amber">▦</i>
                        <div>
                          <strong>{str(p.nome)}</strong>
                          <small>
                            {Number(p.fisico) - Number(p.reservado)} disponíveis · mínimo{" "}
                            {str(p.minimo)}
                          </small>
                        </div>
                        <button
                          onClick={() =>
                            setModal({
                              title: "Planejar compra",
                              op: "registro",
                              extra: { tipo: "COMPRA" },
                              fields: [
                                {
                                  key: "produto_id",
                                  label: "Produto",
                                  options: estoqueOptions,
                                  value: str(p.id),
                                },
                                amount("custo_unitario", "Custo unitário", str(p.custo)),
                                {
                                  key: "quantidade",
                                  label: "Quantidade",
                                  type: "integer",
                                  value: "10",
                                },
                                data.fornecedores.length
                                  ? {
                                      key: "fornecedor",
                                      label: "Fornecedor",
                                      options: data.fornecedores.map((f) => ({
                                        value: str(f.nome),
                                        label: str(f.nome),
                                      })),
                                    }
                                  : { key: "fornecedor", label: "Fornecedor" },
                                { key: "observacao", label: "Prazo / observação", required: false },
                              ],
                            })
                          }
                        >
                          Planejar
                        </button>
                      </div>
                    ))
                  ) : (
                    <Empty text="Estoque acima do mínimo." />
                  )}
                </section>
                <section className="rd-card">
                  <div className="rd-card-head">
                    <h2>Fornecedores</h2>
                    <button onClick={() => go("fornecedores")}>Gerenciar →</button>
                  </div>
                  {data.fornecedores.length ? (
                    data.fornecedores.slice(0, 6).map((f) => (
                      <div className="rd-task" key={str(f.id)}>
                        <i>⇲</i>
                        <div>
                          <strong>{str(f.nome)}</strong>
                          <small>
                            {f.prazo_entrega_dias == null
                              ? "Prazo não informado"
                              : `Entrega em ${str(f.prazo_entrega_dias)} dias`}
                          </small>
                        </div>
                      </div>
                    ))
                  ) : (
                    <Empty text="Nenhum fornecedor cadastrado." />
                  )}
                </section>
              </div>
              <section className="rd-card">
                <h2>Compras planejadas</h2>
                <Table
                  headers={["Produto", "Quantidade", "Fornecedor", "Recebimento"]}
                  rows={records("COMPRA").map((r) => {
                    const d = r.dados as Record<string, unknown>;
                    return [
                      str(prod(d.produto_id)?.nome),
                      str(d.quantidade),
                      str(d.fornecedor),
                      d.recebida_em ? (
                        <Badge tone="green">Recebida</Badge>
                      ) : (
                        <button
                          disabled={busy}
                          onClick={() =>
                            setModal({
                              title: "Confirmar recebimento físico",
                              op: "receber_compra",
                              extra: { id: r.id },
                              fields: [
                                {
                                  key: "confirmacao",
                                  label:
                                    "Confirme somente após conferir as unidades. Atualiza estoque, custo médio e contas a pagar locais.",
                                  value: "Recebimento total conferido",
                                },
                              ],
                            })
                          }
                        >
                          Conferir recebimento
                        </button>
                      ),
                    ];
                  })}
                />
              </section>
              <div className="rd-note">
                Receber atualiza estoque e custo médio e cria uma conta a pagar local. Não envia
                pedido nem pagamento ao fornecedor.
              </div>
            </>
          )}
          {page === "fiscal" && (
            <>
              <section className="rd-card rd-setup">
                <div className="rd-large-icon">▧</div>
                <h2>Prepare o caminho para sua NF-e</h2>
                <p>
                  A emissão real depende de um provedor fiscal, certificado e configuração
                  tributária homologada. Nenhuma nota foi emitida por esta versão local.
                </p>
                <div className="rd-checklist">
                  <span>✓ Catálogo com campo NCM</span>
                  <span>✓ Pedidos com valores informados</span>
                  <span>○ Escolher e conectar emissor</span>
                  <span>○ Cadastrar CNPJ, certificado e regime</span>
                  <span>○ Homologar emissão e retorno aos canais</span>
                </div>
                <button onClick={() => go("produtos")}>Revisar cadastro fiscal →</button>
              </section>
              <section className="rd-card">
                <div className="rd-card-head">
                  <h2>Produtos que precisam de NCM</h2>
                  <Badge tone="amber">{principais.filter((p) => !p.ncm).length}</Badge>
                </div>
                <Table
                  headers={["SKU", "Produto", "Pendência"]}
                  rows={products
                    .filter((p) => !p.ncm)
                    .map((p) => [
                      str(p.sku),
                      str(p.nome),
                      "Classificação fiscal ainda não informada",
                    ])}
                />
              </section>
            </>
          )}
          {page === "financeiro" && data.financeiroPermitido && (
            <>
              <div className="rd-kpis">
                {[
                  ["Receita registrada", money(data.resumo.bruto)],
                  ["Resultado local", money(data.resumo.resultado)],
                  [
                    "A pagar",
                    centMoney(
                      data.titulos
                        .filter((t) => t.tipo === "PAGAR" && t.estado === "ABERTO")
                        .reduce((a, t) => a + cents(t.valor), 0),
                    ),
                  ],
                  [
                    "A receber",
                    centMoney(
                      data.titulos
                        .filter((t) => t.tipo === "RECEBER" && t.estado === "ABERTO")
                        .reduce((a, t) => a + cents(t.valor), 0),
                    ),
                  ],
                ].map(([l, v]) => (
                  <div className="rd-kpi" key={l}>
                    <span>{l}</span>
                    <strong>{v}</strong>
                    <small>Base local · informações manuais</small>
                  </div>
                ))}
              </div>
              <div className="rd-toolbar">
                <span>Custos informados e não homologados com fontes externas.</span>
                <div>
                  <button
                    onClick={() =>
                      setModal({
                        title: "Registrar despesa no resultado",
                        op: "despesa",
                        fields: [
                          { key: "descricao", label: "Descrição" },
                          amount("valor", "Valor"),
                        ],
                      })
                    }
                  >
                    + Despesa
                  </button>
                  <button
                    className="primary"
                    onClick={() =>
                      setModal({
                        title: "Adicionar conta",
                        op: "titulo",
                        fields: [
                          { key: "descricao", label: "Descrição" },
                          {
                            key: "tipo",
                            label: "Tipo",
                            options: [
                              { value: "PAGAR", label: "A pagar" },
                              { value: "RECEBER", label: "A receber" },
                            ],
                          },
                          amount("valor", "Valor"),
                          {
                            key: "vencimento",
                            label: "Vencimento",
                            type: "date",
                            value: new Date().toISOString().slice(0, 10),
                          },
                        ],
                      })
                    }
                  >
                    + Conta
                  </button>
                </div>
              </div>
              <section className="rd-card">
                <div className="rd-card-head">
                  <h2>Resultado por pedido</h2>
                  <Badge tone="blue">Memória de cálculo</Badge>
                </div>
                <Table
                  headers={[
                    "Pedido",
                    "Canal",
                    "Receita",
                    "CMV",
                    "Outros custos",
                    "Resultado",
                    "Conferência",
                  ]}
                  rows={orders
                    .filter((o) => !["CANCELADO", "DEVOLVIDO"].includes(str(o.estado)))
                    .map((o) => [
                      str(o.numero),
                      str(o.canal),
                      centMoney(cents(o.preco) * Number(o.quantidade)),
                      centMoney(cents(o.custo_unitario) * Number(o.quantidade)),
                      centMoney(
                        ["comissao", "frete", "imposto", "ads", "embalagem", "desconto"].reduce(
                          (s, k) => s + cents(o[k]),
                          0,
                        ),
                      ),
                      <strong className={resultados(o) < 0 ? "rd-negative" : "rd-positive"}>
                        {centMoney(resultados(o))}
                      </strong>,
                      o.conciliado ? (
                        <Badge tone="green">Conferido manualmente</Badge>
                      ) : (
                        <button
                          onClick={() =>
                            setModal({
                              title: "Conferir repasse informado",
                              op: "conciliar",
                              extra: { id: o.id },
                              fields: [
                                amount(
                                  "valor",
                                  "Recebido (bruto menos comissão, frete e desconto)",
                                ),
                              ],
                            })
                          }
                        >
                          Conferir
                        </button>
                      ),
                    ])}
                />
              </section>
              <div className="rd-grid two">
                {[
                  ["Resultado por canal", data.porCanal ?? []],
                  ["Resultado por SKU", data.porSku ?? []],
                ].map(([title, rows]) => (
                  <section className="rd-card" key={str(title)}>
                    <h2>{str(title)}</h2>
                    <Table
                      headers={["Origem", "Resultado líquido registrado"]}
                      rows={(rows as Row[]).map((r) => [str(r.nome), money(r.resultado)])}
                    />
                    <p className="rd-note">
                      Inclui estornos e devoluções. Despesas gerais sem rateio aparecem somente no
                      total da operação.
                    </p>
                  </section>
                ))}
              </div>
              <section className="rd-card">
                <h2>Divergências de conferência</h2>
                <Table
                  headers={["Pedido", "Esperado", "Informado", "Data"]}
                  rows={(data.divergencias ?? []).map((r) => {
                    const d = r.dados as Record<string, unknown>;
                    return [
                      str(orders.find((o) => o.id === d.pedido_id)?.numero),
                      money(d.esperado),
                      money(d.recebido),
                      date(r.criado_em),
                    ];
                  })}
                />
              </section>
              <section className="rd-card">
                <div className="rd-card-head">
                  <h2>Contas a pagar e receber</h2>
                </div>
                <Table
                  headers={["Descrição", "Tipo", "Valor", "Vencimento", "Estado", "Ação"]}
                  rows={data.titulos.map((t) => [
                    str(t.descricao),
                    str(t.tipo),
                    money(t.valor),
                    str(t.vencimento).slice(0, 10),
                    str(t.estado),
                    t.estado === "ABERTO" ? (
                      <button onClick={() => command({ op: "baixar_titulo", id: t.id })}>
                        Registrar baixa
                      </button>
                    ) : (
                      "—"
                    ),
                  ])}
                />
              </section>
              <section className="rd-card">
                <div className="rd-card-head">
                  <h2>Razão de resultado</h2>
                  <span>Correções preservam lançamentos anteriores</span>
                </div>
                <Table
                  headers={["Data", "Pedido", "Componente", "Valor", "Origem"]}
                  rows={data.lancamentos.map((l) => [
                    date(l.criado_em),
                    str(orders.find((o) => o.id === l.pedido_id)?.numero) || "Geral",
                    str(l.tipo),
                    <span className={cents(l.valor) < 0 ? "rd-negative" : "rd-positive"}>
                      {money(l.valor)}
                    </span>,
                    str(l.fonte),
                  ])}
                />
              </section>
            </>
          )}
          {page === "precos" && data.financeiroPermitido && (
            <Pricing
              products={vendaveis}
              listings={listings}
              onPropose={(id, preco) =>
                setModal({
                  title: "Propor preço simulado",
                  op: "propor_preco",
                  extra: { id },
                  fields: [
                    amount("preco", "Novo preço", preco),
                    {
                      key: "motivo",
                      label: "Motivo",
                      value: "Simulação de margem — premissas manuais",
                    },
                  ],
                })
              }
            />
          )}
          {page === "missao" && (
            <>
              <div className="rd-grid two">
                <section className="rd-card">
                  <div className="rd-card-head">
                    <h2>Aprovações de preço</h2>
                    <Badge tone="purple">{pendentes.length} pendentes</Badge>
                  </div>
                  {pendentes.length ? (
                    pendentes.map((a) => (
                      <div className="rd-approval" key={str(a.id)}>
                        <Badge tone="purple">APROVAÇÃO HUMANA</Badge>
                        <h3>{str(listings.find((l) => l.id === a.anuncio_id)?.titulo)}</h3>
                        <p>{str(a.motivo)}</p>
                        <div className="rd-price-change">
                          <span>{money(a.antes)}</span> → <strong>{money(a.depois)}</strong>
                        </div>
                        <small>A execução altera somente o anúncio local.</small>
                        {can("DONO", "GESTOR") && (
                          <div className="rd-row-actions">
                            <button
                              disabled={busy}
                              className="primary"
                              onClick={() => command({ op: "aprovar", id: a.id })}
                            >
                              Aprovar e aplicar localmente
                            </button>
                            <button
                              disabled={busy}
                              onClick={() => command({ op: "rejeitar", id: a.id })}
                            >
                              Rejeitar
                            </button>
                          </div>
                        )}
                      </div>
                    ))
                  ) : (
                    <Empty text="Nenhuma aprovação pendente." />
                  )}
                </section>
                <section className="rd-card">
                  <div className="rd-card-head">
                    <h2>Profit Guardian</h2>
                    <Badge tone="amber">Regras locais</Badge>
                  </div>
                  {data.financeiroPermitido &&
                    negativos.map((o) => (
                      <div className="rd-task" key={str(o.id)}>
                        <i className="red">!</i>
                        <div>
                          <strong>{str(o.numero)} · resultado negativo</strong>
                          <small>{centMoney(resultados(o))} após custos informados</small>
                        </div>
                        <button onClick={() => go("precos")}>Simular</button>
                      </div>
                    ))}
                  {!negativos.length && (
                    <Empty text="Sem prejuízo identificado nos pedidos locais." />
                  )}
                  {baixos.map((p) => (
                    <div className="rd-task" key={str(p.id)}>
                      <i className="amber">▦</i>
                      <div>
                        <strong>{str(p.nome)}</strong>
                        <small>Estoque no mínimo</small>
                      </div>
                      <button onClick={() => go("compras")}>Ver reposição</button>
                    </div>
                  ))}
                </section>
              </div>
            </>
          )}
          {page === "inbox" && (
            <>
              <div className="rd-toolbar">
                <Badge tone="amber">Caixa de demonstração · sem envio externo</Badge>
                <button
                  className="primary"
                  onClick={() =>
                    setModal({
                      title: "Adicionar conversa local",
                      op: "registro",
                      extra: { tipo: "MENSAGEM" },
                      fields: [
                        { key: "cliente", label: "Cliente fictício" },
                        channelField,
                        { key: "mensagem", label: "Mensagem recebida", type: "textarea" },
                      ],
                    })
                  }
                >
                  + Conversa local
                </button>
              </div>
              <div className="rd-grid two">
                <RecordCards title="Conversas" records={records("MENSAGEM")} />
                <section className="rd-card">
                  <div className="rd-card-head">
                    <h2>Política de atendimento</h2>
                    <Badge tone="purple">Supervisão</Badge>
                  </div>
                  <div className="rd-checklist">
                    <span>○ Status e FAQ: automação após avaliação</span>
                    <span>○ Troca, atraso e defeito: aprovação humana</span>
                    <span>○ Fraude e exceção financeira: humano obrigatório</span>
                  </div>
                  <p className="rd-note">
                    Os registros desta tela são anotações locais. Recebimento, resposta sugerida
                    contextual e envio via marketplace ainda precisam ser integrados.
                  </p>
                  <button onClick={() => go("pedidos")}>Consultar pedidos →</button>
                </section>
              </div>
            </>
          )}
          {page === "studio" && (
            <>
              <div className="rd-grid two">
                <section className="rd-card rd-brand-preview">
                  <div className="rd-eyebrow">BRAND PROFILE</div>
                  <h2>
                    Sua identidade
                    <br />
                    em cada anúncio.
                  </h2>
                  <div className="rd-swatches">
                    <i style={{ background: "#2563eb" }} />
                    <i style={{ background: "#8b5cf6" }} />
                    <i style={{ background: "#10b981" }} />
                    <i style={{ background: "#f8fafc" }} />
                  </div>
                  <p>Salve cores, tom de voz e orientações da marca para sua equipe.</p>
                  <button
                    className="primary"
                    onClick={() =>
                      setModal({
                        title: "Salvar perfil da marca",
                        op: "registro",
                        extra: { tipo: "MARCA" },
                        fields: [
                          { key: "nome", label: "Marca" },
                          { key: "cores", label: "Cores (ex.: #2563EB)" },
                          { key: "tom", label: "Tom de voz" },
                          {
                            key: "regras",
                            label: "Regras de imagem e identidade",
                            type: "textarea",
                          },
                        ],
                      })
                    }
                  >
                    + Perfil de marca
                  </button>
                </section>
                <section className="rd-card">
                  <div className="rd-card-head">
                    <h2>Listing Agent</h2>
                    <Badge>Rascunho por regra</Badge>
                  </div>
                  <p>
                    Selecione um produto para preparar o título de um anúncio. Você revisa antes de
                    salvar.
                  </p>
                  <div className="rd-stack">
                    {principais.slice(0, 5).map((p) => (
                      <button
                        key={str(p.id)}
                        onClick={() =>
                          setModal({
                            title: "Revisar rascunho do anúncio",
                            op: "anuncio",
                            extra: { produto_id: p.id },
                            fields: [
                              channelField,
                              {
                                key: "titulo",
                                label: "Título sugerido",
                                value: `${p.nome}${p.marca ? " | " + p.marca : ""}`,
                              },
                              amount("preco", "Preço", str(p.preco)),
                            ],
                          })
                        }
                      >
                        {str(p.nome)} →
                      </button>
                    ))}
                  </div>
                  <p className="rd-note">
                    Edição e geração de imagens não habilitadas. Dependem de provedor e avaliação
                    visual.
                  </p>
                </section>
              </div>
              <RecordCards title="Perfis salvos" records={records("MARCA")} />
            </>
          )}
          {page === "clientes" && (
            <Clientes
              clientes={data.clientes}
              vendedores={data.vendedores ?? []}
              podeEditar={can("DONO", "GESTOR", "ATENDIMENTO")}
              executar={commandResult}
              recarregar={refresh}
            />
          )}
          {page === "fornecedores" && (
            <Clientes
              modo="fornecedores"
              clientes={data.clientes}
              vendedores={data.vendedores ?? []}
              produtoFornecedores={data.produtoFornecedores}
              podeEditar={can("DONO", "GESTOR", "ATENDIMENTO", "ESTOQUE")}
              executar={commandResult}
              recarregar={refresh}
            />
          )}
          {page === "vendedores" && (
            <Vendedores
              vendedores={data.vendedores ?? []}
              usuariosSistema={data.usuariosSistema ?? []}
              podeEditar={can("DONO", "GESTOR")}
              podeVerDetalhe={can("DONO", "GESTOR", "FINANCEIRO")}
              podeAlterarSenha={can("DONO")}
              executar={commandResult}
            />
          )}
          {page === "categorias" && (
            <Categorias
              categorias={data.categorias}
              categoriaCanais={data.categoriaCanais ?? []}
              produtos={products}
              podeEditar={can("DONO", "GESTOR", "MARKETING")}
              busy={busy}
              executar={commandResult}
            />
          )}
          {page === "embalagens" && (
            <Embalagens
              produtos={products}
              embalagens={data.embalagens}
              veCusto={data.financeiroPermitido}
              podeEditar={can("DONO", "GESTOR", "ESTOQUE")}
              busy={busy}
              executar={commandResult}
            />
          )}
          {page === "promocoes" && (
            <Promocoes
              promocoes={data.promocoes}
              produtos={products}
              podeEditar={can("DONO", "GESTOR", "MARKETING")}
              veCusto={data.financeiroPermitido}
              abrirModal={setModal}
            />
          )}
          {page === "relatorios" && data.financeiroPermitido && <Relatorios />}
          {page === "mercado" && (
            <Mercado
              registros={data.registros}
              produtos={products}
              anuncios={listings}
              podeEditar={can("DONO", "GESTOR", "MARKETING")}
              veCusto={data.financeiroPermitido}
              abrirModal={setModal}
              go={go}
            />
          )}
          {page === "ia" && (
            <>
              <Chat />
              <section className="rd-card">
                <div className="rd-card-head">
                  <h2>Seus agentes</h2>
                  <Badge tone="purple">Registro de capacidades</Badge>
                </div>
                <div className="rd-agent-grid">
                  {data.agentes.map((a) => (
                    <div key={a.nome}>
                      <span className="rd-agent-icon">✳</span>
                      <h3>{a.nome}</h3>
                      <Badge
                        tone={
                          a.estado.includes("Requer") || a.estado === "Desligado" ? "gray" : "blue"
                        }
                      >
                        {a.estado}
                      </Badge>
                      <p>{a.descricao}</p>
                    </div>
                  ))}
                </div>
              </section>
            </>
          )}
          {page === "integracoes" && (
            <>
              <div className="rd-integration-grid">
                {[
                  ...canais,
                  "Emissor de NF-e",
                  "WhatsApp",
                  "Instagram",
                  "E-mail",
                  "Provedor de IA",
                  "Ads",
                ].map((c, i) => (
                  <section className="rd-card" key={c}>
                    <span className={`rd-channel c${i % 4}`}>{c.slice(0, 2)}</span>
                    <h2>{c}</h2>
                    <Badge>Não conectado</Badge>
                    <p>
                      {i < 4
                        ? "App, autorização do vendedor e homologação por capacidade são necessários."
                        : "Configuração de provedor e credenciais ainda não realizadas."}
                    </p>
                    <button
                      onClick={() =>
                        setNotice(
                          `${c}: configuração externa pendente. Nenhum token ou senha é solicitado nesta prévia local.`,
                        )
                      }
                    >
                      Ver requisitos →
                    </button>
                  </section>
                ))}
              </div>
              <div className="rd-note">
                CSV, XLS, XLSX e XML de catálogo funcionam localmente. Não há sincronização
                automática de marketplace, emissão fiscal ou envio de mensagens.
              </div>
            </>
          )}
          {page === "auditoria" && can("DONO") && (
            <section className="rd-card">
              <Table
                headers={["Data", "Operação", "Recurso", "Detalhes"]}
                rows={data.auditoria.map((a) => [
                  date(a.criado_em),
                  str(a.operacao),
                  str(a.recurso).slice(0, 24),
                  <details>
                    <summary>Ver registro</summary>
                    <pre>
                      {typeof a.detalhes === "object"
                        ? JSON.stringify(a.detalhes, null, 2)
                        : str(a.detalhes)}
                    </pre>
                  </details>,
                ])}
              />
            </section>
          )}
          {page === "guia" && <Guide step={tour} onStep={setTour} go={go} />}
          {page === "configuracoes" && (
            <Configuracoes
              go={go}
              empresa={data.empresa ?? {}}
              usuarios={data.usuarios ?? []}
              papel={data.usuario.papel}
              euId={data.usuario.id}
              configuracoes={data.configuracoes ?? {}}
              executar={commandResult}
              atualizar={refresh}
              avisar={setNotice}
            />
          )}
          {!data.financeiroPermitido && ["financeiro", "precos", "relatorios"].includes(page) && (
            <Empty text="Seu cargo não tem acesso a dados financeiros." />
          )}
          <footer className="rd-footer">
            <span>
              radar. <span>Mais controle. Mais clareza. Mais automação.</span>
            </span>
            <span>Local · PostgreSQL · sem Docker</span>
          </footer>
        </main>
      </div>
      {modal && (
        <div className="rd-modal-backdrop" onClick={() => !busy && setModal(null)}>
          <section
            className="rd-modal"
            role="dialog"
            aria-modal="true"
            aria-label={modal.title}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rd-card-head">
              <h2>{modal.title}</h2>
              <button aria-label="Fechar" disabled={busy} onClick={() => setModal(null)}>
                ×
              </button>
            </div>
            <form
              onSubmit={async (e: FormEvent<HTMLFormElement>) => {
                e.preventDefault();
                const form = new FormData(e.currentTarget);
                const values: Record<string, unknown> = {};
                modal.fields.forEach((f) => {
                  const v = String(form.get(f.key) ?? "");
                  values[f.key] =
                    f.type === "integer"
                      ? v === "" && f.required === false
                        ? ""
                        : Number(v)
                      : f.key === "retornar_estoque"
                        ? v === "true"
                        : v;
                });
                const body =
                  modal.op === "registro"
                    ? { op: modal.op, ...modal.extra, dados: values }
                    : { op: modal.op, ...modal.extra, ...values };
                if (await command(body)) setModal(null);
              }}
            >
              <div className="rd-form-grid">
                {modal.fields.map((f) => (
                  <label key={f.key} className={f.type === "textarea" ? "wide" : ""}>
                    {f.label}
                    {f.options ? (
                      <select
                        name={f.key}
                        defaultValue={f.value ?? f.options[0]?.value}
                        required={f.required !== false}
                      >
                        {f.options.map((o) => (
                          <option key={o.value} value={o.value}>
                            {o.label}
                          </option>
                        ))}
                      </select>
                    ) : f.type === "textarea" ? (
                      <textarea
                        name={f.key}
                        defaultValue={f.value}
                        required={f.required !== false}
                      />
                    ) : (
                      <input
                        name={f.key}
                        type={f.type === "integer" ? "number" : (f.type ?? "text")}
                        step={f.type === "integer" ? "1" : "0.01"}
                        defaultValue={f.value}
                        required={f.required !== false}
                        maxLength={f.key === "ncm" ? 8 : 500}
                      />
                    )}
                  </label>
                ))}
              </div>
              {error && (
                <div className="rd-error" role="alert">
                  {error}
                </div>
              )}
              <div className="rd-modal-foot">
                <span>Alteração apenas local</span>
                <button type="button" disabled={busy} onClick={() => setModal(null)}>
                  Voltar
                </button>
                <button disabled={busy} className="primary">
                  {busy ? "Salvando…" : "Confirmar e salvar"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

// Espera até ~7 minutos pelo servidor acordar (42 × 10 s). No plano gratuito
// do Render ele já levou 3 min e meio para ligar depois de dias parado.
const ESPERA_MAX_TENTATIVAS = 42;
const ESPERA_ENTRE_TENTATIVAS_MS = 10_000;

function Login({ onLogin }: { onLogin: () => Promise<void> }) {
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [acordando, setAcordando] = useState(0),
    [servidor, setServidor] = useState<"acordando" | "pronto">("acordando");
  // Começa a acordar o servidor assim que a tela abre, enquanto a pessoa digita.
  // Qualquer resposta que não seja 5xx (até 401, sem sessão) quer dizer que ele ligou.
  useEffect(() => {
    let vivo = true;
    (async () => {
      for (let i = 0; vivo && i < ESPERA_MAX_TENTATIVAS; i++) {
        try {
          const r = await fetch("/api/sessao", { credentials: "include" });
          if (r.status < 500) {
            if (vivo) setServidor("pronto");
            return;
          }
        } catch {
          // rede ou servidor ainda subindo: tenta de novo
        }
        await new Promise((pronto) => setTimeout(pronto, ESPERA_ENTRE_TENTATIVAS_MS));
      }
    })();
    return () => {
      vivo = false;
    };
  }, []);
  return (
    <div className="radar-app rd-login">
      <div className="rd-login-story">
        <a className="rd-brand" href="/radar">
          ◉ radar.
        </a>
        <Badge>COMMERCE OPERATING SYSTEM</Badge>
        <h1>
          A operação inteira.
          <br />
          Uma visão mais clara.
        </h1>
        <p>Produtos, pedidos, estoque, resultado e inteligência trabalhando juntos.</p>
        <div className="rd-login-steps">
          {["Cadastre seus produtos", "Organize sua operação", "Entenda seu resultado"].map(
            (x, i) => (
              <span key={x}>
                <b>0{i + 1}</b>
                {x}
              </span>
            ),
          )}
        </div>
        <small>Ambiente local de avaliação · sem conexões externas</small>
      </div>
      <div className="rd-login-form">
        <div>
          <span className="rd-eyebrow">BEM-VINDA AO RADAR</span>
          <h2>Seu próximo passo começa aqui.</h2>
          <p>Entre para explorar a operação local.</p>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              setError("");
              const f = new FormData(e.currentTarget);
              const corpo = JSON.stringify({ email: f.get("email"), senha: f.get("senha") });
              try {
                // No plano gratuito o servidor dorme sem uso e leva alguns minutos para
                // acordar. Enquanto responde 5xx (ou nem responde), esperamos e tentamos de novo.
                for (let tentativa = 1; ; tentativa++) {
                  let status = 0;
                  try {
                    const r = await fetch("/api/login", {
                      method: "POST",
                      headers: { "Content-Type": "application/json" },
                      body: corpo,
                    });
                    status = r.status;
                  } catch {
                    status = 0;
                  }
                  if (status >= 200 && status < 300) break;
                  if (status === 401 || status === 400)
                    throw new Error("E-mail ou senha incorretos.");
                  if (tentativa >= ESPERA_MAX_TENTATIVAS)
                    throw new Error(
                      "O servidor não respondeu. Aguarde um pouco e tente entrar de novo.",
                    );
                  setAcordando(tentativa);
                  await new Promise((pronto) => setTimeout(pronto, ESPERA_ENTRE_TENTATIVAS_MS));
                }
                await onLogin();
              } catch (e) {
                setError((e as Error).message);
              } finally {
                setAcordando(0);
                setBusy(false);
              }
            }}
          >
            <label>
              E-mail
              <input
                name="email"
                type="email"
                defaultValue="dono@demo.plataforma"
                required
                autoComplete="username"
              />
            </label>
            <label>
              Senha
              <input
                name="senha"
                type="password"
                defaultValue="demo1234"
                required
                autoComplete="current-password"
              />
            </label>
            {acordando > 0 ? (
              <div className="rd-note" role="status">
                Acordando o servidor… já se passaram{" "}
                {Math.round((acordando * ESPERA_ENTRE_TENTATIVAS_MS) / 1000)} s. Depois de alguns
                dias sem uso pode levar até 5 minutos. Não precisa clicar de novo.
              </div>
            ) : (
              <div className="rd-note" role="status">
                {servidor === "pronto"
                  ? "● Servidor pronto."
                  : "○ Preparando o servidor… se ele estava parado, pode levar alguns minutos."}
              </div>
            )}
            {error && (
              <div className="rd-error" role="alert">
                {error}
              </div>
            )}
            <button className="primary" disabled={busy}>
              {acordando > 0 ? "Aguardando o servidor…" : busy ? "Entrando…" : "Entrar no Radar →"}
            </button>
          </form>
          <div className="rd-note">
            Acesso de demonstração preenchido para este computador. Não use dados pessoais ou senhas
            de marketplaces nesta avaliação.
          </div>
        </div>
      </div>
    </div>
  );
}

function RecordCards({ records, title }: { records: Row[]; title: string }) {
  return (
    <section className="rd-card">
      <div className="rd-card-head">
        <h2>{title}</h2>
        <Badge>{records.length}</Badge>
      </div>
      {records.length ? (
        <div className="rd-records">
          {records.map((r) => (
            <article key={str(r.id)}>
              {Object.entries(r.dados as Record<string, unknown>).map(([k, v]) => (
                <div key={k}>
                  <small>{k}</small>
                  <p>
                    {k === "url" && str(v).startsWith("https://") ? (
                      <a href={str(v)} target="_blank" rel="noopener noreferrer">
                        Abrir referência ↗
                      </a>
                    ) : (
                      str(v)
                    )}
                  </p>
                </div>
              ))}
              <small>{date(r.criado_em)}</small>
            </article>
          ))}
        </div>
      ) : (
        <Empty />
      )}
    </section>
  );
}

function Importer({
  busy,
  onImport,
}: {
  busy: boolean;
  onImport: (items: Record<string, unknown>[]) => Promise<boolean>;
}) {
  const [rows, setRows] = useState<string[][]>([]),
    [headers, setHeaders] = useState<string[]>([]),
    [mapping, setMapping] = useState<Record<string, string>>({}),
    [name, setName] = useState(""),
    [error, setError] = useState(""),
    [done, setDone] = useState(false);
  const fields = ["sku", "nome", "custo", "preco", "saldo", "marca", "ncm", "descricao"];
  function parseCsv(text: string) {
    const first = text.split(/\r?\n/)[0];
    const sep = first.split(";").length > first.split(",").length ? ";" : ",";
    const result: string[][] = [];
    let row: string[] = [],
      cell = "",
      quoted = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (c === '"') {
        if (quoted && text[i + 1] === '"') {
          cell += '"';
          i++;
        } else quoted = !quoted;
      } else if (c === sep && !quoted) {
        row.push(cell);
        cell = "";
      } else if ((c === "\n" || c === "\r") && !quoted) {
        if (c === "\r" && text[i + 1] === "\n") i++;
        row.push(cell);
        if (row.some(Boolean)) result.push(row);
        row = [];
        cell = "";
      } else cell += c;
    }
    row.push(cell);
    if (row.some(Boolean)) result.push(row);
    if (quoted) throw new Error("Aspas não fechadas no CSV.");
    return result;
  }
  async function file(f: File) {
    setError("");
    setDone(false);
    try {
      if (f.size > 10000000) throw new Error("Use arquivo de até 10 MB.");
      let table: string[][];
      if (f.name.toLowerCase().endsWith(".xml")) {
        const raw = await f.text();
        if (/<!DOCTYPE|<!ENTITY/i.test(raw)) throw new Error("XML com entidades não é permitido.");
        const doc = new DOMParser().parseFromString(raw, "application/xml");
        if (doc.querySelector("parsererror")) throw new Error("XML inválido.");
        table = [
          ["sku", "nome", "custo", "preco", "saldo", "ncm"],
          ...Array.from(doc.getElementsByTagName("det")).map((e) => {
            const get = (k: string) => e.getElementsByTagName(k)[0]?.textContent ?? "";
            return [get("cProd"), get("xProd"), get("vUnCom"), "0", "0", get("NCM")];
          }),
        ];
      } else if (/\.csv$/i.test(f.name)) {
        table = parseCsv((await f.text()).replace(/^\uFEFF/, ""));
      } else if (/\.xlsx?$/i.test(f.name)) {
        const XLSX = await import("xlsx");
        const wb = XLSX.read(await f.arrayBuffer(), {
          type: "array",
          sheetRows: 3002,
          cellFormula: false,
          cellHTML: false,
        });
        const ws = wb.Sheets[wb.SheetNames[0]];
        if (!ws) throw new Error("Planilha vazia.");
        table = XLSX.utils
          .sheet_to_json<unknown[]>(ws, { header: 1, defval: "", raw: true })
          .map((row) => row.map((v) => String(v ?? "")));
      } else throw new Error("Use CSV, XLS, XLSX ou XML.");
      table = table.filter((row) => row.some((cell) => cell.trim()));
      if (table.length < 2) throw new Error("Arquivo sem produtos.");
      if (table.length > 3001) throw new Error("Importe até 3.000 produtos por arquivo.");
      const head = table[0];
      const aliases: Record<string, string[]> = {
        sku: ["sku", "codigo", "código", "cprod"],
        nome: ["nome", "produto", "descricao", "descrição", "xprod"],
        custo: ["custo", "preco compra", "preço compra", "vUnCom"],
        preco: ["preco", "preço", "venda"],
        saldo: ["saldo", "estoque"],
        marca: ["marca"],
        ncm: ["ncm"],
        descricao: ["descricao", "descrição"],
      };
      const map: Record<string, string> = {};
      fields.forEach((k) => {
        map[k] = str(
          head.findIndex((h) => aliases[k].some((a) => h.toLowerCase() === a.toLowerCase())),
        );
      });
      const saved = localStorage.getItem("radar-import-" + head.join("|"));
      if (saved) Object.assign(map, JSON.parse(saved));
      setMapping(map);
      setHeaders(head);
      setRows(table.slice(1));
      setName(f.name);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  function items() {
    return rows.map((r) => {
      const obj: Record<string, unknown> = {};
      fields.forEach((k) => {
        let v = Number(mapping[k]) >= 0 ? (r[Number(mapping[k])] ?? "") : "";
        if (["custo", "preco"].includes(k)) {
          v = v.replace(/R\$|\s/g, "");
          if (v.includes(",")) v = v.replace(/\./g, "").replace(",", ".");
          obj[k] = v || "0";
        } else obj[k] = k === "saldo" ? Number(v || 0) : v;
      });
      return obj;
    });
  }
  return (
    <>
      <div className="rd-import-steps">
        <span className="active">1 · Enviar arquivo</span>
        <i>→</i>
        <span className={rows.length ? "active" : ""}>2 · Mapear e conferir</span>
        <i>→</i>
        <span className={done ? "active" : ""}>3 · Importar</span>
      </div>
      <section
        className="rd-upload"
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          if (e.dataTransfer.files[0]) file(e.dataTransfer.files[0]);
        }}
      >
        <span>↥</span>
        <h2>Arraste seu catálogo para cá</h2>
        <p>CSV, XLS, XLSX ou XML de NF-e · até 10 MB · até 3.000 produtos</p>
        <label className="rd-file-label">
          Escolher arquivo
          <input
            type="file"
            accept=".csv,.xml,.xlsx,.xls"
            onChange={(e) => e.target.files?.[0] && file(e.target.files[0])}
          />
        </label>
        <small>O mapeamento é sugerido por regras locais. Você confirma antes de gravar.</small>
      </section>
      {error && (
        <div className="rd-error" role="alert">
          {error}
        </div>
      )}
      {rows.length > 0 && (
        <>
          <section className="rd-card">
            <div className="rd-card-head">
              <h2>{name}</h2>
              <Badge tone="blue">{rows.length} produtos</Badge>
            </div>
            <div className="rd-form-grid">
              {fields.map((k) => (
                <label key={k}>
                  {k}
                  <select
                    value={mapping[k]}
                    onChange={(e) => setMapping({ ...mapping, [k]: e.target.value })}
                  >
                    <option value="-1">Não importar / vazio</option>
                    {headers.map((h, i) => (
                      <option key={i} value={i}>
                        {h}
                      </option>
                    ))}
                  </select>
                </label>
              ))}
            </div>
            <p className="rd-note">
              XML cria pré-cadastro com saldo zero. Confirme o recebimento físico em Estoque.
              Produtos duplicados bloqueiam o lote sem gravação parcial.
            </p>
          </section>
          <section className="rd-card">
            <div className="rd-card-head">
              <h2>Prévia das primeiras 10 linhas</h2>
            </div>
            <Table
              headers={fields}
              rows={items()
                .slice(0, 10)
                .map((i) => fields.map((k) => str(i[k])))}
            />
            <div className="rd-modal-foot">
              <span>O template será salvo neste navegador.</span>
              <button
                className="primary"
                disabled={busy || done}
                onClick={async () => {
                  if (await onImport(items())) {
                    localStorage.setItem(
                      "radar-import-" + headers.join("|"),
                      JSON.stringify(mapping),
                    );
                    setDone(true);
                  }
                }}
              >
                {done ? "✓ Importado" : busy ? "Importando…" : `Confirmar ${rows.length} produtos`}
              </button>
            </div>
          </section>
        </>
      )}
    </>
  );
}

function Pricing({
  products,
  listings,
  onPropose,
}: {
  products: Row[];
  listings: Row[];
  onPropose: (id: string, price: string) => void;
}) {
  const [pid, setPid] = useState(str(products[0]?.id)),
    [price, setPrice] = useState("99.90"),
    [fee, setFee] = useState("16"),
    [tax, setTax] = useState("6"),
    [shipping, setShipping] = useState("12.00"),
    [ads, setAds] = useState("5.00"),
    [margin, setMargin] = useState("15");
  const p = products.find((x) => x.id === pid);
  const cost = cents(p?.custo);
  const percent = (Number(fee) + Number(tax)) / 100;
  const revenue = cents(price);
  const fixed = cost + cents(shipping) + cents(ads);
  const profit = Math.round(revenue * (1 - percent)) - fixed;
  const denom = 1 - percent - Number(margin) / 100;
  const min = denom > 0 ? Math.ceil(fixed / denom) : null;
  return (
    <div className="rd-grid two">
      <section className="rd-card">
        <div className="rd-card-head">
          <h2>Monte seu cenário</h2>
          <Badge>Estimativa</Badge>
        </div>
        <div className="rd-form-grid">
          <label className="wide">
            Produto
            <select value={pid} onChange={(e) => setPid(e.target.value)}>
              {products.map((p) => (
                <option key={str(p.id)} value={str(p.id)}>
                  {str(p.nome)}
                </option>
              ))}
            </select>
          </label>
          {[
            ["Preço de venda", price, setPrice],
            ["Comissão %", fee, setFee],
            ["Imposto %", tax, setTax],
            ["Frete por venda", shipping, setShipping],
            ["Ads por venda", ads, setAds],
            ["Margem desejada %", margin, setMargin],
          ].map(([l, v, s]) => (
            <label key={str(l)}>
              {str(l)}
              <input
                type="number"
                min="0"
                step="0.01"
                value={str(v)}
                onChange={(e) => (s as (v: string) => void)(e.target.value)}
              />
            </label>
          ))}
        </div>
        <p className="rd-note">
          Taxas manuais ilustrativas. Não inclui regras por faixa, despesas gerais ou custos não
          preenchidos. Não é garantia de margem real.
        </p>
      </section>
      <section className="rd-card rd-pricing-result">
        <Badge tone="purple">SIMULAÇÃO LOCAL</Badge>
        <h2>
          Conheça sua margem
          <br />
          antes de mudar o preço.
        </h2>
        <span>Resultado estimado por unidade</span>
        <strong className={profit < 0 ? "rd-negative" : "rd-positive"}>{centMoney(profit)}</strong>
        <div className="rd-breakdown">
          {[
            ["Venda", revenue],
            ["Custo do produto", -cost],
            ["Comissão + imposto", -Math.round(revenue * percent)],
            ["Frete + Ads", -cents(shipping) - cents(ads)],
          ].map(([l, v]) => (
            <div key={l}>
              <span>{l}</span>
              <b>{centMoney(Number(v))}</b>
            </div>
          ))}
        </div>
        <div className="rd-price-floor">
          <span>Preço para a margem alvo de {margin}%</span>
          <b>{min === null ? "Cenário inviável" : centMoney(min)}</b>
        </div>
        {listings
          .filter((a) => a.produto_id === pid)
          .map((a) => (
            <button
              className="primary"
              key={str(a.id)}
              disabled={min === null}
              onClick={() => min !== null && onPropose(str(a.id), (min / 100).toFixed(2))}
            >
              Propor para {str(a.canal)} →
            </button>
          ))}
      </section>
    </div>
  );
}

function Chat() {
  const [text, setText] = useState(""),
    [busy, setBusy] = useState(false),
    [messages, setMessages] = useState<{ q: string; a: string; refs: string[] }[]>([]),
    [error, setError] = useState("");
  async function ask(q: string) {
    setBusy(true);
    setError("");
    try {
      const r = await call("/perguntar", { texto: q });
      setMessages([...messages, { q, a: r.resposta, refs: r.fontes }]);
      setText("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="rd-card rd-chat">
      <div className="rd-chat-welcome">
        <span>✳</span>
        <h2>O que vamos entender hoje?</h2>
        <p>Consultas locais. Sem LLM externo, sem ações escondidas.</p>
      </div>
      <div className="rd-suggestions">
        {[
          "Quanto sobrou na operação?",
          "Como está o estoque?",
          "Quais pedidos estão pendentes?",
        ].map((q) => (
          <button disabled={busy} key={q} onClick={() => ask(q)}>
            {q} ↗
          </button>
        ))}
      </div>
      <div aria-live="polite">
        {messages.map((m, i) => (
          <div key={i} className="rd-chat-pair">
            <div className="rd-chat-question">{m.q}</div>
            <div className="rd-chat-answer">
              <strong>✳ Radar</strong>
              <p>{m.a}</p>
              <details>
                <summary>Como cheguei nessa resposta</summary>
                <p>
                  {m.refs.join(" · ") ||
                    "Pergunta fora das ferramentas disponíveis. Nenhum dado inventado."}
                </p>
              </details>
            </div>
          </div>
        ))}
      </div>
      {error && (
        <div className="rd-error" role="alert">
          {error}
        </div>
      )}
      <form
        className="rd-chat-form"
        onSubmit={(e) => {
          e.preventDefault();
          ask(text);
        }}
      >
        <input
          aria-label="Pergunte ao Radar"
          maxLength={500}
          required
          placeholder="Pergunte sobre resultado, estoque ou pedidos…"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        <button className="primary" disabled={busy}>
          {busy ? "Consultando…" : "Enviar ↑"}
        </button>
      </form>
      <small>Voz ainda não habilitada. O áudio não é capturado nesta versão.</small>
    </section>
  );
}

const slides = [
  {
    title: "Seu Radar começa pelo produto",
    text: "Cadastre um SKU ou importe um catálogo. O mesmo produto poderá ter um anúncio diferente em cada marketplace.",
    page: "produtos",
    cta: "Abrir produtos",
    steps: [
      "Cadastre nome, SKU e custo",
      "Informe saldo e dados fiscais",
      "Prepare anúncios por canal",
    ],
    icon: "▣",
  },
  {
    title: "Uma venda movimenta a operação",
    text: "Ao criar um pedido local, o Radar reserva o saldo. Na expedição simulada, o físico é baixado uma única vez.",
    page: "pedidos",
    cta: "Ver pedidos",
    steps: [
      "Pedido criado → estoque reservado",
      "Separação → conferência",
      "Expedição local → baixa física",
    ],
    icon: "▢",
  },
  {
    title: "Veja de onde vem o resultado",
    text: "Abra Financeiro e acompanhe receita, CMV, comissão, frete, impostos, Ads e despesas. Cada valor fica registrado.",
    page: "financeiro",
    cta: "Entender o resultado",
    steps: [
      "Custos congelados no pedido",
      "Lançamentos com origem",
      "Conferência manual de repasse",
    ],
    icon: "◈",
  },
  {
    title: "Você decide. O Radar registra.",
    text: "Simule um preço e envie uma proposta. Uma pessoa autorizada aprova; o histórico mostra a mudança.",
    page: "precos",
    cta: "Simular um preço",
    steps: [
      "Simulação com premissas visíveis",
      "Aprovação na Central de ações",
      "Aplicação local e auditoria",
    ],
    icon: "✓",
  },
  {
    title: "Inteligência com limites claros",
    text: "Converse com o copiloto, salve referências e perfis de marca. Integrações externas ficam identificadas como não conectadas.",
    page: "ia",
    cta: "Perguntar ao Radar",
    steps: [
      "Pergunte sobre dados locais",
      "Confira a origem da resposta",
      "Veja capacidades no registro de agentes",
    ],
    icon: "✳",
  },
];
function Guide({
  step,
  onStep,
  go,
}: {
  step: number;
  onStep: (n: number) => void;
  go: (p: string) => void;
}) {
  const s = slides[step];
  return (
    <>
      <div className="rd-guide">
        <aside>
          {slides.map((x, i) => (
            <button className={i === step ? "active" : ""} key={x.title} onClick={() => onStep(i)}>
              <b>0{i + 1}</b>
              <span>{x.title}</span>
            </button>
          ))}
        </aside>
        <section>
          <Badge tone="purple">
            PASSO {step + 1} DE {slides.length}
          </Badge>
          <div className="rd-guide-icon">{s.icon}</div>
          <h2>{s.title}</h2>
          <p>{s.text}</p>
          <div className="rd-guide-flow">
            {s.steps.map((x, i) => (
              <div key={x}>
                <span>{i + 1}</span>
                {x}
              </div>
            ))}
          </div>
          <div className="rd-row-actions">
            <button className="primary" onClick={() => go(s.page)}>
              {s.cta} →
            </button>
            <button onClick={() => onStep((step + 1) % slides.length)}>Próximo passo →</button>
          </div>
        </section>
      </div>
      <section className="rd-card rd-start">
        <div>
          <h2>O que você pode testar agora</h2>
          <p>
            Cadastro, importação CSV/Excel/XML, anúncios locais, pedidos, estoque, lançamentos,
            simulação de preços, aprovação, auditoria e consultas.
          </p>
          <p>
            Marketplaces, NF-e, mensagens externas, geração de imagens, voz e automação autônoma
            aguardam implementação ou configuração.
          </p>
        </div>
        <Badge tone="amber">Prévia funcional local</Badge>
      </section>
    </>
  );
}

/** Produtos na lixeira: restaurar (volta inativo) ou apagar de vez o que não tem histórico. */
function Lixeira({
  itens,
  podeEditar,
  executar,
  voltar,
}: {
  itens: Row[];
  podeEditar: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  voltar: () => void;
}) {
  const [apagar, setApagar] = useState<Row | null>(null);
  return (
    <section className="rd-card">
      <div className="rd-card-head">
        <h2>Lixeira · {itens.length} produto(s)</h2>
        <button type="button" onClick={voltar}>
          ← Voltar para produtos
        </button>
      </div>
      <p className="rd-note">
        Produtos excluídos ficam aqui, fora da venda e das listas. Pedidos, estoque e histórico
        continuam ligados a eles. Restaurar devolve o produto inativo.
      </p>
      <Table
        headers={["Produto", "SKU", "Excluído em", ""]}
        vazio="A lixeira está vazia."
        rows={itens.map((p) => [
          str(p.nome),
          str(p.sku),
          new Date(str(p.excluido_em)).toLocaleString("pt-BR"),
          podeEditar ? (
            <div className="rd-row-actions">
              <button
                onClick={() => executar({ op: "produtos_lote", acao: "RESTAURAR", ids: [p.id] })}
              >
                ↺ Restaurar
              </button>
              <button className="perigo" onClick={() => setApagar(p)}>
                Apagar de vez
              </button>
            </div>
          ) : (
            ""
          ),
        ])}
      />
      {apagar && (
        <div className="rd-modal-backdrop" onClick={() => setApagar(null)}>
          <section
            className="rd-modal"
            role="dialog"
            aria-modal="true"
            aria-label="Apagar de vez"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rd-card-head">
              <h2>Apagar de vez</h2>
              <button aria-label="Fechar" onClick={() => setApagar(null)}>
                ×
              </button>
            </div>
            <p>
              Apagar <strong>{str(apagar.nome)}</strong> para sempre? Não dá para desfazer. Só é
              possível se o produto nunca teve pedido, estoque, anúncio nem fez parte de kit; se
              tiver, ele fica na lixeira sem atrapalhar nada.
            </p>
            <div className="rd-modal-foot">
              <button onClick={() => setApagar(null)}>Cancelar</button>
              <button
                className="perigo"
                onClick={async () => {
                  await executar({
                    op: "produtos_lote",
                    acao: "EXCLUIR_DEFINITIVO",
                    ids: [apagar.id],
                  });
                  setApagar(null);
                }}
              >
                Apagar de vez
              </button>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
