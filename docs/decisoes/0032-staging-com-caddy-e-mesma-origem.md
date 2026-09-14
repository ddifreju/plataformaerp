# 0032 — Staging com Caddy, mesma origem, e CORS que morre em produção

**Data:** 14 de setembro de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL] — trocar o proxy é mudar um serviço do
compose e um arquivo de configuração.

## Contexto

A condição 2 da decisão 0025 diz, sem meio-termo: em produção, frontend e
backend ficam **na mesma origem, atrás de proxy reverso**. Isso não é
preferência de infraestrutura — é o que sustenta o CSRF estar desligado.

Essa condição nunca foi exercitada. Em dev, o `next.config.ts` faz rewrite de
`/api/*` para o backend, o que é bom para a segurança (o navegador só fala com
a própria origem) e péssimo para a validação: **esconde** tanto a configuração
de CORS quanto os atributos do cookie no fio. Um erro em qualquer um dos dois
só apareceria em produção.

A Fase 4, bloco B (decisão 0029), existe para fechar isso antes de haver VPS.

## Decisão

### Proxy reverso: Caddy

O staging sobe atrás do **Caddy**, não do nginx.

O motivo é o critério do projeto — fundadora solo, 20h/semana, manutenibilidade
acima de tudo. A configuração equivalente em nginx tem dezenas de linhas de
`proxy_set_header`, `upstream` e bloco de TLS, cada uma delas uma chance de
errar em silêncio. O Caddyfile que faz o mesmo tem menos de dez linhas, repassa
os cabeçalhos certos por padrão e resolve TLS sozinho.

Especificamente para o que precisamos provar: `tls internal` dá certificado
local confiável **sem depender de domínio registrado nem de internet**. É o que
torna possível exercitar cookie `Secure` hoje, numa máquina sem VPS — que é
exatamente o buraco que este bloco fecha.

### Uma origem só, e `/api` não é exceção

O Caddy serve o frontend em `/` e repassa `/api/*` ao backend. Do ponto de
vista do navegador existe **um** host. Consequência direta e desejada:

**CORS deixa de existir no caminho de produção.** A configuração de CORS
continua no código, mas passa a ser dirigida por variável de ambiente e fica
**vazia por padrão**. Em staging e produção, nenhuma origem é permitida — não
porque a lista está errada, mas porque não há requisição cross-origin para
permitir. Em dev, a variável traz `http://localhost:3000`.

Isso é melhor que "configurar CORS direito": uma allowlist vazia não tem como
ser larga demais.

### Cookie: os atributos vão para o fio, e um teste olha

No perfil de staging, `Secure=true`, `HttpOnly=true`, `SameSite=Lax`. Sem TLS
o `Secure` derrubaria o login, então ele **só** liga junto com o proxy — os
dois andam juntos, por isso estão na mesma decisão.

A verificação é feita contra o `Set-Cookie` que sai do Caddy, não por leitura
de arquivo de configuração. Já aconteceu neste projeto de o código parecer
completo e o comportamento estar ausente (o login que não persistia sessão);
a lição foi priorizar execução.

### O que este bloco NÃO faz

Não faz deploy. Não cria VPS, não registra domínio, não compra nada, não sobe
nada para lugar nenhum. Deploy é ação irreversível com custo real e é da
fundadora. O que fica pronto é: imagens que constroem, um compose que sobe a
pilha inteira numa origem só, e um checklist do que conferir quando a máquina
existir.

## Alternativas consideradas

- **nginx.** O padrão da indústria e a escolha certa para quem já o conhece.
  Descartada pelo critério do projeto: mais linhas, mais cerimônia de TLS, e
  cada linha é manutenção de quem tem 20h/semana. Se um dia houver time de
  infraestrutura, trocar é meia hora.
- **Traefik.** Bom com Docker e descoberta automática de serviço. Descartado
  por resolver um problema que não temos (orquestração dinâmica) ao custo de
  um modelo mental maior que o do serviço que ele serve.
- **Manter o rewrite do Next também em produção**, com o Next na frente.
  Descartada: põe o Node no caminho de toda requisição de API, acopla a
  disponibilidade do backend à do frontend, e continua sem exercitar o cookie
  em TLS. Além disso mantém exatamente o ponto cego que este bloco veio tirar.
- **Provar CORS configurando duas origens de propósito em staging.**
  Descartada por ser o inverso do objetivo: o certo é provar que produção **não
  precisa** de CORS, não fazer CORS funcionar bonito. Se um dia o frontend
  morar em outro domínio, cai a condição 2 da 0025 e, junto com ela, o CSRF
  desligado — aí a conversa é outra e está prevista na condição 3.

## Consequências

- A pendência "CORS nunca exercitado" muda de natureza: deixa de ser "falta
  testar" e vira "não se aplica em produção, por desenho" — com o teste que
  prova a mesma origem no lugar
- Aparece um serviço novo no compose de staging e um `Caddyfile` no repositório
- O perfil de staging exige TLS. Rodar o backend com `Secure=true` sem proxy
  quebra o login, e isso precisa estar escrito onde quem for rodar vai ler
- Quando a VPS existir, trocar `tls internal` pelo domínio real é uma linha, e
  o Caddy resolve o certificado sozinho
