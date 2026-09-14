# 0030 — Arquitetura da camada de IA: intenção fechada, número nunca gerado

**Data:** 14 de setembro de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER] — não pelo código, que é pequeno,
mas porque define o que o produto promete. Prometer "pergunte qualquer coisa"
e depois recuar para um catálogo é perda de confiança do cliente; o contrário
é ganho. A direção da escolha importa mais que a escolha.

## Contexto

O pitch diz que a plataforma "responde perguntas sobre a operação". Nada disso
existe. É a Fase 4, bloco A (decisão 0029).

O desenho óbvio — e errado para este produto — é *text-to-SQL*: manda a
pergunta e o schema para o modelo, ele escreve a consulta, roda, e o modelo
redige a resposta. É o que quase todo mundo faz.

Ele colide de frente com três das cinco regras inegociáveis:

- **Regra 5 (nunca invente dado).** Um número redigido por um modelo é
  indistinguível, para quem lê, de um número consultado. Quando o modelo
  arredonda, troca um dígito ou soma dois períodos, ninguém percebe.
- **Regra 3 (toda resposta numérica é rastreável).** SQL gerado na hora é
  rastreável só no sentido literal — dá para guardar o texto. Não dá para
  afirmar que ele calculou o que a pergunta pedia.
- **Regra 1 (tenant é identidade).** SQL gerado é a forma mais direta de
  furar RLS que existe, porque o modelo não tem motivo nenhum para incluir o
  predicado de tenant, e o `SET LOCAL` protege só se o SQL passar pela mesma
  conexão com o mesmo contexto. Uma camada a menos de defesa, por escolha.

E colide com o diferencial do produto. A proposta inteira é que o número tem
**rótulo de confiança** e **memória de cálculo** (decisão 0019). Um número que
o modelo produziu não tem nem um nem outro.

## Decisão

**O modelo de linguagem interpreta a pergunta. Ele não calcula, não consulta e
não redige número nenhum.**

Pipeline, no pacote `com.plataforma.pergunta`:

```
texto livre
   ↓  PortaModeloLinguagem.interpretar(texto, catálogo)
IntencaoDetectada { codigo, parâmetros, confiança }     ← única saída do LLM
   ↓  validação determinística em Java
PerguntaRespondivel (do catálogo fechado)
   ↓  executa chamando os serviços que já existem (margem, painel, canal)
Resultado + ConsultaAuditada (regra 3)
   ↓  template determinístico
Resposta com números do banco, rótulo de confiança e link para a memória
```

As quatro propriedades que sustentam isso:

1. **Catálogo fechado.** Existe uma lista finita e versionada de perguntas que
   o sistema sabe responder. Cada uma é uma classe Java com parâmetros
   tipados, implementada sobre os serviços já testados. O modelo escolhe
   **dentro** da lista; não inventa item novo. Código de intenção que não
   existe no catálogo é rejeitado, não interpretado com boa vontade.
2. **Parâmetro é validado, nunca aceito.** Data, período, canal e limite são
   coagidos e conferidos em Java. `canalId` é sempre verificado contra os
   canais do tenant do contexto. Parâmetro obrigatório ausente vira **pedido
   de esclarecimento**, jamais um valor padrão silencioso.
3. **Nenhum dígito da resposta vem do modelo.** O texto sai de template
   preenchido com o resultado da consulta. Isso é verificável por teste, e vai
   ser verificado: a resposta final não pode conter dígito que não esteja no
   resultado (ou no texto da própria pergunta).
4. **Recusa é caminho de primeira classe.** Confiança abaixo do limiar, ou
   pergunta fora do catálogo, produz "não sei responder isso ainda" seguido do
   que o sistema *sabe* responder. Não produz tentativa aproximada. É a
   regra 5 aplicada à camada que mais tenta violá-la.

### Sem chave de LLM: duas implementações da porta

`PortaModeloLinguagem` é uma interface com um método. Hoje, duas
implementações, nenhuma delas com rede:

- **`ModeloHeuristico`** — casamento por palavra-chave sobre o catálogo,
  conservador por construção: na dúvida, devolve confiança baixa e o sistema
  recusa. É o que roda em dev, para a tela funcionar no navegador sem chave.
  Não é um "quase LLM": é o piso de qualidade contra o qual o modelo real vai
  ser comparado quando a chave chegar.
- **`ModeloGravado`** — devolve interpretações gravadas em fixture, indexadas
  pela pergunta normalizada. É o que a suíte de avaliação usa, e o que permite
  a coisa mais útil deste desenho: **alimentar saídas adversárias de propósito**
  (código de intenção inexistente, `canalId` de outro tenant, período
  invertido, parâmetro em formato errado) e provar por asserção dura que as
  travas seguram.

Quando a chave existir, entra `ModeloAnthropic` e nada mais muda. Trocar o
adaptador é a única alteração.

### Onde a regra 4 se aplica, e onde não

A regra 4 do CLAUDE.md separa determinístico de não-determinístico, e a
divisão aqui é limpa justamente porque a superfície do LLM é pequena:

- **Asserção dura** (é tudo determinístico): validação de parâmetro, execução
  do catálogo, gravação da consulta auditada, isolamento de tenant, recusa
  fora do catálogo, ausência de dígito estranho na resposta, e o comportamento
  contra cada saída adversária gravada.
- **Avaliação com limiar** (só isto): a qualidade da interpretação — dado um
  corpus de perguntas reais escritas do jeito que a lojista escreveria, quantas
  caem na intenção certa com os parâmetros certos. Responsabilidade do
  `especialista-testes`, com limiar declarado e sem asserção dura.

Vale dizer o que isso compra: mesmo com a interpretação **errada**, o sistema
não mente. Ele responde a pergunta errada, com número certo e rastreável, ou
recusa. O pior caso não é número inventado.

## Alternativas consideradas

- **Text-to-SQL com schema no prompt.** Descartada pelos três conflitos de
  regra acima. É mais poderosa no demo e pior no produto: a falha dela é
  silenciosa e cai justamente sobre a promessa central ("saber o lucro real").
- **Text-to-SQL restrito a *views* seguras, em transação somente-leitura.**
  Segunda colocada, e a versão defensável da anterior. Descartada por custo de
  verificação: continua sem responder "este SQL calculou o que foi perguntado?",
  e a memória de cálculo da decisão 0019 não sobrevive — a decomposição N0→N3
  não é um `SELECT`, é um motor com regra de teto e rateio. Reimplementá-la em
  SQL gerado seria ter duas verdades sobre margem.
- **Deixar o LLM redigir a resposta final a partir dos números já calculados.**
  Descartada **por ora**, e é a que eu mais espero reverter. O ganho é real
  (texto melhor), o risco é conhecido (o modelo reescreve um número no meio da
  frase). O caminho de volta já está preparado: como a resposta determinística
  existe antes, dá para deixar o modelo reescrever e **recusar a reescrita** se
  ela introduzir qualquer dígito que não estava no original. Fica para quando
  houver chave e a suíte de avaliação estiver de pé — sem ela, não há como
  medir se a reescrita piorou algo.
- **Esperar a chave de LLM para começar.** Descartada: 90% deste desenho é
  determinístico e testável hoje. Esperar trocaria trabalho por ociosidade.

## Consequências

- O produto promete "respondo estas perguntas, e provo cada número", não
  "pergunte qualquer coisa". É menos vendável numa demo e mais defensável num
  contrato — e combina com a única coisa que o produto tem de diferente.
- Toda pergunta nova é **código**, não prompt. Custa mais por pergunta e é
  exatamente por isso que cada uma nasce testada. O catálogo cresce por
  demanda observada, não por antecipação.
- A camada de IA herda o isolamento e a auditoria do resto do sistema, porque
  ela **reusa os mesmos serviços** em vez de falar com o banco por fora. Nenhum
  caminho novo até os dados.
- O custo de chamada de LLM fica baixo e previsível: uma classificação curta
  por pergunta, sem schema no prompt, sem dado do cliente no prompt além do
  texto que ele mesmo escreveu. Esse último ponto também é de privacidade —
  registrado em `docs/PENDENCIAS.md` porque tem lado jurídico.
