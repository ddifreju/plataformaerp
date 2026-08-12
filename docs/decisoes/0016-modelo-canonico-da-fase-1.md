# 0016 — Modelo canônico da Fase 1

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER]

Cobre as tarefas 7, 8 e 9 da fila. Aplica a decisão 0002 (modelo agnóstico à
fonte) e repete o molde da 0010 (RLS por tabela) em todas as onze tabelas
novas. As convenções completas, com o porquê de cada uma, estão no cabeçalho
da `V005__canal.sql` — este documento registra só as escolhas estruturais e o
que foi descartado.

## Contexto

A Fase 1 precisa de um modelo único para dados que chegam de fontes
incompatíveis entre si (Mercado Livre, Shopee, loja própria, Bling). O modelo
é a base de tudo que vem depois: o motor de margem da Fase 2 lê essas tabelas,
e a interface da Fase 3 lê o que a Fase 2 calcula. Errar aqui é caro.

Nada disto foi executado — não há JDK, Docker nem psql na máquina
(`docs/PENDENCIAS.md`). O modelo foi revisado por leitura.

## Decisão

Onze tabelas, em oito migrations (`V005` a `V012`), cada uma com undo pareado.

```
canal ──┬── produto ── variacao ──┐
        │                          │
        ├── cliente ──┐            │
        │             │            │
        └── pedido ───┴─ item_pedido ─┬─ item_devolucao ─ devolucao
                 │                     │                      │
                 └──────── custo ──────┴──────────────────────┘
        conversa ── mensagem          evento_ingerido
```

As oito escolhas estruturais:

**1. `tenant_id` entra na chave, e as FKs entre tabelas de dados são
compostas.** Toda tabela ganha `UNIQUE (tenant_id, id)` além da PK, e as FKs
apontam para `(tenant_id, id)`. Motivo: a checagem de FK do Postgres **ignora
RLS**. Com FK simples, um bug de contexto grava um `item_pedido` do tenant A
apontando para um `pedido` do tenant B e o banco aceita calado. Com FK
composta, vira erro de integridade. Custo em JPA: zero — é restrição de banco,
o `@ManyToOne` continua mapeando uma coluna só. Bônus: o `UNIQUE` é o índice
com `tenant_id` na primeira posição que a 0010 exige.

**2. Dinheiro é `NUMERIC(18,4)`; quantidade é `NUMERIC(14,4)`.** Quatro casas
porque o produto vive de rateio (taxa por item, Ads dividido entre pedidos,
imposto proporcional); com duas casas cada rateio intermediário perde até meio
centavo e a soma erra reais. Arredondamento para duas casas acontece uma vez,
na apresentação. Toda tabela que agrega dinheiro carrega `moeda char(3)`.

**3. Quem se vende é a `variacao`, nunca o `produto`.** `item_pedido`
referencia variação. Produto sem variante ganha variação padrão criada na
ingestão. Alternativa descartada: item apontando ora para produto ora para
variação — obrigaria `COALESCE` e caminho duplo em toda query de margem.

**4. Status é `text` + `CHECK`, não ENUM nativo nem tabela de domínio.** ENUM
nativo não tem `DROP VALUE`, o que quebraria o undo pareado da 0006 na
primeira vez que um status novo aparecesse. Tabela de domínio custaria um join
em toda leitura, ou uma tabela sem `tenant_id` (exceção ao molde da 0010).
Custo aceito: status novo exige migration — o que é desejável, porque status
canônico novo é mudança de modelo.

**5. O total do pedido não é a soma dos itens, e não há `CHECK` amarrando os
dois.** `valor_total_pedido` é o que a fonte informou. Um `CHECK` rejeitaria
pedido real na ingestão por arredondamento da fonte, e perderíamos o pedido
para preservar uma igualdade contábil que não é nossa. Divergência é fato a
conciliar (Fase 2), não erro a corrigir. Pelo mesmo motivo,
`item_pedido.valor_total_linha` é coluna armazenada e **não** `GENERATED`: a
fonte arredonda diferente e o número dela é que vale.

**6. Receita não muda depois de gravada; toda perda é custo.** Devolução,
reembolso, estorno de comissão e frete reverso viram linhas em `custo`. As
colunas de dinheiro em `devolucao` são descrição do que a fonte informou, e o
motor de margem não as soma. Um lugar só para dinheiro que sai — se a margem
lesse dois lugares, bastaria um caminho de ingestão gravar nos dois para o
prejuízo aparecer dobrado.

**7. `custo` é tabela longa (`natureza` + `valor` + vínculo), com contrato de
soma explícito.** Custo de item também carrega `pedido_id` (há `CHECK`), então
custo real de um pedido é `WHERE pedido_id = X` — um `WHERE`, sem risco de
contar duas vezes. Rateio gera linha filha com `rateado_de_custo_id`, e o P&L
do período filtra `rateado_de_custo_id IS NULL`. `eh_estimativa`,
`base_calculo` e `aliquota_aplicada` colocam a regra 5 do CLAUDE.md ("se for
estimativa, diga que é") dentro do schema. Alternativa descartada: uma coluna
por tipo de custo em `pedido` — o Mercado Livre inventa taxa nova todo ano, e
cada uma viraria coluna, backfill e reescrita das queries de margem.

**8. `evento_ingerido` com `(tenant_id, canal_id, tipo_evento, id_externo)`
único mais `hash_payload`.** Reenvio idêntico é no-op; hash diferente é
atualização legítima. Implementado em uma instrução (`INSERT ... ON CONFLICT
... DO UPDATE ... WHERE hash IS DISTINCT FROM excluded.hash`), sem janela de
corrida. As chaves naturais das tabelas de negócio (`uq_pedido_origem` e
irmãs) são uma segunda trava independente, porque pedido duplicado corrompe
exatamente o número que o cliente confere primeiro.

## Consequências para LGPD

`cliente` não guarda documento em claro: guarda **HMAC-SHA256** dos dígitos,
com chave fora do banco. SHA-256 puro seria minimização de mentira — o espaço
de CPF tem ~10¹¹ valores e se reverte por força bruta. O endereço fica em
CEP/cidade/UF **no pedido**, nunca rua e número: analisamos venda, não
despachamos encomenda.

Exclusão a pedido do titular (Art. 18, VI) é **anonimização**
(`anonimizado_em` em `cliente` e `conversa`), não `DELETE` — o histórico
fiscal do pedido precisa sobreviver (Art. 16, I). Por isso o papel
`app_aplicacao` **não recebe `DELETE`** em nenhuma tabela de negócio;
cancelamento, devolução e desativação são estado. A única exceção é
`evento_ingerido`, que é payload descartável com política de retenção.

Duas pendências de negócio/jurídicas ficam abertas, na mesma categoria da
registrada pela decisão 0012: (a) retenção e base legal do `payload_bruto` de
`evento_ingerido`, que é o maior volume de dado pessoal do banco; (b)
consentimento/opt-in de WhatsApp, deliberadamente **não** modelado agora —
quando entrar, entra como tabela própria com data, origem e texto aceito, não
como boolean solto, que não prova nada.

## O que foi deixado de fora, de propósito

- **Coluna `vector` em `mensagem`.** A dimensão é fixa por modelo de embedding
  e não há chave de API para escolher modelo. Quando entrar, entra como tabela
  `mensagem_embedding`, para que trocar de modelo não reescreva a tabela de
  mensagens.
- **Alíquota fiscal junto do NCM.** A reforma CBS/IBS muda o cálculo ano a ano
  entre 2026 e 2033. Regra fiscal versionada por vigência é a tarefa 13; o
  `produto` guarda a classificação, não o imposto.
- **Movimentação de estoque.** `variacao.estoque_disponivel` é fotografia da
  última sincronização, não livro razão. Declarado como tal no `COMMENT`.
- **Trigger para manter `atualizado_em`.** Regra de negócio invisível no
  banco. A aplicação mantém, via `@UpdateTimestamp`.
- **`CHECK` impedindo devolver mais do que foi vendido.** Exigiria trigger
  (`CHECK` não enxerga outra tabela), e a fonte às vezes manda exatamente
  isso. É verificação de conciliação com alerta, não restrição que barra
  ingestão.

## Ajuste na numeração sugerida

A fila sugeria `V009 custo` / `V010 devolucao`. Invertido: `custo` tem FK para
`devolucao` (frete reverso e reembolso são custos causados por uma devolução).
Criar `custo` antes exigiria um `ALTER TABLE` numa migration seguinte para
adicionar a FK — duas migrations na mesma tabela e um undo mais frágil.

## Risco em aberto

Nenhuma destas migrations foi executada. O `RlsAtivoEmTodasAsTabelasTest`
descobre tabela nova sozinho pela coluna `tenant_id` e vai cobrar as quatro
policies e o `FORCE` de todas as onze — mas só quando houver Docker para
rodá-lo.
