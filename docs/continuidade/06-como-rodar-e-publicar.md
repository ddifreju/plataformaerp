# Como rodar e publicar

## Rodar no computador

O passo a passo completo está em `docs/radar/COMO-RODAR-LOCAL.md`: Docker para
o Postgres, backend e frontend direto na máquina. Resumo:

```bash
make preparar          # Postgres + migrations + senha do app (cria infra/.env)
make dados-demo        # loja de demonstração e os 7 perfis
cd frontend && npm install && cd ..
make dev && make backend          # terminal 1
cd frontend && npm run dev        # terminal 2  → http://localhost:3000/radar
```

- Use **`localhost`**, não `127.0.0.1`, por causa do CORS.
- Windows: o Docker Desktop já travou por causa de sockets órfãos. A cura
  está em `docs/CONTEXTO-HANDOFF.md`.

Testes:
```bash
make test                                  # suíte completa (precisa do Docker)
cd backend && mvn test -Dtest=RadarProdutosTest   # uma classe
cd frontend && npx tsc --noEmit -p . && npx eslint src/app/radar/
```

## Publicar (o fluxo que a Jéssica espera)

1. Trabalhe numa branch e faça commit em português, no imperativo.
2. **Migration nova?** Aplique no **Supabase antes do merge**, senão o
   backend novo sobe sem a tabela. Três caminhos:
   - pelo MCP do Supabase (`apply_migration`), se estiver conectado;
   - colando o SQL no **SQL Editor** do painel (projeto
     `lovciyurbpgzeaijvldk`);
   - com `psql "$CONNECTION_STRING" -f backend/src/main/resources/db/migration/Vnnn__x.sql`.
   - A tabela de histórico do Flyway no Supabase não é usada. As migrations
     foram aplicadas uma a uma; confira com `\d nome_da_tabela` antes de
     reaplicar.
3. `git push`, abra o PR (`gh pr create`) e faça o merge (`gh pr merge
   --squash`).
4. O Render e a Vercel publicam sozinhos a partir da `main`. O backend leva
   de 5 a 10 minutos.
5. **Confira que está no ar** antes de avisar a Jéssica:

```bash
U=https://plataformaerp.vercel.app; C=/tmp/cj.txt
# backend: login + procurar um campo novo na resposta
curl -s -c $C -b $C -H 'Content-Type: application/json' \
  -d '{"email":"dono@demo.plataforma","senha":"demo1234"}' $U/api/login -o /dev/null
curl -s -b $C $U/api/radar | grep -o '"configuracoes"'
# frontend: procurar um texto novo nos chunks
for c in $(curl -s $U/radar | grep -o '/_next/static/chunks/[^"]*\.js' | sort -u); do
  curl -s "$U$c" | grep -q "TEXTO NOVO" && echo NO_AR && break; done
```

O login é em `POST /api/login` com `{email, senha}`.

## Local × produção
- O banco local pode estar com o histórico do Flyway diferente (já deu
  "checksum mismatch" na V021). Nesse caso, aplique a migration nova direto
  com `psql`.
- A produção roda com o plano gratuito do Render: a primeira chamada depois
  de dormir leva uns 3 minutos.
