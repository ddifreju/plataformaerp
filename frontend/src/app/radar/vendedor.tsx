"use client";

// Vendedores: cadastro completo como o do cliente (dados, endereço, contato),
// mais acesso ao sistema e comissão. As restrições de acesso ficam salvas e
// passam a valer quando o login do vendedor for ativado.

import { useEffect, useState, type ReactNode } from "react";
import { Campo, CONTRIBUINTE, TIPOS_PESSOA, UFS, formatarDocumento } from "./cliente";
import { Badge, Empty, Table, str, type Row } from "./ui";

type Valores = Record<string, string | boolean>;

type Props = {
  vendedores: Row[];
  usuariosSistema: Row[];
  podeEditar: boolean;
  podeVerDetalhe: boolean;
  podeAlterarSenha: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<Record<string, unknown> | null>;
};

const ABAS = [
  ["dados", "Dados gerais"],
  ["endereco", "Endereço"],
  ["contato", "Contato"],
  ["acesso", "Dados de acesso"],
  ["comissao", "Comissionamento"],
  ["observacoes", "Observações"],
] as const;
type Aba = (typeof ABAS)[number][0];

const DIAS: [string, string][] = [
  ["SEG", "Seg"],
  ["TER", "Ter"],
  ["QUA", "Qua"],
  ["QUI", "Qui"],
  ["SEX", "Sex"],
  ["SAB", "Sáb"],
  ["DOM", "Dom"],
];
const MODULOS: [string, string][] = [
  ["CLIENTES", "Clientes"],
  ["COMISSOES", "Comissões"],
  ["CRM", "CRM"],
  ["PEDIDOS", "Pedidos de venda"],
  ["PDV", "PDV"],
  ["PROPOSTAS", "Propostas comerciais"],
  ["RELATORIO_PRECOS", "Relatório de preços de produtos"],
  ["PERFORMANCE", "Performance de vendas"],
  ["COTACAO_FRETE", "Cotação de fretes"],
];
const PERFIS: [string, string][] = [
  ["QUALQUER", "Qualquer perfil de contato"],
  ["CLIENTE", "Só clientes"],
  ["FORNECEDOR", "Só fornecedores"],
  ["TRANSPORTADOR", "Só transportadores"],
];
const CAMPOS = [
  "codigo",
  "nome",
  "fantasia",
  "tipo_pessoa",
  "documento",
  "contribuinte",
  "inscricao_estadual",
  "cep",
  "endereco",
  "numero",
  "complemento",
  "bairro",
  "cidade",
  "uf",
  "municipio_ibge",
  "telefone",
  "celular",
  "email",
  "email_comunicacoes",
  "situacao",
  "deposito",
  "usuario_id",
  "acesso_horario_inicio",
  "acesso_horario_fim",
  "perfil_contatos",
  "comissao_regra",
  "comissao_aliquota",
  "observacoes",
];

function lista(v: unknown): string[] {
  return Array.isArray(v) ? (v as string[]) : [];
}

function iniciais(c: Row | null): Valores {
  const v: Valores = {
    pode_incluir_produto_nao_cadastrado: c?.pode_incluir_produto_nao_cadastrado === true,
    pode_emitir_cobrancas: c?.pode_emitir_cobrancas === true,
    desconsiderar_comissao_linha: c?.desconsiderar_comissao_linha === true,
    restrito_horario: !!c?.acesso_horario_inicio,
    restrito_ip: lista(c?.acesso_ips).length > 0,
  };
  for (const k of CAMPOS) v[k] = c?.[k] == null ? "" : str(c[k]);
  if (!c) {
    v.tipo_pessoa = "F";
    v.situacao = "ATIVO";
    v.perfil_contatos = "QUALQUER";
    v.comissao_regra = "FIXA";
  }
  if (c?.documento) v.documento = formatarDocumento(str(c.documento));
  if (str(c?.comissao_aliquota) === "0.00") v.comissao_aliquota = "";
  return v;
}

const ABAS_LISTA: [string, string][] = [
  ["ACESSO", "Ativos com acesso ao sistema"],
  ["TODOS", "Todos"],
  ["ATIVO", "Ativos"],
  ["INATIVO", "Inativos"],
  ["EXCLUIDO", "Excluídos"],
];

function naAba(v: Row, aba: string) {
  const excluido = !!v.excluido_em;
  if (aba === "EXCLUIDO") return excluido;
  if (excluido) return false;
  if (aba === "ACESSO") return v.situacao === "ATIVO" && !!v.usuario_id;
  if (aba === "ATIVO" || aba === "INATIVO") return v.situacao === aba;
  return true;
}

type Rapida = { tipo: "comissao" | "senha" | "excluir" | "restaurar"; ids: string[] };

export default function Vendedores(props: Props) {
  const [aberto, setAberto] = useState<{ id: string | null; versao: number } | null>(null);
  const [busca, setBusca] = useState("");
  const [aba, setAba] = useState("ACESSO");
  const [marcados, setMarcados] = useState<string[]>([]);
  const [menuLinha, setMenuLinha] = useState<{ id: string; x: number; y: number } | null>(null);
  const [rapida, setRapida] = useState<Rapida | null>(null);
  const [aviso, setAviso] = useState("");

  if (aberto)
    return (
      <VendedorForm
        key={`${aberto.id ?? "novo"}-${aberto.versao}`}
        id={aberto.id}
        {...props}
        voltar={() => setAberto(null)}
        aoSalvar={(id) => setAberto({ id, versao: Date.now() })}
      />
    );

  const termo = busca.trim().toLowerCase();
  const linhas = props.vendedores.filter(
    (v) =>
      naAba(v, aba) &&
      (!termo ||
        [v.nome, v.fantasia, v.codigo, v.email, v.cidade].some((x) =>
          str(x).toLowerCase().includes(termo),
        )),
  );
  const visiveis = marcados.filter((id) => linhas.some((v) => str(v.id) === id));
  const todos = linhas.length > 0 && linhas.every((v) => marcados.includes(str(v.id)));
  const naLixeira = aba === "EXCLUIDO";
  const vendedorLinha = menuLinha && props.vendedores.find((v) => str(v.id) === menuLinha.id);

  function trocarAba(chave: string) {
    setAba(chave);
    setMarcados([]);
    setMenuLinha(null);
  }

  function abrirRapida(r: Rapida) {
    setMenuLinha(null);
    setAviso("");
    setRapida(r);
  }

  // Depois de uma ação, tira da seleção só os vendedores em que ela valeu.
  function concluir(ids: string[]) {
    setRapida(null);
    setMarcados((m) => m.filter((id) => !ids.includes(id)));
  }

  const caixa = (v: Row) => {
    const id = str(v.id);
    return (
      <span key="m" className="rd-celula-lote">
        <input
          type="checkbox"
          aria-label={`Selecionar ${str(v.nome)}`}
          checked={marcados.includes(id)}
          onChange={(e) =>
            setMarcados((m) => (e.target.checked ? [...m, id] : m.filter((x) => x !== id)))
          }
        />
        <button
          type="button"
          className="rd-linha-mais"
          aria-label={`Edição rápida de ${str(v.nome)}`}
          title="Edição rápida"
          aria-expanded={menuLinha?.id === id}
          onClick={(e) => {
            const r = e.currentTarget.getBoundingClientRect();
            // Perto do rodapé o menu abre para cima, para não sair da tela.
            const altura = 200;
            const y =
              r.bottom + altura > window.innerHeight ? Math.max(8, r.top - altura) : r.bottom + 4;
            setMenuLinha(menuLinha?.id === id ? null : { id, x: r.left, y });
          }}
        >
          ⋯
        </button>
      </span>
    );
  };

  return (
    <section className="rd-card">
      <div className="rd-card-head">
        <h2>{props.vendedores.filter((v) => !v.excluido_em).length} vendedores</h2>
        <input
          aria-label="Buscar vendedor"
          className="rd-search"
          placeholder="Buscar por nome, código, e-mail…"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
        {props.podeEditar && (
          <button className="primary" onClick={() => setAberto({ id: null, versao: Date.now() })}>
            + Novo vendedor
          </button>
        )}
      </div>
      <div className="rd-tabs" role="tablist" aria-label="Situação dos vendedores">
        {ABAS_LISTA.map(([chave, rotulo]) => (
          <button
            key={chave}
            role="tab"
            aria-selected={aba === chave}
            className={aba === chave ? "active" : ""}
            onClick={() => trocarAba(chave)}
          >
            {rotulo}
            <span className="rd-tab-num">
              {props.vendedores.filter((v) => naAba(v, chave)).length}
            </span>
          </button>
        ))}
      </div>
      {aviso && (
        <p className="rd-ok" role="status">
          {aviso}
        </p>
      )}
      {props.vendedores.length === 0 ? (
        <Empty text="Nenhum vendedor cadastrado ainda." />
      ) : linhas.length === 0 ? (
        <Empty text="Nenhum vendedor nesta aba." />
      ) : (
        <Table
          headers={[
            props.podeEditar ? (
              <input
                key="todos"
                type="checkbox"
                aria-label="Marcar todos da lista"
                title="Marcar todos da lista"
                checked={todos}
                onChange={(e) => setMarcados(e.target.checked ? linhas.map((v) => str(v.id)) : [])}
              />
            ) : (
              ""
            ),
            "Código",
            "Vendedor",
            "CPF/CNPJ",
            "Cidade",
            "Situação",
            "Comissão",
            "Clientes",
            "Usuário do sistema",
            "",
          ]}
          rows={linhas.map((v) => [
            props.podeEditar ? caixa(v) : "",
            str(v.codigo),
            <div key="n">
              <strong>{str(v.nome)}</strong>
              {v.fantasia && <small> · {str(v.fantasia)}</small>}
              <br />
              <small>{str(v.email) || str(v.celular)}</small>
            </div>,
            str(v.documento) || "—",
            [str(v.cidade), str(v.uf)].filter(Boolean).join(" / ") || "—",
            v.excluido_em ? (
              <Badge key="s" tone="red">
                Excluído
              </Badge>
            ) : (
              <Badge key="s" tone={v.situacao === "ATIVO" ? "green" : "gray"}>
                {v.situacao === "ATIVO" ? "Ativo" : "Inativo"}
              </Badge>
            ),
            "comissao_aliquota" in v
              ? `${str(v.comissao_aliquota).replace(".", ",")}% ${v.comissao_regra === "DESCONTO" ? "(conforme desconto)" : "(fixa)"}`
              : "—",
            str(v.clientes),
            str(v.usuario_nome) || "—",
            props.podeVerDetalhe && !v.excluido_em ? (
              <button key="a" onClick={() => setAberto({ id: str(v.id), versao: Date.now() })}>
                {props.podeEditar ? "Editar" : "Ver"}
              </button>
            ) : (
              ""
            ),
          ])}
        />
      )}
      {props.podeEditar && visiveis.length > 0 && (
        <div className="rd-barra-lote" role="region" aria-label="Ações para os vendedores marcados">
          <span className="rd-barra-qtd rd-pilula">
            <span aria-hidden="true">↥</span>
            {String(visiveis.length).padStart(2, "0")}
            <button
              type="button"
              aria-label="Limpar seleção"
              title="Limpar seleção"
              onClick={() => setMarcados([])}
            >
              ✕
            </button>
          </span>
          {naLixeira ? (
            <button
              type="button"
              className="primary"
              onClick={() => abrirRapida({ tipo: "restaurar", ids: visiveis })}
            >
              ↺ Restaurar vendedores
            </button>
          ) : (
            <button
              type="button"
              className="primary"
              onClick={() => abrirRapida({ tipo: "excluir", ids: visiveis })}
            >
              🗑 Excluir vendedores
            </button>
          )}
          <span className="rd-barra-total">
            <strong>{linhas.length}</strong>
            <small>cadastros</small>
          </span>
          <button
            type="button"
            className="rd-barra-topo"
            aria-label="Voltar ao topo da página"
            title="Voltar ao topo da página"
            onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
          >
            ↑
          </button>
        </div>
      )}
      {menuLinha && vendedorLinha && (
        <>
          <div className="rd-menu-fundo" onClick={() => setMenuLinha(null)} />
          <ul
            role="menu"
            className="rd-menu-linha"
            style={{ left: menuLinha.x, top: menuLinha.y }}
            aria-label={`Edição rápida de ${str(vendedorLinha.nome)}`}
          >
            <li className="rd-menu-titulo">
              <span className="rd-circulo-cheio" aria-hidden="true">
                ⋯
              </span>
              {str(vendedorLinha.nome)}
            </li>
            {vendedorLinha.excluido_em ? (
              <li>
                <button
                  role="menuitem"
                  onClick={() => abrirRapida({ tipo: "restaurar", ids: [menuLinha.id] })}
                >
                  ↺ Restaurar vendedor
                </button>
              </li>
            ) : (
              <>
                <li>
                  <button
                    role="menuitem"
                    onClick={() => abrirRapida({ tipo: "comissao", ids: [menuLinha.id] })}
                  >
                    ▤ Gerenciar comissões
                  </button>
                </li>
                <li>
                  <button
                    role="menuitem"
                    onClick={() => abrirRapida({ tipo: "excluir", ids: [menuLinha.id] })}
                  >
                    🗑 Excluir vendedor
                  </button>
                </li>
                <li>
                  <button
                    role="menuitem"
                    disabled={!props.podeAlterarSenha || !vendedorLinha.usuario_id}
                    title={
                      !props.podeAlterarSenha
                        ? "Só a dona da conta troca senhas."
                        : !vendedorLinha.usuario_id
                          ? "Este vendedor não tem usuário do sistema. Ligue um em Dados de acesso."
                          : undefined
                    }
                    onClick={() => abrirRapida({ tipo: "senha", ids: [menuLinha.id] })}
                  >
                    ⚿ Alterar senha de acesso
                  </button>
                </li>
              </>
            )}
          </ul>
        </>
      )}
      {rapida && (
        <EdicaoRapida
          rapida={rapida}
          vendedores={props.vendedores}
          executar={props.executar}
          fechar={() => setRapida(null)}
          concluir={() => {
            if (rapida.tipo === "senha") setAviso("✓ Senha de acesso alterada.");
            concluir(rapida.ids);
          }}
        />
      )}
      <p className="rd-note">
        O vendedor aparece como &quot;Vendedor padrão&quot; no cadastro do cliente. CPF/CNPJ
        completo só ao abrir o cadastro. Vendedor excluído vai para a aba &quot;Excluídos&quot;,
        perde o acesso ao sistema e pode ser restaurado.
      </p>
    </section>
  );
}

/** Janelas da edição rápida (⋯ da linha) e das ações em lote. */
function EdicaoRapida({
  rapida,
  vendedores,
  executar,
  fechar,
  concluir,
}: {
  rapida: Rapida;
  vendedores: Row[];
  executar: Props["executar"];
  fechar: () => void;
  concluir: () => void;
}) {
  const um = vendedores.find((v) => str(v.id) === rapida.ids[0]);
  const [regra, setRegra] = useState(str(um?.comissao_regra) || "FIXA");
  const [aliquota, setAliquota] = useState(str(um?.comissao_aliquota).replace(".", ","));
  const [semLinha, setSemLinha] = useState(um?.desconsiderar_comissao_linha === true);
  const [senha, setSenha] = useState("");
  const [confirmacao, setConfirmacao] = useState("");
  const [erro, setErro] = useState("");
  const [ocupado, setOcupado] = useState(false);
  const titulo = {
    comissao: "Gerenciar comissões",
    senha: "Alterar senha de acesso",
    excluir: rapida.ids.length > 1 ? "Excluir vendedores" : "Excluir vendedor",
    restaurar: rapida.ids.length > 1 ? "Restaurar vendedores" : "Restaurar vendedor",
  }[rapida.tipo];
  const quem = rapida.ids.length === 1 ? str(um?.nome) : `${rapida.ids.length} vendedores`;

  async function confirmar() {
    setErro("");
    setOcupado(true);
    try {
      if (rapida.tipo === "senha") {
        const r = await fetch(`/api/radar/vendedores/${rapida.ids[0]}/senha`, {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json", "X-Radar-Request": "1" },
          body: JSON.stringify({ senha, confirmacao }),
        });
        const d = await r.json().catch(() => ({}));
        if (!r.ok) {
          setErro(str(d.mensagem) || "Não foi possível trocar a senha.");
          return;
        }
        concluir();
        return;
      }
      const corpo =
        rapida.tipo === "comissao"
          ? {
              op: "vendedor_comissao",
              id: rapida.ids[0],
              comissao_regra: regra,
              comissao_aliquota: aliquota.trim() || "0",
              desconsiderar_comissao_linha: semLinha,
            }
          : {
              op: "vendedores_lote",
              acao: rapida.tipo === "excluir" ? "EXCLUIR" : "RESTAURAR",
              ids: rapida.ids,
            };
      if (await executar(corpo)) concluir();
    } finally {
      setOcupado(false);
    }
  }

  return (
    <div className="rd-modal-backdrop" onClick={fechar}>
      <section
        className="rd-modal"
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="rd-card-head">
          <h2>{titulo}</h2>
          <button aria-label="Fechar" onClick={fechar}>
            ×
          </button>
        </div>
        <p className="rd-note">{quem}</p>
        {rapida.tipo === "comissao" && (
          <div className="rd-form-grid">
            <label>
              Regra
              <select value={regra} onChange={(e) => setRegra(e.target.value)}>
                <option value="FIXA">Alíquota fixa</option>
                <option value="DESCONTO">Conforme o desconto do pedido</option>
              </select>
            </label>
            <label>
              Alíquota (%)
              <input
                inputMode="decimal"
                placeholder="Ex.: 5"
                value={aliquota}
                onChange={(e) => setAliquota(e.target.value)}
              />
            </label>
            <label className="rd-check wide">
              <input
                type="checkbox"
                checked={semLinha}
                onChange={(e) => setSemLinha(e.target.checked)}
              />
              Desconsiderar a comissão definida nas linhas de produtos
            </label>
          </div>
        )}
        {rapida.tipo === "senha" && (
          <div className="rd-form-grid">
            <label>
              Nova senha
              <input
                type="password"
                autoComplete="new-password"
                value={senha}
                onChange={(e) => setSenha(e.target.value)}
              />
            </label>
            <label>
              Repita a senha
              <input
                type="password"
                autoComplete="new-password"
                value={confirmacao}
                onChange={(e) => setConfirmacao(e.target.value)}
              />
            </label>
            <small className="rd-dica wide">
              Pelo menos 8 caracteres. Vale para o usuário do sistema ligado a este vendedor.
            </small>
          </div>
        )}
        {rapida.tipo === "excluir" && (
          <p>
            O cadastro vai para a aba &quot;Excluídos&quot; e o acesso ao sistema é desligado. Os
            clientes e pedidos antigos continuam mostrando este vendedor. Dá para restaurar depois.
          </p>
        )}
        {rapida.tipo === "restaurar" && (
          <p>
            O cadastro volta para a lista como inativo e sem usuário do sistema. Para devolver o
            acesso, edite o vendedor e ligue o usuário de novo.
          </p>
        )}
        {erro && (
          <div className="rd-error" role="alert">
            {erro}
          </div>
        )}
        <div className="rd-modal-foot">
          <button onClick={fechar}>Cancelar</button>
          <button
            className={rapida.tipo === "excluir" ? "perigo" : "primary"}
            disabled={ocupado}
            onClick={confirmar}
          >
            {rapida.tipo === "excluir"
              ? "Excluir"
              : rapida.tipo === "restaurar"
                ? "Restaurar"
                : "Salvar"}
          </button>
        </div>
      </section>
    </div>
  );
}

function VendedorForm({
  id,
  usuariosSistema,
  podeEditar,
  executar,
  voltar,
  aoSalvar,
}: Props & { id: string | null; voltar: () => void; aoSalvar: (id: string) => void }) {
  const novo = !id;
  const [carregado, setCarregado] = useState<Row | null>(null);
  const [carregando, setCarregando] = useState(!novo);
  const [aba, setAba] = useState<Aba>("dados");
  const [v, setV] = useState<Valores>(() => iniciais(null));
  const [dias, setDias] = useState<string[]>([]);
  const [ips, setIps] = useState("");
  const [modulos, setModulos] = useState<string[]>([]);
  const [erro, setErro] = useState("");
  const [aviso, setAviso] = useState("");
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    let vivo = true;
    if (id)
      fetch(`/api/radar/vendedores/${id}`, { credentials: "include" })
        .then(async (r) => ({ ok: r.ok, corpo: await r.json().catch(() => ({})) }))
        .then(({ ok, corpo }) => {
          if (!vivo) return;
          if (!ok) {
            setErro(corpo.mensagem ?? "Não foi possível abrir o vendedor.");
            return;
          }
          setCarregado(corpo);
          setV(iniciais(corpo));
          setDias(lista(corpo.acesso_dias));
          setIps(lista(corpo.acesso_ips).join("\n"));
          setModulos(lista(corpo.modulos));
        })
        .finally(() => {
          if (vivo) setCarregando(false);
        });
    return () => {
      vivo = false;
    };
  }, [id]);

  const tipo = str(v.tipo_pessoa);
  const rotuloDoc = TIPOS_PESSOA.find((t) => t[0] === tipo)?.[2] ?? "Documento";
  const set = (k: string) => (e: { target: { value: string } }) =>
    setV((a) => ({ ...a, [k]: e.target.value }));
  const marca = (k: string) => (e: { target: { checked: boolean } }) =>
    setV((a) => ({ ...a, [k]: e.target.checked }));
  const entrada = (k: string, extra: Record<string, unknown> = {}) => (
    <input
      id={`vendedor-${k}`}
      value={str(v[k])}
      onChange={set(k)}
      disabled={!podeEditar}
      {...extra}
    />
  );
  const escolha = (k: string, opcoes: [string, string][]) => (
    <select id={`vendedor-${k}`} value={str(v[k])} onChange={set(k)} disabled={!podeEditar}>
      {opcoes.map(([valor, r]) => (
        <option key={valor} value={valor}>
          {r}
        </option>
      ))}
    </select>
  );
  const alternar = (lista: string[], setLista: (l: string[]) => void, valor: string) =>
    setLista(lista.includes(valor) ? lista.filter((x) => x !== valor) : [...lista, valor]);

  async function buscarCep() {
    const cep = str(v.cep).replace(/\D/g, "");
    if (cep.length !== 8) return;
    try {
      const d = await (await fetch(`https://viacep.com.br/ws/${cep}/json/`)).json();
      if (d.erro) {
        setAviso("CEP não encontrado. Preencha o endereço à mão.");
        return;
      }
      setAviso("");
      setV((a) => ({
        ...a,
        endereco: d.logradouro || a.endereco,
        bairro: d.bairro || a.bairro,
        cidade: d.localidade || a.cidade,
        uf: d.uf || a.uf,
        municipio_ibge: d.ibge || "",
      }));
    } catch {
      setAviso("Não deu para consultar o CEP agora. Preencha o endereço à mão.");
    }
  }

  async function salvar() {
    setErro("");
    const faltando: [string, Aba][] = [];
    if (!str(v.nome).trim()) faltando.push(["Nome", "dados"]);
    if ((tipo === "F" || tipo === "J") && !str(v.documento).trim())
      faltando.push([rotuloDoc, "dados"]);
    if (!str(v.email).trim()) faltando.push(["E-mail", "contato"]);
    if (faltando.length) {
      setErro(`Preencha os campos obrigatórios: ${faltando.map((f) => f[0]).join(", ")}.`);
      setAba(faltando[0][1]);
      return;
    }
    setSalvando(true);
    try {
      const corpo: Record<string, unknown> = {
        op: "vendedor_salvar",
        ...v,
        acesso_horario_inicio: v.restrito_horario ? v.acesso_horario_inicio : "",
        acesso_horario_fim: v.restrito_horario ? v.acesso_horario_fim : "",
        acesso_dias: v.restrito_horario ? dias : [],
        acesso_ips: v.restrito_ip
          ? ips
              .split(/[\s,;]+/)
              .map((x) => x.trim())
              .filter(Boolean)
          : [],
        modulos,
      };
      if (id) corpo.id = id;
      const r = await executar(corpo);
      if (r) aoSalvar(str(r.id));
    } finally {
      setSalvando(false);
    }
  }

  if (carregando) return <section className="rd-card">Abrindo cadastro…</section>;

  const usuarioLigado = usuariosSistema.find((u) => u.id === v.usuario_id);
  const conteudo: Record<Aba, ReactNode> = {
    dados: (
      <div className="rd-form-grid">
        <Campo rotulo="Tipo de pessoa" obrigatorio largo>
          <div className="rd-opcoes">
            {TIPOS_PESSOA.map(([valor, r]) => (
              <label key={valor} className={tipo === valor ? "ativo" : ""}>
                <input
                  type="radio"
                  name="vendedor-tipo"
                  value={valor}
                  checked={tipo === valor}
                  disabled={!podeEditar}
                  onChange={set("tipo_pessoa")}
                />
                {r}
              </label>
            ))}
          </div>
        </Campo>
        <Campo rotulo={tipo === "J" ? "Razão social" : "Nome"} obrigatorio largo>
          {entrada("nome", { maxLength: 200 })}
        </Campo>
        <Campo rotulo="Nome fantasia">{entrada("fantasia", { maxLength: 200 })}</Campo>
        <Campo rotulo="Código" dica="Em branco, o Radar gera (V00001, V00002…).">
          {entrada("codigo", { maxLength: 30 })}
        </Campo>
        {tipo !== "E" && (
          <Campo
            rotulo={rotuloDoc}
            obrigatorio={tipo === "F" || tipo === "J"}
            dica="Conferimos os dígitos."
          >
            {entrada("documento", {
              maxLength: 18,
              onBlur: () => setV((a) => ({ ...a, documento: formatarDocumento(str(a.documento)) })),
            })}
          </Campo>
        )}
        <Campo rotulo="Contribuinte">{escolha("contribuinte", CONTRIBUINTE)}</Campo>
        <Campo rotulo="Inscrição estadual" dica="Só números, ou ISENTO.">
          {entrada("inscricao_estadual", { maxLength: 20 })}
        </Campo>
        <Campo rotulo="Situação">
          {escolha("situacao", [
            ["ATIVO", "Ativo"],
            ["INATIVO", "Inativo"],
          ])}
        </Campo>
        <Campo rotulo="Depósito" dica="De onde saem as vendas deste vendedor.">
          {entrada("deposito", { maxLength: 60, placeholder: "Ex.: Depósito principal" })}
        </Campo>
      </div>
    ),
    endereco: (
      <div className="rd-form-grid">
        {aviso && <p className="rd-dica wide">{aviso}</p>}
        <Campo rotulo="CEP" dica="Ao sair do campo, buscamos o endereço.">
          {entrada("cep", { maxLength: 9, inputMode: "numeric", onBlur: buscarCep })}
        </Campo>
        <Campo rotulo="Endereço" largo>
          {entrada("endereco", { maxLength: 200 })}
        </Campo>
        <Campo rotulo="Número">{entrada("numero", { maxLength: 20 })}</Campo>
        <Campo rotulo="Complemento">{entrada("complemento", { maxLength: 120 })}</Campo>
        <Campo rotulo="Bairro">{entrada("bairro", { maxLength: 120 })}</Campo>
        <Campo rotulo="Cidade">{entrada("cidade", { maxLength: 120 })}</Campo>
        <Campo rotulo="UF">
          {escolha("uf", [["", "Escolha"], ...UFS.map((u): [string, string] => [u, u])])}
        </Campo>
      </div>
    ),
    contato: (
      <div className="rd-form-grid">
        <Campo rotulo="Telefone">{entrada("telefone", { maxLength: 40, type: "tel" })}</Campo>
        <Campo rotulo="Celular">{entrada("celular", { maxLength: 40, type: "tel" })}</Campo>
        <Campo rotulo="E-mail" obrigatorio>
          {entrada("email", { maxLength: 320, type: "email" })}
        </Campo>
        <Campo
          rotulo="E-mail para comunicações"
          dica="Para onde vão avisos de comissão e pedidos. Em branco, usa o e-mail acima."
        >
          {entrada("email_comunicacoes", { maxLength: 320, type: "email" })}
        </Campo>
      </div>
    ),
    acesso: (
      <div className="rd-form-grid">
        <div className="wide rd-aviso-categoria">
          Estas configurações ficam salvas e passam a valer quando o login do vendedor for ativado.
          A senha de acesso é definida num segundo passo, depois de salvar o vendedor.
        </div>
        <Campo
          rotulo="Usuário do sistema"
          dica="Liga este vendedor a um login que já existe. Cada login atende um vendedor só."
          largo
        >
          {escolha("usuario_id", [
            ["", "Nenhum (só cadastro)"],
            ...usuariosSistema.map((u): [string, string] => [
              str(u.id),
              `${str(u.nome)} · ${str(u.email)}`,
            ]),
          ])}
          {v.usuario_id && !usuarioLigado && (
            <small className="rd-dica">Usuário ligado: só o dono ou o gestor veem qual é.</small>
          )}
        </Campo>
        <h3 className="rd-secao wide">Restrições de acesso</h3>
        <label className="rd-check wide">
          <input
            type="checkbox"
            checked={v.restrito_horario === true}
            disabled={!podeEditar}
            onChange={marca("restrito_horario")}
          />
          Acesso restrito por horário
        </label>
        {v.restrito_horario === true && (
          <>
            <Campo rotulo="Das">{entrada("acesso_horario_inicio", { type: "time" })}</Campo>
            <Campo rotulo="Até">{entrada("acesso_horario_fim", { type: "time" })}</Campo>
            <Campo rotulo="Dias da semana" dica="Nenhum marcado = todos os dias." largo>
              <div className="rd-opcoes">
                {DIAS.map(([valor, r]) => (
                  <label key={valor} className={dias.includes(valor) ? "ativo" : ""}>
                    <input
                      type="checkbox"
                      checked={dias.includes(valor)}
                      disabled={!podeEditar}
                      onChange={() => alternar(dias, setDias, valor)}
                    />
                    {r}
                  </label>
                ))}
              </div>
            </Campo>
          </>
        )}
        <label className="rd-check wide">
          <input
            type="checkbox"
            checked={v.restrito_ip === true}
            disabled={!podeEditar}
            onChange={marca("restrito_ip")}
          />
          Acesso restrito por IP
        </label>
        {v.restrito_ip === true && (
          <Campo
            rotulo="IPs liberados"
            dica="Utilize esta opção apenas caso tenha IP fixo. Um por linha (ex.: 200.150.10.5 ou 200.150.10.0/24)."
            largo
          >
            <textarea
              id="vendedor-ips"
              rows={3}
              value={ips}
              disabled={!podeEditar}
              onChange={(e) => setIps(e.target.value)}
            />
          </Campo>
        )}
        <Campo rotulo="Pode acessar contatos com o perfil" largo>
          {escolha("perfil_contatos", PERFIS)}
        </Campo>
        <Campo rotulo="Módulos que podem ser acessados pelo vendedor" largo>
          <div className="rd-opcoes">
            {MODULOS.map(([valor, r]) => (
              <label key={valor} className={modulos.includes(valor) ? "ativo" : ""}>
                <input
                  type="checkbox"
                  checked={modulos.includes(valor)}
                  disabled={!podeEditar}
                  onChange={() => alternar(modulos, setModulos, valor)}
                />
                {r}
              </label>
            ))}
          </div>
        </Campo>
        <label className="rd-check wide">
          <input
            type="checkbox"
            checked={v.pode_incluir_produto_nao_cadastrado === true}
            disabled={!podeEditar}
            onChange={marca("pode_incluir_produto_nao_cadastrado")}
          />
          Tem permissão para incluir produtos não cadastrados em pedidos de venda e propostas
          comerciais
        </label>
        <label className="rd-check wide">
          <input
            type="checkbox"
            checked={v.pode_emitir_cobrancas === true}
            disabled={!podeEditar}
            onChange={marca("pode_emitir_cobrancas")}
          />
          Pode emitir cobranças
        </label>
      </div>
    ),
    comissao: (
      <div className="rd-form-grid">
        <Campo rotulo="Regras para liberação de comissões" largo>
          <div className="rd-opcoes">
            {[
              ["FIXA", "Comissão com alíquota fixa"],
              ["DESCONTO", "Comissão com alíquota conforme descontos"],
            ].map(([valor, r]) => (
              <label key={valor} className={v.comissao_regra === valor ? "ativo" : ""}>
                <input
                  type="radio"
                  name="vendedor-comissao"
                  value={valor}
                  checked={v.comissao_regra === valor}
                  disabled={!podeEditar}
                  onChange={set("comissao_regra")}
                />
                {r}
              </label>
            ))}
          </div>
        </Campo>
        <Campo
          rotulo="Alíquota de comissão (%)"
          dica={
            v.comissao_regra === "DESCONTO"
              ? "Alíquota cheia, para venda sem desconto. A regra de redução por desconto entra junto com o cálculo de comissões."
              : "Percentual sobre o valor da venda."
          }
        >
          {entrada("comissao_aliquota", { inputMode: "decimal", placeholder: "0,00" })}
        </Campo>
        <label className="rd-check wide">
          <input
            type="checkbox"
            checked={v.desconsiderar_comissao_linha === true}
            disabled={!podeEditar}
            onChange={marca("desconsiderar_comissao_linha")}
          />
          Desconsiderar comissionamento por linhas de produto para este vendedor
        </label>
      </div>
    ),
    observacoes: (
      <Campo rotulo="Observações" largo>
        <textarea
          id="vendedor-observacoes"
          rows={8}
          maxLength={2000}
          value={str(v.observacoes)}
          disabled={!podeEditar}
          onChange={set("observacoes")}
        />
      </Campo>
    ),
  };

  return (
    <div className="rd-produto-form">
      <div className="rd-toolbar">
        <button type="button" onClick={voltar}>
          ← Voltar para vendedores
        </button>
        <span className="rd-produto-titulo">
          {str(v.nome) || (novo ? "Novo vendedor" : "Vendedor")}
          {str(v.codigo) && <small> · {str(v.codigo)}</small>}
          {carregado && v.situacao === "INATIVO" && <Badge>Inativo</Badge>}
        </span>
        {podeEditar && (
          <button type="button" className="primary" disabled={salvando} onClick={salvar}>
            {salvando ? "Salvando…" : "Salvar vendedor"}
          </button>
        )}
      </div>
      {erro && (
        <div className="rd-error" role="alert">
          {erro}
        </div>
      )}
      <div className="rd-tabs" role="tablist" aria-label="Seções do cadastro do vendedor">
        {ABAS.map(([chave, r]) => (
          <button
            key={chave}
            role="tab"
            aria-selected={aba === chave}
            className={aba === chave ? "active" : ""}
            onClick={() => setAba(chave)}
          >
            {r}
          </button>
        ))}
      </div>
      <section className="rd-card">{conteudo[aba]}</section>
      <div className="rd-actions rd-produto-rodape">
        <button type="button" onClick={voltar}>
          Voltar
        </button>
        {podeEditar && (
          <button type="button" className="primary" disabled={salvando} onClick={salvar}>
            {salvando ? "Salvando…" : "Salvar vendedor"}
          </button>
        )}
      </div>
    </div>
  );
}
