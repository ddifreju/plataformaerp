# LEIA PRIMEIRO

Se você é uma instância do Claude lendo isto, siga a ordem abaixo.

## Modo de operação deste projeto

**Você tem autoridade de decisão.** A fundadora trabalha sozinha, ~20h/semana.
O gargalo dela é tempo. Não devolva decisão técnica para ela.

Decida, registre em `docs/decisoes/`, e continue. Ela revisa depois.

Pare apenas quando: falta credencial externa, a ação é irreversível com custo
real, ou a decisão é de negócio e não de software. Nesses casos, escreva em
`docs/PENDENCIAS.md` e **siga para outra tarefa** — nunca fique ocioso.

## O que é este projeto

Plataforma unificada de gestão para e-commerce brasileiro. Multi-tenant.
Integra marketplaces, ERPs e canais de comunicação num modelo de dados único,
com camada de IA que responde perguntas sobre a operação.

O problema central: o lojista brasileiro não sabe seu lucro real. Os
integradores atuais mostram faturamento bruto e chamam de resultado, sem
deduzir corretamente taxa de marketplace, frete, imposto, devolução e Ads.

## Ordem de leitura

0. **`docs/continuidade/00-LEIA-PRIMEIRO.md`** — contexto completo mais
   recente (outubro/2026): visão, jeito de trabalhar da Juliana, acessos,
   histórico, próximos passos. Comece por lá.
1. Este arquivo
2. `docs/ESTADO.md` — onde o projeto está
3. `docs/PENDENCIAS.md` — o que está travado por falta de credencial
4. `CLAUDE.md` — convenções (carrega automaticamente)
5. `docs/decisoes/` — decisões já tomadas, não reabra sem motivo forte

## Ponto de entrada

Use o agente `gerente-projeto` para qualquer trabalho. Ele orquestra os
outros dez.

## Quando falta credencial

Construa contra mock: adaptador com payload gravado, teste passando,
interface funcionando com dado falso. Quando a credencial chegar, troca.

Falta de credencial atrasa a validação, não a construção.

## Regra permanente

Ao terminar qualquer tarefa, ATUALIZE `docs/ESTADO.md`. É a memória entre
sessões.
