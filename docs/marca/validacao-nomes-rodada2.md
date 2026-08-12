# Validação de nomes — rodada 2

**Data:** 2026-08-11
**Executado por:** agente `pesquisador-marca`
**Escopo:** validação de **Navega** (candidato da fundadora) + segunda rodada de
candidatos gerados
**Contexto:** a rodada 1 (ver [`validacao-nomes.md`](validacao-nomes.md)) não
produziu nenhum verde, e a Juliana não aprovou nenhum dos três amarelos
(Régua, Sobra, Norte).

---

## Metodologia

Mesmo processo de 6 etapas da rodada 1, com os mesmos métodos que funcionaram:

- **Domínios `.com.br`** — API do registro.br (`/v2/ajax/avail/raw/`), semântica
  calibrada com controles (`status: 0` = livre, `status: 2` = registrado)
- **Domínios `.com`** — RDAP da Verisign (404 = livre, 200 = registrado)
- **INPI** — sessão anônima no pePI (`LoginController?action=login`) e submissão
  do formulário `F_Pesquisa_Classe_Basica`. Buscas **exatas na classe 42**,
  **exatas em todas as classes** e **radicais** onde relevante. A base declarou
  dados atualizados até **11/08/2026**
- **Redes** — Instagram e LinkedIn via requisição direta

**Nota de leitura sobre classes do INPI:** processos anteriores a ~2000 usam a
classificação nacional antiga (formato `40 : 15`); os modernos usam Nice
(`NCL(12) 42`). Ao avaliar conflito na classe de software, o que importa são as
linhas **`NCL(...) 42`**. Registros antigos em `40 : xx` não bloqueiam a classe
42 moderna, e este relatório os trata como ruído histórico.

### Limitações desta rodada

| Item | Situação | Como verificar manualmente |
|---|---|---|
| **Instagram `@quilate`** | Resposta veio só com imagens base64 — inconclusivo | Abrir logada |
| **Redes de Cerne, Esteio, Bitola** | Não consultadas (candidatos eliminados antes por outros critérios) | Só se forem reconsiderados |
| **Titularidade dos domínios** | Sabemos que estão registrados, não quem detém | WHOIS logado no registro.br |
| **Parecer de registrabilidade** | Busca no INPI ≠ parecer jurídico. O próprio INPI adverte que o exame de mérito faz nova busca | Advogado de propriedade industrial |
| **Marcas foneticamente próximas** | A busca cobre elemento nominal exato e radical, não homófonos de outra grafia | Busca de anterioridade profissional |

---

# PARTE 1 — Navega

Candidato da fundadora. Tratado com o mesmo rigor dos demais, e com atenção
especial aos pontos levantados: Navegg, associação com navegador, e variações do
radical "naveg".

## Etapa 1 — Busca web: CONFLITO VERIFICADO

**Conflito direto de categoria:**

> **Navega Consultoria** (`navegaconsultoria.com.br`) — o "Sistema Navega" é um
> aplicativo Android/iOS para **acompanhamento diário de centenas de indicadores
> e contas patrimoniais** (inadimplência, associados ativos, depósitos,
> operações de crédito, carteira de seguros e consórcio), voltado a cooperativas
> de crédito, com dados originados do Banco Central.

Isto é um painel brasileiro de indicadores financeiros chamado Navega. Não é
adjacente — é a mesma categoria de produto (dashboard de números financeiros de
uma operação), com o mesmo nome, no mesmo país.

**Colisão fonética (o ponto levantado sobre a Navegg):**

> **Navegg** — adtech brasileira fundada em 2008 em Curitiba, com escritório em
> SP. Base de mais de 400 milhões de perfis, clientes como Globo, Fiat, NET,
> Estadão e Abril. **Adquirida pela Dentsu Aegis Network em dezembro de 2015.**

A colisão é mais séria do que "parecido": **"Navegg" foi construída a partir de
"navega"** — é a mesma palavra com consoante dobrada. No mercado digital
brasileiro (justamente onde estão os lojistas de e-commerce e as agências que os
atendem), Navegg é um nome conhecido há mais de 15 anos. Em conversa falada,
"Navega" e "Navegg" são **indistinguíveis**. O teste de telefone que o nome passa
com folga é exatamente o que torna essa colisão perigosa: ninguém vai ouvir a
diferença.

**Outros no espaço:** **Nave** (`nave.app.br`) — plataforma de gestão comercial e
pós-venda; **Navegantes** (`navegantes.digital`); **Navee** (AGV Gestão) —
inteligência comercial e geográfica.

## Etapa 2 — Domínio: INDISPONÍVEL (verificado)

| Domínio | Status |
|---|---|
| `navega.com.br` | **Registrado** (registro.br `status: 2`) |
| `navega.com` | **Registrado** (RDAP 200) |

## Etapa 3 — INPI: CONFLITO VERIFICADO NA CLASSE 42

**Classe 42, busca exata:**

| Processo | Marca | Situação | Titular | Classe |
|---|---|---|---|---|
| 931307759 | NAVEGA | **Registro de marca em vigor** | **NAVEGA EMPREENDIMENTOS LTDA** | NCL(12) **42** |

**Todas as classes, busca exata — 9 processos, dos quais 5 registros em vigor:**

| Processo | Marca | Situação | Titular | Classe |
|---|---|---|---|---|
| 931307759 | NAVEGA | **Em vigor** | NAVEGA EMPREENDIMENTOS LTDA | NCL(12) **42** |
| 931307392 | NAVEGA | **Em vigor** | NAVEGA EMPREENDIMENTOS LTDA | NCL(12) **36** |
| 931307570 | NAVEGA | **Em vigor** | NAVEGA EMPREENDIMENTOS LTDA | NCL(12) 37 |
| 909821330 | NAVEGA | **Em vigor** | CROMOSFERA SERVIÇOS DE DEPTO PESSOAL E FINANCEIRO | NCL(10) **36** |
| 928777766 | NAVEGA | **Em vigor** | NAVEGA ISOLAMENTOS TÉRMICOS LTDA | NCL(11) 17 |
| 918583225 | Navega | **Em vigor** | FELIPE BARTH DE OLIVEIRA | NCL(11) 25 |
| 938418947 | NAVEGA | Aguardando exame | NAVEGA CHARTER E TURISMO NAUTICO | NCL(12) 39 |
| 943637660 | NAVEGA | Aguardando exame | BABA VACARO CONSULTORIA EM DESIGN | — |
| 825755417 | NAVEGA | Arquivado | SERPRO | NCL(8) 35 |

**Leitura:** a NAVEGA EMPREENDIMENTOS detém uma **família de marcas** cobrindo as
classes 36 (serviços financeiros), 37 e **42 (software)** — exatamente o conjunto
que uma plataforma de gestão financeira precisaria. Some-se a isso um segundo
registro em vigor na classe 36 por uma empresa de serviços financeiros. As duas
classes centrais para este produto estão ocupadas por registros vigentes.

**Busca radical "NAVEG" na classe 42:** retornou apenas compostos históricos
(NAVEGADOR, NAVEGAÇÃO OCEANIC, NAVEGANTES, BORIS NAVEGAÇÃO), quase todos extintos
ou arquivados — sem conflito adicional, mas confirma que o radical é
intensamente usado no universo náutico/marítimo.

## Etapa 4 — Redes: não verificadas individualmente

Não foram consultadas separadamente, dado que o conflito de classe 42 já é
determinante. Marcar como **não verificado**.

## Etapa 5 — Teste de telefone: EXCELENTE

Três sílabas (na-ve-ga), palavra corrente do português, grafia sem ambiguidade.
Ninguém precisa soletrar. **É o ponto mais forte do nome** — e, paradoxalmente,
o que agrava a colisão com Navegg, já que a diferença é inaudível.

## Etapa 6 — Ambiguidade: MUITO ALTA

Dois problemas somados:

1. **"Navega" é forma verbal de altíssima frequência.** Mesmo problema estrutural
   de "Sobra" na rodada 1 — impossível dominar a busca.
2. **A associação dominante de "navegar" no contexto digital é navegador de
   internet.** Isto é diluição, não ajuda: o campo semântico já pertence a
   browser, navegação de site, dados de navegação — e a Navegg construiu uma
   marca de 15 anos justamente sobre *dados de navegação*. Um produto de gestão
   financeira chamado Navega seria lido, na primeira audição por alguém do
   mercado digital, como ferramenta de analytics de navegação.

## O que o nome tem de bom (registro honesto)

Seria desonesto listar só os problemas. Navega tem méritos reais:

- **Metáfora coerente e digna.** Conduzir uma operação por águas que o lojista
  não enxerga é uma imagem verdadeira do que a plataforma faz — e é uma metáfora
  de *condução*, não de mágica, alinhada ao posicionamento
- **É verbo, não substantivo.** Dá movimento e agência ao cliente ("você navega"),
  o que é raro e bom: a maioria dos candidatos das duas rodadas são substantivos
  estáticos
- **Sonoridade aberta e calorosa**, três sílabas fáceis, sem encontro consonantal
  difícil. Teste de telefone impecável
- **Sem conotação negativa** — ao contrário de Sobra ("resto") ou Bitola
  ("bitolado")

O nome não é ruim. O problema não é de gosto nem de conceito — é que o espaço
legal e de mercado já está ocupado.

## 🔴 Semáforo final: VERMELHO

**Justificativa direta:** existe **registro de marca em vigor na classe 42**
(NAVEGA EMPREENDIMENTOS LTDA, processo 931307759), acompanhado de registros
vigentes da mesma titular nas classes 36 e 37 e de um segundo registro em vigor
na classe 36. As duas classes que este produto precisaria estão fechadas por
marcas vigentes de terceiros.

Independentemente disso, dois fatores bastariam para rebaixar o nome: a Navega
Consultoria opera um painel de indicadores financeiros homônimo, e a Navegg é
foneticamente indistinguível e conhecida no mercado digital brasileiro há 15 anos.

**Não é uma questão de preferência estética — é bloqueio verificado.** Se a
Juliana quiser preservar a ideia, a metáfora náutica de condução ainda pode ser
explorada por outro termo do mesmo campo (embora Farol já tenha caído na triagem
anterior e o radical "naveg" esteja saturado).

---

# PARTE 2 — Segunda rodada de candidatos

## Critério de geração

Seguindo a observação estratégica registrada ao fim da rodada 1: **palavras reais
do português, porém menos frequentes**, 2–3 sílabas, fáceis ao telefone, que não
descrevem a função literalmente e não colidem com produto de consumo. O campo
semântico escolhido foi o de **medição precisa, sustentação e essência** — não o
de direção/luz (esgotado por Farol, Norte, Prisma, Lume) nem o de resultado
literal (Sobra, Apura).

**10 candidatos brutos gerados:** Lastro, Prumo, Cerne, Esteio, Âmago, Esquadro,
Bitola, Quilate, Teor, Peneira.

## Triagem 1 — domínios: a hipótese estratégica foi REFUTADA

Verifiquei os 10 (mais Navega) de uma vez:

| Nome | `.com.br` | `.com` |
|---|---|---|
| Navega, Lastro, Prumo, Cerne, Esteio, Âmago, Esquadro, Bitola, Quilate, Teor, Peneira | **todos tomados** | **todos tomados** |

**Onze de onze.** Isto merece ser dito com clareza, porque contradiz a
recomendação que eu mesmo registrei ao fim da rodada 1: **escolher palavras
portuguesas mais raras não melhora em nada a disponibilidade de domínio.** Mesmo
termos incomuns como "âmago", "esquadro" e "bitola" têm `.com.br` e `.com`
ocupados. O mercado de domínios de palavra única em português está saturado
independentemente de frequência de uso.

A recomendação continua válida **para o INPI** — e é lá que a raridade
efetivamente pagou, como mostra a triagem seguinte.

## Triagem 2 — INPI classe 42: aqui a raridade funcionou

| Nome | Classe 42 (NCL) | Veredito |
|---|---|---|
| **Quilate** | **Zero processos em TODAS as classes** | avança |
| **Bitola** | Zero na classe 42 | avança |
| **Esquadro** | Zero em `NCL 42` (único em vigor é de 1978, classificação antiga) | avança |
| **Esteio** | Zero em `NCL 42` (31 processos, todos antigos/extintos) | avança |
| **Cerne** | Zero em vigor, mas **1 pedido pendente de 29/03/2026** | avança com ressalva |
| **Teor** | Só 1 indeferido | não avançado — conceito abstrato demais |
| **Peneira** | Só 1 arquivado (MTV) | não avançado — coloquial ("peneira" de futebol) |
| **Lastro** | **2 registros em vigor** `NCL 42` — LASTRO TECNOLOGIA FINANCEIRA E IMOBILIÁRIA LTDA | 🔴 eliminado |
| **Prumo** | **5 registros em vigor** `NCL 42` — IPT, Prumo Participações (×2), MEI, Prumo Agropecuária | 🔴 eliminado |
| **Âmago** | **1 registro em vigor** `NCL 42` — PENDOLO TECNOLOGIA LTDA (+2 pendentes) | 🔴 eliminado |

**Lastro** doeu: conceito excelente para o posicionamento ("número com lastro"),
2 sílabas, teste de telefone perfeito. Mas a titular do registro em classe 42 é
uma **fintech** (Lastro Tecnologia Financeira e Imobiliária) — conflito de classe
e de setor ao mesmo tempo. Sem caminho.

---

## Validação completa dos 5 finalistas

### 1. Quilate

**Etapa 1 — Busca web: LIVRE (verificado).** Nenhuma empresa de software,
ERP, e-commerce ou fintech chamada Quilate. Existe **Quilate Joias**
(`quilatejoias.com.br`), joalheria, 11-50 funcionários — setor distinto.

**Etapa 2 — Domínio: INDISPONÍVEL.** `quilate.com.br` e `quilate.com` registrados.

**Etapa 3 — INPI: LIVRE, E É O MELHOR RESULTADO DAS DUAS RODADAS.**
Busca exata na classe 42: *"Nenhum resultado foi encontrado."*
Busca exata em **todas as classes**: *"Nenhum resultado foi encontrado."*

**Zero processos de marca "QUILATE" no INPI inteiro, em qualquer classe, em
qualquer situação.** Nenhum dos 15 nomes checados nas duas rodadas chegou perto
disso — nem Régua, que tinha um pedido pendente em classe 39.

**Etapa 4 — Redes: parcialmente indisponível.**
LinkedIn `/company/quilate` — TOMADO (Quilate Joias, joalheria).
Instagram `@quilate` — **NÃO VERIFICADO** (resposta sem dados de perfil).

**Etapa 5 — Teste de telefone: BOM.** Três sílabas (qui-la-te), palavra conhecida,
grafia previsível, sem acento. A pessoa entende e escreve sem soletrar.

**Etapa 6 — Ambiguidade: ALTA.** Este é o problema. "Quilate" é termo técnico de
**joalheria e ourivesaria** — medida de pureza do ouro e de peso de pedras
preciosas. A busca no Google é dominada por joalherias, cotação de ouro e guias
de compra de aliança. **Colide com produto de consumo conhecido (joias)**, o que
viola um dos critérios explícitos da lista.

Há ainda uma tensão de posicionamento: quilate evoca **luxo e valor material**,
enquanto o posicionamento é rigor e honestidade. "Ouro 18 quilates" puxa para
sofisticação, não para "aqui está o número real".

**Conceito a favor:** quilate é literalmente **medida de pureza** — quanto do
que parece ouro é ouro de fato. Aplicado a faturamento versus lucro real, a
metáfora é precisa e elegante: *"qual o quilate do seu faturamento?"*

**🟡 Semáforo final: AMARELO.** INPI absolutamente limpo (o único resultado assim
das duas rodadas) e nenhum homônimo em software. Rebaixado pela colisão com o
setor de joias, que compromete busca e puxa a percepção para luxo.

---

### 2. Esquadro

**Etapa 1 — Busca web: LIVRE (verificado).** Nenhuma empresa de software homônima.
**Esquadgroup** atua em software para indústria de esquadrias — nome distinto,
setor distinto. **Esquadro Comercial** (`esquadrocomercial.com.br`) é do ramo de
construção civil em SP.

**Etapa 2 — Domínio: INDISPONÍVEL.** `esquadro.com.br` e `esquadro.com` registrados.

**Etapa 3 — INPI: LIVRE NA CLASSE 42 (verificado).**
Classe 42: **zero registros em `NCL 42`**. Todas as classes: 12 processos, e o
único em vigor é de **1978** (ESQUADRO EMPREENDIMENTOS IMOBILIARIOS, classificação
antiga `40 : 10`) — não bloqueia a classe 42 moderna. Os demais estão extintos ou
arquivados. **Segundo resultado mais limpo da rodada.**

**Etapa 4 — Redes: indisponível, mas dormente.**
LinkedIn `/company/esquadro` — TOMADO (Esquadro, construção, Santana de
Parnaíba/SP, 97 funcionários, **apenas 15 seguidores**).
Instagram `@esquadro` — TOMADO ("Esquadro Materiais Construção", **7 seguidores,
29 seguindo**) — conta praticamente abandonada.

**Etapa 5 — Teste de telefone: MÉDIO — é a fraqueza do candidato.**
Três sílabas (es-qua-dro), mas com **dois encontros consonantais difíceis**: o
"squa" e o "dro". Ao telefone, com ruído, é plausível que precise de uma
repetição ou de uma âncora ("esquadro, de desenho técnico"). Todos os outros
finalistas são mais fáceis de falar. É um custo real e recorrente para um produto
vendido por telefone e indicação.

**Etapa 6 — Ambiguidade: MÉDIA — a menor de todos os finalistas.**
"Esquadro" é instrumento de desenho e construção. A busca puxa para materiais de
construção, esquadrias e papelaria — ruído real, porém **menos denso** que joias
(Quilate), jargão de incubadora (Cerne) ou nome de cidade (Esteio). Não é forma
verbal comum, o que já o separa de Sobra, Norte e Navega.

**Conceito a favor:** *"no esquadro"* significa alinhado, reto, sem desvio — é a
expressão que um profissional usa quando algo está **de fato certo**, não
aparentemente certo. Para um produto cuja tese é que os números dos concorrentes
estão fora do esquadro, a metáfora é direta e não descreve a função literalmente.

**🟡 Semáforo final: AMARELO.** O melhor equilíbrio das duas rodadas: INPI limpo
na classe 42, sem homônimo em software, ambiguidade a mais baixa do conjunto e
conceito bem alinhado. Rebaixado sobretudo pela pronúncia mais trabalhosa e pelo
domínio indisponível.

---

### 3. Cerne

**Etapa 1 — Busca web: CONFLITO VERIFICADO (duplo).**

> **Cerne ERP** (`cerneerp.com.br`) — software de gestão brasileiro, ERP completo
> para marcenarias, integrando todas as áreas da empresa. **É um ERP de gestão —
> mesma categoria de produto.**

> **Modelo CERNE** (Centro de Referência para Apoio a Novos Empreendimentos) —
> metodologia nacional Anprotec/Sebrae de **certificação de incubadoras de
> startups**, com literatura acadêmica própria e uso corrente no ecossistema.

O segundo é o mais grave, e repete exatamente o padrão que derrubou Régua na
rodada 1: **a palavra já é jargão consolidado no ecossistema onde a fundadora
circula**, com outro significado.

**Etapa 2 — Domínio: INDISPONÍVEL.** `cerne.com.br` e `cerne.com` registrados.

**Etapa 3 — INPI: SEM REGISTRO EM VIGOR, MAS COM RISCO DE PRIORIDADE.**

| Processo | Marca | Situação | Titular | Classe |
|---|---|---|---|---|
| 943209110 | Cerne | **Aguardando exame de mérito** | IGOR AMANCIO CARVALHO | NCL(13) **42** |
| 829616357 | CERNE | Indeferido (sem recurso) | CERNE TECNOLOGIA E TREINAMENTO | NCL(9) 42 |

Nenhum registro em vigor na classe 42 — mas há um **pedido depositado em
29/03/2026**, há menos de cinco meses, aguardando exame. Pelo princípio de
**first-to-file**, esse pedido tem prioridade sobre qualquer depósito novo. Se
for concedido, bloqueia. É risco aberto, não conflito consumado.
(32 processos no total, em todas as classes.)

**Etapa 4 — Redes: NÃO VERIFICADO.**

**Etapa 5 — Teste de telefone: EXCELENTE.** Duas sílabas (cer-ne), grafia
inequívoca. O melhor dos finalistas neste critério.

**Etapa 6 — Ambiguidade: ALTA.** O jargão CERNE no ecossistema de incubadoras,
mais o Cerne ERP na mesma categoria de produto.

**Conceito a favor:** *"o cerne da questão"* — o núcleo, a parte mais densa e
verdadeira da madeira. **Conceitualmente é o melhor nome das duas rodadas** para
o posicionamento: chegar ao cerne dos números.

**🟡 Semáforo final: AMARELO-ESCURO.** Conceito excelente, telefone perfeito,
INPI sem registro concedido. Mas acumula três riscos: pedido pendente na classe
42 com prioridade, um ERP homônimo na mesma categoria, e jargão consolidado no
ecossistema de startups. **Repete exatamente o erro que fez a Juliana rejeitar
Régua** — recomendo não insistir.

---

### 4. Esteio

**Etapa 1 — Busca web: CONFLITO ESTRUTURAL VERIFICADO.**
**Esteio é um município do Rio Grande do Sul** — 83 mil habitantes, região
metropolitana de Porto Alegre. A busca por "Esteio software" retorna **diretórios
de empresas de TI sediadas na cidade** (SulCode, um ERP local; WCC; Algayer
Tecnologia; Fast Computers). Existe também **Esteio Engenharia e
Aerolevantamentos**.

**Etapa 2 — Domínio: INDISPONÍVEL.** `esteio.com.br` e `esteio.com` registrados.

**Etapa 3 — INPI: LIVRE NA CLASSE 42 (verificado).** Zero registros em `NCL 42`.
São 31 processos em todas as classes, todos em classificação antiga
(construtoras, agrícolas, churrascaria) e todos extintos ou arquivados.

**Etapa 4 — Redes: NÃO VERIFICADO.**

**Etapa 5 — Teste de telefone: BOM.** Três sílabas (es-tei-o).

**Etapa 6 — Ambiguidade: MUITO ALTA E IRRECUPERÁVEL.** Ser nome de cidade com
polo tecnológico na região metropolitana significa que a busca por "Esteio" +
qualquer termo de software retorna **empresas locais, não o produto**. É
exatamente o problema que derrubou Vera na rodada 1 (município Vera/MT), só que
pior: Esteio é maior, mais próxima de um polo de TI (Tecnosinos) e já tem um ERP
sediado nela.

**🔴 Semáforo final: VERMELHO.** INPI limpo não compensa: o nome é
estruturalmente inbuscável no contexto de software brasileiro.

---

### 5. Bitola

**Etapa 1 — Busca web: LIVRE.** Nenhuma empresa de software homônima.

**Etapa 2 — Domínio: INDISPONÍVEL.** `bitola.com.br` e `bitola.com` registrados.

**Etapa 3 — INPI: LIVRE NA CLASSE 42 (verificado).** *"Nenhum resultado foi
encontrado"* na classe 42.

**Etapa 4 — Redes: NÃO VERIFICADO.**

**Etapa 5 — Teste de telefone: BOM.** Três sílabas (bi-to-la).

**Etapa 6 — Ambiguidade: CONOTAÇÃO NEGATIVA VERIFICADA — eliminatória.**
"Bitola" tecnicamente é medida padrão (de trilho, de fio). Mas o derivado
**"bitolado"** significa, em uso corrente e registrado em dicionário (Dicio,
Priberam, Infopédia): *pessoa de mente fechada, que não recebe bem novas ideias,
com conhecimentos ultrapassados, careta*.

Um produto que existe para **abrir os olhos do lojista** sobre números que ele
não enxerga não pode carregar uma raiz que significa estreiteza mental. O
primeiro concorrente ou cliente irritado que fizer o trocadilho, ele cola.

**🔴 Semáforo final: VERMELHO.** INPI limpo, mas a conotação do derivado é
diretamente contrária ao posicionamento. Descartar.

---

## Tabela-resumo — rodada 2

| Nome | Web | `.com.br` | `.com` | INPI cl. 42 | Redes | Telefone | Ambiguidade | **Semáforo** |
|---|---|---|---|---|---|---|---|---|
| **Navega** | 🔴 Navega Consultoria (mesma categoria) + Navegg | 🔴 tomado | 🔴 tomado | 🔴 **registro em vigor** (+36 e 37) | ⚪ não verif. | 🟢 excelente | 🔴 muito alta | 🔴 **VERMELHO** |
| **Quilate** | 🟢 sem homônimo em software | 🔴 tomado | 🔴 tomado | 🟢 **zero em TODAS as classes** | 🔴 LinkedIn tomado · ⚪ IG não verif. | 🟢 bom | 🔴 alta (joias) | 🟡 **AMARELO** |
| **Esquadro** | 🟢 sem homônimo em software | 🔴 tomado | 🔴 tomado | 🟢 **zero em `NCL 42`** | 🟡 tomados, mas dormentes | 🟡 médio | 🟡 média | 🟡 **AMARELO** |
| **Cerne** | 🔴 Cerne ERP + jargão CERNE | 🔴 tomado | 🔴 tomado | 🟡 sem registro, **1 pendente cl. 42** | ⚪ não verif. | 🟢 excelente | 🔴 alta | 🟡 **AMARELO-ESCURO** |
| **Esteio** | 🔴 município do RS + ERP local | 🔴 tomado | 🔴 tomado | 🟢 zero em `NCL 42` | ⚪ não verif. | 🟢 bom | 🔴 muito alta | 🔴 **VERMELHO** |
| **Bitola** | 🟢 sem homônimo | 🔴 tomado | 🔴 tomado | 🟢 zero na cl. 42 | ⚪ não verif. | 🟢 bom | 🔴 "bitolado" | 🔴 **VERMELHO** |
| Lastro | — | 🔴 | 🔴 | 🔴 **2 em vigor** (fintech) | — | — | — | 🔴 eliminado na triagem |
| Prumo | — | 🔴 | 🔴 | 🔴 **5 em vigor** | — | — | — | 🔴 eliminado na triagem |
| Âmago | — | 🔴 | 🔴 | 🔴 **1 em vigor** (Pendolo Tecnologia) | — | — | — | 🔴 eliminado na triagem |

**Legenda:** 🟢 verificado livre · 🟡 risco conhecido · 🔴 verificado em conflito ·
⚪ não verificado

---

## O padrão que emergiu depois de 16 nomes

Vale nomear, porque muda o que fazer a seguir. Entre as duas rodadas foram
checados 16 nomes (4 descartados antes, 5 na rodada 1, Navega e 10 na rodada 2).
**Nenhum saiu verde.** Os obstáculos se repetem em ordem previsível:

1. **Domínio de palavra única em português: esgotado.** 16 de 16 `.com.br`
   tomados, incluindo palavras raras. Minha recomendação anterior — buscar
   palavras menos frequentes para achar domínio — está **refutada pelos dados**.
2. **Palavra comum → INPI disputado + SEO impossível** (Cardume, Vera, Navega,
   Prumo, Lastro, Âmago).
3. **Palavra rara → INPI livre, mas quase sempre com um "porém" de significado**:
   é jargão do setor (Régua, Cerne), nome de cidade (Esteio, Vera), produto de
   consumo (Quilate) ou tem derivado ruim (Bitola).

O gargalo **não é a lista de palavras — é o formato**. Insistir em "uma palavra
real do português, domínio exato `.com.br`" tem probabilidade baixa de sucesso,
e cada rodada nova vai reencontrar as mesmas três paredes.

**Duas saídas que mudariam o jogo** (decisão da Juliana, não minha):

- **Aceitar domínio não exato e manter um nome bom.** `useesquadro.com.br`,
  `esquadro.app.br`, `esquadroapp.com.br`. Isso destrava imediatamente os nomes
  que já passaram no INPI. É o caminho que a maioria dos SaaS brasileiros toma
- **Palavra modificada ou cunhada a partir do português.** Um termo levemente
  alterado (como a Navegg fez com "navega") mantém a memorabilidade brasileira,
  mas ganha domínio livre e registrabilidade muito maior no INPI. É o único
  formato que resolve as três paredes de uma vez

---

## Top 3 — trade-offs para a Juliana decidir

Considerando **as duas rodadas juntas**. A decisão é sua.

### 1. Esquadro — o melhor equilíbrio geral

**A favor:** INPI verificadamente livre na classe 42. Nenhuma empresa de software
homônima. **A ambiguidade mais baixa de todos os 16 nomes checados** — não é
forma verbal comum, não é cidade, não é jargão do setor. Conceito preciso e
alinhado: "no esquadro" é o que está de fato certo. Handles tomados, mas por
contas dormentes (7 e 15 seguidores).

**Contra:** o mais difícil de pronunciar dos finalistas — dois encontros
consonantais, pode exigir repetição ao telefone. Domínio exato indisponível.

**Escolha se:** você aceita um nome ligeiramente mais trabalhoso de falar em
troca do perfil de risco mais baixo do conjunto inteiro.

### 2. Quilate — o mais limpo juridicamente de todos

**A favor:** **zero processos no INPI em todas as classes** — nenhum outro nome
das duas rodadas chegou a isso. Via de registro mais desimpedida possível.
Conceito bonito e exato: medida de pureza aplicada ao lucro.

**Contra:** colide com joalheria, que é produto de consumo conhecido — critério
que a própria lista exclui. Busca dominada por ouro e alianças. Puxa a percepção
para luxo, não para rigor. LinkedIn tomado por joalheria homônima.

**Escolha se:** segurança de registro é a prioridade absoluta e você aceita
disputar o significado da palavra com o setor de joias.

### 3. Régua (rodada 1) — permanece competitivo

**A favor:** INPI sem nenhum registro concedido em qualquer classe. Conceito
perfeitamente alinhado. Duas sílabas, fácil ao telefone. Único com janela real de
domínio: `regua.com.br` está `on_hold` com expiração vencida em 20/05/2026.

**Contra:** "régua de cobrança" é jargão consolidado no vocabulário do
público-alvo — o motivo pelo qual você já o rejeitou. Vale reconsiderar apenas
porque, depois de mais 11 nomes checados, **nenhum superou seu perfil de risco**.

### Menção honrosa — Cerne

Conceitualmente o melhor nome das duas rodadas, e perfeito ao telefone. Não está
no top 3 porque acumula pedido pendente na classe 42 com prioridade, um ERP
homônimo na mesma categoria e jargão de ecossistema. Se algum desses três se
dissolver (por exemplo, se o pedido 943209110 for indeferido), ele volta forte.

### Leitura, não decisão

Se a prioridade for **risco baixo de forma equilibrada**, Esquadro. Se for
**segurança de registro acima de tudo**, Quilate. Se você estiver disposta a
reabrir a rodada 1 à luz do que veio depois, Régua continua de pé.

E se nenhum dos três convencer, a recomendação estrutural acima (aceitar domínio
não exato, ou partir para palavra modificada) tende a render mais que uma
terceira rodada no mesmo formato.

---

## Passos manuais — só a Juliana pode fazer

### Prioridade alta

1. **Decidir o formato antes de decidir a palavra.** Se aceitar domínio não
   exato (`use<nome>.com.br`, `<nome>.app.br`), Esquadro e Quilate ficam
   imediatamente viáveis. Se exigir `.com.br` exato, nenhum dos 16 nomes serve e
   a saída é palavra cunhada.

2. **Busca de anterioridade com advogado de PI** para o finalista, cobrindo
   **classes 42 e 35** e marcas foneticamente semelhantes. A busca feita aqui é
   por elemento nominal exato e radical, na base pública, atualizada até
   11/08/2026 — não substitui parecer.

3. **Se for Cerne, verificar o andamento do processo 943209110** (depositado
   29/03/2026, aguardando exame). Ele tem prioridade sobre um depósito novo.

### Prioridade média

4. **Verificar manualmente** (logada): Instagram `@quilate`; e, se Cerne voltar
   à mesa, Instagram e LinkedIn de `cerne`.

5. **WHOIS logado** no registro.br para `esquadro.com.br` e `quilate.com.br` —
   descobrir titular e avaliar disposição de venda.

6. **Monitorar `regua.com.br`** — segue `on_hold` com expiração vencida.

### Não delegável

7. **A escolha do nome.** Este documento traz o que foi verificado, o que não
   foi, e onde estão os riscos. A decisão é sua.

---

## Fontes consultadas

- INPI — busca de marcas: `https://busca.inpi.gov.br/pePI/` (dados até 11/08/2026)
- registro.br — API de disponibilidade · Verisign RDAP
- [Navega Consultoria — Sistema Navega](https://navegaconsultoria.com.br/sistema.html)
- [Navegg](https://www.navegg.com/br/perguntas-frequentes) ·
  [Dentsu anuncia aquisição da Navegg](https://www.dentsu.com/br/pt/ultimas-noticias/press-release-navegg) ·
  [Meio & Mensagem sobre a aquisição](https://www.meioemensagem.com.br/comunicacao/navegg-e-a-mais-nova-compra-da-dentsu)
- [Nave — gestão comercial](https://nave.app.br/)
- [Cerne ERP](https://www.cerneerp.com.br/) ·
  [Certificação CERNE — Sebrae](https://sebraepr.com.br/comunidade/artigo/o-que-e-a-certificacao-cerne)
- [SulCode — ERP sediado em Esteio/RS](https://sulcode.com.br/sobre/) ·
  [Esteio (município) — Wikipédia](https://en.wikipedia.org/wiki/Esteio)
- [Esquadro Comercial](http://esquadrocomercial.com.br) ·
  [Esquadgroup](https://www.esquadgroup.com.br/)
- [Quilate — Wikipédia](https://pt.wikipedia.org/wiki/Quilate)
- [Bitolado — Dicio](https://www.dicio.com.br/bitolado/) ·
  [Bitola — Priberam](https://dicionario.priberam.org/bitola)
