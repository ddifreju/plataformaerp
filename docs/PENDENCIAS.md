# Pendências — só a Juliana pode resolver

Este arquivo é escrito pelo `gerente-projeto` quando ele encontra algo que
não consegue resolver sozinho. Ele NÃO fica esperando — segue para outra
tarefa e registra aqui.

Revise este arquivo uma vez por dia.

---

## CNPJ e conexões com marketplaces — EM ABERTO (01/10/2026)

- [ ] **CNPJ da empresa (Simples Nacional)** — previsão: até 09/10/2026.
      Sem ele não dá para pedir acesso às APIs dos marketplaces, registrar
      domínio em nome da empresa nem emitir nota fiscal.
- [ ] **Domínio** — depende do CNPJ.
- Plano completo e papelada provável: `docs/integracoes/plano-conexoes.md`.


## Ferramentas da máquina — RESOLVIDO em 12/08/2026

- [x] **JDK 21** — Temurin 21.0.12 instalado
- [x] **Maven 3.9.9** — instalado. `mvnw` (Maven Wrapper) gerado no `backend/`,
      então o projeto agora é autossuficiente
- [x] **make 4.4.1** — instalado
- [x] **Node 24** — instalado (só necessário na Fase 3)
- [x] **Docker Desktop** — Engine 29.7.2 funcionando desde 13/08/2026.
      (A demora foi por sockets órfãos que o Windows não apagava; resolvido
      afastando `AppData\Local\Docker\run` e `docker-secrets-engine`.)

**Toolchain completo.**

- [x] **Tomcat não subia nesta máquina — RESOLVIDO em 13/08/2026.**
      A causa NÃO era antivírus: era **reserva de portas do Hyper-V/Docker**
      (o winnat reservava faixas que englobavam as portas efêmeras que o NIO
      do Tomcat usa para o par interno de sockets do *selector*).
      Corrigido pela Juliana, como administrador, com:
      `net stop winnat && net start winnat` e
      `netsh int ipv4 set dynamic tcp start=49152 num=16384`.
      O Tomcat agora inicializa normalmente na porta 8080.
      Com isso, validar o **frontend contra a API real** deixou de ser
      bloqueio — está liberado.

Estado da validação em 13/08/2026:
- **A suíte inteira passa contra Postgres real: 150 testes, 0 falhas, 0 erros**
- As 15 migrations aplicam num Postgres 16 + pgvector de verdade
- O molde de RLS está correto nas 14 tabelas, provado pelo teste sentinela
- Frontend: `build`, `lint` e 20 testes limpos

O que ainda não foi provado: as **fixtures de Mercado Livre e Bling**, que
continuam sendo hipótese até o primeiro payload verdadeiro. (O frontend contra
a API real ficou possível com o conserto do winnat acima — validação visual em
andamento em 13/08/2026.)

## Repositório remoto — RESOLVIDO

- [x] **GitHub**: `https://github.com/ddifreju/plataformaerp.git`
      Push inicial feito pela fundadora. Existe backup fora da máquina.
      **O push continua sendo dela** — eu commito localmente, ela publica.

## Credenciais e contas (resolva conforme a fase exigir)

Marque conforme for conseguindo. Credenciais externas atrasam a validação,
não a construção — cada item não resolvido é uma parte do sistema que segue
construída contra mock até a credencial chegar (ver "Como usar", no fim).

- [ ] **Chave de API de LLM** (Anthropic ou OpenAI) — sem isso, nenhuma
      funcionalidade de IA funciona
- [ ] **Conta de desenvolvedor Mercado Livre** — developers.mercadolivre.com.br
      App ID + Secret. Precisa de conta de vendedor para testar de verdade
- [ ] **Conta de desenvolvedor Shopee Open Platform** — se for integrar
- [ ] **Conta em ERP para testes** (Bling ou Tiny) — chave de API.
      Contas de teste às vezes exigem plano pago
- [ ] **WhatsApp Business API via BSP** — exige CNPJ, verificação do Meta
      Business e aprovação. Pode levar semanas
- [ ] **Domínio registrado**
- [ ] **VPS ou conta de cloud** — para quando sair do local
- [ ] **Chave HMAC para hash de documento de cliente**
      Variável `APP_DOCUMENTO_HMAC_CHAVE`. A tabela `cliente` guarda
      `documento_hash` = HMAC-SHA256 do CPF/CNPJ, nunca o documento em claro
      (SHA-256 puro seria quebrável por força bruta: o espaço de CPF é pequeno).
      Gere uma chave aleatória longa e guarde fora do banco e fora do git.
      **Trocar a chave depois invalida todos os hashes existentes** — decida uma
      vez e guarde bem. Sem ela o adaptador não consegue preencher a coluna.

## Decisões de negócio pendentes

- [ ] **Mandar nome de canal para um LLM externo (quando `ModeloAnthropic` existir)**
      `ModeloHeuristico` já extrai `canal` e `periodoRelativo` de texto livre
      hoje, mas de forma 100% determinística e local: casa o texto contra os
      canais do próprio tenant, e nada sai da máquina (ver Javadoc da classe).
      Quando existir chave de LLM e `ModeloAnthropic` entrar, colocar nome de
      canal (dado do tenant) dentro de um prompt significa mandar esse dado a
      um provedor de IA externo - é a exceção que a fundadora reservou para
      si (`docs/CONTEXTO-HANDOFF.md`, "provedores de IA externos"), não uma
      decisão técnica.
- [ ] **O texto INTEIRO da pergunta sai do país quando o LLM entrar**
      Maior que o item acima, e descoberto na auditoria da Fase 4. A lojista
      digita texto livre em `POST /api/pergunta`, e esse texto pode conter nome
      ou CPF do consumidor final dela ("quanto o Fulano, CPF tal, comprou?").
      Hoje nada sai da máquina. Com `ModeloAnthropic`, o texto **integral**
      precisa ir ao provedor para a interpretação funcionar — transferência
      internacional de dado pessoal de **titular terceiro**, que nunca teve
      relação com a plataforma. Não é o mesmo risco do nome de canal; é outra
      categoria de dado e outro titular. Precisa de base legal antes de ligar.
- [ ] **PII de consumidor final em `consulta_auditada.pergunta`**
      Mesmo sem LLM nenhum, esse texto livre já é gravado em toda chamada,
      numa tabela append-only e sem prazo de expurgo. A pendência de retenção
      logo abaixo cobre `executado_por` (dado de empregado do cliente); esta
      cobre dado de terceiro. Decidir junto: prazo, expurgo e base legal.
- [ ] **`papel` vira autorização de verdade? E quem vê o quê?**
      DONO/GESTOR/ANALISTA existe como campo e **não autoriza nada** — qualquer
      usuário autenticado alcança qualquer endpoint. A camada de pergunta
      herdou isso: um ANALISTA pode perguntar "onde a operação está travando" e
      receber a visão de gestor. Não é regressão (o endpoint direto já era
      assim desde a tarefa 19), mas é mais uma porta para o mesmo dado.
      A parte técnica é simples (matriz papel×operação e testes de negação);
      **quem pode ver qual número é decisão de produto**, e é por isso que está
      aqui e não no ESTADO.
- [ ] Nome definitivo da marca
- [ ] Nicho inicial: moda ou pet/suplementos
- [ ] Modelo e valores de precificação
- [ ] **Retenção e base legal da trilha de auditoria** (ver decisão 0012)
      A tabela `consulta_auditada` guarda `executado_por` — dado pessoal de
      empregado do cliente. Hoje é append-only e cresce para sempre. Antes de
      rodar com cliente real é preciso definir: por quanto tempo guardar, e sob
      qual base legal (provavelmente legítimo interesse ou obrigação de guarda
      de prova). É decisão de negócio/jurídica, não técnica.
      Não bloqueia as Fases 0 a 2.
- [ ] **Regime tributário do tenant** (decisão 0020)
      Enquanto não existir, o imposto é **lacuna declarada** e toda margem sai
      rotulada `COM_TETO`. Precisa do contador: regime (Simples/Presumido),
      anexo, RBT12, e se há produto com ICMS-ST ou PIS/COFINS monofásico —
      nesses casos o imposto já foi pago antes e **não pode ser deduzido de
      novo**, senão a margem sai subestimada.
- [ ] **O lojista antecipa recebíveis?**
      Se sim, qual a taxa. É a única lacuna que o sistema **não consegue
      detectar sozinho** (ver `docs/ESTADO.md`): sem essa resposta, a margem de
      quem antecipa sai superestimada em silêncio.
- [ ] **Retenção de `evento_ingerido.payload_bruto`**
      É o maior volume de dado pessoal do banco: o payload cru de cada pedido
      vindo do marketplace, com nome, endereço e contato do consumidor final do
      seu cliente. Guardado para poder reprocessar e para provar o que a fonte
      mandou. Hoje sem prazo de expurgo. Mesma natureza da pendência acima.
- [ ] **Consentimento de WhatsApp (opt-in)**
      Deliberadamente não modelado. Quando entrar, é tabela própria de
      consentimento com data, origem e texto aceito — não um booleano no
      cliente. Depende de definir o fluxo comercial.

## Página de Produtos (combinado com a Juliana em 07/10/2026)

O Bloco 1 foi entregue: rascunho, SKU automático, valores padrão, lixeira com
restaurar, clonar e aba Histórico. Ficam na fila:

- [ ] **Bloco 2: variações e kits.**
      - Tipo de variação aprendido: o que a pessoa digitar entra sozinho na
        lista de Configurações.
      - Transformações com confirmação, mostrando variações e anúncios
        afetados:
        - variação ↔ simples;
        - simples → variação, num pai novo ou num pai existente;
        - variação → produto independente.
      - Editar a variação sozinha, escolhendo quais campos o pai repassa.
      - Custo do kit recalculado quando o custo de um componente muda.
      - Preço sugerido do kit: soma dos componentes menos um desconto.
      - Kit com variações (cada variação é um kit).
      - Envio do kit como kit para o marketplace (junto com as integrações).
- [ ] **Bloco 3: organização.**
      - Cadastro de marcas com detector de repetidas e unificação.
      - Unificar produtos duplicados, mantendo o histórico.
      - Localização no estoque.
      - Sugestão de NCM pela tabela oficial e de CEST pelo NCM.
      - CEST em lote por NCM.
- [ ] **Bloco 4: planilha completa.**
      - Importar todos os campos, com variações pelo SKU do pai, kits e
        imagens por link.
      - Prévia antes de confirmar e botão de desfazer.
      - Exportar, editar no Excel e subir de volta.
- [ ] **Fabricado, matéria-prima, lote e validade: entram juntos.**
      - Público: artesanato, impressão 3D e cosméticos próprios.
      - Estrutura de produção: o custo vem da matéria-prima, não do
        fornecedor.
      - Esse custo entra na calculadora e no painel de quem produz.
      - Lote e validade na entrada, na saída (vence primeiro, sai primeiro) e
        na NF-e.
- [x] **Quem apaga produto de vez e muda a configuração de SKU** (respondido
      pela Jéssica em 07/10/2026): de fábrica, dono e gestor (como já está).
- [ ] **Permissões liberadas pelo gestor.** A Jéssica quer que o dono e o
      gestor possam liberar ações para outras pessoas (ex.: um
      "administrativo" poder apagar produto de vez). Hoje a permissão é fixa
      por cargo no código. Proposta enviada a ela; aguardando o escopo.
- [x] **Anunciar por loja** (07/10/2026, decisão 0036): lojas do cliente em
      Integrações, passo a passo com conferência e coluna "Cadastro" com o que
      falta. Marketplaces: Mercado Livre, Shopee, TikTok Shop e AliExpress.
- [ ] **Anunciar sem recusa, com a conexão real** (pedido forte da Jéssica):
      limites por categoria e loja vindos do marketplace, lista de categorias
      para escolher, teste do anúncio antes de enviar e erro traduzido em
      instrução. Detalhes na decisão 0036.
- [ ] **Bloqueio de pendências no envio.** Ligar `RadarProdutos.pendencias()`
      no envio ao marketplace e na emissão de NF-e, quando essas funções
      existirem. Hoje o cadastro salva incompleto e só marca `incompleto`.

## Como usar

Enquanto uma credencial não existe, o gerente constrói contra **mock**:
adaptador com payload gravado, teste passando, interface funcionando com
dado falso. Quando a credencial chegar, troca o mock pelo real.

Isso significa que a falta de credencial atrasa a VALIDAÇÃO, não a
CONSTRUÇÃO. O sistema é construído mesmo assim.
