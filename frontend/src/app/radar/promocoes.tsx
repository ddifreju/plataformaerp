"use client";
/* eslint-disable react/jsx-key -- Table wraps each supplied cell in a keyed td; these arrays are table data, not rendered sibling lists. */

// Promoções: período, alvo (produto e canal) e o efeito na margem antes de
// valer. O desconto só entra num pedido quando a promoção é escolhida nele.

import {
  Badge,
  Empty,
  Table,
  cents,
  centMoney,
  str,
  type Field,
  type ModalSpec,
  type Row,
} from "./ui";

const CANAIS = ["Mercado Livre", "Shopee", "TikTok Shop", "SHEIN"];

type Props = {
  promocoes: Row[];
  produtos: Row[];
  podeEditar: boolean;
  veCusto: boolean;
  abrirModal: (m: ModalSpec) => void;
};

export function hojeIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function situacao(p: Row): { rotulo: string; tom: string; vale: boolean } {
  const hoje = hojeIso();
  const inicio = str(p.inicio).slice(0, 10);
  const fim = str(p.fim).slice(0, 10);
  if (!p.ativo) return { rotulo: "Encerrada", tom: "gray", vale: false };
  if (hoje < inicio) return { rotulo: "Agendada", tom: "blue", vale: false };
  if (hoje > fim) return { rotulo: "Vencida", tom: "gray", vale: false };
  return { rotulo: "Ativa", tom: "green", vale: true };
}

// Preço unitário depois do desconto, em centavos. Só para exibição; o
// desconto que vale no pedido é calculado no servidor.
function precoPromocional(p: Row, precoCentavos: number) {
  if (p.tipo === "PERCENTUAL")
    return precoCentavos - Math.round((precoCentavos * cents(p.valor)) / 10000);
  return precoCentavos - cents(p.valor);
}

function descricao(p: Row) {
  return p.tipo === "PERCENTUAL"
    ? `${str(p.valor).replace(".", ",")}% de desconto`
    : `${centMoney(cents(p.valor))} por unidade`;
}

function dataBr(v: unknown) {
  const [a, m, d] = str(v).slice(0, 10).split("-");
  return d ? `${d}/${m}/${a}` : "—";
}

export default function Promocoes({ promocoes, produtos, podeEditar, veCusto, abrirModal }: Props) {
  const produtoDe = (id: unknown) => produtos.find((x) => x.id === id);

  function campos(p?: Row): Field[] {
    return [
      { key: "nome", label: "Nome da promoção", value: str(p?.nome) },
      {
        key: "tipo",
        label: "Tipo de desconto",
        value: str(p?.tipo) || "PERCENTUAL",
        options: [
          { value: "PERCENTUAL", label: "Percentual (%)" },
          { value: "VALOR_FIXO", label: "Valor fixo por unidade (R$)" },
        ],
      },
      { key: "valor", label: "Desconto", type: "number", value: str(p?.valor) },
      {
        key: "produto_id",
        label: "Produto",
        required: false,
        value: str(p?.produto_id),
        options: [
          { value: "", label: "Todos os produtos" },
          ...produtos.map((x) => ({ value: str(x.id), label: `${x.sku} · ${x.nome}` })),
        ],
      },
      {
        key: "canal",
        label: "Canal",
        required: false,
        value: str(p?.canal),
        options: [
          { value: "", label: "Todos os canais" },
          ...CANAIS.map((c) => ({ value: c, label: c })),
        ],
      },
      {
        key: "inicio",
        label: "Começa em",
        type: "date",
        value: str(p?.inicio).slice(0, 10) || hojeIso(),
      },
      {
        key: "fim",
        label: "Termina em",
        type: "date",
        value: str(p?.fim).slice(0, 10) || hojeIso(),
      },
    ];
  }

  function efeito(p: Row) {
    const produto = produtoDe(p.produto_id);
    if (!produto) return "Vale para todos os produtos";
    const preco = cents(produto.preco);
    const promo = precoPromocional(p, preco);
    if (!veCusto) return `${centMoney(preco)} → ${centMoney(promo)}`;
    const custo = cents(produto.custo);
    const margem = promo > 0 ? (((promo - custo) / promo) * 100).toFixed(1).replace(".", ",") : "—";
    return (
      <span>
        {centMoney(preco)} → <strong>{centMoney(promo)}</strong>
        <small>
          {" "}
          · margem bruta {margem}%{promo < custo ? " · abaixo do custo" : ""}
        </small>
      </span>
    );
  }

  return (
    <>
      <div className="rd-toolbar">
        <Badge>Desconto aplicado só quando escolhido no pedido</Badge>
        {podeEditar && (
          <button
            className="primary"
            onClick={() => abrirModal({ title: "Nova promoção", op: "promocao", fields: campos() })}
          >
            + Nova promoção
          </button>
        )}
      </div>
      <section className="rd-card">
        {promocoes.length ? (
          <Table
            headers={[
              "Promoção",
              "Desconto",
              "Vale para",
              "Período",
              "Preço e margem",
              "Situação",
              "",
            ]}
            rows={promocoes.map((p) => {
              const s = situacao(p);
              const produto = produtoDe(p.produto_id);
              return [
                <strong>{str(p.nome)}</strong>,
                descricao(p),
                `${produto ? str(produto.sku) : "Todos os produtos"} · ${str(p.canal) || "todos os canais"}`,
                `${dataBr(p.inicio)} a ${dataBr(p.fim)}`,
                efeito(p),
                <Badge tone={s.tom}>{s.rotulo}</Badge>,
                podeEditar && p.ativo ? (
                  <span className="rd-row-actions">
                    <button
                      onClick={() =>
                        abrirModal({
                          title: "Editar promoção",
                          op: "promocao_atualizar",
                          extra: { id: p.id },
                          fields: campos(p),
                        })
                      }
                    >
                      Editar
                    </button>
                    <button
                      onClick={() =>
                        abrirModal({
                          title: `Encerrar “${str(p.nome)}”? Ela deixa de aparecer nos pedidos.`,
                          op: "promocao_encerrar",
                          extra: { id: p.id },
                          fields: [],
                        })
                      }
                    >
                      Encerrar
                    </button>
                  </span>
                ) : (
                  ""
                ),
              ];
            })}
          />
        ) : (
          <Empty text="Nenhuma promoção criada ainda." />
        )}
      </section>
    </>
  );
}
