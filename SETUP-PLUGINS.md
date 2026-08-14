# Setup de plugins — instruções para o Claude Code

Você está lendo isso dentro do Claude Code, no diretório do meu projeto. A tarefa é instalar e configurar as cinco ferramentas abaixo, na ordem dada.

## Regras gerais para você seguir

1. **Antes de instalar cada item, confirme o comando atual.** Os comandos abaixo foram levantados em agosto de 2026 e podem ter mudado. Consulte o repositório oficial de cada um antes de rodar.
2. **Me mostre o que vai rodar antes de rodar.** Não execute instalação nem altere arquivo de configuração sem eu aprovar.
3. **Uma ferramenta por vez.** Instalou, verificou que funciona, aí passa pra próxima. Se algo falhar, para e me avisa em vez de tentar contornar sozinho.
4. **Itens 4 e 5 são opcionais.** Só instale se eu confirmar explicitamente, os dois mexem em configuração de rede e provedor.
5. Ao final, gere um resumo do que ficou instalado, onde e como eu desativo cada coisa se precisar.

---

## 1. Claude Code Setup (plugin oficial da Anthropic)

**O que é:** analisa a base de código do projeto e recomenda de 1 a 2 automações em cada categoria — MCP servers, skills, hooks, subagents e slash commands. É read-only, ele sugere mas não altera arquivo.

**Por que primeiro:** ele vai olhar meu projeto e dizer o que faz sentido antes de eu sair instalando coisa aleatória.

**Instalação:**

```
/plugin marketplace add anthropics/claude-plugins-official
/plugin install claude-code-setup@claude-plugins-official
```

**Depois de instalar, faça isso:** me peça para rodar a análise, ou rode você mesmo com "recommend automations for this project". Me apresente o relatório e **espere eu escolher** o que implementar. Não implemente as recomendações automaticamente.

---

## 2. Task Observer

**O que é:** meta-skill que acompanha as sessões de trabalho e registra padrões — correções que eu faço, fluxos que se repetem, coisas que deram certo. Depois vira proposta de skill nova ou melhoria em skill existente.

**Origem:** repositório `rebelytics/one-skill-to-rule-them-all`, autor Eoghan Henn.

**Instalação:** baixe a pasta `task-observer` do repositório e coloque em `.claude/skills/task-observer/` no projeto, preservando a subpasta `references/`.

**Configuração importante:** a documentação do próprio autor diz que só a descrição da skill não é suficiente para disparar a ativação de forma confiável, porque durante uma tarefa o foco fica na tarefa. Então **adicione no CLAUDE.md do projeto uma instrução explícita** carregando a task-observer no início de toda sessão orientada a tarefa.

**Como eu uso:** ao fechar uma sessão, eu pergunto "alguma observação registrada?" e reviso o que ele juntou.

---

## 3. claude-mem

**O que é:** memória persistente entre sessões. Grava o que aconteceu na sessão via hooks de ciclo de vida, comprime com IA e injeta um resumo no começo da próxima sessão. Armazena em SQLite local.

**Instalação, pelo marketplace:**

```
/plugin marketplace add thedotmack/claude-mem
/plugin install claude-mem@thedotmack
```

**Ou via CLI:** `npx claude-mem install`

**Atenção:** `npm install -g claude-mem` instala só a biblioteca, não registra os hooks nem sobe o worker. Não use essa forma.

**Antes de instalar, me responda uma coisa:** o Claude Code já tem memória automática nativa e o arquivo CLAUDE.md. Verifique qual versão eu estou rodando e me diga se a memória nativa já cobre o que eu preciso. Se cobrir, sugira pular este item.

**Verificação:** depois de instalar, encerre e abra uma sessão nova. O bloco de contexto do claude-mem deve aparecer no topo.

---

## 4. Headroom (opcional, só se eu confirmar)

**O que é:** camada local de compressão de contexto. Fica entre o agente e o modelo e comprime saída de ferramenta, log, JSON e resultado de busca antes de virar token. A compressão é reversível, o original fica salvo local e o modelo pode pedir de volta.

**Instalação:**

```
pip install "headroom-ai[all]"
headroom wrap claude
```

**Antes de instalar, rode `headroom perf`** e me mostre quanto ele economizaria nas cargas típicas do meu projeto. Se a economia for pequena, me diga e não instale.

**Expectativa realista:** os números de 60 a 95 por cento valem para JSON e log. Para agente de código a economia fica em torno de 15 a 20 por cento. Não me venda o número grande.

---

## 5. OmniRoute (opcional, só se eu confirmar)

**O que é:** gateway local que expõe um endpoint só e roteia para vários provedores de IA. Quando a cota de um acaba, cai automaticamente para o próximo.

**Instalação:** instale via npm e suba com `omniroute launch`. O painel fica em `http://localhost:20128`.

**Configuração:** conecte no Claude Code conforme a documentação atual do projeto. Pode haver conflito de ID de modelo quando os provedores `cc/` e `claude/` estão ambos conectados — se aparecer erro de modelo ambíguo, resolva fixando o prefixo.

**Me explique antes de configurar:**

- Quais provedores você vai deixar ativos na cadeia de fallback
- Quais deles usam meus dados para treinar modelo
- Como eu desativo o fallback e volto a usar só o Claude

Este é meu projeto pessoal, não tem código de terceiro nem dado sensível de cliente, mas eu ainda quero saber para onde meu código está indo antes de ligar isso.

---

## Nota de segurança

A própria Anthropic avisa no repositório oficial que ela não controla o que vai dentro dos plugins de terceiros e não garante que funcionem como esperado nem que não mudem depois. Dos cinco acima, só o item 1 é da Anthropic. Os outros quatro são de terceiros.

Antes de instalar cada um dos itens 2 a 5, dê uma olhada no repositório e me diga se viu algo estranho — permissão excessiva, código ofuscado, telemetria não declarada, dependência abandonada.
