# Mapeamento — Mercado Livre → modelo canônico

**Data desta versão:** 12 de agosto de 2026
**Adaptador correspondente:** ainda não escrito (esta rodada é só fixture + mapeamento,
por instrução explícita — outro agente está criando as entidades JPA em paralelo)
**Última tentativa de validação contra a API real:** 12/08/2026, **sem sucesso** (ver seção
"Status de verificação" abaixo). Ainda não há credencial (`docs/PENDENCIAS.md`).

---

## Status de verificação — leia antes de confiar em qualquer campo abaixo

Por instrução da tarefa, tentei confirmar a estrutura contra a documentação oficial
(developers.mercadolivre.com.br) usando WebFetch/WebSearch nesta sessão. **Todas as
tentativas falharam**:

| URL tentada | Resultado |
|---|---|
| `developers.mercadolivre.com.br/pt_br/gestao-de-vendas` | HTTP 403 |
| `developers.mercadolivre.com.br/en_us/manage-orders` | HTTP 403 |
| `developers.mercadolibre.com/en_us/order-management-resource` | HTTP 403 |
| `api.mercadolibre.com/orders/{id}` (sem token) | HTTP 403 |
| Busca Google/GitHub/StackOverflow por trechos de payload real | bloqueado ou sem resultado utilizável |

O site da Mercado Livre bloqueia o agente de busca automatizado (SPA + anti-bot). **Não
consegui abrir nenhuma página da documentação oficial nesta sessão.**

**Consequência, por honestidade (regra 5 do CLAUDE.md — nunca inventar dado apresentado
como fato):** a estrutura usada nas fixtures abaixo é **reconstruída do conhecimento geral,
publicamente documentado e amplamente replicado, sobre a API de Orders do Mercado Livre**
(nomes de campo como `order_items`, `sale_fee`, `full_unit_price`, `shipping.id`,
`payments[].installments` são estáveis há anos e aparecem em qualquer integração real desse
tipo) — **não é uma cópia de payload real capturado agora**. Isto é uma hipótese, no mesmo
sentido que a decisão 0013 já registrou para o Bling: "a fixture é hipótese até um payload
real confirmá-la". Marquei abaixo, campo a campo onde relevante, o nível de confiança:

- 🟢 **Alta confiança** — estrutura estável, documentada há anos, presente em qualquer SDK/wrapper conhecido.
- 🟡 **Confiança média** — a existência do campo é conhecida, mas o nome exato ou o formato pode ter mudado.
- 🔴 **Baixa confiança / reconstrução deliberada** — a API do Mercado Livre não expõe isso de forma direta no recurso citado, e a fixture representa uma composição plausível, não um payload confirmado.

**Ação pendente registrada em `docs/PENDENCIAS.md` (a acrescentar pelo gerente-projeto):**
quando a conta de desenvolvedor Mercado Livre existir, a primeira coisa a fazer é capturar
um pedido real e comparar campo a campo com este documento — não só com as fixtures.

---

## Fixtures gravadas

- `backend/src/test/resources/fixtures/mercadolivre/pedido-completo.json` — 🟢/🟡. Pedido
  pago, 2 itens, cupom de desconto, `sale_fee` por item, 3x no cartão.
- `backend/src/test/resources/fixtures/mercadolivre/envio-detalhe.json` — 🟡. **Fixture
  extra, não pedida na lista original de 5** — ver "Armadilhas de dinheiro" abaixo: o custo
  de frete NÃO está no recurso de pedido, está em `/shipments/{shipping.id}`, e sem essa
  fixture a lacuna ficaria invisível.
- `backend/src/test/resources/fixtures/mercadolivre/pedido-cancelado.json` — 🟢 estrutura,
  🔴 `cancel_detail` (nomes de campo reconstruídos, baixa confiança — ver tabela de status).
- `backend/src/test/resources/fixtures/mercadolivre/pedido-com-devolucao.json` — 🔴. O
  Mercado Livre **não representa devolução dentro do pedido**: é o recurso de "reclamações/
  mediação" (`post-purchase claims`), separado, referenciando o pedido por `resource_id`.
  Esta é a fixture de menor confiança de toda a entrega — a API de claims mudou de versão
  (v1 → v2) nos últimos anos e não consegui confirmar o formato atual.

---

## Tabela de mapeamento — `canal` (configuração, não payload de pedido)

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `seller.id` (do pedido) | `canal.id_externo` | É o `seller_id` da conta — normalmente configurado uma vez, não lido de cada pedido. |
| `site_id` (ex.: `"MLB"`) | `canal.dados_origem.site_id` | País/site do Mercado Livre. Não é canônico (não existe coluna própria); fica em extensão. |

## Tabela de mapeamento — `cliente` (de `buyer`)

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `buyer.id` | `cliente.id_externo` | 🟢. Chave de idempotência com `canal_id`. |
| `buyer.nickname` | `cliente.apelido_origem` | 🟢. **Isto, e não `nome`**, é o que o ML normalmente entrega. |
| `buyer.first_name`, `buyer.last_name` | `cliente.nome` | 🟡. Na prática quase sempre `null` fora do fluxo de emissão de nota pelo vendedor. Regra 5: se vier `null`, `cliente.nome` fica `NULL` — não inventar a partir do nickname. |
| `buyer.email` | `cliente.email` | 🟡. O ML historicamente mascara/omite o e-mail do comprador na maior parte do ciclo do pedido. Esperar `null` com frequência. |
| `buyer.phone.*` | `cliente.telefone` | 🟡. Composto (`area_code`+`number`) quando presente; formatar E.164 no adaptador. Frequentemente vazio. |
| `buyer.billing_info.doc_type` / `doc_number` | `cliente.documento_tipo` / `cliente.documento_hash` | 🔴. Só aparece quando há emissão de nota associada ao pedido pelo próprio ML, caso raro na Fase 1. Quando vier, o adaptador aplica HMAC (V007) — nunca grava em claro. |
| `canal_id` do pedido | `cliente.canal_id` | Origem é sempre o canal do pedido, nunca outro. |

## Tabela de mapeamento — `pedido`

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `id` | `pedido.id_externo` | 🟢. Convertido para `text` (o ML manda número, mas outras fontes mandam alfanumérico — `id_externo` é sempre text no canônico). |
| `id` | `pedido.codigo_exibicao` | 🟡. Mesmo número visível ao comprador; não há campo separado de "número de exibição" confirmado no order resource. |
| `status` | `pedido.status_origem` | 🟢. Palavra crua preservada (`"paid"`, `"cancelled"` etc.), conforme convenção 4/regra 5. |
| `status` (+ `shipping`/`tags`, ver nota) | `pedido.status` | 🔴 **ver "canônico exige e a fonte não fornece" abaixo — o `status` do pedido no ML NÃO avança para "enviado"/"entregue"; isso vem de outro recurso.** |
| `date_created` | `pedido.feito_em` | 🟢. |
| `payments[].date_approved` (do pagamento aprovado) | `pedido.pago_em` | 🟡. Quando há mais de um pagamento (parcela recusada e reenviada), qual usar não é óbvio — usar o último `approved`, documentar a escolha no adaptador. |
| `cancel_detail.date` (quando `status = cancelled`) | `pedido.cancelado_em` | 🔴, ver nota de confiança do campo `cancel_detail`. |
| — (calculado) | `pedido.valor_bruto_itens` | 🔴 **o ML não expõe um subtotal de itens pronto.** O adaptador soma `Σ order_items[].unit_price × quantity`. Isso é permitido pela regra 5 quando a fonte não manda (calcula uma vez, na ingestão) — mas é diferente de "copiar o dado da fonte", e deve ficar registrado. |
| `coupon.amount` | `pedido.valor_desconto` | 🟢 o campo existe; 🟡 pode não cobrir TODO desconto (promoções aplicadas diretamente no preço do item aparecem como diferença entre `full_unit_price` e `unit_price`, não em `coupon`). Ver "armadilhas de dinheiro". |
| `shipping_option.cost` (via `/shipments/{id}`, **não** vem em `/orders/{id}`) | `pedido.valor_frete_cobrado` | 🔴. Ver seção de frete abaixo — exige uma segunda chamada. |
| `total_amount` | `pedido.valor_total_pedido` | 🟢. É o valor final pago pelo comprador — nunca recalculado (regra R1 da V008). |
| — | `pedido.valor_repasse_previsto` | 🔴 **"canônico exige e a fonte não fornece" — ver seção dedicada.** |
| `currency_id` | `pedido.moeda` | 🟢. |
| `payments[0].payment_method_id` | `pedido.forma_pagamento` | 🟡. Precisa de tabela de tradução (`"master"`, `"visa"`, `"pix"` etc. → domínio canônico `CARTAO_CREDITO`/`PIX`/...). O ML tem dezenas de `payment_method_id` (bandeiras) que mapeiam para poucas formas canônicas; a lista completa de códigos não pôde ser confirmada nesta sessão. |
| `payments[0].installments` | `pedido.quantidade_parcelas` | 🟢. |
| `receiver_address.*` (via `/shipments/{id}`, **não** vem em `/orders/{id}`) | `pedido.cep_entrega` / `cidade_entrega` / `uf_entrega` | 🔴. Mesma ressalva do frete: precisa da segunda chamada. `state.id` vem como `"BR-SP"` — o adaptador extrai só a sigla para bater com `uf_entrega char(2)`. |

## Tabela de mapeamento — `item_pedido` (de `order_items[]`)

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `item.id` + `item.variation_id` | `item_pedido.id_externo` | 🟡. Concatenado pelo adaptador (`"MLB123-158541288641"`) porque o mesmo `item.id` pode aparecer em duas linhas com variações diferentes no mesmo pedido — usar só `item.id` quebraria a idempotência por linha (`uq_item_pedido_origem`). |
| `item.seller_sku` ou `item.seller_custom_field` | `item_pedido.sku_origem` | 🟡. O ML tem os dois campos historicamente com o mesmo propósito (SKU do vendedor); qual prevalece quando os dois vêm preenchidos e diferentes não está confirmado — usar `seller_sku` como principal e registrar `seller_custom_field` em `dados_origem`. |
| `item.title` | `item_pedido.titulo_origem` | 🟢. |
| `quantity` | `item_pedido.quantidade` | 🟢. |
| `unit_price` | `item_pedido.valor_unitario_bruto` | 🔴 **ver "armadilhas de dinheiro" — não é claramente "antes do desconto".** |
| `full_unit_price − unit_price`, × `quantity` | `item_pedido.valor_desconto_linha` | 🔴 calculado pelo adaptador; o ML não manda um "desconto da linha" pronto. |
| `unit_price × quantity` | `item_pedido.valor_total_linha` | 🔴 **o ML não manda um total de linha pronto** (diferente da premissa geral da V008 de que "a fonte informa o total da linha"). O adaptador calcula uma vez, na ingestão — permitido pela regra 5, mas registrar isso é importante porque contraria a expectativa do comentário da V008. |
| `item.variation_attributes[]` | (join com `variacao.atributos`, quando casado) | Não grava direto em `item_pedido`; usado para casar com `variacao` existente por `atributos`/SKU. |
| `item.condition`, `item.category_id`, `item.warranty` | `item_pedido.dados_origem` | Não têm coluna canônica — extensão. |

## Tabela de mapeamento — `custo` (de `order_items[].sale_fee` e frete)

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `order_items[].sale_fee` | `custo` (`natureza='COMISSAO_CANAL'`, `item_pedido_id`=a linha, `pedido_id`, `valor=sale_fee`, `eh_estimativa=false`) | 🟢 o campo `sale_fee` existe e é por item; 🔴 **ele NÃO vem decomposto em percentual (`COMISSAO_CANAL`) e tarifa fixa (`TARIFA_FIXA_CANAL`) — o ML devolve só o total.** O adaptador não pode inventar a divisão; grava tudo como `COMISSAO_CANAL` com `base_calculo`/`aliquota_aplicada` = `NULL` (não estimar uma alíquota que não temos). |
| `shipping_option.list_cost − shipping_option.cost` (via `/shipments/{id}`) | `custo` (`natureza='FRETE'`, `pedido_id`, `eh_estimativa=true`) | 🔴 **estimativa, não fato.** `list_cost` é o "preço de tabela" do frete; `cost` é o que o comprador pagou. A diferença é subsidiada por ML e/ou pelo vendedor conforme o programa de frete grátis vigente naquele momento — **a API não diz qual parte cabe a cada um.** Gravar com `eh_estimativa=true` e `descricao` explicando a fórmula é o máximo que se pode fazer sem inventar. O valor real cobrado do vendedor só aparece no extrato financeiro (fora do escopo desta fixture). |
| `payments[].installment_amount` vs. valor à vista (quando existir) | `custo` (`natureza='TAXA_PARCELAMENTO'`) | 🔴 não incluído nesta fixture: o custo do parcelamento para o vendedor normalmente não está no recurso de pedido; costuma vir do relatório de "cobranças"/billing, não confirmado nesta sessão. |

## Tabela de mapeamento — `devolucao` / `item_devolucao` (do recurso de claim)

| Campo na origem (claim/return) | Campo canônico | Observação |
|---|---|---|
| `id` (do claim) | `devolucao.id_externo` | 🔴 baixa confiança na origem exata do identificador (v1 vs v2 da API de claims). |
| `resource_id` | resolve `devolucao.pedido_id` (via `pedido.id_externo`) | 🟡. |
| `type`/`stage` | `devolucao.status_origem` | 🔴. |
| `status` (`opened`/`closed`) + `resolution` | `devolucao.status` | 🔴 mapeamento aproximado: `opened`→`EM_ANALISE`, presença de `players[mediator]`→`EM_MEDIACAO`, `closed`+`resolution.benefited=[complainant]`→`CONCLUIDA`. **Não confirmado contra a API real.** |
| `reason_id` (código, ex.: `"PDD8062"`) | `devolucao.motivo_origem` | 🟢 a existência de um código de motivo é conhecida; 🔴 a tabela de tradução `PDD8062 → PRODUTO_DIFERENTE_DO_ANUNCIO` etc. não foi confirmada — **é a peça que mais precisa de validação com payload real**, porque `devolucao.motivo` decide "de quem é o prejuízo" (comentário da V009). |
| `return.refund_at`, `return.destination` | `devolucao.responsavel_frete_reverso`, `devolucao.destino_produto` | 🔴 mapeamento especulativo — ver seção de gaps abaixo. |
| — | `devolucao.eh_arrependimento_cdc` | **Ver "canônico exige e a fonte não fornece".** |
| — | `item_devolucao` (linha por item) | O claim referencia o pedido inteiro (`resource_id`), não uma linha específica. Quando a devolução é parcial, **a API precisaria listar quais `order_items` voltaram — não encontrei confirmação de que o recurso de claims faz isso.** Nesta fixture a devolução é tratada como TOTAL por falta de informação de item; ver gap abaixo. |

---

## O que não tem equivalente canônico e vai para `dados_origem`

- `order_request` (`return`/`change`, sempre `null` nos exemplos vistos) — não canônico.
- `pack_id`, `pickup_id` — específicos de carrinho multi-pedido e retirada em ponto físico do ML; sem equivalente hoje.
- `context.channel`/`context.flows` — metadado interno de canal de venda do ML (ex.: "mshops").
- `item.category_id`, `item.condition`, `item.warranty` — descritivos do anúncio, não do pedido em si; ficam em `item_pedido.dados_origem`.
- `payments[].issuer_id`, `authorization_code`, `atm_transfer_reference` — rastro de gateway de pagamento, sem coluna canônica; úteis para suporte, ficam em extensão.
- `shipping_option.logistic_type`, `mode` (`me2`, `flex`, fulfillment) — não existe coluna canônica para "modalidade de envio"; fica em `dados_origem` do pedido ou do custo de frete até virar consulta de negócio.
- `players[].type` no claim (`mercadolibre` como mediador) — específico do fluxo de mediação do ML.

## O que o canônico exige e a fonte NÃO fornece (o item mais importante desta entrega)

1. **`pedido.status` além de "pago"** — `order.status` **não** avança para `EM_SEPARACAO`/
   `ENVIADO`/`ENTREGUE`. Ele fica em `paid` do pagamento até o cancelamento. O progresso de
   entrega mora em `/shipments/{id}.status` (`pending`, `handling`, `ready_to_ship`,
   `shipped`, `delivered`, `not_delivered`, `cancelled`) — **um recurso completamente
   separado**, e as `tags` do pedido (`"not_delivered"`, `"delivered"`) parecem ser um eco
   parcial disso, não confirmado como fonte de verdade. **Sem uma segunda chamada por
   pedido, o adaptador não tem como preencher `enviado_em`/`entregue_em` nem status
   corretos** — é o maior gap desta fixture, e ele é estrutural, não um detalhe.
2. **`pedido.valor_repasse_previsto`** — a coluna existe porque, segundo o comentário da
   V008, é "a única conferência externa que temos do motor de margem". **Não encontrei, com
   confiança, um campo no recurso de pedido que corresponda a "quanto o ML vai
   efetivamente repassar líquido".** Isso provavelmente mora no relatório de liquidações /
   extrato financeiro do vendedor (outro recurso, `/finance` ou similar), fora do escopo do
   pedido. Até confirmar, o adaptador **deve deixar esta coluna `NULL`**, nunca estimar —
   regra 5.
3. **Decomposição de `sale_fee`** em comissão percentual vs. tarifa fixa por item de baixo
   valor (`COMISSAO_CANAL` vs. `TARIFA_FIXA_CANAL`, ambos previstos na V010) — a API entrega
   só o total.
4. **Quem paga o subsídio do frete grátis** (vendedor vs. Mercado Livre) — nem `/orders`
   nem `/shipments` parecem expor isso diretamente; é inferido, não informado.
5. **CPF/CNPJ do comprador** — quase nunca disponível no pedido; o ML só expõe documento em
   fluxos específicos de emissão de nota fiscal pelo próprio vendedor. Isso significa que
   `cliente.documento_hash` **fica `NULL` na maior parte dos pedidos do Mercado Livre**, e a
   deduplicação de cliente entre canais (ML × loja própria) via documento **não vai
   funcionar para a maioria dos compradores do ML** — só via e-mail/apelido, quando
   disponíveis, ou nunca.
6. **`devolucao.eh_arrependimento_cdc` e o item específico devolvido** (para popular
   `item_devolucao` corretamente em devolução parcial) — não confirmado que a API de claims
   informe isso de forma estruturada e por item. Pode ser algo que só existe em texto livre
   de reclamação, o que forçaria heurística humana, não parsing automático.

## Armadilhas de dinheiro

1. **TIPO DE DADO — CRÍTICO (regra 2 do CLAUDE.md).** Os campos monetários do Mercado
   Livre (`unit_price`, `full_unit_price`, `sale_fee`, `total_amount`, `paid_amount`,
   `coupon.amount`, `transaction_amount`, `shipping_option.cost`, `list_cost` etc.) chegam
   como **número JSON (float)**, não como string. Um parser Jackson ingênuo mapeando direto
   para `double`/`Double` **perde centavo por arredondamento binário** antes mesmo de o
   valor chegar ao `BigDecimal`. **O adaptador tem que ler o token JSON como texto e
   construir `new BigDecimal(node.asText())` (ou `BigDecimal.valueOf` só como último recurso
   e nunca a partir de um `double` já arredondado por outro parse) — nunca
   `BigDecimal.valueOf(node.asDouble())` como caminho principal.** Isso vale para toda
   fixture desta entrega, ML e Bling: **nenhum valor monetário do JSON de origem pode passar
   por `double` no caminho até o `BigDecimal`.**
2. **`unit_price` não é claramente "bruto antes de desconto".** O par
   `full_unit_price`/`unit_price` existe para promoções custeadas pelo vendedor (o item 2 da
   fixture: `full_unit_price=99.90`, `unit_price=79.90`). Mas o desconto de **cupom** aplicado
   no checkout (`coupon.amount=15.00`) é **outro mecanismo, no nível do pedido**, que não
   aparece em nenhum `order_item`. Ou seja: existem **dois descontos de origens diferentes**
   que juntos formam `valor_desconto` do pedido e `valor_desconto_linha` do item — misturá-los
   sem cuidado faz a soma bater "por acaso" em alguns pedidos e errar em outros.
3. **`sale_fee` é por item, mas não tem base nem alíquota.** A V010 quer `base_calculo` e
   `aliquota_aplicada` para poder responder "por que R$ 25,17 de comissão" dali a um ano. O
   ML só manda o resultado. Sem a alíquota, `base_calculo`/`aliquota_aplicada` ficam `NULL`
   — não inventar `sale_fee / unit_price` como "a alíquota", porque tarifa fixa embutida
   distorceria esse cálculo para itens de valor baixo.
4. **`valor_total_linha` não vem pronto** (ver tabela de `item_pedido` acima) — é o inverso
   do caso geral descrito no comentário da V008 (que assume que a fonte manda o total da
   linha). Aqui o adaptador calcula, e isso precisa estar destacado no código para quem ler
   depois não achar que é uma cópia fiel da fonte.
5. **Frete "grátis" ≠ frete sem custo.** `shipping_option.cost = 0` (o que o comprador
   pagou) coexistindo com `list_cost = 24.90` (custo de tabela) é o caso concreto da regra
   R3 da V008 ("o que o cliente pagou de frete e o que o frete custou são números
   diferentes"). Gravar `valor_frete_cobrado = 0` e a estimativa de custo em `custo`
   separadamente — nunca um valor só.
6. **Moeda internacional.** Fora do escopo desta fixture (CBT/cross-border do ML muda
   `currency_id` para USD em alguns casos) — mencionado aqui só para registrar que
   `item_pedido.moeda = pedido.moeda` (regra do schema) deixa de ser `'BRL'` nesses casos, e
   o adaptador não deve fixar `'BRL'` como default silencioso.
