"use client";
/* eslint-disable react/jsx-key -- Table wraps each supplied cell in a keyed td; these arrays are table data, not rendered sibling lists. */

// Inteligência de Mercado: interpreta sinais que o próprio usuário registra
// (referências e preços observados). Nada aqui coleta dados de terceiros;
// o que depende de fonte externa aparece como "fonte não conectada", nunca
// como número estimado apresentado como fato (regra 5 do CLAUDE.md).

import { CANAIS } from "./canais";
import { useState, type ReactNode } from "react";
import { Badge, Empty, Table, cents, centMoney, date, str, type ModalSpec, type Row } from "./ui";

const ABAS = [
  ["visao", "Visão do mercado"],
  ["referencias", "Referências monitoradas"],
  ["precos", "Preços e ofertas"],
  ["posicionamento", "Posicionamento"],
  ["demanda", "Palavras-chave e demanda"],
  ["sinais", "Sinais e oportunidades"],
  ["alertas", "Alertas de mercado"],
] as const;

type Aba = (typeof ABAS)[number][0];

type Referencia = {
  id: string;
  nome: string;
  url: string;
  canal: string;
  produto?: Row;
  precoAtual: number | null; // centavos; null quando nenhum preço foi informado
  observadoEm: string | null;
  meuPreco: number | null; // centavos
  origemMeuPreco: string;
};

type Props = {
  registros: Row[];
  produtos: Row[];
  anuncios: Row[];
  podeEditar: boolean;
  veCusto: boolean;
  abrirModal: (m: ModalSpec) => void;
  go: (pagina: string) => void;
};

// Margem bruta em pontos-base (1% = 100): (preço - custo) / preço.
function margemBp(preco: number, custo: number) {
  return preco > 0 ? Math.round(((preco - custo) * 10000) / preco) : null;
}

function pct(bp: number | null) {
  return bp === null ? "—" : `${(bp / 100).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`;
}

// Menor preço (em centavos, arredondado para cima) que mantém a margem-alvo.
function precoMinimo(custo: number, alvoBp: number) {
  return alvoBp >= 10000 ? null : Math.ceil((custo * 10000) / (10000 - alvoBp));
}

function FonteNaoConectada({ titulo, texto }: { titulo: string; texto: string }) {
  return (
    <section className="rd-card rd-mercado-fonte">
      <Badge>Fonte não conectada</Badge>
      <h2>{titulo}</h2>
      <p>{texto}</p>
      <p className="rd-note">
        O Radar não mostra estimativas como se fossem dados. Esta área é preenchida quando a fonte
        for integrada.
      </p>
    </section>
  );
}

export default function Mercado({
  registros,
  produtos,
  anuncios,
  podeEditar,
  veCusto,
  abrirModal,
  go,
}: Props) {
  const [aba, setAba] = useState<Aba>("visao");
  const [alvoPct, setAlvoPct] = useState("12");
  const [ignorados, setIgnorados] = useState<string[]>([]);

  const observacoes = registros.filter((r) => r.tipo === "PRECO_REFERENCIA");
  const dadosDe = (r: Row) => (r.dados ?? {}) as Record<string, unknown>;

  const referencias: Referencia[] = registros
    .filter((r) => r.tipo === "CONCORRENTE")
    .map((r) => {
      const d = dadosDe(r);
      const id = str(r.id);
      const produto = produtos.find((p) => p.id === d.produto_id);
      const canal = str(d.canal);
      // Registros chegam do mais novo para o mais antigo.
      const ultima = observacoes.find((o) => dadosDe(o).referencia_id === id);
      const precoInformado = ultima ? dadosDe(ultima).preco : d.preco;
      const anuncio = anuncios.find((a) => a.produto_id === d.produto_id && a.canal === canal);
      return {
        id,
        nome: str(d.nome),
        url: str(d.url),
        canal,
        produto,
        precoAtual: str(precoInformado) ? cents(precoInformado) : null,
        observadoEm: ultima ? str(ultima.criado_em) : str(r.criado_em),
        meuPreco: anuncio ? cents(anuncio.preco) : produto ? cents(produto.preco) : null,
        origemMeuPreco: anuncio ? `seu anúncio em ${canal}` : "preço base do produto",
      };
    });

  const alvoBp = Math.round(Number(alvoPct.replace(",", ".")) * 100);
  const alvoValido = Number.isFinite(alvoBp) && alvoBp >= 0 && alvoBp < 10000;

  const sinais = referencias.filter(
    (r) =>
      r.produto &&
      r.precoAtual !== null &&
      r.meuPreco !== null &&
      r.precoAtual < r.meuPreco &&
      !ignorados.includes(r.id),
  );

  function adicionarReferencia() {
    abrirModal({
      title: "Adicionar referência",
      op: "registro",
      extra: { tipo: "CONCORRENTE" },
      fields: [
        { key: "nome", label: "Nome da referência (anúncio, loja ou produto)" },
        { key: "url", label: "Link HTTPS do anúncio", type: "url" },
        {
          key: "canal",
          label: "Canal",
          options: CANAIS.map((c) => ({ value: c, label: c })),
        },
        {
          key: "produto_id",
          label: "Seu produto comparável",
          required: false,
          options: [
            { value: "", label: "Nenhum por enquanto" },
            ...produtos.map((p) => ({ value: str(p.id), label: `${p.sku} · ${p.nome}` })),
          ],
        },
        { key: "preco", label: "Preço observado agora", type: "number", required: false },
        {
          key: "observacao",
          label: "Frete, condições ou observações",
          type: "textarea",
          required: false,
        },
      ],
    });
  }

  function registrarPreco(r: Referencia) {
    abrirModal({
      title: `Registrar preço · ${r.nome}`,
      op: "registro",
      extra: { tipo: "PRECO_REFERENCIA" },
      fields: [
        { key: "referencia_id", label: "Referência", options: [{ value: r.id, label: r.nome }] },
        { key: "preco", label: "Preço observado", type: "number" },
        {
          key: "condicao",
          label: "Promoção, frete ou condição (opcional)",
          required: false,
        },
      ],
    });
  }

  const conteudo: Record<Aba, ReactNode> = {
    visao: (
      <>
        <div className="rd-kpis">
          {[
            ["Referências monitoradas", referencias.length],
            ["Preços registrados", observacoes.length],
            ["Sinais abertos", sinais.length],
          ].map(([rotulo, valor]) => (
            <div className="rd-kpi" key={rotulo}>
              <span>{rotulo}</span>
              <strong>{valor}</strong>
            </div>
          ))}
        </div>
        <FonteNaoConectada
          titulo="Tendências, categorias e produtos em crescimento"
          texto="A visão ampla do mercado depende de dados dos marketplaces (buscas, vendas por categoria, ranking). Enquanto essa fonte não estiver conectada, o Radar trabalha com as referências que você escolhe acompanhar."
        />
      </>
    ),
    referencias: referencias.length ? (
      <section className="rd-card">
        <Table
          headers={["Referência", "Canal", "Seu produto", "Último preço", "Atualizado", ""]}
          rows={referencias.map((r) => [
            <span>
              <strong>{r.nome}</strong>
              {r.url && (
                <>
                  <br />
                  <a href={r.url} target="_blank" rel="noopener noreferrer">
                    Abrir anúncio ↗
                  </a>
                </>
              )}
            </span>,
            r.canal || "—",
            r.produto ? `${str(r.produto.sku)} · ${str(r.produto.nome)}` : "Não vinculado",
            r.precoAtual === null ? "Não informado" : centMoney(r.precoAtual),
            r.observadoEm ? date(r.observadoEm) : "—",
            podeEditar ? <button onClick={() => registrarPreco(r)}>Registrar preço</button> : "",
          ])}
        />
      </section>
    ) : (
      <Empty
        text="Nenhuma referência monitorada ainda."
        action={
          podeEditar && (
            <button className="primary" onClick={adicionarReferencia}>
              + Adicionar referência
            </button>
          )
        }
      />
    ),
    precos: (
      <section className="rd-card">
        <Table
          headers={["Quando", "Referência", "Preço", "Condição"]}
          rows={observacoes.map((o) => {
            const d = dadosDe(o);
            const ref = referencias.find((r) => r.id === d.referencia_id);
            return [
              date(o.criado_em),
              ref?.nome ?? "—",
              centMoney(cents(d.preco)),
              str(d.condicao) || "—",
            ];
          })}
        />
      </section>
    ),
    posicionamento: (
      <>
        <section className="rd-card">
          <Table
            headers={["Referência", "Preço da referência", "Seu preço", "Diferença"]}
            rows={referencias
              .filter((r) => r.produto)
              .map((r) => [
                r.nome,
                r.precoAtual === null ? "Não informado" : centMoney(r.precoAtual),
                r.meuPreco === null ? "—" : `${centMoney(r.meuPreco)} (${r.origemMeuPreco})`,
                r.precoAtual === null || r.meuPreco === null
                  ? "—"
                  : centMoney(r.meuPreco - r.precoAtual),
              ])}
          />
        </section>
        <FonteNaoConectada
          titulo="Título, avaliações e reputação"
          texto="A comparação de conteúdo do anúncio, nota, número de avaliações e reputação do vendedor exige leitura do marketplace. Hoje comparamos apenas o preço que você registrou."
        />
      </>
    ),
    demanda: (
      <FonteNaoConectada
        titulo="Palavras-chave e demanda"
        texto="Volume de busca e termos mais procurados vêm das ferramentas dos canais. Esta área fica disponível quando os canais que oferecem esses dados forem conectados."
      />
    ),
    sinais: (
      <>
        <section className="rd-card rd-mercado-alvo">
          <label>
            Margem mínima desejada (%)
            <input
              id="mercado-margem-alvo"
              inputMode="decimal"
              value={alvoPct}
              onChange={(e) => setAlvoPct(e.target.value)}
            />
          </label>
          <p className="rd-note">
            Margem bruta = preço menos o custo do produto. Não inclui comissão do canal, frete e
            impostos; use a Precificação para a conta completa.
          </p>
        </section>
        {!veCusto ? (
          <Empty text="Seu cargo não tem acesso ao custo dos produtos, necessário para calcular a margem." />
        ) : sinais.length ? (
          sinais.map((r) => {
            const custo = cents(r.produto!.custo);
            const atual = margemBp(r.meuPreco!, custo);
            const igualando = margemBp(r.precoAtual!, custo);
            const minimo = alvoValido ? precoMinimo(custo, alvoBp) : null;
            return (
              <section key={r.id} className="rd-card rd-sinal">
                <Badge tone="amber">Sinal de preço</Badge>
                <h3>
                  O preço de “{r.nome}” está em {centMoney(r.precoAtual!)}.
                </h3>
                <p>
                  Para igualar esse valor, sua margem bruta em {str(r.produto!.nome)} cairia de{" "}
                  <strong>{pct(atual)}</strong> para <strong>{pct(igualando)}</strong>.
                </p>
                {minimo !== null && (
                  <p>
                    Seu preço mínimo para manter a margem de {alvoPct}% é{" "}
                    <strong>{centMoney(minimo)}</strong>.
                  </p>
                )}
                <small>
                  Seu preço considerado: {centMoney(r.meuPreco!)} ({r.origemMeuPreco}). Preço da
                  referência registrado em{" "}
                  {r.observadoEm ? date(r.observadoEm) : "data não informada"}.
                </small>
                <div className="rd-actions">
                  <button className="primary" onClick={() => go("precos")}>
                    Simular preço
                  </button>
                  <button onClick={() => go("anuncios")}>Ver meu anúncio</button>
                  <button onClick={() => setIgnorados([...ignorados, r.id])}>Ignorar sinal</button>
                </div>
              </section>
            );
          })
        ) : (
          <Empty text="Nenhum sinal no momento. Sinais aparecem quando uma referência vinculada a um produto fica abaixo do seu preço." />
        )}
      </>
    ),
    alertas: (
      <>
        <section className="rd-card">
          <Table
            headers={["Quando", "Referência", "O que mudou"]}
            rows={referencias.flatMap((r) => {
              const historico = observacoes.filter((o) => dadosDe(o).referencia_id === r.id);
              // historico[0] é o mais recente; compara cada preço com o anterior.
              return historico.slice(0, -1).flatMap((o, i) => {
                const agora = cents(dadosDe(o).preco);
                const antes = cents(dadosDe(historico[i + 1]).preco);
                if (agora === antes) return [];
                return [
                  [
                    date(o.criado_em),
                    r.nome,
                    `Preço ${agora < antes ? "caiu" : "subiu"} de ${centMoney(antes)} para ${centMoney(agora)}`,
                  ],
                ];
              });
            })}
          />
        </section>
        <p className="rd-note">
          Alertas são gerados a partir dos preços que você registra. O monitoramento automático das
          referências depende da conexão com os canais.
        </p>
      </>
    ),
  };

  return (
    <>
      <div className="rd-toolbar">
        <Badge>Sinais registrados por você · sem coleta automática</Badge>
        {podeEditar && (
          <button className="primary" onClick={adicionarReferencia}>
            + Adicionar referência
          </button>
        )}
      </div>
      <div className="rd-tabs" role="tablist" aria-label="Áreas de Inteligência de Mercado">
        {ABAS.map(([id, rotulo]) => (
          <button
            key={id}
            role="tab"
            aria-selected={aba === id}
            className={aba === id ? "active" : ""}
            onClick={() => setAba(id)}
          >
            {rotulo}
          </button>
        ))}
      </div>
      {conteudo[aba]}
    </>
  );
}
