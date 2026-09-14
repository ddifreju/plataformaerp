# Contexto de handoff — para a próxima IA que trabalhar neste projeto

Escrito em 14/08/2026 pela sessão de Claude Code que executou as Fases 0 a 3.
Isto complementa (não substitui) a ordem de leitura do `LEIA-PRIMEIRO.md`.
Leia primeiro os documentos do projeto; este arquivo traz o que NÃO está neles.

## Como a Juliana trabalha (aprendido em sessão)

- Solo, ~20h/semana, gargalo é tempo. Ela pediu explicitamente, em 13/08:
  **não pedir confirmação nem compartilhar sugestões com ela** — decidir,
  executar e passar recomendações ao agente `gerente-projeto`, que resolve.
  Isso se soma ao modo autônomo do `LEIA-PRIMEIRO.md`.
- Exceções que continuam sendo dela: credenciais, decisões de negócio (nome,
  nicho, preço), ações irreversíveis com custo real, e **enviar código dela a
  provedores de IA externos** (ela quer saber para onde o código vai).
- **O push é dela.** A IA commita localmente; ela publica. Não tente `git push`.
- Ela responde rápido e curto ("y", "pronto", "ja instalei"). Interprete pelo
  contexto e siga; não fique repetindo perguntas.
- Comunicação em português; commits em português no imperativo.

## Estado do trabalho (resumo; detalhes no ESTADO.md)

- Roadmap inicial (20 tarefas) completo e validado de ponta a ponta em dev:
  suíte verde contra Postgres real, três telas vistas no navegador com o seed.
- Fila nova não existe. Candidatos: credencial ML → primeiro payload real;
  staging/VPS para exercitar CORS; decisões de negócio pendentes.
- Logins do ambiente de demonstração: `dono@demo.plataforma`,
  `gestor@demo.plataforma`, `analista@demo.plataforma` — senha `demo1234`
  (só existem no banco local após `make dados-demo`).

## Setup de Claude Code (o que migra e o que não)

- **Migra com o repositório:** os 11 agentes (`.claude/agents/`), a skill
  `task-observer` (`.claude/skills/`), o `settings.json`, o `launch.json`
  do preview e a instrução de ativação no `CLAUDE.md`.
- **NÃO migra (escopo de usuário/máquina):** o plugin `claude-code-setup`
  (oficial da Anthropic). Para reinstalar na máquina nova:
  `claude plugin marketplace add anthropics/claude-plugins-official` e
  `claude plugin install claude-code-setup@claude-plugins-official`.
- **Pendente:** a análise do claude-code-setup ("recommend automations for
  this project") nunca foi rodada. Quando rodar, as recomendações vão para o
  `gerente-projeto` decidir — instrução literal da Juliana.
- **Decidido e registrado:** claude-mem foi pulado (memória nativa cobre);
  Headroom e OmniRoute aguardam confirmação explícita dela (mexem em rede e
  provedor). Detalhes em `SETUP-PLUGINS-RESULTADO.md` na raiz.
- A memória nativa do Claude Code e o log do task-observer da máquina antiga
  não migram. A única observação registrada lá está resumida abaixo.

## Lições de ambiente da máquina antiga (Windows 11)

Podem não se aplicar à máquina nova, mas o padrão de diagnóstico vale:

1. **Docker Desktop não subia**: sockets Unix órfãos em
   `AppData\Local\Docker\run` e `AppData\Local\docker-secrets-engine` que o
   Windows não conseguia apagar (erro 1920). Correção: parar o Docker,
   RENOMEAR as pastas (apagar os arquivos é impossível), relançar. Limpar
   todas de uma vez, senão cada tentativa deixa sockets novos para trás.
   **CONFIRMADO NA MÁQUINA NOVA (14/09/2026):** aconteceu de novo, com o
   mesmo sintoma (o processo sobe e morre em silêncio, sem janela de erro) e
   a mesma cura. Duas notas que economizam tempo: o executável desta máquina
   está em `AppData\Local\Programs\DockerDesktop\Docker Desktop.exe`
   (instalação por usuário), **não** em `Program Files`; e `docker version`
   responde normalmente com o cliente mesmo sem daemon — quem diz a verdade
   é `docker info`.
2. **Tomcat não subia** ("Unable to establish loopback connection"):
   duas causas empilhadas. (a) Reserva de portas do winnat/Hyper-V —
   corrigido pela Juliana com `net stop winnat && net start winnat` +
   `netsh int ipv4 set dynamic tcp start=49152 num=16384`. (b) O JDK 16+
   cria o pipe do Selector com socket AF_UNIX no diretório `TMP`; shells
   que herdam TMP na grafia curta 8.3 (`JULIAN~1`) quebram o connect com
   EINVAL, porque o driver afunix.sys não resolve caminho 8.3. Correção:
   exportar `TMP`/`TEMP` num caminho sem forma 8.3 (ex.: `C:\Temp`) para o
   processo Java. PowerShell da Juliana não sofre disso; Git Bash sim.
   **CORREÇÃO DE ESCOPO (14/09/2026):** esse `TMP` vale para **subir o
   backend**, e NÃO para rodar o Maven. Exportar `TMP=C:\Temp` antes de
   `./mvnw test` mata o maven-surefire 3.2.5 com
   `ExceptionInInitializerError` antes de executar teste nenhum. Rode a
   suíte sem tocar em `TMP`.
   Princípio: mesmo binário funcionando num shell e falhando noutro → o
   diff é o ambiente herdado; reduza ao caso mínimo (`Selector.open()`)
   antes de culpar firewall/antivírus.
3. **Testcontainers × Docker novo**: se o engine for muito recente e os
   testes falharem com 400 na negociação de API, o caminho é bump do BOM
   do Testcontainers (decisão 0027).

## Padrões que deram certo nesta colaboração

- `gerente-projeto` como ponto de entrada de TODO trabalho de produto, com
  missões objetivas e "não pergunte — decida e registre" no final. Ele
  decompõe, delega aos especialistas, passa pelos revisores e atualiza
  `ESTADO.md`/`decisoes/`. Funcionou por 4 fases inteiras.
- Validação por execução pega o que revisão por leitura não pega: os bugs
  mais graves (login sem sessão, evento em erro nunca reprocessado, cliente
  recorrente rejeitado) só apareceram rodando de verdade. Priorize rodar.
- O `ESTADO.md` é a memória real entre sessões. Atualize a cada tarefa.
