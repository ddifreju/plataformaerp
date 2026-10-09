# Regras de anúncio por marketplace (vendedor no Brasil)

Conferidas em **07/10/2026**, só em fonte oficial: documentação de API,
Central ou Universidade do Vendedor e páginas de regras do marketplace.
"Sem fonte oficial" quer dizer que não há número público, e o Radar não
inventa um (regra 5). Onde está no código: `RadarAnuncios.REGRAS` (servidor)
e `frontend/src/app/radar/canais.ts` (tela). Decisão: 0036.

**Atenção:** no Mercado Livre, na Shopee e no TikTok vários limites mudam
por categoria ou por loja, e a API manda consultar em tempo real. Quando a
conexão existir, esses limites vêm do marketplace (ver decisão 0036).

## Mercado Livre (MLB)

| Regra | Valor | Fonte |
|---|---|---|
| Título | Máx. 60 (Imóveis: 200); por categoria em `max_title_length`. Mínimo sem número oficial | https://developers.mercadolivre.com.br/pt_br/publicacao-de-produtos e `api.mercadolibre.com/categories/{id}` |
| Imagens | Mín. 1; máx. 12 (com variações: 10). Foto com pelo menos 500 px num dos lados (erro 3703) | https://developers.mercadolivre.com.br/pt_br/trabalhar-com-imagens e https://developers.mercadolivre.com.br/pt_br/validacoes |
| Código de barras | Por categoria: `required` ou `conditional_required` (GTIN ou motivo). Obrigatório se a marca já tem 30+ GTINs publicados (erro 7810) | https://developers.mercadolivre.com.br/pt_br/identificadores-de-produtos |
| Peso e medidas | Sem fonte oficial que torne obrigatório | https://developers.mercadolivre.com.br/pt_br/itens-atributos-de-envio-e-dimensoes |
| Marca | Por categoria (`BRAND` com tag `required`); faltando, erro 147 | `/categories/{id}/attributes` |
| Descrição | Máx. 50.000 letras (erro 3707). Obrigatoriedade sem fonte oficial | `/categories/{id}` (`max_description_length`) |
| Preço | Mínimo por categoria (`minimum_price`): 0 na maioria; R$ 8 em Alimentos e Bebidas e em Cuidado da Casa | https://developers.mercadolivre.com.br/pt_br/validacoes (erro 109) |
| Quantidade | Obrigatória; 0 só no Fulfillment | https://developers.mercadolivre.com.br/pt_br/publicacao-de-produtos |

## Shopee Brasil

| Regra | Valor | Fonte |
|---|---|---|
| Título | Máx. 120 (Shopee Brasil). Mínimo sem número oficial. A API ainda devolve o limite da loja/categoria (`item_name_length_limit`), que vale quando a loja for conectada | https://ads.shopee.com.br/learn/faq/363/1795 e https://open.shopee.com/documents/v2/v2.product.get_item_limit?module=89&type=1 |
| Imagens | Mín. 1. Máximo por loja. Recomendado 5 fotos de 500x500 px | https://open.shopee.com/documents/v2/v2.product.add_item?module=89&type=1 |
| Código de barras | Obrigatório em eletrônicos, eletrodomésticos e suplementos; nas outras, conforme a categoria (`gtin_validation_rule`) | https://seller.shopee.com.br/edu/article/16213/aumente-sua-exposicao-com-o-GTIN-EAN |
| Peso | Obrigatório (kg, produto embalado) | add_item |
| Medidas | Por categoria (`dimension_mandatory`). Correios: comprimento 15–70, largura 10–70, altura 1–70 cm, soma 26–200 cm | get_item_limit e e-book oficial |
| Marca | Obrigatória | E-book oficial de cadastro, p. 19 |
| Descrição | Obrigatória; limites por loja | add_item e get_item_limit |
| Preço e quantidade | Sem número oficial (por loja) | get_item_limit |

## TikTok Shop Brasil

| Regra | Valor | Fonte |
|---|---|---|
| Título | 25 a 200 letras (política BR de 23/09/2026; a API aceita 1–300; vale a mais restrita) | https://seller-br.tiktok.com/university/essay?knowledge_id=6483182812759824&lang=pt-BR |
| Imagens | Mín. 1, máx. 9; quadradas, 300x300 a 4000x4000 px | Mesma política e https://partner.tiktokshop.com/docv2/page/create-product-202309 |
| Código de barras | Opcional; se enviado, EAN de 8, 13 ou 14 dígitos | create-product-202309 |
| Peso e medidas | Obrigatórios | create-product-202309 e política BR |
| Marca | Não obrigatória | create-product-202309 |
| Descrição | Obrigatória; mín. 30 palavras, máx. 10.000 letras | Política BR e API |
| Preço | R$ 0,50 a R$ 10.000 | https://partner.tiktokshop.com/docv2/page/67e1288d76cfee049d9af858 |
| Quantidade | 1 a 99.999 | create-product-202309 |

## AliExpress (comerciante local no Brasil)

| Regra | Valor | Fonte |
|---|---|---|
| Título | Máx. 128 (recomendado 79–128) | https://rule.aliexpress.com/rule-channels/40949687/206457749 (3.1.5) |
| Imagens | Até 6 principais; 1:1 (mín. 800x800) ou 3:4 (mín. 750x1000), até 5 MB | Mesma página (3.3) |
| Código de barras | Sem fonte oficial | — |
| Peso e medidas | Obrigatórios | Regras BR (3.6) |
| Marca | Obrigatória ("Outros" se não estiver na lista) | Regras BR (3.4.3) |
| Descrição | Obrigatória, com "Detalhes da mercadoria" e "Informações de pós-venda" | Regras BR (3.7) |
| Preço e quantidade | Sem fonte oficial de limite | — |

## O que o Radar já confere hoje (sem conexão)

- Cadastro completo (`RadarProdutos.pendencias`): origem, NCM, código de
  barras ou motivo, preço, marca, categoria, descrição, peso e medidas.
- Por marketplace: título (ML 60; Shopee 120; TikTok 25–200; AliExpress 128; sem mínimo oficial no ML, na Shopee e no AliExpress), pelo menos
  1 imagem, tamanho da foto (ML 500 px no maior lado; TikTok 300 px nos dois
  lados), descrição (TikTok 30 palavras e 10.000 letras; ML 50.000), preço
  (TikTok R$ 0,50 a R$ 10.000), quantidade (ML e TikTok pelo menos 1; TikTok
  até 99.999) e categoria ligada à do marketplace.
- Regras do próprio Radar (não são de marketplace): quantidade anunciada de pelo
  menos 1 em todos; título de até 250 letras quando o marketplace não tem limite
  oficial; emoji conta como 1 letra. Tamanho da foto conferido também no
  servidor (PNG e JPG; WEBP só na tela). Todos os problemas de um anúncio
  aparecem juntos.
- Ainda não confere (precisa da conexão): atributos obrigatórios de cada
  categoria, preço mínimo por categoria do ML, limites por loja da Shopee e a
  proporção das fotos do AliExpress.
