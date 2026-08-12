# 0013 — Bling como primeiro ERP integrado

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

A tarefa 11 pede um adaptador de ERP contra fixture. Precisa escolher **qual**
ERP serve de primeiro alvo. Os candidatos reais no varejo online brasileiro de
pequeno e médio porte são **Bling** e **Tiny** (grupo Olist).

A decisão 0002 registra que a fundadora tem reserva quanto a depender dessas
ferramentas, que considera limitadas. Aquela decisão já resolveu a tensão:
integrar não é endossar, é ler onde o dado está.

## Decisão

**Bling, API v3** (REST/JSON, OAuth2), como primeiro adaptador de ERP.

O adaptador é construído contra fixture — payload gravado em arquivo — porque
não há credencial (`docs/PENDENCIAS.md`). Nenhuma chamada de rede real nesta
fase.

## Alternativas consideradas

- **Tiny (Olist).** Também tem API pública e base relevante. Descartada como
  *primeiro* alvo, não como alvo: o Bling tem a maior base instalada entre
  lojistas SMB brasileiros, que é exatamente o perfil do primeiro cliente. Em
  caso de empate técnico, escolher o mais provável de aparecer.
- **Fazer os dois ao mesmo tempo.** Descartada: dobra o trabalho antes de o
  primeiro adaptador ter provado o desenho da porta. O ganho de ter dois
  adaptadores é justamente descobrir o que é canônico e o que é específico —
  e isso se aprende melhor com o segundo, depois do primeiro estar pronto.
- **Nenhum ERP, só marketplace.** Descartada: o ERP é onde mora o custo da
  mercadoria. Sem ele não há lucro real, que é o produto inteiro.

## Consequências

- O adaptador de Bling vira o **segundo** exemplo do contrato de porta (o
  primeiro é o Mercado Livre). Dois exemplos de fontes bem diferentes é o
  mínimo para saber se a abstração presta
- Quando a credencial chegar, troca-se a fixture pela chamada real sem tocar no
  núcleo (decisão 0002)
- **Risco assumido e registrado:** as fixtures foram escritas a partir do
  formato documentado da API, **sem** uma resposta real capturada. Campos podem
  divergir do que a API devolve de fato. A fixture é hipótese até um payload
  real confirmá-la — está anotado em `docs/ESTADO.md` como pendente de
  validação
