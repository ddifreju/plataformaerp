# Como rodar o Claude Code sem travar pedindo permissão

## Passo 1 — Mova o kit para a raiz

O Claude Code só carrega `.claude/agents/` se estiver na raiz do diretório
onde a sessão foi aberta.

```bash
# se o kit está em kit-inicio/, mova o conteúdo para a raiz
mv kit-inicio/* .
mv kit-inicio/.claude .
rmdir kit-inicio
```

Ou simplesmente abra o Claude Code dentro da pasta `kit-inicio/`.

Confirme com `/agents` — devem aparecer os 11.

## Passo 2 — Configure o modo automático (nível de usuário)

**Atenção:** `defaultMode` precisa estar em `~/.claude/settings.json`
(nível de usuário), NÃO no `.claude/settings.json` do projeto. No projeto
ele é ignorado. Isso é uma pegadinha documentada.

Edite ou crie `~/.claude/settings.json`:

```json
{
  "permissions": {
    "defaultMode": "auto"
  }
}
```

O modo `auto` usa um classificador que avalia cada ação e aprova as
rotineiras. É o modo recomendado para tarefas longas — você para de ver
prompts de rotina, mas mantém uma checagem no meio.

## Passo 3 — As allow rules do projeto já estão prontas

O arquivo `.claude/settings.json` do kit já vem com lista de comandos
pré-aprovados (maven, npm, docker, git, psql) e lista de negados
(`rm -rf /`, `sudo`, `git push`, leitura de `.env`).

Isso cobre praticamente tudo que o desenvolvimento normal precisa.

## Passo 4 — Se quiser autonomia total (só em ambiente isolado)

O modo `bypassPermissions` pula todos os prompts. **Use apenas em container
ou VM descartável** — nesse modo qualquer comando roda sem confirmação.

```bash
claude --dangerously-skip-permissions
```

Ou via flag de modo:

```bash
claude --permission-mode bypassPermissions
```

Docker é a forma segura:

```bash
docker run -it --rm -v $(pwd):/app -w /app node:22 bash
# instala o claude code dentro e roda com bypass
```

**Nota de segurança:** houve uma vulnerabilidade (CVE-2026-33068, corrigida
na versão 2.1.53) em que um repositório malicioso podia forçar
`bypassPermissions` pelo `.claude/settings.json` commitado. Mantenha o
Claude Code atualizado.

Alguns prompts continuam aparecendo mesmo em bypass: regras `ask` explícitas,
ferramentas MCP marcadas como `requiresUserInteraction`, e `rm -rf /` ou
`rm -rf ~` (circuit breaker contra erro do modelo).

## Passo 5 — Execução contínua

Para tarefas longas sem você presente:

```bash
# headless, uma tarefa
claude --dangerously-skip-permissions -p "execute as próximas 3 tarefas do ESTADO.md"

# loop dentro da sessão (a cada 30 min)
/loop 30m continue a próxima tarefa de docs/ESTADO.md

# agendado
/schedule 0 9 * * * revise o ESTADO.md e execute a próxima tarefa
```

## Passo 6 — Compactação em sessões longas

Em sessões de várias horas, o contexto degrada e a qualidade do julgamento
cai. Sem prompts servindo de checkpoint, um julgamento ruim executa na hora.

Rode `/compact` periodicamente em execuções autônomas longas.

## Guard rails recomendados

Como o gerente decide sozinho, os freios ficam no ambiente:

1. **Commit frequente.** Git é o desfazer. Configure o gerente para commitar
   a cada tarefa concluída
2. **`git push` está na lista de negados.** Nada sai da máquina sem você
3. **Branch separada.** Rode autônomo em `dev`, faça merge quando revisar
4. **Revise `docs/decisoes/` uma vez por dia.** É o log do que ele decidiu
