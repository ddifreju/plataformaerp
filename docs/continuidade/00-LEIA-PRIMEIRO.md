# Continuidade do Radar — leia primeiro

Escrito em 07/10/2026 pela sessão do Claude Code na nuvem (claude.ai/code)
que trabalhou com a Juliana de setembro a outubro de 2026. A Juliana vai
continuar em outra sessão, rodando **local** no computador dela.

Esta pasta junta tudo o que a sessão sabia: o que é o Radar, como a Juliana
trabalha, onde está cada coisa, o que já foi feito e o que vem a seguir. A
ideia é não perder nada de contexto.

## Ordem de leitura

1. **Este arquivo.**
2. [`01-o-que-e-o-radar.md`](01-o-que-e-o-radar.md): a visão do produto e o
   que ele já faz.
3. [`02-como-a-juliana-trabalha.md`](02-como-a-juliana-trabalha.md): o jeito
   de conversar, decidir e entregar. **Não pule este.**
4. [`03-acessos-e-infraestrutura.md`](03-acessos-e-infraestrutura.md): cada
   site e serviço usado, para que serve e como entrar.
5. [`04-historico-do-que-foi-feito.md`](04-historico-do-que-foi-feito.md):
   todos os PRs, em ordem, e as decisões tomadas no caminho.
6. [`05-codigo-e-padroes.md`](05-codigo-e-padroes.md): como o código está
   organizado e os padrões que se repetem.
7. [`06-como-rodar-e-publicar.md`](06-como-rodar-e-publicar.md): rodar local,
   testar, publicar (PR → merge → Render/Vercel), aplicar migration.
8. [`07-proximos-passos.md`](07-proximos-passos.md): a fila de trabalho, em
   ordem.
9. [`08-licoes-e-armadilhas.md`](08-licoes-e-armadilhas.md): erros que já
   aconteceram e como evitar.
10. [`PROMPT-NOVA-SESSAO.md`](PROMPT-NOVA-SESSAO.md): a mensagem que a
    Juliana cola na sessão nova.

Também valem, e continuam atualizados:
- `CLAUDE.md` na raiz: as regras inegociáveis, que carregam sozinhas.
- `docs/decisoes/`: 35 decisões registradas. Não reabra sem motivo forte.
- `docs/PENDENCIAS.md`: pendências, inclusive os blocos de Produtos.
- `docs/ESTADO.md` e `docs/CONTEXTO-HANDOFF.md`: o handoff anterior (agosto,
  fases 0 a 3). É história; o que está aqui é mais novo.

## Senhas e chaves

**Não estão nesta pasta, de propósito.** O repositório vai para o GitHub e
a regra do projeto é "nenhum segredo em código". A Juliana copia
[`ACESSOS-LOCAL.exemplo.md`](ACESSOS-LOCAL.exemplo.md) para
`ACESSOS-LOCAL.md` na mesma pasta, preenche no computador dela, e o git
ignora esse arquivo. A sessão local pode ler o arquivo de lá.

Exceção, porque é pública de propósito: o login de demonstração do Radar,
`dono@demo.plataforma` / `demo1234`.

## Resumo em 10 linhas

- **Radar** é o produto: um ERP inteligente para e-commerce brasileiro que
  quer ser um funcionário, não só um sistema. Ele automatiza, mostra o
  número real e deixa o lojista à frente da concorrência.
- **Stack:** Java 21 + Spring Boot 3 (JdbcTemplate), PostgreSQL com RLS,
  Next.js 16 + React 19.
- **No ar:** frontend na Vercel (`https://plataformaerp.vercel.app/radar`),
  backend no Render (`radar-api-navega`), banco no Supabase (São Paulo).
- **Fluxo de trabalho:** a IA implementa, testa local, abre PR, faz o merge e
  confere no site. A Juliana testa no site e aprova.
- **Já pronto:**
  - cadastros completos (produtos, clientes, fornecedores, vendedores,
    categorias, embalagens) e anúncios por loja;
  - pedidos com lote e filtros;
  - Configurações com 8 abas;
  - dados da empresa e usuários;
  - Bloco 1 de Produtos.
- **Próximo:** Bloco 2 de Produtos (variações e kits), depois os Blocos 3 e 4
  e o pacote fabricado + lote/validade. Depois, página por página.
- **Travado até o CNPJ:** NF-e e conexão real com os marketplaces.
