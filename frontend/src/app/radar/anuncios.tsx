"use client";

// Anúncios: painel com as lojas e, por loja, a lista de anúncios com abas,
// filtros e ações em lote. Todo anúncio é vinculado a um produto; produto
// incompleto (criado por importação) ou anúncio com problema aparece em
// "Necessitam atenção". A conexão das lojas fica em Integrações; importar e
// impulsionar só aparecem quando houver loja conectada.

import { useState, type ReactNode } from "react";
import { Badge, Empty, Table, money, str, type ModalSpec, type Row } from "./ui";
import { vinculoDe } from "./categorias";
import { CANAIS, CENTRAL, SIGLA, canaisCom } from "./canais";
import FiltrosAnuncios, {
  ANUNCIOS_VAZIO,
  filtrarAnuncios,
  type FiltroAnuncios,
} from "./anuncios-filtros";

type Props = {
  anuncios: Row[];
  produtos: Row[];
  podeEditar: boolean;
  busy: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  abrirModal: (m: ModalSpec) => void;
  abrirProduto: (id: string) => void;
  categorias: Row[];
  categoriaCanais: Row[];
  imagens: Row[];
  /** Lojas da empresa: o anúncio mostra em qual delas vai subir. */
  lojas: Row[];
};

const NO_MARKETPLACE: Record<string, [string, string]> = {
  NAO_PUBLICADO: ["Não publicado", "gray"],
  ATIVO: ["Ativo", "green"],
  PAUSADO: ["Pausado", "amber"],
  REJEITADO: ["Rejeitado", "red"],
  ENCERRADO: ["Encerrado", "gray"],
};
export const NO_RADAR: Record<string, string> = {
  RASCUNHO: "Rascunho",
  PRONTO: "Pronto para publicar",
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

const SEM_LOJA =
  "Enviar ao marketplace precisa de uma loja conectada. As conexões entram depois do CNPJ, em Integrações.";

type AcaoLote = "relacionar" | "precos" | "excluir" | "criar";

/**
 * Abre a central do vendedor do marketplace e copia o código do anúncio, para
 * colar na busca de lá. Sem loja conectada o Radar não sabe o endereço exato da
 * página de cada anúncio. Devolve o aviso para mostrar.
 */
export function verNaCentral(a: Row): string {
  const codigo = str(a.id_externo);
  const central = CENTRAL[str(a.canal)];
  if (!central) return "O endereço da central do vendedor deste marketplace entra com a conexão.";
  window.open(central, "_blank", "noopener,noreferrer");
  if (!codigo) return "Este anúncio foi criado no Radar e ainda não tem código no marketplace.";
  navigator.clipboard?.writeText(codigo).catch(() => {});
  return `Código ${codigo} copiado: cole na busca da central do vendedor.`;
}

/** Motivos para o anúncio pedir atenção. Vazio = tudo certo. */
function alertas(a: Row, p: Row | undefined, categoriaCanais: Row[], imagens: Row[]): string[] {
  const out: string[] = [];
  if (!p) out.push("Sem produto vinculado");
  else {
    if (p.incompleto === true) out.push("Produto com cadastro incompleto");
    // Variação usa a imagem dela ou a do produto principal.
    if (!imagens.some((i) => i.produto_id === p.id || (p.pai_id && i.produto_id === p.pai_id)))
      out.push("Produto sem imagem");
    if (!p.categoria_id) out.push("Produto sem categoria");
    else if (!vinculoDe(categoriaCanais, p.categoria_id, str(a.canal)))
      out.push(`Categoria sem vínculo com o ${str(a.canal)}`);
  }
  if (a.situacao_ecommerce === "REJEITADO")
    out.push(`Rejeitado pelo marketplace${a.motivo_rejeicao ? `: ${str(a.motivo_rejeicao)}` : ""}`);
  if (a.erro_integracao) out.push(`Erro de integração: ${str(a.erro_integracao)}`);
  return out;
}

export default function Anuncios(props: Props) {
  const [loja, setLoja] = useState<string | null>(null);
  const [novo, setNovo] = useState<string | null>(null);
  const produtoDe = (id: unknown) => props.produtos.find((p) => p.id === id);
  const atencao = (a: Row) =>
    alertas(a, produtoDe(a.produto_id), props.categoriaCanais, props.imagens).length > 0;

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

  const lojas = canaisCom(props.anuncios).filter((c) => props.anuncios.some((a) => a.canal === c));
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
                  {CENTRAL[c] && (
                    <a href={CENTRAL[c]} target="_blank" rel="noopener noreferrer">
                      Abrir central do vendedor ↗
                    </a>
                  )}
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
  categorias,
  categoriaCanais,
  imagens,
  lojas,
}: Props & {
  canal: string;
  voltar: () => void;
  novoAnuncio: () => void;
  produtoDe: (id: unknown) => Row | undefined;
}) {
  const [aba, setAba] = useState("TODOS");
  const [filtro, setFiltro] = useState<FiltroAnuncios>(ANUNCIOS_VAZIO);
  const [marcados, setMarcados] = useState<string[]>([]);
  const [acao, setAcao] = useState<AcaoLote | null>(null);
  // Anúncios que a ação vale: os marcados (barra) ou um só (⋯ da linha).
  const [alvo, setAlvo] = useState<string[]>([]);
  const [menuLote, setMenuLote] = useState(false);
  const [menuLinha, setMenuLinha] = useState<{
    id: string;
    x: number;
    y: number;
  } | null>(null);
  const [aviso, setAviso] = useState("");
  const [avisoOk, setAvisoOk] = useState("");

  const daLoja = anuncios.filter((a) => a.canal === canal);
  const naAba = (a: Row, chave: string) =>
    chave === "TODOS" ||
    (chave === "ATENCAO"
      ? alertas(a, produtoDe(a.produto_id), categoriaCanais, imagens).length > 0
      : a.situacao_ecommerce === chave);
  const contextoFiltro = {
    produtoDe,
    motivos: (a: Row) => alertas(a, produtoDe(a.produto_id), categoriaCanais, imagens),
    categorias,
  };
  const daAba = daLoja.filter((a) => naAba(a, aba));
  const linhas = filtrarAnuncios(daAba, filtro, contextoFiltro);
  const problemas = daLoja.filter((a) => naAba(a, "ATENCAO")).length;
  const selecionados = daLoja.filter((a) => alvo.includes(str(a.id)));
  const todosMarcados = linhas.length > 0 && linhas.every((a) => marcados.includes(str(a.id)));

  function escolherAcao(qual: AcaoLote, ids: string[] = marcados) {
    setMenuLote(false);
    setMenuLinha(null);
    if (!ids.length) {
      alert("Marque na lista os anúncios que quer alterar.");
      return;
    }
    setAviso("");
    setAlvo(ids);
    setAcao(qual);
  }

  function semLoja() {
    setMenuLote(false);
    setMenuLinha(null);
    setAviso(SEM_LOJA);
  }

  // Depois de uma ação, tira da seleção só os anúncios em que ela valeu.
  function concluir() {
    setAcao(null);
    setMarcados((m) => m.filter((id) => !alvo.includes(id)));
  }

  const item = (rotulo: string, onClick: () => void) => (
    <li>
      <button role="menuitem" onClick={onClick}>
        {rotulo}
      </button>
    </li>
  );
  const emBreve = (rotulo: string, motivo: string, sufixo = "em breve") => (
    <li>
      <button role="menuitem" disabled title={motivo}>
        {rotulo} <small>({sufixo})</small>
      </button>
    </li>
  );
  const anuncioLinha = menuLinha && daLoja.find((a) => str(a.id) === menuLinha.id);

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
        {CENTRAL[canal] && (
          <a href={CENTRAL[canal]} target="_blank" rel="noopener noreferrer">
            Abrir central do vendedor ↗
          </a>
        )}
        {podeEditar && (
          <div className="rd-acoes-loja">
            <button className="primary" onClick={novoAnuncio}>
              + Novo anúncio
            </button>
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
        <FiltrosAnuncios
          filtro={filtro}
          setFiltro={setFiltro}
          contexto={contextoFiltro}
          produtos={produtos}
          base={daAba}
          total={linhas.length}
        />
        {daLoja.length === 0 ? (
          <Empty text="Nenhum anúncio nesta loja." />
        ) : (
          <Table
            headers={[
              podeEditar ? (
                <input
                  key="todos"
                  type="checkbox"
                  aria-label="Marcar todos da lista"
                  title="Marcar todos da lista"
                  checked={todosMarcados}
                  onChange={(e) =>
                    setMarcados(e.target.checked ? linhas.map((a) => str(a.id)) : [])
                  }
                />
              ) : (
                ""
              ),
              "Anúncio",
              "Produto vinculado",
              "Preço",
              "No Radar",
              "No marketplace",
              "",
            ]}
            rows={linhas.map((a) => {
              const p = produtoDe(a.produto_id);
              const motivos = alertas(a, p, categoriaCanais, imagens);
              const [rotulo, tom] = NO_MARKETPLACE[str(a.situacao_ecommerce)] ?? ["—", "gray"];
              return [
                podeEditar ? (
                  <span key="m" className="rd-celula-lote">
                    <input
                      type="checkbox"
                      aria-label={`Selecionar ${str(a.titulo)}`}
                      checked={marcados.includes(str(a.id))}
                      onChange={(e) =>
                        setMarcados((m) =>
                          e.target.checked ? [...m, str(a.id)] : m.filter((x) => x !== str(a.id)),
                        )
                      }
                    />
                    <button
                      type="button"
                      className="rd-linha-mais"
                      aria-label={`Ações de ${str(a.titulo)}`}
                      aria-expanded={menuLinha?.id === str(a.id)}
                      onClick={(e) => {
                        const id = str(a.id);
                        const ret = e.currentTarget.getBoundingClientRect();
                        // Perto do rodapé o menu abre para cima, para não sair da tela.
                        const altura = Math.min(380, window.innerHeight * 0.6);
                        const y =
                          ret.bottom + altura > window.innerHeight
                            ? Math.max(8, ret.top - altura)
                            : ret.bottom + 4;
                        setMenuLote(false);
                        setMenuLinha(menuLinha?.id === id ? null : { id, x: ret.left, y });
                      }}
                    >
                      ⋯
                    </button>
                  </span>
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
                    {a.loja_id && ` · ${str(lojas.find((l) => l.id === a.loja_id)?.nome)}`}
                  </small>
                  {motivos.map((m) => (
                    <small key={m} className="rd-motivo">
                      ⚠ {m}
                    </small>
                  ))}
                </div>,
                p ? (
                  <div key="p">
                    <button
                      type="button"
                      className="rd-link-produto"
                      title="Abrir a página do produto"
                      onClick={() => abrirProduto(str(p.id))}
                    >
                      {str(p.sku)} · {str(p.nome)}
                    </button>
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
        {aviso && (
          <div className="rd-error" role="alert">
            {aviso}
          </div>
        )}
        {avisoOk && (
          <p className="rd-ok" role="status">
            {avisoOk}
          </p>
        )}
        {podeEditar && marcados.length > 0 && (
          <div className="rd-barra-lote" role="region" aria-label="Ações para os anúncios marcados">
            <span
              className="rd-barra-qtd rd-pilula"
              title={`${marcados.length} de ${linhas.length} anúncios`}
            >
              <span aria-hidden="true">↥</span>
              {String(marcados.length).padStart(2, "0")}
              <button
                type="button"
                aria-label="Limpar seleção"
                title="Limpar seleção"
                onClick={() => setMarcados([])}
              >
                ✕
              </button>
            </span>
            <button type="button" className="primary" onClick={semLoja}>
              ⇪ Enviar para o e-commerce
            </button>
            <button type="button" onClick={() => escolherAcao("precos")}>
              $ Enviar preços para o e-commerce
            </button>
            <button type="button" onClick={semLoja}>
              ▣ Enviar estoque para o e-commerce
            </button>
            <div className="rd-mais-acoes">
              <button
                type="button"
                aria-expanded={menuLote}
                onClick={() => {
                  setMenuLinha(null);
                  setMenuLote(!menuLote);
                }}
              >
                Mais ações{" "}
                <span className="rd-circulo" aria-hidden="true">
                  ⋯
                </span>
              </button>
              {menuLote && (
                <ul role="menu" className="rd-menu-cima rd-menu-longo">
                  {item("⇪ Enviar para o e-commerce", semLoja)}
                  {item("$ Enviar preços para o e-commerce", () => escolherAcao("precos"))}
                  {item("▣ Enviar estoque para o e-commerce", semLoja)}
                  {item("🔗 Relacionar anúncios", () => escolherAcao("relacionar"))}
                  {emBreve("⇩ Importar anúncios", SEM_LOJA)}
                  {item("🗑 Excluir anúncios", () => escolherAcao("excluir"))}
                  <li className="rd-menu-sep" />
                  {emBreve("↻ Atualizar situação no e-commerce", SEM_LOJA)}
                  <li className="rd-menu-sep" />
                  {item("⊞ Criar produtos", () => escolherAcao("criar"))}
                  {emBreve(
                    "↻ Atualizar produtos",
                    "Traz título, preço e fotos do marketplace para o produto. " + SEM_LOJA,
                  )}
                  {emBreve(
                    "⛓ Desvincular produtos",
                    "Todo anúncio precisa de um produto. Para trocar, use Relacionar anúncios.",
                    "use relacionar",
                  )}
                  <li className="rd-menu-sep" />
                  {emBreve("🚀 Impulsionar anúncios", SEM_LOJA)}
                </ul>
              )}
            </div>
          </div>
        )}
        <p className="rd-note">
          Sem loja conectada, nada é enviado ao marketplace: publicação, pausa e preços valem só no
          Radar. Preço novo passa por aprovação na Central de ações.
        </p>
      </section>

      {menuLinha && anuncioLinha && (
        <>
          <div className="rd-menu-fundo" onClick={() => setMenuLinha(null)} />
          <ul
            role="menu"
            className="rd-menu-linha"
            style={{ left: menuLinha.x, top: menuLinha.y }}
            aria-label={`Ações de ${str(anuncioLinha.titulo)}`}
          >
            {anuncioLinha.produto_id &&
              item("▥ Ver na página do produto", () => {
                setMenuLinha(null);
                abrirProduto(str(anuncioLinha.produto_id));
              })}
            {item(`↗ Ver na central do vendedor (${canal})`, () => {
              setMenuLinha(null);
              setAviso("");
              setAvisoOk(verNaCentral(anuncioLinha));
            })}
            <li className="rd-menu-sep" />
            {item("⇪ Enviar para o e-commerce", semLoja)}
            {item("$ Enviar preço para o e-commerce", () => escolherAcao("precos", [menuLinha.id]))}
            {item("▣ Enviar estoque para o e-commerce", semLoja)}
            {item("🔗 Relacionar a outro produto", () =>
              escolherAcao("relacionar", [menuLinha.id]),
            )}
            <li className="rd-menu-sep" />
            {emBreve("↻ Atualizar situação no e-commerce", SEM_LOJA)}
            {item("⊞ Criar produto a partir do anúncio", () =>
              escolherAcao("criar", [menuLinha.id]),
            )}
            <li className="rd-menu-sep" />
            {item("🗑 Excluir anúncio", () => escolherAcao("excluir", [menuLinha.id]))}
          </ul>
        </>
      )}
      {acao === "excluir" && (
        <Janela titulo="Excluir anúncios" fechar={() => setAcao(null)}>
          <p>
            Excluir {alvo.length} anúncio(s) do Radar? Não dá para desfazer. Anúncio no ar no
            marketplace (ativo ou pausado) ou com histórico de preço na Central de ações não é
            excluído.
          </p>
          <div className="rd-modal-foot">
            <button onClick={() => setAcao(null)}>Cancelar</button>
            <button
              className="perigo"
              disabled={busy}
              onClick={async () => {
                if (
                  await executar({
                    op: "anuncios_acao_lote",
                    acao: "EXCLUIR",
                    ids: alvo,
                  })
                )
                  concluir();
              }}
            >
              Excluir
            </button>
          </div>
        </Janela>
      )}
      {acao === "criar" && (
        <Janela titulo="Criar produtos a partir dos anúncios" fechar={() => setAcao(null)}>
          <p>
            Cria {alvo.length} produto(s) novo(s), um por anúncio, com título, SKU e preço do
            anúncio, e passa cada anúncio para o produto novo. Os produtos entram com cadastro
            incompleto: complete em Produtos.
          </p>
          <p className="rd-note">
            Use quando o anúncio estiver ligado ao produto errado e o produto certo ainda não
            existir. Se ele já existe, use &quot;Relacionar anúncios&quot;.
          </p>
          <div className="rd-modal-foot">
            <button onClick={() => setAcao(null)}>Cancelar</button>
            <button
              className="primary"
              disabled={busy}
              onClick={async () => {
                if (
                  await executar({
                    op: "anuncios_acao_lote",
                    acao: "CRIAR_PRODUTOS",
                    ids: alvo,
                  })
                )
                  concluir();
              }}
            >
              Criar produtos
            </button>
          </div>
        </Janela>
      )}
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
              if (ok) concluir();
            }}
          />
        </Janela>
      )}
      {acao === "precos" && (
        <Janela titulo="Enviar preços para o e-commerce" fechar={() => setAcao(null)}>
          <p className="rd-note">
            Sem loja conectada, o preço novo vale só no Radar, depois de aprovado na Central de
            ações. O envio ao marketplace entra com a conexão da loja.
          </p>
          <Precos
            selecionados={selecionados}
            busy={busy}
            enviar={async (itens, motivo) => {
              const ok = await executar({
                op: "anuncios_precos",
                itens,
                motivo,
              });
              if (ok) concluir();
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
  categorias,
  categoriaCanais,
  busy,
  executar,
  fechar,
}: Props & { canal: string; fechar: () => void }) {
  const [produto, setProduto] = useState("");
  const [destino, setDestino] = useState(canal || CANAIS[0]);
  const [titulo, setTitulo] = useState("");
  const [preco, setPreco] = useState("");
  const [erro, setErro] = useState("");
  const [codigoCategoria, setCodigoCategoria] = useState("");
  const [nomeCategoria, setNomeCategoria] = useState("");
  const escolhido = produtos.find((x) => x.id === produto);
  const categoria = categorias.find((c) => c.id === escolhido?.categoria_id);
  const faltaVinculo = !!categoria && !vinculoDe(categoriaCanais, categoria.id, destino);

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
          // Vínculo da categoria informado aqui é gravado antes do anúncio.
          if (faltaVinculo && categoria && codigoCategoria.trim()) {
            if (!nomeCategoria.trim()) {
              setErro(`Informe também o nome da categoria no ${destino}.`);
              return;
            }
            const ok = await executar({
              op: "categoria_vinculo",
              categoria_id: categoria.id,
              canal: destino,
              codigo_externo: codigoCategoria.trim(),
              nome_externo: nomeCategoria.trim(),
            });
            if (!ok) return;
          }
          if (
            await executar({
              op: "anuncio",
              produto_id: produto,
              canal: destino,
              titulo,
              preco,
            })
          )
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
          {escolhido && !categoria && (
            <p className="wide rd-aviso-categoria">
              ⚠ Este produto não tem categoria. Defina a categoria no cadastro do produto: o
              marketplace precisa dela para aceitar o anúncio.
            </p>
          )}
          {faltaVinculo && categoria && (
            <div className="wide rd-aviso-categoria">
              <strong>
                ⚠ A categoria &quot;{str(categoria.nome)}&quot; ainda não está vinculada a uma
                categoria do {destino}.
              </strong>
              <p>
                Diga em qual categoria do {destino} este produto entra. Sem loja conectada, copie o
                código e o nome na central do vendedor.
              </p>
              <div className="rd-vinculo-canal">
                <input
                  aria-label={`Código da categoria no ${destino}`}
                  placeholder="Código (ex.: MLB1234)"
                  maxLength={60}
                  value={codigoCategoria}
                  onChange={(e) => setCodigoCategoria(e.target.value)}
                />
                <input
                  aria-label={`Nome da categoria no ${destino}`}
                  placeholder="Nome no marketplace (ex.: Casa > Cortinas)"
                  maxLength={300}
                  value={nomeCategoria}
                  onChange={(e) => setNomeCategoria(e.target.value)}
                />
              </div>
              <small className="rd-dica">
                Se deixar em branco, o anúncio fica em &quot;Necessitam atenção&quot; até você
                vincular a categoria.
              </small>
            </div>
          )}
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
