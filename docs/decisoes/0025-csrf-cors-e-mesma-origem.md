# 0025 — CSRF desligado apoiado em `SameSite`, e mesma origem em produção

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

O `engenheiro-backend` marcou esta como "a decisão mais discutível, vale sua
revisão". Ele estava certo em sinalizar. Avaliei e mantenho — **com condições
explícitas**, porque sem elas a escolha deixa de ser segura.

## Contexto

A autenticação usa cookie de sessão (decisão 0023). Cookie de sessão é
exatamente o que torna CSRF possível: o navegador o envia sozinho, então um site
malicioso pode disparar uma requisição autenticada em nome do usuário.

O Spring Security liga proteção CSRF por padrão. Ela foi desligada, apoiada no
atributo `SameSite=Lax` do cookie.

## Decisão

**CSRF permanece desligado**, sustentado por `SameSite=Lax`. Sob `Lax`, o
navegador **não envia o cookie** em requisição de escrita vinda de outro site —
que é a definição do ataque.

Isso só vale enquanto **as três condições abaixo** forem verdadeiras. Elas não
são recomendações; são o que sustenta a decisão:

1. **Nenhum `GET` pode alterar estado.** `Lax` *permite* o cookie em navegação
   top-level `GET`. Um `GET /api/algo/excluir` seria explorável mesmo com `Lax`.
   Hoje todo `GET` é leitura e todo escrita é `POST` — inclusive o logout.
2. **Em produção, frontend e backend na mesma origem**, atrás de proxy reverso.
   Isso elimina CORS e mantém o cookie estritamente primeira-parte.
3. **Se algum dia o cookie precisar de `SameSite=None`** (frontend em domínio
   diferente do backend), **a proteção CSRF volta a ser obrigatória**, sem
   discussão. `None` desliga exatamente a defesa em que esta decisão se apoia.

Se qualquer uma cair, esta decisão cai junto.

## CORS em desenvolvimento

Em dev, o frontend roda em `localhost:3000` e o backend em `localhost:8080` —
origens diferentes, então o navegador exige CORS com credenciais.

Configurado como **allowlist explícita da origem de dev**, por variável de
ambiente, com `allowCredentials = true`. Nunca `*`: com credenciais, o curinga
é proibido pelo próprio padrão, e aceitar qualquer origem aqui devolveria o
problema que o `SameSite` resolve.

Nota técnica que evita confusão futura: `localhost:3000` e `localhost:8080` são
**o mesmo site** para efeito de `SameSite` (o atributo olha o domínio
registrável, não a porta). Ou seja, em dev o cookie flui entre eles normalmente
sob `Lax` — o que precisa de configuração é o **CORS**, não o `SameSite`.

## Alternativas consideradas

- **Manter o CSRF token do Spring.** É a opção conservadora e continua sendo o
  caminho se a condição 2 cair. Descartada agora porque, com SPA e cookie
  `Lax` de primeira parte, ela adiciona um ciclo de token (ler cookie
  `XSRF-TOKEN`, reenviar em header) que é fonte recorrente de bug de integração
  — para proteger contra um vetor que o `Lax` já fecha.
- **`SameSite=Strict`.** Mais forte e descartada por efeito colateral concreto:
  o usuário que chega por um link externo (e-mail de alerta, por exemplo) cairia
  deslogado, porque o cookie não acompanha a primeira navegação.
- **JWT em header** (imune a CSRF por não usar cookie). Descartada na 0023, por
  motivos que continuam valendo — e trocaria CSRF por guardar credencial em
  JavaScript, exposta a XSS.

## Consequências

- Menos código no caminho de login e menos um modo de falha em integração
- **A condição 1 vira regra de revisão**: qualquer endpoint `GET` que altere
  estado quebra a segurança do sistema, não só o estilo. Vale conferir em toda
  revisão daqui em diante
- A configuração de produção (proxy reverso, mesma origem) passa a ser
  requisito de segurança, não preferência de infraestrutura. Registrado em
  `docs/PENDENCIAS.md` junto do item de VPS
