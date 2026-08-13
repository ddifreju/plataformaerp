# 0024 — A fresta de login no RLS, e o e-mail único global

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [DIFÍCIL DE REVERTER]

Registra duas decisões do `arquiteto-dados` na V014 que eu avaliei e aceitei.
Ambas são estruturais e contraintuitivas — por isso ficam registradas em vez de
só comentadas no SQL.

## Problema 1 — o ovo e a galinha do login

O molde 0010 diz: toda tabela filtra por `tenant_id = app_current_tenant_id()`.
Mas o login acontece **antes** de haver tenant no contexto. Aplicado à risca, o
RLS esconderia todas as linhas de `usuario` e **ninguém conseguiria entrar**.

### Decisão

Uma quinta policy, `usuario_select_login`, mutuamente exclusiva com a de tenant
por construção:

- `usuario_select` exige `app_current_tenant_id() IS NOT NULL`
- `usuario_select_login` exige `app_current_tenant_id() IS NULL` **e**
  `email = current_setting('app.login_email')`

Dentro de requisição autenticada a policy de login é sempre falsa. Fora de
sessão, ela abre **no máximo uma linha**, e só se alguém setar o GUC de
propósito.

O GUC `app.login_email` é **LOCAL** (`set_config(..., is_local => true)`),
setado dentro da mesma instrução que faz a leitura. Isso é o detalhe que segura
tudo: a decisão 0010 registra que `SET` de sessão **vaza no pool**, e a conexão
voltaria ao HikariCP com a fresta aberta para aquele e-mail.

### O que isso expõe, dito sem eufemismo

Quem já tem a senha do `app_aplicacao` e um e-mail **exato** lê aquela linha,
incluindo o `senha_hash` (BCrypt). É um oráculo de existência: "este e-mail tem
conta, e em qual loja".

O que **não** expõe: enumeração (é igualdade exata, não prefixo nem lista, então
não dá para *descobrir* endereços), nenhuma outra linha, nenhuma outra tabela, e
nada por acidente — sem o GUC setado a comparação vira `email = NULL`, falso,
zero linhas.

A linha de base honesta: `app_aplicacao` já pode setar `app.tenant_id` com
qualquer valor. Este RLS contém **bug de aplicação**, não um papel de banco
hostil. Se a senha do app vazar, o banco vaza com ou sem esta policy. A fresta
acrescenta uma superfície de formato conhecido, não uma classe nova de risco.

### Alternativas consideradas

- **Função `SECURITY DEFINER`.** Descartada: no Postgres ela roda com os
  privilégios do dono, que aqui **é superusuário** — o que ignora RLS por
  completo. Trocaria uma fresta estreita e auditável por uma porta larga.
- **Tabela sem RLS, como `tenant` (V002).** Descartada: `tenant` é catálogo com
  nome e slug; `usuario` tem hash de senha e dado pessoal. Deixar a tabela
  inteira legível para qualquer conexão sem contexto é muito pior.
- **Papel de banco separado só para autenticar.** Tecnicamente o mais limpo, e
  descartada por custo operacional: mais um papel, mais uma senha para
  gerenciar, mais um `DataSource` no Spring — para quem opera sozinha 20h/semana.

## Problema 2 — o e-mail é único por tenant ou global?

### Decisão: `UNIQUE (email)` global

Contraintuitivo num sistema multi-tenant, e é a única escolha que faz o login
funcionar.

Com `UNIQUE (tenant_id, email)`, o mesmo e-mail poderia existir em dois tenants.
No momento do login **ainda não há tenant** — é justamente o que se quer
descobrir. O sistema teria que perguntar "de qual loja você é?" antes da senha,
o que é péssima experiência e ainda vaza em quais lojas o e-mail existe.

### Custo aceito

A mesma pessoa atendendo duas lojas precisa de dois e-mails. É real e incômodo,
e é o caso que a decisão 0023 já previu: quando virar demanda, a solução é
`usuario_tenant` com troca explícita de contexto — não afrouxar esta constraint.

## Consequências

- O `papel` (dono/gestor/analista) existe para as telas saberem o que mostrar.
  **Não é autorização** e não protege nada ainda — transformar em autorização de
  verdade é tarefa futura, registrada como tal para não parecer que já protege.
- Se alguém quebrar o mecanismo do GUC local, o modo de falha é "ninguém
  consegue logar": alto, imediato e seguro.
