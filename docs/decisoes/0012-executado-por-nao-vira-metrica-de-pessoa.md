# 0012 — `executado_por` é prova de auditoria, não métrica de pessoa

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

Reconcilia a decisão 0003 (métricas de processo, nunca de pessoa) com a tabela
`consulta_auditada`, criada depois dela. A `revisor-seguranca` apontou que as
duas nunca haviam sido postas lado a lado.

## Contexto

A regra 3 do CLAUDE.md exige rastreabilidade: guardar a query executada e os IDs
retornados para provar ou corrigir um número contestado. A V003 implementa isso
em `consulta_auditada`, que inclui a coluna `executado_por` — a identidade de
quem originou a consulta.

A decisão 0003 proíbe ranking de produtividade individual, por exposição sob a
LGPD e sob o direito do trabalho, e porque o analista sabota a adoção de uma
ferramenta que o deduram.

`executado_por` é dado pessoal de empregado. A coluna é legítima e necessária —
mas o dado, uma vez gravado, fica disponível para qualquer `GROUP BY`.

## Decisão

A coluna fica. A finalidade é **restrita e declarada**: provar um número
específico quando alguém o contesta.

Três restrições que passam a valer:

1. **Nenhuma agregação por `executado_por`.** Qualquer relatório, dashboard ou
   endpoint que faça `GROUP BY executado_por`, `COUNT(*) ... BY executado_por`
   ou ordene pessoas por volume **contradiz a 0003** e exige decisão explícita
   antes de ser escrito. Isso vale especialmente para a tarefa 19 ("visão do
   gestor: gargalos do processo") — gargalo é do processo, não do analista.
2. **Acesso pontual, não listagem.** Quando a Fase 3 expuser auditoria, o
   caminho é consulta por `id` da consulta contestada. Não é uma tela de
   "consultas por usuário".
3. **Retenção definida antes da produção.** Hoje a tabela é append-only e cresce
   para sempre. Antes de rodar com cliente real, precisa de prazo de expurgo ou
   anonimização de `executado_por` (a prova do número não depende de saber quem
   a rodou depois de encerrado o prazo de contestação).

## Alternativas consideradas

- **Remover a coluna.** Descartada: perde-se a capacidade de investigar uso
  indevido e de responder "quem gerou este número".
- **Hash de `executado_por`.** Descartada por ora: pseudonimização com conjunto
  pequeno de usuários é reversível por frequência, então dá pouca proteção real
  e atrapalha a investigação legítima.
- **Deixar como está sem registrar.** Descartada: o risco não é técnico, é de
  desvio de finalidade. O dado disponível vira feature sem ninguém decidir. O
  registro existe para que a decisão seja consciente quando alguém propuser.

## Consequências

- Não bloqueia nada na Fase 0 ou 1
- Vira item de revisão obrigatório na tarefa 19
- **Pendente de decisão de negócio:** base legal e prazo de retenção. Anotado em
  `docs/PENDENCIAS.md`
