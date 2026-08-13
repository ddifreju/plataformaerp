# Frontend

Next.js 16 (App Router) + React 19 + Tailwind + TypeScript.
Gerado com `create-next-app`. Ver `docs/decisoes/0022-next-16-e-sessao-no-servidor.md`.

## Rodar

```bash
npm install     # só na primeira vez
npm run dev     # http://localhost:3000
npm run build   # verificação antes de commitar
```

O backend precisa estar no ar (`make dev` na raiz, depois `make migrate`).

## As quatro regras que não se negociam aqui

Estão na decisão 0022. Repetidas aqui porque é onde se erra:

1. **O navegador nunca escolhe o tenant.** Ele vem da sessão assinada no
   servidor. Se o cliente puder informá-lo, qualquer pessoa lê dado de qualquer
   loja trocando um header.
2. **Dinheiro nunca vira `number`.** Chega como string decimal, é formatado
   como string. `0.1 + 0.2 !== 0.3` é o bug que este produto existe para não
   ter.
3. **O frontend não recalcula margem.** Toda conta vem do motor. Uma subtração
   na tela cria uma segunda fonte de verdade, e as duas vão divergir.
4. **Lacuna não vira zero.** "Não tenho esse dado" é texto, nunca `R$ 0,00`.
   Zero é indistinguível de custo inexistente e infla o lucro aparente.

## Nome da marca

Ainda não definido — é pendência de negócio (`docs/PENDENCIAS.md`). O nome de
exibição vive numa constante única, para ser trocado em um lugar só.
