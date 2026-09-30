# Como rodar o Radar no seu computador

Caminho padrão do projeto: Postgres no Docker, backend e frontend direto na
máquina. Funciona igual em Windows (Git Bash), macOS e Linux. O launcher
`infra/local-native/launch.ps1` é outro caminho, específico da estação onde o
Codex montou tudo sem Docker (veja `infra/local-native/LOCAL-RADAR.txt`); não
use os dois na mesma base.

## Pré-requisitos

- JDK 21 e Maven
- Node 20 ou superior
- Docker Desktop no ar
- `make` (Windows: `winget install ezwinports.make`, rodando no Git Bash)

## Primeira vez

Na raiz do repositório:

```bash
make preparar          # sobe o Postgres, aplica V001–V017 e define a senha do app
make dados-demo        # cria a loja de demonstração e os 7 perfis de acesso
cd frontend && npm install && cd ..
```

`make preparar` cria `infra/.env` a partir de `infra/.env.exemplo` se ele não
existir. Revise as senhas antes de seguir.

## Todo dia

Dois terminais, na raiz do repositório:

```bash
make dev && make backend             # terminal 1: Postgres + Spring em :8080
cd frontend && npm run dev           # terminal 2: Next em :3000
```

Abra **http://localhost:3000/radar**.

> Use `localhost`, não `127.0.0.1`. O backend só aceita chamadas da origem
> `http://localhost:3000` (CORS, decisão 0025). Aberto por `127.0.0.1`, o
> login falha com "Não foi possível entrar".

## Acessos de demonstração

Senha de todos: `demo1234`. Existem só no banco local.

| E-mail                        | Papel       |
|-------------------------------|-------------|
| dono@demo.plataforma          | DONO        |
| gestor@demo.plataforma        | GESTOR      |
| analista@demo.plataforma      | ANALISTA    |
| financeiro@demo.plataforma    | FINANCEIRO  |
| atendimento@demo.plataforma   | ATENDIMENTO |
| estoque@demo.plataforma       | ESTOQUE     |
| marketing@demo.plataforma     | MARKETING   |

O núcleo do Radar (`radar_*`) começa vazio. Na Visão geral, clique em
**Carregar catálogo de exemplo** para criar quatro produtos fictícios.

## Recomeçar do zero

`make dados-demo` de novo apaga a loja de demonstração, incluindo tudo o que
foi criado no Radar, e recria os dados. Não toca em outros tenants e recusa
rodar se encontrar um tenant que não seja de demonstração.
