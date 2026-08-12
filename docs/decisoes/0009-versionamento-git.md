# 0009 — Repositório git inicializado localmente

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O diretório do projeto não era um repositório git. A partir da Fase 0 passa a
existir código de verdade, e sem versionamento não há como voltar atrás de um
erro nem entender o que mudou entre sessões de trabalho.

## Decisão

`git init` local, branch padrão `main`, com `.gitignore` cobrindo Java, Node e
arquivos de ambiente.

**Nenhum remoto configurado e nenhum push.** Publicar o repositório envolve
escolher plataforma e visibilidade, e é decisão da fundadora.

Commits em português, imperativo, conforme CLAUDE.md.

## Alternativas consideradas

- **Continuar sem git.** Descartada: perder trabalho por falta de
  versionamento é o tipo de acidente que custa dias de quem só tem 20h/semana.
- **Já criar repositório no GitHub e dar push.** Descartada: é ação externa com
  efeito público (o código passa a existir fora da máquina dela). Cai na regra
  de parar. Registrado em `docs/PENDENCIAS.md`.

## Consequências

- Histórico local desde a fundação do projeto
- **Não há backup fora da máquina** enquanto não houver remoto — este é o risco
  aberto e está em `PENDENCIAS.md`
- O `.gitignore` já bloqueia `.env`, que é onde ficam as credenciais
