// Marketplaces em que o Radar anuncia e as regras de anúncio de cada um. Lista única: as telas
// importam daqui. As mesmas regras estão no servidor (RadarAnuncios.REGRAS), que é quem trava;
// aqui a tela confere antes, para a lojista corrigir na hora.

import { str } from "./ui";

export const CANAIS = ["Mercado Livre", "Shopee", "TikTok Shop", "AliExpress"];

// Regras conferidas nas fontes oficiais em 07/10/2026 (docs/integracoes/regras-de-anuncio.md).
// null = sem número oficial: a tela não inventa limite. Vários limites mudam por categoria ou
// loja; esses o Radar confere com o marketplace quando a loja estiver conectada.
export type Regra = {
  tituloMin: number;
  tituloMax: number | null;
  imagensMin: number;
  descricaoMinPalavras: number | null;
  descricaoMax: number | null;
  precoMin: number | null;
  precoMax: number | null;
  estoqueMin: number | null;
  estoqueMax: number | null;
  /** Tamanho mínimo da foto, em pixels: no maior lado (ML) ou no menor lado (TikTok). */
  fotoMaiorLadoMin: number | null;
  fotoMenorLadoMin: number | null;
};

const LIVRE: Regra = {
  tituloMin: 1,
  tituloMax: null,
  imagensMin: 1,
  descricaoMinPalavras: null,
  descricaoMax: null,
  precoMin: null,
  precoMax: null,
  estoqueMin: null,
  estoqueMax: null,
  fotoMaiorLadoMin: null,
  fotoMenorLadoMin: null,
};

export const REGRAS: Record<string, Regra> = {
  // Estoque 0 só é aceito no Fulfillment: anúncio comum precisa de pelo menos 1.
  "Mercado Livre": {
    ...LIVRE,
    tituloMax: 60,
    descricaoMax: 50000,
    estoqueMin: 1,
    fotoMaiorLadoMin: 500,
  },
  // Shopee Brasil: título até 120 (ads.shopee.com.br, FAQ 363/1795). Descrição, preço e
  // estoque têm limite por loja, sem número público.
  Shopee: { ...LIVRE, tituloMax: 120 },
  // Política BR diz 25 a 200 letras (a API aceita até 300): vale a mais restrita.
  "TikTok Shop": {
    ...LIVRE,
    tituloMin: 25,
    tituloMax: 200,
    descricaoMinPalavras: 30,
    descricaoMax: 10000,
    precoMin: 0.5,
    precoMax: 10000,
    estoqueMin: 1,
    estoqueMax: 99999,
    fotoMenorLadoMin: 300,
  },
  AliExpress: { ...LIVRE, tituloMax: 128 },
};

export const regraDe = (marketplace: unknown) => REGRAS[str(marketplace)] ?? LIVRE;

/** Teto do próprio Radar para título (tamanho do campo), quando o marketplace não tem limite. */
export const TITULO_MAX_RADAR = 250;

/** Letras como a pessoa vê: emoji conta 1 (o servidor conta igual). */
export const letras = (texto: unknown) => [...str(texto).trim()].length;

export const palavras = (texto: unknown) => str(texto).trim().split(/\s+/).filter(Boolean).length;

// Central do vendedor de cada marketplace. A lojista entra com o próprio login. SHEIN fica para
// os anúncios antigos, de antes de ela sair da lista.
export const CENTRAL: Record<string, string> = {
  "Mercado Livre": "https://www.mercadolivre.com.br/anuncios",
  Shopee: "https://seller.shopee.com.br",
  "TikTok Shop": "https://seller-br.tiktok.com",
  SHEIN: "https://sellerhub.shein.com",
};

export const SIGLA: Record<string, string> = {
  "Mercado Livre": "ML",
  Shopee: "SP",
  "TikTok Shop": "TT",
  AliExpress: "AE",
  SHEIN: "SH",
};

/** Canais com anúncio salvo além dos da lista (ex.: SHEIN, de antes) continuam aparecendo. */
export function canaisCom(anuncios: { canal?: unknown }[]) {
  return [...new Set([...CANAIS, ...anuncios.map((a) => str(a.canal)).filter(Boolean)])];
}
