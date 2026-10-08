"use client";

// Anunciar: passo a passo para levar um ou vários produtos às lojas da empresa.
//   1. Onde anunciar: as lojas cadastradas em Integrações (quantas forem).
//   2. Categoria: o marketplace exige a categoria dele; quem não tem o vínculo liga ali mesmo.
//   3. Ajustes: título, preço e quantidade podem ser diferentes em cada loja.
//   4. Conferência: as regras de cada marketplace; o que falta no cadastro se completa ali.
// O resultado são anúncios PRONTOS. Nada sobe para o marketplace antes da loja ser conectada.

import { useEffect, useState } from "react";
import { TITULO_MAX_RADAR, letras, palavras, regraDe, type Regra } from "./canais";
import { vinculoDe } from "./categorias";
import { CHAVE_DA_PENDENCIA, LinhaPreencher, faltasDoCadastro, type Chave } from "./pendencias";
import { Badge, str, type Row } from "./ui";

type Props = {
  ids: string[];
  produtos: Row[];
  lojas: Row[];
  categorias: Row[];
  categoriaCanais: Row[];
  imagens: Row[];
  fornecedores: Row[];
  produtoFornecedores: Row[];
  disponivel: (p: Row) => number;
  podeCadastrarLojas: boolean;
  erro: string;
  busy: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  recarregar: () => Promise<void>;
  irParaLojas: () => void;
  fechar: () => void;
};

type Ajuste = { titulo: string; preco: string; estoque: string };

const PASSOS = ["Onde anunciar", "Categoria", "Ajustes por loja", "Conferir e salvar"];

export default function Anunciar(props: Props) {
  const { produtos, lojas } = props;
  const [passo, setPasso] = useState(0);
  const [escolhidas, setEscolhidas] = useState<string[]>(() =>
    lojas.length === 1 ? [str(lojas[0].id)] : [],
  );
  const [ajustes, setAjustes] = useState<Record<string, Ajuste>>({});
  const [novaCategoria, setNovaCategoria] = useState("");
  // Largura e altura de cada foto dos produtos, medidas no navegador (o tamanho mínimo é regra
  // do marketplace).
  const [tamanhos, setTamanhos] = useState<Record<string, [number, number]>>({});
  const [vinculo, setVinculo] = useState<Record<string, { codigo: string; nome: string }>>({});

  // Produto com variações não é vendido: as variações são, cada uma vira um anúncio.
  const itens = props.ids
    .flatMap((id) => {
      const p = produtos.find((x) => x.id === id);
      if (!p || p.excluido_em) return [];
      return p.tipo === "VARIACAO"
        ? produtos.filter((f) => f.pai_id === p.id && !f.excluido_em)
        : [p];
    })
    .filter((p, i, todos) => todos.findIndex((x) => x.id === p.id) === i);

  const lojaDe = (id: string) => lojas.find((l) => str(l.id) === id)!;
  // Cada par produto × loja vira um anúncio novo, mesmo que já exista outro dele na loja (o
  // lojista pode ter quantos quiser: teste de título, ads, mais catálogo).
  const pares = itens.flatMap((p) =>
    escolhidas.map((l) => ({ p, loja: lojaDe(l), chave: `${str(p.id)}|${l}` })),
  );
  const marketplaces = [...new Set(escolhidas.map((l) => str(lojaDe(l).marketplace)))];
  const ajusteDe = (p: Row, chave: string): Ajuste =>
    ajustes[chave] ?? {
      titulo: str(p.nome),
      preco: str(p.preco),
      estoque: String(Math.max(0, props.disponivel(p))),
    };
  const mudar = (p: Row, chave: string, campo: keyof Ajuste, valor: string) =>
    setAjustes((a) => ({ ...a, [chave]: { ...ajusteDe(p, chave), [campo]: valor } }));

  // Categoria: produto sem categoria escolhe uma; categoria sem vínculo com o marketplace liga.
  const semCategoria = itens.filter((p) => !p.categoria_id);
  const faltaVinculo = [...new Set(itens.map((p) => str(p.categoria_id)).filter(Boolean))].flatMap(
    (cat) =>
      marketplaces
        .filter((m) => !vinculoDe(props.categoriaCanais, cat, m))
        .map((m) => ({ cat, m, chave: `${cat}|${m}` })),
  );

  // Conferência por produto: o que falta no cadastro (regra do servidor) e imagens.
  const fotosDe = (p: Row) =>
    props.imagens.filter((i) => i.produto_id === p.id || (p.pai_id && i.produto_id === p.pai_id));
  const imagensDe = (p: Row) => fotosDe(p).length;
  const medir = [...new Set(itens.flatMap((p) => fotosDe(p).map((i) => str(i.id))))].join(",");
  useEffect(() => {
    for (const id of medir.split(",").filter(Boolean)) {
      const foto = new Image();
      foto.onload = () =>
        setTamanhos((t) => ({ ...t, [id]: [foto.naturalWidth, foto.naturalHeight] }));
      foto.src = `/api/radar/imagens/${id}`;
    }
  }, [medir]);
  const faltasDe = (p: Row) => {
    // Sem campo para completar ali (chave), o texto diz o que fazer.
    const faltas: { texto: string; chave?: Chave }[] = faltasDoCadastro(p).map((t) =>
      CHAVE_DA_PENDENCIA[t]
        ? { texto: t, chave: CHAVE_DA_PENDENCIA[t] }
        : { texto: `Falta ${t}: complete no cadastro do produto` },
    );
    const minimo = Math.max(1, ...marketplaces.map((m) => regraDe(m).imagensMin));
    if (imagensDe(p) < minimo)
      faltas.push({ texto: `Pelo menos ${minimo} imagem(ns)`, chave: "imagem" });
    for (const m of marketplaces) {
      const r = regraDe(m);
      const pequenas = fotosDe(p).filter((i) => {
        const t = tamanhos[str(i.id)];
        if (!t) return false;
        return (
          (r.fotoMaiorLadoMin != null && Math.max(...t) < r.fotoMaiorLadoMin) ||
          (r.fotoMenorLadoMin != null && Math.min(...t) < r.fotoMenorLadoMin)
        );
      }).length;
      if (pequenas)
        faltas.push({
          texto: `${pequenas} foto(s) pequena(s) para o ${m}: ${
            r.fotoMaiorLadoMin
              ? `o maior lado precisa ter ${r.fotoMaiorLadoMin} pixels ou mais`
              : `os dois lados precisam ter ${r.fotoMenorLadoMin} pixels ou mais`
          }. Troque na aba Imagens do cadastro do produto`,
        });
    }
    const descricao = str(p.descricao).trim();
    if (descricao)
      for (const m of marketplaces) {
        const r = regraDe(m);
        if (r.descricaoMinPalavras && palavras(descricao) < r.descricaoMinPalavras)
          faltas.push({
            texto: `Descrição com pelo menos ${r.descricaoMinPalavras} palavras no ${m} (tem ${palavras(descricao)})`,
            chave: "descricao",
          });
        if (r.descricaoMax && descricao.length > r.descricaoMax)
          faltas.push({
            texto: `Descrição com até ${r.descricaoMax} letras no ${m} (tem ${descricao.length})`,
            chave: "descricao",
          });
      }
    return faltas;
  };
  const problemasDoPar = ({ p, loja, chave }: (typeof pares)[number]) => {
    const r = regraDe(loja.marketplace);
    const a = ajusteDe(p, chave);
    const m = str(loja.marketplace);
    const lista: string[] = [];
    const n = letras(a.titulo);
    if (foraDoTitulo(r, n)) lista.push(`Título com ${n} letras: no ${m} ${limiteTitulo(r)}.`);
    const preco = Number(a.preco.replace(",", "."));
    if (!(preco > 0)) lista.push("Preço maior que zero.");
    // Custo só chega para quem vê o financeiro; para os outros, o servidor confere ao salvar.
    else if (p.custo != null && preco < Number(p.custo))
      lista.push(`Preço abaixo do custo (R$ ${moeda(Number(p.custo))}): a política bloqueia.`);
    else if (
      (r.precoMin != null && preco < r.precoMin) ||
      (r.precoMax != null && preco > r.precoMax)
    )
      lista.push(`Preço no ${m}: de R$ ${moeda(r.precoMin ?? 0)} a R$ ${moeda(r.precoMax ?? 0)}.`);
    const qtd = Number(a.estoque.trim());
    if (!/^\d+$/.test(a.estoque.trim())) lista.push("Quantidade a anunciar (número inteiro).");
    else if (qtd < Math.max(1, r.estoqueMin ?? 1))
      lista.push(`Quantidade: pelo menos ${Math.max(1, r.estoqueMin ?? 1)}.`);
    else if (r.estoqueMax != null && qtd > r.estoqueMax)
      lista.push(`Quantidade no ${m}: no máximo ${r.estoqueMax.toLocaleString("pt-BR")}.`);
    return lista;
  };
  const comFalta = itens.filter((p) => faltasDe(p).length > 0);
  const paresComProblema = pares.filter((x) => problemasDoPar(x).length > 0);
  const tudoCerto = pares.length > 0 && comFalta.length === 0 && paresComProblema.length === 0;

  const podeAvancar = [
    pares.length > 0,
    semCategoria.length === 0 && faltaVinculo.length === 0,
    paresComProblema.length === 0,
    tudoCerto,
  ];

  async function salvar() {
    const ok = await props.executar({
      op: "anunciar",
      itens: pares.map(({ p, loja, chave }) => {
        const a = ajusteDe(p, chave);
        return {
          produto_id: p.id,
          loja_id: loja.id,
          titulo: a.titulo.trim(),
          preco: a.preco.replace(",", "."),
          estoque: a.estoque.trim(),
        };
      }),
    });
    if (ok) props.fechar();
  }

  const linha = (p: Row, chave: Chave) => (
    <LinhaPreencher
      key={`${str(p.id)}-${chave}`}
      produto={p}
      chave={chave}
      imagens={props.imagens}
      categorias={props.categorias}
      fornecedores={props.fornecedores}
      produtoFornecedores={props.produtoFornecedores}
      executar={props.executar}
      recarregar={props.recarregar}
    />
  );

  let corpo: React.ReactNode;
  if (passo === 0) {
    corpo = lojas.length ? (
      <>
        <p className="rd-note">
          {itens.length === 1
            ? `Escolha onde anunciar ${str(itens[0].nome)}.`
            : `Escolha onde anunciar estes ${itens.length} produtos.`}{" "}
          Aparecem as suas lojas cadastradas em Integrações.
        </p>
        <div className="rd-anunciar-lojas">
          {lojas.map((l) => {
            const id = str(l.id);
            return (
              <label key={id} className={escolhidas.includes(id) ? "ativo" : ""}>
                <input
                  type="checkbox"
                  checked={escolhidas.includes(id)}
                  onChange={(e) =>
                    setEscolhidas((s) =>
                      e.target.checked ? [...s, id] : s.filter((x) => x !== id),
                    )
                  }
                />
                <span>
                  <strong>{str(l.nome)}</strong>
                  <small>{str(l.marketplace)}</small>
                </span>
                {l.conectada_em ? (
                  <Badge tone="green">Conectada</Badge>
                ) : (
                  <Badge tone="amber">Aguardando conexão</Badge>
                )}
              </label>
            );
          })}
        </div>
        {lojas.length > 1 && (
          <button
            type="button"
            className="text"
            onClick={() => setEscolhidas(lojas.map((l) => str(l.id)))}
          >
            Marcar todas
          </button>
        )}
      </>
    ) : (
      <div className="rd-anunciar-vazio">
        <p>
          Você ainda não tem lojas cadastradas. Cadastre cada conta sua nos marketplaces (pode ter
          várias no mesmo) e volte aqui.
        </p>
        {props.podeCadastrarLojas ? (
          <button className="primary" onClick={props.irParaLojas}>
            Cadastrar minhas lojas
          </button>
        ) : (
          <p className="rd-dica">Peça ao dono ou ao gestor para cadastrar as lojas.</p>
        )}
      </div>
    );
  } else if (passo === 1) {
    corpo =
      semCategoria.length === 0 && faltaVinculo.length === 0 ? (
        <p className="rd-pendencias rd-pendencias-ok">
          ✓ As categorias já estão ligadas às dos marketplaces escolhidos.
        </p>
      ) : (
        <>
          {semCategoria.length > 0 && (
            <>
              <h3 className="rd-secao">Escolha a categoria</h3>
              <div className="rd-preencher-lista">
                {semCategoria.map((p) => linha(p, "categoria"))}
              </div>
              <form
                className="rd-anunciar-nova-categoria"
                onSubmit={async (e) => {
                  e.preventDefault();
                  if (await props.executar({ op: "categoria", nome: novaCategoria.trim() }))
                    setNovaCategoria("");
                }}
              >
                <label>
                  Não tem a categoria na lista? Crie aqui:
                  <input
                    maxLength={120}
                    placeholder="Ex.: Cortinas"
                    value={novaCategoria}
                    onChange={(e) => setNovaCategoria(e.target.value)}
                  />
                </label>
                <button disabled={props.busy || !novaCategoria.trim()}>Criar categoria</button>
              </form>
            </>
          )}
          {faltaVinculo.length > 0 && (
            <>
              <h3 className="rd-secao">Ligue à categoria do marketplace</h3>
              <p className="rd-note">
                Cada marketplace tem a lista de categorias dele. Informe o código e o nome da
                categoria lá (aparecem na central do vendedor). Isso fica salvo: da próxima vez não
                pergunta de novo. Quando a loja estiver conectada, o Radar mostra a lista do
                marketplace para você só escolher.
              </p>
              {faltaVinculo.map(({ cat, m, chave }) => {
                const v = vinculo[chave] ?? { codigo: "", nome: "" };
                const nomeCat = str(props.categorias.find((c) => str(c.id) === cat)?.nome);
                return (
                  <form
                    key={chave}
                    className="rd-anunciar-vinculo"
                    onSubmit={async (e) => {
                      e.preventDefault();
                      await props.executar({
                        op: "categoria_vinculo",
                        categoria_id: cat,
                        canal: m,
                        codigo_externo: v.codigo.trim(),
                        nome_externo: v.nome.trim(),
                      });
                    }}
                  >
                    <strong>
                      {nomeCat} → {m}
                    </strong>
                    <input
                      aria-label={`Código da categoria no ${m}`}
                      placeholder="Código (ex.: MLB1234)"
                      maxLength={60}
                      value={v.codigo}
                      onChange={(e) =>
                        setVinculo((x) => ({ ...x, [chave]: { ...v, codigo: e.target.value } }))
                      }
                    />
                    <input
                      aria-label={`Nome da categoria no ${m}`}
                      placeholder="Nome (ex.: Casa > Cortinas)"
                      maxLength={300}
                      value={v.nome}
                      onChange={(e) =>
                        setVinculo((x) => ({ ...x, [chave]: { ...v, nome: e.target.value } }))
                      }
                    />
                    <button
                      className="primary"
                      disabled={props.busy || !v.codigo.trim() || !v.nome.trim()}
                    >
                      Ligar
                    </button>
                  </form>
                );
              })}
            </>
          )}
        </>
      );
  } else if (passo === 2) {
    corpo = (
      <>
        <p className="rd-note">
          Mudar aqui altera só o anúncio daquela loja; o cadastro do produto continua igual. A
          quantidade é a que vai aparecer no anúncio.
        </p>
        <div className="rd-anunciar-ajustes">
          {pares.map((par) => {
            const { p, loja, chave } = par;
            const a = ajusteDe(p, chave);
            const r = regraDe(loja.marketplace);
            const n = letras(a.titulo);
            const foraDoLimite = foraDoTitulo(r, n);
            return (
              <div key={chave} className="rd-anunciar-par">
                <div className="rd-anunciar-par-nome">
                  <strong>{str(p.nome)}</strong>
                  <small>
                    SKU {str(p.sku)} · {str(loja.nome)}
                  </small>
                </div>
                <label className="rd-anunciar-titulo">
                  Título
                  <input
                    value={a.titulo}
                    maxLength={300}
                    onChange={(e) => mudar(p, chave, "titulo", e.target.value)}
                  />
                  <small className={foraDoLimite ? "rd-erro-texto" : ""}>
                    {`${n}/${r.tituloMax ?? TITULO_MAX_RADAR}`}
                    {r.tituloMin > 1 && ` (mínimo ${r.tituloMin})`}
                    {!r.tituloMax && " (limite do Radar; o da loja é conferido ao conectar)"}
                  </small>
                </label>
                <label>
                  Preço (R$)
                  <input
                    inputMode="decimal"
                    value={a.preco}
                    onChange={(e) =>
                      mudar(p, chave, "preco", e.target.value.replace(/[^0-9.,]/g, ""))
                    }
                  />
                </label>
                <label>
                  Quantidade
                  <input
                    inputMode="numeric"
                    value={a.estoque}
                    onChange={(e) => mudar(p, chave, "estoque", e.target.value.replace(/\D/g, ""))}
                  />
                  <small>Em estoque: {props.disponivel(p)}</small>
                </label>
              </div>
            );
          })}
        </div>
      </>
    );
  } else {
    corpo = tudoCerto ? (
      <>
        <p className="rd-pendencias rd-pendencias-ok">
          ✓ Tudo certo: {pares.length} anúncio(s) em {escolhidas.length} loja(s) seguem as regras
          dos marketplaces.
        </p>
        <p className="rd-note">
          Os anúncios ficam salvos como <strong>prontos para publicar</strong>. Eles sobem para o
          marketplace quando a loja for conectada (a conexão entra depois do CNPJ).
        </p>
      </>
    ) : (
      <>
        <p className="rd-note">
          Falta pouco. Complete abaixo: cada campo salva sozinho no cadastro do produto ao sair
          dele.
        </p>
        {comFalta.map((p) => {
          const faltas = faltasDe(p);
          return (
            <div key={str(p.id)} className="rd-anunciar-falta">
              <strong>
                {str(p.nome)} <small>SKU {str(p.sku)}</small>
              </strong>
              <div className="rd-preencher-lista">
                {faltas.map((f, i) =>
                  f.chave ? (
                    <div key={f.texto}>
                      <small className="rd-dica">{f.texto}</small>
                      {faltas.findIndex((x) => x.chave === f.chave) === i && linha(p, f.chave)}
                    </div>
                  ) : (
                    <p key={f.texto} className="rd-dica">
                      {f.texto}.
                    </p>
                  ),
                )}
              </div>
            </div>
          );
        })}
        {paresComProblema.map((par) => (
          <div key={par.chave} className="rd-anunciar-falta">
            <strong>
              {str(par.p.nome)} <small>{str(par.loja.nome)}</small>
            </strong>
            {problemasDoPar(par).map((t) => (
              <p key={t} className="rd-dica">
                {t}{" "}
                <button type="button" className="text" onClick={() => setPasso(2)}>
                  Ajustar
                </button>
              </p>
            ))}
          </div>
        ))}
      </>
    );
  }

  return (
    <div className="rd-modal-backdrop lateral" onClick={() => !props.busy && props.fechar()}>
      <section
        className="rd-modal rd-modal-largo lateral"
        role="dialog"
        aria-modal="true"
        aria-label="Anunciar"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="rd-card-head">
          <h2>Anunciar</h2>
          <button aria-label="Fechar" disabled={props.busy} onClick={props.fechar}>
            ×
          </button>
        </div>
        <ol className="rd-anunciar-passos">
          {PASSOS.map((t, i) => (
            <li key={t} className={i === passo ? "atual" : i < passo ? "feito" : ""}>
              {i + 1}. {t}
            </li>
          ))}
        </ol>
        {props.erro && (
          <p className="rd-error" role="alert">
            {props.erro}
          </p>
        )}
        {corpo}
        <div className="rd-modal-foot">
          {passo > 0 && (
            <button disabled={props.busy} onClick={() => setPasso(passo - 1)}>
              ← Voltar
            </button>
          )}
          {passo < PASSOS.length - 1 ? (
            <button
              className="primary"
              disabled={!podeAvancar[passo]}
              onClick={() => setPasso(passo + 1)}
            >
              Continuar →
            </button>
          ) : (
            <button className="primary" disabled={props.busy || !tudoCerto} onClick={salvar}>
              Salvar {pares.length} anúncio(s) pronto(s)
            </button>
          )}
        </div>
      </section>
    </div>
  );
}

const foraDoTitulo = (r: Regra, n: number) =>
  n < r.tituloMin || n > (r.tituloMax ?? TITULO_MAX_RADAR);
const limiteTitulo = (r: Regra) =>
  r.tituloMax
    ? `vai de ${r.tituloMin} a ${r.tituloMax}`
    : `vai de ${r.tituloMin} a ${TITULO_MAX_RADAR} (limite do Radar)`;
const moeda = (v: number) => v.toLocaleString("pt-BR", { minimumFractionDigits: 2 });
