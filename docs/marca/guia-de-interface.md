# Guia de interface — marca e linguagem

**Status:** ativo, referência direta para quem constrói telas
**Escopo:** Fase 3 (`frontend/`)
**Não decide:** o nome da marca. Essa decisão é da fundadora
(`docs/PENDENCIAS.md`, "Nome definitivo da marca") e este guia trabalha em
volta dela, não no lugar dela.

Este documento existe para que o engenheiro de frontend não precise inventar
tom, cor ou frase na hora de construir tela. Onde há dúvida de negócio (nome,
paleta de marca definitiva, dark mode), o documento diz isso explicitamente
em vez de decidir por conta própria uma coisa que não é técnica.

---

## 1. Placeholder de nome

### A constante

```ts
// frontend/src/lib/marca.ts
export const NOME_PRODUTO = "Plataforma";
```

Um único lugar, um único símbolo. Nenhuma tela, e-mail, `<title>` ou string
de erro escreve o nome da marca literalmente — todos importam
`NOME_PRODUTO`.

### Por que "Plataforma" e não outra coisa

- É a palavra que `CLAUDE.md` e `LEIA-PRIMEIRO.md` já usam para descrever o
  produto ("Plataforma [NOME A DEFINIR]", "plataforma unificada de gestão").
  Reaproveitar mantém documentação, código e tela dizendo a mesma coisa.
- É descritiva, não um nome próprio — ninguém que vir "Plataforma" no
  cabeçalho vai achar que essa é a marca registrada. Ela se anuncia como
  provisória pelo próprio formato da palavra, sem precisar de colchetes,
  aviso ou `TODO` visível na tela.
- Não é piada nem nome de teste (nada de "Acme", "MinhaLoja", "AppTeste").
  Numa demonstração para um lojista real, "Plataforma" não distrai — ele lê
  como "o sistema", que é exatamente o que é.
- Não colide com nenhum dos 16 nomes já pesquisados e descartados em
  `docs/marca/` — importante para não sugerir, por engano, que a decisão já
  foi tomada.

### Onde ela mora e como usar

- **Arquivo:** `frontend/src/lib/marca.ts`. É o único arquivo que qualquer
  outro módulo pode importar para saber o nome do produto.
- **Uso em texto corrido:** sempre via template, nunca concatenação solta —
  `` `${NOME_PRODUTO} · Painel do dono` `` no `<title>`, por exemplo.
- **Uso em `layout.tsx`:** o `metadata.title` e `metadata.description` hoje
  ainda têm o texto padrão do `create-next-app` ("Create Next App"). Trocar
  para usar `NOME_PRODUTO` é tarefa da primeira tela real, não deste guia —
  mas fica registrado aqui para não passar despercebido.
- **Logo/wordmark:** enquanto não houver marca, não desenhar um símbolo. Usar
  só o texto de `NOME_PRODUTO`, tipografado em peso normal (não bold, não
  itálico, sem ícone ao lado) — um logo desenhado em cima de um nome
  provisório vira retrabalho garantido quando o nome mudar.

### Como trocar quando o nome existir

1. Editar o valor de `NOME_PRODUTO` em `frontend/src/lib/marca.ts`.
2. Buscar por strings literais que escaparam da constante (comando sugerido:
   `grep -ri "plataforma" frontend/src --include=*.tsx --include=*.ts`) —
   qualquer ocorrência fora de `marca.ts` é uma pendência de codificação, não
   uma segunda fonte de verdade.
3. Desenhar o logo/wordmark definitivo é tarefa de marca, fora de escopo
   deste guia — quando existir, este documento é atualizado com a seção de
   identidade visual que falta.

---

## 2. Paleta e tipografia mínimas

Ferramenta de trabalho, não landing page: prioridade é contraste alto, texto
comprido legível por horas, e cor com significado — não cor decorativa.

### 2.1 Fundação (neutros)

O scaffold do `create-next-app` já gerou `frontend/src/app/globals.css` com
`--background`/`--foreground` e um bloco `@theme inline`. Este guia estende o
mesmo arquivo, no mesmo padrão, em vez de introduzir um segundo sistema.

```css
/* frontend/src/app/globals.css */
@import "tailwindcss";

:root {
  /* Neutros — base de texto e superfície */
  --background: #ffffff;
  --foreground: #0f172a;      /* slate-900: quase preto, não preto puro */
  --superficie: #f8fafc;      /* slate-50: fundo de cartão/tabela, sutil */
  --borda: #e2e8f0;           /* slate-200 */
  --texto-secundario: #475569; /* slate-600: legenda, rótulo, texto de apoio */
  --texto-desabilitado: #94a3b8; /* slate-400 */

  /* Ação — sóbria de propósito, não é cor de venda */
  --acao: #1e3a5f;            /* azul-marinho fechado */
  --acao-hover: #16304d;

  /* Semânticas de dado — o coração da interface */
  --valor-positivo: #15803d;     /* green-700 */
  --valor-positivo-bg: #f0fdf4;  /* green-50 */
  --valor-negativo: #b91c1c;     /* red-700 */
  --valor-negativo-bg: #fef2f2;  /* red-50 */
  --dado-estimado: #b45309;      /* amber-700 */
  --dado-estimado-bg: #fffbeb;   /* amber-50 */
  --dado-ausente: #57534e;       /* stone-600 — propositalmente NÃO é vermelho */
  --dado-ausente-bg: #f5f5f4;    /* stone-100 */
}

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-superficie: var(--superficie);
  --color-borda: var(--borda);
  --color-texto-secundario: var(--texto-secundario);
  --color-texto-desabilitado: var(--texto-desabilitado);
  --color-acao: var(--acao);
  --color-acao-hover: var(--acao-hover);

  --color-valor-positivo: var(--valor-positivo);
  --color-valor-positivo-bg: var(--valor-positivo-bg);
  --color-valor-negativo: var(--valor-negativo);
  --color-valor-negativo-bg: var(--valor-negativo-bg);
  --color-dado-estimado: var(--dado-estimado);
  --color-dado-estimado-bg: var(--dado-estimado-bg);
  --color-dado-ausente: var(--dado-ausente);
  --color-dado-ausente-bg: var(--dado-ausente-bg);

  --font-sans: var(--font-geist-sans);
  --font-mono: var(--font-geist-mono);
}

body {
  background: var(--background);
  color: var(--foreground);
  font-family: var(--font-sans), system-ui, sans-serif;
}
```

Isso libera classes Tailwind diretas: `text-valor-negativo`,
`bg-dado-estimado-bg`, `border-borda`, `text-texto-secundario`, etc.

**Sobre o `prefers-color-scheme: dark` que o scaffold já gerou:** removido
propositalmente do bloco acima. Dark mode para uma ferramenta financeira
precisa de par de cores por token semântico (o `--valor-negativo` do modo
escuro não pode ser o mesmo vermelho — perde contraste em fundo escuro), e
isso ainda não foi desenhado. Ativar o dark mode automático do sistema
operacional agora aplicaria cores não pensadas aos números de dinheiro. Fica
registrado como pendência de design, não decidido por conta própria aqui.

### 2.2 As quatro cores semânticas — o motivo de existir deste documento

| Token | Uso | Regra de aparência |
|---|---|---|
| `valor-positivo` | número medido, favorável (margem positiva, lucro) | verde, só no número — não pinta a linha inteira |
| `valor-negativo` | número medido, desfavorável (margem negativa, prejuízo) | vermelho, mesma regra |
| `dado-estimado` | número que passou por cálculo nosso (rateio, alíquota aplicada), `eh_estimativa = true` | âmbar/mostarda — cor de "atenção ao dado", não de erro. Sempre acompanhado de indicador não-só-cor (ver 2.3) |
| `dado-ausente` | lacuna: não existe informação, não existe linha (`custo` não gravado) | cinza-pedra, nunca vermelho. Cor deliberadamente "morta", sem energia — não briga visualmente com um erro real do sistema |

**Regra dura de uso:** `valor-positivo` e `valor-negativo` só se aplicam a
número que veio do motor de margem (medido ou com rótulo de teto/estimativa
já resolvido). `dado-estimado` e `dado-ausente` não são cores de *estado da
tela* (loading, erro de rede) — são cores de *natureza do dado*. Um erro de
carregamento (API fora do ar) usa outro vocabulário visual, fora do escopo
deste guia, e não deve pegar emprestado nenhum destes quatro tokens — senão
o lojista aprende a desconfiar do número errado.

### 2.3 Acessibilidade: cor nunca é o único sinal

Cerca de 8% dos homens têm alguma forma de daltonismo, e vermelho/verde é
exatamente o par mais comum de se confundir. Nenhuma das quatro cores pode
ser o único jeito de saber o que um número é:

- `valor-negativo`: sinal `−` explícito antes do número, nunca só a cor.
- `dado-estimado`: prefixo `~` antes do valor **e** sublinhado tracejado
  (`border-b border-dashed`), não só a cor âmbar.
- `dado-ausente`: nunca é um número — é texto ("não cadastrado", "sem
  dado"), então já se diferencia por forma, não só por cor.
- `valor-positivo`: sem prefixo (positivo é o padrão visualmente neutro);
  onde precisar reforçar, usar `+` explícito em contexto de variação
  ("+12% vs. mês anterior").

### 2.4 Tipografia

O scaffold já traz Geist Sans e Geist Mono via `next/font/google`
(`frontend/src/app/layout.tsx`) — não é preciso adicionar fonte nova.

- **Texto de interface (rótulo, tabela, parágrafo, menu):** Geist Sans
  (`font-sans`). Alta legibilidade em texto pequeno, é o motivo de já estar
  no projeto.
- **Todo número monetário ou percentual:** `font-variant-numeric:
  tabular-nums` (classe Tailwind `tabular-nums`). Sem isso, uma coluna de
  preços com "1" e "8" de larguras diferentes não alinha, e uma tabela de
  margem que não alinha por SKU já começa transmitindo desleixo — exatamente
  o oposto do que o produto promete.
- **Tamanho de base:** `text-sm` (14px) para tabela e dado denso, `text-base`
  (16px) para texto corrido de explicação. Nada de `text-xs` em número de
  dinheiro — é a informação mais importante da tela, não a menos importante.
- **Peso:** `font-normal` para texto, `font-medium` (não `font-bold`) para
  destacar o número mais importante de um cartão. Peso 700 reservado para
  título de seção, não para dado — bold em toda linha cansa em uso de 8h.
- **Nunca itálico em número.** Itálico é reservado para legenda/explicação
  textual (ex.: a frase que acompanha um rótulo `COM_TETO`).

---

## 3. Como falar de dinheiro e de incerteza

Esta é a seção que faz a regra 5 do `CLAUDE.md` ("nunca invente dado") virar
texto de verdade na tela. Copiar literalmente onde possível.

### 3.1 Os quatro números

Nomes pensados para o lojista, não para o contador dele. Cada rótulo vem
acompanhado de uma frase de apoio curta (subtítulo/tooltip) — a frase é
parte do nome, não decoração.

| # | Rótulo na tela | Frase de apoio | Nunca escrever |
|---|---|---|---|
| N0 | **Faturamento bruto** | "O que o comprador pagou, frete incluído." | "Faturamento" sozinho sem dizer que é bruto |
| N1 | **Receita líquida** | "Faturamento menos devolução e desconto. Ainda não tirei taxa de canal, frete nem imposto." | "Receita" sozinho — sem o "líquida" o lojista lê como se já fosse o resultado |
| N2 | **Margem por pedido** | "O que sobra depois dos custos diretos: mercadoria, comissão, frete, imposto. Cada centavo daqui bate com a fatura do canal." | "Margem de contribuição" sem tradução — é termo de curso de contabilidade, não de loja |
| N3 | **Resultado do pedido** | "Margem menos Ads e outros custos rateados entre pedidos. A parte rateada é estimativa nossa, não fato do canal." | "Lucro" sozinho, em qualquer um dos quatro — nunca, em nenhuma tela |

Regra de composição na tela: os quatro números aparecem **na ordem N0 → N3**,
sempre com o rótulo visível ao lado do valor (nunca só o valor, nunca só o
rótulo). Se o espaço não comporta rótulo + frase de apoio + valor, o valor
não cabe naquele espaço — reduzir para menos números antes de cortar o
rótulo.

Quando N4 (lucro operacional do período, soma de N3 mais custos de período)
aparecer em alguma tela futura, mesma regra: nunca "lucro" sozinho — sempre
"lucro operacional do período", com a nota de que ele não é a soma simples
de N3 enquanto houver custo de período não rateado (ver
`docs/fiscal/regras-de-margem.md` §1).

### 3.2 Os três rótulos de confiança

Todo número de margem carrega um destes três — nunca aparece sem rótulo
(decisão 0019).

#### `CALCULADA`

Uso: nenhuma lacuna. Badge verde-neutro (não usar `valor-positivo` no badge
em si — o badge indica *confiança*, a cor do número ao lado indica *sinal*).

> Texto padrão:
> **"Calculada."** Nenhum dado faltando neste pedido.

#### `COM_TETO`

Uso: todas as lacunas presentes puxam a margem para cima (catálogo de
lacunas #1, 2, 3, 4, 6, 7, 10, 11, 12, 18 — `regras-de-margem.md` §9.1). O
número é teto, não resultado. Nunca soar como aviso genérico — é uma
afirmação específica e útil.

> Texto padrão:
> **"{valor} é o teto — a margem real é menor."**
> Faltam: {lista de lacunas, cada uma com o link de cadastro}.

Exemplo concreto (o do §2.5 do documento fiscal):

> **R$ 44,96 (22,49%) é o teto — a margem real é menor.**
> Não considerei a taxa de antecipação, porque ela não está cadastrada. Se
> você antecipa recebíveis, o resultado de verdade é menor que este.
> [Cadastrar taxa de antecipação →]

Nunca escrever "margem aproximada de X" nem "cerca de X" para `COM_TETO` —
não é aproximação, é limite superior comprovável. "Aproximado" e "teto" são
afirmações diferentes; a segunda é mais forte e é a que temos.

#### `INDETERMINADA`

Uso: há lacuna de viés oposto (ICMS-ST/PIS-COFINS monofásico não sinalizado
— #8, #9) ou de direção desconhecida (#13, #15, #17) junto de lacunas de
viés para cima. Não existe teto honesto a declarar.

> Texto padrão:
> **"Não dá para calcular esta margem com confiança."**
> Faltam: {lista}. Alguns desses fazem o número subir, outros descer — por
> isso não existe um teto seguro para mostrar aqui.

Nunca substituir por `R$ 0,00`, `—` sozinho ou omitir a linha da tabela. A
linha existe, mostra o rótulo `INDETERMINADA` e a lista de lacunas no lugar
do valor.

### 3.3 Lacunas — texto de "não tenho esse dado"

Regra de forma para toda lacuna, sem exceção: **frase 1 diz o que falta,
frase 2 diz o que fazer.** Nunca uma sem a outra. "Não sei" sem próximo passo
é lamento; com próximo passo é pendência de trabalho.

#### Custo do produto não cadastrado

> Não sei quanto o produto {sku/nome} custou para você — sem esse número, a
> maior parte do custo do pedido fica de fora.
> **Cadastre o custo do produto e recalculo este pedido e mais {N} que
> também estão faltando.** [Cadastrar custo →]

#### Taxa do canal não cadastrada (comissão, tarifa fixa, parcelamento)

> Não tenho a comissão do {canal} para a categoria {categoria}, anúncio
> {tipo de anúncio}, na data {data}.
> **Cadastre essa taxa e recalculo {N} pedidos que dependem dela.**
> [Cadastrar taxa →]

#### Regime tributário ausente

> Não sei o regime tributário da sua empresa — por isso não calculei imposto
> em nenhum pedido deste período. Sem essa informação, também não sei dizer
> se a margem está para mais ou para menos.
> **Informe o regime (Simples Nacional, Lucro Presumido...) em Configurações
> → Fiscal.** [Configurar regime →]

#### Antecipação de recebíveis desconhecida

> Não sei se você antecipa recebíveis. Isto é diferente das outras lacunas:
> se você antecipa e eu não sei, a margem aparece **maior** do que é de
> verdade, e eu não tenho como perceber isso sozinho.
> **Responda em Configurações → Financeiro, mesmo que a resposta seja
> "não antecipo".** [Configurar antecipação →]

#### Padrão geral (para lacunas não listadas acima)

> Não tenho {o que falta} para {contexto: este pedido / este período / este
> produto}. {Efeito no número, em uma frase, se for conhecido}.
> **{Ação específica com verbo no imperativo}.** [{Rótulo do botão} →]

### 3.4 Valor estimado — como marcar sem poluir

Regra: o indicador de estimativa é **discreto por padrão, explícito sob
demanda**. Não é para gritar "cuidado!" toda vez — é para nunca deixar a
pessoa achar que é fato quando não é.

- **No número:** prefixo `~` e sublinhado tracejado (`~R$ 13,45`), cor
  `dado-estimado`. Sem ícone de alerta grudado no número — ícone de alerta é
  para erro, e estimativa não é erro.
- **Ao passar o mouse / tocar (tooltip):** frase curta com a origem.
  - Estimado por taxa cadastrada: *"Estimado com a comissão que você
    cadastrou em {data}. O canal não informou o valor cobrado neste
    pedido."*
  - Estimado por rateio: *"Estimado por rateio entre {N} pedidos do
    período. Correto no total do mês, aproximado neste pedido específico."*
- **Em totais que somam linhas estimadas e medidas:** o total **não** ganha
  o `~` automaticamente. Ele ganha uma nota abaixo: *"Inclui R$ {valor} em
  custos estimados (ver detalhamento)."* — mistura estimativa dentro de um
  total sem marcar cada componente individualmente é o jeito mais comum de
  a estimativa virar fato por acidente.
- **Nunca** usar `~` como decoração estética em número medido. Se o número é
  fato (`eh_estimativa = false`), zero indicador — silêncio visual é o
  próprio sinal de confiança.

---

## 4. Tom de voz

O leitor administra uma loja, tem pouco tempo, e já foi decepcionado por
sistema que promete "inteligência" e entrega número furado. Cinco
princípios, cada um com exemplo.

### 1. Número primeiro, adjetivo nunca

**Escreva assim:** "Você devolveu 312 pedidos em julho. 40% por tamanho
errado, e 8 SKUs concentram isso."
**Não assim:** "Uau, muitas devoluções esse mês! Vamos dar uma olhada?"

### 2. Diga o que não sabe, no mesmo tom que diz o que sabe

**Escreva assim:** "Não sei a comissão deste pedido — o canal não informou e
não há taxa cadastrada."
**Não assim:** "Comissão estimada" sem dizer, em lugar nenhum, que é uma
estimativa e de onde ela veio.

### 3. Erro e lacuna vêm com o próximo passo, nunca sozinhos

**Escreva assim:** "Sem regime tributário cadastrado — nenhum imposto foi
calculado neste período. Cadastre em Configurações → Fiscal."
**Não assim:** "Erro ao calcular imposto." / "Dados insuficientes."

### 4. Nada de hype, nada de venda, nada de emoji

**Escreva assim:** "Margem líquida: 22,49%."
**Não assim:** "Sua margem está incrível esse mês! 🎉" / "Descubra o poder
da IA para revolucionar sua operação!"

Palavras banidas de qualquer texto de produto: "poderoso", "revolucionário",
"inteligente" (como adjetivo vago), "mágico", "simples" (dizer que algo é
simples não faz ser simples — mostrar que é, sim).

### 5. Fale como quem senta do lado do dono da loja, não como quem vende pra ele

**Escreva assim:** "Você recebeu R$ 149,01 por um pedido de R$ 199,90. A
diferença: R$ 25,99 de comissão, R$ 24,90 de frete."
**Não assim:** "Maximize seus resultados com insights poderosos sobre sua
operação."

---

## 5. Nomes das três visões

"Visão do dono / gestor / analista" (`docs/ESTADO.md`, tarefas 18-20) são
nomes de papel no roadmap — não devem aparecer na interface como rótulo,
porque numa loja pequena a mesma pessoa é as três coisas no mesmo dia. Os
rótulos da interface nomeiam a **pergunta que a tela responde**, não o cargo
de quem pergunta.

| Papel no roadmap | Rótulo na interface | Pergunta que responde |
|---|---|---|
| Visão do dono | **Resultado** | "Quanto sobrou de verdade, e de onde saiu cada centavo?" — os quatro números, N0 a N3, com decomposição |
| Visão do gestor | **Operação** | "Onde a operação está travando?" — gargalo, tempo de resolução, backlog por tipo (nunca desempenho de pessoa — decisão 0003) |
| Visão do analista | **Pendências** | "O que eu preciso resolver para os números ficarem mais confiáveis?" — fila de lacunas: taxa não cadastrada, custo faltando, regime ausente |

Navegação principal sugerida: **Resultado · Operação · Pendências** — três
itens, sem submenu de "papéis". Uma pessoa que abre o sistema sozinha,
20h/semana, entra em "Pendências" de manhã, olha "Resultado" no fim do mês e
usa "Operação" quando alguma coisa trava — o mesmo usuário, três perguntas.

Nota para quem for escrever o texto de introdução de cada aba: a aba
"Pendências" desta interface é sobre lacunas de **dado** (fiscal e de
custo), e não deve ser confundida com `docs/PENDENCIAS.md` (que é lista
interna de decisões da fundadora) — são conceitos parecidos no nome, mas
públicos diferentes. Vale um texto de apoio na aba deixando isso implícito
pelo conteúdo (mostra taxa não cadastrada, não mostra "decidir nome da
marca").

---

## 6. O que este guia não resolve (e não deveria, agora)

Registrado para não ser confundido com omissão:

- **Paleta de marca definitiva e logo.** Impossível antes do nome. A paleta
  da seção 2 é neutra de propósito — não usa nenhuma cor "de marca", só
  cores funcionais. Quando o nome existir, cores de identidade podem se
  somar a esta base sem substituí-la (as quatro semânticas continuam
  precisando existir independente de qual for a cor de marca).
- **Dark mode.** Ver nota em 2.1. Precisa de par de cores por token
  semântico, desenhado com contraste conferido — não decidido por conta
  própria aqui.
- **Componentes visuais (botão, tabela, cartão).** Este guia dá os tokens;
  a biblioteca de componentes é trabalho de implementação da Fase 3, não de
  marca.
