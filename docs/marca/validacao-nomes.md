# Validação dos candidatos a nome — primeira rodada

**Data:** 2026-08-11
**Executado por:** agente `pesquisador-marca`
**Candidatos avaliados:** Cardume, Sobra, Vera, Norte, Régua

---

## Metodologia

Para cada candidato foram executadas seis etapas, nesta ordem:

1. **Busca web** — empresas de software no Brasil com o nome, com foco em gestão,
   ERP, e-commerce e fintech.
2. **Domínio** — disponibilidade de `<nome>.com.br` e `<nome>.com`.
3. **INPI** — busca de marca na classe 42 (software) e, complementarmente, em
   todas as classes.
4. **Redes** — handles no Instagram e LinkedIn.
5. **Teste de telefone** — a pessoa entende e escreve o nome ao ouvir?
6. **Ambiguidade** — colisão com produto conhecido ou jargão do setor que
   comprometa busca no Google.

### O que foi verificado com sucesso

**Domínios `.com.br` — verificado.** Consulta à API pública do registro.br
(`https://registro.br/v2/ajax/avail/raw/<dominio>`). A semântica do retorno foi
calibrada com dois controles: um domínio inexistente retornou `status: 0`
(disponível) e `google.com.br` retornou `status: 2` (registrado). Portanto
`status: 2` = indisponível.

**Domínios `.com` — verificado.** Consulta ao RDAP da Verisign
(`https://rdap.verisign.com/com/v1/domain/<dominio>.com`). HTTP 200 = registrado,
HTTP 404 = disponível.

**INPI — VERIFICADO.** Ao contrário do previsto, a consulta funcionou. O sistema
pePI permite acesso anônimo: foi estabelecida sessão via
`servlet/LoginController?action=login` e submetido o formulário
`F_Pesquisa_Classe_Basica` para `servlet/MarcasServletController` com
`tipoPesquisa=BY_MARCA_CLASSIF_BASICA`. Foram rodadas buscas **exatas** na classe
42 e em todas as classes, mais buscas **radicais** na classe 42. A própria base
do INPI declarou os dados atualizados até **11/08/2026** — ou seja, a consulta é
do dia.

**Redes sociais — parcialmente verificado.** Instagram e LinkedIn responderam em
alguns casos e bloquearam em outros (ver limitações).

### Limitações — o que NÃO foi verificado

| Item | Situação | Como verificar manualmente |
|---|---|---|
| **Titularidade real dos domínios** | Sabemos que estão registrados, não quem detém nem se há interesse em vender | WHOIS logado no registro.br; para `.com`, consultar o registrador |
| **Instagram @regua e @vera** | Respostas retornaram só imagens base64, sem dados de perfil — inconclusivo | Abrir `instagram.com/regua` e `instagram.com/vera` logada |
| **LinkedIn — demais handles** | Uma consulta retornou HTTP 429 (rate limit) antes de nova tentativa bem-sucedida | Buscar o handle logada no LinkedIn |
| **Parecer de registrabilidade** | Busca no INPI ≠ parecer jurídico. O próprio INPI adverte que o exame de mérito faz nova busca e decide sobre a registrabilidade do sinal | Consulta com advogado de propriedade industrial antes de depositar |
| **Marcas figurativas/mistas semelhantes** | A busca foi por elemento nominal exato e radical; não cobre marcas graficamente semelhantes ou foneticamente próximas de outra grafia | Busca de anterioridade profissional |

**Regra aplicada em todo este documento:** onde está escrito "verificado", houve
consulta bem-sucedida com retorno registrado. Onde está "não verificado", a
consulta falhou tecnicamente e nenhuma conclusão foi inferida.

---

## 1. Cardume

### Etapa 1 — Busca web: CONFLITO VERIFICADO

Várias empresas ativas no Brasil, todas no espaço digital/tecnologia:

- **Cardume Digital** (Nova Prata, RS, fundada em 2010, 2-10 funcionários) —
  agência de sites e plataforma de marketing, `cardume.digital`
- **Agência Cardume** — agência digital
- **cardume.io** — consultoria de gestão e sustentabilidade financeira
- **Cardume Consultoria Empresarial** (Manaus, AM, CNPJ 24.091.232/0001-60, ativa)
- **Cardume** (`cardume.com`, Flórida, EUA) — agência de publicidade digital que
  também constrói produtos de tecnologia próprios

### Etapa 2 — Domínio: INDISPONÍVEL (verificado)

| Domínio | Status | Evidência |
|---|---|---|
| `cardume.com.br` | **Registrado e em uso ativo** | registro.br `status: 2`, expira 18/04/2031, DNS na AWS. Responde HTTP 200 e **redireciona para `cardume.com`** |
| `cardume.com` | **Registrado** | RDAP 200 — site ativo da agência americana |

### Etapa 3 — INPI classe 42: CONFLITO VERIFICADO

Busca exata "CARDUME", NCL 42:

| Processo | Marca | Situação | Titular |
|---|---|---|---|
| 917480996 | cardume | **Registro de marca em vigor** | CARDUME AGENCIA DIGITAL LTDA |
| 922232776 | Cardume | **Registro de marca em vigor** | CARDUME SOCIOAMBIENTAL E COMUNICACAO LTDA ME |
| 912692952 | CARDUME | Pedido definitivamente arquivado | CARDUME MEIOS DE PAGAMENTOS LTDA |
| 919362478 | CARDUME | Pedido indeferido | DANIEL FRÓES LIMA LAFRAIA |

**Dois registros em vigor na classe exata do produto.** Este é o achado mais
grave de toda a rodada. Note também que um pedido anterior de terceiro foi
*indeferido* na mesma classe — indício de que o INPI já barrou tentativa de
convivência.

### Etapa 4 — Redes: INDISPONÍVEL (verificado)

- **Instagram `@cardume`** — TOMADO. Perfil "Cardume 🐟🐟🐟", bio "Não se sinta um
  peixe fora d'água. Encontre o seu grupo digital na comunidade Cardume!",
  531 seguidores, link para cardume.com.br
- **LinkedIn `/company/cardume`** — TOMADO. Cardume Digital, publicidade e
  propaganda, 1.245 seguidores

### Etapa 5 — Teste de telefone: BOM

Três sílabas (car-du-me), palavra do vocabulário comum. A pessoa entende ao ouvir
e escreve corretamente sem soletração. Sem risco de grafia ambígua.

### Etapa 6 — Ambiguidade: MODERADA

Não colide com produto de consumo (carro, banco, remédio). Mas colide com
**quatro agências digitais brasileiras homônimas** — a busca por "cardume
software" ou "cardume tecnologia" já retorna concorrentes de atenção no mesmo
campo semântico digital.

### 🔴 Semáforo final: VERMELHO

Dois registros de marca **em vigor na classe 42**, domínio `.com.br` em uso ativo
redirecionando para empresa estrangeira homônima do setor, Instagram e LinkedIn
tomados. Não há caminho viável aqui. **Recomenda-se descartar.**

---

## 2. Sobra

### Etapa 1 — Busca web: CONFLITO DE MERCADO (verificado)

Nenhuma empresa de software chamada exatamente "Sobra" no Brasil. **Porém**, o
achado mais relevante da busca:

> **QuantoSobra** (`quantosobra.com.br`) — ERP/sistema de gestão para lojas do
> varejo brasileiro. Controle financeiro, estoque, clientes, emissão fiscal.
> Declaram mais de 1.500 lojas e milhares de usuários ativos em todo o Brasil.
> Também operam um segundo produto, o Awise.

Isto não é um conflito legal (o sinal é outro), mas é um **conflito de
posicionamento sério**: a marca QuantoSobra já ocupa exatamente a pergunta
"quanto sobra?" no exato segmento de gestão para lojista brasileiro. O conceito
que torna "Sobra" atraente já foi plantado no mercado por outro.

### Etapa 2 — Domínio: INDISPONÍVEL (verificado)

| Domínio | Status | Evidência |
|---|---|---|
| `sobra.com.br` | **Registrado** | registro.br `status: 2`, publicado, expira 20/03/2027, DNS genérico (`a.auto.dns.br`). **Não responde HTTP** — registrado mas sem site ativo |
| `sobra.com` | **Registrado** | RDAP 200 |

### Etapa 3 — INPI: LIVRE NA CLASSE 42 (verificado)

**Classe 42, busca exata:** *"Nenhum resultado foi encontrado para a sua
pesquisa."* — nenhum processo, em nenhuma situação.

**Todas as classes, busca exata:** 2 processos, **nenhum com registro concedido**:

| Processo | Marca | Situação | Titular | Classe |
|---|---|---|---|---|
| 934033676 | S'OBRA | Aguardando exame de mérito | S'OBRA LTDA | NCL(12) 35 |
| 942942493 | Sobra | Aguardando exame de mérito | EVANDRO ARAUJO MACEDO MARTINS | NCL(13) 36 |

**Leitura:** a classe 42 está limpa. Atenção ao processo 934033676 — a classe 35
cobre "gestão de negócios comerciais", que uma plataforma de gestão
provavelmente também vai querer. O sinal é "S'OBRA" (com apóstrofo), distinto,
e o pedido ainda está pendente. Risco moderado, não bloqueio.

A busca radical na classe 42 retornou apenas compostos irrelevantes (SOBRATEL,
SOBRAVE, SOBRABIPI, SOBRATUR), todos **extintos**.

### Etapa 4 — Redes: INDISPONÍVEL (verificado)

- **Instagram `@sobra`** — TOMADO. Conta pessoal "cleide sobra", 142 seguidores,
  posts de 2018. Conta dormente — negociação teoricamente possível, mas o
  Instagram não intermedeia transferência de handle
- **LinkedIn `/company/sobra`** — TOMADO. Empresa "Sobra" sediada em Zottegem,
  Bélgica

### Etapa 5 — Teste de telefone: EXCELENTE

Duas sílabas (so-bra), palavra corriqueira do português. Ninguém precisa
soletrar. O melhor do conjunto neste critério, empatado com Vera e Norte.

### Etapa 6 — Ambiguidade: ALTA

Não colide com produto de consumo, mas o problema é outro e é grave: **"sobra" é
forma verbal e substantivo de altíssima frequência** ("sobra dinheiro", "o que
sobra no fim do mês"). É praticamente impossível ranquear ou dominar o termo na
busca. A busca por "sobra e-commerce lucro" retorna artigos genéricos de
conteúdo — inclusive títulos como "Por Que Seu E-commerce Vende Muito e Não
Sobra Dinheiro". O vocabulário do nicho já usa a palavra livremente.

Some-se a isso o **risco de percepção já mapeado**: "sobra" carrega conotação de
resto, sobejo, o que ficou. Para vender a lojista de R$1M–5M/ano com equipe de
até 15 pessoas, o nome pode soar diminutivo.

### 🟡 Semáforo final: AMARELO

O gate mais difícil (INPI classe 42) está **verificado livre** — e isso é
significativo. Mas três fatores pesam contra: QuantoSobra ocupa o mesmo conceito
no mesmo segmento, o SEO é estruturalmente inviável, e a conotação de "resto"
permanece. Viável juridicamente, frágil estrategicamente.

---

## 3. Vera

### Etapa 1 — Busca web: CONFLITO VERIFICADO

- **Vera.Support** (`vera.support`) — software de suporte com IA **para bancos e
  fintechs**, com página em português para o mercado brasileiro. Adjacência
  direta de setor
- **Vera Solutions** (`verasolutions.org`) — soluções de dados, opera no Brasil
- Ruído massivo: **Vera é município de Mato Grosso** (ERPs regionais anunciam
  "Sistema ERP em Vera/MT"), há **Vera Cruz**, **Vera Mendes/PI**, e uma
  quantidade grande de resultados de "Versa ERP" / "Sistema Versa" na mesma
  vizinhança fonética

### Etapa 2 — Domínio: INDISPONÍVEL (verificado)

| Domínio | Status | Evidência |
|---|---|---|
| `vera.com.br` | **Registrado** | registro.br `status: 2`, publicado, expira 02/02/2027. Não responde HTTP |
| `vera.com` | **Registrado** | RDAP 200 |

### Etapa 3 — INPI classe 42: CONFLITO VERIFICADO

Busca exata "VERA", NCL 42:

| Processo | Marca | Situação | Titular | Classe |
|---|---|---|---|---|
| 930362284 | VERA | **Registro de marca em vigor** | **VEREYE TECNOLOGIA LTDA** | NCL(12) 42 |
| 501895872 | Vera | Aguardando exame de mérito | LAREPONSE.TECH | NCL(12) 09, 35, 38, 41, 42 |
| 812844645 | VERA | Arquivado | VERA EMPREENDIMENTOS IMOBILIARIOS | 40:10 |

**Registro em vigor na classe 42, detido por uma empresa de tecnologia.** Além
disso, há um pedido multiclasse pendente (classes 9, 35, 38, 41 e 42) de outra
empresa de tecnologia — se concedido, fecha praticamente todo o espaço de
proteção relevante para um SaaS.

### Etapa 4 — Redes: INDISPONÍVEL / NÃO VERIFICADO

- **LinkedIn `/company/vera`** — TOMADO. Banda de rock alternativo de Istambul,
  ativa, 12 pessoas
- **Instagram `@vera`** — **NÃO VERIFICADO.** A resposta retornou apenas imagens
  codificadas em base64, sem dados de perfil. Dado o padrão observado
  (nomes próprios curtos são sempre ocupados no Instagram), a expectativa é de
  indisponibilidade, mas **isso é expectativa, não verificação**

### Etapa 5 — Teste de telefone: EXCELENTE

Duas sílabas (ve-ra), nome próprio universalmente conhecido no Brasil. Zero
fricção de grafia. É o ponto mais forte do candidato — e não compensa o resto.

### Etapa 6 — Ambiguidade: MUITO ALTA

"Vera" é **um dos nomes femininos mais comuns do Brasil**. A busca é dominada por
pessoas físicas, pelo município de Vera/MT, por Vera Cruz e por Vera Fischer.
Construir presença de busca sobre este termo exigiria orçamento de mídia
incompatível com uma fundadora solo.

Há ainda um risco de posicionamento: usar "Vera" como persona de IA aproxima o
produto justamente da "magia de IA" que o posicionamento declarado (honestidade
numérica, não magia de IA) quer evitar. Nomear a IA com nome de gente humaniza e
sugere agência própria — o oposto de "aqui está o número e aqui está a query que
o gerou".

### 🔴 Semáforo final: VERMELHO

Registro de marca **em vigor na classe 42** por empresa de tecnologia, mais
pedido multiclasse pendente de outra. Domínios tomados. Ambiguidade de busca
severa. Tensão com o próprio posicionamento. **Recomenda-se descartar.**

---

## 4. Norte

### Etapa 1 — Busca web: CONFLITO DE PERCEPÇÃO (verificado)

- **Norte Ventures** (`norte.ventures`) — fundo de venture capital brasileiro
  sediado em São Paulo, fundado em 2020, pre-seed e seed. Investidores incluem
  fundadores de iFood, Gympass, Movile, Kovi, Brex, e fundos como Monashees,
  Redpoint e OneVC. Portfólio de 100+ empresas. **Confirmado e relevante.**
- **Nortesys** (`nortesys.com.br`) — sistema de gestão ERP completo, com app de
  vendas, leitor de código de barras e dashboards de faturamento. **Adjacência
  direta de produto**, ainda que o nome seja composto
- **NORD Software** (Jundiaí/SP) — ERP, 20+ anos

O conflito com Norte Ventures não é legal, é **social**: uma fundadora solo
buscando investimento no ecossistema brasileiro de startups teria o nome do
produto confundido com o de um fundo conhecido, exatamente nas conversas onde
isso mais atrapalha.

### Etapa 2 — Domínio: INDISPONÍVEL (verificado)

| Domínio | Status | Evidência |
|---|---|---|
| `norte.com.br` | **Registrado** | registro.br `status: 2`, publicado, expira 08/04/2028, DNS `ns1.ifxwh.com.br`. Não responde HTTP |
| `norte.com` | **Registrado** | RDAP 200 |

### Etapa 3 — INPI: CLASSE 42 LIVRE, CLASSE 35 COMPROMETIDA (verificado)

**Classe 42, busca exata:** *"Nenhum resultado foi encontrado."*

**Todas as classes, busca exata:** 19 processos. Os relevantes:

| Processo | Marca | Situação | Titular | Classe |
|---|---|---|---|---|
| 913948969 | NORTE | **Registro em vigor** | MÖVE ESCRITÓRIO DE ARTE LTDA - EPP | NCL(11) **35** |
| 909826811 | Norte | **Registro em vigor** | MÖVE ESCRITÓRIO DE ARTE LTDA - EPP | NCL(10) **35** |
| 913928780 | Norte | Indeferido (sem recurso) | NORTE CONSULTORES ASSOCIADOS LTDA - ME | NCL(11) 35 |
| 925246611 | NORTE | Indeferido (mantido em recurso) | DOUGLAS ADOLPHO | NCL(11) 35 |
| 931727308 | + NORTE | Aguardando recurso contra indeferimento | ELVIS DA SILVA - ME | NCL(12) 35 |
| 938960377 | NORTE | Aguardando exame de mérito | LUCIANO INACIO PEREIRA JUNIOR | NCL(12) 35 |
| 903616700 | NORTE | Registro em vigor | LESSANDRO PANDOLFI PESSOTTI | NCL(9) 30 |
| 931727227 | + NORTE | Registro em vigor | ELVIS DA SILVA - ME | NCL(12) 30 |

**Leitura importante:** a classe 42 está verificadamente livre. Mas a **classe 35
(gestão de negócios comerciais) tem dois registros em vigor de um mesmo
terceiro**, e o histórico mostra **três pedidos de "NORTE" indeferidos na classe
35** por outros requerentes. Ou seja: o INPI vem barrando sistematicamente novos
"NORTE" na classe de gestão de negócios. Uma plataforma de gestão que queira
proteção nas classes 42 **e** 35 provavelmente só conseguiria metade.

Risco adicional a considerar com advogado: "norte" é ponto cardeal e designação
de região do país, o que pode atrair objeção de sinal de uso comum.

### Etapa 4 — Redes: PARCIALMENTE DISPONÍVEL (verificado)

- **LinkedIn `/company/norte`** — **HTTP 404. Handle aparentemente LIVRE.** Único
  handle verificadamente disponível em toda a rodada
- **Instagram `@norte`** — TOMADO. Conta pessoal "NORTE834", 359 seguidores,
  posts de 2015. Conta dormente

### Etapa 5 — Teste de telefone: EXCELENTE

Duas sílabas (nor-te), palavra universal do português. Zero soletração.

### Etapa 6 — Ambiguidade: MUITO ALTA

Não colide com produto de consumo, mas "norte" é simultaneamente ponto cardeal,
região do Brasil, e metáfora corriqueira ("dar o norte", "perder o norte"). A
busca é irrecuperável sem qualificador. Há ainda o risco de leitura **regional**
— um produto chamado Norte vendido nacionalmente pode ser lido como
especializado na Região Norte, o que não é o posicionamento.

### 🟡 Semáforo final: AMARELO

Classe 42 verificadamente livre e handle de LinkedIn disponível são pontos reais
a favor. Contra: classe 35 praticamente fechada, Norte Ventures ocupando o nome
no ecossistema onde a fundadora circula, Nortesys adjacente no produto, e
ambiguidade de busca severa com risco de leitura regional.

---

## 5. Régua

### Etapa 1 — Busca web: SEM CONFLITO DE EMPRESA (verificado)

Nenhuma empresa de software, ERP, e-commerce ou fintech chamada "Régua"
encontrada no Brasil. A busca por "Régua software plataforma SaaS Brasil gestão"
retornou apenas conteúdo genérico sobre SaaS, sem produto homônimo. **É o mais
limpo do conjunto nesta etapa.**

Registrado apenas: **REGUA — Reserva Ecológica de Guapiaçu**, ONG brasileira de
conservação da Mata Atlântica (Cachoeiras de Macacu/RJ, fundada em 2001, 11-50
pessoas). Setor completamente distinto, mas ocupa o acrônimo.

### Etapa 2 — Domínio: INDISPONÍVEL, COM RESSALVA RELEVANTE (verificado)

| Domínio | Status | Evidência |
|---|---|---|
| `regua.com.br` | **Registrado, mas `on_hold`** | registro.br `status: 2`, `publication-status: "on_hold"`, **expirou em 20/05/2026**. Não responde HTTP |
| `regua.com` | **Registrado** | RDAP 200 |

**Ressalva importante e a única boa notícia de domínio da rodada:** `regua.com.br`
está com status de publicação `on_hold` e data de expiração já vencida
(20/05/2026, quase três meses atrás). No registro.br, isso indica **não
renovação** — o domínio está congelado e tende a seguir para processo de
liberação. **Não está disponível hoje**, e o calendário exato de liberação não
foi verificado. Mas é o único candidato com uma janela plausível de abertura.
Vale monitorar.

### Etapa 3 — INPI: LIVRE NA CLASSE 42 (verificado)

**Classe 42, busca exata (com acento):** *"Nenhum resultado foi encontrado."*

**Todas as classes**, busca exata com acento (`RÉGUA`) e sem acento (`REGUA`) —
ambas retornaram o mesmo processo único:

| Processo | Marca | Situação | Titular | Classe |
|---|---|---|---|---|
| 942283953 | RÉGUA | Aguardando exame de mérito | RÉGUA VIAGENS LTDA | NCL(12) 39 |

Classe 39 é transporte e viagens — sem sobreposição. **Nenhum registro concedido
para "Régua" em nenhuma classe, no Brasil inteiro.** É de longe o resultado mais
limpo da rodada no INPI.

A busca radical na classe 42 retornou apenas compostos não relacionados
(REGUARDIONS, e marcas de drogaria/Iron Mountain).

Ressalva de registrabilidade a discutir com advogado: por ser instrumento de
medição, "Régua" poderia atrair alegação de descritividade se a especificação
de serviços enfatizar medição/métricas. Aplicado a software de gestão, porém, o
termo é **arbitrário** (não descreve o serviço), o que joga a favor.

### Etapa 4 — Redes: PARCIALMENTE INDISPONÍVEL

- **LinkedIn `/company/regua`** — TOMADO pela ONG REGUA (Reserva Ecológica de
  Guapiaçu), 978 seguidores, página ativa com publicações recentes
- **Instagram `@regua`** — **NÃO VERIFICADO.** A resposta retornou apenas imagens
  base64, sem dados de perfil. Precisa de verificação manual logada

### Etapa 5 — Teste de telefone: BOM, COM UMA RESSALVA

Duas sílabas (ré-gua), palavra do vocabulário básico — toda pessoa alfabetizada
no Brasil sabe o que é uma régua e escreve a palavra. A pessoa **entende** ao
ouvir, sem soletrar.

A ressalva é de **grafia digital**: a palavra leva acento agudo, mas o domínio e
os handles seriam sem acento (`regua`). Isso cria um descasamento leve entre
como se fala/escreve e como se digita — o cliente pode tentar `régua.com.br`.
Fricção real, porém menor: é o mesmo problema que marcas como "Méliuz"
administram sem prejuízo.

### Etapa 6 — Ambiguidade: ALTA — E É O PROBLEMA CENTRAL

Este é o achado que rebaixa o candidato, e é mais sério do que uma colisão com
produto de consumo:

> **"Régua" já é jargão consolidado no vocabulário do público-alvo, com outro
> significado.** "Régua de cobrança" e "régua de comunicação" são termos-padrão
> de e-commerce, CRM e marketing no Brasil — sequências automatizadas de
> mensagens antes e depois do vencimento de uma fatura. Serasa Experian, Vindi,
> Nuvemshop, Docusign, Dinamize e Pontaltech todas publicam conteúdo usando o
> termo.

O lojista de e-commerce — exatamente o público desta plataforma — **já usa a
palavra "régua" para significar automação de cobrança**. Um produto de gestão
chamado Régua seria constantemente confundido com uma ferramenta de régua de
cobrança. Isso é pior que ambiguidade genérica: é colisão de categoria **dentro
do próprio nicho**, e polui a busca com concorrentes que resolvem outro problema.

Some-se o ruído de e-commerce de papelaria (réguas físicas) na busca por
"régua".

### 🟡 Semáforo final: AMARELO

**Rebaixado de verde.** O INPI é o mais limpo da rodada (nenhum registro
concedido em nenhuma classe) e não há empresa de software homônima. Mas o
jargão "régua de cobrança" ocupa a palavra dentro do próprio nicho-alvo, o
LinkedIn está tomado pela ONG REGUA, e o domínio `.com.br` está registrado — com
a ressalva de que é o único com janela plausível de liberação.

---

## Tabela-resumo

| Nome | Web | `.com.br` | `.com` | INPI cl. 42 | Instagram | LinkedIn | Telefone | Ambiguidade | **Semáforo** |
|---|---|---|---|---|---|---|---|---|---|
| **Cardume** | 🔴 4+ empresas digitais | 🔴 ativo (redir. p/ .com) | 🔴 tomado | 🔴 **2 registros em vigor** | 🔴 tomado | 🔴 tomado | 🟢 bom | 🟡 moderada | 🔴 **VERMELHO** |
| **Sobra** | 🟡 QuantoSobra (mesmo segmento) | 🔴 registrado (sem site) | 🔴 tomado | 🟢 **livre** (2 pendentes cl. 35/36) | 🔴 tomado | 🔴 tomado | 🟢 excelente | 🔴 alta | 🟡 **AMARELO** |
| **Vera** | 🔴 Vera.Support (fintech) | 🔴 registrado (sem site) | 🔴 tomado | 🔴 **registro em vigor** + multiclasse pendente | ⚪ não verificado | 🔴 tomado | 🟢 excelente | 🔴 muito alta | 🔴 **VERMELHO** |
| **Norte** | 🟡 Norte Ventures, Nortesys | 🔴 registrado (sem site) | 🔴 tomado | 🟢 **livre** (mas cl. 35 fechada) | 🔴 tomado | 🟢 **livre (404)** | 🟢 excelente | 🔴 muito alta | 🟡 **AMARELO** |
| **Régua** | 🟢 nenhuma empresa homônima | 🟡 registrado, **`on_hold` vencido** | 🔴 tomado | 🟢 **o mais limpo** (0 concedidos) | ⚪ não verificado | 🔴 tomado (ONG REGUA) | 🟢 bom (acento) | 🔴 alta (jargão do nicho) | 🟡 **AMARELO** |

**Legenda:** 🟢 verificado livre · 🟡 risco conhecido · 🔴 verificado em conflito ·
⚪ não verificado

**Nenhum candidato saiu verde.** Os dois "verdes" que entraram na rodada (Cardume
e Régua) foram rebaixados: Cardume por conflito legal verificado, Régua por
colisão de jargão dentro do nicho.

---

## Os 3 melhores candidatos — trade-offs para a Juliana decidir

A decisão é sua. Abaixo o que cada um custa e o que cada um entrega.

### Régua — o mais limpo juridicamente, o mais sujo semanticamente

**A favor:** INPI verificadamente livre em todas as classes (nenhum registro
concedido no Brasil). Nenhuma empresa de software homônima. Duas sílabas.
Concreto e brasileiro. Alinhado ao posicionamento — "o que mede de verdade" é
literalmente a tese de honestidade numérica. E é o único cujo `.com.br` tem
janela plausível de liberação.

**Contra:** "régua de cobrança" é jargão consolidado no vocabulário exato do
público-alvo. Você passaria os primeiros dois anos explicando que não é uma
ferramenta de automação de cobrança. LinkedIn tomado pela ONG REGUA. Acento
cria descasamento com o domínio.

**Escolha Régua se:** você aceita gastar explicação no começo em troca da via
jurídica mais desimpedida — e se conseguir o domínio quando liberar.

### Sobra — o mais afiado conceitualmente, o mais frágil comercialmente

**A favor:** INPI livre na classe 42. Nomeia a dor com precisão cirúrgica —
"quanto sobrou?" é literalmente a pergunta que os integradores atuais não
respondem. Duas sílabas, impossível de esquecer, perfeito ao telefone.

**Contra:** QuantoSobra já é um ERP de gestão para lojas com mais de 1.500
clientes no varejo brasileiro — o conceito já tem dono no seu segmento. SEO
estruturalmente inviável (palavra de altíssima frequência). Conotação de "resto"
pode soar pequeno para o cliente de R$5M. Um pedido pendente na classe 35.

**Escolha Sobra se:** você acredita que a força do conceito compensa o SEO, e se
não incomoda operar na sombra semântica do QuantoSobra.

### Norte — o mais executivo, o mais genérico

**A favor:** classe 42 verificadamente livre. Handle de LinkedIn disponível
(único da rodada). Duas sílabas, tom executivo, bom para venda consultiva.

**Contra:** classe 35 (gestão de negócios) tem dois registros em vigor de
terceiro e três indeferimentos históricos — proteção provavelmente incompleta.
Norte Ventures é fundo de VC conhecido no ecossistema exato onde você circula.
Nortesys é um ERP adjacente. Ambiguidade de busca severa. Risco de leitura como
produto regional da Região Norte.

**Escolha Norte se:** o tom executivo importa mais que a proteção de marca
completa e você não pretende levantar investimento tão cedo.

### Recomendação de leitura, não de decisão

Se a prioridade for **segurança jurídica**, Régua é o caminho — é o único com o
INPI genuinamente aberto. Se a prioridade for **clareza de mensagem**, Sobra
comunica a tese em uma palavra como nenhum outro.

**Vale considerar uma sexta rodada.** Os cinco candidatos gastaram-se contra
domínios `.com.br` universalmente tomados — sintoma de terem sido escolhidos
todos como palavras únicas e comuns do português. Uma rodada com palavras reais
porém menos frequentes (ou compostos curtos) enfrentaria muito menos disputa de
domínio e de INPI.

---

## Passos manuais — só a Juliana pode fazer

### Prioridade alta

1. **Confirmar os achados do INPI com advogado de propriedade industrial.**
   A busca aqui foi feita na base pública e está atualizada até 11/08/2026, mas o
   próprio INPI adverte que o exame de mérito faz nova busca. Peça busca de
   anterioridade profissional para o nome finalista, cobrindo marcas
   foneticamente semelhantes e figurativas — o que a busca por elemento nominal
   não cobre. Classes a pedir: **42 e 35**.

2. **Monitorar `regua.com.br`.** Está `on_hold` com expiração vencida em
   20/05/2026. Verifique logada no registro.br a data prevista de liberação e
   configure acompanhamento. É a única chance real de domínio exato da rodada.

3. **Consultar WHOIS logada** no registro.br para os domínios dos finalistas —
   descobrir titular e avaliar se há disposição de venda. `sobra.com.br`,
   `norte.com.br` e `regua.com.br` estão registrados mas **sem site ativo**, o
   que às vezes indica domínio parqueado e negociável.

### Prioridade média

4. **Verificar manualmente os handles não verificados** (logada):
   `instagram.com/regua` e `instagram.com/vera`.

5. **Decidir o plano B de domínio.** Como todos os `.com.br` exatos estão
   tomados, considere as alternativas antes de fechar o nome: `<nome>.app.br`,
   `usar<nome>.com.br`, `<nome>app.com.br`, ou os sufixos que o próprio
   registro.br sugeriu (`.tec.br`, `.dev.br`, `.srv.br`). Um nome perfeito com
   domínio ruim custa caro no dia a dia.

6. **Registrar o domínio e depositar a marca no mesmo período.** Depositar a
   marca antes de anunciar publicamente o nome evita que um terceiro deposite
   primeiro.

### Não delegável

7. **A decisão do nome.** Este documento traz o que foi verificado e o que não
   foi. A escolha entre segurança jurídica (Régua), clareza de mensagem (Sobra) e
   tom executivo (Norte) é uma decisão de negócio, sua.

---

## Fontes consultadas

- INPI — busca de marcas: `https://busca.inpi.gov.br/pePI/` (dados atualizados
  até 11/08/2026)
- registro.br — API de disponibilidade: `https://registro.br/v2/ajax/avail/raw/`
- Verisign RDAP: `https://rdap.verisign.com/com/v1/domain/`
- [Cardume Digital — LinkedIn](https://br.linkedin.com/company/cardume) ·
  [cardume.digital](https://cardume.digital/) · [cardume.com](https://cardume.com/) ·
  [Agência Cardume](https://agenciacardume.com/) · [cardume.io](https://cardume.io/)
- [QuantoSobra — sistema de gestão para lojas](https://www.quantosobra.com.br/)
- [Vera.Support — soluções para fintech](https://vera.support/pt/solutions/fintech/) ·
  [Vera Solutions](https://verasolutions.org/br/)
- [Norte Ventures](https://www.norte.ventures/portfolio) ·
  [Norte Ventures no NeoFeed](https://neofeed.com.br/startups/no-norte-ventures-os-fundadores-de-startups-dao-as-cartas-nos-investimentos/) ·
  [Nortesys](https://www.nortesys.com.br/)
- Jargão "régua de cobrança":
  [Serasa Experian](https://www.serasaexperian.com.br/conteudos/regua-de-comunicacao/) ·
  [Vindi](https://blog.vindi.com.br/regua-de-cobranca/) ·
  [Nuvemshop](https://www.nuvemshop.com.br/blog/regua-de-cobranca/)
- [REGUA — Reserva Ecológica de Guapiaçu (LinkedIn)](https://www.linkedin.com/company/regua/)
