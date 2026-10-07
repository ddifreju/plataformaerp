# 0035 — Grupo de empresas: várias contas, um dono, uma visão

**Data:** 7 de outubro de 2026
**Status:** proposta (aprovada no conceito pela Juliana; a fase 1 ainda não começou)
**Reversibilidade:** [DIFÍCIL DE REVERTER] na fase 1 (muda o login)

## Contexto

A Juliana quer que um cliente com várias operações de e-commerce (por exemplo
eletrônicos, roupa e maquiagem, cada uma com seu CNPJ) possa:

1. ter uma assinatura do Radar para cada empresa;
2. entrar em todas com um login só;
3. ver o grupo inteiro: faturamento, lucro, estoque e valor, somados e por
   empresa;
4. gerenciar o estoque das empresas juntas.

Ela vê isso como diferencial. Concordo: os ERPs do mercado tratam
"multiempresa" como filial dentro de uma conta, e não como painel de dono de
grupo.

## Decisão

**Cada empresa continua sendo um tenant.** O grupo é uma camada acima, que
nunca mistura dados no banco. Isso mantém a regra 1 do projeto ("tenant é
identidade") e as quatro camadas da decisão 0007 intactas.

### Fase 1 — um login, várias empresas

- Nova tabela `usuario_tenant` (usuário × tenant × papel), exatamente como as
  decisões 0023 e 0024 já previam.
- A troca de empresa é **explícita**:
  - um endpoint troca o tenant da sessão;
  - ele confere que o vínculo existe;
  - ele gira o id da sessão;
  - ele audita a troca.
- Nunca um parâmetro por requisição. O tenant continua saindo da sessão, e não
  do cliente.
- Só o dono das duas contas pode vincular uma empresa à outra, com confirmação
  nas duas pontas.

### Fase 2 — painel do grupo (somente leitura)

- O backend roda **a mesma consulta uma vez por empresa**, cada uma com o seu
  próprio contexto de tenant e RLS.
- Depois soma em Java com `BigDecimal`.
- Não existe consulta que atravesse tenants no SQL. Assim o RLS continua sendo
  a segunda camada, sem exceção.
- Cada número do painel guarda as consultas e os IDs de cada empresa (regra 3).
- Se uma empresa não tiver o dado (por exemplo, custo não informado), o painel
  diz isso em vez de somar como zero (regra 5).

### Fase 3 — estoque do grupo

- **Visão unificada:** quanto tem de cada produto em cada empresa, lado a lado.
  É só leitura, e usa o mesmo mecanismo da fase 2.
- **Transferência entre empresas:** mover estoque de um CNPJ para outro gera a
  nota de transferência ou venda entre elas.
  - Por isso a transferência depende da emissão de NF-e.
  - **Ponto fiscal importante:** CNPJs diferentes não podem compartilhar um
    estoque só. Cada empresa precisa ter o seu, com nota para entrar e sair.
  - "Um estoque único" é, portanto, uma visão unificada com transferência
    fácil, e não um saldo misturado.

## Alternativas consideradas

- **Várias empresas dentro de um tenant (filial).** Descartada. Mistura CNPJs
  no mesmo isolamento, e a nota, o certificado e os impostos são por CNPJ.
  Também quebraria o modelo de uma assinatura por empresa.
- **Consulta SQL que atravessa tenants para o painel.** Descartada. Exigiria um
  papel de banco que ignora o RLS, que é exatamente a porta que a decisão 0024
  recusou.

## Consequências

- A fase 1 mexe em login e resolução de tenant. Exige teste de isolamento:
  - um usuário sem vínculo não troca;
  - a troca não vaza dado da empresa anterior;
  - a sessão antiga é invalidada.
- A fase 1 também exige revisão de segurança antes do merge.
- As fases 2 e 3 não mexem no modelo de segurança, só o usam.
- A cobrança por empresa fica fora desta decisão (ainda não existe cobrança).
