# 0028 — Dados de demonstração ficam fora do Flyway

**Data:** 13 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

Para validar as três telas ponta a ponta era preciso um banco com conteúdo:
tenant, usuário com senha conhecida, canal, pedidos que produzissem margem
`CALCULADA` e `COM_TETO`, um evento em `ERRO` e uma devolução aberta.

A pergunta é onde esse conteúdo mora. A tentação óbvia é uma migration — o
Flyway já está configurado e roda sozinho.

## Decisão

**`infra/dados-demo.sql`, aplicado por `make dados-demo`. Nunca uma migration.**

Migration descreve **estrutura**; isto é **conteúdo descartável**. Se virasse
migration, o Flyway o aplicaria em produção no primeiro deploy — e não existe
desfazer para dado que um cliente já viu. Um tenant fictício aparecendo na base
de produção é o tipo de erro que só se descobre pelo suporte.

Por isso o arquivo vive em `infra/`, fora de `db/migration/`, onde o Flyway não
enxerga.

### As duas travas

1. **Exige `-v confirmo=sim`.** Sem a variável, o `psql` aborta na primeira
   linha. Nenhuma execução acidental funciona — quem roda, roda de propósito.
2. **Recusa se achar tenant que não seja de demo.** Um banco com cliente de
   verdade tem tenant de verdade; encontrar um é prova suficiente de que aquele
   banco não é descartável. O script aborta com mensagem explícita.

Uma trava sozinha não bastava: a primeira protege contra o dedo escorregando, a
segunda contra o script ser chamado por engano de um lugar que passa a
confirmação.

### A senha

Gerada pelo **pgcrypto**, com `crypt('demo1234', gen_salt('bf', 12))`. O formato
`$2a$12$...` é lido diretamente pelo `BCryptPasswordEncoder` do Spring e
satisfaz o `CHECK` de formato da V014.

Nenhum hash fica escrito no arquivo — o SQL o gera na hora. A senha em claro
existe só como literal de entrada, num arquivo que anuncia em três lugares que
vale apenas para o banco local.

### Idempotente

Apaga o tenant de demo e recria. Rodar duas vezes deixa o mesmo estado, não o
dobro de pedidos.

## Alternativas consideradas

- **Migration `V016__dados_demo.sql`.** Descartada pelo motivo central acima.
  Também poluiria o histórico do Flyway com algo que não é schema.
- **Perfil Spring `demo` com um `CommandLineRunner`** populando via JPA.
  Descartada: colocaria código de dado fictício dentro do artefato que vai para
  produção, e bastaria a variável de perfil errada para executá-lo lá. SQL fora
  do jar não tem como rodar por acidente.
- **Deixar cada pessoa criar seus dados à mão.** Descartada: a montagem correta
  exige acertar chave composta, `NUMERIC` com escala, valores de `CHECK` e o
  hash da senha. Errar isso silenciosamente produz tela vazia sem explicação.

## Consequências

- `make dados-demo` dá um ambiente demonstrável em segundos
- O cenário serve de referência viva para o que cada tela precisa: quem alterar
  o motor de margem pode conferir contra os números do documento fiscal (§2.5),
  que os dados reproduzem
- Credenciais de demonstração (`dono@demo.local` / `demo1234`) **só existem em
  banco local**. Não há caminho para elas chegarem a produção
