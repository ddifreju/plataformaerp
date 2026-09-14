/**
 * Tipos espelhando, campo a campo, os DTOs do backend. Nenhum campo
 * inventado, nenhum campo omitido — se o backend não devolve algo, o
 * tipo também não promete.
 *
 * Todo `BigDecimal` do backend (dinheiro, percentual) chega como `string`
 * — o `ConfiguracaoJackson` do backend garante isso na origem
 * (`toPlainString()`), então o "número" nesses campos é sempre o TEXTO
 * decimal exatamente como veio na resposta; nunca faça conta com eles,
 * use `lib/dinheiro.ts`. Contagens inteiras pequenas (`int`/`long` no
 * backend: `quantidade`, `tentativas`, `quantidadePedidos` etc.) NÃO são
 * `BigDecimal` e chegam como `number` de verdade — não têm risco de perda
 * de precisão nesse tamanho.
 *
 * Fonte de cada tipo, para conferência: `backend/src/main/java/com/plataforma/...`.
 */

// ---- autenticacao/RespostaSessao.java -------------------------------

export type PapelUsuario = "DONO" | "GESTOR" | "ANALISTA";

export interface RespostaSessao {
  email: string;
  nome: string;
  papel: PapelUsuario;
  tenantId: string;
  tenantNome: string;
}

// ---- margem: enums e memória de cálculo ------------------------------

export type RotuloTeto = "CALCULADA" | "COM_TETO" | "INDETERMINADA";

export type DirecaoViesLacuna = "SUPERESTIMA_MARGEM" | "SUBESTIMA_MARGEM" | "INDETERMINADA";

/**
 * Os oito blocos de apresentação (BlocoMargem.java). A ORDEM aqui importa
 * na tela — é a mesma ordem de `values()` no backend (B1 → B8), e o
 * backend já devolve `decomposicao` nessa ordem; este array serve para
 * quem precisar da ordem sem depender da resposta.
 */
export const ORDEM_BLOCOS_MARGEM = [
  "B1_DEDUCOES_RECEITA",
  "B2_CUSTO_MERCADORIA",
  "B3_CUSTOS_CANAL",
  "B4_CUSTOS_LOGISTICOS",
  "B5_CUSTOS_FINANCEIROS",
  "B6_IMPOSTO",
  "B7_MARKETING_ATRIBUIDO",
  "B8_OVERHEAD_ATRIBUIDO",
] as const;

export type BlocoMargem = (typeof ORDEM_BLOCOS_MARGEM)[number];

export interface Lacuna {
  codigo: string;
  descricao: string;
  direcaoVies: DirecaoViesLacuna;
}

/** margem/MemoriaCalculoBlocoPeriodo.java — decomposição agregada do período. */
export interface MemoriaCalculoBlocoPeriodo {
  bloco: BlocoMargem;
  valor: string;
  contemEstimativa: boolean;
}

// ---- margem/RequisicaoMargemPeriodo.java (corpo de POST /api/margem/periodo, decisão 0034) ----

export interface RequisicaoMargemPeriodo {
  inicio: string;
  fim: string;
  canalId: string;
}

// ---- margem/RespostaMargemPeriodo.java -------------------------------

export interface RespostaMargemPeriodo {
  canalId: string;
  inicio: string;
  fim: string;
  escopoCanal: string;
  faturamentoBrutoN0: string;
  receitaLiquidaN1: string;
  margemContribuicaoN2: string;
  resultadoPeriodoN3: string;
  lucroOperacionalN4: string;
  /** Optional<BigDecimal> do backend: ausente (faturamento zero) vira `null`, nunca 0. */
  margemContribuicaoPercentual: string | null;
  margemLiquidaPercentual: string | null;
  decomposicao: MemoriaCalculoBlocoPeriodo[];
  lacunas: Lacuna[];
  rotulo: RotuloTeto;
  /** `int` no backend (não `BigDecimal`) — chega como `number` de verdade. */
  quantidadePedidos: number;
  idsPedidoUsados: string[];
  idsCustoUsados: string[];
}

// ---- painel/RespostaGargalosProcesso.java (visão "Operação") --------

export type StatusPedido =
  | "AGUARDANDO_PAGAMENTO"
  | "PAGAMENTO_RECUSADO"
  | "PAGO"
  | "EM_SEPARACAO"
  | "ENVIADO"
  | "ENTREGUE"
  | "CANCELADO"
  | "DEVOLVIDO";

export interface ContagemPorStatusPedido {
  status: StatusPedido;
  /** `long` no backend (não `BigDecimal`) — chega como `number` de verdade. */
  quantidade: number;
}

export type StatusDevolucao =
  | "ABERTA"
  | "EM_ANALISE"
  | "EM_MEDIACAO"
  | "APROVADA"
  | "RECUSADA"
  | "EM_TRANSITO"
  | "RECEBIDA"
  | "CONCLUIDA"
  | "CANCELADA";

export interface ContagemPorStatusDevolucao {
  status: StatusDevolucao;
  /** `long` no backend (não `BigDecimal`) — chega como `number` de verdade. */
  quantidade: number;
}

export interface RespostaGargalosProcesso {
  pedidosPorStatus: ContagemPorStatusPedido[];
  devolucoesPorStatus: ContagemPorStatusDevolucao[];
  /** `long` no backend (não `BigDecimal`) — chega como `number` de verdade. */
  eventosIngestaoComErro: number;
  /** `long` no backend (não `BigDecimal`) — chega como `number` de verdade. */
  pedidosSemCustoMercadoria: number;
}

// ---- painel/RespostaFilaPendencias.java (visão "Pendências") --------

export interface ItemEventoComErro {
  id: string;
  canalId: string;
  tipoEvento: string;
  idExterno: string;
  erroMensagem: string | null;
  recebidoEm: string;
  /** `int` no backend (não `BigDecimal`) — chega como `number` de verdade. */
  tentativas: number;
  acao: string;
}

export interface ItemPedidoSemVariacao {
  id: string;
  pedidoId: string;
  skuOrigem: string | null;
  tituloOrigem: string | null;
  criadoEm: string;
  acao: string;
}

export interface ItemVariacaoSemCusto {
  id: string;
  sku: string;
  descricaoVariacao: string | null;
  criadoEm: string;
  acao: string;
}

export interface ItemDevolucaoAberta {
  id: string;
  pedidoId: string;
  status: string;
  motivo: string | null;
  abertaEm: string;
  acao: string;
}

export interface RespostaFilaPendencias {
  eventosComErro: ItemEventoComErro[];
  itensSemVariacao: ItemPedidoSemVariacao[];
  variacoesSemCusto: ItemVariacaoSemCusto[];
  devolucoesAbertas: ItemDevolucaoAberta[];
}

// ---- comum/web/ErroApi.java -------------------------------------------

export interface ErroApi {
  erro: string;
  mensagem: string;
}

// ---- canal/RespostaCanal.java -----------------------------------------

export interface RespostaCanal {
  id: string;
  codigo: string;
  nome: string;
  tipo: string;
  categoria: string;
  ativo: boolean;
}

// ---- pergunta: camada de IA (decisão 0030) -----------------------------

/** RequisicaoPergunta.java — corpo de `POST /api/pergunta`. */
export interface RequisicaoPergunta {
  texto: string;
}

/**
 * TipoResposta.java — os três caminhos de primeira classe da decisão 0030.
 * Nenhum é erro HTTP: os três chegam como `200 OK`.
 */
export type TipoResposta = "RESPOSTA" | "ESCLARECIMENTO" | "RECUSA";

/**
 * NumeroCitado.java — `valor` já chega FORMATADO PARA EXIBIÇÃO
 * (`FormatadorDeTexto.moeda`/`percentual`, ou uma contagem simples como
 * `"12"`), não um decimal cru como em `RespostaMargemPeriodo`. NUNCA passe
 * este campo por `ValorMonetario`/`formatarDinheiro` — o texto já pode
 * conter `"R$"`, `","` e `"%"`, o que quebraria o parser de decimal deles;
 * e mesmo que não quebrasse, reformatar por cima de um formato que a
 * origem já fechou é o que a decisão 0026 proíbe.
 */
export interface NumeroCitado {
  nome: string;
  valor: string;
}

/**
 * RespostaPergunta.java — o contrato inteiro da camada de IA. `lacunas`
 * aqui é `string[]` (só a descrição já pronta, via
 * `Lacuna::descricao`) — diferente do `Lacuna[]` completo (com `codigo` e
 * `direcaoVies`) de `RespostaMargemPeriodo`. `rotuloConfianca` só vem
 * preenchido quando a intenção é `MARGEM_DO_PERIODO`/`LACUNAS_DA_MARGEM`;
 * `intencao` é `null` em `RECUSA` (nenhuma intenção do catálogo casou).
 */
export interface RespostaPergunta {
  tipo: TipoResposta;
  texto: string;
  numeros: NumeroCitado[];
  rotuloConfianca: RotuloTeto | null;
  lacunas: string[];
  consultaAuditadaId: string;
  intencao: string | null;
  parametrosUsados: Record<string, string>;
  perguntasQueSeiResponder: string[];
}
