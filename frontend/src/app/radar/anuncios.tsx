"use client";

// Anúncios: painel com as lojas e, por loja, a lista de anúncios com abas,
// filtros e ações em lote. Todo anúncio é vinculado a um produto; produto
// incompleto (criado por importação) ou anúncio com problema aparece em
// "Necessitam atenção". A conexão das lojas fica em Integrações; importar e
// impulsionar só aparecem quando houver loja conectada.

import { useState, type ReactNode } from "react";
import { Badge, Empty, Table, money, str, type ModalSpec, type Row } from "./ui";

type Props = {
  anuncios: Row[];
  produtos: Row[];
  podeEditar: boolean;
  busy: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  abrirModal: (m: ModalSpec) => void;
  abrirProduto: (id: string) => void;
};

const CANAIS = ["Mercado Livre", "Shopee", "TikTok Shop", "SHEIN"];

// Central do vendedor de cada marketplace. A lojista entra com o próprio login.
const CENTRAL: Record<string, string> = {
  "Mercado Livre": "https://www.mercadolivre.com.br/anuncios",
  Shopee: "https://seller.shopee.com.br",
  "TikTok Shop": "https://seller-br.tiktok.com",
  SHEIN: "https://sellerhub.shein.com",
};
const SIGLA: Record<string, string> = {
  "Mercado Livre": "ML",
  Shopee: "SP",
  "TikTok Shop": "TT",
  SHEIN: "SH",
};
const NO_MARKETPLACE: Record<string, [string, string]> = {
  NAO_PUBLICADO: ["Não publicado", "gray"],
  ATIVO: ["Ativo", "green"],
  PAUSADO: ["Pausado", "amber"],
  REJEITADO: ["Rejeitado", "red"],
  ENCERRADO: ["Encerrado", "gray"],
};
const NO_RADAR: Record<string, string> = {
  RASCUNHO: "Rascunho",
  SIMULADO: "Simulado",
  PAUSADO: "Pausado",
};
const ABAS: [string, string][] = [
  ["TODOS", "Todos"],
  ["ATIVO", "Ativos"],
  ["REJEITADO", "Rejeitados"],
  ["NAO_PUBLICADO", "Não publicados"],
  ["ATENCAO", "Necessitam atenção"],
];

type Filtros = { produto: string; situacao: string; ecommerce: string };
const SEM_FILTRO: Filtros = { produto: "", situacao: "", ecommerce: "" };

/** Motivos para o anúncio pedir atenção. Vazio = tudo certo. */
function alertas(a: Row, p: Row | undefined): string[] {
  const out: string[] = [];
  if (!p) out.push("Sem produto vinculado");
  else if (p.incompleto === true) out.push("Produto com cadastro incompleto");
  if (a.situacao_ecommerce === "REJEITADO")
    out.push(`Rejeitado pelo marketplace${a.motivo_rejeicao ? `: ${str(a.motivo_rejeicao)}` : ""}`);
  if (a.erro_integracao) out.push(`Erro de integração: ${str(a.erro_integracao)}`);
  return out;
}

export default function Anuncios(props: Props) {
  const [loja, setLoja] = useState<string | null>(null);
  const [novo, setNovo] = useState<string | null>(null);
  const produtoDe = (id: unknown) => props.produtos.find((p) => p.id === id);
  const atencao = (a: Row) => alertas(a, produtoDe(a.produto_id)).length > 0;

  const formNovo = novo !== null && (
    <NovoAnuncio {...props} canal={novo} fechar={() => setNovo(null)} />
  );

  if (loja)
    return (
      <>
        <ListaDaLoja
          {...props}
          canal={loja}
          voltar={() => setLoja(null)}
          novoAnuncio={() => setNovo(loja)}
          produtoDe={produtoDe}
        />
        {formNovo}
      </>
    );

  const lojas = CANAIS.filter((c) => props.anuncios.some((a) => a.canal === c));
  return (
    <>
      <div className="rd-toolbar">
        <span className="rd-note">
          A conexão das lojas fica em Integrações. Aqui você gerencia os anúncios de cada uma.
        </span>
        {props.podeEditar && (
          <button className="primary" onClick={() => setNovo("")}>
            + Novo anúncio
          </button>
        )}
      </div>
      {lojas.length === 0 ? (
        <section className="rd-card">
          <Empty text="Nenhuma loja com anúncios ainda." />
          <p className="rd-note">
            Quando uma loja for conectada em Integrações, os anúncios dela aparecem aqui. Você
            também pode preparar anúncios no Radar com &quot;+ Novo anúncio&quot;.
          </p>
        </section>
      ) : (
        <div className="rd-lojas">
          {lojas.map((c) => {
            const daLoja = props.anuncios.filter((a) => a.canal === c);
            const ativos = daLoja.filter((a) => a.situacao_ecommerce === "ATIVO").length;
            const problemas = daLoja.filter(atencao).length;
            return (
              <article key={c} className="rd-loja">
                <header>
                  <span className="rd-loja-sigla" aria-hidden="true">
                    {SIGLA[c]}
                  </span>
                  <h3>{c}</h3>
                  <Badge>Loja não conectada</Badge>
                </header>
                <strong>{ativos}</strong>
                <small>anúncios ativos no marketplace</small>
                <small>{daLoja.length} anúncio(s) no Radar</small>
                {problemas > 0 && (
                  <span className="rd-loja-alerta">⚠ {problemas} precisam de atenção</span>
                )}
                <footer>
                  <a href={CENTRAL[c]} target="_blank" rel="noopener noreferrer">
                    Abrir central do vendedor ↗
                  </a>
                  <button onClick={() => setLoja(c)}>Gerenciar</button>
                </footer>
              </article>
            );
          })}
        </div>
      )}
      {formNovo}
    </>
  );
}

function ListaDaLoja({
  canal,
  anuncios,
  podeEditar,
  busy,
  executar,
  abrirModal,
  abrirProduto,
  produtos,
  voltar,
  novoAnuncio,
  produtoDe,
}: Props & {
  canal: string;
  voltar: () => void;
  novoAnuncio: () => void;
  produtoDe: (id: unknown) => Row | undefined;
}) {
  const [aba, setAba] = useState("TODOS");
  const [busca, setBusca] = useState("");
  const [filtros, setFiltros] = useState<Filtros>(SEM_FILTRO);
  const [rascunho, setRascunho] = useState<Filtros | null>(null);
  const [menu, setMenu] = useState(false);
  const [marcados, setMarcados] = useState<string[]>([]);
  const [acao, setAcao] = useState<"relacionar" | "precos" | null>(null);

  const daLoja = anuncios.filter((a) => a.canal === canal);
  const naAba = (a: Row, chave: string) =>
    chave === "TODOS" ||
    (chave === "ATENCAO"
      ? alertas(a, produtoDe(a.produto_id)).length > 0
      : a.situacao_ecommerce === chave);
  const termo = busca.trim().toLowerCase();
  const prodTermo = filtros.produto.trim().toLowerCase();
  const linhas = daLoja.filter((a) => {
    const p = produtoDe(a.produto_id);
    return (
      naAba(a, aba) &&
      (!termo ||
        [a.id_externo, a.titulo, p?.nome].some((x) => str(x).toLowerCase().includes(termo))) &&
      (!prodTermo ||
        [p?.nome, p?.sku, p?.gtin].some((x) => str(x).toLowerCase().includes(prodTermo))) &&
      (!filtros.situacao || a.estado === filtros.situacao) &&
      (!filtros.ecommerce || a.situacao_ecommerce === filtros.ecommerce)
    );
  });
  const problemas = daLoja.filter((a) => naAba(a, "ATENCAO")).length;
  const selecionados = daLoja.filter((a) => marcados.includes(str(a.id)));
  const todosMarcados = linhas.length > 0 && linhas.every((a) => marcados.includes(str(a.id)));
  const filtrosAtivos = Object.values(filtros).filter(Boolean).length;

  function escolherAcao(qual: "relacionar" | "precos") {
    setMenu(false);
    if (!marcados.length) {
      alert("Marque na lista os anúncios que quer alterar.");
      return;
    }
    setAcao(qual);
  }

  return (
    <>
      <div className="rd-toolbar rd-anuncios-topo">
        <button onClick={voltar}>← Todas as lojas</button>
        <h2>
          <span className="rd-loja-sigla" aria-hidden="true">
            {SIGLA[canal]}
          </span>
          {canal}
        </h2>
        <a href={CENTRAL[canal]} target="_blank" rel="noopener noreferrer">
          Abrir central do vendedor ↗
        </a>
        {podeEditar && (
          <div className="rd-acoes-loja">
            <button className="primary" onClick={novoAnuncio}>
              + Novo anúncio
            </button>
            <div className="rd-mais-acoes">
              <button aria-expanded={menu} onClick={() => setMenu(!menu)}>
                Mais ações ⋯
              </button>
              {menu && (
                <ul role="menu">
                  <li>
                    <button role="menuitem" onClick={() => escolherAcao("relacionar")}>
                      🔗 Relacionar anúncios
                    </button>
                  </li>
                  <li>
                    <button role="menuitem" onClick={() => escolherAcao("precos")}>
                      $ Gerenciar preços dos anúncios
                    </button>
                  </li>
                  <li>
                    <button
                      role="menuitem"
                      onClick={() => {
                        setMenu(false);
                        setAba("ATENCAO");
                      }}
                    >
                      ⚠ Verificar anúncios com problemas
                    </button>
                  </li>
                </ul>
              )}
            </div>
          </div>
        )}
      </div>

      {problemas > 0 && (
        <div className="rd-alerta-anuncios" role="alert">
          <strong>⚠ {problemas} anúncio(s) precisam de atenção.</strong> Produto incompleto ou
          anúncio com problema atrapalha o controle de estoque e o financeiro.
          <button onClick={() => setAba("ATENCAO")}>Ver quais são</button>
        </div>
      )}

      <section className="rd-card">
        <div className="rd-tabs" role="tablist" aria-label="Situação dos anúncios">
          {ABAS.map(([chave, rotulo]) => (
            <button
              key={chave}
              role="tab"
              aria-selected={aba === chave}
              className={aba === chave ? "active" : ""}
              onClick={() => setAba(chave)}
            >
              {rotulo}
              <span className="rd-tab-num">{daLoja.filter((a) => naAba(a, chave)).length}</span>
            </button>
          ))}
        </div>
        <div className="rd-card-head rd-busca-anuncios">
          <input
            aria-label="Buscar anúncio"
            className="rd-search"
            placeholder="Pesquise por identificador, título ou nome do produto"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <div className="rd-filtros-wrap">
            <button
              aria-expanded={rascunho !== null}
              onClick={() => setRascunho(rascunho ? null : filtros)}
            >
              ⚲ Filtros{filtrosAtivos > 0 && ` (${filtrosAtivos})`}
            </button>
            {rascunho && (
              <div className="rd-filtros-painel">
                <label>
                  Produto no Radar
                  <input
                    placeholder="Descrição, SKU ou GTIN"
                    value={rascunho.produto}
                    onChange={(e) => setRascunho({ ...rascunho, produto: e.target.value })}
                  />
                </label>
                <label>
                  Situação no Radar
                  <select
                    value={rascunho.situacao}
                    onChange={(e) => setRascunho({ ...rascunho, situacao: e.target.value })}
                  >
                    <option value="">Todas</option>
                    {Object.entries(NO_RADAR).map(([v, r]) => (
                      <option key={v} value={v}>
                        {r}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Situação no marketplace
                  <select
                    value={rascunho.ecommerce}
                    onChange={(e) => setRascunho({ ...rascunho, ecommerce: e.target.value })}
                  >
                    <option value="">Todas</option>
                    {Object.entries(NO_MARKETPLACE).map(([v, [r]]) => (
                      <option key={v} value={v}>
                        {r}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="rd-actions">
                  <button
                    className="primary"
                    onClick={() => {
                      setFiltros(rascunho);
                      setRascunho(null);
                    }}
                  >
                    Aplicar
                  </button>
                  <button
                    onClick={() => {
                      setFiltros(SEM_FILTRO);
                      setRascunho(null);
                    }}
                  >
                    Limpar
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
        {daLoja.length === 0 ? (
          <Empty text="Nenhum anúncio nesta loja." />
        ) : (
          <Table
            headers={[
              podeEditar ? "✓" : "",
              "Anúncio",
              "Produto vinculado",
              "Preço",
              "No Radar",
              "No marketplace",
              "",
            ]}
            rows={linhas.map((a) => {
              const p = produtoDe(a.produto_id);
              const motivos = alertas(a, p);
              const [rotulo, tom] = NO_MARKETPLACE[str(a.situacao_ecommerce)] ?? ["—", "gray"];
              return [
                podeEditar ? (
                  <input
                    key="m"
                    type="checkbox"
                    aria-label={`Selecionar ${str(a.titulo)}`}
                    checked={marcados.includes(str(a.id))}
                    onChange={(e) =>
                      setMarcados((m) =>
                        e.target.checked ? [...m, str(a.id)] : m.filter((x) => x !== str(a.id)),
                      )
                    }
                  />
                ) : (
                  ""
                ),
                <div key="t">
                  <strong>{str(a.titulo)}</strong>
                  <br />
                  <small>
                    {a.id_externo
                      ? `Código no marketplace: ${str(a.id_externo)}`
                      : "Criado no Radar"}
                  </small>
                  {motivos.map((m) => (
                    <small key={m} className="rd-motivo">
                      ⚠ {m}
                    </small>
                  ))}
                </div>,
                p ? (
                  <div key="p">
                    {str(p.sku)} · {str(p.nome)}
                    {p.incompleto === true && (
                      <div>
                        <Badge tone="amber">Cadastro incompleto</Badge>{" "}
                        <button onClick={() => abrirProduto(str(p.id))}>Completar produto</button>
                      </div>
                    )}
                  </div>
                ) : (
                  <Badge key="p" tone="red">
                    Sem produto
                  </Badge>
                ),
                money(a.preco),
                NO_RADAR[str(a.estado)] ?? str(a.estado),
                <Badge key="e" tone={tom}>
                  {rotulo}
                </Badge>,
                podeEditar ? (
                  <div key="a" className="rd-row-actions">
                    <button
                      disabled={busy}
                      onClick={() =>
                        executar({
                          op: "anuncio_estado",
                          id: a.id,
                          estado: a.estado === "SIMULADO" ? "PAUSADO" : "SIMULADO",
                        })
                      }
                    >
                      {a.estado === "SIMULADO" ? "Pausar local" : "Simular publicação"}
                    </button>
                    <button
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
                            { key: "motivo", label: "Motivo da alteração" },
                          ],
                        })
                      }
                    >
                      Preço
                    </button>
                  </div>
                ) : (
                  ""
                ),
              ];
            })}
          />
        )}
        {podeEditar && linhas.length > 0 && (
          <div className="rd-selecao">
            <label className="rd-check">
              <input
                type="checkbox"
                checked={todosMarcados}
                onChange={(e) => setMarcados(e.target.checked ? linhas.map((a) => str(a.id)) : [])}
              />
              Marcar todos da lista ({linhas.length})
            </label>
            {marcados.length > 0 && <span>{marcados.length} marcado(s)</span>}
          </div>
        )}
        <p className="rd-note">
          Sem loja conectada, nada é enviado ao marketplace: publicação, pausa e preços valem só no
          Radar. Preço novo passa por aprovação na Central de ações.
        </p>
      </section>

      {acao === "relacionar" && (
        <Janela titulo="Relacionar anúncios a um produto" fechar={() => setAcao(null)}>
          <Relacionar
            selecionados={selecionados}
            produtos={produtos}
            busy={busy}
            enviar={async (produto) => {
              const ok = await executar({
                op: "anuncio_relacionar",
                produto_id: produto,
                ids: selecionados.map((a) => a.id),
              });
              if (ok) {
                setAcao(null);
                setMarcados([]);
              }
            }}
          />
        </Janela>
      )}
      {acao === "precos" && (
        <Janela titulo="Gerenciar preços dos anúncios" fechar={() => setAcao(null)}>
          <Precos
            selecionados={selecionados}
            busy={busy}
            enviar={async (itens, motivo) => {
              const ok = await executar({ op: "anuncios_precos", itens, motivo });
              if (ok) {
                setAcao(null);
                setMarcados([]);
              }
            }}
          />
        </Janela>
      )}
    </>
  );
}

function Janela({
  titulo,
  fechar,
  children,
}: {
  titulo: string;
  fechar: () => void;
  children: ReactNode;
}) {
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
        {children}
      </section>
    </div>
  );
}

/** Busca de produto por nome, SKU ou GTIN. Produto pai com variações não entra. */
function BuscaProduto({
  produtos,
  escolhido,
  escolher,
}: {
  produtos: Row[];
  escolhido: string;
  escolher: (id: string) => void;
}) {
  const [texto, setTexto] = useState("");
  const atual = produtos.find((p) => p.id === escolhido);
  const termo = texto.trim().toLowerCase();
  const achados = termo
    ? produtos
        .filter(
          (p) =>
            p.tipo !== "VARIACAO" &&
            [p.nome, p.sku, p.gtin].some((x) => str(x).toLowerCase().includes(termo)),
        )
        .slice(0, 8)
    : [];
  return (
    <div className="rd-busca-produto">
      {atual ? (
        <div className="rd-produto-escolhido">
          <strong>
            {str(atual.sku)} · {str(atual.nome)}
          </strong>
          <button type="button" onClick={() => escolher("")}>
            Trocar
          </button>
        </div>
      ) : (
        <>
          <input
            id="anuncio-produto-busca"
            autoFocus
            placeholder="Digite o nome, SKU ou GTIN do produto"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
          />
          {termo && (
            <ul>
              {achados.length === 0 && (
                <li className="rd-dica">
                  Nenhum produto encontrado. Cadastre o produto primeiro em Produtos.
                </li>
              )}
              {achados.map((p) => (
                <li key={str(p.id)}>
                  <button type="button" onClick={() => escolher(str(p.id))}>
                    {str(p.sku)} · {str(p.nome)}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  );
}

function NovoAnuncio({
  canal,
  produtos,
  busy,
  executar,
  fechar,
}: Props & { canal: string; fechar: () => void }) {
  const [produto, setProduto] = useState("");
  const [destino, setDestino] = useState(canal || CANAIS[0]);
  const [titulo, setTitulo] = useState("");
  const [preco, setPreco] = useState("");
  const [erro, setErro] = useState("");

  function escolher(id: string) {
    setErro("");
    setProduto(id);
    const p = produtos.find((x) => x.id === id);
    if (p) {
      if (!titulo) setTitulo(str(p.nome));
      if (!preco) setPreco(str(p.preco));
    }
  }

  return (
    <Janela titulo="Novo anúncio" fechar={fechar}>
      <form
        noValidate
        onSubmit={async (e) => {
          e.preventDefault();
          if (!produto) {
            setErro("Escolha primeiro o produto do anúncio. Todo anúncio precisa de um produto.");
            return;
          }
          if (await executar({ op: "anuncio", produto_id: produto, canal: destino, titulo, preco }))
            fechar();
        }}
      >
        <div className="rd-form-grid">
          <div className="wide rd-campo-produto">
            <span>1. Produto do anúncio *</span>

            <BuscaProduto produtos={produtos} escolhido={produto} escolher={escolher} />
            <small className="rd-dica">
              Obrigatório: é o produto que dá baixa no estoque e entra no financeiro.
            </small>
          </div>
          <label>
            2. Loja
            <select value={destino} onChange={(e) => setDestino(e.target.value)}>
              {CANAIS.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </label>
          <label>
            3. Preço
            <input
              type="number"
              step="0.01"
              min="0.01"
              required
              value={preco}
              onChange={(e) => setPreco(e.target.value)}
            />
          </label>
          <label className="wide">
            4. Título do anúncio
            <input
              required
              maxLength={250}
              value={titulo}
              onChange={(e) => setTitulo(e.target.value)}
            />
          </label>
        </div>
        {erro && (
          <div className="rd-error" role="alert">
            {erro}
          </div>
        )}
        <div className="rd-modal-foot">
          <button type="button" onClick={fechar}>
            Cancelar
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Salvando…" : "Salvar anúncio"}
          </button>
        </div>
      </form>
    </Janela>
  );
}

function Relacionar({
  selecionados,
  produtos,
  busy,
  enviar,
}: {
  selecionados: Row[];
  produtos: Row[];
  busy: boolean;
  enviar: (produto: string) => Promise<void>;
}) {
  const [produto, setProduto] = useState("");
  return (
    <>
      <p className="rd-note">
        {selecionados.length} anúncio(s) marcado(s) passam a apontar para o produto escolhido.
      </p>
      <BuscaProduto produtos={produtos} escolhido={produto} escolher={setProduto} />
      <div className="rd-modal-foot">
        <button className="primary" disabled={busy || !produto} onClick={() => enviar(produto)}>
          Vincular ao produto
        </button>
      </div>
    </>
  );
}

function Precos({
  selecionados,
  busy,
  enviar,
}: {
  selecionados: Row[];
  busy: boolean;
  enviar: (itens: { id: unknown; preco: string }[], motivo: string) => Promise<void>;
}) {
  const [precos, setPrecos] = useState<Record<string, string>>(() =>
    Object.fromEntries(selecionados.map((a) => [str(a.id), str(a.preco)])),
  );
  const [motivo, setMotivo] = useState("");
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        enviar(
          selecionados.map((a) => ({ id: a.id, preco: precos[str(a.id)] })),
          motivo,
        );
      }}
    >
      <Table
        headers={["Anúncio", "Preço atual", "Novo preço"]}
        rows={selecionados.map((a) => [
          str(a.titulo),
          money(a.preco),
          <input
            key="p"
            aria-label={`Novo preço de ${str(a.titulo)}`}
            type="number"
            step="0.01"
            min="0.01"
            required
            value={precos[str(a.id)] ?? ""}
            onChange={(e) => setPrecos({ ...precos, [str(a.id)]: e.target.value })}
          />,
        ])}
      />
      <label className="rd-motivo-preco">
        Motivo da alteração
        <input
          required
          maxLength={500}
          value={motivo}
          onChange={(e) => setMotivo(e.target.value)}
        />
      </label>
      <p className="rd-note">Os novos preços vão para aprovação na Central de ações.</p>
      <div className="rd-modal-foot">
        <button className="primary" disabled={busy}>
          Enviar para aprovação
        </button>
      </div>
    </form>
  );
}
