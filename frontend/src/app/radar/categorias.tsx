"use client";

// Categorias da loja, cada uma vinculada à categoria correspondente em cada
// marketplace. O nome é o da lojista (pode ser um "nome fantasia"); o nome do
// marketplace fica no vínculo. Importar anúncios cria as categorias já
// vinculadas.

import { useState } from "react";
import { Badge, Empty, Table, str, type Row } from "./ui";
import { configCategorias } from "./catalogo-filtros";
import FiltrosGenericos, { filtrar, filtroVazio, type Filtro } from "./filtros-genericos";

export { CANAIS } from "./canais";
import { CANAIS } from "./canais";

type Props = {
  categorias: Row[];
  categoriaCanais: Row[];
  produtos: Row[];
  podeEditar: boolean;
  busy: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<Record<string, unknown> | null>;
};

type Vinculo = { codigo: string; nome: string };

export function vinculoDe(categoriaCanais: Row[], categoria: unknown, canal: string) {
  return categoriaCanais.find((v) => v.categoria_id === categoria && v.canal === canal);
}

export default function Categorias(props: Props) {
  const [aberta, setAberta] = useState<Row | "nova" | null>(null);
  const cfg = configCategorias(props.produtos, props.categoriaCanais);
  const [busca, setBusca] = useState<Filtro>(() => filtroVazio(cfg));
  const produtosDa = (id: unknown) =>
    props.produtos.filter((p) => p.categoria_id === id && !p.pai_id).length;
  const semVinculo = props.categorias.filter(
    (c) =>
      produtosDa(c.id) > 0 &&
      !CANAIS.some((canal) => vinculoDe(props.categoriaCanais, c.id, canal)),
  );
  const linhas = filtrar(props.categorias, busca, cfg);

  return (
    <>
      {semVinculo.length > 0 && (
        <div className="rd-alerta-anuncios" role="alert">
          <strong>
            ⚠ {semVinculo.length} categoria(s) com produtos e sem vínculo com marketplace.
          </strong>
          Sem o vínculo, o anúncio pode cair na categoria errada ou ser recusado.
        </div>
      )}
      <section className="rd-card">
        <div className="rd-card-head">
          <h2>{props.categorias.length} categorias</h2>
          {props.podeEditar && (
            <button className="primary" onClick={() => setAberta("nova")}>
              + Nova categoria
            </button>
          )}
        </div>
        <FiltrosGenericos
          cfg={cfg}
          filtro={busca}
          setFiltro={setBusca}
          base={props.categorias}
          total={linhas.length}
        />
        {props.categorias.length === 0 ? (
          <Empty text="Nenhuma categoria ainda. Ao importar anúncios, elas vêm junto, já vinculadas." />
        ) : (
          <Table
            headers={["Categoria", "Produtos", "Vínculo com os marketplaces", ""]}
            rows={linhas.map((c) => [
              <div key="n">
                <strong>{str(c.nome)}</strong>
                {c.origem === "IMPORTACAO" && (
                  <>
                    {" "}
                    <Badge>Veio do marketplace</Badge>
                  </>
                )}
                {c.descricao && (
                  <>
                    <br />
                    <small>{str(c.descricao)}</small>
                  </>
                )}
              </div>,
              String(produtosDa(c.id)),
              <div key="v" className="rd-vinculos">
                {CANAIS.map((canal) => {
                  const v = vinculoDe(props.categoriaCanais, c.id, canal);
                  return (
                    <span
                      key={canal}
                      className={v ? "ok" : "falta"}
                      title={v ? str(v.nome_externo) : ""}
                    >
                      {v ? "✓" : "—"} {canal}
                      {v && <small>{str(v.nome_externo)}</small>}
                    </span>
                  );
                })}
              </div>,
              props.podeEditar ? (
                <button key="e" onClick={() => setAberta(c)}>
                  Editar e vincular
                </button>
              ) : (
                ""
              ),
            ])}
          />
        )}
        <p className="rd-note">
          O nome da categoria é o que você vê no Radar e pode usar no seu site; o vínculo diz em
          qual categoria do marketplace o anúncio entra.
        </p>
      </section>
      {aberta && (
        <CategoriaForm
          {...props}
          categoria={aberta === "nova" ? null : aberta}
          fechar={() => setAberta(null)}
        />
      )}
    </>
  );
}

function CategoriaForm({
  categoria,
  categoriaCanais,
  busy,
  executar,
  fechar,
}: Props & { categoria: Row | null; fechar: () => void }) {
  const [nome, setNome] = useState(str(categoria?.nome));
  const [descricao, setDescricao] = useState(str(categoria?.descricao));
  const inicial = Object.fromEntries(
    CANAIS.map((canal) => {
      const v = categoria ? vinculoDe(categoriaCanais, categoria.id, canal) : undefined;
      return [canal, { codigo: str(v?.codigo_externo), nome: str(v?.nome_externo) }];
    }),
  ) as Record<string, Vinculo>;
  const [vinculos, setVinculos] = useState(inicial);
  const [erro, setErro] = useState("");

  async function salvar() {
    setErro("");
    for (const canal of CANAIS) {
      const v = vinculos[canal];
      if (v.codigo.trim() && !v.nome.trim()) {
        setErro(`Informe também o nome da categoria no ${canal}.`);
        return;
      }
    }
    const r = await executar({
      op: categoria ? "categoria_atualizar" : "categoria",
      ...(categoria ? { id: categoria.id } : {}),
      nome,
      descricao,
    });
    if (!r) return;
    const id = categoria ? categoria.id : r.id;
    for (const canal of CANAIS) {
      const v = vinculos[canal];
      const antes = inicial[canal];
      if (v.codigo.trim() === antes.codigo && v.nome.trim() === antes.nome) continue;
      const ok = await executar({
        op: "categoria_vinculo",
        categoria_id: id,
        canal,
        codigo_externo: v.codigo.trim(),
        nome_externo: v.nome.trim(),
      });
      if (!ok) return;
    }
    fechar();
  }

  return (
    <div className="rd-modal-backdrop" onClick={() => !busy && fechar()}>
      <section
        className="rd-modal"
        role="dialog"
        aria-modal="true"
        aria-label="Categoria"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="rd-card-head">
          <h2>{categoria ? "Editar categoria" : "Nova categoria"}</h2>
          <button aria-label="Fechar" onClick={fechar}>
            ×
          </button>
        </div>
        <div className="rd-form-grid">
          <label className="wide">
            Nome da categoria no Radar *
            <input
              id="categoria-nome"
              maxLength={120}
              value={nome}
              onChange={(e) => setNome(e.target.value)}
            />
            <small className="rd-dica">
              Pode ser um nome seu, mais organizado. Não muda nada no marketplace.
            </small>
          </label>
          <label className="wide">
            Descrição
            <textarea
              rows={2}
              maxLength={500}
              value={descricao}
              onChange={(e) => setDescricao(e.target.value)}
            />
          </label>
          <h3 className="rd-secao wide">Categoria correspondente em cada marketplace</h3>
          <p className="rd-dica wide">
            Sem loja conectada, copie o código e o nome da categoria na central do vendedor. Quando
            a loja for conectada, você escolhe direto da lista do marketplace.
          </p>
          {CANAIS.map((canal) => (
            <div key={canal} className="wide rd-vinculo-canal">
              <strong>{canal}</strong>
              <input
                aria-label={`Código da categoria no ${canal}`}
                placeholder="Código (ex.: MLB1234)"
                maxLength={60}
                value={vinculos[canal].codigo}
                onChange={(e) =>
                  setVinculos({
                    ...vinculos,
                    [canal]: { ...vinculos[canal], codigo: e.target.value },
                  })
                }
              />
              <input
                aria-label={`Nome da categoria no ${canal}`}
                placeholder="Nome no marketplace (ex.: Casa > Cortinas)"
                maxLength={300}
                value={vinculos[canal].nome}
                onChange={(e) =>
                  setVinculos({
                    ...vinculos,
                    [canal]: { ...vinculos[canal], nome: e.target.value },
                  })
                }
              />
            </div>
          ))}
        </div>
        {erro && (
          <div className="rd-error" role="alert">
            {erro}
          </div>
        )}
        <div className="rd-modal-foot">
          <button onClick={fechar}>Cancelar</button>
          <button className="primary" disabled={busy || !nome.trim()} onClick={salvar}>
            {busy ? "Salvando…" : "Salvar categoria"}
          </button>
        </div>
      </section>
    </div>
  );
}
