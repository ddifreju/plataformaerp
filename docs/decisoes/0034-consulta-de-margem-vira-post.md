# 0034 — A consulta de margem vira POST, porque ela grava a trilha

**Data:** 14 de setembro de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL] — é uma troca de verbo e a chamada
correspondente no frontend.
**Emenda a:** 0025 (condição 1) e 0021.

## Contexto

A auditoria de segurança da Fase 4 encontrou algo que três auditorias
anteriores não tinham encontrado, e que não está no código novo:

**`GET /api/margem/periodo` altera estado.** `ServicoMargemPeriodo.calcular`
grava uma linha em `consulta_auditada` a cada chamada — corretamente, porque é
a regra 3 do CLAUDE.md ("toda resposta numérica é rastreável") sendo cumprida.
Só que o endpoint que a expõe é um `GET`.

Isso quebra a condição 1 da decisão 0025, que está escrita assim: "Nenhum `GET`
pode alterar estado. `Lax` *permite* o cookie em navegação top-level `GET`."
Essa condição não é estilo — é a premissa que sustenta o CSRF estar desligado
no sistema inteiro.

O ataque é real, ainda que modesto: um site hostil leva a lojista autenticada
a uma navegação de topo para
`https://app/api/margem/periodo?inicio=...&fim=...&canalId=...`. O cookie
`Lax` acompanha, a requisição executa, e o servidor grava uma linha de
auditoria com parâmetros escolhidos pelo atacante. Ele não lê a resposta — a
política de mesma origem impede — mas o efeito colateral acontece. É a
definição de CSRF. Somado à ausência de teto de intervalo, vira também um
amplificador de carga.

O detalhe que mais incomoda: o `PerguntaController`, escrito nesta mesma fase,
tem um comentário explicando em detalhe por que **ele** é POST — e o endpoint
irmão já fazia exatamente o que aquele comentário descreve como inaceitável.

## Decisão

**`GET /api/margem/periodo` passa a ser `POST /api/margem/periodo`.**

O caminho não muda; os parâmetros saem da query string e vão para o corpo.
O frontend passa a chamar com POST.

A justificativa não é burocrática. A operação **não é** uma leitura pura: ela
cria um registro de auditoria que existe para ser oposto à lojista mais tarde
("foi este número, calculado com estes IDs, nesta data"). Criar registro é
POST. O verbo estava mentindo sobre a natureza da operação, e o `SameSite=Lax`
acreditou na mentira.

Com isso a condição 1 da 0025 volta a ser verdadeira, e o CSRF pode continuar
desligado pelos motivos originais.

### A regra geral que fica

**Todo endpoint que grava `consulta_auditada` é POST.** Não há exceção, e a
recíproca é o que vale na revisão: ao ver um `@GetMapping`, confira se o
serviço por trás é de fato somente-leitura — inclusive a auditoria.

Os painéis (`GET /api/painel/gestor` e `GET /api/painel/analista`) continuam
GET porque **não** gravam auditoria hoje. Se um dia passarem a gravar, viram
POST junto.

## Alternativas consideradas

- **Religar a proteção CSRF do Spring.** É a opção conservadora e continua
  disponível. Descartada porque resolve o sintoma com o custo que a 0025 já
  tinha avaliado e recusado (ciclo de token, fonte recorrente de bug de
  integração), enquanto o problema real é que um verbo está descrevendo errado
  o que a operação faz. Corrigir o verbo é menor e mais honesto.
- **Não gravar auditoria em consulta de leitura.** Descartada de imediato:
  seria desligar a regra 3 para consertar a decisão 0025. A rastreabilidade é
  o diferencial do produto; o verbo HTTP não é.
- **Gravar a auditoria fora da transação do GET (assíncrono).** Descartada:
  esconde o efeito colateral em vez de eliminá-lo — o atacante continua
  forçando escrita e processamento — e ainda introduz um caminho assíncrono
  onde hoje existe uma garantia transacional simples.
- **Declarar que "estado" na condição 1 significa estado de negócio, e trilha
  de auditoria não conta.** Foi a saída tentadora, e é a que eu recusaria em
  revisão se outro tivesse proposto. Redefinir o invariante para caber no
  código é como invariante morre. Além disso seria falso na prática: escrita
  ilimitada forçada por terceiro é efeito colateral, com ou sem o rótulo.

## Consequências

- Uma chamada no frontend muda de verbo, e o `ContratoApiTest` muda junto
- O `POST /api/margem/periodo` não é cacheável — irrelevante aqui, porque a
  resposta nunca deveria ser cacheada mesmo: ela carrega número financeiro por
  tenant
- A decisão 0021 continua inteira: `canalId` segue obrigatório. Muda o verbo,
  não o contrato de escopo
- Fica um item de revisão permanente, somado à lista de invariantes do
  `ESTADO.md`: **GET que chama serviço que grava auditoria é bug de segurança,
  não de estilo**
- Vale registrar o padrão: três revisões por leitura não pegaram isto, e a
  auditoria só pegou quando foi perguntada de forma específica ("confirme que
  nenhum GET, **inclusive os antigos**, alterou-se para gravar estado"). O
  invariante estava escrito no `ESTADO.md` desde a Fase 3 e mesmo assim passou
