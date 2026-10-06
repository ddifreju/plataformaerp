// Configuração dos filtros de Clientes, Fornecedores e Vendedores (usam o
// componente de filtros-genericos.tsx). As três telas compartilham os campos
// de cadastro (tipo de pessoa, UF, cidade, situação...) e cada uma acrescenta
// os seus.

import { STATUS_CRM, TIPOS_CONTATO, TIPOS_PESSOA, UFS } from "./cliente";
import type { Campo, Config } from "./filtros-genericos";
import { cents, str, type Row } from "./ui";

const tiposDe = (c: Row) => (Array.isArray(c.tipos_contato) ? (c.tipos_contato as string[]) : []);
const data = (v: unknown) => new Date(str(v)).getTime() || 0;
const porNome = (a: Row, b: Row) => str(a.nome).localeCompare(str(b.nome), "pt-BR");
const porCodigo = (a: Row, b: Row) =>
  str(a.codigo).localeCompare(str(b.codigo), "pt-BR", { numeric: true });
const diasDesde = (v: unknown) => (v ? (Date.now() - data(v)) / 86_400_000 : null);

function camposDeCadastro(): Campo[] {
  return [
    {
      tipo: "multi",
      chave: "tipo_pessoa",
      rotulo: "Tipo de pessoa",
      opcoes: TIPOS_PESSOA.map(([k, r]) => [k, r]),
      valor: (r) => str(r.tipo_pessoa),
    },
    {
      tipo: "multi",
      chave: "uf",
      rotulo: "UF",
      opcoes: UFS.map((u) => [u, u]),
      valor: (r) => str(r.uf),
    },
    {
      tipo: "texto",
      chave: "cidade",
      rotulo: "Cidade",
      placeholder: "Digite a cidade",
      valor: (r) => r.cidade,
    },
  ];
}

const contato: Campo[] = [
  {
    tipo: "marca",
    chave: "com_email",
    rotulo: "Com e-mail",
    teste: (r) => !!str(r.email),
  },
  {
    tipo: "marca",
    chave: "com_celular",
    rotulo: "Com celular ou telefone",
    teste: (r) => !!(str(r.celular) || str(r.telefone)),
  },
  {
    tipo: "marca",
    chave: "incompleto",
    rotulo: "Cadastro incompleto",
    teste: (r) => r.incompleto === true,
  },
];

const buscaContato = (r: Row) => [
  r.nome,
  r.fantasia,
  r.codigo,
  r.email,
  r.telefone,
  r.celular,
  r.cidade,
  // CPF/CNPJ chega mascarado na lista: acha pelos dígitos que aparecem.
  r.documento,
  str(r.documento).replace(/\D/g, ""),
];

export function configClientes(vendedores: Row[]): Config {
  return {
    salvos: "radar.clientes.filtrosSalvos",
    plural: "clientes",
    placeholder: "Pesquise por nome, código, CPF/CNPJ, e-mail, telefone ou cidade",
    busca: buscaContato,
    ordens: [
      ["NOME", "nome", porNome],
      ["RECENTES", "mais recentes", (a, b) => data(b.criado_em) - data(a.criado_em)],
      ["ULTIMA", "última compra", (a, b) => data(b.ultima_compra) - data(a.ultima_compra)],
      ["TOTAL", "maior total comprado", (a, b) => cents(b.total) - cents(a.total)],
      ["PEDIDOS", "mais pedidos", (a, b) => Number(b.pedidos) - Number(a.pedidos)],
      ["CODIGO", "código", porCodigo],
    ],
    campos: [
      {
        tipo: "multi",
        chave: "classificacao",
        rotulo: "Classificação",
        opcoes: [
          ["LEAD", "Lead (sem compra)"],
          ["PRIMEIRA_COMPRA", "Primeira compra"],
          ["RECORRENTE", "Recorrente"],
        ],
        valor: (r) => str(r.classificacao),
      },
      {
        tipo: "multi",
        chave: "situacao",
        rotulo: "Situação",
        opcoes: [
          ["ATIVO", "Ativo"],
          ["INATIVO", "Inativo"],
        ],
        valor: (r) => (r.ativo === false ? "INATIVO" : "ATIVO"),
      },
      {
        tipo: "multi",
        chave: "status_crm",
        rotulo: "Status no CRM",
        opcoes: STATUS_CRM,
        valor: (r) => str(r.status_crm),
      },
      {
        tipo: "multi",
        chave: "vendedor",
        rotulo: "Vendedor",
        opcoes: [
          ["", "Sem vendedor"],
          ...vendedores
            .filter((v) => !v.excluido_em)
            .map((v): [string, string] => [str(v.id), str(v.nome)]),
        ],
        valor: (r) => str(r.vendedor_id),
      },
      {
        tipo: "texto",
        chave: "lista_preco",
        rotulo: "Lista de preço",
        placeholder: "Nome da lista",
        valor: (r) => r.lista_preco,
      },
      {
        tipo: "multi",
        chave: "tambem",
        rotulo: "Também é",
        opcoes: TIPOS_CONTATO.filter(([k]) => k !== "CLIENTE"),
        valor: (r) => tiposDe(r),
      },
      ...camposDeCadastro(),
      {
        tipo: "faixa",
        chave: "total",
        rotulo: "Total comprado",
        unidade: "R$",
        valor: (r) => cents(r.total) / 100,
      },
      {
        tipo: "faixa",
        chave: "pedidos",
        rotulo: "Pedidos",
        unidade: "",
        valor: (r) => Number(r.pedidos ?? 0),
      },
      {
        tipo: "faixa",
        chave: "sem_comprar",
        rotulo: "Última compra há",
        unidade: "dias",
        valor: (r) => diasDesde(r.ultima_compra),
      },
      {
        tipo: "multi",
        chave: "origem",
        rotulo: "Origem do cadastro",
        opcoes: [
          ["MANUAL", "Cadastrado à mão"],
          ["PEDIDO", "Veio de pedido"],
        ],
        valor: (r) => str(r.origem) || "MANUAL",
      },
      ...contato,
      {
        tipo: "data",
        chave: "criado",
        rotulo: "Cadastrados a partir de",
        valor: (r) => r.criado_em,
      },
    ],
    atalhos: [
      {
        chaves: ["sem compra", "nunca comprou", "lead"],
        rotulo: "Classificação: lead (sem compra)",
        aplicar: (f) => ({ ...f, v: { ...f.v, classificacao: ["LEAD"] } }),
      },
      {
        chaves: ["sumiu", "parado", "nao compra", "inativo ha"],
        rotulo: "Última compra há mais de 90 dias",
        aplicar: (f) => ({ ...f, v: { ...f.v, sem_comprar: { de: "90", ate: "" } } }),
      },
      {
        chaves: ["pj", "empresa", "cnpj"],
        rotulo: "Tipo de pessoa: jurídica",
        aplicar: (f) => ({ ...f, v: { ...f.v, tipo_pessoa: ["J"] } }),
      },
      {
        chaves: ["pf", "cpf", "pessoa fisica"],
        rotulo: "Tipo de pessoa: física",
        aplicar: (f) => ({ ...f, v: { ...f.v, tipo_pessoa: ["F"] } }),
      },
    ],
  };
}

export function configFornecedores(produtoFornecedores: Row[]): Config {
  const produtos = (id: unknown) =>
    produtoFornecedores.filter((pf) => pf.fornecedor_id === id).length;
  return {
    salvos: "radar.fornecedores.filtrosSalvos",
    plural: "fornecedores",
    placeholder: "Pesquise por nome, código, CPF/CNPJ, e-mail, telefone ou cidade",
    busca: buscaContato,
    ordens: [
      ["NOME", "nome", porNome],
      ["RECENTES", "mais recentes", (a, b) => data(b.criado_em) - data(a.criado_em)],
      [
        "PRAZO",
        "menor prazo de entrega",
        (a, b) => Number(a.prazo_entrega_dias ?? 999) - Number(b.prazo_entrega_dias ?? 999),
      ],
      ["PRODUTOS", "mais produtos", (a, b) => produtos(b.id) - produtos(a.id)],
      ["CODIGO", "código", porCodigo],
    ],
    campos: [
      {
        tipo: "multi",
        chave: "tipo",
        rotulo: "Tipo de contato",
        opcoes: TIPOS_CONTATO,
        valor: (r) => tiposDe(r),
      },
      {
        tipo: "multi",
        chave: "situacao",
        rotulo: "Situação",
        opcoes: [
          ["ATIVO", "Ativo"],
          ["INATIVO", "Inativo"],
        ],
        valor: (r) => (r.ativo === false ? "INATIVO" : "ATIVO"),
      },
      ...camposDeCadastro(),
      {
        tipo: "faixa",
        chave: "prazo",
        rotulo: "Prazo de entrega",
        unidade: "dias",
        valor: (r) => (r.prazo_entrega_dias == null ? null : Number(r.prazo_entrega_dias)),
      },
      {
        tipo: "marca",
        chave: "com_produtos",
        rotulo: "Com produtos vinculados",
        teste: (r) => produtos(r.id) > 0,
      },
      {
        tipo: "marca",
        chave: "sem_produtos",
        rotulo: "Sem produtos vinculados",
        teste: (r) => produtos(r.id) === 0,
      },
      ...contato,
      {
        tipo: "data",
        chave: "criado",
        rotulo: "Cadastrados a partir de",
        valor: (r) => r.criado_em,
      },
    ],
    atalhos: [
      {
        chaves: ["transport", "frete"],
        rotulo: "Tipo de contato: transportador",
        aplicar: (f) => ({ ...f, v: { ...f.v, tipo: ["TRANSPORTADOR"] } }),
      },
      {
        chaves: ["sem produto"],
        rotulo: "Sem produtos vinculados",
        aplicar: (f) => ({ ...f, v: { ...f.v, sem_produtos: true } }),
      },
    ],
  };
}

export function configVendedores(): Config {
  return {
    salvos: "radar.vendedores.filtrosSalvos",
    plural: "vendedores",
    placeholder: "Pesquise por nome, código, CPF/CNPJ, e-mail, telefone ou cidade",
    busca: (r) => [...buscaContato(r), r.usuario_nome],
    ordens: [
      ["NOME", "nome", porNome],
      ["CLIENTES", "mais clientes", (a, b) => Number(b.clientes) - Number(a.clientes)],
      [
        "COMISSAO",
        "maior comissão",
        (a, b) => Number(b.comissao_aliquota ?? 0) - Number(a.comissao_aliquota ?? 0),
      ],
      ["CODIGO", "código", porCodigo],
    ],
    campos: [
      {
        tipo: "multi",
        chave: "acesso",
        rotulo: "Acesso ao sistema",
        opcoes: [
          ["COM", "Com usuário do sistema"],
          ["SEM", "Sem acesso"],
        ],
        valor: (r) => (r.usuario_id ? "COM" : "SEM"),
      },
      {
        tipo: "multi",
        chave: "regra",
        rotulo: "Regra de comissão",
        opcoes: [
          ["FIXA", "Alíquota fixa"],
          ["DESCONTO", "Conforme o desconto"],
        ],
        valor: (r) => str(r.comissao_regra),
      },
      {
        tipo: "faixa",
        chave: "comissao",
        rotulo: "Comissão",
        unidade: "%",
        valor: (r) => (r.comissao_aliquota == null ? null : Number(r.comissao_aliquota)),
      },
      {
        tipo: "faixa",
        chave: "clientes",
        rotulo: "Clientes",
        unidade: "",
        valor: (r) => Number(r.clientes ?? 0),
      },
      ...camposDeCadastro(),
      {
        tipo: "marca",
        chave: "com_email",
        rotulo: "Com e-mail",
        teste: (r) => !!str(r.email),
      },
    ],
    atalhos: [
      {
        chaves: ["sem acesso", "sem usuario", "sem login"],
        rotulo: "Acesso ao sistema: sem acesso",
        aplicar: (f) => ({ ...f, v: { ...f.v, acesso: ["SEM"] } }),
      },
      {
        chaves: ["sem cliente"],
        rotulo: "Sem clientes",
        aplicar: (f) => ({ ...f, v: { ...f.v, clientes: { de: "", ate: "0" } } }),
      },
    ],
  };
}
