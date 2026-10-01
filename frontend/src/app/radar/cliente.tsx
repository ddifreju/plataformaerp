"use client";

// Clientes: lista com histórico de compras (lead, primeira compra, recorrente) e
// cadastro completo em abas. Pedido sem cliente cria o cliente sozinho, marcado
// como incompleto; salvar pela tela exige os dados da nota fiscal.

import { useEffect, useState, type ReactNode } from "react";
import { Badge, Empty, Table, date, money, str, type Row } from "./ui";

type Valores = Record<string, string | boolean>;
type Pessoa = {
  nome: string;
  setor: string;
  email: string;
  telefone: string;
  ramal: string;
};

type Props = {
  clientes: Row[];
  vendedores: Row[];
  podeEditar: boolean;
  executar: (
    corpo: Record<string, unknown>,
  ) => Promise<Record<string, unknown> | null>;
  recarregar: () => Promise<void>;
};

const TIPOS_PESSOA: [string, string, string][] = [
  ["F", "Física", "CPF"],
  ["J", "Jurídica", "CNPJ"],
  ["E", "Estrangeira", "Documento do país de origem"],
  ["B", "Estrangeira no Brasil", "CPF (se tiver)"],
];
const TIPOS_CONTATO: [string, string][] = [
  ["CLIENTE", "Cliente"],
  ["FORNECEDOR", "Fornecedor"],
  ["TRANSPORTADOR", "Transportador"],
];
const CONTRIBUINTE: [string, string][] = [
  ["", "Não informado"],
  ["1", "1 - Contribuinte de ICMS"],
  ["2", "2 - Contribuinte isento de inscrição"],
  ["9", "9 - Não contribuinte"],
];
const REGIMES: [string, string][] = [
  ["", "Não informado"],
  ["1", "1 - Simples Nacional"],
  ["2", "2 - Simples Nacional, excesso de sublimite"],
  ["3", "3 - Regime normal"],
  ["4", "4 - MEI"],
];
const STATUS_CRM: [string, string][] = [
  ["NOVO", "Novo"],
  ["EM_CONTATO", "Em contato"],
  ["NEGOCIACAO", "Em negociação"],
  ["CLIENTE", "Cliente"],
  ["FIDELIZADO", "Fidelizado"],
  ["INATIVO", "Inativo"],
  ["PERDIDO", "Perdido"],
];
const UFS = [
  "AC",
  "AL",
  "AP",
  "AM",
  "BA",
  "CE",
  "DF",
  "ES",
  "GO",
  "MA",
  "MT",
  "MS",
  "MG",
  "PA",
  "PB",
  "PR",
  "PE",
  "PI",
  "RJ",
  "RN",
  "RS",
  "RO",
  "RR",
  "SC",
  "SP",
  "SE",
  "TO",
];
const CLASSES: Record<string, [string, string]> = {
  LEAD: ["Lead", "gray"],
  PRIMEIRA_COMPRA: ["Primeira compra", "blue"],
  RECORRENTE: ["Recorrente", "green"],
};
const FILTROS: [string, string][] = [
  ["TODOS", "Todos"],
  ["LEAD", "Leads"],
  ["PRIMEIRA_COMPRA", "Primeira compra"],
  ["RECORRENTE", "Recorrentes"],
  ["INCOMPLETO", "Cadastro incompleto"],
];
const ABAS = [
  ["dados", "Dados gerais"],
  ["endereco", "Endereço"],
  ["contato", "Contato"],
  ["comercial", "Comercial e CRM"],
  ["anexos", "Anexos e observações"],
  ["historico", "Histórico de compras"],
] as const;
type Aba = (typeof ABAS)[number][0];

const CAMPOS = [
  "codigo",
  "nome",
  "fantasia",
  "tipo_pessoa",
  "documento",
  "documento_estrangeiro",
  "pais",
  "contribuinte",
  "inscricao_estadual",
  "inscricao_municipal",
  "cep",
  "endereco",
  "numero",
  "complemento",
  "bairro",
  "cidade",
  "uf",
  "municipio_ibge",
  "cobranca_cep",
  "cobranca_endereco",
  "cobranca_numero",
  "cobranca_complemento",
  "cobranca_bairro",
  "cobranca_cidade",
  "cobranca_uf",
  "telefone",
  "telefone_adicional",
  "celular",
  "website",
  "email",
  "email_nfe",
  "observacoes_contato",
  "regime_tributario",
  "inscricao_suframa",
  "data_nascimento",
  "status_crm",
  "vendedor_id",
  "condicao_pagamento",
  "lista_preco",
  "limite_credito",
  "observacao",
];

/** "52998224725" → "529.982.247-25"; CNPJ (inclusive alfanumérico) → "12.ABC.345/01DE-35". */
export function formatarDocumento(d: string) {
  const s = d.toUpperCase().replace(/[^0-9A-Z]/g, "");
  if (s.length === 11)
    return s.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4");
  if (s.length === 14)
    return s.replace(/^(.{2})(.{3})(.{3})(.{4})(.{2})$/, "$1.$2.$3/$4-$5");
  return d;
}

function valoresIniciais(c: Row | null): Valores {
  const v: Valores = {
    cobranca_diferente: c?.cobranca_diferente === true,
    ativo: c?.ativo !== false,
  };
  for (const k of CAMPOS) v[k] = c?.[k] == null ? "" : str(c[k]);
  if (!c) {
    v.tipo_pessoa = "F";
    v.status_crm = "NOVO";
  }
  if (c?.documento) v.documento = formatarDocumento(str(c.documento));
  if (str(c?.limite_credito) === "0.00") v.limite_credito = "";
  return v;
}

async function buscarCliente(id: string) {
  const res = await fetch(`/api/radar/clientes/${id}`, {
    credentials: "include",
  });
  return { ok: res.ok, corpo: await res.json().catch(() => ({})) };
}

function lista<T>(v: unknown): T[] {
  return Array.isArray(v) ? (v as T[]) : [];
}

function Campo({
  rotulo,
  obrigatorio,
  dica,
  largo,
  children,
}: {
  rotulo: string;
  obrigatorio?: boolean;
  dica?: string;
  largo?: boolean;
  children: ReactNode;
}) {
  return (
    <label className={largo ? "wide" : ""}>
      <span>
        {rotulo}
        {obrigatorio && <b className="rd-obrigatorio"> *</b>}
      </span>
      {children}
      {dica && <small className="rd-dica">{dica}</small>}
    </label>
  );
}

export default function Clientes(props: Props) {
  const [aberto, setAberto] = useState<{
    id: string | null;
    versao: number;
  } | null>(null);
  const [filtro, setFiltro] = useState("TODOS");
  const [busca, setBusca] = useState("");

  if (aberto)
    return (
      <ClienteForm
        key={`${aberto.id ?? "novo"}-${aberto.versao}`}
        id={aberto.id}
        {...props}
        voltar={() => setAberto(null)}
        aoSalvar={(id) => setAberto({ id, versao: Date.now() })}
      />
    );

  const contagem = (f: string) =>
    f === "TODOS"
      ? props.clientes.length
      : f === "INCOMPLETO"
        ? props.clientes.filter((c) => c.incompleto === true).length
        : props.clientes.filter((c) => c.classificacao === f).length;
  const termo = busca.trim().toLowerCase();
  const linhas = props.clientes.filter(
    (c) =>
      (filtro === "TODOS" ||
        (filtro === "INCOMPLETO"
          ? c.incompleto === true
          : c.classificacao === filtro)) &&
      (!termo ||
        [
          c.nome,
          c.fantasia,
          c.codigo,
          c.email,
          c.cidade,
          c.telefone,
          c.celular,
        ].some((x) => str(x).toLowerCase().includes(termo))),
  );

  return (
    <>
      <div className="rd-kpis rd-clientes-resumo">
        {FILTROS.slice(1).map(([chave, rotulo]) => (
          <button
            key={chave}
            type="button"
            className={`rd-kpi ${filtro === chave ? "ativo" : ""}`}
            onClick={() => setFiltro(filtro === chave ? "TODOS" : chave)}
          >
            <span>{rotulo}</span>
            <strong>{contagem(chave)}</strong>
          </button>
        ))}
      </div>
      <section className="rd-card">
        <div className="rd-card-head">
          <h2>{props.clientes.length} clientes</h2>
          <input
            aria-label="Buscar cliente"
            className="rd-search"
            placeholder="Buscar por nome, código, e-mail, cidade…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          {props.podeEditar && (
            <button
              type="button"
              className="primary"
              onClick={() => setAberto({ id: null, versao: Date.now() })}
            >
              + Novo cliente
            </button>
          )}
        </div>
        <div
          className="rd-tabs rd-filtros"
          role="tablist"
          aria-label="Filtrar clientes"
        >
          {FILTROS.map(([chave, rotulo]) => (
            <button
              key={chave}
              role="tab"
              aria-selected={filtro === chave}
              className={filtro === chave ? "active" : ""}
              onClick={() => setFiltro(chave)}
            >
              {rotulo} ({contagem(chave)})
            </button>
          ))}
        </div>
        {props.clientes.length === 0 ? (
          <Empty text="Nenhum cliente ainda. Cada pedido novo cadastra o cliente sozinho." />
        ) : (
          <Table
            headers={[
              "Código",
              "Cliente",
              "CPF/CNPJ",
              "Cidade",
              "Situação",
              "Pedidos",
              "Última compra",
              "Total comprado",
              "",
            ]}
            rows={linhas.map((c) => {
              const [rotulo, tom] = CLASSES[str(c.classificacao)] ?? [
                "—",
                "gray",
              ];
              return [
                str(c.codigo),
                <div key="n">
                  <strong>{str(c.nome)}</strong>
                  {c.fantasia && <small> · {str(c.fantasia)}</small>}
                  <br />
                  <small>
                    {str(c.email) || str(c.celular) || str(c.telefone)}
                  </small>
                </div>,
                str(c.documento) || "—",
                [str(c.cidade), str(c.uf)].filter(Boolean).join(" / ") || "—",
                <div key="s" className="rd-badges">
                  <Badge tone={tom}>{rotulo}</Badge>
                  {c.incompleto === true && (
                    <Badge tone="amber">Incompleto</Badge>
                  )}
                  {c.origem === "PEDIDO" && <Badge>Veio de pedido</Badge>}
                </div>,
                str(c.pedidos),
                c.ultima_compra ? date(c.ultima_compra) : "—",
                money(c.total),
                <button
                  key="a"
                  type="button"
                  onClick={() =>
                    setAberto({ id: str(c.id), versao: Date.now() })
                  }
                >
                  {props.podeEditar
                    ? c.incompleto === true
                      ? "Completar"
                      : "Editar"
                    : "Ver"}
                </button>,
              ];
            })}
          />
        )}
        <p className="rd-note">
          Lead: cadastrado sem compra. Primeira compra: um pedido. Recorrente:
          dois ou mais. Pedidos cancelados não contam. CPF/CNPJ aparece completo
          só ao abrir o cadastro.
        </p>
      </section>
    </>
  );
}

function ClienteForm({
  id,
  vendedores,
  podeEditar,
  executar,
  voltar,
  aoSalvar,
}: Props & {
  id: string | null;
  voltar: () => void;
  aoSalvar: (id: string) => void;
}) {
  const novo = !id;
  const [carregado, setCarregado] = useState<Row | null>(null);
  const [carregando, setCarregando] = useState(!novo);
  const [aba, setAba] = useState<Aba>("dados");
  const [v, setV] = useState<Valores>(() => valoresIniciais(null));
  const [tipos, setTipos] = useState<string[]>(["CLIENTE"]);
  const [pessoas, setPessoas] = useState<Pessoa[]>([]);
  const [erro, setErro] = useState("");
  const [aviso, setAviso] = useState("");
  const [salvando, setSalvando] = useState(false);

  function aplicar(r: { ok: boolean; corpo: Row & { mensagem?: string } }) {
    if (!r.ok) {
      setErro(r.corpo.mensagem ?? "Não foi possível abrir o cliente.");
      return;
    }
    const c = r.corpo;
    setCarregado(c);
    setV(valoresIniciais(c));
    setTipos(lista<string>(c.tipos_contato));
    setPessoas(
      lista<Partial<Pessoa>>(c.pessoas_contato).map((p) => ({
        nome: str(p.nome),
        setor: str(p.setor),
        email: str(p.email),
        telefone: str(p.telefone),
        ramal: str(p.ramal),
      })),
    );
  }

  async function carregar() {
    if (id) aplicar(await buscarCliente(id));
  }

  useEffect(() => {
    let vivo = true;
    if (id)
      buscarCliente(id)
        .then((r) => {
          if (vivo) aplicar(r);
        })
        .finally(() => {
          if (vivo) setCarregando(false);
        });
    return () => {
      vivo = false;
    };
  }, [id]);

  const tipo = str(v.tipo_pessoa);
  const noBrasil = tipo !== "E";
  const rotuloDoc = TIPOS_PESSOA.find((t) => t[0] === tipo)?.[2] ?? "Documento";
  const set = (campo: string) => (e: { target: { value: string } }) =>
    setV((atual) => ({ ...atual, [campo]: e.target.value }));
  const entrada = (campo: string, extra: Record<string, unknown> = {}) => (
    <input
      id={`cliente-${campo}`}
      value={str(v[campo])}
      onChange={set(campo)}
      disabled={!podeEditar}
      {...extra}
    />
  );
  const escolha = (campo: string, opcoes: [string, string][]) => (
    <select
      id={`cliente-${campo}`}
      value={str(v[campo])}
      onChange={set(campo)}
      disabled={!podeEditar}
    >
      {opcoes.map(([valor, rotulo]) => (
        <option key={valor} value={valor}>
          {rotulo}
        </option>
      ))}
    </select>
  );

  // Busca o endereço pelo CEP (ViaCEP, serviço público dos Correios/IBGE).
  async function buscarCep(prefixo: "" | "cobranca_") {
    const cep = str(v[`${prefixo}cep`]).replace(/\D/g, "");
    if (cep.length !== 8) return;
    try {
      const res = await fetch(`https://viacep.com.br/ws/${cep}/json/`);
      const d = await res.json();
      if (d.erro) {
        setAviso("CEP não encontrado. Preencha o endereço à mão.");
        return;
      }
      setAviso("");
      setV((atual) => ({
        ...atual,
        [`${prefixo}endereco`]: d.logradouro || atual[`${prefixo}endereco`],
        [`${prefixo}bairro`]: d.bairro || atual[`${prefixo}bairro`],
        [`${prefixo}cidade`]: d.localidade || atual[`${prefixo}cidade`],
        [`${prefixo}uf`]: d.uf || atual[`${prefixo}uf`],
        ...(prefixo === "" ? { municipio_ibge: d.ibge || "" } : {}),
      }));
    } catch {
      setAviso(
        "Não deu para consultar o CEP agora. Preencha o endereço à mão.",
      );
    }
  }

  // Mesma regra do servidor, para mostrar o que falta antes de enviar.
  function faltando(): [string, Aba][] {
    const f: [string, Aba][] = [];
    const vazio = (c: string) => !str(v[c]).trim();
    if (vazio("nome")) f.push(["Nome", "dados"]);
    if ((tipo === "F" || tipo === "J") && vazio("documento"))
      f.push([rotuloDoc, "dados"]);
    if (!noBrasil && vazio("pais")) f.push(["País", "endereco"]);
    if (noBrasil && vazio("cep")) f.push(["CEP", "endereco"]);
    if (vazio("endereco")) f.push(["Endereço", "endereco"]);
    if (vazio("numero")) f.push(["Número", "endereco"]);
    if (noBrasil && vazio("bairro")) f.push(["Bairro", "endereco"]);
    if (vazio("cidade")) f.push(["Município", "endereco"]);
    if (noBrasil && vazio("uf")) f.push(["UF", "endereco"]);
    if (v.cobranca_diferente)
      for (const [c, r] of [
        ["cobranca_cep", "CEP de cobrança"],
        ["cobranca_endereco", "Endereço de cobrança"],
        ["cobranca_numero", "Número de cobrança"],
        ["cobranca_bairro", "Bairro de cobrança"],
        ["cobranca_cidade", "Município de cobrança"],
        ["cobranca_uf", "UF de cobrança"],
      ])
        if (vazio(c)) f.push([r, "endereco"]);
    if (pessoas.some((p) => !p.nome.trim()))
      f.push(["Nome da pessoa de contato", "contato"]);
    return f;
  }

  async function salvar() {
    setErro("");
    const f = faltando();
    if (f.length) {
      setErro(
        `Preencha os campos obrigatórios: ${f.map((x) => x[0]).join(", ")}.`,
      );
      setAba(f[0][1]);
      return;
    }
    setSalvando(true);
    try {
      const corpo: Record<string, unknown> = {
        op: "cliente_salvar",
        ...v,
        tipos_contato: tipos,
        pessoas_contato: pessoas,
      };
      if (id) corpo.id = id;
      const r = await executar(corpo);
      if (r) aoSalvar(str(r.id));
    } finally {
      setSalvando(false);
    }
  }

  async function enviarAnexos(arquivos: FileList | null) {
    if (!arquivos || !id) return;
    setErro("");
    for (const arquivo of Array.from(arquivos)) {
      if (arquivo.size > 2 * 1024 * 1024) {
        setErro(`${arquivo.name} tem mais de 2 MB.`);
        continue;
      }
      const form = new FormData();
      form.append("arquivo", arquivo);
      const res = await fetch(`/api/radar/clientes/${id}/anexos`, {
        method: "POST",
        credentials: "include",
        headers: { "X-Radar-Request": "1" },
        body: form,
      });
      if (!res.ok) {
        const corpo = await res.json().catch(() => ({}));
        setErro(corpo.mensagem ?? `Não foi possível enviar ${arquivo.name}.`);
      }
    }
    await carregar();
  }

  async function removerAnexo(anexoId: string) {
    const res = await fetch(`/api/radar/anexos/${anexoId}`, {
      method: "DELETE",
      credentials: "include",
      headers: { "X-Radar-Request": "1" },
    });
    if (!res.ok) setErro("Não foi possível remover o anexo.");
    await carregar();
  }

  if (carregando)
    return <section className="rd-card">Abrindo cadastro…</section>;

  const pedidos = lista<Row>(carregado?.pedidos);
  const anexos = lista<Row>(carregado?.anexos);
  const [classe, tom] = CLASSES[str(carregado?.classificacao)] ?? CLASSES.LEAD;
  const abas = ABAS.filter(
    ([chave]) => !novo || (chave !== "historico" && chave !== "anexos"),
  );

  const conteudo: Record<Aba, ReactNode> = {
    dados: (
      <div className="rd-form-grid">
        <Campo rotulo="Tipo de pessoa" obrigatorio largo>
          <div className="rd-opcoes">
            {TIPOS_PESSOA.map(([valor, rotulo]) => (
              <label key={valor} className={tipo === valor ? "ativo" : ""}>
                <input
                  type="radio"
                  name="cliente-tipo"
                  value={valor}
                  checked={tipo === valor}
                  disabled={!podeEditar}
                  onChange={set("tipo_pessoa")}
                />
                {rotulo}
              </label>
            ))}
          </div>
        </Campo>
        <Campo
          rotulo={tipo === "J" ? "Razão social" : "Nome"}
          obrigatorio
          largo
        >
          {entrada("nome", { maxLength: 200 })}
        </Campo>
        <Campo rotulo="Nome fantasia">
          {entrada("fantasia", { maxLength: 200 })}
        </Campo>
        <Campo
          rotulo="Código"
          dica="Em branco, o Radar gera (C00001, C00002…)."
        >
          {entrada("codigo", { maxLength: 30 })}
        </Campo>
        {tipo !== "E" && (
          <Campo
            rotulo={rotuloDoc}
            obrigatorio={tipo === "F" || tipo === "J"}
            dica={
              tipo === "J"
                ? "Aceita o CNPJ novo com letras."
                : "Conferimos os dígitos."
            }
          >
            {entrada("documento", {
              maxLength: 18,
              onBlur: () =>
                setV((a) => ({
                  ...a,
                  documento: formatarDocumento(str(a.documento)),
                })),
            })}
          </Campo>
        )}
        {(tipo === "E" || tipo === "B") && (
          <Campo rotulo="Passaporte / documento estrangeiro">
            {entrada("documento_estrangeiro", { maxLength: 20 })}
          </Campo>
        )}
        <Campo rotulo="Tipo de contato" dica="Pode marcar mais de um." largo>
          <div className="rd-opcoes">
            {TIPOS_CONTATO.map(([valor, rotulo]) => (
              <label
                key={valor}
                className={tipos.includes(valor) ? "ativo" : ""}
              >
                <input
                  type="checkbox"
                  checked={tipos.includes(valor)}
                  disabled={!podeEditar}
                  onChange={(e) =>
                    setTipos((t) =>
                      e.target.checked
                        ? [...t, valor]
                        : t.filter((x) => x !== valor),
                    )
                  }
                />
                {rotulo}
              </label>
            ))}
          </div>
        </Campo>
        {tipo === "F" && (
          <Campo rotulo="Data de nascimento">
            {entrada("data_nascimento", { type: "date" })}
          </Campo>
        )}
        <h3 className="rd-secao wide">Dados fiscais</h3>
        <Campo
          rotulo="Contribuinte"
          dica="Indicador de IE do destinatário na NF-e."
        >
          {escolha("contribuinte", CONTRIBUINTE)}
        </Campo>
        <Campo
          rotulo="Inscrição estadual"
          obrigatorio={v.contribuinte === "1"}
          dica="Só números, ou ISENTO."
        >
          {entrada("inscricao_estadual", { maxLength: 20 })}
        </Campo>
        <Campo rotulo="Inscrição municipal">
          {entrada("inscricao_municipal", { maxLength: 20 })}
        </Campo>
        <Campo rotulo="Código de regime tributário (CRT)">
          {escolha("regime_tributario", REGIMES)}
        </Campo>
        <Campo
          rotulo="Inscrição Suframa"
          dica="Zona Franca de Manaus e áreas de livre comércio."
        >
          {entrada("inscricao_suframa", {
            maxLength: 12,
            inputMode: "numeric",
          })}
        </Campo>
      </div>
    ),
    endereco: (
      <div className="rd-form-grid">
        {aviso && <p className="rd-dica wide">{aviso}</p>}
        {!noBrasil && (
          <Campo rotulo="País" obrigatorio>
            {entrada("pais", { maxLength: 60 })}
          </Campo>
        )}
        <Campo
          rotulo={noBrasil ? "CEP" : "Código postal"}
          obrigatorio={noBrasil}
          dica={noBrasil ? "Ao sair do campo, buscamos o endereço." : undefined}
        >
          {entrada("cep", {
            maxLength: 9,
            inputMode: "numeric",
            onBlur: () => buscarCep(""),
          })}
        </Campo>
        <Campo rotulo="Endereço" obrigatorio largo>
          {entrada("endereco", { maxLength: 200 })}
        </Campo>
        <Campo rotulo="Número" obrigatorio dica="Sem número? Escreva S/N.">
          {entrada("numero", { maxLength: 20 })}
        </Campo>
        <Campo rotulo="Complemento">
          {entrada("complemento", { maxLength: 120 })}
        </Campo>
        <Campo rotulo="Bairro" obrigatorio={noBrasil}>
          {entrada("bairro", { maxLength: 120 })}
        </Campo>
        <Campo rotulo="Município" obrigatorio>
          {entrada("cidade", { maxLength: 120 })}
        </Campo>
        {noBrasil && (
          <Campo rotulo="UF" obrigatorio>
            {escolha("uf", [
              ["", "Escolha"],
              ...UFS.map((u): [string, string] => [u, u]),
            ])}
          </Campo>
        )}
        <label className="rd-check wide">
          <input
            type="checkbox"
            checked={v.cobranca_diferente === true}
            disabled={!podeEditar}
            onChange={(e) =>
              setV((a) => ({ ...a, cobranca_diferente: e.target.checked }))
            }
          />
          Endereço de cobrança diferente
        </label>
        {v.cobranca_diferente === true && (
          <>
            <h3 className="rd-secao wide">Endereço de cobrança</h3>
            <Campo rotulo="CEP" obrigatorio>
              {entrada("cobranca_cep", {
                maxLength: 9,
                inputMode: "numeric",
                onBlur: () => buscarCep("cobranca_"),
              })}
            </Campo>
            <Campo rotulo="Endereço" obrigatorio largo>
              {entrada("cobranca_endereco", { maxLength: 200 })}
            </Campo>
            <Campo rotulo="Número" obrigatorio>
              {entrada("cobranca_numero", { maxLength: 20 })}
            </Campo>
            <Campo rotulo="Complemento">
              {entrada("cobranca_complemento", { maxLength: 120 })}
            </Campo>
            <Campo rotulo="Bairro" obrigatorio>
              {entrada("cobranca_bairro", { maxLength: 120 })}
            </Campo>
            <Campo rotulo="Município" obrigatorio>
              {entrada("cobranca_cidade", { maxLength: 120 })}
            </Campo>
            <Campo rotulo="UF" obrigatorio>
              {escolha("cobranca_uf", [
                ["", "Escolha"],
                ...UFS.map((u): [string, string] => [u, u]),
              ])}
            </Campo>
          </>
        )}
      </div>
    ),
    contato: (
      <div className="rd-form-grid">
        <Campo rotulo="Telefone">
          {entrada("telefone", { maxLength: 40, type: "tel" })}
        </Campo>
        <Campo rotulo="Telefone adicional">
          {entrada("telefone_adicional", { maxLength: 40, type: "tel" })}
        </Campo>
        <Campo rotulo="Celular">
          {entrada("celular", { maxLength: 40, type: "tel" })}
        </Campo>
        <Campo rotulo="WebSite">{entrada("website", { maxLength: 300 })}</Campo>
        <Campo rotulo="E-mail">
          {entrada("email", { maxLength: 320, type: "email" })}
        </Campo>
        <Campo
          rotulo="E-mail para envio da NF-e"
          dica="Em branco, a nota vai para o e-mail acima."
        >
          {entrada("email_nfe", { maxLength: 320, type: "email" })}
        </Campo>
        <Campo rotulo="Observações do contato" largo>
          <textarea
            id="cliente-observacoes_contato"
            rows={3}
            value={str(v.observacoes_contato)}
            onChange={set("observacoes_contato")}
            disabled={!podeEditar}
          />
        </Campo>
        <h3 className="rd-secao wide">Pessoas de contato</h3>
        <div className="wide">
          {pessoas.length === 0 && (
            <p className="rd-dica">Nenhuma pessoa de contato.</p>
          )}
          {pessoas.map((p, i) => (
            <div key={i} className="rd-inline rd-pessoa">
              {(["nome", "setor", "email", "telefone", "ramal"] as const).map(
                (c) => (
                  <input
                    key={c}
                    aria-label={`${c} da pessoa ${i + 1}`}
                    placeholder={
                      {
                        nome: "Nome *",
                        setor: "Setor",
                        email: "E-mail",
                        telefone: "Telefone",
                        ramal: "Ramal",
                      }[c]
                    }
                    value={p[c]}
                    disabled={!podeEditar}
                    maxLength={
                      {
                        nome: 120,
                        setor: 80,
                        email: 320,
                        telefone: 40,
                        ramal: 10,
                      }[c]
                    }
                    onChange={(e) =>
                      setPessoas((l) =>
                        l.map((x, j) =>
                          j === i ? { ...x, [c]: e.target.value } : x,
                        ),
                      )
                    }
                  />
                ),
              )}
              {podeEditar && (
                <button
                  type="button"
                  aria-label={`Remover pessoa ${i + 1}`}
                  onClick={() => setPessoas((l) => l.filter((_, j) => j !== i))}
                >
                  ✕
                </button>
              )}
            </div>
          ))}
          {podeEditar && pessoas.length < 20 && (
            <button
              type="button"
              onClick={() =>
                setPessoas((l) => [
                  ...l,
                  { nome: "", setor: "", email: "", telefone: "", ramal: "" },
                ])
              }
            >
              + Adicionar pessoa de contato
            </button>
          )}
        </div>
      </div>
    ),
    comercial: (
      <div className="rd-form-grid">
        <Campo rotulo="Status no CRM">
          {escolha("status_crm", STATUS_CRM)}
        </Campo>
        <Campo rotulo="Vendedor padrão">
          {escolha("vendedor_id", [
            ["", "Nenhum"],
            ...vendedores.map((u): [string, string] => [
              str(u.id),
              str(u.nome),
            ]),
          ])}
        </Campo>
        <Campo rotulo="Condição de pagamento" dica="Ex.: 30 60, 3x, 15 +2x">
          {entrada("condicao_pagamento", { maxLength: 60 })}
        </Campo>
        <Campo rotulo="Lista de preço">
          {entrada("lista_preco", { maxLength: 60 })}
        </Campo>
        <Campo
          rotulo="Limite de crédito (R$)"
          dica="Zero ou vazio = sem limite."
        >
          {entrada("limite_credito", {
            inputMode: "decimal",
            placeholder: "0.00",
          })}
        </Campo>
        <Campo rotulo="Data de criação">
          <input
            value={
              carregado?.criado_em ? date(carregado.criado_em) : "Ao salvar"
            }
            disabled
            readOnly
          />
        </Campo>
        <label className="rd-check wide">
          <input
            type="checkbox"
            checked={v.ativo === true}
            disabled={!podeEditar}
            onChange={(e) => setV((a) => ({ ...a, ativo: e.target.checked }))}
          />
          Cadastro ativo
        </label>
      </div>
    ),
    anexos: (
      <div className="rd-form-grid">
        <div className="wide">
          <h3 className="rd-secao">Anexos</h3>
          {anexos.length === 0 && <p className="rd-dica">Nenhum anexo.</p>}
          <ul className="rd-anexos">
            {anexos.map((a) => (
              <li key={str(a.id)}>
                <a href={`/api/radar/anexos/${str(a.id)}`}>
                  {str(a.nome_arquivo)}
                </a>
                <small>
                  {(Number(a.tamanho) / 1024).toFixed(0)} KB ·{" "}
                  {date(a.criado_em)}
                </small>
                {podeEditar && (
                  <button type="button" onClick={() => removerAnexo(str(a.id))}>
                    Remover
                  </button>
                )}
              </li>
            ))}
          </ul>
          {podeEditar && (
            <label className="rd-anexo-upload">
              + Anexar arquivo (até 2 MB: PDF, imagem, Word, Excel ou texto)
              <input
                type="file"
                multiple
                accept=".pdf,.jpg,.jpeg,.png,.webp,.txt,.csv,.doc,.docx,.xls,.xlsx"
                onChange={(e) => {
                  enviarAnexos(e.target.files);
                  e.target.value = "";
                }}
              />
            </label>
          )}
        </div>
        <Campo rotulo="Observações" largo>
          <textarea
            id="cliente-observacao"
            rows={6}
            value={str(v.observacao)}
            onChange={set("observacao")}
            disabled={!podeEditar}
          />
        </Campo>
      </div>
    ),
    historico: (
      <div>
        <p>
          <Badge tone={tom}>{classe}</Badge>{" "}
          {pedidos.length === 0
            ? "Ainda não comprou."
            : `${pedidos.filter((p) => p.estado !== "CANCELADO").length} pedido(s) válidos.`}
        </p>
        <Table
          headers={[
            "Pedido",
            "Data",
            "Canal",
            "Produto",
            "Qtd.",
            "Total",
            "Situação",
          ]}
          rows={pedidos.map((p) => [
            str(p.numero),
            date(p.criado_em),
            str(p.canal),
            str(p.produto),
            str(p.quantidade),
            money(p.total),
            str(p.estado),
          ])}
        />
        <p className="rd-note">{str(carregado?.fonte)}</p>
      </div>
    ),
  };

  return (
    <div className="rd-produto-form">
      <div className="rd-toolbar">
        <button type="button" onClick={voltar}>
          ← Voltar para clientes
        </button>
        <span className="rd-produto-titulo">
          {str(v.nome) || (novo ? "Novo cliente" : "Cliente")}
          {str(v.codigo) && <small> · {str(v.codigo)}</small>}
          {carregado?.incompleto === true && (
            <Badge tone="amber">Cadastro incompleto</Badge>
          )}
        </span>
        {podeEditar && (
          <button
            type="button"
            className="primary"
            disabled={salvando}
            onClick={salvar}
          >
            {salvando ? "Salvando…" : "Salvar cliente"}
          </button>
        )}
      </div>
      {carregado?.incompleto === true && (
        <p className="rd-note">
          {carregado.origem === "PEDIDO"
            ? "Criado automaticamente por um pedido. "
            : ""}
          Complete os campos com * (os que a nota fiscal exige) e salve.
        </p>
      )}
      {erro && (
        <div className="rd-error" role="alert">
          {erro}
        </div>
      )}
      <div
        className="rd-tabs"
        role="tablist"
        aria-label="Seções do cadastro do cliente"
      >
        {abas.map(([chave, rotulo]) => (
          <button
            key={chave}
            role="tab"
            aria-selected={aba === chave}
            className={aba === chave ? "active" : ""}
            onClick={() => setAba(chave)}
          >
            {rotulo}
          </button>
        ))}
      </div>
      <section className="rd-card">{conteudo[aba]}</section>
      {novo && (
        <p className="rd-note">
          Anexos e histórico de compras aparecem depois de salvar.
        </p>
      )}
      <div className="rd-actions rd-produto-rodape">
        <button type="button" onClick={voltar}>
          Voltar
        </button>
        {podeEditar && (
          <button
            type="button"
            className="primary"
            disabled={salvando}
            onClick={salvar}
          >
            {salvando ? "Salvando…" : "Salvar cliente"}
          </button>
        )}
      </div>
    </div>
  );
}
