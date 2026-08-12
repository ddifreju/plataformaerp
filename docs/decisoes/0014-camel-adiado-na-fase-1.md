# 0014 — Apache Camel adiado; adaptadores são classes Spring simples

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

A decisão 0001 e o CLAUDE.md fixam Apache Camel como a ferramenta de
integrações. A Fase 1 constrói os dois primeiros adaptadores (Mercado Livre e
Bling) — mas ambos **contra fixture**, sem credencial e sem rede.

A pergunta prática: os adaptadores da Fase 1 devem já nascer como rotas Camel?

## Decisão

**Não nesta fase.** Os adaptadores são classes Spring comuns por trás de uma
interface de porta (`AdaptadorDeCanal`), e o pipeline de ingestão é um serviço
Java direto.

A dependência do Camel **continua declarada** no `pom.xml` e a decisão 0001
**não é revogada**. Camel entra quando aparecer o problema que ele resolve.

Gatilho explícito para adotar Camel — quando qualquer um destes for verdade:
1. Precisar de agendamento e *polling* real de várias fontes
2. Precisar de retentativa com *backoff*, *dead letter* e circuit breaker
3. Aparecer um terceiro protocolo além de HTTP/JSON (SFTP, fila, e-mail)
4. O roteamento entre fonte e destino virar condicional o bastante para não
   caber em `if`

## Alternativas consideradas

- **Já escrever rotas Camel agora.** Descartada. Nesta fase, uma "integração" é
  ler um arquivo JSON de fixture e traduzir para o modelo canônico. Envolver
  isso num `RouteBuilder` acrescenta um DSL, um contexto e um modelo de erro
  próprios para transportar um objeto de A para B dentro do mesmo processo.
  Pesa mais contra: **nada disto foi executado ainda** — nem a Fase 0. Depurar
  uma rota Camel que não sobe, sem ter certeza de que o Spring Boot sequer
  compila, é somar duas incógnitas. A fundadora tem 20h/semana; a primeira
  execução precisa falhar em poucos lugares, não em muitos.
- **Remover a dependência do Camel do pom.** Descartada: a decisão 0001 é
  recente, deliberada e continua correta para o estado final. Adiar o uso não é
  reverter a escolha.
- **Abstração própria de "rota" caseira.** Descartada com força: seria escrever
  um Camel ruim. Se o problema chegar, use o Camel de verdade.

## Consequências

- A tradução fonte → canônico fica em código Java comum, testável com JUnit,
  sem subir contexto de integração. Barato de escrever e de entender depois
- O contrato de porta (`AdaptadorDeCanal`) é o que preserva a opção: quando
  Camel entrar, ele entra **em volta** dos adaptadores (transporte, agendamento,
  retentativa), não dentro deles. A lógica de tradução não é reescrita
- Se este adiamento virar permanente por inércia, a decisão 0001 precisa ser
  atualizada para refletir a realidade. Revisar quando o primeiro conector real
  com credencial entrar em produção
