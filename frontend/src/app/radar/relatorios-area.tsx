"use client";

// Área de Relatórios: os prontos de cada área, os salvos da empresa ("Meus relatórios") e o
// "Montar relatório". Cada pronto é só uma configuração do montador e aparece se o cargo tem a
// fonte e as colunas de que ele precisa. Os atalhos "Relatórios" no fim de cada menu abrem esta
// mesma área já na aba daquela parte.

import { useState } from "react";
import { ResumoDoPeriodo } from "./relatorios";
import MontarRelatorio, { configVazia, type Config, type Fonte } from "./relatorios-montar";
import { Empty } from "./ui";

export type Area = "cadastros" | "vendas" | "suprimentos" | "financas";

const AREAS: [Area | "todos" | "meus", string][] = [
  ["todos", "Todos"],
  ["cadastros", "Cadastros"],
  ["vendas", "Vendas"],
  ["suprimentos", "Suprimentos"],
  ["financas", "Finanças"],
  ["meus", "Meus relatórios"],
];

type Pronto = {
  id: string;
  areas: Area[];
  titulo: string;
  descricao: string;
  config?: Partial<Config> & { fonte: string };
  /** Colunas sem as quais o relatório não faz sentido (ex.: lucro, só para o financeiro). */
  precisa?: string[];
};

const SEM_CANCELADOS = [{ coluna: "situacao", op: "diferente", valor: "Cancelado, Devolvido" }];

const PRONTOS: Pronto[] = [
  {
    id: "incompletos",
    areas: ["cadastros"],
    titulo: "Produtos com cadastro incompleto",
    descricao: "O que falta completar antes de anunciar e emitir nota.",
    config: {
      fonte: "produtos",
      colunas: ["sku", "produto", "categoria", "situacao", "cadastro"],
      filtros: [{ coluna: "cadastro", op: "igual", valor: "Incompleto" }],
    },
  },
  {
    id: "sem-venda",
    areas: ["cadastros", "suprimentos"],
    titulo: "Produtos sem venda no período",
    descricao: "Estoque parado: o que não vendeu nos últimos 90 dias.",
    config: {
      fonte: "produtos",
      dias: 90,
      colunas: [
        "sku",
        "produto",
        "categoria",
        "disponivel",
        "valor_estoque",
        "vendidos",
        "ultima_venda",
      ],
      filtros: [{ coluna: "vendidos", op: "max", valor: "0" }],
    },
  },
  {
    id: "produtos-categoria",
    areas: ["cadastros"],
    titulo: "Produtos por categoria",
    descricao: "Estoque, valor parado e vendas de cada categoria.",
    config: {
      fonte: "produtos",
      colunas: ["categoria", "disponivel", "valor_estoque", "vendidos", "receita_periodo"],
      agrupar: "categoria",
    },
  },
  {
    id: "anuncios-loja",
    areas: ["cadastros", "vendas"],
    titulo: "Anúncios por loja",
    descricao: "Quantos anúncios cada loja tem e a quantidade anunciada.",
    config: { fonte: "anuncios", colunas: ["loja", "marketplace", "quantidade"], agrupar: "loja" },
  },
  {
    id: "resumo",
    areas: ["vendas", "financas"],
    titulo: "Resumo do período e curva ABC",
    descricao: "Receita, resultado, por canal, por categoria, curva ABC e clientes.",
  },
  {
    id: "vendas-dia",
    areas: ["vendas"],
    titulo: "Vendas por dia",
    descricao: "Unidades, receita e lucro de cada dia.",
    config: {
      fonte: "pedidos",
      colunas: ["data", "quantidade", "receita_bruta", "resultado", "margem"],
      agrupar: "data",
      filtros: SEM_CANCELADOS,
      ordenar: { coluna: "data", desc: true },
    },
  },
  {
    id: "vendas-marketplace",
    areas: ["vendas", "financas"],
    titulo: "Vendas por marketplace",
    descricao: "Receita, custos e margem de cada marketplace.",
    config: {
      fonte: "pedidos",
      colunas: [
        "marketplace",
        "quantidade",
        "receita_bruta",
        "comissao",
        "frete",
        "resultado",
        "margem",
      ],
      agrupar: "marketplace",
      filtros: SEM_CANCELADOS,
      ordenar: { coluna: "receita_bruta", desc: true },
    },
  },
  {
    id: "vendas-produto",
    areas: ["vendas"],
    titulo: "Vendas por produto",
    descricao: "Os produtos que mais vendem e os que mais dão lucro.",
    config: {
      fonte: "pedidos",
      colunas: ["produto", "quantidade", "receita_bruta", "resultado", "margem"],
      agrupar: "produto",
      filtros: SEM_CANCELADOS,
      ordenar: { coluna: "receita_bruta", desc: true },
    },
  },
  {
    id: "vendas-cliente",
    areas: ["vendas"],
    titulo: "Vendas por cliente",
    descricao: "Quem mais compra e quanto.",
    config: {
      fonte: "pedidos",
      colunas: ["cliente", "quantidade", "receita_bruta"],
      agrupar: "cliente",
      filtros: SEM_CANCELADOS,
      ordenar: { coluna: "receita_bruta", desc: true },
    },
  },
  {
    id: "cancelamentos",
    areas: ["vendas"],
    titulo: "Cancelamentos e devoluções",
    descricao: "Pedidos cancelados ou devolvidos, com produto e marketplace.",
    config: {
      fonte: "pedidos",
      colunas: ["numero", "data", "marketplace", "situacao", "cliente", "produto", "receita_bruta"],
      filtros: [{ coluna: "situacao", op: "igual", valor: "Cancelado, Devolvido" }],
    },
  },
  {
    id: "atendimento-canal",
    areas: ["vendas"],
    titulo: "Atendimento por canal",
    descricao: "Quantas conversas chegaram por canal.",
    config: { fonte: "atendimento", colunas: ["canal"], agrupar: "canal" },
  },
  {
    id: "estoque-atual",
    areas: ["suprimentos"],
    titulo: "Estoque atual e valor",
    descricao: "Disponível, reservado, mínimo e valor de cada produto.",
    config: {
      fonte: "produtos",
      colunas: ["sku", "produto", "fisico", "reservado", "disponivel", "minimo", "valor_estoque"],
      ordenar: { coluna: "disponivel", desc: false },
    },
  },
  {
    id: "abaixo-minimo",
    areas: ["suprimentos"],
    titulo: "Abaixo do estoque mínimo",
    descricao: "O que precisa de reposição.",
    config: {
      fonte: "produtos",
      colunas: ["sku", "produto", "disponivel", "minimo", "vendidos"],
      filtros: [{ coluna: "abaixo_minimo", op: "igual", valor: "Sim" }],
    },
  },
  {
    id: "compras-fornecedor",
    areas: ["suprimentos", "financas"],
    titulo: "Compras por fornecedor",
    descricao: "Quanto foi comprado de cada fornecedor nos últimos 90 dias.",
    config: {
      fonte: "compras",
      dias: 90,
      colunas: ["fornecedor", "quantidade", "total"],
      agrupar: "fornecedor",
    },
  },
  {
    id: "compras-pendentes",
    areas: ["suprimentos"],
    titulo: "Compras ainda não recebidas",
    descricao: "Pedidos ao fornecedor que ainda não chegaram.",
    config: {
      fonte: "compras",
      dias: 365,
      colunas: ["data", "produto", "fornecedor", "quantidade", "total", "situacao"],
      filtros: [{ coluna: "situacao", op: "igual", valor: "Pendente" }],
    },
  },
  {
    id: "movimentos",
    areas: ["suprimentos"],
    titulo: "Movimentos de estoque",
    descricao: "Entradas, saídas, reservas e devoluções, com o pedido.",
    config: {
      fonte: "movimentos",
      colunas: ["data", "sku", "produto", "tipo", "quantidade", "reserva", "pedido"],
    },
  },
  {
    id: "resultado-pedido",
    areas: ["financas"],
    titulo: "Resultado por pedido",
    descricao: "Receita, cada custo, lucro e margem de cada pedido.",
    precisa: ["resultado"],
    config: {
      fonte: "pedidos",
      colunas: [
        "numero",
        "data",
        "marketplace",
        "receita_bruta",
        "comissao",
        "frete",
        "imposto",
        "custo_produtos",
        "resultado",
        "margem",
      ],
    },
  },
  {
    id: "lucro-categoria",
    areas: ["financas"],
    titulo: "Lucro por categoria",
    descricao: "Quais categorias dão mais lucro.",
    precisa: ["resultado"],
    config: {
      fonte: "pedidos",
      colunas: ["categoria", "receita_bruta", "custo_produtos", "resultado", "margem"],
      agrupar: "categoria",
      filtros: SEM_CANCELADOS,
      ordenar: { coluna: "resultado", desc: true },
    },
  },
  {
    id: "contas",
    areas: ["financas"],
    titulo: "Contas a pagar e a receber",
    descricao: "Dos últimos 30 dias e dos próximos 60.",
    config: {
      fonte: "contas",
      dias: 30,
      adiante: 60,
      colunas: ["descricao", "tipo", "valor", "vencimento", "situacao", "pedido"],
      ordenar: { coluna: "vencimento", desc: false },
    },
  },
  {
    id: "contas-atrasadas",
    areas: ["financas"],
    titulo: "Contas atrasadas",
    descricao: "Vencidas e ainda em aberto.",
    config: {
      fonte: "contas",
      dias: 365,
      colunas: ["descricao", "tipo", "valor", "vencimento", "pedido"],
      filtros: [{ coluna: "situacao", op: "igual", valor: "Atrasado" }],
    },
  },
  {
    id: "razao",
    areas: ["financas"],
    titulo: "Lançamentos por tipo (razão)",
    descricao: "Receitas, custos e despesas do período, somados por tipo.",
    config: { fonte: "lancamentos", colunas: ["tipo", "valor"], agrupar: "tipo" },
  },
];

export type Salvo = { id: string; nome: string; config: Config; autor: string };

type Props = {
  area?: Area;
  catalogo: Fonte[];
  salvos: Salvo[];
  financeiro: boolean;
  usuarioId: string;
  apagaQualquer: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
};

export default function AreaRelatorios(props: Props) {
  const { catalogo } = props;
  const [aba, setAba] = useState<Area | "todos" | "meus">(props.area ?? "todos");
  const [aberto, setAberto] = useState<{ titulo: string; config: Config | null } | null>(null);
  const [apagando, setApagando] = useState<string | null>(null);

  const fonte = (nome: unknown) => catalogo.find((f) => f.nome === nome);
  const disponivel = (p: Pronto) => {
    if (!p.config) return props.financeiro;
    const f = fonte(p.config.fonte);
    return !!f && (p.precisa ?? []).every((id) => f.colunas.some((c) => c.id === id));
  };
  const configDe = (p: Pronto): Config => ({
    ...configVazia(fonte(p.config!.fonte)!),
    ...p.config,
  });
  const visiveis = PRONTOS.filter(
    (p) => disponivel(p) && (aba === "todos" || (aba !== "meus" && p.areas.includes(aba))),
  );

  if (aberto)
    return (
      <>
        <button className="text rd-voltar" onClick={() => setAberto(null)}>
          ← Voltar aos relatórios
        </button>
        {aberto.config ? (
          <MontarRelatorio
            key={aberto.titulo}
            catalogo={catalogo}
            inicial={aberto.config}
            titulo={aberto.titulo}
            salvar={(nome, config) => props.executar({ op: "relatorio_salvar", nome, config })}
          />
        ) : (
          <ResumoDoPeriodo />
        )}
      </>
    );

  return (
    <div className="rd-relatorios">
      <div className="rd-relatorios-topo">
        <div className="rd-tabs" role="tablist" aria-label="Áreas dos relatórios">
          {AREAS.map(([id, rotulo]) => (
            <button
              key={id}
              role="tab"
              aria-selected={aba === id}
              className={aba === id ? "active" : ""}
              onClick={() => setAba(id)}
            >
              {rotulo}
              {id === "meus" && props.salvos.length > 0 && ` (${props.salvos.length})`}
            </button>
          ))}
        </div>
        {catalogo.length > 0 && (
          <button
            className="primary"
            onClick={() =>
              setAberto({
                titulo: "Novo relatório",
                config: { ...configVazia(catalogo[0]), colunas: [] },
              })
            }
          >
            + Montar relatório
          </button>
        )}
      </div>
      {aba === "meus" ? (
        props.salvos.length ? (
          <div className="rd-relatorios-grade">
            {props.salvos.map((s) => {
              const podeApagar = props.apagaQualquer || s.autor === props.usuarioId;
              const f = fonte(s.config?.fonte);
              return (
                <article key={s.id} className="rd-card">
                  <h3>{s.nome}</h3>
                  <p className="rd-dica">{f?.rotulo ?? "Fonte que o seu cargo não usa"}</p>
                  <div className="rd-actions">
                    <button
                      className="primary"
                      disabled={!f}
                      onClick={() => setAberto({ titulo: s.nome, config: s.config })}
                    >
                      Abrir
                    </button>
                    {podeApagar &&
                      (apagando === s.id ? (
                        <button
                          className="perigo"
                          onClick={async () => {
                            await props.executar({ op: "relatorio_excluir", id: s.id });
                            setApagando(null);
                          }}
                        >
                          Confirmar
                        </button>
                      ) : (
                        <button onClick={() => setApagando(s.id)}>Apagar</button>
                      ))}
                  </div>
                </article>
              );
            })}
          </div>
        ) : (
          <Empty text="Nenhum relatório salvo ainda. Monte um e clique em “Salvar relatório”." />
        )
      ) : (
        <div className="rd-relatorios-grade">
          {visiveis.map((p) => (
            <article key={p.id} className="rd-card">
              <h3>{p.titulo}</h3>
              <p className="rd-dica">{p.descricao}</p>
              <button
                className="primary"
                onClick={() =>
                  setAberto({ titulo: p.titulo, config: p.config ? configDe(p) : null })
                }
              >
                Abrir
              </button>
            </article>
          ))}
          {!visiveis.length && (
            <Empty text="Nenhum relatório pronto para o seu cargo nesta área." />
          )}
        </div>
      )}
    </div>
  );
}
