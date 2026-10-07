// Configuração dos filtros de Pedidos (componente em filtros-genericos.tsx).
// A busca acha o pedido por qualquer coisa que a lojista tenha em mãos:
// número do Radar, número no marketplace, nota fiscal (número ou chave),
// rastreio, nome/CPF-CNPJ/e-mail/telefone/CEP/cidade do cliente e
// nome/SKU/GTIN do produto. Número do marketplace, nota e rastreio ficam
// vazios até as integrações e a NF-e existirem.

import type { Config, Filtro } from "./filtros-genericos";
import { cents, str, type Row } from "./ui";

export type ContextoPedidos = {
  produtos: Row[];
  clientes: Row[];
  vendedores: Row[];
  categorias: Row[];
  promocoes: Row[];
  veFinanceiro: boolean;
};

export const SITUACOES_PEDIDO: [string, string][] = [
  ["RESERVADO", "Pendente (reservado)"],
  ["SEPARADO", "Em separação"],
  ["EXPEDIDO", "Enviado"],
  ["CANCELADO", "Cancelado"],
  ["DEVOLVIDO", "Devolvido"],
];
const CANAIS = ["Mercado Livre", "Shopee", "TikTok Shop", "SHEIN"];

const data = (v: unknown) => new Date(str(v)).getTime() || 0;
const digitos = (v: unknown) => str(v).replace(/\D/g, "");
const hoje = () => new Date().toISOString().slice(0, 10);
const diasAtras = (n: number) => new Date(Date.now() - n * 86_400_000).toISOString().slice(0, 10);

/** Valor do pedido em reais: preço × quantidade − desconto (quando visível). */
const valor = (p: Row) => (cents(p.preco) * Number(p.quantidade) - cents(p.desconto ?? 0)) / 100;

/**
 * Resultado do pedido (só para quem vê o financeiro): o que entrou menos
 * custo do produto e despesas lançadas no pedido.
 */
const resultado = (p: Row) =>
  (cents(p.preco) * Number(p.quantidade) -
    cents(p.desconto) -
    cents(p.custo_unitario) * Number(p.quantidade) -
    cents(p.comissao) -
    cents(p.frete) -
    cents(p.imposto) -
    cents(p.ads) -
    cents(p.embalagem)) /
  100;

export function configPedidos(c: ContextoPedidos, pedidos: Row[]): Config {
  const produto = (p: Row) => c.produtos.find((x) => x.id === p.produto_id);
  const cliente = (p: Row) => c.clientes.find((x) => x.id === p.cliente_id);
  const canais = [...new Set([...CANAIS, ...pedidos.map((p) => str(p.canal)).filter(Boolean)])];
  const marcadores = [
    ...new Set(
      pedidos.flatMap((p) => (Array.isArray(p.marcadores) ? (p.marcadores as string[]) : [])),
    ),
  ].sort((a, b) => a.localeCompare(b, "pt-BR"));
  const comPeriodo = (chave: string, de: string, ate: string) => (f: Filtro) => ({
    ...f,
    v: { ...f.v, [chave]: { de, ate } },
  });
  const comSituacoes = (lista: string[]) => (f: Filtro) => ({
    ...f,
    v: { ...f.v, situacao: lista },
  });

  return {
    salvos: "radar.pedidos.filtrosSalvos",
    plural: "pedidos",
    placeholder:
      "Pesquise por nº do pedido, nº no marketplace, nota fiscal, rastreio, cliente, CPF/CNPJ, CEP ou produto",
    busca: (p) => {
      const pr = produto(p);
      const cl = cliente(p);
      return [
        p.numero,
        p.numero_externo,
        p.nota_fiscal_numero,
        p.nota_fiscal_chave,
        p.codigo_rastreio,
        p.canal,
        p.cliente,
        ...(Array.isArray(p.marcadores) ? (p.marcadores as string[]) : []),
        pr?.nome,
        pr?.sku,
        pr?.gtin,
        cl?.nome,
        cl?.fantasia,
        cl?.email,
        cl?.cidade,
        // CEP e telefones com ou sem traço/espaço; CPF/CNPJ pelos dígitos visíveis.
        cl?.cep,
        digitos(cl?.cep),
        cl?.telefone,
        digitos(cl?.telefone),
        cl?.celular,
        digitos(cl?.celular),
        cl?.documento,
        digitos(cl?.documento),
      ];
    },
    ordens: [
      ["RECENTES", "mais recentes", (a, b) => data(b.criado_em) - data(a.criado_em)],
      ["ANTIGOS", "mais antigos", (a, b) => data(a.criado_em) - data(b.criado_em)],
      ["MAIOR", "maior valor", (a, b) => valor(b) - valor(a)],
      ["MENOR", "menor valor", (a, b) => valor(a) - valor(b)],
      ["CLIENTE", "cliente (A-Z)", (a, b) => str(a.cliente).localeCompare(str(b.cliente), "pt-BR")],
      ["NUMERO", "número do pedido", (a, b) => str(a.numero).localeCompare(str(b.numero), "pt-BR")],
      ...(c.veFinanceiro
        ? ([
            ["RESULTADO", "pior resultado", (a: Row, b: Row) => resultado(a) - resultado(b)],
          ] as Config["ordens"])
        : []),
    ],
    campos: [
      {
        tipo: "multi",
        chave: "situacao",
        rotulo: "Situação",
        opcoes: SITUACOES_PEDIDO,
        valor: (p) => str(p.estado),
      },
      {
        tipo: "multi",
        chave: "canal",
        rotulo: "Canal de venda",
        opcoes: canais.map((x): [string, string] => [x, x]),
        valor: (p) => str(p.canal),
      },
      {
        tipo: "periodo",
        chave: "data",
        rotulo: "Data do pedido",
        valor: (p) => p.criado_em,
      },
      {
        tipo: "multi",
        chave: "nota",
        rotulo: "Nota fiscal",
        opcoes: [
          ["COM", "Com nota fiscal"],
          ["SEM", "Sem nota fiscal"],
        ],
        valor: (p) => (p.nota_fiscal_numero || p.nota_fiscal_chave ? "COM" : "SEM"),
      },
      {
        tipo: "periodo",
        chave: "faturamento",
        rotulo: "Data de faturamento",
        valor: (p) => p.data_faturamento,
      },
      {
        tipo: "multi",
        chave: "rastreio",
        rotulo: "Rastreio",
        opcoes: [
          ["COM", "Com código de rastreio"],
          ["SEM", "Sem código de rastreio"],
        ],
        valor: (p) => (p.codigo_rastreio ? "COM" : "SEM"),
      },
      {
        tipo: "multi",
        chave: "marcadores",
        rotulo: "Marcadores",
        opcoes: [["", "Sem marcador"], ...marcadores.map((m): [string, string] => [m, m])],
        valor: (p) => {
          const m = Array.isArray(p.marcadores) ? (p.marcadores as string[]) : [];
          return m.length ? m : [""];
        },
      },
      {
        tipo: "texto",
        chave: "cliente",
        rotulo: "Cliente",
        placeholder: "Nome do cliente",
        valor: (p) => p.cliente,
      },
      {
        tipo: "multi",
        chave: "uf",
        rotulo: "UF do cliente",
        opcoes: [...new Set(c.clientes.map((x) => str(x.uf)).filter(Boolean))]
          .sort()
          .map((u): [string, string] => [u, u]),
        valor: (p) => str(cliente(p)?.uf),
      },
      {
        tipo: "texto",
        chave: "cidade",
        rotulo: "Cidade do cliente",
        placeholder: "Cidade",
        valor: (p) => cliente(p)?.cidade,
      },
      {
        tipo: "multi",
        chave: "vendedor",
        rotulo: "Vendedor do cliente",
        opcoes: [
          ["", "Sem vendedor"],
          ...c.vendedores
            .filter((v) => !v.excluido_em)
            .map((v): [string, string] => [str(v.id), str(v.nome)]),
        ],
        valor: (p) => str(cliente(p)?.vendedor_id),
      },
      {
        tipo: "texto",
        chave: "produto",
        rotulo: "Produto",
        placeholder: "Nome do produto",
        valor: (p) => produto(p)?.nome,
      },
      {
        tipo: "texto",
        chave: "sku",
        rotulo: "SKU",
        placeholder: "Código do produto",
        valor: (p) => produto(p)?.sku,
      },
      {
        tipo: "multi",
        chave: "categoria",
        rotulo: "Categoria do produto",
        opcoes: c.categorias.map((x): [string, string] => [str(x.id), str(x.nome)]),
        valor: (p) => str(produto(p)?.categoria_id),
      },
      {
        tipo: "texto",
        chave: "marca",
        rotulo: "Marca do produto",
        placeholder: "Marca",
        valor: (p) => produto(p)?.marca,
      },
      {
        tipo: "multi",
        chave: "tipo_produto",
        rotulo: "Tipo de produto",
        opcoes: [
          ["SIMPLES", "Simples ou variação"],
          ["KIT", "Kit"],
        ],
        valor: (p) => (produto(p)?.tipo === "KIT" ? "KIT" : "SIMPLES"),
      },
      {
        tipo: "faixa",
        chave: "valor",
        rotulo: "Valor do pedido",
        unidade: "R$",
        valor,
      },
      {
        tipo: "faixa",
        chave: "quantidade",
        rotulo: "Quantidade de itens",
        unidade: "",
        valor: (p) => Number(p.quantidade ?? 0),
      },
      {
        tipo: "multi",
        chave: "promocao",
        rotulo: "Promoção",
        opcoes: [
          ["", "Sem promoção"],
          ...c.promocoes.map((x): [string, string] => [str(x.id), str(x.nome)]),
        ],
        valor: (p) => str(p.promocao_id),
      },
      ...(c.veFinanceiro
        ? ([
            {
              tipo: "marca",
              chave: "prejuizo",
              rotulo: "Com prejuízo (resultado negativo)",
              teste: (p: Row) => resultado(p) < 0,
            },
            {
              tipo: "marca",
              chave: "com_desconto",
              rotulo: "Com desconto",
              teste: (p: Row) => cents(p.desconto) > 0,
            },
            {
              tipo: "multi",
              chave: "conciliado",
              rotulo: "Conciliação financeira",
              opcoes: [
                ["SIM", "Conciliado"],
                ["NAO", "Não conciliado"],
              ],
              valor: (p: Row) => (p.conciliado === true ? "SIM" : "NAO"),
            },
          ] as Config["campos"])
        : []),
      {
        tipo: "marca",
        chave: "cliente_incompleto",
        rotulo: "Cliente com cadastro incompleto",
        teste: (p) => cliente(p)?.incompleto === true || !p.cliente_id,
      },
      {
        tipo: "multi",
        chave: "origem",
        rotulo: "Origem do pedido",
        opcoes: [
          ["LOCAL", "Criado no Radar"],
          ["INTEGRACAO", "Veio do marketplace"],
        ],
        valor: (p) => (str(p.fonte) === "LOCAL" || !p.fonte ? "LOCAL" : "INTEGRACAO"),
      },
    ],
    atalhos: [
      {
        chaves: ["pendente", "nao enviado", "a enviar", "aberto"],
        rotulo: "Não enviados (pendentes e em separação)",
        aplicar: comSituacoes(["RESERVADO", "SEPARADO"]),
      },
      {
        chaves: ["enviado", "expedido", "despachado"],
        rotulo: "Situação: enviado",
        aplicar: comSituacoes(["EXPEDIDO"]),
      },
      {
        chaves: ["entregue"],
        rotulo: "Situação: enviado (entrega ainda não é rastreada)",
        aplicar: comSituacoes(["EXPEDIDO"]),
      },
      {
        chaves: ["hoje"],
        rotulo: "Pedidos de hoje",
        aplicar: comPeriodo("data", hoje(), hoje()),
      },
      {
        chaves: ["ontem"],
        rotulo: "Pedidos de ontem",
        aplicar: comPeriodo("data", diasAtras(1), diasAtras(1)),
      },
      {
        chaves: ["semana", "7 dias"],
        rotulo: "Pedidos dos últimos 7 dias",
        aplicar: comPeriodo("data", diasAtras(6), hoje()),
      },
      {
        chaves: ["mes", "30 dias"],
        rotulo: "Pedidos dos últimos 30 dias",
        aplicar: comPeriodo("data", diasAtras(29), hoje()),
      },
      {
        chaves: ["sem nota", "sem nf", "nao faturado"],
        rotulo: "Nota fiscal: sem nota fiscal",
        aplicar: (f) => ({ ...f, v: { ...f.v, nota: ["SEM"] } }),
      },
      {
        chaves: ["sem rastreio"],
        rotulo: "Sem código de rastreio",
        aplicar: (f) => ({ ...f, v: { ...f.v, rastreio: ["SEM"] } }),
      },
      ...(c.veFinanceiro
        ? [
            {
              chaves: ["prejuizo", "negativo", "perdendo"],
              rotulo: "Com prejuízo (resultado negativo)",
              aplicar: (f: Filtro) => ({ ...f, v: { ...f.v, prejuizo: true } }),
            },
          ]
        : []),
    ],
  };
}
