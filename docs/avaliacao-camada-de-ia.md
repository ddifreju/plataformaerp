# Avaliação da camada de IA (`com.plataforma.pergunta`)

Documento de uso da suíte criada na tarefa 25. Contexto e contrato completo:
decisão [0030](decisoes/0030-arquitetura-da-camada-de-ia.md) e regra 4 do
`CLAUDE.md` ("separe determinístico de não-determinístico"). Este arquivo
não é relatório de sessão - é para consultar quando a suíte falhar ou
quando alguém (inclusive você, daqui a 3 meses) esquecer por que os
números são os que são.

## O que existe, e por quê

| Classe de teste | O que prova | Como prova |
|---|---|---|
| `InterpretacaoAdversariaTest` | Determinístico (regra 4): validação de parâmetro, resolução de código, isolamento de tenant, recusa fora do catálogo seguram contra saída de LLM **de propósito errada** | **Asserção dura** - RECUSA, ESCLARECIMENTO ou RESPOSTA com dado do tenant certo, nunca outra coisa |
| `AvaliacaoDeInterpretacaoTest` | Não-determinístico (regra 4): qualidade da interpretação de `ModeloHeuristico` contra pergunta real de lojista | **Avaliação com limiar**, nunca asserção por caso |
| `ModeloGravado` (dublê, não é teste) | Fixture pergunta→intenção gravada, usada pelo primeiro item acima | - |

A separação importa: **nunca** existe um `assertEquals` cravado num caso
individual de `AvaliacaoDeInterpretacaoTest` - isso violaria a regra 4 (a
saída do "modelo" ali é o próprio `ModeloHeuristico` interpretando texto
livre, comportamento não-determinístico do ponto de vista do catálogo,
mesmo sendo um algoritmo determinístico por dentro). Os casos individuais
alimentam três métricas agregadas, e só as métricas têm limiar.

## `ModeloGravado`

`backend/src/test/java/com/plataforma/pergunta/ModeloGravado.java` - dublê
de `PortaModeloLinguagem` que devolve `IntencaoDetectada` gravada em
fixture JSON (`backend/src/test/resources/fixtures/pergunta/*.json`),
indexada pela pergunta normalizada (minúsculo, sem acento, espaço
colapsado). Usa `com.plataforma.suporte.LeitorDeFixture` para ler o
classpath - nenhuma responsabilidade nova precisou entrar lá, porque ele só
lê bytes; quem entende o formato pergunta→intenção é o próprio dublê.

**Pergunta sem fixture gravada lança `IllegalStateException` imediatamente.**
Nunca um fallback silencioso. Se um teste que usa `ModeloGravado` quebrar
com essa exceção, o problema é uma fixture faltando, não um bug do sistema
sob teste - grave a entrada em
`backend/src/test/resources/fixtures/pergunta/adversario.json` (ou outro
arquivo do mesmo diretório) e rode de novo.

`codigoBruto` nunca é JSON `null` na fixture: o record `IntencaoDetectada`
proíbe `codigoBruto` nulo por contrato. Uma fixture que precise simular "o
modelo não devolveu código nenhum" usa `""`. O caso de código **literalmente
nulo** é coberto direto contra `CatalogoDePerguntas.resolver(null)`
(`CatalogoDePerguntasTest#rejeitaCodigoNulo`), porque não existe como
nenhuma implementação de `PortaModeloLinguagem` produzir isso.

## Os limiares de hoje, e por quê

Medido contra o corpus de 51 perguntas em
`AvaliacaoDeInterpretacaoTest#CORPUS`, rodando `ModeloHeuristico` de
verdade (não é o `ModeloGravado` - a avaliação de qualidade usa a
implementação real):

| Métrica | Medido | Limiar no teste | Direção |
|---|---|---|---|
| Taxa de intenção correta | 82,4% (42/51) | **≥ 80%** | mínimo |
| Taxa de parâmetro correto (dado intenção certa) | 100% (12/12) | **≥ 90%** | mínimo |
| Taxa de erro perigoso (confiança alta + intenção errada) | 3,9% (2/51) | **≤ 6%** | máximo |

Os limiares ficam **logo abaixo/acima** do medido, não confortavelmente
longe - o objetivo é que qualquer regressão real derrube o teste, não dar
folga. Isso funciona porque `ModeloHeuristico` é 100% determinístico (sem
rede, sem amostragem): a mesma pergunta sempre produz a mesma saída, então
não há ruído para absorver com margem larga.

**Estes números são um PISO A SUBIR quando `ModeloAnthropic` existir, nunca
uma meta atingida.** `ModeloHeuristico` é casamento de palavra-chave sem
sinônimo nem plural/singular (nenhum *stemming* - decisão 0030,
"conservador por construção"): ele erra em gíria que usa um verbo ou plural
diferente do catálogo por design, não por bug. Baixar os limiares para
"passar" seria esconder justamente o que esta suíte existe para expor.

## Como rodar

```bash
cd backend
./mvnw -Dtest=com.plataforma.pergunta.AvaliacaoDeInterpretacaoTest test
./mvnw -Dtest=com.plataforma.pergunta.InterpretacaoAdversariaTest test
```

O primeiro comando imprime uma tabela no console (pergunta, esperado,
obtido, confiança, ok?, perigoso?) e o resumo das três taxas - é a forma
mais rápida de ver onde a heurística erra sem ler código. `make test` roda
os dois como parte da suíte completa.

## Como interpretar uma queda

- **Taxa de intenção correta caiu** - alguma mudança no catálogo
  (`CatalogoDePerguntas`) ou na heurística (`ModeloHeuristico`) piorou o
  casamento de palavra-chave. Olhe a tabela impressa: as linhas `ERRO`
  mostram exatamente qual pergunta mudou de resultado.
- **Taxa de parâmetro correto caiu** - a extração de `canal` ou
  `periodoRelativo` regrediu (ex.: uma expressão de período saiu do
  conjunto fechado `EXPRESSOES_DE_PERIODO`, ou o casamento de nome de canal
  mudou). Confira `ValidadorDeParametrosTest` e `ModeloHeuristicoTest`
  também - eles têm cobertura unitária mais fina do mesmo mecanismo.
- **Taxa de erro perigoso subiu** - o pior caso: alguma pergunta que antes
  era recusada (ou respondida certo) agora é respondida **convicta e
  errada**. Trate como prioridade alta - é exatamente o cenário que a
  regra 5 do `CLAUDE.md` ("nunca invente dado") existe para evitar na
  camada de interpretação.
- **`InterpretacaoAdversariaTest` falhou** - isto é sempre prioridade
  máxima: uma trava determinística (isolamento de tenant, validação de
  parâmetro, resolução de código) parou de segurar contra uma entrada que
  já sabíamos ser hostil. Nunca ajuste o teste para passar sem entender a
  causa - releia o Javadoc do cenário que falhou primeiro.

## Onde a heurística erra mais (achado da tarefa 25, útil para a tarefa 26 - a tela)

A maior fonte de erro **não-perigoso** (recusa quando deveria responder) é
gíria que usa um verbo, plural ou sinônimo que não está em nenhum
`exemploDePergunta` do catálogo: "cadastrado" em vez de "cadastrados",
"em aberto" em vez dos exemplos existentes, "sem custo" sozinho sem outra
palavra do vocabulário de `FILA_DE_PENDENCIAS`. A correção de baixo risco
para isso é **sempre** adicionar mais `exemplosDePergunta` em
`CatalogoDePerguntas` (o mecanismo de extensão que `ModeloHeuristico` já
foi desenhado para usar - ver o Javadoc dele), nunca mudar o algoritmo de
pontuação.

A maior fonte de erro **perigoso** (confiança alta, intenção errada) é a
sobreposição de vocabulário entre `MARGEM_DO_PERIODO` e
`LACUNAS_DA_MARGEM`: as duas intenções compartilham "margem", nome de
canal, e frases de período como "mês atual" nos próprios exemplos do
catálogo. Hoje o desempate entre elas depende, em parte, de a pergunta
conter a palavra "quanto" (presente nos exemplos de `MARGEM_DO_PERIODO`,
ausente dos de `LACUNAS_DA_MARGEM`) - o que **também** é a causa de um
falso positivo medido: "quanto custa o frete dos correios", totalmente
fora do catálogo, bate confiança alta em `MARGEM_DO_PERIODO` só por causa
de "quanto" (ver `ModeloHeuristico`, comentário acima de
`PALAVRAS_IGNORADAS`, e `InterpretacaoAdversariaTest`/`AvaliacaoDeInterpretacaoTest`
para os casos que documentam isso).

Uma tentativa de corrigir isso ignorando "quanto" como palavra de ligação
foi revertida nesta tarefa: ela trocou o falso positivo ocasional por mais
empates (recusa) entre `MARGEM_DO_PERIODO` e `LACUNAS_DA_MARGEM` em
perguntas realistas, porque removeu o único desempate que hoje existe entre
elas. Não é uma correção isolada - é uma escolha de design sobre como
desambiguar duas intenções com vocabulário quase idêntico, e fica registrada
aqui como gap conhecido em vez de decidida sem revisão.

**Para a tarefa 26 (a tela):** isto significa que a UI não deveria tratar
"o sistema respondeu com confiança" como sinônimo de "a resposta é sobre o
que a lojista perguntou" quando a pergunta menciona margem/canal/período -
vale considerar mostrar, junto da resposta de `MARGEM_DO_PERIODO` ou
`LACUNAS_DA_MARGEM`, um atalho visível para a outra ("Você quis perguntar
sobre o que falta calcular, em vez do resultado?") - mais barato que
resolver a ambiguidade no modelo, e consistente com "recusa/pedido de
confirmação é caminho de primeira classe" da decisão 0030.
