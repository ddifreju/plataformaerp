"use client";

// Pendências do cadastro de produtos: agrupa os produtos pelo que falta
// ("23 sem NCM", "10 sem peso"…) e abre uma grade de preenchimento rápido com
// foto, SKU, nome e só o campo que falta, já travado no formato certo. Cada
// linha salva sozinha ao sair do campo.

import { useState } from "react";
import { cents, str, type Row } from "./ui";

type Props = {
  produtos: Row[];
  imagens: Row[];
  categorias: Row[];
  fornecedores: Row[];
  produtoFornecedores: Row[];
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  recarregar: () => Promise<void>;
  marcarParaLote: (ids: string[]) => void;
};

type Chave =
  | "ncm"
  | "origem"
  | "marca"
  | "categoria"
  | "descricao"
  | "preco"
  | "peso"
  | "medidas"
  | "gtin"
  | "imagem"
  | "fornecedor";

const ORIGENS: [string, string][] = [
  ["0", "0 - Nacional"],
  ["1", "1 - Estrangeira, importação direta"],
  ["2", "2 - Estrangeira, mercado interno"],
  ["3", "3 - Nacional, importação acima de 40%"],
  ["4", "4 - Nacional, processos produtivos básicos"],
  ["5", "5 - Nacional, importação até 40%"],
  ["6", "6 - Estrangeira direta, sem similar nacional"],
  ["7", "7 - Estrangeira interna, sem similar nacional"],
  ["8", "8 - Nacional, importação acima de 70%"],
];
const MOTIVOS: [string, string][] = [
  ["PRODUTO_ARTESANAL", "Produto artesanal ou sob medida"],
  ["KIT_DA_LOJA", "Kit montado pela loja"],
  ["SEM_CODIGO_DO_FABRICANTE", "O fabricante não fornece código"],
  ["OUTRO", "Outro motivo"],
];

export default function Pendencias(props: Props) {
  const [aberta, setAberta] = useState<{ chave: Chave; ids: string[] } | null>(null);
  const principais = props.produtos.filter((p) => !p.pai_id);
  const temImagem = (id: unknown) => props.imagens.some((i) => i.produto_id === id);
  const temFornecedor = (id: unknown) => props.produtoFornecedores.some((f) => f.produto_id === id);

  // Mesmas regras do servidor (nota fiscal + marketplaces), mais imagem e fornecedor.
  const regras: [Chave, string, (p: Row) => boolean][] = [
    ["ncm", "sem NCM", (p) => !str(p.ncm)],
    ["origem", "sem origem (ICMS)", (p) => p.origem == null || str(p.origem) === ""],
    ["gtin", "sem código de barras nem motivo", (p) => !p.gtin && !p.motivo_sem_gtin],
    ["marca", "sem marca", (p) => !str(p.marca).trim()],
    ["categoria", "sem categoria", (p) => !p.categoria_id],
    ["descricao", "sem descrição", (p) => !str(p.descricao).trim()],
    ["preco", "sem preço", (p) => !(cents(p.preco) > 0)],
    ["peso", "sem peso bruto", (p) => p.peso_bruto_kg == null || str(p.peso_bruto_kg) === ""],
    [
      "medidas",
      "sem medidas nem embalagem",
      (p) => !p.embalagem_id && (!p.largura_cm || !p.altura_cm || !p.comprimento_cm),
    ],
    ["imagem", "sem imagem", (p) => !temImagem(p.id)],
    ["fornecedor", "sem fornecedor", (p) => !temFornecedor(p.id)],
  ];
  const grupos = regras
    .map(([chave, rotulo, teste]) => ({ chave, rotulo, lista: principais.filter(teste) }))
    .filter((g) => g.lista.length > 0);

  if (aberta) {
    const grupo = regras.find(([c]) => c === aberta.chave)!;
    const linhas = principais.filter((p) => aberta.ids.includes(str(p.id)));
    const faltando = linhas.filter(grupo[2]).length;
    return (
      <section className="rd-card rd-preenchimento">
        <div className="rd-card-head">
          <h2>
            Completar: {aberta.ids.length} produto(s) {grupo[1]}
            <small> · faltam {faltando}</small>
          </h2>
          <button
            onClick={() => {
              props.marcarParaLote(aberta.ids);
              setAberta(null);
            }}
          >
            Marcar todos para editar em massa
          </button>
          <button className="primary" onClick={() => setAberta(null)}>
            Concluir
          </button>
        </div>
        <p className="rd-note">
          Cada linha salva sozinha quando você sai do campo. O produto continua na lista até você
          concluir, para conferir.
        </p>
        <div className="rd-preencher-lista">
          {linhas.map((p) => (
            <LinhaPreencher key={str(p.id)} produto={p} chave={aberta.chave} {...props} />
          ))}
        </div>
      </section>
    );
  }

  if (!grupos.length)
    return (
      <div className="rd-pendencias rd-pendencias-ok">
        ✓ Nenhuma pendência no cadastro dos produtos.
      </div>
    );
  return (
    <div className="rd-pendencias">
      <strong>Pendências do cadastro</strong>
      <span className="rd-dica">Clique para completar de uma vez:</span>
      <div className="rd-pendencias-chips">
        {grupos.map((g) => (
          <button
            key={g.chave}
            onClick={() => setAberta({ chave: g.chave, ids: g.lista.map((p) => str(p.id)) })}
          >
            <b>{g.lista.length}</b> {g.rotulo}
          </button>
        ))}
      </div>
    </div>
  );
}

function LinhaPreencher({
  produto,
  chave,
  imagens,
  categorias,
  fornecedores,
  produtoFornecedores,
  executar,
  recarregar,
}: Props & { produto: Row; chave: Chave }) {
  const id = str(produto.id);
  const capa = imagens.find((i) => i.produto_id === produto.id);
  const inicial = (k: string) => (produto[k] == null ? "" : str(produto[k]));
  const [v, setV] = useState<Record<string, string>>({
    ncm: inicial("ncm"),
    origem: inicial("origem"),
    marca: inicial("marca"),
    categoria_id: inicial("categoria_id"),
    descricao: inicial("descricao"),
    preco: cents(produto.preco) > 0 ? inicial("preco") : "",
    peso_bruto_kg: inicial("peso_bruto_kg"),
    largura_cm: inicial("largura_cm"),
    altura_cm: inicial("altura_cm"),
    comprimento_cm: inicial("comprimento_cm"),
    gtin: inicial("gtin"),
    motivo_sem_gtin: inicial("motivo_sem_gtin"),
    fornecedor_id: str(produtoFornecedores.find((f) => f.produto_id === produto.id)?.fornecedor_id),
  });
  const [salvo, setSalvo] = useState<Record<string, string>>({});
  const [estado, setEstado] = useState<"" | "salvando" | "ok" | "erro">("");
  const [dica, setDica] = useState("");

  async function salvar(campo: string, valor: string, valido = true, mensagem = "") {
    if (!valido) {
      setEstado("erro");
      setDica(mensagem);
      return;
    }
    if (valor === "" || valor === (salvo[campo] ?? inicial(campo))) return;
    setEstado("salvando");
    setDica("");
    const ok = await executar({
      op: "produtos_lote",
      acao: "PREENCHER",
      ids: [id],
      campo,
      valor: valor.replace(",", "."),
    });
    setEstado(ok ? "ok" : "erro");
    if (ok) setSalvo((s) => ({ ...s, [campo]: valor }));
  }

  const set = (k: string, filtro?: (t: string) => string) => (e: { target: { value: string } }) =>
    setV((a) => ({ ...a, [k]: filtro ? filtro(e.target.value) : e.target.value }));
  const digitos = (max: number) => (t: string) => t.replace(/\D/g, "").slice(0, max);
  const decimal = (t: string) => t.replace(/[^0-9.,]/g, "");

  async function enviarImagem(arquivos: FileList | null) {
    if (!arquivos?.length) return;
    const arquivo = arquivos[0];
    if (arquivo.size > 2 * 1024 * 1024) {
      setEstado("erro");
      setDica("A imagem passa de 2 MB.");
      return;
    }
    setEstado("salvando");
    const form = new FormData();
    form.append("arquivo", arquivo);
    const res = await fetch(`/api/radar/produtos/${id}/imagens`, {
      method: "POST",
      credentials: "include",
      headers: { "X-Radar-Request": "1" },
      body: form,
    });
    setEstado(res.ok ? "ok" : "erro");
    if (!res.ok) setDica("Envie JPG, PNG ou WEBP de até 2 MB.");
    await recarregar();
  }

  let campo: React.ReactNode;
  switch (chave) {
    case "ncm":
      campo = (
        <input
          aria-label={`NCM de ${str(produto.nome)}`}
          inputMode="numeric"
          placeholder="8 dígitos"
          value={v.ncm}
          onChange={set("ncm", digitos(8))}
          onBlur={() => salvar("ncm", v.ncm, v.ncm.length === 8, "O NCM tem 8 dígitos.")}
        />
      );
      break;
    case "origem":
      campo = (
        <select
          aria-label={`Origem de ${str(produto.nome)}`}
          value={v.origem}
          onChange={(e) => {
            setV((a) => ({ ...a, origem: e.target.value }));
            salvar("origem", e.target.value);
          }}
        >
          <option value="">Escolha</option>
          {ORIGENS.map(([o, r]) => (
            <option key={o} value={o}>
              {r}
            </option>
          ))}
        </select>
      );
      break;
    case "marca":
      campo = (
        <input
          aria-label={`Marca de ${str(produto.nome)}`}
          maxLength={120}
          value={v.marca}
          onChange={set("marca")}
          onBlur={() => salvar("marca", v.marca.trim())}
        />
      );
      break;
    case "categoria":
      campo = (
        <select
          aria-label={`Categoria de ${str(produto.nome)}`}
          value={v.categoria_id}
          onChange={(e) => {
            setV((a) => ({ ...a, categoria_id: e.target.value }));
            salvar("categoria_id", e.target.value);
          }}
        >
          <option value="">Escolha</option>
          {categorias.map((c) => (
            <option key={str(c.id)} value={str(c.id)}>
              {str(c.nome)}
            </option>
          ))}
        </select>
      );
      break;
    case "descricao":
      campo = (
        <textarea
          aria-label={`Descrição de ${str(produto.nome)}`}
          rows={2}
          value={v.descricao}
          onChange={set("descricao")}
          onBlur={() => salvar("descricao", v.descricao.trim())}
        />
      );
      break;
    case "preco":
      campo = (
        <input
          aria-label={`Preço de ${str(produto.nome)}`}
          inputMode="decimal"
          placeholder="R$ 0,00"
          value={v.preco}
          onChange={set("preco", decimal)}
          onBlur={() => salvar("preco", v.preco)}
        />
      );
      break;
    case "peso":
      campo = (
        <input
          aria-label={`Peso bruto de ${str(produto.nome)}`}
          inputMode="decimal"
          placeholder="kg (ex.: 1,250)"
          value={v.peso_bruto_kg}
          onChange={set("peso_bruto_kg", decimal)}
          onBlur={() => salvar("peso_bruto_kg", v.peso_bruto_kg)}
        />
      );
      break;
    case "medidas":
      campo = (
        <div className="rd-preencher-medidas">
          {(
            [
              ["largura_cm", "Largura"],
              ["altura_cm", "Altura"],
              ["comprimento_cm", "Comprimento"],
            ] as const
          ).map(([k, r]) => (
            <input
              key={k}
              aria-label={`${r} de ${str(produto.nome)}`}
              inputMode="decimal"
              placeholder={`${r} (cm)`}
              value={v[k]}
              onChange={set(k, decimal)}
              onBlur={() => salvar(k, v[k])}
            />
          ))}
        </div>
      );
      break;
    case "gtin":
      campo = (
        <div className="rd-preencher-medidas">
          <input
            aria-label={`Código de barras de ${str(produto.nome)}`}
            inputMode="numeric"
            placeholder="GTIN/EAN (8, 12, 13 ou 14 dígitos)"
            value={v.gtin}
            onChange={set("gtin", digitos(14))}
            onBlur={() =>
              v.gtin &&
              salvar(
                "gtin",
                v.gtin,
                [8, 12, 13, 14].includes(v.gtin.length),
                "O código de barras tem 8, 12, 13 ou 14 dígitos.",
              )
            }
          />
          <select
            aria-label={`Motivo sem código de ${str(produto.nome)}`}
            value={v.motivo_sem_gtin}
            onChange={(e) => {
              setV((a) => ({ ...a, motivo_sem_gtin: e.target.value }));
              salvar("motivo_sem_gtin", e.target.value);
            }}
          >
            <option value="">…ou o motivo de não ter</option>
            {MOTIVOS.map(([m, r]) => (
              <option key={m} value={m}>
                {r}
              </option>
            ))}
          </select>
        </div>
      );
      break;
    case "imagem":
      campo = (
        <input
          type="file"
          aria-label={`Imagem de ${str(produto.nome)}`}
          accept="image/jpeg,image/png,image/webp"
          onChange={(e) => {
            enviarImagem(e.target.files);
            e.target.value = "";
          }}
        />
      );
      break;
    case "fornecedor":
      campo = (
        <select
          aria-label={`Fornecedor de ${str(produto.nome)}`}
          value={v.fornecedor_id}
          onChange={(e) => {
            setV((a) => ({ ...a, fornecedor_id: e.target.value }));
            salvar("fornecedor_id", e.target.value);
          }}
        >
          <option value="">
            {fornecedores.length ? "Escolha" : "Cadastre fornecedores antes"}
          </option>
          {fornecedores.map((f) => (
            <option key={str(f.id)} value={str(f.id)}>
              {str(f.codigo)} · {str(f.nome)}
            </option>
          ))}
        </select>
      );
      break;
  }

  return (
    <div className={`rd-preencher-linha ${estado}`}>
      {capa ? (
        // eslint-disable-next-line @next/next/no-img-element -- imagem servida pela API autenticada
        <img className="rd-product-thumb" src={`/api/radar/imagens/${str(capa.id)}`} alt="" />
      ) : (
        <span className="rd-product-thumb">▥</span>
      )}
      <div className="rd-preencher-nome">
        <strong>{str(produto.nome)}</strong>
        <small>SKU {str(produto.sku)}</small>
      </div>
      <div className="rd-preencher-campo">{campo}</div>
      <span className="rd-preencher-estado" aria-live="polite">
        {estado === "salvando"
          ? "Salvando…"
          : estado === "ok"
            ? "✓ Salvo"
            : estado === "erro"
              ? "⚠"
              : ""}
        {dica && <small>{dica}</small>}
      </span>
    </div>
  );
}
