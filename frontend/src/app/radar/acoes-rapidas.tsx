"use client";

// Ações rápidas do produto ("⋯" da lista, barra de lote e "Mais ações" da visualização): cada uma
// abre um painel na lateral, com a lista visível atrás, em vez de trocar de tela. Pedido da
// Jéssica com base no modelo de outro ERP.

import { createContext, use, useEffect, useMemo, useState, type ReactNode } from "react";
import { SIGLA } from "./canais";
import { Badge, cents, centMoney, date, money, str, type Row } from "./ui";

export type TipoRapido =
  | "precos"
  | "estoque"
  | "fiscais"
  | "gerenciar-estoque"
  | "vendas"
  | "compras"
  | "custo-variacoes"
  | "multiempresa"
  | "receber"
  | "custos-iniciar";

// O erro do último comando aparece dentro do painel (a mensagem da tela fica atrás dele).
const ErroDoPainel = createContext("");

/** Painel lateral padrão das ações rápidas. */
export function Gaveta({
  titulo,
  fechar,
  children,
  rodape,
}: {
  titulo: string;
  fechar: () => void;
  children: ReactNode;
  rodape?: ReactNode;
}) {
  const erro = use(ErroDoPainel);
  return (
    <div className="rd-gaveta-fundo" onClick={fechar}>
      <aside
        className="rd-gaveta"
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        onClick={(e) => e.stopPropagation()}
      >
        <header>
          <h2>{titulo}</h2>
          <button className="rd-gaveta-fechar" onClick={fechar}>
            fechar <span aria-hidden="true">×</span>
          </button>
        </header>
        <div className="rd-gaveta-corpo">
          {erro && (
            <p className="rd-error" role="alert">
              {erro}
            </p>
          )}
          {children}
        </div>
        {rodape && <footer>{rodape}</footer>}
      </aside>
    </div>
  );
}

function Aviso({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <div className="rd-gaveta-aviso">
      <span aria-hidden="true">i</span>
      <div>
        <strong>{titulo}</strong>
        <p>{children}</p>
      </div>
    </div>
  );
}

type Props = {
  tipo: TipoRapido;
  ids: string[];
  produtos: Row[];
  lojas: Row[];
  anuncios: Row[];
  movimentos: Row[];
  registros: Row[];
  financeiro: boolean;
  podeAjustarEstoque: boolean;
  busy: boolean;
  erro: string;
  disponivel: (p: Row) => number;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  ir: (pagina: string) => void;
  fechar: () => void;
};

export default function PainelRapido(props: Props) {
  return (
    <ErroDoPainel value={props.erro}>
      <Painel {...props} />
    </ErroDoPainel>
  );
}

function Painel(props: Props) {
  const { produtos } = props;
  const escolhidos = props.ids
    .map((id) => produtos.find((p) => p.id === id))
    .filter((p): p is Row => !!p);
  // O produto e as variações dele: o que é vendido de fato.
  const vendidos = escolhidos.flatMap((p) =>
    p.tipo === "VARIACAO" ? produtos.filter((f) => f.pai_id === p.id && !f.excluido_em) : [p],
  );
  const nomes = escolhidos.length === 1 ? str(escolhidos[0].nome) : `${escolhidos.length} produtos`;
  const fechar = props.fechar;

  switch (props.tipo) {
    case "precos":
    case "estoque":
      return <Sincronizar {...props} vendidos={vendidos} nomes={nomes} />;
    case "fiscais":
      return (
        <Gaveta titulo="Enviar dados fiscais para o e-commerce" fechar={fechar}>
          <EscolherLojas lojas={props.lojas} escolhidas={[]} mudar={() => {}} desligado />
          <Aviso titulo="Depende da conexão">
            NCM, CEST, origem e código de barras vão para o marketplace quando a loja for conectada.
            Hoje nenhuma loja está conectada, então nada é enviado.
          </Aviso>
          <table className="rd-table">
            <thead>
              <tr>
                <th>Produto</th>
                <th>NCM</th>
                <th>CEST</th>
                <th>Origem</th>
                <th>Código de barras</th>
              </tr>
            </thead>
            <tbody>
              {vendidos.slice(0, 50).map((p) => (
                <tr key={str(p.id)}>
                  <td>{str(p.nome)}</td>
                  <td>{str(p.ncm) || "—"}</td>
                  <td>{str(p.cest) || "—"}</td>
                  <td>{str(p.origem) || "—"}</td>
                  <td>{str(p.gtin) || (p.motivo_sem_gtin ? "sem código" : "—")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Gaveta>
      );
    case "gerenciar-estoque":
      return <GerenciarEstoque {...props} vendidos={vendidos} />;
    case "vendas":
      return <HistoricoVendas {...props} vendidos={vendidos} nomes={nomes} />;
    case "compras": {
      const compras = props.registros.filter(
        (r) => r.tipo === "COMPRA" && vendidos.some((p) => p.id === (r.dados as Row)?.produto_id),
      );
      return (
        <Gaveta titulo={`Histórico de compras · ${nomes}`} fechar={fechar}>
          {compras.length ? (
            <table className="rd-table">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Produto</th>
                  <th>Fornecedor</th>
                  <th>Qtd.</th>
                  {props.financeiro && <th>Custo unit.</th>}
                  <th>Situação</th>
                </tr>
              </thead>
              <tbody>
                {compras.map((r) => {
                  const d = r.dados as Row;
                  return (
                    <tr key={str(r.id)}>
                      <td>{date(r.criado_em)}</td>
                      <td>{str(produtos.find((p) => p.id === d.produto_id)?.sku)}</td>
                      <td>{str(d.fornecedor) || "—"}</td>
                      <td>{str(d.quantidade)}</td>
                      {props.financeiro && <td>{money(d.custo_unitario)}</td>}
                      <td>{d.recebida_em ? "Recebida" : "Pendente"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          ) : (
            <p className="rd-dica">Nenhuma compra registrada para {nomes}.</p>
          )}
        </Gaveta>
      );
    }
    case "custo-variacoes":
      return <CustoVariacoes {...props} escolhidos={escolhidos} />;
    case "custos-iniciar": {
      const comCusto = vendidos.filter((p) => p.custo !== null && p.custo !== undefined);
      return (
        <Gaveta
          titulo="Iniciar histórico de custos"
          fechar={fechar}
          rodape={
            <>
              <button
                className="primary"
                disabled={props.busy || !comCusto.length}
                onClick={async () => {
                  if (await props.executar({ op: "custos_iniciar", ids: props.ids })) fechar();
                }}
              >
                Iniciar
              </button>
              <button onClick={fechar}>cancelar</button>
            </>
          }
        >
          <Aviso titulo="Ponto de partida">
            O custo de hoje de cada produto vira o primeiro registro do histórico. Daí em diante,
            toda mudança de custo (edição, edição em lote, compra recebida) fica registrada com o
            valor antes, o depois, o motivo e quem fez. Produto que já tem histórico não muda.
          </Aviso>
          <p>
            <strong>{comCusto.length}</strong> produto(s) com custo
            {vendidos.length > comCusto.length &&
              ` · ${vendidos.length - comCusto.length} sem custo ficam de fora`}
            .
          </p>
          <p className="rd-dica">
            O histórico aparece na aba “Custo e compras” do produto e em Relatórios → Histórico de
            custos.
          </p>
        </Gaveta>
      );
    }
    case "receber":
      return (
        <Gaveta
          titulo="Receber produtos do e-commerce"
          fechar={fechar}
          rodape={
            <>
              <button className="primary" onClick={() => props.ir("importar")}>
                Importar de uma planilha
              </button>
              <button onClick={() => props.ir("integracoes")}>Ver minhas lojas</button>
            </>
          }
        >
          {props.lojas.length > 0 ? (
            <EscolherLojas lojas={props.lojas} escolhidas={[]} mudar={() => {}} desligado />
          ) : (
            <p className="rd-dica">Nenhuma loja cadastrada ainda.</p>
          )}
          <Aviso titulo="Depende da conexão">
            Quando a loja estiver conectada, o Radar traz os anúncios dela e cria os produtos que
            ainda não existem, vinculando cada anúncio ao seu produto. Hoje nenhuma loja está
            conectada: dá para trazer o catálogo por planilha.
          </Aviso>
        </Gaveta>
      );
    case "multiempresa":
      return (
        <Gaveta titulo="Consultar estoque multiempresa" fechar={fechar}>
          <Aviso titulo="Grupo de empresas">
            Quando a sua conta tiver mais de uma empresa, o estoque de cada uma aparece aqui, lado a
            lado. Por enquanto, só esta empresa.
          </Aviso>
          <table className="rd-table">
            <thead>
              <tr>
                <th>Produto</th>
                <th>Esta empresa: disponível</th>
              </tr>
            </thead>
            <tbody>
              {vendidos.map((p) => (
                <tr key={str(p.id)}>
                  <td>
                    {str(p.nome)} <small className="rd-dica">{str(p.sku)}</small>
                  </td>
                  <td>{props.disponivel(p)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Gaveta>
      );
  }
}

function EscolherLojas({
  lojas,
  escolhidas,
  mudar,
  contagem,
  desligado,
}: {
  lojas: Row[];
  escolhidas: string[];
  mudar: (ids: string[]) => void;
  contagem?: (loja: string) => number;
  desligado?: boolean;
}) {
  return (
    <fieldset className="rd-gaveta-lojas" disabled={desligado}>
      <legend>Lojas</legend>
      {lojas.map((l) => {
        const id = str(l.id);
        const n = contagem?.(id);
        return (
          <label key={id} className={escolhidas.includes(id) ? "ativo" : ""}>
            <input
              type="checkbox"
              checked={escolhidas.includes(id)}
              onChange={(e) =>
                mudar(e.target.checked ? [...escolhidas, id] : escolhidas.filter((x) => x !== id))
              }
            />
            <span className="rd-ver-canal">{SIGLA[str(l.marketplace)] ?? "?"}</span>
            {str(l.nome)}
            {n != null && <small>{n} anúncio(s)</small>}
          </label>
        );
      })}
    </fieldset>
  );
}

function Sincronizar(props: Props & { vendidos: Row[]; nomes: string }) {
  const preco = props.tipo === "precos";
  const anunciosDe = (loja: string) =>
    props.anuncios.filter(
      (a) => str(a.loja_id) === loja && props.vendidos.some((p) => p.id === a.produto_id),
    );
  const comAnuncio = props.lojas.filter((l) => anunciosDe(str(l.id)).length > 0);
  const [lojas, setLojas] = useState<string[]>(() => comAnuncio.map((l) => str(l.id)));
  const [promocional, setPromocional] = useState(false);
  const linhas = lojas.flatMap((l) =>
    anunciosDe(l).map((a) => {
      const p = props.vendidos.find((x) => x.id === a.produto_id)!;
      const novo = preco
        ? promocional && cents(p.preco_promocional) > 0
          ? cents(p.preco_promocional)
          : cents(p.preco)
        : Math.max(0, props.disponivel(p));
      return { a, p, loja: props.lojas.find((x) => str(x.id) === l)!, novo };
    }),
  );
  async function enviar() {
    const ok = await props.executar({
      op: "anuncios_sincronizar",
      campo: preco ? "PRECO" : "ESTOQUE",
      produto_ids: props.ids,
      loja_ids: lojas,
      usar_promocional: promocional,
    });
    if (ok) props.fechar();
  }
  return (
    <Gaveta
      titulo={preco ? "Enviar preços para o e-commerce" : "Enviar estoque ao e-commerce"}
      fechar={props.fechar}
      rodape={
        <>
          <button className="primary" disabled={props.busy || !linhas.length} onClick={enviar}>
            Enviar
          </button>
          <button onClick={props.fechar}>fechar</button>
        </>
      }
    >
      {comAnuncio.length ? (
        <EscolherLojas
          lojas={comAnuncio}
          escolhidas={lojas}
          mudar={setLojas}
          contagem={(l) => anunciosDe(l).length}
        />
      ) : (
        <p className="rd-dica">
          {props.nomes} ainda não tem anúncio em nenhuma loja. Use “Enviar para o e-commerce”
          primeiro.
        </p>
      )}
      <Aviso titulo={preco ? "Sincronização de preços" : "Sincronização de estoque"}>
        {preco
          ? "O preço de venda do cadastro vai para os anúncios das lojas escolhidas. Preço abaixo do custo não é enviado; quem não aprova preço gera uma proposta para o dono ou o gestor."
          : "A quantidade disponível em estoque (físico menos reservado) vai para os anúncios das lojas escolhidas."}{" "}
        Vale no Radar agora e vai para o marketplace quando a loja for conectada.
      </Aviso>
      {preco && (
        <label className="rd-check">
          <input
            type="checkbox"
            checked={promocional}
            onChange={(e) => setPromocional(e.target.checked)}
          />
          Usar o preço promocional quando houver
        </label>
      )}
      {linhas.length > 0 && (
        <table className="rd-table">
          <thead>
            <tr>
              <th>Produto</th>
              <th>Loja</th>
              <th>Hoje</th>
              <th>Vai ficar</th>
            </tr>
          </thead>
          <tbody>
            {linhas.slice(0, 100).map(({ a, p, loja, novo }) => (
              <tr key={str(a.id)}>
                <td>
                  {str(p.nome)} <small className="rd-dica">{str(p.sku)}</small>
                </td>
                <td>{str(loja.nome)}</td>
                <td>{preco ? money(a.preco) : str(a.estoque ?? "—")}</td>
                <td>
                  {preco ? centMoney(novo) : novo}
                  {preco && props.financeiro && novo < cents(p.custo) && (
                    <Badge tone="red">abaixo do custo</Badge>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Gaveta>
  );
}

function GerenciarEstoque(props: Props & { vendidos: Row[] }) {
  const controlados = props.vendidos.filter((p) => p.tipo !== "KIT");
  const [alvo, setAlvo] = useState(str(controlados[0]?.id));
  const [sentido, setSentido] = useState<"ENTRADA" | "SAIDA">("ENTRADA");
  const [quantidade, setQuantidade] = useState("");
  const [motivo, setMotivo] = useState("");
  const p = props.produtos.find((x) => x.id === alvo);
  const movimentos = props.movimentos.filter((m) => m.produto_id === alvo).slice(0, 15);
  const kit = props.vendidos.length === 1 && props.vendidos[0].tipo === "KIT";
  async function ajustar() {
    const q = Number(quantidade);
    const ok = await props.executar({
      op: "estoque",
      produto_id: alvo,
      quantidade: sentido === "ENTRADA" ? q : -q,
      motivo: motivo.trim(),
    });
    if (ok) {
      setQuantidade("");
      setMotivo("");
    }
  }
  return (
    <Gaveta titulo="Gerenciar estoque" fechar={props.fechar}>
      {kit ? (
        <Aviso titulo="Kit">
          O estoque do kit vem dos componentes. Ajuste o estoque de cada componente.
        </Aviso>
      ) : (
        <>
          {controlados.length > 1 && (
            <label className="rd-gaveta-campo">
              Produto
              <select value={alvo} onChange={(e) => setAlvo(e.target.value)}>
                {controlados.map((x) => (
                  <option key={str(x.id)} value={str(x.id)}>
                    {str(x.nome)} · {str(x.sku)}
                  </option>
                ))}
              </select>
            </label>
          )}
          {p && (
            <div className="rd-ver-grade">
              <div className="rd-ver-info">
                <small>Estoque físico</small>
                <span>{str(p.fisico)}</span>
              </div>
              <div className="rd-ver-info">
                <small>Reservado em pedidos</small>
                <span>{str(p.reservado)}</span>
              </div>
              <div className="rd-ver-info">
                <small>Disponível</small>
                <span>{props.disponivel(p)}</span>
              </div>
              <div className="rd-ver-info">
                <small>Mínimo</small>
                <span>{str(p.minimo) || "—"}</span>
              </div>
            </div>
          )}
          {props.podeAjustarEstoque ? (
            <div className="rd-gaveta-ajuste">
              <label className="rd-gaveta-campo">
                Lançamento
                <select
                  value={sentido}
                  onChange={(e) => setSentido(e.target.value as "ENTRADA" | "SAIDA")}
                >
                  <option value="ENTRADA">Entrada (soma)</option>
                  <option value="SAIDA">Saída (tira)</option>
                </select>
              </label>
              <label className="rd-gaveta-campo">
                Quantidade
                <input
                  inputMode="numeric"
                  value={quantidade}
                  onChange={(e) => setQuantidade(e.target.value.replace(/\D/g, ""))}
                />
              </label>
              <label className="rd-gaveta-campo largo">
                Motivo
                <input
                  maxLength={500}
                  placeholder="Ex.: contagem do estoque, avaria, brinde"
                  value={motivo}
                  onChange={(e) => setMotivo(e.target.value)}
                />
              </label>
              <button
                className="primary"
                disabled={props.busy || !(Number(quantidade) > 0) || !motivo.trim()}
                onClick={ajustar}
              >
                Lançar
              </button>
            </div>
          ) : (
            <p className="rd-dica">Só dono, gestor e estoque lançam ajustes.</p>
          )}
          <h3 className="rd-secao">Últimos movimentos</h3>
          {movimentos.length ? (
            <table className="rd-table">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Movimento</th>
                  <th>Físico</th>
                  <th>Reserva</th>
                  <th>Motivo</th>
                </tr>
              </thead>
              <tbody>
                {movimentos.map((m) => (
                  <tr key={str(m.id)}>
                    <td>{date(m.criado_em)}</td>
                    <td>
                      {str(m.tipo)
                        .toLowerCase()
                        .replace(/^./, (c) => c.toUpperCase())}
                    </td>
                    <td>{str(m.fisico_delta)}</td>
                    <td>{str(m.reserva_delta)}</td>
                    <td>{str(m.motivo)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="rd-dica">Nenhum movimento ainda.</p>
          )}
        </>
      )}
    </Gaveta>
  );
}

const dia = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};

function HistoricoVendas(props: Props & { vendidos: Row[]; nomes: string }) {
  const [dias, setDias] = useState(90);
  const [resposta, setResposta] = useState<{
    dias: number;
    linhas?: Row[];
    erro?: string;
  }>();
  const skus = useMemo(() => new Set(props.vendidos.map((p) => str(p.sku))), [props.vendidos]);
  // As vendas vêm do servidor (sem o limite das listas da tela), pelo mesmo relatório de pedidos.
  useEffect(() => {
    let vivo = true;
    const q = new URLSearchParams({
      nome: "pedidos",
      de: dia(dias - 1),
      ate: dia(0),
    });
    fetch(`/api/radar/relatorios/fonte?${q}`, { credentials: "include" })
      .then(async (res) => {
        const corpo = await res.json();
        if (!res.ok) throw new Error(corpo.mensagem ?? "Não foi possível carregar as vendas.");
        if (vivo) setResposta({ dias, linhas: corpo.linhas });
      })
      .catch((e) => vivo && setResposta({ dias, erro: (e as Error).message }));
    return () => {
      vivo = false;
    };
  }, [dias]);
  const carregando = resposta?.dias !== dias;
  const vendas = (resposta?.linhas ?? []).filter((l) => skus.has(str(l.sku)));
  const validas = vendas.filter((l) => !["Cancelado", "Devolvido"].includes(str(l.situacao)));
  const unidades = validas.reduce((s, l) => s + Number(l.quantidade ?? 0), 0);
  const receita = validas.reduce((s, l) => s + cents(l.receita_bruta), 0);
  return (
    <Gaveta titulo={`Histórico de vendas · ${props.nomes}`} fechar={props.fechar}>
      <label className="rd-gaveta-campo">
        Período
        <select value={dias} onChange={(e) => setDias(Number(e.target.value))}>
          <option value={30}>Últimos 30 dias</option>
          <option value={90}>Últimos 90 dias</option>
          <option value={365}>Últimos 12 meses</option>
        </select>
      </label>
      {carregando ? (
        <p className="rd-dica">Carregando…</p>
      ) : resposta?.erro ? (
        <p className="rd-error">{resposta.erro}</p>
      ) : (
        <>
          <p>
            <strong>{validas.length}</strong> pedido(s) · <strong>{unidades}</strong> unidade(s) ·{" "}
            <strong>{centMoney(receita)}</strong> de receita{" "}
            <small className="rd-dica">(sem cancelados e devolvidos)</small>
          </p>
          {vendas.length ? (
            <table className="rd-table">
              <thead>
                <tr>
                  <th>Pedido</th>
                  <th>Data</th>
                  <th>Marketplace</th>
                  <th>Qtd.</th>
                  <th>Valor</th>
                  <th>Situação</th>
                </tr>
              </thead>
              <tbody>
                {vendas.map((l) => (
                  <tr key={str(l.numero)}>
                    <td>{str(l.numero)}</td>
                    <td>{str(l.data).split("-").reverse().join("/")}</td>
                    <td>{str(l.marketplace)}</td>
                    <td>{str(l.quantidade)}</td>
                    <td>{money(l.receita_bruta)}</td>
                    <td>{str(l.situacao)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="rd-dica">Nenhuma venda no período.</p>
          )}
        </>
      )}
    </Gaveta>
  );
}

function CustoVariacoes(props: Props & { escolhidos: Row[] }) {
  const pais = props.escolhidos.filter((p) => p.tipo === "VARIACAO");
  const filhas = props.produtos.filter(
    (f) => pais.some((p) => p.id === f.pai_id) && !f.excluido_em,
  );
  const [custo, setCusto] = useState("");
  async function aplicar() {
    const ok = await props.executar({
      op: "produtos_lote",
      acao: "EDITAR",
      ids: pais.map((p) => p.id),
      campo: "custo",
      modo: "DEFINIR",
      valor: custo.replace(",", "."),
    });
    if (ok) props.fechar();
  }
  return (
    <Gaveta
      titulo="Atualizar custo das variações"
      fechar={props.fechar}
      rodape={
        <>
          <button
            className="primary"
            disabled={
              props.busy || !pais.length || !(Number(custo.replace(",", ".")) >= 0) || !custo
            }
            onClick={aplicar}
          >
            Aplicar a todas
          </button>
          <button onClick={props.fechar}>fechar</button>
        </>
      }
    >
      {!pais.length ? (
        <p className="rd-dica">Só vale para produto com variações.</p>
      ) : (
        <>
          <Aviso titulo="Um custo para todas as variações">
            O custo novo vale para as {filhas.length} variações. Depois, cada compra recebida ajusta
            o custo médio de cada uma.
          </Aviso>
          <label className="rd-gaveta-campo">
            Novo custo (R$)
            <input
              inputMode="decimal"
              placeholder="0,00"
              value={custo}
              onChange={(e) => setCusto(e.target.value.replace(/[^0-9.,]/g, ""))}
            />
          </label>
          <table className="rd-table">
            <thead>
              <tr>
                <th>Variação</th>
                <th>Custo hoje</th>
              </tr>
            </thead>
            <tbody>
              {filhas.map((f) => (
                <tr key={str(f.id)}>
                  <td>
                    {str(f.nome)} <small className="rd-dica">{str(f.sku)}</small>
                  </td>
                  <td>{money(f.custo)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </Gaveta>
  );
}
