import type { BlocoMargem, StatusDevolucao, StatusPedido } from "./api/tipos";

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
