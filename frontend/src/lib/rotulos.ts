import type { BlocoMargem, CategoriaCanal, StatusDevolucao, StatusPedido, TipoCanal } from "./api/tipos";

/**
 * Rótulos em português para os enums do backend. Nenhum destes textos
 * está no guia de interface (que fala dos 4 números e 3 rótulos de
 * confiança, não da decomposição por bloco nem dos status de processo) —
 * são nomes descritivos diretos, sem adjetivo, seguindo o tom de voz da
 * seção 4 do guia ("número primeiro, adjetivo nunca").
 */

export const ROTULO_BLOCO_MARGEM: Record<BlocoMargem, string> = {
  B1_DEDUCOES_RECEITA: "Devolução e desconto",
  B2_CUSTO_MERCADORIA: "Custo da mercadoria",
  B3_CUSTOS_CANAL: "Comissão e tarifa do canal",
  B4_CUSTOS_LOGISTICOS: "Frete e embalagem",
  B5_CUSTOS_FINANCEIROS: "Taxa de pagamento, parcelamento e antecipação",
  B6_IMPOSTO: "Imposto",
  B7_MARKETING_ATRIBUIDO: "Ads rateado",
  B8_OVERHEAD_ATRIBUIDO: "Armazenagem e outros custos rateados",
};

export const ROTULO_STATUS_PEDIDO: Record<StatusPedido, string> = {
  AGUARDANDO_PAGAMENTO: "Aguardando pagamento",
  PAGAMENTO_RECUSADO: "Pagamento recusado",
  PAGO: "Pago",
  EM_SEPARACAO: "Em separação",
  ENVIADO: "Enviado",
  ENTREGUE: "Entregue",
  CANCELADO: "Cancelado",
  DEVOLVIDO: "Devolvido",
};

export const ROTULO_STATUS_DEVOLUCAO: Record<StatusDevolucao, string> = {
  ABERTA: "Aberta",
  EM_ANALISE: "Em análise",
  EM_MEDIACAO: "Em mediação",
  APROVADA: "Aprovada",
  RECUSADA: "Recusada",
  EM_TRANSITO: "Em trânsito",
  RECEBIDA: "Recebida",
  CONCLUIDA: "Concluída",
  CANCELADA: "Cancelada",
};

/**
 * `CodigoIntencao.java` (catálogo fechado da decisão 0030), em texto para
 * a área "Como cheguei nesse número" da tela Perguntar (tarefa 26) — a
 * lojista não deveria precisar reconhecer o nome de uma constante Java
 * para entender qual pergunta o sistema respondeu.
 */
export const ROTULO_CODIGO_INTENCAO: Record<string, string> = {
  MARGEM_DO_PERIODO: "Margem do período (quanto sobrou, por canal e período)",
  LACUNAS_DA_MARGEM: "O que falta para calcular a margem com confiança",
  GARGALOS_DA_OPERACAO: "Onde a operação está travando",
  FILA_DE_PENDENCIAS: "O que precisa ser resolvido",
  CANAIS_DISPONIVEIS: "Canais cadastrados",
};

/** TipoCanal.java, em texto (tela "Canais", tarefa 34) — nome que a lojista reconhece, não a constante Java. */
export const ROTULO_TIPO_CANAL: Record<TipoCanal, string> = {
  MERCADO_LIVRE: "Mercado Livre",
  SHOPEE: "Shopee",
  AMAZON: "Amazon",
  MAGALU: "Magalu",
  AMERICANAS: "Americanas",
  SHOPIFY: "Shopify",
  NUVEMSHOP: "Nuvemshop",
  WOOCOMMERCE: "WooCommerce",
  LOJA_PROPRIA: "Loja própria",
  ERP_BLING: "Bling",
  ERP_TINY: "Tiny",
  WHATSAPP: "WhatsApp",
  EMAIL: "E-mail",
  INSTAGRAM: "Instagram",
  OUTRO: "Outro",
};

/** CategoriaCanal.java, em texto — o papel que o canal cumpre, não o sistema por trás dele. */
export const ROTULO_CATEGORIA_CANAL: Record<CategoriaCanal, string> = {
  MARKETPLACE: "Marketplace",
  LOJA_PROPRIA: "Loja própria",
  ERP: "ERP",
  COMUNICACAO: "Comunicação",
};

/** Chaves de `RespostaPergunta.parametrosUsados`, em texto — mesmo motivo do mapa acima. */
export const ROTULO_PARAMETRO_PERGUNTA: Record<string, string> = {
  canal: "Canal",
  periodoInicio: "Início do período",
  periodoFim: "Fim do período",
  periodoRelativo: "Período (expressão reconhecida)",
};
