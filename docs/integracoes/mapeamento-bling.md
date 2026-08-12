# Mapeamento — Bling API v3 → modelo canônico

**Data desta versão:** 12 de agosto de 2026
**Adaptador correspondente:** ainda não escrito (esta rodada é só fixture + mapeamento)
**Decisão relacionada:** `docs/decisoes/0013-bling-como-primeiro-erp.md` — já registra que
"as fixtures foram escritas a partir do formato documentado da API, **sem** uma resposta
real capturada" e que isso é risco assumido, anotado como pendente de validação.
**Última tentativa de validação contra a API real nesta sessão:** 12/08/2026, **sem
sucesso**.

---

## Status de verificação — leia antes de confiar em qualquer campo abaixo

Tentei abrir a documentação oficial (`developer.bling.com.br`) com WebFetch nesta sessão:

| URL tentada | Resultado |
|---|---|
| `developer.bling.com.br/referencia` | Página carregada só com navegação/rodapé (SPA — o conteúdo real é renderizado por JavaScript que o WebFetch não executa) |
| `developer.bling.com.br/api-docs` | HTTP 404 |
| `developer.bling.com.br/pedidos-vendas` | HTTP 404 (slug incorreto — não encontrei o slug real sem navegação interativa) |
| `developer.bling.com.br/openapi.json` | HTTP 404 |

**Não consegui, nesta sessão, confirmar um único campo do Bling API v3 contra a
documentação ao vivo.** A decisão 0013 já previa exatamente este risco antes mesmo de eu
tentar. O que segue é reconstrução do formato **publicamente conhecido e estável** da API v3
do Bling (REST/JSON, recursos `/pedidos/vendas` e `/produtos`, ambos com essa forma geral há
alguns anos), na mesma lógica de confiança usada no mapeamento do Mercado Livre:

- 🟢 Alta confiança (nome/formato de campo amplamente conhecido)
- 🟡 Confiança média (campo existe, formato exato incerto)
- 🔴 Baixa confiança / reconstrução deliberada

O Bling v3, sendo API mais nova (lançada ~2022) e com integração nativa de marketplaces
(inclusive Mercado Livre) dentro do próprio produto, tem uma área de menor confiança
específica: os campos que identificam **de qual marketplace o pedido veio** (`loja`,
`numeroPedidoCompra`, `intermediador`). Esses são os que mais importam para este projeto
(é como o adaptador teria que casar um pedido do Bling com o mesmo pedido já ingerido do
Mercado Livre) e são também os que tenho menos confiança estrutural.

---

## Fixtures gravadas

- `backend/src/test/resources/fixtures/bling/pedido-venda.json` — 🟢 estrutura geral do
  pedido e dos itens; 🔴 bloco `intermediador` (ver nota acima).
- `backend/src/test/resources/fixtures/bling/produto-com-variacoes.json` — 🟢 conceito de
  produto pai (`formato: "V"`) com array `variacoes[]`; 🟡 nomes exatos de subcampos de
  estoque/atributo.

---

## Tabela de mapeamento — `canal`

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `loja.id` | `canal.dados_origem.loja_id` | 🟡. No Bling, "loja" é o canal de venda cadastrado dentro do próprio Bling (pode representar o Mercado Livre, um site próprio etc.) — **não confundir com `canal` do nosso modelo**, que é a integração configurada no nosso sistema. Mapeamento N:1 possível: várias "lojas" do Bling podem cair no mesmo `canal_id` nosso, ou vice-versa — decisão de configuração, não de parsing. |
| — | `canal.id_externo` | O id da empresa/conta Bling normalmente vem de outro recurso (`/empresas` ou dados da própria credencial OAuth), não do pedido. Não confirmado. |

## Tabela de mapeamento — `cliente` (de `contato`)

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `contato.id` | `cliente.id_externo` | 🟢. |
| `contato.nome` | `cliente.nome` | 🟢. **Diferença importante em relação ao Mercado Livre**: o Bling, sendo ERP, normalmente TEM o nome completo do comprador (veio de nota fiscal / cadastro), ao contrário do ML que só dá apelido. Isso favorece o Bling como fonte de enriquecimento de `cliente.nome` quando o mesmo comprador existir nos dois canais. |
| `contato.tipoPessoa` (`"F"`/`"J"`) | `cliente.tipo` | 🟢 conceito conhecido; 🟡 valores exatos (`"F"`/`"J"` vs. outra convenção) não confirmados nesta sessão — tratar como hipótese e validar com payload real. |
| `contato.numeroDocumento` | `cliente.documento_hash` (+ `documento_mascarado`) | 🟢 o campo existe e normalmente vem **só dígitos** (a fixture usa `"12345678909"`, sem máscara) — mas isso não está confirmado; o adaptador deve normalizar removendo não-dígitos de qualquer forma, e nunca gravar o valor em claro (V007). |
| `contato.email` | `cliente.email` | 🟢. |
| `contato.telefone` | `cliente.telefone` | 🟢 campo existe; 🟡 formato (com ou sem DDI, com ou sem máscara) não confirmado — normalizar para E.164 no adaptador com tolerância a formato torto (mesma regra do telefone na V007: não rejeitar, só normalizar o que der). |
| `contato.endereco.*` | **não vai para `cliente`** | Ver "o que o canônico exige e a fonte não fornece" — endereço fica no `pedido` (CEP/cidade/UF), nunca em `cliente` (minimização de LGPD, V007). O Bling manda endereço completo (rua, número, complemento); **o adaptador descarta rua/número/complemento deliberadamente**, mesmo a fonte fornecendo — é filtro de entrada, não limitação da fonte. |

## Tabela de mapeamento — `pedido`

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `id` | `pedido.id_externo` | 🟢. |
| `numero` | `pedido.codigo_exibicao` | 🟢 conceito conhecido (número do pedido dentro do Bling); pode divergir do número que o comprador vê no marketplace de origem — nesse caso, `numeroLoja` é o candidato mais próximo do id do marketplace (ver linha abaixo). |
| `numeroLoja` | `pedido.dados_origem.numero_loja` (e cruzamento de idempotência) | 🟡. Hipótese central deste mapeamento: quando o pedido do Bling **veio de uma sincronização automática com o Mercado Livre**, este campo carregaria o id do pedido no marketplace de origem — o que permitiria casar o `pedido` já criado pelo adaptador de ML com a entrada equivalente do Bling **sem duplicar**. **Não confirmado.** Se estiver errado, o pedido do Bling vira uma segunda linha em `pedido` para a mesma venda real — risco alto, ver seção de gaps. |
| `situacao.valor` | `pedido.status_origem` | 🟢 conceito (Bling usa texto livre configurável por conta, não um enum fixo pequeno — ex.: "Em aberto", "Atendido", "Cancelado", mas o lojista pode ter situações customizadas). |
| `situacao.valor` (traduzido) | `pedido.status` | 🔴 **o domínio de `situacao` no Bling é configurável por conta** (o usuário pode criar situações customizadas no painel). Isso é estruturalmente diferente do domínio fechado do ML. O adaptador precisa de uma tabela de tradução por tenant (situação Bling → status canônico), não um mapeamento fixo de código — e quando a situação for uma que o adaptador não conhece, cai em nada equivalente pronto no domínio canônico: melhor logar divergência e não adivinhar. |
| `data` | `pedido.feito_em` | 🟡 o Bling manda só data (`"2024-03-15"`, sem hora nem fuso) neste recurso, aparentemente — se confirmado, é uma perda de precisão em relação ao `timestamptz` exigido pelo canônico (convenção 7 da V005). O adaptador teria que assumir um horário (meio-dia America/Sao_Paulo, por convenção) e **isso precisa ficar registrado como aproximação**, não como o horário real do pedido. |
| `dataSaida` | `pedido.enviado_em` | 🟡 mesma ressalva de granularidade de data. |
| — | `pedido.pago_em` | Ver gaps — não identifiquei campo de data de pagamento neste recurso; pode estar em `parcelas[].dataVencimento` (que é vencimento, não pagamento) ou em outro recurso financeiro do Bling não coberto por este pedido. |
| `totalProdutos` | `pedido.valor_bruto_itens` | 🟢 nome de campo condizente com o propósito; conferir se inclui ou não o desconto de item antes de somar. |
| `desconto.valor` (quando `unidade="REAL"`) | `pedido.valor_desconto` | 🟡 o Bling representa desconto como valor OU percentual (`unidade: "REAL"` vs `"PERCENTUAL"`) — quando percentual, o adaptador tem que calcular o valor em reais a partir de `totalProdutos`, e isso é um cálculo derivado (documentar `eh_estimativa` não se aplica a `pedido` diretamente, mas o raciocínio vale: não é cópia direta do dado). |
| `transporte.frete` | `pedido.valor_frete_cobrado` | 🟡 **precisa confirmação**: este campo pode representar o frete cobrado do comprador OU o custo de frete pago pelo lojista, dependendo de `transporte.fretePorConta` (CIF/FOB — 0/1/2/9, domínio de "por conta de quem é o frete", comum em ERPs brasileiros). Tratar como cobrado só quando `fretePorConta` indicar que o destinatário paga; senão é custo, não receita — ver armadilha de dinheiro. |
| `total` | `pedido.valor_total_pedido` | 🟢. |
| — | `pedido.valor_repasse_previsto` | Não se aplica ao Bling como ERP (é conceito de marketplace) — fica `NULL` sempre que a origem for `ERP_BLING`. Isso é esperado e não é uma lacuna a resolver. |
| `parcelas[].formaPagamento` | `pedido.forma_pagamento` | 🔴 o Bling usa `id` numérico de forma de pagamento **configurável por conta** (cada empresa cadastra as suas formas de pagamento no Bling, com ids próprios) — não é um enum fixo da API. Precisa de tabela de tradução por tenant, igual à de `situacao`. Sem essa tabela, não dá para preencher `pedido.forma_pagamento` com segurança — melhor `NULL` do que chute. |
| `parcelas.length` | `pedido.quantidade_parcelas` | 🟢. |
| `contato.endereco.cep`/`municipio`/`uf` (ou `transporte.etiqueta`) | `pedido.cep_entrega`/`cidade_entrega`/`uf_entrega` | 🟡 **dois endereços concorrentes na fixture**: o do `contato` (cadastro do cliente) e o do `transporte.etiqueta` (destino real da entrega, que pode ser diferente — presente de aniversário, endereço comercial etc.). **A regra tem que ser usar `transporte.etiqueta`, nunca `contato.endereco`**, porque é o que efetivamente define a UF de destino para frete e ICMS interestadual — usar o errado corrompe justamente a análise regional que essas colunas existem para viabilizar. |

## Tabela de mapeamento — `item_pedido` (de `itens[]`)

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `itens[].id` | `item_pedido.id_externo` | 🟢. |
| `itens[].codigo` | `item_pedido.sku_origem` | 🟢. |
| `itens[].descricao` | `item_pedido.titulo_origem` | 🟢. |
| `itens[].quantidade` | `item_pedido.quantidade` | 🟢 já vem como número com casas decimais — bom sinal para venda fracionada (granel), compatível com `NUMERIC(14,4)`. |
| `itens[].valor` | `item_pedido.valor_unitario_bruto` | 🟢 nome sugere unitário; 🟡 não confirmado se é bruto ou já líquido do `itens[].desconto`. |
| `itens[].desconto` | `item_pedido.valor_desconto_linha` | 🟡 mesma incerteza REAL vs. PERCENTUAL do desconto de pedido — aqui não vi indicação de `unidade` no nível do item na documentação reconstruída; tratar como valor em reais é a hipótese mais simples, mas **não confirmada**. |
| — (calculado) | `item_pedido.valor_total_linha` | 🟡 diferente do ML: aqui é plausível que exista um campo pronto de total de linha que eu não conseguí confirmar; até validar, o adaptador calcula `quantidade × valor − desconto`, seguindo a mesma regra de "calcula uma vez, na ingestão, quando a fonte não manda". |
| `itens[].produto.id` | resolve `item_pedido.variacao_id` (via `uq_variacao_origem`) | O Bling só manda o id do produto/variação como referência; o adaptador busca a `variacao` já sincronizada por `(canal_id, id_externo)`. Se não achar (produto nunca sincronizado), `variacao_id` fica `NULL` e a linha ainda é válida (regra da V008). |
| `itens[].aliquotaIPI` | `custo` (natureza `IMPOSTO`) OU fica de fora | 🔴 fora do escopo da Fase 1 — a V006 é explícita que alíquota fiscal não mora no produto/pedido, é tabela versionada da Fase 2 (tarefa 13). Por ora, registrar em `dados_origem` do item, não interpretar. |

## Tabela de mapeamento — `custo` (frete e tributação do pedido Bling)

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `transporte.frete` (quando `fretePorConta` indicar que o lojista paga) | `custo` (`natureza='FRETE'`) | 🔴 ver ressalva de CIF/FOB acima — depende de interpretar `fretePorConta` corretamente, o que não pôde ser confirmado. |
| `tributacao.totalICMS`, `totalIPI`, `totalICMSST` | `custo` (`natureza='IMPOSTO'`) por natureza, quando aplicável | 🟡 os nomes dos campos são plausíveis para um ERP brasileiro; a Fase 1 registra esses valores como o que a fonte informou (não calcula imposto — isso é Fase 2/tarefa 13), então isso pode virar `custo(IMPOSTO, eh_estimativa=false)` só quando o Bling de fato entrega o valor já apurado (não uma alíquota a aplicar). |
| — | `custo` (`natureza='COMISSAO_CANAL'`) | **Não presente nesta fixture.** O Bling, como ERP, não cobra comissão de marketplace — isso é uma taxa do Mercado Livre, não do Bling. Se o mesmo pedido for ingerido tanto pelo adaptador de ML quanto pelo de Bling, a comissão **só deve ser gravada uma vez** (pelo adaptador de ML) — gravar pelos dois duplicaria custo. Isso é uma regra de orquestração da ingestão (tarefa 12, pipeline), não deste mapeamento isoladamente, mas registra-se aqui porque é a armadilha mais provável quando os dois adaptadores alimentarem o mesmo pedido. |

## Tabela de mapeamento — `produto` / `variacao` (de `produto-com-variacoes.json`)

| Campo na origem | Campo canônico | Observação |
|---|---|---|
| `id` (produto pai) | `produto.id_externo` | 🟢. |
| `nome` | `produto.titulo` | 🟢. |
| `marca` | `produto.marca` | 🟢. |
| `categoria.descricao` | `produto.categoria` | 🟡 o Bling manda `id` + `descricao`; o canônico só tem `categoria text` — o adaptador grava a descrição e guarda o `id` do Bling em `dados_origem` para não perder rastreabilidade. |
| `ncm` | `produto.ncm` | 🟢 nome idêntico; conferir que vem só dígitos (o `CHECK` da V006 exige exatamente 8 dígitos) — se o Bling formatar com pontuação, o adaptador precisa normalizar antes de gravar, senão a linha inteira falha na ingestão. |
| — | `produto.cest` | Não presente nesta fixture — Bling provavelmente tem o campo em outro nível (cadastro fiscal do produto), não confirmado. Fica `NULL`. |
| `formato = "V"` (produto com variações) vs. `"S"` (simples) | decide se cria 1 ou N linhas em `variacao` | 🟡. Quando `"S"`, o adaptador cria a `variacao` única com `eh_variacao_padrao=true`, seguindo a regra da V006 ("todo produto tem ao menos uma variação"). |
| `variacoes[].id` | `variacao.id_externo` | 🟢. |
| `variacoes[].codigo` | `variacao.sku` | 🟢. |
| `variacoes[].gtin` | `variacao.gtin` | 🟢. |
| `variacoes[].nome` | `variacao.descricao_variacao` | 🟢. |
| `variacoes[].atributos[]` (`{nome, valor}[]`) | `variacao.atributos` (jsonb `{"cor":"Preto","tamanho":"M"}`) | 🟡 **o Bling manda lista de pares `{nome, valor}`, o canônico quer objeto chave-valor** — o adaptador tem que normalizar as chaves (minúsculas, sem acento: `"Cor"→"cor"`) para que a mesma pergunta ("vendas por cor") funcione igual para ML e Bling, que muito provavelmente usam grafias diferentes para o mesmo eixo. **Isso é decisão de normalização do adaptador, não do schema**, e precisa de uma lista de sinônimos que ainda não existe — registrar como trabalho futuro do adaptador. |
| `variacoes[].preco` | `variacao.preco_venda_atual` | 🟢. |
| `variacoes[].precoCusto` | `variacao.custo_unitario_atual` | 🟢 **este é o campo mais importante desta fixture inteira** — é a razão de existir o adaptador de ERP (decisão 0013: "o ERP é onde mora o custo da mercadoria"). |
| `variacoes[].estoque.saldoVirtualTotal` | `variacao.estoque_disponivel` | 🟡 nome plausível; o Bling tem múltiplos conceitos de saldo (físico, virtual, reservado) e não confirmei qual corresponde a "disponível para venda" no sentido que o canônico quer (fotografia da última sincronização, V006). |
| `situacao` (`"Ativo"`/outro) | `produto.ativo` / `variacao.ativo` | 🟡 valores de texto (`"Ativo"`, presumivelmente `"Inativo"`) a converter para boolean — domínio exato não confirmado. |

---

## O que não tem equivalente canônico e vai para `dados_origem`

- `numeroPedidoCompra`, `categoria` (do pedido, distinta da categoria do produto),
  `observacoes`/`observacoesInternas` — sem coluna própria no `pedido`.
- `transporte.transportadora`, `transporte.quantidadeVolumes`, `transporte.pesoBruto`,
  `transporte.volumes[]` (rastreio) — não há coluna canônica para transportadora/rastreio no
  `pedido`; se algum dia isso virar consulta de negócio ("atraso por transportadora"), sai do
  jsonb e vira coluna, conforme a regra de uso da convenção 4.
- `vendedor` (vendedor interno do Bling, comissionado) — conceito de força de vendas interna,
  não modelado na Fase 1.
- `intermediador` — específico da integração nativa Bling↔marketplace; guardado por inteiro
  em `pedido.dados_origem`, é candidato natural a virar chave de casamento com o pedido do ML
  no futuro (ver gap abaixo), mas hoje é só extensão.
- `midia.imagens` (do produto) — sem equivalente canônico na Fase 1 (não há tabela de mídia).
- `itensPorCaixa`, `volumes` (embalagem do produto, distinto de `transporte.volumes`) — específico de logística do Bling.

## O que o canônico exige e a fonte NÃO fornece

1. **Comissão/taxa de marketplace por pedido.** O Bling é ERP, não marketplace — ele não
   sabe quanto o Mercado Livre cobrou de comissão. `custo(natureza='COMISSAO_CANAL')` **tem
   que vir do adaptador de Mercado Livre**, nunca do Bling, mesmo que o mesmo pedido passe
   pelos dois. Se o pipeline de ingestão (tarefa 12) não coordenar isso, o Bling sozinho
   nunca vai conseguir calcular margem real — só custo de mercadoria e frete.
2. **`pedido.pago_em` de verdade.** Não identifiquei, nesta reconstrução, um campo de "data
   em que o pagamento foi confirmado" no recurso de pedido de venda — só `parcelas[].
   dataVencimento` (vencimento previsto, não pagamento efetivo) e `situacao` (que muda para
   algo como "Atendido"/"Faturado", sem timestamp granular no nível do pedido). Pode existir
   em um recurso de contas a receber separado do Bling, não coberto por esta fixture.
   Enquanto não confirmado, `pago_em` fica `NULL` quando a origem for só Bling.
3. **Hora do pedido.** `data`/`dataSaida`/`dataPrevista` parecem ser apenas data (sem hora,
   sem fuso), enquanto o canônico exige `timestamptz` em `feito_em`. Isso é uma perda real
   de precisão que a fonte impõe — não um erro do adaptador. Documentar a hora assumida (e
   marcar de alguma forma que é aproximada) evita que um relatório "vendas por hora do dia"
   use dado inventado como se fosse exato.
4. **Domínio fechado de `status`/`forma_pagamento`.** Tanto `situacao` quanto
   `formaPagamento` são **configuráveis por conta** no Bling — não são um enum fixo da API
   como no Mercado Livre. O canônico (V008) exige domínio fechado por `CHECK`. Isso não é
   "a fonte não fornece o dado", é "a fonte fornece um domínio que o tenant configura", o
   que exige uma tabela de tradução por tenant que ainda não existe no modelo da Fase 1 —
   **decisão de produto pendente**, não só de parsing: perguntar antes de inventar um
   mapeamento fixo situação→status que vai quebrar no segundo cliente com nomenclatura
   diferente.
5. **CEST do produto.** Presente no schema canônico (`produto.cest`), não encontrei campo
   correspondente confirmado nesta reconstrução do recurso de produto — fica `NULL`.
6. **Chave de casamento confiável entre o pedido do ML e o pedido do Bling.** O candidato
   (`numeroLoja` = id do pedido no marketplace) é hipótese, não fato confirmado. Sem essa
   chave funcionando de verdade, a ingestão dupla (ML traz o pedido, Bling traz o mesmo
   pedido) puxa `uq_pedido_origem` para dentro de canais DIFERENTES (`canal_id` do ML ≠
   `canal_id` do Bling) e **cria duas linhas em `pedido` para a mesma venda real** — cada
   `canal_id` tem sua própria unicidade, então o banco não vai impedir isso sozinho. É o
   maior risco estrutural encontrado neste mapeamento e precisa virar decisão explícita
   antes do pipeline da tarefa 12 (provavelmente: o Bling não cria pedido novo quando
   reconhece que a venda já veio de um canal de marketplace já integrado — só enriquece
   `custo` de mercadoria a partir do produto casado por SKU/GTIN — mas isso é decisão de
   design do adaptador, não deste documento).

## Armadilhas de dinheiro

1. **TIPO DE DADO — CRÍTICO (regra 2 do CLAUDE.md), igual ao Mercado Livre.** Todos os
   campos monetários do Bling nesta reconstrução (`totalProdutos`, `total`, `valor` do item,
   `desconto`, `frete`, `precoCusto`, `preco`, `parcelas[].valor`) aparecem como **número
   JSON**, não string. A mesma regra vale: **o adaptador lê o token como texto e constrói
   `BigDecimal` a partir da string, nunca a partir de `double`.** Isso é ainda mais
   importante no Bling porque `precoCusto` (custo unitário) alimenta margem por SKU
   diretamente — um erro de arredondamento aqui não é só "o total do pedido bateu errado",
   é "a margem calculada de um produto específico está sistematicamente errada".
2. **REAL vs. PERCENTUAL em desconto.** `desconto.valor` só faz sentido junto com
   `desconto.unidade` (`"REAL"` ou, presumivelmente, `"PERCENTUAL"`). Gravar `desconto.valor`
   direto em `pedido.valor_desconto` sem checar a unidade está **quase certo de estar errado**
   assim que um tenant configurar desconto por percentual — o valor gravado seria a alíquota
   (ex.: `10`), não os R$ 10,00 que ele parece ser.
3. **CIF vs. FOB (`transporte.fretePorConta`).** Mencionado na tabela de `pedido` acima:
   sem interpretar corretamente esse campo, `transporte.frete` pode ir parar em
   `pedido.valor_frete_cobrado` (receita) quando na verdade é um custo que o lojista pagou à
   transportadora sem repassar ao cliente — inverteria receita e custo, dois lados opostos
   da margem.
4. **Duas fontes de custo de mercadoria coexistindo.** `variacao.custo_unitario_atual`
   (vigente, do cadastro do produto) é diferente do custo que deveria ser **congelado** em
   `custo(natureza='MERCADORIA')` no momento da venda (comentário da V006). O Bling, sendo
   ERP, é exatamente a fonte de `custo_unitario_atual` — mas o adaptador de ERP **não deve
   escrever direto em `custo`** a cada sincronização de produto; quem congela o custo na
   venda é o pipeline de ingestão do pedido (tarefa 12), copiando o valor vigente no momento
   certo. Se o adaptador de produto atualizar `custo_unitario_atual` E o pipeline copiar
   errado o momento (por exemplo, depois que o fornecedor já reajustou o preço), a margem de
   pedidos antigos muda silenciosamente — exatamente o que a V006 diz para nunca acontecer.
5. **Parcelas somando diferente do total por arredondamento.** Na fixture,
   `88.24 + 88.23 + 88.23 = 264.70`, batendo com `total`. Isso é comum em ERPs brasileiros
   (a primeira parcela absorve a sobra de centavos da divisão). **Não montar
   `valor_total_pedido` como `soma das parcelas`** — usar sempre o campo `total` da fonte
   (regra R1 da V008: o total não é recalculado, é o que a fonte informou), mesmo que a
   soma das parcelas pareça mais "exata".
