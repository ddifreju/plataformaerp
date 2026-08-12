# 0011 — Migration roda fora do boot da aplicação

**Data:** 12 de agosto de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

O padrão do Spring Boot é rodar o Flyway automaticamente na subida da
aplicação, usando `spring.datasource.*`. Foi o que eu inicialmente pedi ao
`engenheiro-backend`.

Ele apontou um conflito direto com a decisão 0010: a aplicação conecta como
`app_aplicacao`, um papel **sem privilégio de DDL** por desenho. Se o Boot
tentasse migrar no boot com essa credencial, falharia toda vez que houvesse
migration pendente. A única forma de fazer funcionar seria dar DDL ao papel da
aplicação — o que anula a garantia de menor privilégio que a V004 existe para
criar.

O argumento está certo e eu estava errada. Registro a correção.

## Decisão

`spring.flyway.enabled: false`.

Migration roda **exclusivamente** por `make migrate`, via `flyway-maven-plugin`,
com credenciais próprias (`FLYWAY_URL`, `FLYWAY_USUARIO`, `FLYWAY_SENHA`) de um
usuário que tem DDL — em dev, o dono do banco.

Fluxo obrigatório: **`make migrate` antes de subir a aplicação.**
`make preparar` encadeia os dois na primeira execução.

## Alternativas consideradas

- **Flyway no boot com o mesmo usuário da aplicação.** Descartada: exigiria DDL
  no papel da aplicação. Um papel de aplicação com DDL pode alterar as próprias
  policies de RLS — é exatamente o que a V004 impede.
- **Dois DataSources na aplicação**, um para migrar (com DDL) e outro para
  operar. Descartada: coloca credencial privilegiada dentro do processo que
  atende requisição HTTP. Se a aplicação for comprometida, o atacante ganha DDL
  junto. Manter o privilégio de DDL fora do runtime é contenção barata.
- **Migration no entrypoint do container.** Descartada por ora: em VPS com uma
  instância só funcionaria, mas cria corrida se um dia houver duas instâncias
  subindo juntas. O Flyway tem lock, mas o comportamento fica menos óbvio.

## Consequências

- Um passo manual a mais no deploy: migrar, depois subir
- Em compensação, migration vira ato deliberado e não efeito colateral de
  reiniciar um processo — o que é o comportamento desejável em produção
- `ddl-auto: validate` continua ligado: se a aplicação subir contra um schema
  desatualizado, ela **falha no boot** em vez de operar quebrada. Esta é a rede
  que torna o passo manual seguro
- A senha do `app_aplicacao` é definida por `make definir-senha-app`, porque a
  V004 cria o papel sem senha (segredo não entra em migration versionada)
