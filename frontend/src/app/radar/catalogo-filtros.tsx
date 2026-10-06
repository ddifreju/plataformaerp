// Configuração dos filtros de Categorias e Embalagens (componente em
// filtros-genericos.tsx).

import { CANAIS } from "./categorias";
import { TIPOS_EMBALAGEM } from "./embalagens";
import type { Config, Filtro } from "./filtros-genericos";
import { cents, str, type Row } from "./ui";

const data = (v: unknown) => new Date(str(v)).getTime() || 0;
const porNome = (a: Row, b: Row) => str(a.nome).localeCompare(str(b.nome), "pt-BR");
const lista = (v: unknown) => (Array.isArray(v) ? (v as string[]) : []);

export function configCategorias(produtos: Row[], categoriaCanais: Row[]): Config {
  const produtosDa = (id: unknown) =>
    produtos.filter((p) => p.categoria_id === id && !p.pai_id).length;
  const vinculos = (id: unknown) => categoriaCanais.filter((v) => v.categoria_id === id);
  const canaisCom = (id: unknown) => vinculos(id).map((v) => str(v.canal));
  const opcoesCanais = CANAIS.map((c): [string, string] => [c, c]);
  return {
    salvos: "radar.categorias.filtrosSalvos",
    plural: "categorias",
    placeholder: "Pesquise pelo nome, descrição ou categoria no marketplace",
    busca: (r) => [
      r.nome,
      r.descricao,
      ...vinculos(r.id).flatMap((v) => [v.nome_externo, v.codigo_externo]),
    ],
    ordens: [
      ["NOME", "nome", porNome],
      ["PRODUTOS", "mais produtos", (a, b) => produtosDa(b.id) - produtosDa(a.id)],
      ["VINCULOS", "menos vínculos", (a, b) => vinculos(a.id).length - vinculos(b.id).length],
      ["RECENTES", "mais recentes", (a, b) => data(b.criado_em) - data(a.criado_em)],
    ],
    campos: [
      {
        tipo: "multi",
        chave: "com_vinculo",
        rotulo: "Vinculada com",
        opcoes: opcoesCanais,
        valor: (r) => canaisCom(r.id),
      },
      {
        tipo: "multi",
        chave: "sem_vinculo",
        rotulo: "Sem vínculo com",
        opcoes: opcoesCanais,
        valor: (r) => CANAIS.filter((c) => !canaisCom(r.id).includes(c)),
      },
      {
        tipo: "marca",
        chave: "nenhum_vinculo",
        rotulo: "Sem vínculo com nenhum marketplace",
        teste: (r) => vinculos(r.id).length === 0,
      },
      {
        tipo: "marca",
        chave: "todos_vinculos",
        rotulo: "Vinculada com todos os marketplaces",
        teste: (r) => CANAIS.every((c) => canaisCom(r.id).includes(c)),
      },
      {
        tipo: "faixa",
        chave: "produtos",
        rotulo: "Produtos na categoria",
        unidade: "",
        valor: (r) => produtosDa(r.id),
      },
      {
        tipo: "multi",
        chave: "origem",
        rotulo: "Origem",
        opcoes: [
          ["MANUAL", "Criada no Radar"],
          ["IMPORTACAO", "Veio do marketplace"],
        ],
        valor: (r) => str(r.origem) || "MANUAL",
      },
      {
        tipo: "multi",
        chave: "situacao",
        rotulo: "Situação",
        opcoes: [
          ["ATIVA", "Ativa"],
          ["INATIVA", "Inativa"],
        ],
        valor: (r) => (r.ativo === false ? "INATIVA" : "ATIVA"),
      },
      {
        tipo: "marca",
        chave: "sem_descricao",
        rotulo: "Sem descrição",
        teste: (r) => !str(r.descricao),
      },
      {
        tipo: "data",
        chave: "criada",
        rotulo: "Criadas a partir de",
        valor: (r) => r.criado_em,
      },
    ],
    atalhos: [
      {
        chaves: ["sem vinculo", "nao vinculada", "falta vinculo"],
        rotulo: "Sem vínculo com nenhum marketplace",
        aplicar: (f) => ({ ...f, v: { ...f.v, nenhum_vinculo: true } }),
      },
      {
        chaves: ["vazia", "sem produto"],
        rotulo: "Sem produtos na categoria",
        aplicar: (f) => ({ ...f, v: { ...f.v, produtos: { de: "", ate: "0" } } }),
      },
      {
        chaves: ["com produto", "em uso"],
        rotulo: "Com produtos na categoria",
        aplicar: (f) => ({ ...f, v: { ...f.v, produtos: { de: "1", ate: "" } } }),
      },
    ],
  };
}

export function configEmbalagens(embalagens: Row[], produtos: Row[], veCusto: boolean): Config {
  const emUso = (id: unknown) => produtos.filter((p) => p.embalagem_id === id).length;
  const medidas = (r: Row): [number, number, number] | null =>
    r.comprimento_cm && r.largura_cm && r.altura_cm
      ? [Number(r.comprimento_cm), Number(r.largura_cm), Number(r.altura_cm)]
      : null;
  const volume = (r: Row) => {
    const m = medidas(r);
    return m ? m[0] * m[1] * m[2] : Number.MAX_SAFE_INTEGER;
  };
  const rotuloTipo = (t: unknown) => TIPOS_EMBALAGEM.find(([v]) => v === t)?.[1] ?? "";
  const tags = [...new Set(embalagens.flatMap((e) => lista(e.tags)))].sort((a, b) =>
    a.localeCompare(b, "pt-BR"),
  );
  return {
    salvos: "radar.embalagens.filtrosSalvos",
    plural: "embalagens",
    placeholder: "Pesquise pelo nome, tipo ou tag",
    busca: (r) => [r.nome, rotuloTipo(r.tipo), ...lista(r.tags)],
    ordens: [
      ["NOME", "nome", porNome],
      ["MENOR_VOLUME", "menor tamanho", (a, b) => volume(a) - volume(b)],
      ["MAIOR_VOLUME", "maior tamanho", (a, b) => volume(b) - volume(a)],
      ["PESO", "mais leve", (a, b) => Number(a.peso_g ?? 1e9) - Number(b.peso_g ?? 1e9)],
      ...(veCusto
        ? ([
            ["MENOR_CUSTO", "menor custo", (a: Row, b: Row) => cents(a.custo) - cents(b.custo)],
            ["MAIOR_CUSTO", "maior custo", (a: Row, b: Row) => cents(b.custo) - cents(a.custo)],
          ] as Config["ordens"])
        : []),
      ["USO", "mais usada", (a, b) => emUso(b.id) - emUso(a.id)],
    ],
    campos: [
      {
        tipo: "cabe",
        chave: "cabe",
        rotulo: "Cabe um produto de",
        valor: medidas,
      },
      {
        tipo: "multi",
        chave: "tipo",
        rotulo: "Tipo",
        opcoes: TIPOS_EMBALAGEM,
        valor: (r) => str(r.tipo) || "OUTRO",
      },
      {
        tipo: "multi",
        chave: "tags",
        rotulo: "Tags",
        opcoes: tags.map((t): [string, string] => [t, t]),
        valor: (r) => lista(r.tags),
      },
      {
        tipo: "faixa",
        chave: "comprimento",
        rotulo: "Comprimento (cm)",
        unidade: "",
        valor: (r) => (r.comprimento_cm ? Number(r.comprimento_cm) : null),
      },
      {
        tipo: "faixa",
        chave: "largura",
        rotulo: "Largura (cm)",
        unidade: "",
        valor: (r) => (r.largura_cm ? Number(r.largura_cm) : null),
      },
      {
        tipo: "faixa",
        chave: "altura",
        rotulo: "Altura (cm)",
        unidade: "",
        valor: (r) => (r.altura_cm ? Number(r.altura_cm) : null),
      },
      {
        tipo: "faixa",
        chave: "peso",
        rotulo: "Peso da embalagem (g)",
        unidade: "",
        valor: (r) => (r.peso_g == null ? null : Number(r.peso_g)),
      },
      ...(veCusto
        ? ([
            {
              tipo: "faixa",
              chave: "custo",
              rotulo: "Custo",
              unidade: "R$",
              valor: (r: Row) => cents(r.custo) / 100,
            },
            {
              tipo: "marca",
              chave: "sem_custo",
              rotulo: "Sem custo informado",
              teste: (r: Row) => cents(r.custo) === 0,
            },
          ] as Config["campos"])
        : []),
      {
        tipo: "multi",
        chave: "origem",
        rotulo: "Origem",
        opcoes: [
          ["SUGERIDA", "Sugerida pelo Radar"],
          ["PROPRIA", "Cadastrada por você"],
        ],
        valor: (r) => (r.sugerida === true ? "SUGERIDA" : "PROPRIA"),
      },
      {
        tipo: "marca",
        chave: "em_uso",
        rotulo: "Usada em algum produto",
        teste: (r) => emUso(r.id) > 0,
      },
      {
        tipo: "marca",
        chave: "sem_uso",
        rotulo: "Ainda não usada em produto",
        teste: (r) => emUso(r.id) === 0,
      },
      {
        tipo: "marca",
        chave: "sem_medidas",
        rotulo: "Sem medidas",
        teste: (r) => medidas(r) === null,
      },
    ],
    atalhos: [
      ...(veCusto
        ? [
            {
              chaves: ["sem custo", "custo zero"],
              rotulo: "Sem custo informado",
              aplicar: (f: Filtro) => ({
                ...f,
                v: { ...f.v, sem_custo: true },
              }),
            },
          ]
        : []),
      {
        chaves: ["nao usada", "sem uso", "parada"],
        rotulo: "Ainda não usada em produto",
        aplicar: (f) => ({ ...f, v: { ...f.v, sem_uso: true } }),
      },
    ],
  };
}
