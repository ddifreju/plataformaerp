# Frontend

Next.js 15 + React + Tailwind. **Ainda não inicializado.**

## Por que está vazio

O scaffold do Next.js é gerado por `create-next-app`, que precisa do Node
instalado — e esta máquina ainda não tem Node. Escrever à mão um
`package.json` e uma árvore de `app/` seria inventar a saída de um gerador,
com risco alto de ficar sutilmente errada e de travar a fundadora depois.

Além disso, a interface é a **Fase 3** da fila (tarefas 17 a 20). Nada da
Fase 0 depende do frontend existir.

## Como inicializar quando chegar a hora

Com Node 20+ instalado, a partir da raiz do projeto:

```bash
npx create-next-app@latest frontend \
  --typescript --tailwind --eslint --app --src-dir \
  --import-alias "@/*"
```

Responda "não" para Turbopack se quiser o comportamento mais estável.

## O que já está decidido e vale para o frontend

- O tenant **nunca** é escolhido pelo cliente no navegador. Ele vem da sessão
  autenticada. Ver `docs/decisoes/0007-propagacao-de-tenant.md`.
- Valor monetário chega do backend como string decimal e é formatado na
  exibição. **Nunca** use `number` do JavaScript para dinheiro — o
  `0.1 + 0.2` do float é exatamente o bug que este produto existe para não ter.
- A identidade visual depende do nome da marca, que é decisão de negócio
  pendente. Ver `docs/PENDENCIAS.md`.
