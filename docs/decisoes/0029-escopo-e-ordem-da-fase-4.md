# 0029 — Escopo e ordem da Fase 4

**Data:** 14 de setembro de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL] — é uma fila de trabalho, reordenável a
qualquer momento sem custo de código.

## Contexto

O roadmap inicial (tarefas 1–20, Fases 0 a 3) terminou e foi validado de ponta
a ponta em dev: suíte verde contra Postgres real, três telas vistas no
navegador com o seed. A fila está vazia.

O que continua pendente é **credencial externa** (Mercado Livre, Bling, LLM,
WhatsApp) e **decisão de negócio** (nome, nicho, preço, regime tributário).
Nenhuma das duas bloqueia construção — a regra permanente do projeto é
construir contra mock. Bloqueia validação.

Portanto a pergunta da Fase 4 é: **qual trabalho técnico gera mais valor sem
depender de credencial?**

## Decisão

A Fase 4 tem três blocos, nesta ordem:

### Bloco A — Camada de IA sobre dados operacionais (tarefas 21–26)

Primeiro, e é o maior. Razões:

1. **É a promessa do pitch e não existe uma linha dela.** "Responde perguntas
   sobre a operação" é o que diferencia a plataforma de um integrador comum.
   Tudo o que foi construído até aqui (modelo canônico, motor de margem,
   trilha de auditoria) existe para sustentar essa camada. Ela é a razão de
   ser do resto.
2. **A parte difícil não precisa de chave de LLM.** Pela arquitetura da
   decisão 0030, o modelo de linguagem só classifica intenção e extrai
   parâmetro; **nenhum número passa pela geração do modelo**. Isso significa
   que a maior parte do bloco é determinística e 100% testável hoje: catálogo
   de perguntas, execução de consulta auditada, montagem da resposta, recusa
   honesta, isolamento de tenant. O que precisa de chave é um adaptador na
   borda — trocável.
3. **É o bloco de maior risco de projeto.** Se a arquitetura honesta (regras
   3 e 5) não couber, é melhor descobrir agora e não depois de vender.

### Bloco B — Preparo de produção (tarefas 27–30)

Depois, porque é mecânico e de risco baixo — uma sessão futura executa com
segurança mesmo com pouco contexto. Cobre uma pendência conhecida e real: o
rewrite do Next em dev **esconde** a configuração de CORS e os atributos do
cookie no fio. A condição 2 da decisão 0025 (mesma origem atrás de proxy
reverso) é requisito de segurança e nunca foi exercitada.

Nada aqui é deploy de verdade: é a preparação que faz o deploy virar um
comando quando a VPS existir. Deploy em si é ação irreversível com custo real
— é da fundadora.

### Bloco C — Escopo de canal declarado (tarefas 31–32)

Por último, e **não é** o mecanismo de reconciliação que o candidato (c)
descrevia. Ver a seção seguinte.

## O que foi descartado, e por quê

### Reconciliação ML × Bling por casamento de pares — DESCARTADA nesta fase

O candidato (c) pedia o mecanismo de casamento assistido entre pedidos do
Mercado Livre e do Bling. Descartado por contradizer a decisão 0017, que é
explícita: casar pedidos entre fontes só se constrói "quando existir uma chave
confirmada contra dado real — não contra uma suposição".

Nada mudou desde então. As fixtures continuam sendo hipótese; a documentação
oficial das duas APIs respondeu 403/404. Construir hoje a heurística de
candidatos significa **calibrar um casamento contra dado que eu inventei**.
A tela de confirmação herdaria a calibragem: a lojista confirmaria ou recusaria
pares gerados por uma regra que nunca viu um `numeroLoja` verdadeiro.

O argumento "mas ela confirma, então não funde às cegas" só cobre o falso
positivo. Não cobre o falso negativo, que é o pior dos dois: o par que a
heurística **não** propõe nunca chega à tela, e a venda segue contada duas
vezes — em silêncio, que é exatamente o que a regra 5 proíbe.

Custo de esperar: baixo. A decisão 0021 já impede o dano — o endpoint de
margem exige canal explícito e o backend nunca soma canais potencialmente
sobrepostos. O sistema hoje é *limitado*, não *errado*.

**Gatilho para retomar:** o primeiro pedido real de uma conta de Mercado Livre
com Bling ligado. Segundo a própria 0017, isso resolve a dúvida em minutos.

### O substituto: escopo de canal declarado pela lojista (bloco C)

A 0017 registrou uma terceira alternativa e a chamou de "provavelmente a
resposta certa": deixar o lojista declarar a fonte-mestra de pedido por canal.

Essa alternativa **não precisa de dado real**, porque não adivinha nada — a
lojista declara. É configuração, não heurística. Ela destrava a limitação real
de hoje (não dá para perguntar "quanto sobrou no mês", só "quanto sobrou no
mês *no ML Clássico*") sem inventar casamento nenhum, e é pré-requisito
natural para a camada de IA responder qualquer pergunta que não nomeie um
canal.

Fica em último porque é o menor dos três e depende de saber quais perguntas a
camada de IA vai fazer ao modelo de dados.

### Dívidas registradas — não entram como bloco próprio

A única dívida aberta no ESTADO.md é o `ConsultaAuditada` sem
`@GeneratedValue`, que custa um `SELECT` a mais por escrita. O bloco A vai
multiplicar a frequência dessa escrita (toda pergunta grava uma consulta
auditada), então ela deixa de ser hipotética. Fica **dentro** da tarefa 23,
onde o gargalo aparece, em vez de virar tarefa solta — e só se medir gargalo.

### Credencial e decisão de negócio — não entram

Continuam em `docs/PENDENCIAS.md`, sem novidade desde 14/08. Nada da Fase 4
depende delas.

## Consequências

- A fila 21–32 vai para `docs/ESTADO.md` e é executada em ordem
- A camada de IA nasce com o rótulo de confiança e a trilha de auditoria que o
  resto do sistema já tem — não como um anexo que responde por fora
- A reconciliação vira a primeira coisa a fazer quando a credencial do Mercado
  Livre chegar, com gatilho escrito
- Se o bloco A revelar que a arquitetura honesta não responde perguntas úteis
  o bastante, isso é informação de produto e vira pendência de negócio, não
  ajuste silencioso de escopo
