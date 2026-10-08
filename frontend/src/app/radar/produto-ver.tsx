"use client";

// Ver produto: o que se abre ao clicar no produto da lista. Mostra o cadastro em abas enxutas,
// só de leitura (dados gerais, complementares, ficha técnica, anúncios, variações ou kit, preços,
// custos e outros), com "Editar" e "Mais ações" no topo. Editar abre o formulário completo na
// aba certa. Inspirado na disposição que a Jéssica trouxe de outro ERP, que o lojista já conhece.

import { useState, type ReactNode } from "react";
import { NO_RADAR, verNaCentral } from "./anuncios";
import { SIGLA } from "./canais";
import type { TipoRapido } from "./acoes-rapidas";
import { arquivosDeProdutos } from "./produtos-lote";
import { faltasDoCadastro } from "./pendencias";
import { Historico, ORIGENS, UNIDADES, type Aba } from "./produto";
import {
  Badge,
  Empty,
  cents,
  centMoney,
  date,
  money,
  ordenarVariacoes,
  str,
  useFecharFora,
  type Row,
} from "./ui";

const MOTIVOS: Record<string, string> = {
  PRODUTO_ARTESANAL: "Produto artesanal ou feito sob medida",
  KIT_DA_LOJA: "Kit montado pela loja",
  SEM_CODIGO_DO_FABRICANTE: "O fabricante não fornece código",
  OUTRO: "Outro motivo",
};
const EMBALAGENS: Record<string, string> = {
  PACOTE_CAIXA: "Pacote / Caixa",
  ROLO_CILINDRO: "Rolo / Cilindro",
  ENVELOPE: "Envelope",
};
const GARANTIAS: Record<string, string> = {
  VENDEDOR: "Do vendedor",
  FABRICANTE: "De fábrica",
  SEM_GARANTIA: "Sem garantia",
};
const TIPOS: Record<string, string> = { SIMPLES: "Simples", VARIACAO: "Com variações", KIT: "Kit" };

type Props = {
  produto: Row;
  abaInicial?: Aba;
  produtos: Row[];
  imagens: Row[];
  anuncios: Row[];
  lojas: Row[];
  categorias: Row[];
  embalagens: Row[];
  fornecedores: Row[];
  produtoFornecedores: Row[];
  kitItens: Row[];
  registros: Row[];
  pedidos: Row[];
  promocoes: Row[];
  financeiro: boolean;
  podeEditar: boolean;
  podeAnunciar: boolean;
  podeEnviarEstoque: boolean;
  podeAjustarEstoque: boolean;
  disponivel: (p: Row) => number;
  editar: (aba: Aba) => void;
  anunciar: () => void;
  /** Abre um painel de ação rápida (o mesmo do "⋯" da lista). */
  rapido: (tipo: TipoRapido) => void;
  clonar?: () => void;
  paraLixeira?: () => void;
  /** Executa um comando (inativar, excluir anexos) e diz se deu certo. */
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  voltar: () => void;
  /** Avisa a aba escolhida: o próximo produto aberto abre nela. */
  aoTrocarAba?: (aba: Aba) => void;
};

const vazio = (v: unknown) => v == null || str(v).trim() === "";
const texto = (v: unknown) => (vazio(v) ? "—" : str(v));
const kg = (v: unknown) =>
  vazio(v) ? "—" : `${Number(v).toLocaleString("pt-BR", { minimumFractionDigits: 3 })} kg`;
const cm = (v: unknown) =>
  vazio(v) ? "—" : `${Number(v).toLocaleString("pt-BR", { minimumFractionDigits: 1 })} cm`;
const lista = <T,>(v: unknown): T[] => {
  if (Array.isArray(v)) return v as T[];
  try {
    return typeof v === "string" && v.startsWith("[") ? (JSON.parse(v) as T[]) : [];
  } catch {
    return [];
  }
};

function Info({
  rotulo,
  children,
  largo,
}: {
  rotulo: string;
  children: ReactNode;
  largo?: boolean;
}) {
  return (
    <div className={`rd-ver-info ${largo ? "largo" : ""}`}>
      <small>{rotulo}</small>
      <span>{children}</span>
    </div>
  );
}

function Secao({ titulo, children }: { titulo?: string; children: ReactNode }) {
  return (
    <section className="rd-ver-secao">
      {titulo && <h3>{titulo}</h3>}
      {children}
    </section>
  );
}

// Caixa com as medidas: ajuda a conferir largura, altura e comprimento de relance.
function Caixa() {
  return (
    <svg className="rd-ver-caixa" viewBox="0 0 170 130" aria-hidden="true">
      <g fill="none" stroke="currentColor" strokeWidth="1">
        <path d="M20 45 L95 20 L150 45 L75 70 Z" />
        <path d="M20 45 L20 105 L75 125 L75 70" />
        <path d="M75 125 L150 100 L150 45" />
      </g>
      <text x="6" y="80" fontSize="11" fill="currentColor">
        A
      </text>
      <text x="40" y="128" fontSize="11" fill="currentColor">
        L
      </text>
      <text x="118" y="125" fontSize="11" fill="currentColor">
        C
      </text>
    </svg>
  );
}

export default function ProdutoVer(props: Props) {
  const p = props.produto;
  const id = str(p.id);
  const tipo = str(p.tipo);
  // Impressão e planilhas: as mesmas funções do "⋯" da lista.
  const arq = arquivosDeProdutos({
    produtos: props.produtos,
    categorias: props.categorias,
    kitItens: props.kitItens,
    veCusto: props.financeiro,
    disponivel: props.disponivel,
  });
  const [aba, setAba] = useState<Aba>(props.abaInicial ?? "gerais");
  const [menu, setMenu] = useState(false);
  const caixaMenu = useFecharFora<HTMLDivElement>(menu, () => setMenu(false));
  const [aviso, setAviso] = useState("");
  const [erroArquivo, setErroArquivo] = useState("");

  const filhas = ordenarVariacoes(
    props.produtos.filter((f) => f.pai_id === p.id && !f.excluido_em),
    lista<string>(p.tipos_variacao),
  );
  const foraDeVenda = filhas.filter((f) => f.permite_venda === false).length;
  const fotos = props.imagens.filter((i) => i.produto_id === p.id);
  const capa = fotos[0];
  const anunciosDoProduto = props.anuncios.filter(
    (a) => a.produto_id === p.id || filhas.some((f) => f.id === a.produto_id),
  );
  const faltas = faltasDoCadastro(p);
  const categoria = props.categorias.find((c) => c.id === p.categoria_id);
  const embalagem = props.embalagens.find((e) => e.id === p.embalagem_id);
  const loja = (a: Row) => props.lojas.find((l) => l.id === a.loja_id);
  const componentes = props.kitItens.filter((k) => k.kit_id === p.id);
  const compras = props.registros.filter(
    (r) =>
      r.tipo === "COMPRA" &&
      [id, ...filhas.map((f) => str(f.id))].includes(str((r.dados as Row)?.produto_id)),
  );
  const vendas = props.pedidos.filter(
    (o) => o.produto_id === p.id || filhas.some((f) => f.id === o.produto_id),
  );
  const promocoesAtivas = props.promocoes.filter(
    (pr) => pr.ativo && (!pr.produto_id || pr.produto_id === p.id),
  );
  const disponivelTotal =
    tipo === "VARIACAO" ? filhas.reduce((s, f) => s + props.disponivel(f), 0) : props.disponivel(p);
  const fisicoTotal =
    tipo === "VARIACAO"
      ? filhas.reduce((s, f) => s + Number(f.fisico ?? 0), 0)
      : Number(p.fisico ?? 0);
  const reservadoTotal =
    tipo === "VARIACAO"
      ? filhas.reduce((s, f) => s + Number(f.reservado ?? 0), 0)
      : Number(p.reservado ?? 0);
  const custo = cents(p.custo);
  const preco = cents(p.preco);

  const abas: [Aba, string][] = [
    ["gerais", "Dados gerais"],
    ["descricao", "Descrição e imagens"],
    ["fiscal", "Fiscal"],
    ["anuncios", `Anúncios${anunciosDoProduto.length ? ` (${anunciosDoProduto.length})` : ""}`],
    ...(tipo === "VARIACAO" ? [["variacoes", "Variações"] as [Aba, string]] : []),
    ...(tipo === "KIT" ? [["variacoes", "Kit"] as [Aba, string]] : []),
    ["precos", "Preço e promoções"],
    ...(props.financeiro ? [["custos", "Custo e compras"] as [Aba, string]] : []),
    ["outros", "Fornecedores e observações"],
  ];

  const conteudo: Record<Aba, ReactNode> = {
    gerais: (
      <>
        <Secao>
          <Info rotulo="Tipo do produto">{TIPOS[tipo] ?? tipo}</Info>
        </Secao>
        <Secao>
          <div className="rd-ver-grade">
            <Info rotulo="Nome do produto" largo>
              {texto(p.nome)}
            </Info>
            <Info rotulo="Código de barras (GTIN)">
              {vazio(p.gtin)
                ? vazio(p.motivo_sem_gtin)
                  ? "—"
                  : `Sem código: ${MOTIVOS[str(p.motivo_sem_gtin)] ?? str(p.motivo_sem_gtin)}`
                : str(p.gtin)}
            </Info>
            <Info rotulo="Unidade de medida">
              {UNIDADES.find(([v]) => v === str(p.unidade))?.[1] ?? texto(p.unidade)}
            </Info>
            <Info rotulo="Código (SKU)">{texto(p.sku)}</Info>
            <Info rotulo="Condição">
              {texto(p.condicao)
                .toLowerCase()
                .replace(/^./, (c) => c.toUpperCase())}
            </Info>
          </div>
        </Secao>
        <Secao titulo="Categorização">
          <div className="rd-ver-grade">
            <Info rotulo="Categoria">{categoria ? str(categoria.nome) : "—"}</Info>
            <Info rotulo="Marca">{texto(p.marca)}</Info>
            <Info rotulo="Modelo">{texto(p.modelo)}</Info>
            <Info rotulo="Linha de produto">{texto(p.linha_produto)}</Info>
          </div>
        </Secao>
        <Secao titulo="Dimensões e peso">
          <div className="rd-ver-medidas">
            <div className="rd-ver-grade">
              <Info rotulo="Peso líquido">{kg(p.peso_liquido_kg)}</Info>
              <Info rotulo="Peso bruto">{kg(p.peso_bruto_kg)}</Info>
              <Info rotulo="Nº de volumes">{texto(p.volumes)}</Info>
              <Info rotulo="Tipo da embalagem">{EMBALAGENS[str(p.formato_embalagem)] ?? "—"}</Info>
              <Info rotulo="Embalagem">
                {embalagem ? str(embalagem.nome) : "Medidas do produto"}
              </Info>
              <span />
              <Info rotulo="Largura">{cm(p.largura_cm ?? embalagem?.largura_cm)}</Info>
              <Info rotulo="Altura">{cm(p.altura_cm ?? embalagem?.altura_cm)}</Info>
              <Info rotulo="Comprimento">{cm(p.comprimento_cm ?? embalagem?.comprimento_cm)}</Info>
            </div>
            <Caixa />
          </div>
        </Secao>
        <Secao titulo="Estoque">
          <div className="rd-ver-grade">
            <Info rotulo="Controlar estoque">{p.controla_estoque === false ? "Não" : "Sim"}</Info>
            <Info rotulo="Estoque físico">{fisicoTotal}</Info>
            <Info rotulo="Reservado em pedidos">{reservadoTotal}</Info>
            <Info rotulo="Disponível">{disponivelTotal}</Info>
            <Info rotulo="Estoque mínimo">{texto(p.minimo)}</Info>
            <Info rotulo="Estoque máximo">{texto(p.maximo)}</Info>
            <Info rotulo="Dias para preparação">{texto(p.dias_preparacao)}</Info>
          </div>
          {tipo === "VARIACAO" && (
            <p className="rd-dica">
              Somando as {filhas.length} variações
              {foraDeVenda > 0 && ` (${foraDeVenda} fora de venda)`}.
            </p>
          )}
          {tipo === "KIT" && <p className="rd-dica">O estoque do kit vem dos componentes.</p>}
        </Secao>
      </>
    ),
    descricao: (
      <>
        <Secao titulo="Descrição">
          {vazio(p.descricao) ? (
            <p className="rd-dica">Sem descrição.</p>
          ) : (
            <div className="rd-ver-descricao">{str(p.descricao)}</div>
          )}
        </Secao>
        <Secao titulo="Imagens">
          <div className="rd-ver-imagens">
            {fotos.map((f) => (
              // eslint-disable-next-line @next/next/no-img-element -- imagem servida pela API autenticada
              <img key={str(f.id)} src={`/api/radar/imagens/${str(f.id)}`} alt="" />
            ))}
            {!fotos.length && <span className="rd-dica">Sem imagens.</span>}
            {props.podeEditar && (
              <button onClick={() => props.editar("descricao")}>Gerenciar imagens</button>
            )}
          </div>
        </Secao>
        <Secao titulo="Características do produto">
          {lista<{ nome: string; valor: string }>(p.atributos).length ? (
            <div className="rd-ver-grade">
              {lista<{ nome: string; valor: string }>(p.atributos).map((a) => (
                <Info key={a.nome} rotulo={a.nome}>
                  {texto(a.valor)}
                </Info>
              ))}
            </div>
          ) : (
            <p className="rd-dica">
              Sem características. Material, medidas, voltagem e outras características ajudam o
              marketplace a mostrar o produto na busca.
            </p>
          )}
        </Secao>
        <Secao titulo="SEO e vídeo">
          <div className="rd-ver-grade">
            <Info rotulo="Keywords" largo>
              {texto(p.keywords)}
            </Info>
            <Info rotulo="Descrição para SEO" largo>
              {texto(p.descricao_seo)}
            </Info>
            <Info rotulo="Vídeo">
              {vazio(p.video_url) ? (
                "—"
              ) : (
                <a href={str(p.video_url)} target="_blank" rel="noopener noreferrer">
                  Abrir vídeo ↗
                </a>
              )}
            </Info>
          </div>
        </Secao>
        <Secao titulo="Tags">
          {lista<string>(p.tags).length ? (
            <div className="rd-ver-chips">
              {lista<string>(p.tags).map((t) => (
                <Badge key={t}>{t}</Badge>
              ))}
            </div>
          ) : (
            <p className="rd-dica">
              As tags servem para classificar os produtos (exemplo: grupo, cor, coleção).
            </p>
          )}
        </Secao>
      </>
    ),
    fiscal: (
      <>
        <Secao titulo="Classificação fiscal">
          <div className="rd-ver-grade">
            <Info rotulo="Origem do produto conforme ICMS" largo>
              {ORIGENS.find(([v]) => v === str(p.origem))?.[1] ?? "—"}
            </Info>
            <Info rotulo="NCM">{texto(p.ncm)}</Info>
            <Info rotulo="Código CEST">{texto(p.cest)}</Info>
          </div>
        </Secao>
        <Secao titulo="Informações tributárias adicionais">
          <div className="rd-ver-grade">
            <Info rotulo="GTIN tributável">{texto(p.gtin_tributavel)}</Info>
            <Info rotulo="Unidade tributável">{texto(p.unidade_tributavel)}</Info>
            <Info rotulo="Fator de conversão">{texto(p.fator_conversao)}</Info>
            <Info rotulo="EX TIPI">{texto(p.ex_tipi)}</Info>
          </div>
        </Secao>
      </>
    ),
    anuncios: (
      <Secao titulo="Anúncios">
        <p className="rd-dica">
          Use “Enviar para o e-commerce” para criar anúncios conferidos, quantos quiser, em qualquer
          loja. Cada anúncio fica ligado a este produto.
        </p>
        {anunciosDoProduto.length ? (
          <div className="rd-table-wrap">
            <table className="rd-table">
              <thead>
                <tr>
                  <th>E-commerce</th>
                  <th>Identificador</th>
                  <th>Título</th>
                  <th>Preço</th>
                  <th>Qtd.</th>
                  <th>Situação</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {anunciosDoProduto.map((a) => {
                  const estado = str(a.estado);
                  return (
                    <tr key={str(a.id)}>
                      <td>
                        <span className="rd-ver-canal">{SIGLA[str(a.canal)] ?? "?"}</span>
                        {str(loja(a)?.nome) || str(a.canal)}
                      </td>
                      <td>
                        {a.id_externo ? (
                          str(a.id_externo)
                        ) : (
                          <small className="rd-dica">Criado no Radar</small>
                        )}
                      </td>
                      <td>{str(a.titulo)}</td>
                      <td>{money(a.preco)}</td>
                      <td>{a.estoque == null ? "—" : str(a.estoque)}</td>
                      <td>
                        <span
                          className={`rd-ver-ponto ${estado === "PRONTO" || estado === "SIMULADO" ? "verde" : estado === "PAUSADO" ? "vermelho" : "amarelo"}`}
                        />
                        {NO_RADAR[estado] ?? estado}
                      </td>
                      <td>
                        <button className="text" onClick={() => setAviso(verNaCentral(a))}>
                          ver anúncio ↗
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty text="Este produto ainda não tem anúncio." />
        )}
      </Secao>
    ),
    variacoes:
      tipo === "KIT" ? (
        <Secao titulo="Composição do kit">
          <div className="rd-table-wrap">
            <table className="rd-table">
              <thead>
                <tr>
                  <th>Componente</th>
                  <th>SKU</th>
                  <th>Quantidade</th>
                  {props.financeiro && <th>Custo</th>}
                  <th>Disponível</th>
                </tr>
              </thead>
              <tbody>
                {componentes.map((k) => {
                  const c = props.produtos.find((x) => x.id === k.componente_id);
                  return (
                    <tr key={str(k.componente_id)}>
                      <td>{str(c?.nome)}</td>
                      <td>{str(c?.sku)}</td>
                      <td>{str(k.quantidade)}</td>
                      {props.financeiro && <td>{money(c?.custo)}</td>}
                      <td>{c ? props.disponivel(c) : "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Secao>
      ) : (
        <>
          <Secao titulo="Variações do produto">
            {lista<string>(p.tipos_variacao).map((t) => {
              const valores = [
                ...new Set(
                  filhas.map((f) => str((f.atributos_variacao as Row)?.[t])).filter(Boolean),
                ),
              ];
              return (
                <div key={t} className="rd-ver-tipo">
                  <small>{t}</small>
                  <div className="rd-ver-chips">
                    {valores.map((v) => (
                      <Badge key={v} tone="green">
                        {v}
                      </Badge>
                    ))}
                  </div>
                </div>
              );
            })}
          </Secao>
          <Secao titulo="Grade das variações">
            <p className="rd-dica">
              Variação sem imagem própria usa as imagens do produto principal no envio ao
              e-commerce.
            </p>
            <div className="rd-table-wrap">
              <table className="rd-table">
                <thead>
                  <tr>
                    <th>Variação</th>
                    <th>Código (SKU)</th>
                    <th>Preço</th>
                    <th>Disponível</th>
                    <th>Imagens</th>
                    <th>Cadastro</th>
                    <th>À venda</th>
                  </tr>
                </thead>
                <tbody>
                  {filhas.map((f) => (
                    <tr key={str(f.id)}>
                      <td>
                        {Object.values((f.atributos_variacao ?? {}) as Record<string, string>).join(
                          " / ",
                        )}
                      </td>
                      <td>{str(f.sku)}</td>
                      <td>{money(f.preco)}</td>
                      <td>{props.disponivel(f)}</td>
                      <td>
                        {props.imagens.filter((i) => i.produto_id === f.id).length ||
                          "do principal"}
                      </td>
                      <td>
                        {faltasDoCadastro(f).length ? `Falta ${faltasDoCadastro(f).length}` : "✓"}
                      </td>
                      <td>{f.permite_venda === false ? <Badge>fora de venda</Badge> : "Sim"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Secao>
        </>
      ),
    precos: (
      <>
        <Secao titulo="Preço de venda">
          <div className="rd-ver-grade">
            <Info rotulo="Preço de venda">{money(p.preco)}</Info>
            <Info rotulo="Preço promocional">
              {vazio(p.preco_promocional)
                ? "—"
                : `${money(p.preco_promocional)}${
                    preco > 0
                      ? ` · −${Math.round((1 - cents(p.preco_promocional) / preco) * 100)}%`
                      : ""
                  }`}
            </Info>
            {props.financeiro && (
              <Info rotulo="Markup (preço ÷ custo)">
                {custo > 0
                  ? (preco / custo).toLocaleString("pt-BR", { maximumFractionDigits: 2 })
                  : "—"}
              </Info>
            )}
            {props.financeiro && (
              <Info rotulo="Margem bruta">
                {preco > 0
                  ? `${(((preco - custo) * 100) / preco).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`
                  : "—"}
              </Info>
            )}
          </div>
        </Secao>
        <Secao titulo="Preço em cada loja">
          {anunciosDoProduto.length ? (
            <div className="rd-table-wrap">
              <table className="rd-table">
                <thead>
                  <tr>
                    <th>Loja</th>
                    <th>Preço no anúncio</th>
                    <th>Diferença do preço de venda</th>
                  </tr>
                </thead>
                <tbody>
                  {anunciosDoProduto.map((a) => (
                    <tr key={str(a.id)}>
                      <td>{str(loja(a)?.nome) || str(a.canal)}</td>
                      <td>{money(a.preco)}</td>
                      <td>{centMoney(cents(a.preco) - preco)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="rd-dica">Sem anúncios ainda.</p>
          )}
        </Secao>
        <Secao titulo="Promoções ativas que valem para este produto">
          {promocoesAtivas.length ? (
            promocoesAtivas.map((pr) => (
              <p key={str(pr.id)}>
                <strong>{str(pr.nome)}</strong>{" "}
                <small className="rd-dica">
                  {pr.tipo === "PERCENTUAL" ? `${str(pr.valor)}%` : money(pr.valor)} ·{" "}
                  {str(pr.canal) || "todos os canais"} · até{" "}
                  {str(pr.fim).split("-").reverse().join("/")}
                </small>
              </p>
            ))
          ) : (
            <p className="rd-dica">Nenhuma.</p>
          )}
        </Secao>
      </>
    ),
    custos: (
      <>
        <Secao titulo="Custo">
          {tipo === "VARIACAO" ? (
            <p className="rd-dica">Cada variação tem o próprio custo (aba variações, no editar).</p>
          ) : (
            <div className="rd-ver-grade">
              <Info rotulo={tipo === "KIT" ? "Custo (soma dos componentes)" : "Custo médio"}>
                {money(p.custo)}
              </Info>
              <Info rotulo="Valor em estoque (custo × físico)">
                {centMoney(custo * fisicoTotal)}
              </Info>
            </div>
          )}
        </Secao>
        <Secao titulo="Histórico de custos">
          <Historico id={str(p.id)} so="CUSTO" />
        </Secao>
        <Secao titulo="Histórico de compras">
          {compras.length ? (
            <div className="rd-table-wrap">
              <table className="rd-table">
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Fornecedor</th>
                    <th>Quantidade</th>
                    <th>Custo unitário</th>
                    <th>Situação</th>
                  </tr>
                </thead>
                <tbody>
                  {compras.map((r) => {
                    const d = r.dados as Row;
                    return (
                      <tr key={str(r.id)}>
                        <td>{date(r.criado_em)}</td>
                        <td>{texto(d.fornecedor)}</td>
                        <td>{str(d.quantidade)}</td>
                        <td>{money(d.custo_unitario)}</td>
                        <td>{d.recebida_em ? "Recebida" : "Pendente"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="rd-dica">Nenhuma compra registrada para este produto.</p>
          )}
        </Secao>
      </>
    ),
    outros: (
      <>
        <Secao>
          <div className="rd-ver-grade">
            <Info rotulo="Garantia">
              {vazio(p.garantia_tipo)
                ? "Não informada"
                : `${GARANTIAS[str(p.garantia_tipo)] ?? str(p.garantia_tipo)}${vazio(p.garantia_meses) ? "" : ` · ${str(p.garantia_meses)} meses`}`}
            </Info>
            <Info rotulo="Data de criação">{date(p.criado_em)}</Info>
            <Info rotulo="Permitir inclusão nas vendas">
              {p.permite_venda === false ? "Não" : "Sim"}
            </Info>
            <Info rotulo="Unidades por caixa">{texto(p.unidades_por_caixa)}</Info>
          </div>
        </Secao>
        <Secao titulo="Fornecedores">
          {props.produtoFornecedores.filter((f) => f.produto_id === p.id).length ? (
            <div className="rd-table-wrap">
              <table className="rd-table">
                <thead>
                  <tr>
                    <th>Nome</th>
                    <th>Código no fornecedor</th>
                  </tr>
                </thead>
                <tbody>
                  {props.produtoFornecedores
                    .filter((f) => f.produto_id === p.id)
                    .map((f) => (
                      <tr key={str(f.fornecedor_id)}>
                        <td>
                          {str(props.fornecedores.find((x) => x.id === f.fornecedor_id)?.nome)}
                        </td>
                        <td>{texto(f.codigo_no_fornecedor)}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="rd-dica">Nenhum fornecedor ligado.</p>
          )}
        </Secao>
        <Secao titulo="Últimas vendas">
          {vendas.length ? (
            <div className="rd-table-wrap">
              <table className="rd-table">
                <thead>
                  <tr>
                    <th>Pedido</th>
                    <th>Data</th>
                    <th>Canal</th>
                    <th>Qtd.</th>
                    <th>Situação</th>
                  </tr>
                </thead>
                <tbody>
                  {vendas.slice(0, 10).map((o) => (
                    <tr key={str(o.id)}>
                      <td>{str(o.numero)}</td>
                      <td>{date(o.criado_em)}</td>
                      <td>{str(o.canal)}</td>
                      <td>{str(o.quantidade)}</td>
                      <td>
                        {str(o.estado)
                          .toLowerCase()
                          .replace(/^./, (c) => c.toUpperCase())}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="rd-dica">Nenhuma venda entre os pedidos recentes.</p>
          )}
          <p className="rd-dica">
            O histórico completo de vendas fica em Relatórios → Vendas por produto.
          </p>
        </Secao>
        <Secao titulo="Observações gerais sobre o produto">
          <p>{texto(p.observacoes_internas)}</p>
        </Secao>
        {props.podeEditar && (
          <button className="text" onClick={() => props.editar("outros")}>
            Ver histórico de alterações →
          </button>
        )}
      </>
    ),
  };

  // Itens que dependem da conexão com o marketplace aparecem, mas desligados e explicados.
  const emBreve = (rotulo: string, motivo: string) => (
    <li>
      <button role="menuitem" disabled title={motivo}>
        {rotulo} <small>({motivo})</small>
      </button>
    </li>
  );
  const item = (rotulo: string, acao: () => void) => (
    <li>
      <button
        role="menuitem"
        onClick={() => {
          setMenu(false);
          setErroArquivo("");
          acao();
        }}
      >
        {rotulo}
      </button>
    </li>
  );

  return (
    <section className="rd-ver">
      <button className="text rd-voltar" onClick={props.voltar}>
        ← Produtos
      </button>
      <header className="rd-ver-topo">
        <div className="rd-ver-titulo">
          {capa ? (
            // eslint-disable-next-line @next/next/no-img-element -- imagem servida pela API autenticada
            <img src={`/api/radar/imagens/${str(capa.id)}`} alt="" />
          ) : (
            <span className="rd-product-thumb">▥</span>
          )}
          <div>
            <h2>{str(p.nome)}</h2>
            <small>
              SKU {str(p.sku)} ·{" "}
              {faltas.length ? (
                <span className="rd-cadastro-falta" title={`Falta: ${faltas.join(", ")}`}>
                  Falta: {faltas.slice(0, 3).join(", ")}
                  {faltas.length > 3 && ` e mais ${faltas.length - 3}`}
                </span>
              ) : (
                <span className="rd-cadastro-ok">✓ cadastro completo</span>
              )}
            </small>
          </div>
        </div>
        <div className="rd-ver-acoes">
          {props.podeAnunciar && (
            <button onClick={props.anunciar}>⇪ Enviar para o e-commerce</button>
          )}
          {props.podeEditar && (
            <button className="primary" onClick={() => props.editar(aba)}>
              ✎ Editar
            </button>
          )}
          <div className="rd-mais-acoes" ref={caixaMenu}>
            <button aria-expanded={menu} onClick={() => setMenu(!menu)}>
              Mais ações{" "}
              <span className="rd-circulo" aria-hidden="true">
                ⋯
              </span>
            </button>
            {menu && (
              <>
                <ul role="menu" className="rd-ver-menu">
                  {props.podeAnunciar && item("⇪ Enviar para o e-commerce", props.anunciar)}
                  {props.podeAnunciar &&
                    item("$ Enviar preços para o e-commerce", () => props.rapido("precos"))}
                  {props.podeEnviarEstoque &&
                    item("▦ Enviar estoque ao e-commerce", () => props.rapido("estoque"))}
                  {item("▤ Enviar dados fiscais para o e-commerce", () => props.rapido("fiscais"))}
                  <li className="rd-menu-sep" />
                  {tipo !== "KIT" &&
                    item(
                      props.podeAjustarEstoque ? "▦ Gerenciar estoque" : "▦ Consultar estoque",
                      () => props.rapido("gerenciar-estoque"),
                    )}
                  {item("⌕ Consultar estoque multiempresa", () => props.rapido("multiempresa"))}
                  {item("🏷 Imprimir etiqueta", () => setErroArquivo(arq.etiquetas([id])))}
                  {item("↙ Visualizar histórico de compras", () => props.rapido("compras"))}
                  {item("↗ Visualizar histórico de vendas", () => props.rapido("vendas"))}
                  <li className="rd-menu-sep" />
                  {props.podeEditar && item("✎ Editar cadastro completo", () => props.editar(aba))}
                  {props.clonar && item("⧉ Clonar produto", props.clonar)}
                  {props.podeEditar && item("# Alterar tags", () => props.editar("descricao"))}
                  {tipo === "VARIACAO" &&
                    emBreve("⇄ Tornar produto simples", "Entra com o Bloco 2")}
                  {emBreve("⇪ Enviar produto para empresas", "Grupo de empresas")}
                  {tipo === "VARIACAO" &&
                    props.financeiro &&
                    props.podeEditar &&
                    item("$ Atualizar custo das variações", () => props.rapido("custo-variacoes"))}
                  <li className="rd-menu-sep" />
                  {item("🖨 Imprimir relatório", () => setErroArquivo(arq.relatorio([id])))}
                  {item("⇩ Exportar para planilha", () =>
                    setErroArquivo(arq.exportarProdutos([id])),
                  )}
                  {tipo === "KIT" &&
                    item("⇩ Exportar composição do kit", () =>
                      setErroArquivo(arq.exportarKits([id])),
                    )}
                  {props.podeEditar && <li className="rd-menu-sep" />}
                  {props.podeEditar &&
                    item(
                      p.permite_venda === false ? "✓ Ativar produto" : "⊘ Inativar produto",
                      () =>
                        void props.executar({
                          op: "produtos_lote",
                          acao: p.permite_venda === false ? "ATIVAR" : "INATIVAR",
                          ids: [id],
                        }),
                    )}
                  {props.podeEditar &&
                    item("🗑 Excluir anexos", () => {
                      if (
                        window.confirm(
                          "Excluir todas as imagens deste produto (e das variações)? Não dá para desfazer.",
                        )
                      )
                        void props.executar({
                          op: "produtos_lote",
                          acao: "EXCLUIR_ANEXOS",
                          ids: [id],
                        });
                    })}
                  {props.paraLixeira && item("🗑 Mover para a lixeira", props.paraLixeira)}
                </ul>
              </>
            )}
          </div>
        </div>
      </header>
      {aviso && (
        <p className="rd-ok" role="status">
          {aviso}
        </p>
      )}
      {erroArquivo && (
        <p className="rd-error" role="alert">
          {erroArquivo}
        </p>
      )}
      <nav className="rd-ver-abas" role="tablist" aria-label="Seções do produto">
        {abas.map(([chave, rotulo]) => (
          <button
            key={chave}
            role="tab"
            aria-selected={aba === chave}
            className={aba === chave ? "ativa" : ""}
            onClick={() => {
              setAba(chave);
              props.aoTrocarAba?.(chave);
            }}
          >
            {rotulo}
          </button>
        ))}
      </nav>
      <div className="rd-ver-corpo">{conteudo[aba]}</div>
    </section>
  );
}

export type { Aba as AbaVer };
