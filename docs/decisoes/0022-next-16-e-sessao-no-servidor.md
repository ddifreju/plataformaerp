# 0022 — Next.js 16 (não 15) e o que o frontend nunca faz

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O CLAUDE.md e a decisão 0001 dizem "Next.js 15". Ao rodar
`create-next-app@latest`, o gerador oficial instalou **Next.js 16.3.0** com
React 19.2.8 — a 16 passou a ser a estável desde então.

## Decisão

**Fica o Next.js 16.** O `CLAUDE.md` foi atualizado para dizer 16, porque
documentação que mente sobre a stack é pior que documentação ausente.

Forçar a 15 seria escolher deliberadamente uma versão mais velha, contra o
gerador oficial, sem nenhum ganho: nada do que a Fase 3 constrói (App Router,
Server Components, Tailwind) muda entre 15 e 16.

## O que o frontend NUNCA faz

Registro aqui porque vale para toda a Fase 3 e para quem entrar depois:

1. **O navegador nunca escolhe o tenant.** O `X-Tenant-Id` da Fase 0 é
   `// PROVISÓRIO` e morre na tarefa 17. O tenant passa a sair da sessão
   assinada no servidor. Se o cliente puder informar o tenant, qualquer pessoa
   lê dado de qualquer loja trocando um header — escalação horizontal trivial.
2. **Dinheiro nunca vira `number` do JavaScript.** Valor monetário chega como
   string decimal e é formatado como string. `0.1 + 0.2 !== 0.3` é exatamente o
   bug que este produto existe para não ter. O backend já entrega com 2 casas
   pela borda de saída (`Apresentacao`), então o frontend **formata, nunca
   recalcula**.
3. **O frontend não recalcula margem.** Toda conta vem do motor. Se a tela
   fizer uma subtração, existem duas fontes de verdade para o mesmo número e
   elas vão divergir.
4. **Lacuna não vira zero.** "Não tenho esse dado" é renderizado como texto,
   nunca como `R$ 0,00`. Um zero é indistinguível de um custo que não existe, e
   infla o lucro aparente.

## Alternativas consideradas

- **Fixar Next 15 para bater com o documento.** Descartada: ajustar o documento
  ao fato é mais barato e mais honesto que ajustar o fato ao documento.
- **Não usar o `create-next-app` e montar à mão.** Descartada — e chegou a ser
  necessária: a primeira execução do gerador falhou no `npm install` por rede.
  Mas ele **já havia gerado a árvore de arquivos** antes de abortar, e o
  registry voltou a responder logo depois. Bastou rodar `npm install`. Montar à
  mão teria produzido um scaffold pior por um problema transitório.

## Consequências

- `frontend/` tem 358 pacotes e `npm run build` passa
- `node_modules/` já está no `.gitignore` da raiz
- A marca ainda não tem nome (pendência de negócio): o nome de exibição fica
  numa constante única, trocável em um lugar só
