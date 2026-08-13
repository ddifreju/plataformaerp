# Regras de margem — fonte de verdade do domínio fiscal e financeiro (Fase 2)

**Versão:** 1.0
**Data:** 12 de agosto de 2026
**Escopo:** tarefas 13, 14, 15 e 16 (`docs/ESTADO.md`)
**Status:** especificação. Nenhuma linha de código de aplicação foi escrita a partir dela ainda.

Este documento é o contrato de domínio que o arquiteto e o engenheiro implementam.
Ele **não** define classes, pacotes nem assinaturas — define o que cada número
significa, de onde ele sai, quando ele **não pode ser calculado**, e com que
escala e arredondamento ele é produzido.

Regras do `CLAUDE.md` que mandam aqui, e como aparecem neste documento:

- **Regra 2 (dinheiro nunca é float):** toda operação abaixo declara escala e
  `RoundingMode`. Onde não declara, é bug de especificação — reporte, não adivinhe.
- **Regra 3 (toda resposta numérica é rastreável):** nenhum número deste
  documento pode ser apresentado sem a memória de cálculo da seção 7.
- **Regra 5 (nunca invente dado):** a seção 8 é a lista fechada do que o sistema
  tem permissão de dizer "não tenho esse dado". Ela é tão parte do produto quanto
  a fórmula.

---

## Sumário

1. [Vocabulário: quatro números diferentes que o lojista chama de "lucro"](#1-vocabulário)
2. [A fórmula da margem, decomposta](#2-a-fórmula-da-margem-decomposta)
3. [Dedução da receita vs custo — por que a ordem importa](#3-dedução-da-receita-vs-custo)
4. [Mercado Livre: as taxas do primeiro adaptador](#4-mercado-livre)
5. [Imposto](#5-imposto)
6. [Arredondamento](#6-arredondamento)
7. [Memória de cálculo](#7-memória-de-cálculo)
8. [Versionamento por vigência (tarefa 13)](#8-versionamento-por-vigência)
9. [O que o sistema NÃO pode afirmar](#9-o-que-o-sistema-não-pode-afirmar)
10. [Lacunas do modelo atual (o que falta no enum `NaturezaCusto`)](#10-lacunas-do-modelo-atual)

---

## 1. Vocabulário

O lojista usa "lucro" para quatro coisas distintas. O produto existe porque os
integradores mostram a primeira e chamam de quarta. **Nenhuma tela pode usar a
palavra "lucro" sem qualificador.**

| # | Nome canônico | O que é | De onde sai |
|---|---|---|---|
| N0 | **Faturamento bruto** (GMV) | O que o comprador pagou. Inclui frete cobrado dele. | `pedido.valor_total_pedido` (R1 da V008) |
| N1 | **Receita líquida** | N0 menos as deduções legais da receita (devolução, reembolso, desconto bancado pela loja). **Não** desconta comissão nem frete. | N0 − bloco B1 (seção 2) |
| N2 | **Margem de contribuição** | N1 menos todos os custos **diretos e medidos** do pedido: CMV, comissão, tarifa fixa, frete, embalagem, taxas financeiras, imposto. | N1 − blocos B2..B6 |
| N3 | **Resultado do pedido** | N2 menos os custos **rateados** (Ads, armazenagem, overhead atribuído). | N2 − blocos B7..B8 |
| N4 | **Lucro operacional do período** | Σ N3 dos pedidos do período, menos os custos de período **não rateados** (`custo` com `pedido_id IS NULL` e `rateado_de_custo_id IS NULL`). | Contrato S4 da V010 |

**A fronteira que importa é entre N2 e N3.** Ela é exatamente a fronteira entre
o que foi **medido** e o que foi **atribuído por rateio**. N2 é auditável linha a
linha contra a fatura do marketplace. N3 depende de um método de rateio que é
escolha nossa, não fato do mundo. A interface deve deixar isso visível — não como
nota de rodapé, como estrutura da tela.

**N4 não é a soma de N3 por definição** enquanto existir custo de período sem
rateio. Isso é correto e desejável: mensalidade de ferramenta não pertence a
nenhum pedido. Quem somar N3 e chamar de "lucro do mês" está errado por omissão.

> **Restrição da decisão 0017.** N0 e N4 só podem ser somados dentro de um escopo
> de canal declarado. Somar canais potencialmente sobrepostos (ML + Bling
> espelhando o mesmo pedido) conta a mesma venda duas vezes. Todo endpoint da
> tarefa 16 declara o escopo de canal na resposta ou se recusa a responder.

---

## 2. A fórmula da margem, decomposta

### 2.1 Regra estrutural: uma receita, um razão de custo

O modelo da Fase 1 já resolveu isso e a Fase 2 não pode reabrir:

- **Receita não muda depois de gravada** (R2 da V008). Devolução, reembolso e
  estorno **não** reescrevem `valor_total_pedido`.
- **Todo dinheiro que sai é linha em `custo`** (S6 da V010).

Consequência direta para o motor de margem: **a separação entre "dedução da
receita" e "custo" é uma CLASSIFICAÇÃO das naturezas de `custo`, não um segundo
caminho de dados.** Não existe consulta que leia `devolucao.valor_reembolsado`
para compor margem. Não existe subtração aplicada em `pedido`. Existe um `SELECT
sum(valor) FROM custo WHERE pedido_id = :id` (contrato S1) e um agrupamento por
natureza.

Se alguém implementar a "dedução" lendo de outra tabela, o prejuízo aparece
dobrado. Esse é o modo de falha número um desta fase.

### 2.2 Os blocos

Mapeamento fechado de `NaturezaCusto` (V010) para bloco de apresentação e para
tratamento fiscal:

| Bloco | Naturezas | Nível | Reduz a base do imposto? |
|---|---|---|---|
| **B1 — Deduções da receita bruta** | `REEMBOLSO`, `DESCONTO_CONCEDIDO` | N0 → N1 | **Sim** (com regra de competência, ver 5.4) |
| **B2 — Custo da mercadoria** | `MERCADORIA` | N1 → N2 | Não |
| **B3 — Custos do canal** | `COMISSAO_CANAL`, `TARIFA_FIXA_CANAL` | N1 → N2 | **Não** — ver seção 3 |
| **B4 — Custos logísticos** | `FRETE`, `FRETE_REVERSO`, `EMBALAGEM` | N1 → N2 | Não |
| **B5 — Custos financeiros** | `TAXA_PAGAMENTO`, `TAXA_PARCELAMENTO`, `TAXA_ANTECIPACAO` | N1 → N2 | Não |
| **B6 — Imposto** | `IMPOSTO` | N1 → N2 | (é o próprio) |
| **B7 — Marketing atribuído** | `ADS` | N2 → N3 | Não |
| **B8 — Overhead atribuído** | `ARMAZENAGEM`, `TARIFA_ADMINISTRATIVA`, `OUTRO` | N2 → N3 | Não |

Notas de classificação, cada uma com motivo:

- **`ARMAZENAGEM` em B8 e não em B4.** Armazenagem de fulfillment é cobrada por
  período de estoque parado, não por pedido; quando ela chega atribuída a um
  pedido pela fonte (raro), a linha existe com `pedido_id` preenchido e
  `eh_estimativa = false`, e continua em B8 porque é natureza de overhead. A
  posição no bloco não muda a soma — muda o que a tela chama de "margem de
  contribuição". Consistência vale mais que precisão de taxonomia aqui.
- **`OUTRO` em B8 de propósito.** `OUTRO` é o escape hatch da V010. Colocá-lo
  abaixo de N2 impede que um custo não classificado contamine silenciosamente o
  número mais auditável do sistema. Toda ocorrência de `OUTRO` em produção deve
  disparar alerta de "natureza faltando".
- **`FRETE_REVERSO` em B4 e não em B1.** O frete de retorno é custo operacional,
  não devolução de receita. Confundir os dois reduziria indevidamente a base do
  imposto (seção 3).

### 2.3 A fórmula

Com `C(bloco, p)` = `SELECT sum(valor) FROM custo WHERE pedido_id = p AND natureza IN (...)`:

```
N0  Faturamento bruto            = pedido.valor_total_pedido
N1  Receita líquida              = N0 − C(B1)
N2  Margem de contribuição       = N1 − C(B2) − C(B3) − C(B4) − C(B5) − C(B6)
N3  Resultado do pedido          = N2 − C(B7) − C(B8)

Margem de contribuição %         = N2 ÷ N0        (denominador é N0, não N1)
Margem líquida %                 = N3 ÷ N0
```

**O denominador é sempre N0.** Usar N1 faria a margem percentual *subir* quando o
pedido é devolvido — matematicamente correto e gerencialmente absurdo. Fixado em
N0, um pedido totalmente devolvido tem margem negativa, que é a verdade.

Como `custo.valor` é positivo para saída e negativo para estorno (S5 da V010), a
soma dos blocos já embute os estornos automaticamente. **Não existe tratamento
especial de estorno no motor de margem.** Se o ML devolve a comissão numa
devolução, isso é uma linha `COMISSAO_CANAL` com valor negativo, e `C(B3)`
diminui sozinho.

### 2.4 Ordem de aplicação

A ordem só importa em um ponto: **o imposto (B6) não incide sobre o saldo
corrente.** Ele incide sobre uma base própria, definida na seção 5. Fora isso, a
soma é comutativa e a ordem abaixo é de **apresentação**, escolhida para que a
tela conte a história na sequência em que o dinheiro sai:

1. Faturamento bruto (N0)
2. − Deduções da receita (B1) → **N1**
3. − Custo da mercadoria (B2) — a maior parcela na maioria dos casos, vem primeiro
4. − Custos do canal (B3)
5. − Custos logísticos (B4)
6. − Custos financeiros (B5)
7. − Imposto (B6) — **calculado sobre a base fiscal da seção 5, não sobre o saldo do passo 6** → **N2**
8. − Ads atribuído (B7)
9. − Overhead atribuído (B8) → **N3**

### 2.5 Exemplo numérico completo

Pedido real-plausível: 1 unidade, Mercado Livre, anúncio Clássico, R$ 199,90,
frete grátis (acima do limiar), lojista no Simples Anexo I com RBT12 de
R$ 500.000,00.

| Passo | Linha | Valor (escala 4) | Origem | Estimativa? |
|---|---|---:|---|---|
| 1 | Faturamento bruto | `199,9000` | `pedido.valor_total_pedido` | não |
| 2 | Deduções da receita | `0,0000` | sem devolução | — |
| | **N1 Receita líquida** | **`199,9000`** | | |
| 3 | `MERCADORIA` | `82,5000` | `variacao.custo_unitario_atual` × 1, congelado na ingestão | não |
| 4 | `COMISSAO_CANAL` | `25,9870` | informado pela fonte; `base=199,9000`, `aliquota=0,130000` | não |
| 4 | `TARIFA_FIXA_CANAL` | `0,0000` | acima da faixa de tarifa fixa | não |
| 5 | `FRETE` | `24,9000` | cobrado do vendedor, informado pela fonte | não |
| 5 | `EMBALAGEM` | `1,8000` | rateio `EMBALAGEM_POR_ITEM` | **sim** |
| 6 | `TAXA_ANTECIPACAO` | — | **não cadastrada** → lacuna | — |
| 7 | `IMPOSTO` | `13,4494` | Simples, alíquota efetiva `0,067280` × `199,9000` | **sim** |
| | **N2 Margem de contribuição** | **`51,2636`** | | |
| 8 | `ADS` | `6,3000` | rateio `ADS_POR_RECEITA_DO_PERIODO` | **sim** |
| 9 | Overhead | `0,0000` | não rateado neste tenant | — |
| | **N3 Resultado do pedido** | **`44,9636`** | | |

Apresentação (escala 2, `HALF_UP`): N0 `R$ 199,90` · N2 `R$ 51,26` (25,64%) ·
N3 `R$ 44,96` (**22,49%**).

O que o integrador mostra hoje: **R$ 199,90**. O que sobrou: **R$ 44,96** — e
mesmo esse número carrega três estimativas e uma lacuna declarada. A tela deve
dizer, com todas as letras:

> Resultado estimado: **R$ 44,96** (22,49%). Inclui R$ 21,55 de custos estimados
> (embalagem, imposto, Ads). **Não inclui taxa de antecipação — não cadastrada.**
> Se você antecipa recebíveis, este número está superestimado.

### 2.6 A conferência externa: `valor_repasse_previsto`

`pedido.valor_repasse_previsto` (V008) é a única checagem que temos contra o
mundo. Regra:

```
repasse_esperado = N0 − C(B1) − C(B3) − C(B4 ∩ {FRETE, FRETE_REVERSO}) − C(B5)
delta            = pedido.valor_repasse_previsto − repasse_esperado
```

`repasse_esperado` inclui **apenas o que o canal desconta na fonte** — não inclui
CMV, imposto, embalagem nem Ads, que saem do bolso do lojista por fora.

- `delta ≈ 0` (|delta| ≤ `0,0100`): custos do canal estão completos.
- `delta < 0`: **existe custo que não estamos vendo.** Achar esse custo invisível
  é literalmente o produto. Deve virar linha visível de `custo` com natureza
  `OUTRO` e `eh_estimativa = true`, descrição `"diferença não identificada contra
  repasse do canal"` — **nunca** ser absorvido em silêncio.
- `delta > 0`: estamos cobrando custo a mais do lojista (dupla contagem, taxa
  errada, estorno não lançado). Isto é bug, não dado. Alerta, não linha.
- `valor_repasse_previsto IS NULL`: sem conferência. O grau de confiança do
  pedido cai — ver seção 9.

---

## 3. Dedução da receita vs custo

Esta seção existe porque é a fonte de erro fiscal mais comum e mais cara do
e-commerce brasileiro.

### 3.1 A regra

> **Comissão de marketplace, frete e taxa de cartão são CUSTO, não dedução da
> receita bruta. O imposto incide sobre o preço de venda cheio, não sobre o que
> caiu na conta.**

O lojista vende por R$ 199,90, recebe R$ 149,01 do Mercado Livre, e calcula o
imposto sobre R$ 149,01. Está errado. A receita bruta da operação é R$ 199,90 —
a comissão é despesa dele com um prestador de serviço, não um desconto no preço.
A calculadora de planilha que o lojista usa quase sempre erra aqui, e erra a
favor dele, o que faz a diferença aparecer só na fiscalização.

No exemplo da seção 2.5: base correta `199,9000` → imposto `13,4494`. Base
errada `149,0130` → imposto `10,0257`. Diferença de **R$ 3,42 por pedido**, ou
1,7 ponto percentual de margem. Em 1.000 pedidos/mês, R$ 3.423,70/mês de tributo
não provisionado.

### 3.2 O que reduz a base e o que não reduz

**Reduz a base de cálculo (bloco B1):**

- **Devolução de mercadoria** — com regra de competência própria (5.4).
- **Desconto incondicional** concedido no ato da venda e destacado no documento
  fiscal (`DESCONTO_CONCEDIDO`, parte bancada pela loja).
- **Cancelamento** antes da emissão do documento fiscal — nesse caso o pedido
  nem deveria compor N0; se compôs, a correção é linha B1.

**NÃO reduz a base (blocos B2..B8):**

- Comissão do marketplace, sob qualquer nome (tarifa de venda, taxa de serviço).
- Tarifa fixa por item.
- Frete pago pelo vendedor, subsidiado ou não. Inclusive o frete reverso.
- Taxa de cartão, de parcelamento e de antecipação.
- Ads, embalagem, armazenagem, mensalidade.
- **Desconto condicional** (concedido depois, por pagamento antecipado ou por
  acordo pós-venda). É despesa financeira, não redução de preço.

### 3.3 O frete cobrado do comprador entra na base?

**Depende, e a decisão é do contador do lojista, não nossa.**

- Se o vendedor cobra o frete e o destaca na sua própria nota, ele compõe a
  receita bruta e entra na base.
- Se o comprador paga o frete diretamente ao marketplace (Mercado Envios com
  cobrança na plataforma) e o vendedor não o fatura, há entendimento de que ele
  não compõe a receita bruta do vendedor.

**Modelagem:** flag por tenant, versionada por vigência,
`frete_cobrado_compoe_base_tributavel` (boolean, sem default). **Sem default**:
não configurado é lacuna declarada, não é `false` presumido. Consequência de
errar: se marcarmos `false` indevidamente, o imposto sai subestimado e a margem
**superestimada** — o pior sentido do erro.

---

## 4. Mercado Livre

> ### AVISO DE CONFIABILIDADE — leia antes de usar qualquer número desta seção
>
> **Os percentuais abaixo NÃO devem ser codificados como constante em lugar
> nenhum.** Eles mudam por categoria, por tipo de anúncio, por faixa de preço,
> por campanha e por ano — o Mercado Livre alterou a estrutura de tarifas em
> março de 2026 e as fontes secundárias divergem entre si sobre o resultado.
>
> Este documento consultou as páginas oficiais de ajuda do Mercado Livre e a
> documentação de desenvolvedores em **12/08/2026**. Onde a fonte oficial dá
> uma **faixa** (10% a 14%) e não um valor por categoria, o valor por categoria
> é **NÃO CONFIRMADO e precisa ser cadastrado pelo lojista** ou lido da API.
>
> Regra 5 do `CLAUDE.md`: é melhor o sistema pedir o dado ao lojista do que
> inventar uma alíquota.

### 4.1 Hierarquia de fontes de taxa (a decisão mais importante desta seção)

O motor de margem consulta as fontes **nesta ordem**, e para na primeira que
responde:

| Nível | Fonte | `eh_estimativa` | Quando |
|---|---|---|---|
| **1 — FATO** | Valor cobrado, informado pela fonte no próprio pedido/fatura (`order.payments[].fee_details`, billing do ML) | `false` | Sempre que existir. **É a única fonte que reflete promoção, acordo comercial e correção retroativa do canal.** |
| **2 — REGRA** | Tabela `taxa_canal` versionada por vigência (seção 8), cadastrada pelo lojista ou populada pela API de simulação | `true` | Quando a fonte não informou o valor cobrado |
| **3 — LACUNA** | Nenhuma | — | Não calcula. Reporta ausência. Ver seção 9 |

**Não existe nível 4.** Não há fallback para "a taxa mais próxima", "a taxa
atual" nem "a média da categoria". Consequência aceita: alguns pedidos não terão
margem calculável. Isso é o produto funcionando, não falhando.

**Sobre a API de simulação:** o Mercado Livre expõe
`GET https://api.mercadolibre.com/sites/MLB/listing_prices?price={p}&category_id={c}&listing_type_id={t}`,
que retorna `sale_fee_amount` — a comissão para aquela combinação
([Prices API, developers.mercadolivre.com.br, consultado em 12/08/2026](https://developers.mercadolivre.com.br/en_us/price-apl)).
Ela retorna a taxa **vigente hoje**, não a taxa histórica.

> **Regra dura:** a API de simulação **nunca** é chamada no caminho de cálculo de
> um pedido passado. Ela é usada para **popular** `taxa_canal` com
> `vigencia_inicio = data da consulta` e `confianca = CONFIRMADO_FONTE_OFICIAL`.
> Chamar a API na hora de calcular um pedido de março com a taxa de agosto é
> exatamente o erro que a tarefa 13 existe para impedir.

### 4.2 Comissão por tipo de anúncio

Mapeamento API → nome comercial, **confirmado** na documentação de
desenvolvedores do Mercado Livre
([Listing types, consultado em 12/08/2026](https://developers.mercadolivre.com.br/pt_br/tutorial-tipos-de-publicacao-y-atualizacao-de-artigos)):

| `listing_type_id` | Nome comercial | Canônico sugerido |
|---|---|---|
| `gold_special` | Clássico | `CLASSICO` |
| `gold_pro` | Premium | `PREMIUM` |
| `free` | Grátis | `GRATIS` |
| `gold_premium`, `gold`, `silver`, `bronze` | legados (Diamante, Ouro, Prata, Bronze) | `LEGADO` |

Faixas de comissão, conforme a página oficial de ajuda do Mercado Livre
([Quanto custa vender um produto?, consultado em 12/08/2026](https://www.mercadolivre.com.br/ajuda/quanto-custa-vender-um-produto_1338)):

| Tipo | Faixa oficial | Diferença prática |
|---|---|---|
| Clássico (`gold_special`) | **10% a 14%** do valor da venda | Sem parcelamento sem juros bancado pelo vendedor |
| Premium (`gold_pro`) | **15% a 19%** do valor da venda | Inclui até 12x sem juros ao comprador |

**O que está confirmado:** a existência das duas faixas e a diferença estrutural
entre elas (o Premium embute o custo do parcelamento sem juros — por isso a
diferença de ~5 pontos percentuais).

**O que NÃO está confirmado e precisa ser cadastrado pelo lojista:**

- **O percentual exato de cada categoria.** A faixa 10–14% / 15–19% não permite
  derivar o número de nenhuma categoria específica. Fontes secundárias citam
  11–14% e 16–19% como referência de mercado
  ([ecommercenapratica.com](https://ecommercenapratica.com/blog/comissao-mercado-livre/),
  [gosmarter.com.br](https://gosmarter.com.br/taxas-mercado-livre/), consultadas
  em 12/08/2026) — **divergem entre si e da faixa oficial, e por isso não entram
  no sistema como dado.**
- **Categorias com tarifa reduzida.** A ajuda oficial menciona redução em
  categorias como Livros e Supermercado, sem publicar os percentuais na página
  consultada. Tratar como categoria comum é **subestimar a margem**; tratar como
  reduzida sem saber o valor é inventar. → cadastro obrigatório.

**Implicação para o adaptador (tarefa 14):** o `listing_type_id` e o
`category_id` do anúncio precisam ser preservados no pedido. Hoje eles não têm
coluna canônica — devem ir em `pedido.dados_origem` ou
`item_pedido.dados_origem` (campo de extensão da decisão 0002) e ser lidos de lá
pelo motor. **Sem `category_id`, a taxa de nível 2 não é selecionável e o pedido
cai no nível 3 (lacuna).**

### 4.3 Tarifa fixa por item de baixo valor

Natureza `TARIFA_FIXA_CANAL` (V010). É onde a margem mais dói, porque incide
proporcionalmente mais sobre o produto barato.

**Estrutura descrita na ajuda oficial** ([consultada em 12/08/2026](https://www.mercadolivre.com.br/ajuda/quanto-custa-vender-um-produto_1338)):

| Faixa de preço unitário | Custo fixo |
|---|---|
| Abaixo de R$ 12,50 | **50% do valor da unidade** |
| De R$ 12,50 a R$ 79,00 | valor fixo, em **três faixas de preço** |
| Acima de R$ 79,00 | **sem custo fixo** |

**Duas coisas NÃO confirmadas, ambas materiais:**

1. **Os três valores fixos da faixa intermediária.** A página consultada
   descreve a estrutura, não os valores. → **cadastro obrigatório.**
2. **Se essa estrutura ainda vale.** Fontes secundárias afirmam que, a partir de
   **2 de março de 2026**, o custo fixo abaixo de R$ 79 foi substituído por um
   custo operacional **variável por peso e dimensão**
   ([blog.tecnospeed.com.br](https://blog.tecnospeed.com.br/tarifas-do-mercado-livre/),
   [blog.joompulse.com](https://blog.joompulse.com/2026/02/12/custos-mercado-livre-o-que-muda-para-sellers-2026/),
   consultadas em 12/08/2026). Isso **contradiz** a estrutura descrita na página
   de ajuda consultada no mesmo dia. Não resolvi essa contradição e **não vou
   arbitrá-la**.

**Consequência de modelagem — e é uma consequência de peso:** se a tarifa passou
a depender de **peso e dimensão**, ela deixa de ser função de (categoria, tipo de
anúncio, faixa de valor). A tabela `taxa_canal` da seção 8 **não consegue
representá-la** sem eixos novos (peso, cubagem), que o modelo canônico da Fase 1
não tem — `variacao` não guarda peso nem dimensão.

Encaminhamento, em ordem de preferência:

1. **Nível 1 sempre.** Ler o valor cobrado da fatura/pedido do ML. Resolve o
   problema inteiro sem modelar regra nenhuma, e é o que o adaptador deve buscar
   primeiro.
2. Nível 2 limitado à estrutura por faixa de valor, marcado
   `confianca = INFORMADO_PELO_LOJISTA`.
3. **Não** adicionar peso/cubagem ao modelo nesta fase. É escopo novo
   (`CLAUDE.md`, "quando parar e perguntar"), e a opção 1 resolve mais de 80% do
   problema.

### 4.4 Frete grátis e quem paga

A regra do frete no ML tem três partes, e o produto precisa das três separadas —
porque é aqui que mora o custo que o lojista mais subestima.

**Limiares e subsídio, conforme ajuda oficial** ([consultada em 12/08/2026](https://www.mercadolivre.com.br/ajuda/CustosdefretegratispeloMercadoEnvios_3362)):

| Faixa do produto | Frete grátis | Quem arca |
|---|---|---|
| Abaixo de R$ 19 | não obrigatório | comprador |
| De R$ 19 a R$ 78,99 | oferecido | **Mercado Livre cobre 100% da tarifa de envio** |
| A partir de **R$ 79** | oferecido | **vendedor**, com o ML cobrindo uma parcela conforme reputação |

**NÃO confirmado:** o percentual da parcela coberta pelo ML acima de R$ 79. A
página consultada indica **10% para reputação verde**; fontes secundárias falam
em **até 70% de desconto conforme reputação**
([duoke.com](https://www.duoke.com/pt/blog/article/329-guia-custos-frete-mercado-livre-brasil-2026),
consultada em 12/08/2026). São afirmações incompatíveis — provavelmente medem
coisas diferentes (subsídio sobre a tarifa cheia vs desconto na tabela do
vendedor), e **não vou escolher uma.**

**Regra do motor, e ela dispensa resolver a contradição:**

> O custo de frete **nunca é derivado da regra**. Ele é lido do valor efetivamente
> descontado do vendedor, informado pela fonte, e gravado como
> `custo(FRETE, eh_estimativa = false)`. A regra de limiar serve apenas para
> **validar** (se o pedido é R$ 45,00, frete grátis, e a fonte reporta custo de
> frete para o vendedor, isso é anomalia a investigar) e para **simular** preço
> de produto novo.

E a distinção que a V008 já fixou em R3, repetida aqui porque é o coração do
problema: `pedido.valor_frete_cobrado` (receita, o que o comprador pagou) e
`custo(FRETE)` (o que saiu do vendedor) são **números diferentes**. No pedido do
exemplo 2.5: cobrado `0,0000`, custo `24,9000`. Uma coluna só esconderia
exatamente o número que o produto existe para revelar.

### 4.5 Parcelamento e antecipação

Naturezas `TAXA_PARCELAMENTO` e `TAXA_ANTECIPACAO` (V010). São custos distintos e
o modelo já os separa corretamente:

- **Parcelamento**: custo de vender em N vezes. No **Premium** ele está **embutido
  na comissão** — por isso o Premium é ~5 pp mais caro. Lançar `TAXA_PARCELAMENTO`
  separada em pedido Premium é **contar o mesmo custo duas vezes.**
- **Antecipação**: custo de receber antes do prazo. O prazo padrão do ML é da
  ordem de 14 dias após a confirmação de entrega, variando com a reputação do
  vendedor. Antecipar troca prazo por percentual.

**Nada de valor nesta subseção está confirmado.** Fontes secundárias citam tarifa
base de 4,99% à vista mais 0,99–2,99% por parcela, e antecipação de 1,99–3,99%
conforme o plano do Mercado Pago
([rallydevendas.com.br](https://rallydevendas.com.br/como-vender-em/taxa-mercado-livre-quanto-cobra),
consultada em 12/08/2026). São **números de blog, não de fonte oficial** — não
entram no sistema.

**Regras duras:**

1. `TAXA_PARCELAMENTO` só é lançada quando `pedido.quantidade_parcelas > 1` **e**
   o tipo de anúncio não embute parcelamento. Em pedido Premium, não se lança —
   e a memória de cálculo deve registrar a descrição
   `"embutida na comissão do anúncio Premium"`, para que a ausência da linha seja
   explicável e não pareça lacuna.
2. **Antecipação é opt-in do lojista, e o sistema não tem como descobrir sozinho.**
   Se o lojista antecipa recebíveis e não configura isso, o custo é invisível e a
   margem sai **superestimada**. Esta é a lacuna mais silenciosa da seção 9,
   porque não deixa rastro em nenhum campo do pedido.
3. Antecipação é `custo` de **período**, tipicamente: uma antecipação cobre
   vários pedidos de uma vez. Modelo: linha-mãe com `pedido_id IS NULL` e linhas
   filhas com `rateado_de_custo_id` preenchido (contrato S4 da V010),
   `metodo_rateio = 'ANTECIPACAO_POR_VALOR_A_RECEBER'`.

### 4.6 Resumo do que o lojista precisa cadastrar para o ML funcionar

Se nada disso vier da fonte no nível 1, é o mínimo para sair da lacuna:

- [ ] Percentual de comissão por categoria × tipo de anúncio, com data de vigência
- [ ] Valores da tarifa fixa por faixa de preço (ou confirmação de que virou variável)
- [ ] Se antecipa recebíveis, e a taxa contratada
- [ ] Reputação atual (só afeta simulação de frete, não o cálculo do pedido)

---

## 5. Imposto

### 5.1 Simples Nacional, Anexo I (comércio)

**Alíquota nominal ≠ alíquota efetiva.** O lojista que está na faixa de 9,50%
não paga 9,50%. A confusão entre as duas é o segundo erro fiscal mais comum
depois do da seção 3.

**Tabela do Anexo I** (LC 123/2006, Anexo I, com a redação da LC 155/2016 —
valores estáveis desde 2018, [texto oficial no Planalto](https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp123.htm),
consultado em 12/08/2026):

| Faixa | RBT12 (receita bruta dos 12 meses anteriores) | Alíquota nominal | Parcela a deduzir |
|---|---|---:|---:|
| 1 | até R$ 180.000,00 | 4,00% | R$ 0,00 |
| 2 | de R$ 180.000,01 a R$ 360.000,00 | 7,30% | R$ 5.940,00 |
| 3 | de R$ 360.000,01 a R$ 720.000,00 | 9,50% | R$ 13.860,00 |
| 4 | de R$ 720.000,01 a R$ 1.800.000,00 | 10,70% | R$ 22.500,00 |
| 5 | de R$ 1.800.000,01 a R$ 3.600.000,00 | 14,30% | R$ 87.300,00 |
| 6 | de R$ 3.600.000,01 a R$ 4.800.000,00 | 19,00% | R$ 378.000,00 |

> **Esta tabela é DADO, não código.** Ela vai para uma tabela versionada por
> vigência (mesmo mecanismo da seção 8, `tipo_taxa = 'IMPOSTO_SIMPLES_ANEXO_I'`)
> com `vigencia_inicio` e fonte registrada. Hardcodar significa que a próxima
> alteração legal exige deploy — e, pior, recalcula pedidos antigos com a regra
> nova. Ver seção 8.4.

**Fórmulas:**

```
alíquota_efetiva = (RBT12 × alíquota_nominal − parcela_a_deduzir) ÷ RBT12
imposto_do_mês   = alíquota_efetiva × receita_bruta_tributável_do_mês
```

Exemplo (o do 2.5), RBT12 = R$ 500.000,00 → Faixa 3:

```
(500.000,00 × 0,095) − 13.860,00 = 47.500,00 − 13.860,00 = 33.640,00
33.640,00 ÷ 500.000,00 = 0,06728  →  alíquota efetiva 6,728%
```

Nominal 9,50%, efetiva 6,728%. Quem usa a nominal superestima o imposto em 41% e
**subestima a margem**. Quem usa a menor faixa faz o contrário. Ambos erram.

### 5.2 Atribuição do imposto do mês a cada pedido

O Simples é apurado por **mês**, não por pedido. O motor de margem precisa de um
número por pedido. Regra:

```
imposto_do_pedido = alíquota_efetiva(mês de competência do pedido)
                    × receita_bruta_tributável_do_pedido
```

`custo(IMPOSTO)` grava `base_calculo` = receita tributável do pedido,
`aliquota_aplicada` = alíquota efetiva em fração decimal (`0,067280` — seis
casas, ver V010).

**Isto é sempre `eh_estimativa = true`, sem exceção**, por dois motivos:

1. A alíquota efetiva depende do RBT12, que é apurado sobre a **empresa inteira**
   — inclusive vendas em canais que o sistema não ingere.
2. O rateio do DAS por pedido é uma atribuição gerencial nossa, não um fato
   fiscal. O que existe de fato é uma guia mensal.

**O RBT12 é dado do lojista, não dado nosso.** Se derivarmos o RBT12 da receita
que enxergamos, ele sairá **subestimado** (falta o que não integramos), a faixa
cairá para baixo, a alíquota efetiva também, e a margem sairá **superestimada**.

> **Regra:** `RBT12` é campo cadastrado, com competência mensal, informado pelo
> lojista ou pelo contador. **Sem RBT12 informado, não calculamos imposto do
> Simples** — reportamos lacuna (seção 9). Não derivamos.

Detalhe de borda: para empresa com menos de 12 meses de atividade, o RBT12 é
proporcionalizado (média dos meses de atividade × 12). Isso é regra do contador —
o campo cadastrado já deve vir com o valor correto e o sistema não recalcula.

### 5.3 Qual receita entra na base

**Entra:**

- O valor da venda (`pedido.valor_total_pedido`), pelo preço cheio, **antes** de
  qualquer comissão ou taxa (seção 3.1).
- O frete cobrado do comprador, **se** `frete_cobrado_compoe_base_tributavel` do
  tenant estiver ligado (seção 3.3).

**Sai (reduz a base):**

- Devolução de mercadoria, com a regra de competência de 5.4.
- Desconto incondicional destacado no documento fiscal.

**Não sai:** tudo do bloco B2 ao B8. Repetido de propósito.

### 5.4 Devolução: a regra de competência que quase todo mundo erra

> O valor da mercadoria devolvida é deduzido da receita bruta **no período de
> apuração do mês da DEVOLUÇÃO** — não no mês da venda. Se a devolução for maior
> que a receita do mês, o saldo remanescente é deduzido nos meses subsequentes,
> até ser integralmente deduzido.
> — Resolução CGSN nº 140/2018, art. 17 ([consultada em 12/08/2026](https://www.legisweb.com.br/noticia/?legislacao=360430))

Consequências práticas, e são todas contraintuitivas:

1. **O imposto da venda original NÃO é estornado.** Ele foi devido e pago. Um
   pedido de março devolvido em maio continua com `custo(IMPOSTO)` positivo em
   março.
2. **O crédito aparece em maio**, como redução da base de maio. No modelo:
   `custo(IMPOSTO)` com **valor negativo** e `competencia_em` em **maio**
   (linha nova, nunca `UPDATE` — S5 da V010), com
   `descricao = 'crédito de devolução do pedido X, CGSN 140/2018 art. 17'`.
3. **Efeito na margem do pedido devolvido:** ela fica pior do que a intuição
   sugere, porque o imposto não volta para ele — volta para o mês seguinte, e
   beneficia outros pedidos. Isso é o que de fato acontece com o dinheiro do
   lojista.
4. **Saldo remanescente com carry-over** exige estado próprio (quanto de crédito
   de devolução ainda não foi absorvido). Para a Fase 2, é aceitável **não**
   implementar o carry-over e **declarar** a simplificação: quando
   `Σ devoluções do mês > receita do mês`, o sistema reporta
   `"crédito de devolução excedente não aplicado — consulte seu contador"`.
   Declarar a simplificação é honesto; implementá-la errado, não.

### 5.5 Substituição tributária — quando o ICMS já foi pago antes

**Esta é a fonte de erro que a tarefa pediu para tratar explicitamente, e ela erra
no sentido oposto às outras: ela SUBESTIMA a margem.**

**O mecanismo.** No Simples Nacional, o DAS é uma guia única cuja alíquota efetiva
já embute uma parcela de ICMS. Quando a mercadoria está sujeita a **substituição
tributária**, o ICMS de toda a cadeia já foi recolhido antes, pelo industrial ou
pelo importador, e embutido no preço de compra. O varejista **não deve recolher
ICMS de novo** sobre essa venda.

O mecanismo legal é a **segregação de receita**: a receita de produtos com
ICMS-ST é segregada, e o percentual de ICMS da faixa é **excluído** da alíquota
efetiva aplicada a ela. O mesmo raciocínio vale para PIS/COFINS **monofásicos**
(combustível, medicamento, cosmético, bebida fria, autopeça): a parcela de
PIS/Pasep e COFINS é excluída.

```
alíquota_efetiva_segregada = alíquota_efetiva × (1 − Σ percentuais dos tributos excluídos da faixa)
```

Onde os percentuais de repartição por faixa vêm da tabela de partilha do Anexo I
([ANEXO I — Alíquotas e Partilha do Simples Nacional, Receita Federal](http://normas.receita.fazenda.gov.br/sijut2consulta/anexoOutros.action?idArquivoBinario=48430),
consultada em 12/08/2026).

> **VALORES A CONFERIR ANTES DE CODIFICAR.** A repartição do Anexo I destina ao
> ICMS aproximadamente 34% nas faixas iniciais e 33,5% nas faixas 3 a 5, sendo
> que a faixa 6 não tem ICMS (o ICMS deixa de compor o DAS acima de R$ 3,6 mi).
> **Não confirmei esses percentuais linha a linha contra o anexo oficial.** Eles
> vão para a tabela versionada como dado cadastrado, com a mesma disciplina de
> fonte e data da seção 8 — nunca como constante no código.

**Magnitude do erro.** Retomando o exemplo (Faixa 3, efetiva 6,728%), se o
produto tem ICMS-ST e a receita **não** for segregada:

```
Com ST segregado (ICMS ≈ 33,5%): 0,06728 × (1 − 0,335) = 0,0447412  →  4,474%
Sobre R$ 199,90:  esperado 8,9438   vs   cobrado 13,4494
Erro: R$ 4,51 a MAIS de imposto por pedido — 2,26 pp de margem a MENOS
```

**Direção do erro:** aplicar a alíquota cheia a produto com ST **superestima o
imposto** e portanto **subestima a margem**. É o único erro desta especificação
que faz o lojista parecer pior do que é. Ainda é erro — um produto que erra para
baixo perde a confiança do lojista tão rápido quanto um que erra para cima,
porque ele confere contra o extrato.

**Modelagem.** O modelo canônico da Fase 1 **não tem** onde marcar isso. `produto`
tem `ncm` e `cest`, e o `cest` é forte indício de ST (o CEST existe justamente
para produtos passíveis de ST) — **mas indício não é fato**, porque a sujeição a
ST depende do NCM/CEST **e do estado de destino**, e varia por convênio estadual.

> **Regra:** flag explícita por produto (ou por variação), cadastrada,
> tri-estado: `SUJEITO_ST` / `NAO_SUJEITO_ST` / `NAO_INFORMADO`.
> **`NAO_INFORMADO` é lacuna, não é `NAO_SUJEITO_ST`.** Derivar ST do CEST
> automaticamente é exatamente o tipo de heurística que a decisão 0017 recusou
> para reconciliação, pelo mesmo motivo: erra em silêncio.
> Sugestão de campo separado para PIS/COFINS monofásico, com o mesmo tri-estado —
> os dois regimes são independentes e um produto pode estar em ambos.

Isso é **escopo novo** em relação à V006 e precisa de migration. Registrado aqui
como pendência de modelagem, não implementado por conta própria (`CLAUDE.md`,
"quando parar e perguntar").

### 5.6 Lucro Presumido — o suficiente para o modelo não travar

Não é o caso do primeiro lojista, mas o modelo precisa não excluí-lo.

**A diferença estrutural, e é ela que importa para a modelagem:** no Simples,
imposto é **uma alíquota sobre a receita**. No Presumido, imposto é **um conjunto
de tributos com bases, periodicidades e regras diferentes**.

| Tributo | Base | Periodicidade |
|---|---|---|
| PIS (cumulativo) | 0,65% sobre a receita bruta | mensal |
| COFINS (cumulativo) | 3,00% sobre a receita bruta | mensal |
| IRPJ | 15% sobre a presunção de 8% da receita (comércio) = 1,20% da receita | **trimestral** |
| Adicional de IRPJ | 10% sobre o lucro presumido que exceder R$ 20.000/mês do trimestre | **trimestral** |
| CSLL | 9% sobre a presunção de 12% da receita = 1,08% da receita | **trimestral** |
| ICMS | por UF, com crédito, ST e DIFAL interestadual | mensal |

> **Percentuais acima: conhecimento consolidado do regime, NÃO verificados por
> busca nesta rodada.** Antes de implementar o Presumido, conferir contra fonte
> oficial e cadastrar versionado, como tudo o mais.

**Três exigências que isso impõe ao modelo desde já** — e é por isso que a seção
existe:

1. **`custo(IMPOSTO)` deve suportar N linhas por pedido**, uma por tributo, cada
   uma com sua `aliquota_aplicada` e `base_calculo`. O modelo da V010 já
   suporta: são linhas, não colunas. Nada a mudar. Mas **o motor não pode assumir
   "uma linha de imposto por pedido"** em nenhum lugar.
2. **Periodicidade trimestral** significa que `competencia_em` de IRPJ/CSLL não é
   o mês do pedido. A coluna `competencia_em` da V010 já resolve isso — desde que
   o motor não a preencha cegamente com `pedido.feito_em`.
3. **ICMS depende de `uf_entrega`.** A V008 já guarda a UF exatamente por isso
   ("a diferença de ICMS interestadual depende da UF"). O motor do Presumido vai
   precisar de tabela de alíquota por par (UF origem, UF destino), versionada.
   **Fora do escopo da Fase 2.**

### 5.7 CBS/IBS — a transição 2026–2033

Estado em **12/08/2026**:

- 2026 é **ano de teste**, com alíquotas de **0,9% (CBS)** e **0,1% (IBS)**,
  totalizando 1%, compensáveis/dispensadas mediante cumprimento das obrigações
  acessórias.
- **Optantes do Simples Nacional estão FORA do teste de 2026.** Passam a
  destacar IBS e CBS a partir de 2027.
  ([simplifique.contmatic.com.br](https://simplifique.contmatic.com.br/blogs/simples-nacional-ibs-cbs-reforma-tributaria-2026),
  [tributei.net](https://tributei.net/blog/ibs-e-cbs-2026/), consultadas em
  12/08/2026 — **fontes secundárias; conferir contra a LC 214/2025 e regulamentação
  antes de implementar**.)

**Implicação para a Fase 2, e é a única que precisa ser respeitada agora:** a
regra fiscal **não pode ser uma constante nem um `if` por ano no código.** Ela é
um conjunto de registros com `vigencia_inicio`/`vigencia_fim`, e o motor
seleciona a vigente **na data do fato gerador**. A V006 já antecipou isso no
comentário do `ncm`: "guardar alíquota nesta tabela congelaria o imposto de 2026
em 2030."

A consequência de desenho: **o motor de imposto é uma interface com implementações
por regime, selecionadas por vigência**, não uma função com ramos. Para a Fase 2
existe uma implementação (Simples Anexo I) e um stub que reporta lacuna para
todos os outros regimes. Isso basta, e não fecha porta nenhuma.

---

## 6. Arredondamento

### 6.1 As três escalas

| Escala | Onde | Tipo | `RoundingMode` |
|---|---|---|---|
| **8 casas** | cálculo intermediário, em memória | `BigDecimal` | `HALF_UP` só onde a divisão exigir |
| **4 casas** | armazenamento | `NUMERIC(18,4)` | `HALF_UP` |
| **2 casas** | apresentação | string formatada | `HALF_UP` |

**`HALF_UP`, não `HALF_EVEN`.** Justificativa: `HALF_EVEN` (arredondamento
bancário) tem viés estatístico menor, mas o lojista confere na calculadora, e
calculadora faz `HALF_UP`. Um número que ele não consegue reproduzir é um número
que ele não confia — e a proposta inteira do produto é ser conferível. Legibilidade
acima de elegância (`CLAUDE.md`).

**Alíquota:** `NUMERIC(9,6)`, **fração decimal**. `0,130000` = 13%. Nunca `13.0`.
A V010 já fixou isso e o comentário explica por quê: ambiguidade de percentual é
erro por fator 100 esperando acontecer.

**Quantidade:** `NUMERIC(14,4)` — venda fracionada existe (granel, metro, kg). O
motor **não** pode assumir quantidade inteira em nenhuma multiplicação de custo
unitário.

### 6.2 Onde arredondar e onde NÃO arredondar

**NÃO arredonde:**

- Entre uma multiplicação e a soma seguinte. `0,130000 × 199,9000 = 25,987000` —
  mantenha as 6 casas até o fim da cadeia.
- A alíquota efetiva do Simples antes de aplicá-la. `0,06728` tem 5 casas
  significativas; arredondar para 4 (`0,0673`) muda o imposto de um pedido de
  R$ 1.000,00 em R$ 0,04, e de um mês de R$ 500.000,00 em **R$ 20,00**.
- Os pesos do rateio. Ver 6.3.
- Nunca converta para 2 casas e depois continue calculando. Se aparecer
  `setScale(2)` no meio de uma cadeia, é bug.

**ARREDONDE (para 4, `HALF_UP`):**

- Ao gravar `custo.valor`. Uma vez, no fim, por linha.
- Ao gravar `custo.base_calculo`.

**ARREDONDE (para 2, `HALF_UP`):** apenas na borda de saída — DTO de API, tela,
export. Nunca antes.

**Regra operacional que evita a classe inteira de bugs:** toda `BigDecimal.divide`
**declara escala e `RoundingMode` explicitamente**. A forma de um argumento só
lança `ArithmeticException` em dízima, o que transforma um erro de precisão em
erro de produção. Use sempre `divide(x, 8, RoundingMode.HALF_UP)` no
intermediário. Isso é diretamente a regra 2 do `CLAUDE.md`.

### 6.3 Rateio: o problema do centavo perdido

**O problema.** R$ 10,00 de custo de frete rateado entre 3 itens iguais.
`10 ÷ 3 = 3,3333...`. Três vezes `3,3333` são `9,9999`. Falta `0,0001`. Em
escala 2 seria R$ 0,01 — e o lojista **vê** esse centavo, porque ele soma a
coluna.

**Requisito:** `Σ quotas == total`, exatamente, sempre, para qualquer número de
itens, qualquer distribuição de pesos, e valores positivos ou negativos.

**Algoritmo — maior resto (largest remainder), com determinismo obrigatório:**

```
ENTRADA:  total (BigDecimal, escala 4)
          itens[i] com peso pi (BigDecimal, escala livre, pi >= 0)
          escala de saída E (= 4 para armazenamento, = 2 para apresentação)
SAÍDA:    quota[i], com Σ quota[i] == total exatamente

 0. sinal  := total.signum()
    T      := total.abs()                      // trabalhe em módulo; restaure o sinal no fim
    Σp     := Σ pi
    SE Σp == 0 ENTÃO
        ABORTE. Não existe rateio com peso zero. Reporte lacuna.
        (Não divida por zero, não distribua igualmente "porque dá na mesma".
         Peso zero significa que não sabemos como dividir.)

 1. // Bruto com precisão folgada — 8 casas, MUITO além da escala de saída
    bruto[i] := T × pi ÷ Σp,  divide(_, 8, HALF_UP)

 2. // Piso: trunca PARA BAIXO na escala de saída. DOWN, não HALF_UP.
    //   Com DOWN em módulo, Σ piso[i] <= T sempre. Com HALF_UP, o resíduo
    //   pode ser negativo e a distribuição do passo 5 teria de saber tirar
    //   unidade de alguém — mais caminhos, mais bugs.
    piso[i]  := bruto[i].setScale(E, RoundingMode.DOWN)
    resto[i] := bruto[i] − piso[i]              // em [0, 10^-E)

 3. residuo := T − Σ piso[i]                    // >= 0 por construção

 4. // Quantas unidades da última casa faltam distribuir
    unidade := 10^(−E)                          // 0,0001 para E=4
    n       := residuo.divide(unidade, 0, RoundingMode.HALF_UP).intValueExact()
    ASSERT 0 <= n < quantidade_de_itens         // se falhar, o passo 1 ou 2 está errado

 5. // Ordene por resto DESC. DESEMPATE DETERMINÍSTICO E OBRIGATÓRIO:
    //   1º resto DESC
    //   2º peso pi DESC        (quem contribuiu mais absorve o centavo)
    //   3º item_pedido.id ASC  (UUID; último critério, sempre desempata)
    // Sem o desempate, dois recálculos do MESMO pedido produzem distribuições
    // diferentes, a memória de cálculo deixa de reproduzir, e a regra 3 do
    // CLAUDE.md cai.
    Para os n primeiros: quota[i] := piso[i] + unidade
    Para os demais:      quota[i] := piso[i]

 6. quota[i] := quota[i] × sinal                // restaura o sinal (estorno)

 7. ASSERT Σ quota[i] == total                  // igualdade exata, não tolerância.
                                                // Falhou? Não grave nada. É bug nosso.
```

**Exemplo A — empate total.** R$ 10,0000 entre 3 itens de R$ 100,00:

| Item | bruto (8 casas) | piso (DOWN, 4) | resto | +unidade? | quota |
|---|---:|---:|---:|:--:|---:|
| A | 3,33333333 | 3,3333 | 0,00003333 | sim (1º no desempate) | **3,3334** |
| B | 3,33333333 | 3,3333 | 0,00003333 | não | **3,3333** |
| C | 3,33333333 | 3,3333 | 0,00003333 | não | **3,3333** |

Σ piso = `9,9999`, resíduo = `0,0001`, n = 1. Σ quota = **`10,0000`** ✔
Empate resolvido pelo UUID — sempre o mesmo, em toda reexecução.

**Exemplo B — pesos distintos.** R$ 10,0000 entre itens de R$ 199,90 / R$ 49,90 /
R$ 30,00 (Σp = 279,80):

| Item | bruto (8 casas) | piso | resto | +unidade? | quota |
|---|---:|---:|---:|:--:|---:|
| A (199,90) | 7,14438885 | 7,1443 | 0,00008885 | sim (2º maior) | **7,1444** |
| B (49,90) | 1,78341672 | 1,7834 | 0,00001672 | não | **1,7834** |
| C (30,00) | 1,07219442 | 1,0721 | 0,00009442 | sim (maior) | **1,0722** |

Σ piso = `9,9998`, resíduo = `0,0002`, n = 2. Σ quota = **`10,0000`** ✔

**Exemplo C — estorno.** Total `−10,0000`: aplica-se o mesmo algoritmo sobre
`10,0000` e multiplica-se por `−1` no passo 6. Resultado espelhado, soma exata.
Sem isso, `RoundingMode.DOWN` trunca **em direção ao zero** e o resíduo inverte de
sinal — bug clássico que só aparece na primeira devolução.

**Testes obrigatórios** (determinístico, asserção dura — regra 4 do `CLAUDE.md`):

- 3 itens iguais, R$ 10,00 → soma exata
- 2 itens, R$ 0,01 → um recebe, outro não; soma exata
- 1 item → recebe o total inteiro
- 7 itens com pesos primos entre si → soma exata
- total negativo → soma exata e sinal correto
- Σ pesos = 0 → aborta com lacuna, **não** divide igualmente
- **reprodutibilidade:** mesma entrada, 100 execuções, saída idêntica item a item

### 6.4 Rateio na apresentação — a coluna que precisa fechar na tela

Problema distinto e frequentemente esquecido: os valores gravados têm 4 casas, a
tela mostra 2. `Σ round(vi, 2) ≠ round(Σ vi, 2)` acontece com regularidade.

**Regra:**

1. O **total exibido** é `round(Σ vi, 2)` — o arredondamento da soma exata, nunca
   a soma dos arredondados.
2. As **parcelas exibidas** passam pelo mesmo algoritmo de 6.3 com `E = 2`, tendo
   como `total` o total exibido e como pesos os próprios valores. Assim a coluna
   fecha na tela **e** o total está correto.
3. Onde o ajuste de centavo foi aplicado, a memória de cálculo registra
   `"ajuste de arredondamento de apresentação: +R$ 0,01"`. Um centavo inexplicado
   numa tela de margem custa mais credibilidade do que vale.

### 6.5 Percentuais

`margem % = N ÷ N0`, calculado em escala 8 e exibido em 2 casas (`25,64%`).

Se `N0 == 0` (pedido de brinde, bonificação, valor zero), **a margem percentual
não existe.** Exibir `0%`, `—` ou `∞` são todos mentira. Exibir
`"não aplicável (faturamento zero)"`. Divisão por zero em percentual de margem é
um dos poucos lugares onde o código deve preferir lançar exceção a produzir número.

---

## 7. Memória de cálculo

**Entregável obrigatório: nenhum número é exibido sem que o usuário possa abrir e
ver de onde ele saiu, linha por linha.** Um número que não pode ser explicado não
deve ser mostrado.

A V010 já dimensionou as colunas para isso. O motor **preenche todas**:

| Coluna | O que o motor grava | Sem ela, não conseguimos responder |
|---|---|---|
| `natureza` | o bloco a que pertence | "de que tipo é esse custo?" |
| `valor` | resultado final, escala 4 | — |
| `base_calculo` | o valor sobre o qual a alíquota incidiu | "sobre o que incidiu?" |
| `aliquota_aplicada` | fração decimal, 6 casas | "com que taxa?" |
| `eh_estimativa` | `false` só quando a **fonte informou o valor** | "isso é fato ou conta nossa?" |
| `metodo_rateio` | identificador do método, quando houve rateio | "como foi dividido?" |
| `rateado_de_custo_id` | linha-mãe de período | "de onde veio esta parcela?" |
| `competencia_em` | período a que o custo pertence | "em que mês isso pesa?" |
| `descricao` | frase legível por humano | "por que existe esta linha?" |
| `canal_id` + `id_externo` | ponteiro para o registro na fonte | "onde eu confiro isso?" |
| `dados_origem` | id da fatura, linha do extrato | "me mostra o documento" |

**Regras de preenchimento, todas duras:**

1. **`eh_estimativa = false` exige que a fonte tenha informado o VALOR.** Aplicar
   uma alíquota cadastrada pelo próprio lojista produz `eh_estimativa = true`,
   mesmo que a alíquota esteja certa. A distinção é entre "medido" e "calculado",
   não entre "confiável" e "duvidoso".
2. **Toda linha calculada por nós grava `base_calculo` e `aliquota_aplicada`.**
   Custo de valor fixo (tarifa fixa) grava `base_calculo` = valor de referência e
   `aliquota_aplicada = NULL`. Nunca as duas nulas numa linha calculada.
3. **`descricao` é para humano e é obrigatória em linha estimada.** Formato
   sugerido: `"comissão Clássico categoria MLB1051, taxa vigente em 2026-03-14
   cadastrada pelo lojista em 2026-01-08"`.
4. **Recálculo não faz `UPDATE`.** Taxa corrigida retroativamente → linha de
   estorno (valor negativo) + linha nova, ambas com `descricao` apontando uma
   para a outra. A margem histórica continua explicável. Isso é S5 da V010 e a
   convenção 6 da V005 — apagar linha de custo muda silenciosamente uma margem já
   reportada ao cliente.
5. **A resposta de margem carrega o conjunto de IDs de `custo` que a compôs**
   (regra 3 do `CLAUDE.md` — a infraestrutura de `ConsultaAuditada` da Fase 0
   existe para isso). Se o cliente contestar, provamos ou corrigimos em minutos.
6. **A resposta carrega a lista de lacunas** (seção 9), não só os custos
   encontrados. A ausência é informação de primeira classe, não silêncio.

---

## 8. Versionamento por vigência

Especificação da **tarefa 13**.

### 8.1 O que identifica uma taxa

Chave de seleção — os campos abaixo, nesta ordem de especificidade:

| Campo | Tipo | Curinga? | Papel |
|---|---|:--:|---|
| `tenant_id` | `uuid NOT NULL` | não | Regra 1 do `CLAUDE.md`: identidade, não filtro. RLS como sempre. |
| `canal_id` | `uuid NOT NULL` | não | Taxa é do canal |
| `tipo_taxa` | `text NOT NULL` | não | `COMISSAO`, `TARIFA_FIXA`, `PARCELAMENTO`, `ANTECIPACAO`, `FRETE_SUBSIDIO`, `IMPOSTO_SIMPLES_ANEXO_I`, ... |
| `categoria_canal` | `text NULL` | **sim** | Categoria **na fonte** (`MLB1051`), não categoria nossa. `NULL` = vale para todas |
| `tipo_anuncio` | `text NULL` | **sim** | `CLASSICO`, `PREMIUM`, `GRATIS`, `LEGADO`. `NULL` = vale para todos |
| `faixa_valor_min` | `numeric(18,4) NULL` | **sim** | **inclusiva** |
| `faixa_valor_max` | `numeric(18,4) NULL` | **sim** | **EXCLUSIVA** |
| `vigencia_inicio` | `timestamptz NOT NULL` | não | **inclusiva** |
| `vigencia_fim` | `timestamptz NULL` | — | **EXCLUSIVA**. `NULL` = vigência aberta |

**Faixa e vigência são semiabertas `[min, max)` — não negociável.** Com limites
fechados, um pedido de exatamente R$ 79,00 casaria com duas linhas
(`[12,50; 79,00]` e `[79,00; ∞]`) e o resultado dependeria da ordem do `ORDER BY`.
Semiaberto elimina a ambiguidade por construção, e R$ 79,00 é precisamente um
limiar real do Mercado Livre — o bug aconteceria na primeira semana.

**Valores:**

| Campo | Tipo | Nota |
|---|---|---|
| `percentual` | `numeric(9,6) NULL` | **fração decimal**: `0,130000` = 13%. Mesma convenção da V010 |
| `valor_fixo` | `numeric(18,4) NULL` | Tarifa por item/pedido |
| `valor_minimo` / `valor_maximo` | `numeric(18,4) NULL` | Piso e teto da taxa, quando existirem |

`CHECK (percentual IS NOT NULL OR valor_fixo IS NOT NULL)` — taxa sem valor
nenhum é linha inútil que só serve para mascarar lacuna.

**Procedência (regra 5 no schema, mesmo espírito de `custo.eh_estimativa`):**

| Campo | Tipo | Papel |
|---|---|---|
| `confianca` | `text NOT NULL` | `CONFIRMADO_FONTE_OFICIAL`, `INFORMADO_PELO_LOJISTA`, `ESTIMADO` |
| `fonte_url` | `text NULL` | de onde veio |
| `consultado_em` | `timestamptz NULL` | quando |
| `observacao` | `text NULL` | contexto para o humano que revisar daqui a um ano |

`confianca` é o que permite a interface dizer *"comissão 13% — informada por você
em 08/01/2026"* em vez de apresentar palpite como fato. **Nenhuma taxa entra sem
`confianca` preenchida.**

### 8.2 A regra de seleção

> **O cálculo de um pedido usa a taxa vigente NA DATA DO FATO GERADOR. Nunca a
> taxa atual.**

**Qual data, por tipo de taxa** — e isto precisa ser explícito, porque "a data do
pedido" é ambígua:

| `tipo_taxa` | Data do fato gerador |
|---|---|
| `COMISSAO`, `TARIFA_FIXA`, `PARCELAMENTO`, `FRETE_SUBSIDIO` | `pedido.feito_em` — a tarifa é contratada no momento da venda |
| `ANTECIPACAO` | data da antecipação (`custo.competencia_em` da linha-mãe) |
| `IMPOSTO_*` | primeiro dia do mês de competência da apuração |
| custo de devolução (frete reverso) | `devolucao.aberta_em` |

**Fuso horário — bug esperado, evitado de graça.** `feito_em` é `timestamptz`. As
fronteiras de vigência de taxa de marketplace são anunciadas em **hora de
Brasília**. Uma venda às 22:00 de 01/03 em `America/Sao_Paulo` é 01:00 de 02/03
em UTC. Comparar sem converter escolhe a tabela errada por três horas de pedidos
todo dia de virada de tarifa.

> **Regra:** a comparação de vigência é feita convertendo ambos os lados para
> `America/Sao_Paulo`. Isto é teste unitário obrigatório, com um caso exatamente
> em 23:30 do dia anterior à virada.

**Consulta:**

```sql
SELECT *
  FROM taxa_canal
 WHERE tenant_id  = :tenant
   AND canal_id   = :canal
   AND tipo_taxa  = :tipo
   AND :momento >= vigencia_inicio
   AND (vigencia_fim   IS NULL OR :momento < vigencia_fim)
   AND (categoria_canal IS NULL OR categoria_canal = :categoria)
   AND (tipo_anuncio    IS NULL OR tipo_anuncio    = :tipo_anuncio)
   AND (faixa_valor_min IS NULL OR :valor >= faixa_valor_min)
   AND (faixa_valor_max IS NULL OR :valor <  faixa_valor_max)
 ORDER BY especificidade DESC
 LIMIT 2;                                  -- 2, não 1. Ver abaixo.
```

**`especificidade`** é coluna **`GENERATED ALWAYS AS ... STORED`**, para que a
ordenação seja estável, indexável e não dependa da consulta:

```
especificidade = (categoria_canal IS NOT NULL)::int * 4
               + (tipo_anuncio    IS NOT NULL)::int * 2
               + (faixa_valor_min IS NOT NULL OR faixa_valor_max IS NOT NULL)::int * 1
```

Pesos 4/2/1 dão ordem total sem empate entre combinações distintas: categoria é o
discriminador mais específico, faixa o menos.

**`LIMIT 2` e não `LIMIT 1`, de propósito.** Se voltarem duas linhas com a
**mesma** `especificidade`, a seleção é ambígua e o resultado dependeria da ordem
física da tabela.

> **Regra:** ambiguidade **falha alto**. Não escolhe a primeira, não escolhe a
> mais recente, não escolhe a menor. Reporta erro de cadastro identificando as
> duas linhas. Um número silenciosamente errado é pior que um erro visível.

**Prevenção no banco** — não confie só na aplicação. Com `btree_gist`:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE taxa_canal ADD CONSTRAINT ex_taxa_canal_sem_sobreposicao
  EXCLUDE USING gist (
      tenant_id       WITH =,
      canal_id        WITH =,
      tipo_taxa       WITH =,
      categoria_canal WITH =,      -- NULL não conflita com NULL em gist: ver nota
      tipo_anuncio    WITH =,
      numrange(faixa_valor_min, faixa_valor_max, '[)')  WITH &&,
      tstzrange(vigencia_inicio, vigencia_fim, '[)')    WITH &&
  );
```

> **Nota para o arquiteto:** `NULL WITH =` **não** conflita em `EXCLUDE` — duas
> linhas com `categoria_canal IS NULL` não serão barradas por essa coluna. Isso é
> aceitável (as duas seriam curinga, com a mesma especificidade, e a aplicação
> falha alto no `LIMIT 2`), mas se quiser cobertura total no banco, use um
> sentinela `'*'` em vez de `NULL` para curinga. **Decisão do arquiteto.** Se
> escolher o sentinela, a consulta de seleção muda de `IS NULL OR =` para
> `IN ('*', :valor)` e a `especificidade` compara contra `'*'`.

Índice de apoio:

```sql
CREATE INDEX ix_taxa_canal_selecao
    ON taxa_canal (tenant_id, canal_id, tipo_taxa, vigencia_inicio DESC);
```

### 8.3 Quando não existe taxa cadastrada para a data

> **Não inventar. Reportar ausência.**

Comportamento, sem exceções:

1. **Nenhuma linha de `custo` é criada.** Custo zero e custo desconhecido são
   coisas diferentes — a V010 é explícita: *"Zero é permitido e significativo ('o
   frete custou zero e nós sabemos disso'), diferente de não ter linha nenhuma
   ('não sabemos quanto custou')."* Gravar zero apagaria a lacuna.
2. **Uma lacuna é registrada** na resposta, com: `tipo_taxa`, os
   discriminadores usados na busca, a data consultada, e o efeito no número
   (seção 9).
3. **O pedido é marcado como margem INCOMPLETA**, não como margem calculada.
4. **Nada de fallback.** Nem "a taxa mais recente antes da data", nem "a taxa
   vigente hoje", nem "a média das categorias". Todas essas produzem um número
   plausível e errado — o pior tipo de saída para este produto.
5. **A interface pede o cadastro**, com os discriminadores já preenchidos: *"não
   sei a comissão do Mercado Livre para a categoria MLB1051, anúncio Clássico, em
   14/03/2026. Cadastre e eu recalculo 47 pedidos."*

O item 5 é o que transforma a lacuna de defeito em funcionalidade. O sistema que
diz "não sei, me ensine" é mais confiável que o que chuta — e, na prática, é o
onboarding do produto.

### 8.4 Correção retroativa de taxa

O lojista cadastra 13%, descobre que era 12,5%, corrige.

1. A linha antiga **não é apagada nem editada**. Recebe `vigencia_fim`, ou uma
   linha nova com vigência sobreposta é **rejeitada** pela `EXCLUDE` — o que está
   correto: corrigir vigência passada exige encerrar a linha errada
   explicitamente.
2. As linhas de `custo` já geradas **não são atualizadas**. Recálculo gera
   **estorno + linha nova** (S5 da V010), com `descricao` explicando.
3. **O recálculo é acionado, não automático.** Uma correção de taxa que reescreve
   silenciosamente a margem de 400 pedidos que o lojista já olhou é pior que a
   taxa errada. A ação é dele, com prévia de quantos pedidos mudam e de quanto
   muda o total.

---

## 9. O que o sistema NÃO pode afirmar

Esta seção é o produto tanto quanto a fórmula. Um número errado apresentado com
confiança destrói a proposta.

### 9.1 Catálogo de lacunas

| # | Lacuna | Como detectar | Efeito no número | Direção do viés |
|---|---|---|---|---|
| 1 | **Custo do produto não cadastrado** | `item_pedido.variacao_id IS NULL` ou `variacao.custo_unitario_atual IS NULL` ou ausência de linha `MERCADORIA` | Falta a **maior** parcela de custo | **Margem MUITO superestimada** |
| 2 | **Item não casado com o catálogo** | `item_pedido.variacao_id IS NULL` (dívida 3 da Fase 1) | Sem CMV e sem margem por SKU | **Superestimada** |
| 3 | **Taxa não cadastrada para a vigência** | seleção da 8.2 volta vazia | Falta comissão e/ou tarifa fixa | **Superestimada** |
| 4 | **Ads não rastreado por pedido** | sem linha `ADS` no pedido e sem linha-mãe de período | N3 = N2 | **Superestimada** |
| 5 | **Ads rateado (linha-mãe existe)** | `ADS` com `rateado_de_custo_id NOT NULL` | Correto **no agregado**, atribuição **arbitrária** por pedido | **Indeterminada por pedido**, correta no mês |
| 6 | **Regime tributário não configurado** | tenant sem regime vigente | Falta imposto inteiro | **Superestimada** (4% a 19% do faturamento) |
| 7 | **RBT12 não informado** (Simples) | campo vazio no mês de competência | Não calculamos. Se **derivarmos** da nossa receita: faixa baixa demais | **Superestimada** — por isso não derivamos |
| 8 | **ICMS-ST não sinalizado** | `sujeicao_st = NAO_INFORMADO` | Alíquota cheia sobre produto que já pagou ICMS | **SUBESTIMADA** ← única que erra para baixo |
| 9 | **PIS/COFINS monofásico não sinalizado** | idem | idem | **SUBESTIMADA** |
| 10 | **Frete real não informado pela fonte** | sem linha `FRETE` num pedido enviado | Falta o custo logístico | **Superestimada** |
| 11 | **Antecipação não configurada** | tenant sem flag e sem linhas `TAXA_ANTECIPACAO` | Custo invisível — **não deixa rastro em campo nenhum** | **Superestimada, silenciosamente** |
| 12 | **Devolução em curso** | `devolucao.status IN (ABERTA, EM_ANALISE, EM_MEDIACAO)` | Resultado ainda pode piorar; nunca melhorar | **Superestimada — teto conhecido** |
| 13 | **Destino do produto devolvido desconhecido** | `destino_produto = 'NAO_RETORNOU'` sem confirmação | Diferença entre perder o frete e perder a mercadoria inteira | **Indeterminada, com faixa calculável** |
| 14 | **Embalagem estimada** | `EMBALAGEM` com `eh_estimativa = true` | Valor pequeno, viés desconhecido | **Ruído**, não viés |
| 15 | **Canais sobrepostos** (decisão 0017) | mais de um canal no escopo, sem declaração de não sobreposição | Faturamento e custos potencialmente dobrados | **Valor absoluto INDETERMINADO** (o % pode estar certo) |
| 16 | **`valor_repasse_previsto` diverge** | `delta < 0` na conferência 2.6 | Existe custo que não vemos, de valor **conhecido** | **Superestimada em exatamente `\|delta\|`** ← quantificável |
| 17 | **`valor_repasse_previsto` ausente** | `NULL` | Sem conferência externa. Nenhum erro conhecido, e nenhuma garantia | **Desconhecida** |
| 18 | **Status do pedido desatualizado** | dívida 1 da Fase 1 | Pedido cancelado contado como venda | **Superestimada** |
| 19 | **Custo `OUTRO` presente** | linha com natureza `OUTRO` | Custo não classificado entra na soma, mas não na narrativa | Valor correto, **explicação incompleta** |

### 9.2 A regra do teto — como ser honesto e ainda ser útil

O sistema **não** precisa se calar sempre que falta um dado. Ele precisa dizer a
verdade sobre o que sabe. Três respostas possíveis, e a escolha é mecânica:

**a) Margem calculada.** Nenhuma lacuna. Exibe o número.
> "Resultado: R$ 44,96 (22,49%). Todos os custos conhecidos foram considerados."

**b) Margem com teto.** Todas as lacunas presentes têm viés **para cima**
(#1,2,3,4,6,7,10,11,12,18). O número calculado é um **limite superior** — o real é
menor. Isto é uma afirmação verdadeira e acionável.
> "Sua margem é de **no máximo** 22,49%. Não considerei: custo do produto em 3
> itens, taxa de antecipação. O valor real é menor."

**c) Margem indeterminada.** Há lacunas de viés oposto (#8, #9) ou de direção
desconhecida (#13, #15, #17) misturadas com as de viés para cima. Não existe
teto honesto.
> "Não consigo calcular sua margem com confiança neste período. Faltam: [lista].
> Alguns desses fazem o número subir e outros descer."

**A regra que fecha a seção:** o sistema **nunca** exibe um número de margem sem
o rótulo (a), (b) ou (c). Não existe margem "sem qualificador". Se o rótulo não
couber na tela, o número também não cabe.

### 9.3 Grau de completude por pedido

Métrica simples, exibível, que resume o acima. Para cada pedido, uma checklist
das naturezas **esperadas** dada a situação do pedido:

| Natureza esperada | Quando é esperada |
|---|---|
| `MERCADORIA` | sempre |
| `COMISSAO_CANAL` | canal é marketplace |
| `TARIFA_FIXA_CANAL` | canal é marketplace **e** valor unitário abaixo do limiar vigente |
| `FRETE` | `pedido.enviado_em IS NOT NULL` |
| `IMPOSTO` | tenant tem regime configurado |
| `EMBALAGEM` | tenant configurou custo de embalagem |
| `TAXA_PARCELAMENTO` | `quantidade_parcelas > 1` **e** anúncio não embute |
| `TAXA_ANTECIPACAO` | tenant declarou que antecipa |
| `FRETE_REVERSO` | existe devolução com produto retornando |
| `REEMBOLSO` | existe devolução aprovada |

`completude = naturezas presentes ÷ naturezas esperadas`, exibida como
`"7 de 9 custos conhecidos"` — **não** como percentual. Percentual de completude
convida a ser lido como percentual de confiança, e não é: faltar `MERCADORIA`
(80% do custo) e faltar `EMBALAGEM` (1%) contam igual na fração. O texto com a
lista das duas faltantes é honesto; o "78% completo" não é.

---

## 10. Lacunas do modelo atual

Avaliação do enum `NaturezaCusto` da V010 (16 valores) contra tudo o que este
documento precisa.

### 10.1 O que já está certo e não deve mudar

As 16 naturezas cobrem o cálculo completo das seções 2 a 5. Três acertos
estruturais que a Fase 2 deve preservar sem discussão:

- **`TARIFA_FIXA_CANAL` separada de `COMISSAO_CANAL`.** São regras de vigência
  diferentes (percentual vs valor por faixa) e, em 2026, possivelmente eixos
  diferentes. Fundi-las obrigaria a remodelar agora.
- **`TAXA_PARCELAMENTO` separada de `TAXA_ANTECIPACAO`.** Custos distintos, com
  fatos geradores em datas distintas (seção 8.2) e um deles embutido na comissão
  do Premium.
- **`FRETE` separado de `FRETE_REVERSO`.** Tratamento fiscal idêntico, mas
  atribuição de causa completamente diferente — e o custo real da devolução é um
  dos diferenciais declarados do produto.

E os contratos S1–S6 resolvem, antes de a Fase 2 começar, os três modos de falha
mais caros: dupla contagem entre níveis (S1/S3), dupla contagem em rateio (S4) e
mutação de histórico (S5).

### 10.2 O que falta

Nada bloqueia a Fase 2. Duas ausências que vão doer, em ordem:

**1. Não há natureza para a perda de valor da mercadoria devolvida.**

Quando o produto volta e vira `DESCARTE` ou
`ESTOQUE_COMO_SEGUNDA_LINHA` (`devolucao.destino_produto`, V009), há uma perda
**adicional** ao frete reverso e ao reembolso: o valor da mercadoria que não
volta a valer o que valia. Hoje ela cairia em `OUTRO`, e o produto perde a
capacidade de responder *"quanto as devoluções me custaram de verdade"* — que é
uma das perguntas que o documento de escopo lista como diferencial.

Sugestão: `PERDA_DE_ESTOQUE`, ou reaproveitar `MERCADORIA` com `devolucao_id`
preenchido. **A segunda opção não exige migration** e é provavelmente suficiente:
`MERCADORIA` com `devolucao_id NOT NULL` é semanticamente "mercadoria perdida na
devolução", e o índice `ix_custo_tenant_devolucao` já responde à consulta.
**Recomendação: usar `MERCADORIA` + `devolucao_id`, e documentar a convenção.**
Simplicidade acima de completude taxonômica.

**2. Não há natureza para o custo de reprocessamento da devolução.**

O escopo do produto cita explicitamente "frete reverso + reprocessamento +
perda". O reprocessamento (conferir, testar, reembalar, reetiquetar) é mão de
obra, e hoje só cabe em `OUTRO` ou `TARIFA_ADMINISTRATIVA`. É custo **estimado
por natureza** (ninguém cronometra), então: linha com `eh_estimativa = true`,
valor por devolução cadastrado pelo lojista, `metodo_rateio =
'REPROCESSAMENTO_POR_DEVOLUCAO'`. `TARIFA_ADMINISTRATIVA` com `devolucao_id`
preenchido resolve sem migration, pelo mesmo raciocínio do item 1.

**Recomendação final sobre o enum: não mexer na V010 na Fase 2.** As duas
ausências se resolvem por convenção de uso das naturezas existentes combinadas
com `devolucao_id`. Migration em tabela central para ganhar precisão de
nomenclatura é troca ruim para uma fundadora solo com 20h/semana.

### 10.3 Pendências de modelagem que este documento levanta

Não implementadas por conta própria — são escopo novo e/ou decisão de negócio
(`CLAUDE.md`, "quando parar e perguntar"):

| # | Pendência | Seção | Por que não decidi sozinha |
|---|---|---|---|
| P1 | Flag `sujeicao_st` tri-estado em produto/variação | 5.5 | Migration em V006; e a decisão de derivar ou não do CEST é do contador |
| P2 | Flag `pis_cofins_monofasico` tri-estado | 5.5 | idem |
| P3 | `frete_cobrado_compoe_base_tributavel` por tenant | 3.3 | Decisão do contador do lojista, não técnica |
| P4 | `RBT12` mensal cadastrado por tenant | 5.2 | Dado externo; define se o imposto é calculável |
| P5 | `listing_type_id` e `category_id` do ML preservados no pedido | 4.2 | Cabe em `dados_origem` (decisão 0002) — confirmar com o adaptador |
| P6 | Carry-over de crédito de devolução entre meses | 5.4 | Complexidade real; a simplificação declarada resolve o caso comum |
| P7 | Peso/cubagem em `variacao` para tarifa variável do ML | 4.3 | Escopo novo; a fonte de nível 1 provavelmente torna desnecessário |
| P8 | Tabela de ICMS por par de UF (Lucro Presumido) | 5.6 | Fora do escopo da Fase 2 |

---

## Fontes consultadas

Todas em **12 de agosto de 2026**.

**Mercado Livre — oficiais:**
- [Quanto custa vender um produto? (Ajuda)](https://www.mercadolivre.com.br/ajuda/quanto-custa-vender-um-produto_1338)
- [Custos por oferecer frete grátis pelo Mercado Envios (Ajuda)](https://www.mercadolivre.com.br/ajuda/CustosdefretegratispeloMercadoEnvios_3362)
- [Como funcionam as taxas do Mercado Livre (Central do Vendedor)](https://vendedores.mercadolivre.com.br/nota/como-funcionam-as-taxas-do-mercado-livre)
- [Custos por vender (Developers)](https://developers.mercadolivre.com.br/pt_br/comissao-por-vender)
- [Prices API / listing_prices (Developers)](https://developers.mercadolivre.com.br/en_us/price-apl)
- [Tipos de publicação / listing types (Developers)](https://developers.mercadolivre.com.br/pt_br/tutorial-tipos-de-publicacao-y-atualizacao-de-artigos)

**Fiscais — oficiais:**
- [Lei Complementar 123/2006, texto consolidado (Planalto)](https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp123.htm)
- [ANEXO I — Alíquotas e Partilha do Simples Nacional, Comércio (Receita Federal)](http://normas.receita.fazenda.gov.br/sijut2consulta/anexoOutros.action?idArquivoBinario=48430)
- [Resolução CGSN nº 140/2018 (íntegra)](https://bibliotecadigital.gestao.gov.br/bitstream/123456789/529809/2/Resolu%C3%A7%C3%A3o%20CGSN%20140,%20de%2022%20de%20maio%20de%202018%20-%20Original.pdf)
- [Resolução CGSN nº 140/2018, art. 17 — devoluções (LegisWeb)](https://www.legisweb.com.br/noticia/?legislacao=360430)

**Secundárias — citadas para registrar DIVERGÊNCIA, nunca como dado do sistema:**
- [Tarifas do Mercado Livre 2026 (TecnoSpeed)](https://blog.tecnospeed.com.br/tarifas-do-mercado-livre/) e [Custos ML 2026 (Joom Pulse)](https://blog.joompulse.com/2026/02/12/custos-mercado-livre-o-que-muda-para-sellers-2026/) — mudança de março/2026 na tarifa fixa
- [Guia de frete ML 2026 (Duoke)](https://www.duoke.com/pt/blog/article/329-guia-custos-frete-mercado-livre-brasil-2026) — subsídio por reputação
- [Taxa Mercado Livre (Rally de Vendas)](https://rallydevendas.com.br/como-vender-em/taxa-mercado-livre-quanto-cobra) — parcelamento e antecipação
- [Comissão ML (E-commerce na Prática)](https://ecommercenapratica.com/blog/comissao-mercado-livre/) e [Taxas ML (GoSmarter)](https://gosmarter.com.br/taxas-mercado-livre/) — faixas de comissão
- [IBS e CBS 2026 (Contmatic)](https://simplifique.contmatic.com.br/blogs/simples-nacional-ibs-cbs-reforma-tributaria-2026) e [IBS e CBS na prática (Tributei)](https://tributei.net/blog/ibs-e-cbs-2026/) — Simples fora do ano-teste

**Não confirmado por fonte oficial nesta rodada, e portanto NÃO codificável:**
percentual de comissão por categoria · valores da tarifa fixa por faixa · se a
tarifa fixa virou variável em março/2026 · percentual de subsídio de frete por
reputação · taxas de parcelamento e antecipação · percentuais de repartição do
Anexo I linha a linha · percentuais do Lucro Presumido · regulamentação de
CBS/IBS para o Simples em 2027.
