"use client";

// Configurações → geral: "Alterar dados da empresa" e "Cadastro de usuários
// do sistema". Só a dona altera; os demais veem os dados da empresa.

import { useState } from "react";
import { Campo, formatarDocumento, UFS } from "./cliente";
import { Badge, date, str, type Row } from "./ui";

type Executar = (corpo: Record<string, unknown>) => Promise<Record<string, unknown> | null>;

const REGIMES: [string, string][] = [
  ["", "Escolha"],
  ["MEI", "MEI"],
  ["SIMPLES", "Simples Nacional"],
  ["SIMPLES_EXCESSO", "Simples Nacional – excesso de sublimite"],
  ["PRESUMIDO", "Lucro Presumido"],
  ["REAL", "Lucro Real"],
];

export const CARGOS: [string, string, string][] = [
  ["DONO", "Dono", "Vê e faz tudo, inclusive usuários e dados da empresa."],
  ["GESTOR", "Gestor", "Toca a operação: cadastros, pedidos, anúncios e financeiro."],
  ["FINANCEIRO", "Financeiro", "Contas, margem e relatórios financeiros."],
  ["ESTOQUE", "Estoque", "Produtos, estoque, separação e expedição."],
  ["ATENDIMENTO", "Atendimento", "Clientes, pedidos e conversas."],
  ["MARKETING", "Marketing", "Anúncios, imagens e promoções."],
  ["ANALISTA", "Analista", "Consulta: vê os números, não altera."],
];

const CAMPOS_EMPRESA = [
  "razao_social",
  "nome_fantasia",
  "cnpj",
  "inscricao_estadual",
  "inscricao_municipal",
  "regime_tributario",
  "cnae",
  "email",
  "telefone",
  "celular",
  "site",
  "cep",
  "endereco",
  "numero",
  "complemento",
  "bairro",
  "cidade",
  "uf",
  "municipio_ibge",
];

async function enviar(caminho: string, corpo: unknown, metodo = "POST") {
  const r = await fetch(`/api/radar${caminho}`, {
    method: metodo,
    credentials: "include",
    headers:
      corpo instanceof FormData
        ? { "X-Radar-Request": "1" }
        : { "Content-Type": "application/json", "X-Radar-Request": "1" },
    body: corpo instanceof FormData ? corpo : corpo == null ? undefined : JSON.stringify(corpo),
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(str(d.mensagem) || "Não foi possível concluir.");
  return d as Record<string, unknown>;
}

function Voltar({
  voltar,
  titulo,
  children,
}: {
  voltar: () => void;
  titulo: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="rd-toolbar">
      <button type="button" onClick={voltar}>
        ← Configurações
      </button>
      <span className="rd-produto-titulo">{titulo}</span>
      {children}
    </div>
  );
}

export function DadosEmpresa({
  empresa,
  podeEditar,
  executar,
  atualizar,
  voltar,
}: {
  empresa: Row;
  podeEditar: boolean;
  executar: Executar;
  atualizar: () => Promise<void>;
  voltar: () => void;
}) {
  const [v, setV] = useState<Record<string, string>>(() => {
    const ini: Record<string, string> = {};
    for (const k of CAMPOS_EMPRESA) ini[k] = empresa[k] == null ? "" : str(empresa[k]);
    if (ini.cnpj) ini.cnpj = formatarDocumento(ini.cnpj);
    return ini;
  });
  const [erro, setErro] = useState("");
  const [aviso, setAviso] = useState("");
  const [salvando, setSalvando] = useState(false);
  const [versaoLogo, setVersaoLogo] = useState(() => str(empresa.atualizado_em));
  const temLogo = empresa.tem_logo === true;

  const set = (k: string) => (e: { target: { value: string } }) =>
    setV((a) => ({ ...a, [k]: e.target.value }));
  const entrada = (k: string, extra: Record<string, unknown> = {}) => (
    <input id={`empresa-${k}`} value={v[k]} onChange={set(k)} disabled={!podeEditar} {...extra} />
  );

  async function buscarCep() {
    const cep = v.cep.replace(/\D/g, "");
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
    setSalvando(true);
    try {
      await executar({ op: "empresa_salvar", ...v });
    } finally {
      setSalvando(false);
    }
  }

  async function trocarLogo(arquivo: File | undefined) {
    if (!arquivo) return;
    setErro("");
    const f = new FormData();
    f.append("arquivo", arquivo);
    try {
      await enviar("/empresa/logo", f);
      await atualizar();
      setVersaoLogo(String(Date.now()));
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  async function tirarLogo() {
    setErro("");
    try {
      await enviar("/empresa/logo", null, "DELETE");
      await atualizar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  const botaoSalvar = podeEditar && (
    <button type="button" className="primary" disabled={salvando} onClick={salvar}>
      {salvando ? "Salvando…" : "Salvar dados da empresa"}
    </button>
  );

  return (
    <div className="rd-produto-form">
      <Voltar voltar={voltar} titulo="Dados da empresa">
        {botaoSalvar}
      </Voltar>
      {!podeEditar && <p className="rd-note">Só a dona da conta altera os dados da empresa.</p>}
      {erro && (
        <div className="rd-error" role="alert">
          {erro}
        </div>
      )}

      <section className="rd-card">
        <h3 className="rd-config-secao">Identificação</h3>
        <div className="rd-form-grid">
          <Campo rotulo="Razão social" largo dica="Como está no cartão CNPJ.">
            {entrada("razao_social", { maxLength: 200 })}
          </Campo>
          <Campo rotulo="Nome fantasia" dica="Aparece no menu do Radar e nos documentos.">
            {entrada("nome_fantasia", { maxLength: 200 })}
          </Campo>
          <Campo rotulo="CNPJ" dica="Conferimos os dígitos.">
            {entrada("cnpj", {
              maxLength: 18,
              onBlur: () => setV((a) => ({ ...a, cnpj: formatarDocumento(a.cnpj) })),
            })}
          </Campo>
          <Campo rotulo="Inscrição estadual" dica="Só números, ou ISENTO.">
            {entrada("inscricao_estadual", { maxLength: 20 })}
          </Campo>
          <Campo rotulo="Inscrição municipal">
            {entrada("inscricao_municipal", { maxLength: 20 })}
          </Campo>
          <Campo
            rotulo="Regime tributário"
            dica="Define como os impostos das notas são calculados."
          >
            <select
              id="empresa-regime_tributario"
              value={v.regime_tributario}
              onChange={set("regime_tributario")}
              disabled={!podeEditar}
            >
              {REGIMES.map(([valor, r]) => (
                <option key={valor} value={valor}>
                  {r}
                </option>
              ))}
            </select>
          </Campo>
          <Campo rotulo="CNAE principal" dica="7 dígitos, como no cartão CNPJ.">
            {entrada("cnae", { maxLength: 12, inputMode: "numeric" })}
          </Campo>
        </div>
      </section>

      <section className="rd-card">
        <h3 className="rd-config-secao">Endereço</h3>
        {aviso && <p className="rd-note">{aviso}</p>}
        <div className="rd-form-grid">
          <Campo rotulo="CEP" dica="Preenchemos o endereço pelo CEP.">
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
            <select id="empresa-uf" value={v.uf} onChange={set("uf")} disabled={!podeEditar}>
              <option value="">—</option>
              {UFS.map((u) => (
                <option key={u} value={u}>
                  {u}
                </option>
              ))}
            </select>
          </Campo>
        </div>
      </section>

      <section className="rd-card">
        <h3 className="rd-config-secao">Contato</h3>
        <div className="rd-form-grid">
          <Campo rotulo="E-mail">{entrada("email", { maxLength: 320, type: "email" })}</Campo>
          <Campo rotulo="Telefone">{entrada("telefone", { maxLength: 40 })}</Campo>
          <Campo rotulo="Celular / WhatsApp">{entrada("celular", { maxLength: 40 })}</Campo>
          <Campo rotulo="Site">{entrada("site", { maxLength: 200 })}</Campo>
        </div>
      </section>

      <section className="rd-card">
        <h3 className="rd-config-secao">Logo</h3>
        <div className="rd-config-logo">
          {temLogo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={`/api/radar/empresa/logo?v=${encodeURIComponent(versaoLogo)}`}
              alt="Logo da empresa"
            />
          ) : (
            <span className="rd-config-logo-vazio">Sem logo</span>
          )}
          {podeEditar && (
            <div>
              <input
                id="empresa-logo"
                type="file"
                aria-label={temLogo ? "Trocar logo" : "Enviar logo"}
                accept="image/png,image/jpeg,image/webp"
                onChange={(e) => {
                  trocarLogo(e.target.files?.[0]);
                  e.target.value = "";
                }}
              />
              {temLogo && (
                <button type="button" onClick={tirarLogo}>
                  Remover
                </button>
              )}
              <small className="rd-dica">
                PNG, JPG ou WEBP, até 1 MB. Vai nas etiquetas e documentos.
              </small>
            </div>
          )}
        </div>
      </section>

      <div className="rd-actions rd-produto-rodape">
        <button type="button" onClick={voltar}>
          Voltar
        </button>
        {botaoSalvar}
      </div>
    </div>
  );
}

type Edicao = { tipo: "novo" } | { tipo: "editar"; u: Row } | { tipo: "senha"; u: Row };

export function UsuariosSistema({
  usuarios,
  euId,
  executar,
  atualizar,
  avisar,
  voltar,
}: {
  usuarios: Row[];
  euId: string;
  avisar: (mensagem: string) => void;
  executar: Executar;
  atualizar: () => Promise<void>;
  voltar: () => void;
}) {
  const [edicao, setEdicao] = useState<Edicao | null>(null);
  const rotulo = (p: unknown) => CARGOS.find((c) => c[0] === p)?.[1] ?? str(p);

  return (
    <div className="rd-produto-form">
      <Voltar voltar={voltar} titulo="Usuários do sistema">
        <button type="button" className="primary" onClick={() => setEdicao({ tipo: "novo" })}>
          + Novo usuário
        </button>
      </Voltar>
      <section className="rd-card">
        <p className="rd-note">
          Cada pessoa entra com o próprio e-mail e senha. O cargo define o que ela vê e pode fazer.
          Quem sai da empresa é desativado, não apagado: o histórico do que fez continua.
        </p>
        <div className="rd-table-wrap">
          <table>
            <thead>
              <tr>
                <th>Nome</th>
                <th>E-mail</th>
                <th>Cargo</th>
                <th>Situação</th>
                <th>Último acesso</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map((u) => (
                <tr key={str(u.id)}>
                  <td>
                    {str(u.nome)}
                    {str(u.id) === euId && <small> (você)</small>}
                  </td>
                  <td>{str(u.email)}</td>
                  <td>{rotulo(u.papel)}</td>
                  <td>
                    {u.ativo === false ? <Badge>Inativo</Badge> : <Badge tone="green">Ativo</Badge>}
                  </td>
                  <td>{u.ultimo_acesso_em ? date(u.ultimo_acesso_em) : "nunca entrou"}</td>
                  <td className="rd-row-actions">
                    <button type="button" onClick={() => setEdicao({ tipo: "editar", u })}>
                      Editar
                    </button>
                    <button type="button" onClick={() => setEdicao({ tipo: "senha", u })}>
                      Trocar senha
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <section className="rd-card">
        <h3 className="rd-config-secao">O que cada cargo faz</h3>
        <ul className="rd-config-cargos">
          {CARGOS.map(([k, r, d]) => (
            <li key={k}>
              <strong>{r}</strong> {d}
            </li>
          ))}
        </ul>
      </section>
      {edicao && (
        <UsuarioModal
          edicao={edicao}
          euId={euId}
          executar={executar}
          fechar={() => setEdicao(null)}
          concluir={async (msg) => {
            setEdicao(null);
            await atualizar();
            avisar(msg);
          }}
        />
      )}
    </div>
  );
}

function UsuarioModal({
  edicao,
  euId,
  executar,
  fechar,
  concluir,
}: {
  edicao: Edicao;
  euId: string;
  executar: Executar;
  fechar: () => void;
  concluir: (mensagem: string) => Promise<void>;
}) {
  const u = edicao.tipo === "novo" ? null : edicao.u;
  const [nome, setNome] = useState(str(u?.nome));
  const [email, setEmail] = useState(str(u?.email));
  const [papel, setPapel] = useState(str(u?.papel) || "ATENDIMENTO");
  const [ativo, setAtivo] = useState(u?.ativo !== false);
  const [senha, setSenha] = useState("");
  const [confirmacao, setConfirmacao] = useState("");
  const [erro, setErro] = useState("");
  const [ocupado, setOcupado] = useState(false);
  const titulo = {
    novo: "Novo usuário",
    editar: `Editar ${str(u?.nome)}`,
    senha: `Trocar a senha de ${str(u?.nome)}`,
  }[edicao.tipo];
  const souEu = !!u && str(u.id) === euId;

  async function confirmar() {
    setErro("");
    setOcupado(true);
    try {
      if (edicao.tipo === "novo") {
        const r = await enviar("/usuarios", { nome, email, papel, senha, confirmacao });
        await concluir(str(r.mensagem));
      } else if (edicao.tipo === "senha") {
        const r = await enviar(`/usuarios/${str(u?.id)}/senha`, { senha, confirmacao });
        await concluir(str(r.mensagem));
      } else {
        const r = await executar({ op: "usuario_salvar", id: u?.id, nome, papel, ativo });
        if (r) await concluir(str(r.mensagem));
      }
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setOcupado(false);
    }
  }

  const camposSenha = (
    <>
      <Campo
        rotulo={edicao.tipo === "novo" ? "Senha inicial" : "Nova senha"}
        obrigatorio
        dica="Pelo menos 8 caracteres."
      >
        <input
          type="password"
          autoComplete="new-password"
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
        />
      </Campo>
      <Campo rotulo="Confirme a senha" obrigatorio>
        <input
          type="password"
          autoComplete="new-password"
          value={confirmacao}
          onChange={(e) => setConfirmacao(e.target.value)}
        />
      </Campo>
    </>
  );

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
        {erro && (
          <div className="rd-error" role="alert">
            {erro}
          </div>
        )}
        <div className="rd-form-grid">
          {edicao.tipo !== "senha" && (
            <>
              <Campo rotulo="Nome" obrigatorio largo>
                <input value={nome} maxLength={200} onChange={(e) => setNome(e.target.value)} />
              </Campo>
              <Campo
                rotulo="E-mail de acesso"
                obrigatorio
                largo
                dica={
                  edicao.tipo === "novo"
                    ? "É o login. Depois não muda."
                    : "O e-mail é o login e não muda."
                }
              >
                <input
                  type="email"
                  value={email}
                  maxLength={320}
                  disabled={edicao.tipo !== "novo"}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </Campo>
              <Campo rotulo="Cargo" largo dica={CARGOS.find((c) => c[0] === papel)?.[2]}>
                <select value={papel} disabled={souEu} onChange={(e) => setPapel(e.target.value)}>
                  {CARGOS.map(([k, r]) => (
                    <option key={k} value={k}>
                      {r}
                    </option>
                  ))}
                </select>
              </Campo>
            </>
          )}
          {edicao.tipo === "editar" && (
            <label className="rd-check wide">
              <input
                type="checkbox"
                checked={ativo}
                disabled={souEu}
                onChange={(e) => setAtivo(e.target.checked)}
              />
              Acesso ativo (desmarque para desligar o acesso de quem saiu)
            </label>
          )}
          {edicao.tipo !== "editar" && camposSenha}
        </div>
        {edicao.tipo === "novo" && (
          <p className="rd-note">
            Passe o e-mail e a senha inicial para a pessoa por um canal seguro. Ela pode trocar a
            senha depois.
          </p>
        )}
        <div className="rd-modal-foot">
          <button onClick={fechar}>Cancelar</button>
          <button className="primary" disabled={ocupado} onClick={confirmar}>
            {ocupado ? "Salvando…" : edicao.tipo === "novo" ? "Criar usuário" : "Salvar"}
          </button>
        </div>
      </section>
    </div>
  );
}
