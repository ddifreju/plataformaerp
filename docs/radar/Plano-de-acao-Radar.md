# Plano de ação para construir o Radar de ponta a ponta

Inspeção em 30 de setembro de 2026. Projeto examinado: `C:\Users\Juliana Trabalho\Documents\ECP\kit-inicio`. Referência local: commit `6eb379b`, de 14 de setembro de 2026. O diretório de trabalho estava sem alterações rastreadas. Este documento é um plano de implementação; não representa implantação, homologação fiscal ou aprovação de integrações externas.

O Radar será um **Commerce Operating System para e-commerce**, conforme o escopo aprovado e retransmitido integralmente nesta tarefa. O ERP independente é sua fundação operacional; a IA participa da arquitetura desde o início e trabalha sobre toda a operação. A recomendação é aproveitar o código existente, corrigir as fundações que ainda impedem operação real e entregar primeiro um ciclo completo em um marketplace. Em seguida, provar esse mesmo ciclo em um segundo canal, antes de ampliar automações e canais.

O produto combina três responsabilidades: **System of Record**, que registra o que aconteceu; **System of Intelligence**, que explica resultados e oportunidades; e **System of Action**, que executa trabalho autorizado e verifica o desfecho. Sua jornada é perguntar → compreender → recomendar → executar. O usuário enxerga um produto único, com experiências por cargo e módulos internos compartilhados.

O primeiro marco comercial de ERP precisa incluir cadastro e importação, anúncios, pedidos, estoque, NF-e, expedição, financeiro, lucro auditável, precificação e permissões. Fiscal e estoque não ficam relegados a uma versão distante. Tiny e Bling ficam como opções futuras de migração, sem dependência operacional no V1.

## 1 Como usar este plano

As seções 2 a 5 registram o inventário, evidências, lacunas e decisões de arquitetura. As seções 6 a 10 especificam execução, contratos, qualidade e implantação. As seções 11 a 14 tratam riscos, decisões pendentes, cobertura do escopo e a primeira fila de trabalho.

Cada fase deve virar um épico de implementação. Cada item numerado deve virar uma tarefa, com responsável, evidência de conclusão e teste de aceite. As fases têm dependências; a posição na lista não impede investigação antecipada de homologação, fiscal, experiência do usuário e dados externos.

Legenda usada neste documento:

- **Constatado:** observado nos arquivos ou em uma execução realizada nesta inspeção.
- **Histórico:** relatado por documentação ou artefatos de execução anteriores; não revalidado agora.
- **Proposto:** decisão de implementação recomendada, sujeita aos critérios e dependências abaixo.
- **Dependência externa:** exige conta, autorização, contrato, homologação ou informação que o repositório não comprova.

Não existe base para prometer datas exatas ou percentual de conclusão do produto inteiro. Há código relevante pronto, mas quantidade de classes e testes não mede cobertura do ERP desejado.

## 2 Inventário do projeto atual

### 2.1 Estrutura e arquivos

O diretório `ECP` contém o projeto em `kit-inicio`. O Git lista 393 arquivos rastreados. Foram identificados 171 arquivos Java de produção, 50 arquivos Java em testes e 16 migrações de avanço acompanhadas por 16 scripts de desfazimento. O arquivo complementar `Inventario-arquivos-Radar.csv` relaciona os arquivos rastreados, seu tamanho e grupo. Bibliotecas instaladas, saídas de compilação e segredos locais não são tratados como código-fonte desse inventário.

| Área | Arquivos e diretórios relevantes | Função constatada |
|---|---|---|
| Orientações | `README.md`, `CLAUDE.md`, `LEIA-PRIMEIRO.md` | Convenções e orientações de trabalho, parcialmente desatualizadas |
| Estado | `docs/ESTADO.md`, `docs/PENDENCIAS.md`, `docs/CONTEXTO-HANDOFF.md` | Histórico de execução, limitações e próximos passos |
| Decisões | `docs/decisoes/0001` até `0034` | Arquitetura, tenant, cálculo, sessão, IA e staging |
| Backend | `backend/pom.xml`, `backend/src/main/java/com/plataforma` | Aplicação Java única organizada por domínio |
| Banco | `backend/src/main/resources/db/migration` e `db/undo` | Schema PostgreSQL, isolamento e evolução |
| Frontend | `frontend/package.json`, `package-lock.json`, `src/app`, `src/lib`, `src/componentes` | Interface Next.js e testes de funções auxiliares |
| Integração | `integracao/AdaptadorMercadoLivre.java`, `AdaptadorBling.java`, `docs/integracoes` | Tradução de payloads e mapeamentos; sem conector HTTP completo encontrado |
| Ingestão | `ingestao/ServicoIngestao.java` | Idempotência, registro de eventos e persistência do modelo canônico |
| Margem | `margem/`, `docs/fiscal/regras-de-margem.md` | Regras monetárias, custos, lacunas e memória de cálculo |
| IA atual | `pergunta/`, `docs/avaliacao-camada-de-ia.md` | Interpretação restrita e execução determinística de consultas |
| Infra | `Makefile`, `infra/docker-compose*.yml`, `Caddyfile`, Dockerfiles | Desenvolvimento e preparação de staging |
| Demonstração | `infra/dados-demo.sql`, fixtures em `backend/src/test/resources` | Dados fictícios para validação local |
| Configuração de agentes | `.claude/agents`, `.claude/skills`, `.claude/settings.json` | Recursos de desenvolvimento; não constituem agentes do produto Radar |

O inventário de caminhos é completo para os arquivos rastreados; a revisão de conteúdo foi direcionada aos pontos de entrada, modelos, segurança, ingestão, cálculo, configuração e documentação. Não foi uma auditoria linha a linha das 171 classes.

### 2.2 Stack e dependências declaradas

| Componente | Versão ou tecnologia observada | Observação |
|---|---|---|
| Backend | Java 21, Spring Boot 3.3.13 | Monólito com um `pom.xml` |
| Persistência | Spring Data JPA, Hibernate, PostgreSQL | `BigDecimal` no cálculo; dinheiro serializado como texto |
| Segurança | Spring Security, BCrypt com custo 12, sessão no servidor | Tenant derivado da identidade autenticada |
| Migrações | Flyway 10.22.0 | Execução separada do boot e usuário de banco separado |
| Driver | PostgreSQL JDBC 42.7.13 | Versão explicitada no manifesto |
| Integração | Apache Camel 4.8.9 declarado | Não foram encontradas rotas `RouteBuilder` em produção |
| Testes backend | Spring Boot Test, JUnit, Testcontainers 1.21.4 | Testes de banco dependem de Docker |
| Frontend | Next.js 16.3.0, React e React DOM 19.2.8 | Manifesto difere do README que ainda menciona Next 15 |
| Estilo e linguagem | Tailwind 4, TypeScript 5 | Faixas de versão no manifesto e lockfile presente |
| Qualidade frontend | ESLint 9 e testes nativos do Node | Não foi encontrada suíte de navegador ponta a ponta |
| Banco local | PostgreSQL 16 na imagem `pgvector/pgvector:pg16` | pgvector disponível na infraestrutura, sem uso funcional comprovado |
| Infraestrutura | Docker Compose, Caddy 2, Adminer opcional | Tags de imagens ainda precisam de política de atualização e fixação |

Essas são as versões declaradas pelo projeto, não uma afirmação de que continuam suportadas, seguras ou recomendadas. A fase 0 inclui auditoria de dependências diretas e transitivas, imagens e licenças antes de exposição pública.

Na máquina, Node 24.19.0 e um JDK 21 foram localizados. Docker não estava no PATH; foi localizado um executável do Docker Desktop, mas sua execução retornou acesso negado nesta sessão. Não foi possível confirmar containers ativos ou a disponibilidade do PostgreSQL. Maven possui wrapper no projeto; a suíte backend não foi reexecutada.

### 2.3 Banco e modelo existente

As migrações declaram 16 tabelas: `tenant`, `consulta_auditada`, `canal`, `produto`, `variacao`, `cliente`, `pedido`, `item_pedido`, `devolucao`, `item_devolucao`, `custo`, `conversa`, `mensagem`, `evento_ingerido`, `taxa_canal` e `usuario`.

Existem mecanismos de RLS no PostgreSQL, propagação de tenant e chaves compostas que incluem tenant. O usuário de runtime é separado do usuário de migração. Esses mecanismos são boas fundações, mas precisam ser estendidos a cada tabela, arquivo, cache, tarefa assíncrona e ferramenta de IA nova.

O catálogo atual já distingue produto e variação, mas também carrega `canal_id`, `id_externo` e dados de origem. Falta explicitar o produto mestre pertencente ao lojista e os vínculos de cada anúncio/variação externa. Não se deve unir produtos automaticamente apenas por título, SKU textual ou GTIN.

O modelo de custos já contempla mercadoria, embalagem, frete, frete reverso, comissão, tarifa fixa, pagamento, parcelamento, antecipação, imposto, Ads, descontos, reembolsos, armazenagem e tarifa administrativa. Isso não prova que existam capturas reais de todas essas informações. O motor subtrai blocos N0 até N3 e conserva IDs de custos usados no cálculo.

`evento_ingerido` não é ainda um arquivo histórico imutável: o `UPSERT` de `ServicoIngestao` substitui hash e payload quando chegam mudanças. Para auditoria histórica, separar recepções imutáveis, versões normalizadas e estado de processamento.

Não foram encontradas tabelas dedicadas a depósitos, movimentos e reservas de estoque, anúncios, contas de marketplace e tokens OAuth, NF-e, remessas, compras e fornecedores, contas a pagar/receber, liquidações, campanhas, permissões granulares, ações de agentes ou assinaturas comerciais.

### 2.4 APIs e telas existentes

| Método e rota | O que existe |
|---|---|
| `POST /api/login` | Autenticação via filtro Spring Security |
| `POST /api/logout` | Encerramento da sessão configurado na segurança |
| `GET /api/sessao` | Dados da sessão |
| `GET /api/canais` | Canais e escopo; DTO ainda sem contagem de pedidos |
| `POST /api/canais/{id}/escopo` | Declaração explícita de fonte ou espelho |
| `POST /api/margem/periodo` | Margem de um canal com consulta auditada |
| `POST /api/margem/periodo/consolidado` | Consolidação protegida contra canais sobrepostos |
| `GET /api/painel/gestor` | Visão operacional |
| `GET /api/painel/analista` | Pendências |
| `POST /api/pergunta` | Consulta por intenção validada |
| `GET /actuator/health` e `/actuator/info` | Endpoints habilitados de diagnóstico |

Telas identificadas: `/login`, `/resultado`, `/operacao`, `/pendencias`, `/perguntar` e `/canais`, além de página inicial e layouts compartilhados. Existem componentes para dinheiro, confiança, lacunas e números citados.

Não foram localizados controllers operacionais de catálogo, importações, anúncios, pedidos, estoque, fiscal, financeiro tradicional ou inbox. Ter entidades de pedido e conversa não equivale a ter seus fluxos operacionais disponíveis.

### 2.5 Testes e estado de execução

| Verificação | Resultado e alcance |
|---|---|
| Testes frontend executados nesta inspeção | 44 passaram, sem falhas ou testes ignorados |
| Lint frontend executado nesta inspeção | Passou |
| Relatórios backend existentes | 46 arquivos XML somam 300 testes, zero falhas, erros ou ignorados; são artefatos anteriores |
| Documentação de setembro | Relata testes contra PostgreSQL, build das duas imagens e validação do Caddy |
| Backend e banco nesta inspeção | Não executados; Docker inacessível pela tentativa realizada |
| Build atual e navegador | Não revalidados nesta inspeção |
| Staging ou produção | Não inspecionados ao vivo; documentação registra staging ainda pendente |
| Marketplaces, emissão e repasses reais | Sem comprovação neste trabalho |

Os 44 testes atuais verificam principalmente funções de apresentação e comportamento auxiliar. Não comprovam publicação de anúncio, emissão de nota, sincronização de saldo, RBAC ou operação no navegador.

## 3 Lacunas e mudanças prioritárias

| Prioridade | Constatação | Consequência e ação |
|---|---|---|
| P0 | A configuração de segurança usa `anyRequest().authenticated()`; não foram encontradas regras por papel | Implementar autorização em serviços e rotas antes de clientes reais; esconder botão não resolve |
| P0 | Adaptadores ML/Bling sem ciclo real de OAuth, coleta e publicação | Implantar plataforma de conectores e homologar um canal completo |
| P0 | Produto ainda guarda identidade de origem e não existe Listing | Migrar para produto mestre, SKU e vínculos externos sem perder histórico |
| P0 | Não há ledger de estoque e reservas | Construir antes de liberar publicação com saldo e recebimento operacional |
| P0 | Não há fluxo NF-e e expedição | Integrar emissor e logística antes de chamar o produto de ERP operacional |
| P0 | Cálculo auditado não equivale a ledger de liquidações | Acrescentar receita, recebíveis, repasses, reversões e conciliação |
| P0 | Atualização sobrescreve payload anterior | Criar arquivo bruto versionado e reprocessamento seguro |
| P0 | CSRF está desabilitado com hipóteses de JSON e mesma origem | Rever antes de upload multipart, callbacks e novos comandos; adicionar proteção adequada à sessão |
| P1 | CPF/CNPJ de cliente está projetado como hash | Para NF-e, criar armazenamento mínimo cifrado e controlado dos dados fiscais necessários; hash não recupera documento |
| P1 | DRE e resultado dependem de custos/impostos/Ads fornecidos | Exibir completude por componente; impedir falso lucro final |
| P1 | Consulta de margem busca itens e custos dentro do laço de pedidos | Medir custo de consultas e evoluir para consultas em lote/projeções |
| P1 | Divergência de repasse tem caminho que registra aviso em log | Tornar exceção visível no produto, com responsável e resolução |
| P1 | Não foi encontrado pipeline de CI no repositório | Criar verificações reprodutíveis e bloqueios de publicação |
| P1 | Infraestrutura descrita, sem prova atual de recuperação | Testar staging, restauração e rollback antes de produção |
| P2 | Marca continua como `Plataforma` | Aplicar identidade Radar e revisar documentos depois da definição funcional |
| P2 | `GET /api/canais` sem quantidade de pedidos | Fechar tarefa 35, incluindo casos zero e isolamento |

A documentação local contém trechos contraditórios sobre imagens já construídas e trechos antigos sobre ferramentas ausentes. Atualizá-la a partir de evidências atuais. A pendência que afirma que nenhuma IA funciona sem chave também precisa distinguir a heurística local que já existe de modelos externos ainda não integrados.

As decisões antigas que priorizam Bling e inteligência conectada devem ser substituídas por novas decisões registradas, preservando seu histórico. A solicitação atual já define o Radar como ERP independente; não é necessário reabrir essa escolha para escrever o plano.

## 4 Arquitetura alvo e contratos fundamentais

### 4.1 Estrutura modular recomendada

Manter Java, Spring e Next.js inicialmente. Organizar um monólito modular com limites explícitos, serviços de domínio e tarefas assíncronas. Não iniciar uma migração para microserviços apenas por antecipação de escala. Separar processos de API e workers quando necessário; extrair serviços somente com gargalo, necessidade de isolamento ou equipe responsável demonstrados.

| Domínio | Responsabilidade | Dependências principais |
|---|---|---|
| Radar Core | Organizações, filiais, identidades, RBAC, assinatura e auditoria | Base comum |
| Radar ERP | Catálogo, estoque, compras, fornecedores, fiscal e financeiro | Core e Data Core |
| Radar Commerce | Contas, anúncios, OMS, logística, preço e conectores | ERP e Data Core |
| Radar Profit | Ledger, CMV, taxas, atribuição, repasses e cálculo | Pedidos, estoque, fiscal e fontes financeiras |
| Radar Service | Inbox, cliente, reclamações e devoluções | Commerce e permissões |
| Radar Market | Concorrência, keywords, tendências e comparáveis | Fontes permitidas e catálogo |
| Radar Studio | Marca, imagens e conteúdo por canal | Catálogo, regras de canal e armazenamento |
| Radar Intelligence | Painéis, métricas, previsões e Mission Control | Projeções verificadas dos domínios |
| Radar AI | Chat, voz, agentes e orquestração | Ferramentas tipadas, Policy/Action Engine e auditoria |

Esses são os nove domínios aprovados. **Radar Data Core** é a fundação transversal de eventos, ledger, identidades, proveniência e qualidade; não exige um décimo produto comercial. AI Gateway, Agent Registry e Policy/Action Engine também são infraestrutura compartilhada desde a fase 1. O registro começa com Import Agent, Product Agent e Business Copilot; os demais agentes são adicionados à medida que seus domínios e ferramentas são homologados.

Fluxo operacional: produto/SKU → anúncio por conta e canal → pedido → reserva → NF-e → separação/expedição → eventos financeiros → conciliação → resultado → insight → ação autorizada.

Fluxo de dados: fonte → recepção durável → payload bruto versionado → normalização → serviço de domínio → transação e outbox → workers → projeções e APIs. Uma outbox registra, na mesma transação, o evento que deverá ser enviado depois, evitando perder a sincronização quando o processo cai.

### 4.2 Decisões técnicas propostas

1. PostgreSQL permanece fonte transacional. Arquivos, XMLs, DANFEs e imagens ficam em armazenamento de objetos privado, com versões, política de retenção e URLs temporárias.
2. Implementar inbox/outbox e fila durável com retries, atrasos e quarentena. Uma fila apoiada no PostgreSQL pode atender o primeiro volume; a decisão de broker separado depende de medições. Camel só permanece no caminho operacional se reduzir complexidade concretamente.
3. Todo consumidor deve tolerar entrega repetida. Chaves de idempotência devem incluir tenant, conta de canal, operação e identificador externo. Não prometer execução exatamente uma vez entre sistemas externos.
4. Modelar horário do fato, recebimento e processamento separadamente; armazenar instantes em UTC e apresentar na zona da organização. Períodos financeiros precisam de regra de competência explícita.
5. Valores usam decimal exato, moeda, escala e arredondamento declarado. Rateios preservam o total até o centavo; porcentagens agregadas são ponderadas, não média simples de margens.
6. APIs internas recebem tenant da identidade; workers recebem contexto verificado da tarefa. Cache, exportação, busca, arquivos e recuperação semântica compartilham a mesma fronteira de isolamento.
7. Separar cliente contratante, organização/CNPJ, filial e conta de marketplace. Um usuário pode ter memberships em organizações distintas; seleção de organização nunca concede acesso por si só.
8. Estados externos são traduzidos e preservados, sem forçar um único status para pedido, pagamento, nota, envio e devolução.
9. Criar OpenAPI, contratos de eventos versionados e política de compatibilidade. Paginação, filtros, limites, códigos de erro e correlation ID são padrões comuns.
10. Conservar o núcleo determinístico do cálculo existente. IA consulta ferramentas; não escreve SQL arbitrário, não calcula dinheiro por geração de texto e não acessa credenciais de marketplace.

### 4.3 Modelo de dados a acrescentar

| Grupo | Entidades propostas | Invariantes obrigatórios |
|---|---|---|
| Identidade | organização, filial, membership, papel, permissão, escopo | Tenant obrigatório; autorização por organização, filial, canal e ação |
| Conectores | conta_marketplace, conexão, referência_credencial, capacidade, cursor_sync | Conta externa única no contexto correto; segredo fora de DTO/log |
| Evidência | recepção_bruta, versão_normalizada, execução_sync, outbox, tentativa | Recepção imutável; replay identificado; erro não apaga origem |
| Catálogo | produto_mestre, sku, atributo, mídia, kit, vínculo_externo | SKU interno estável; correspondências explícitas; histórico preservado |
| Anúncios | listing, listing_sku, revisão, operação_lote, resultado_item | Operação por conta e canal; estado desejado e observado separados |
| Estoque | depósito, posição, movimento, reserva, transferência, inventário | Reserva não é baixa física; saldo reconstituível por movimentos |
| Compras | fornecedor, produto_fornecedor, compra, recebimento | Recebimentos parciais e custo de aquisição rastreáveis |
| OMS | pedido, item, snapshot, pagamento, remessa, evento_status | Estado monotônico quando aplicável; mesma venda não duplica |
| Fiscal | perfil_fiscal, documento_fiscal, tentativa, evento_fiscal | Identidade da nota persistida antes do envio; consulta após timeout |
| Financeiro | conta, título, parcela, lançamento, liquidação, lote_repasse, conciliação | Payout não vira segunda receita; reversão não apaga histórico |
| Profit | versão_custo, regra_taxa, atribuição, snapshot_resultado, fechamento | Fonte e versão de cada componente recuperáveis |
| Atendimento | ticket, conversa, mensagem, SLA, atribuição, consentimento | Sem resposta duplicada; contexto restrito ao cliente e tenant |
| Inteligência | métrica, alerta, tarefa, observação_concorrente, campanha | Dado observado separado de inferência e projeção |
| IA e ação | agente, ferramenta, política, proposta, aprovação, execução, auditoria | Escopo mínimo; aprovação vinculada a parâmetros e versão |
| Comercial | plano, entitlement, medição_uso, limite, cobrança | Uso e orçamento por tenant; suspensão sem perda de dados |

Não criar todas as tabelas vazias no primeiro sprint. Fixar os contratos e adicionar schema conforme a fase consumidora, com migração e teste de isolamento.

## 5 Cobertura de canais e pesquisa competitiva

### 5.1 Ordem dos canais

Proposta de ordem: Mercado Livre → Shopee → TikTok Shop → SHEIN. Mercado Livre aproveita o adaptador existente, mas a escolha final do primeiro piloto deve considerar a conta real disponível. A disponibilidade de permissões pode alterar a ordem, sem excluir nenhum dos quatro canais previstos. Amazon, Magalu e demais canais ficam na expansão posterior.

Para cada canal, manter uma matriz por país, conta, categoria e versão da API. Cada célula terá os estados não investigado, documentado, autorizado, testado em homologação e comprovado no piloto.

| Capacidade a homologar | Evidência exigida |
|---|---|
| Conectar conta | Autorização oficial, escopos, expiração, renovação e revogação |
| Importar catálogo | Paginação, variações, imagens, atributos, vínculos e limites |
| Publicar e gerir | Criar, consultar resultado, editar preço/conteúdo, pausar e reativar quando permitido |
| Estoque | Atualizar saldo, depósitos/fulfillment, conflitos e atraso observado |
| Pedidos e logística | Eventos, coleta incremental, cancelamentos, pacotes, etiquetas e rastreio |
| Fiscal | Forma aceita de transmitir chave, XML ou dados da NF-e |
| Financeiro | Taxas, subsídios, frete, devoluções, retenções, ajustes e repasse |
| Ads | Permissão específica, campanhas, gasto e atribuição disponível |
| Atendimento | Perguntas, comentários, mensagens, anexos e possibilidade de resposta |
| Concorrência | Dados públicos/licenciados permitidos, limites e condições de uso |

Não presumir que acesso a pedidos também autoriza mensagens, Ads ou dados financeiros completos. Priorizar API/webhook oficial, consulta periódica oficial, relatórios oficiais e importação. Automação de navegador não será dependência central do produto.

O Mercado Livre mantém documentação de notificações para recursos operacionais; o conector deverá traduzir notificações em consulta e processamento durável conforme o contrato homologado. A página foi localizada na pesquisa, mas sua abertura integral falhou; comportamento exato será confirmado no spike de integração. [Documentação de notificações do Mercado Livre](https://developers.mercadolivre.com.br/produto-receba-notificacoes).

### 5.2 Benchmark que orienta o backlog

A pesquisa atual confirma que IA e integração já fazem parte do posicionamento de concorrentes. As evidências abaixo são documentação dos fornecedores, não testes independentes de qualidade nem garantia de liberação para toda conta.

| Referência | Evidência consultada | Implicação proposta para Radar |
|---|---|---|
| Olist | ERP, integrações e Lis com consultas e ações sobre a operação | Comparar experiência do ciclo completo e rastreabilidade, não apenas existência de chatbot. [FAQ oficial](https://olist.com/pt-br/faq/) |
| Bling | Assistente com consultas, ações e importação de planilhas; artigo sinaliza testes controlados | Medir limites reais de importação e qualidade da supervisão. [Central de ajuda](https://ajuda.bling.com.br/hc/pt-br/articles/40765473905815-Como-o-Assistente-de-IA-do-Bling-transforma-a-gest%C3%A3o-do-seu-neg%C3%B3cio) |
| ANYMARKET | MIA para enriquecimento de títulos e descrições | Listing Agent deve fechar o ciclo com desempenho observado e margem. [Documentação da MIA](https://suporte.anymarket.com.br/hc/pt-br/articles/51498432723347-MIA-A-INTELIG%C3%8ANCIA-ARTIFICIAL-do-ANY-O-QU%C3%8A-%C3%89-COMO-UTILIZAR) |
| Base | IA para conteúdo, atributos e imagens, incluindo geração em massa | Incluir qualidade de cadastro, regras de canal e revisão em lote. [Central de ajuda](https://base.com/pt-BR/ajuda/knowledgebase/inteligencia-artificial-na-base/) |

Gestor Seller, Oquesobra, Preço Certo, JoomPulse, Linnworks e soluções de inbox citadas na conversa entram no benchmark aprofundado da fase 0 e das fases correspondentes. Suas funcionalidades atuais não foram revalidadas integralmente nesta inspeção.

Executar o mesmo roteiro de demonstração por concorrente: importar catálogo, publicar, reconciliar uma devolução parcial, explicar lucro por SKU, restringir um atendente e aprovar uma ação de IA. Registrar fonte/data, plano, disponibilidade no Brasil, limitações, custo total e evidência de uso. Pontuar 0 a 3: ausente, manual, integrado, automatizado com comprovação. Marcar desconhecido quando faltar evidência.

Não prometer paridade irrestrita com todos os ERPs. Preservar tudo que atende à operação de e-commerce definida: devoluções, kits, multiestoque, compras, fulfillment, conciliação, conteúdo e comunicação. PDV, manufatura, folha de pagamento e ERP genérico não entram automaticamente. A tese de diferenciação a validar é resultado financeiro explicável ligado a ações com controle e medição de benefício.

## 6 Roadmap completo de implementação

### Fase 0 Consolidar escopo e estabelecer uma base verificável

**Depende de:** acesso de leitura ao projeto, já realizado. **Responsáveis propostos:** produto e engenharia; fiscal/contabilidade para critérios tributários.

1. Atualizar README, ESTADO, PENDENCIAS e decisões para Radar independente; distinguir histórico e prova atual. Registrar substituição da prioridade Bling, sem apagar adaptador ou decisões antigas.
2. Transformar este plano em backlog com IDs e dono; definir operação piloto, categorias, volume diário, pico, número de SKUs, contas e filiais.
3. Reproduzir backend, frontend e PostgreSQL em ambiente isolado. Reexecutar 300 testes, build, lint, contratos e testes de RLS. Confirmar staging, cookies e healthcheck.
4. Criar CI com dependências fixadas, auditoria de vulnerabilidades/licenças, verificação de segredos, testes e imagens identificadas por digest.
5. Registrar baseline de latência, consultas SQL, memória, tempo de ingestão e execução de margem. Confirmar e medir o padrão de consultas por pedido.
6. Concluir contagem de pedidos por canal e tornar divergências financeiras visíveis na fila de pendências.
7. Abrir solicitações de apps de marketplace e pesquisar emissores fiscais desde já. Montar matriz de capacidades e dataset do piloto com acesso autorizado.
8. Inventariar anexos de referência da conversa: planilha de vendas, estudo DOCX, áudio e kit de marca. Verificar versões e mapear colunas/processos antes de usá-los como contrato. Nesta inspeção, a recuperação textual da conversa foi limitada pelo leitor e os anexos não foram auditados integralmente; nenhum campo não lido é tratado como requisito confirmado.
9. Executar benchmark funcional e fechar critérios de sucesso do piloto: exatidão, cobertura, tempo de operação e custo de servir.

**Aceite:** ambiente reproduzível; resultados datados; nenhum defeito crítico de isolamento aberto; backlog cobre a matriz da seção 13; dependências externas têm dono e status. **Testes:** banco limpo e banco atualizado, login/logout, tenant adversário, casos existentes, navegador nos seis fluxos atuais.

### Fase 1 Identidade permissões e auditoria de ações

**Depende de:** fase 0. **Entrega:** base segura de operação.

1. Modelar empresa/organização, filiais, memberships e papéis. Migrar usuário sem permitir associação implícita entre empresas.
2. Implantar convite, ativação, recuperação de acesso, encerramento/revogação de sessão, limites de login e MFA para perfis administrativos e ações sensíveis.
3. Implementar RBAC com escopo por filial/canal e permissões de campo: ver custo, margem, dados fiscais e dados pessoais são capacidades distintas.
4. Aplicar autorização no backend, serviços, exportações, ferramentas de IA e tarefas; auditar tentativas negadas.
5. Criar audit log de alterações com ator humano/agente, organização, recurso, antes/depois redigido, motivo, correlação, política e resultado.
6. Redesenhar proteção CSRF para comandos e uploads com sessão, mantendo cookies seguros, CORS restrito e validação de origem aplicável.
7. Criar início do Policy/Action Engine: proposta, simulação, avaliação de permissão, aprovação vinculada à ação, execução e resultado. Operações manuais e IA usam a mesma passagem.
8. Planejar retenção por categoria, acesso de suporte temporário auditado, exclusão/anonimização e exportação de dados do cliente.
9. Criar **AI Gateway desde esta fase**, com interface de provedor, seleção de modelo por tarefa, resposta estruturada, orçamento, timeout, redaction e telemetria. Preservar a heurística atual como implementação local; habilitar provedor externo somente após a definição de dados e orçamento permitidos.
10. Criar **Agent Registry desde esta fase**, incluindo agente, versão, finalidade, ferramentas tipadas, permissões, nível de autonomia, avaliação e responsável. Registrar Business Copilot somente de leitura e os contratos de Import Agent e Product Agent, sem dar acesso direto ao banco ao modelo.

**Aceite:** um atendente não acessa margem por URL, API, exportação ou chat; mudança de papel revoga autorização; dois tenants com IDs semelhantes permanecem isolados. **Testes:** matriz permitir/negar por papel, IDOR, CSRF, sessões revogadas, arquivos e jobs entre tenants, ausência de segredos no log.

### Fase 2 Plataforma de integração e evidência imutável

**Depende de:** fases 0 e 1. **Entrega:** receber e enviar dados de forma recuperável.

1. Separar tipo de marketplace, conta de vendedor, conexão e escopos. Guardar tokens cifrados ou em cofre, com rotação e revogação.
2. Implementar OAuth oficial por fornecedor, validação de state, prevenção de troca indevida de conta e renovação concorrente segura.
3. Criar recepção durável de webhook, validação de autenticidade disponível por canal, anti-replay e resolução segura da conta.
4. Armazenar cada versão de origem sem sobrescrita; guardar hash, versão do adaptador, tempos, origem, tenant e referência externa.
5. Adicionar outbox/inbox, retries exponenciais com jitter, limites por conta, circuit breaker, timeout, dead-letter e replay supervisionado.
6. Implementar carga inicial paginada e sincronização incremental com cursor, janela sobreposta e backfill. Retomar após interrupção sem perder nem duplicar venda.
7. Criar mapa explícito de identidades e proveniência, preservando a proteção atual contra somar espelhos como vendas distintas.
8. Separar evento recebido, estado corrente e histórico; reconciliar notificações fora de ordem consultando versão/estado oficial quando necessário.
9. Criar painel de conexões, último sucesso, atraso, permissão ausente, revogação e reprocessamento.

**Aceite:** interrupção e reinício recuperam trabalho; evento duplicado não duplica efeito; conta revogada gera alerta; todos os resultados possuem origem recuperável. **Testes:** duplicidade, ordem invertida, timeout após sucesso externo, 429, 5xx, payload inesperado, rotação e dois tenants na mesma fila.

### Fase 3 Produto mestre cadastro e importação assistida

**Depende de:** fases 1 e 2 para origens externas; formulários podem avançar após fase 1.

1. Criar produto mestre e SKU interno estável; migrar produto/variação atuais com tabela de equivalência. Conservar referência antiga em pedidos e cálculos.
2. Construir cadastro com título, descrição, marca, categoria, SKU, GTIN/EAN, NCM/CEST, unidade, peso, dimensões, custo, atributos e fotos. Estoque será saldo do módulo de estoque, não campo editável independente.
3. Suportar variações, kits/composições, código de barras, ativo/inativo, dados fiscais e política de completude por canal. Evitar exclusão física de itens já vendidos.
4. Importar CSV, XLS e XLSX com formatos regionais, codificação, zeros à esquerda, múltiplas abas e pré-visualização. Nunca executar fórmulas, macros ou links do arquivo.
5. Implementar mapeamento visual arrastável de colunas; IA sugere correspondências e confiança, usuário confirma; salvar template por fornecedor/origem.
6. Processar por lotes retomáveis, com criar/atualizar/ignorar, chave explícita, duplicidades, relatório de erros por linha e correção/reenvio só do que falhou.
7. Importar XML de NF-e de entrada com validação segura. Detectar nota já importada; mapear fornecedor e itens; abrir pré-cadastro para o desconhecido; solicitar fotos/dimensões/atributos faltantes.
8. Separar leitura do XML, confirmação do recebimento físico e atualização de custo/estoque. Uma nota não deve aumentar saldo duas vezes nem afirmar mercadoria recebida apenas porque seu XML foi carregado.
9. Importar catálogo existente de marketplace e propor vínculo a SKU; ambiguidade exige revisão.
10. Criar histórico de alterações, busca, filtros, edição em lote, exportação e bloqueios por dependências.
11. Entregar **Import Agent e Product Agent na primeira versão de catálogo**: o primeiro reconhece estrutura e sugere mapeamento; o segundo aponta cadastro incompleto e sugere enriquecimento apoiado nas fontes do produto. Ambos usam o gateway/registro da fase 1 e submetem gravações às ferramentas validadas. Não preencher dado fiscal ou característica sem evidência.

**Aceite:** importar um catálogo piloto de 3.000 SKUs sem cadastro manual individual; reimportação idêntica não duplica; zeros e decimais preservados; registros inválidos ficam explicitamente pendentes. O volume é alvo proposto para o ensaio, não capacidade já medida. **Testes:** arquivos corrompidos, XXE no XML, zip bomb, duplicidade, colisões de SKU, unidade, variações e reversão de importação ainda não utilizada.

### Fase 4 Estoque compras e custo de aquisição

**Depende de:** fase 3 e identidade da fase 1.

1. Criar depósitos e posições, incluindo próprio, terceiros e fulfillment. Separar disponível, reservado, avariado, quarentena e em trânsito.
2. Implementar movimentos de entrada, saída, reserva, liberação, transferência, ajuste, perda e devolução. Cada movimento tem motivo e origem.
3. Estabelecer reserva transacional com bloqueio ou controle de versão. Expedição reduz físico e encerra reserva; não descontar disponível duas vezes.
4. Tratar kits pelo consumo de componentes; transferências em duas etapas; recebimentos parciais; lotes/validade quando exigidos pela categoria piloto.
5. Construir fornecedores, vínculo SKU-fornecedor, pedido de compra, prazo, mínimo, custo, aprovação e recebimento divergente.
6. Definir custo médio ponderado como opção inicial a validar com a operação, com custo de aquisição e rateio de frete de compra. Preservar CMV histórico do pedido e tratar ajustes retroativos explicitamente.
7. Implantar inventário cíclico, contagem, diferença, aprovação e trilha de ajuste. Construir valor financeiro do estoque, giro, cobertura e capital imobilizado.
8. Preparar sincronização de disponibilidade por canal: estoque de segurança, prioridade, limite anunciado e reconciliação periódica. Fulfillment mantém fonte e saldo próprios.

**Aceite:** saldo é reconstituído dos movimentos; duas reservas simultâneas não consomem a mesma última unidade dentro do Radar; devolução avariada não vira disponível. **Testes:** concorrência, cancelamento antes/depois da separação, transferência parcial, devolução e custo histórico. Atrasos externos ainda podem gerar overselling; buffers e contingência reduzem risco, sem promessa de impossibilidade absoluta.

### Fase 5 Hub de anúncios e gestão individual ou em massa

**Depende de:** fases 2, 3, 4 e passagem de ações da fase 1.

1. Modelar Listing por conta e canal, com uma ou mais variações vinculadas a SKUs internos. Exibir todos os anúncios de um produto e seus indicadores.
2. Carregar categorias, atributos, exigências fiscais, imagens, títulos, frete e regras por canal/categoria. Registrar versão dos requisitos.
3. Criar assistente de publicação com seleção de produtos/canais, campos faltantes, prévia por destino, validação e confirmação.
4. Executar publicação em lote com status por item/canal, rejeições legíveis, IDs externos, retentativa seletiva e consulta após timeout.
5. Permitir edição individual e em massa de preço, pausa/reativação, estoque e conteúdo permitido. Exibir exatamente quais destinos serão alterados.
6. Separar alterações do produto mestre das adaptações locais do anúncio; mostrar diffs e conflitos com alterações feitas no marketplace.
7. Integrar sincronização de estoque por versão e coalescência de atualizações. Não deixar mensagem antiga sobrescrever saldo recente.
8. Exibir estado desejado e confirmado, falhas, última sincronização, histórico, views/conversão quando fornecidas e disponibilidade da fonte.

**Aceite:** cadastrar uma vez e publicar nos canais homologados; alteração de preço funciona individualmente e em lote; falha na Shopee não mascara sucesso no ML. **Testes:** regra de categoria, token expirado, anúncio duplicado, falha parcial, lote cancelado, alteração concorrente e pausa repetida.

### Fase 6 Pedidos logística e devoluções operacionais

**Depende de:** fases 2 a 4; integra-se à fase 5 e à fase fiscal seguinte.

1. Receber pedidos e itens com snapshot comercial/fiscal, comprador, endereço, descontos, frete, pagamento, conta e IDs externos.
2. Separar estados de pedido, pagamento, reserva, nota, envio e devolução; mapear packs, split de remessas e múltiplos itens.
3. Reservar conforme evento elegível; detectar pedido sem SKU, saldo insuficiente, pagamento pendente e duplicidade. Criar fila de exceções.
4. Criar telas de pedidos, filtros, histórico, edição permitida e ações em lote, com prioridade por SLA.
5. Construir picking, packing, conferência por código de barras, etiquetas, volumes, peso e rastreio. Validar geração/reimpressão sem duplicar contratação de frete.
6. Implementar cancelamento em cada estágio, liberação de reserva, estorno financeiro e fluxo fiscal correspondente.
7. Criar devolução/troca com solicitação, motivo, evidência, autorização, recebimento, inspeção, destino do item, reembolso e novo pedido quando houver troca.
8. Integrar fulfillment por canal, sem presumir que toda expedição nasce no depósito do seller.

**Aceite:** venda gera uma reserva e uma trilha única; cancelamento e devolução parcial atualizam corretamente estoque e financeiro; expedição segue os requisitos fiscais homologados. **Testes:** webhook atrasado, cancelamento concorrente com envio, pacote com vários pedidos, falta de item, troca por SKU diferente, avaria e reembolso sem retorno físico.

### Fase 7 NF-e integrada ao pedido

**Depende de:** fases 1, 3 e 6; pesquisa e contratação iniciam na fase 0.

1. Comparar provedores por cobertura, API, homologação, custo por emissão, retenção, contingência e suporte. Proposta: integrar provedor fiscal, mantendo domínio e dados fiscais próprios do Radar.
2. Construir configuração por CNPJ/filial: regime, inscrição, série, numeração, certificado, responsável e vigências. Tratar certificado como segredo com alerta de validade.
3. Implantar dados mínimos do destinatário cifrados e acesso restrito; preservar hash para busca quando apropriado. O hash atual não substitui CPF/CNPJ exigido para emissão.
4. Validar NCM, CFOP, CST/CSOSN e demais campos aplicáveis com regras aprovadas por especialista. IA pode apontar inconsistências, sem inventar enquadramento tributário.
5. Criar estados rascunho, validado, enviado, processando, autorizado, rejeitado, cancelado e contingência quando suportada. Guardar número, série, chave, protocolo, XML e DANFE.
6. Persistir identidade da emissão antes de transmitir. Após timeout, consultar situação antes de emitir novamente.
7. Implantar cancelamento, carta de correção, inutilização e devolução conforme operação e suporte homologados; anexar eventos à nota original.
8. Enviar informação fiscal ao marketplace no formato e momento exigidos e liberar expedição pelas regras do canal.
9. Manter atualização de schemas e regras fiscais por vigência. Acompanhar notas técnicas oficiais e a transição tributária, incluindo campos aplicáveis de IBS/CBS, sem congelar uma alíquota universal no código. [Portal de notas técnicas da NF-e](https://www.nfe.fazenda.gov.br/portal/consulta.aspx/listaConteudo.aspx?AspxAutoDetectCookieSupport=1&tipoConteudo=04BIflQt1aY%3D).

**Aceite:** fluxo completo de homologação aprovado; notas sem duplicidade, documentos recuperáveis e rejeições acionáveis; casos do piloto conferidos pelo responsável fiscal antes de produção. **Testes:** certificado vencido, indisponibilidade, retorno duplicado, timeout após autorização, numeração concorrente, cancelamento e divergência de totais. NFC-e e NFS-e não entram automaticamente no produto de mercadorias; só por necessidade de e-commerce explicitamente validada.

### Fase 8 Financeiro e Profit Engine auditável

**Depende de:** Data Core, pedidos e CMV; fechamento completo depende da fase fiscal e fontes financeiras. Seu modelo deve ser desenhado desde a fase 0.

1. Reutilizar regras de precisão, rateio, taxas por vigência e lacunas existentes. Documentar significado de bruto, líquido, contribuição, resultado operacional e caixa.
2. Implementar razão financeiro imutável com lançamentos balanceados, contas de controle e referências a pedido/item/SKU. Correções fazem reversão/ajuste; não editam fatos fechados.
3. Separar venda, recebível, repasse e depósito bancário. Compra de mercadoria gera estoque e obrigação; seu pagamento não deve reaparecer como CMV da venda.
4. Criar contas a pagar/receber, parcelas, vencimentos, fornecedores, categorias, centros de custo, baixas parciais, despesas recorrentes, fluxo de caixa e DRE gerencial.
5. Capturar comissão, tarifa fixa, frete de venda e reverso, subsídio, embalagem, cupons de seller e plataforma, taxas de pagamento/parcelamento/antecipação, armazenagem e outros ajustes.
6. Congelar CMV por item e método. Descontos, frete e outros custos compartilhados devem ter rateio versionado, reproduzível e sem centavo perdido.
7. Incorporar tributos conforme perfil, período e regras validadas, evitando dupla dedução de imposto embutido/não recuperável e créditos tratados indevidamente.
8. Importar Ads por API quando autorizado e por relatório oficial enquanto necessário. Distinguir atribuição direta de rateio; conservar gasto sem venda e custo não atribuído.
9. Tratar devoluções parciais/totais, chargebacks, retenções, multas, compensações, reversões de taxas e recuperação de mercadoria com destinação correta.
10. Conciliar pedido → eventos financeiros → lote de repasse → recebimento. Suportar relações um-para-muitos e muitos-para-um, pagamentos parciais e retenções liberadas depois.
11. Produzir resultados por pedido, item/SKU, canal, conta, filial e período; categorias de despesas não atribuídas continuam visíveis no resultado consolidado.
12. Manter estimado, apurado e liquidado/conciliado como dimensões claras. Um repasse conciliado não prova lucro completo se faltam CMV, imposto ou Ads.
13. Implementar fechamento, versão do cálculo, reabertura autorizada e comparação entre versões; eventos tardios geram ajuste explícito.
14. Criar memória acessível com fonte, raw_event_id, valor, moeda, regra, vigência, método, rateio, confiança e data. Toda divergência abre uma exceção resolvível.

**Aceite:** dataset de referência reconciliado por contador/financeiro; replay produz o mesmo resultado; soma dos itens e custos não atribuídos fecha com pedido/canal/período; divergências não são absorvidas por tolerância escondida. **Testes:** centavos, valores negativos, receita zero, multicurrency sem soma inválida, desconto já embutido, devolução tardia, Ads sem pedido, imposto faltante, payout parcial, taxa retroativa e pedido espelhado.

Exemplo didático: venda de R$ 199,90 menos comissão R$ 23,99, frete R$ 18,40, tarifa R$ 6,00, desconto R$ 2,70, imposto R$ 12,00, CMV R$ 74,00, embalagem R$ 1,80 e Ads R$ 8,25 resulta em R$ 52,76. Esse valor só será rotulado como completo se não houver custos relevantes pendentes; despesas gerais alocadas podem alterá-lo. Os valores são ilustrativos, não taxas vigentes.

Usar na interface “resultado gerencial apurado” e esclarecer a expressão comercial “lucro real”. Ela não deve confundir resultado do negócio com o regime tributário brasileiro chamado Lucro Real.

### Fase 9 Precificação dashboards e primeiro ERP multicanal

**Depende de:** fases 1 a 8. **Marco:** V1 ERP, após prova em pelo menos dois canais para anunciar operação multicanal.

1. Criar Pricing Engine com custo, imposto, taxa vigente, tarifa por faixa, frete, descontos, embalagem e cenário de Ads. Resolver preço mínimo respeitando mudanças de faixa; não usar fórmula única quando a regra for descontínua.
2. Separar markup e margem; simular preço por conta/canal, margem desejada, promoções e impacto no lucro. Mostrar premissas e sensibilidade.
3. Aplicar alteração individual ou em massa via Action Engine, com validade da simulação, piso/teto, limite percentual e confirmação do preço observado no canal.
4. Consolidar dashboards para dono, gerente, financeiro, atendimento, estoque/expedição e marketing. Métricas usam a mesma camada semântica do chat.
5. Criar Mission Control: problema/oportunidade, evidência, impacto, prioridade, responsável, prazo, ação e desfecho. Agrupar duplicatas e permitir adiar com motivo.
6. Mostrar frescor, cobertura e status financeiro em todos os painéis; filtros por período, conta, filial, marketplace e SKU.
7. Implementar comparativos, exportação autorizada, drill-down até a origem e acesso móvel. Aplicar identidade Radar, acessibilidade, estados vazios e recuperação de erros.
8. Homologar o segundo canal repetindo publicação, venda, estoque cruzado, fiscal, envio, custo, repasse e devolução. Não basta importar vendas do segundo marketplace.
9. Ampliar o **Business Copilot de leitura**, já presente desde a fundação, para explicar produto/canal/período e abrir simulações. Liberar a primeira versão de **Profit Guardian** com alertas explicáveis e botões simular, pausar, ajustar preço e ignorar; escrita ainda depende da política e da aprovação aplicáveis.

**Aceite:** jornada completa de duas contas/canais; alteração de preço rastreável; nenhum painel contorna permissões; amostras conferidas até o centavo. **Testes:** preço em fronteira de tarifa, custo atualizado entre simulação e execução, margem negativa, baixa cobertura, filtros de zona horária, teclado e leitores de tela.

### Fase 10 Radar Inbox e atendimento unificado

**Depende de:** fases 1, 2, 6 e políticas de ação. Pode avançar após estabilização operacional do primeiro canal.

1. Criar matriz de mensagens, perguntas, comentários e anexos efetivamente suportados por Mercado Livre, Shopee, TikTok Shop e SHEIN. Acrescentar e-mail, WhatsApp e Instagram conforme autorização disponível.
2. Construir inbox com filtros, atribuição, tags, SLA, prioridade, histórico, pesquisa, notas internas, anexos e contexto do pedido.
3. Distinguir mensagem pública de privada. Não revelar dado de pedido em resposta pública; não juntar clientes automaticamente por nome.
4. Implementar templates, base de conhecimento por tenant/marca/produto, macros e indicadores de fila.
5. Iniciar IA em sugestão: pedido, rastreio e ficha técnica consultados em tempo real; resposta com fonte e revisão.
6. Automatizar somente classes autorizadas e avaliadas, como status e FAQs com informação suficiente. Troca, atraso e defeito começam com aprovação; fraude, chargeback, ameaça jurídica e exceção financeira escalam para humano.
7. Implementar janela/política de envio e opt-in quando aplicável, bloqueio, handoff, idempotência de resposta e prevenção de humano e agente responderem simultaneamente.

**Aceite:** atendimento simples resolvido com informação correta e histórico; nenhum envio duplicado; exceções chegam ao humano; limitações de canal explícitas. **Testes:** prompt injection em mensagem/anexo, pedido de outro tenant, dados desatualizados, mensagem pública, múltiplos atendentes e falha após envio externo.

### Fase 11 Brand Studio e Listing Agent

**Depende de:** catálogo, anúncios, permissões e gateway/registro já implantados na fase 1. Listing Agent e Brand Studio são a segunda onda de IA, junto ao Profit Guardian; podem avançar assim que catálogo e hub estiverem estáveis, sem esperar a fase 13.

1. Criar Brand Profile com logo, cores, tipografia, estilo fotográfico, fundos, posicionamento e regras por marca dentro do tenant.
2. Implantar biblioteca de originais, versões e direitos de uso; selecionar produtos e edição individual/em lote.
3. Disponibilizar remoção de fundo, iluminação, resolução, fundo contextual, lifestyle, infográfico e versões por canal.
4. Preservar características reais do produto; bloquear alegações falsas e alteração de quantidade, material, cor ou dimensão sem confirmação. Regras do canal prevalecem sobre logomarca/marca-d'água.
5. Listing Agent propõe categoria, atributos, título, descrição, bullets, palavras-chave e imagens por canal a partir dos dados aprovados.
6. Mostrar prévia/diff, justificativa, validação de políticas e revisão humana; publicar pela mesma ferramenta de anúncios.
7. Relacionar versão do conteúdo à performance posterior. Usar experimentos controlados quando possíveis; não atribuir causalidade a uma simples correlação de conversão.
8. Registrar custo de geração, aprovação, rejeição, retrabalho e versão do modelo; impor quotas.

**Aceite:** conteúdo consistente com marca e produto, validado por canal, com original recuperável e publicação auditada. **Testes:** mudança indevida de produto, imagens inadequadas, instrução maliciosa no catálogo, tenant/marca errados, limites e revisão em lote.

### Fase 12 Inteligência de mercado Ads compras e devoluções

**Depende de:** Profit, catálogo, compras e fontes autorizadas.

1. Competitor Watch permite cadastrar URL, associar SKU comparável e acompanhar preço, frete, disponibilidade, promoção, título, imagem, avaliações e posição quando a fonte permitir.
2. Acrescentar descoberta assistida de anúncios e empresas concorrentes, confirmação de equivalência e histórico temporal. Exibir faixas e estimativas como tais; ausência de dado não vira zero.
3. Unir concorrência e precificação: comparar preço total, variação, frete e condições; mostrar impacto de igualar concorrente na margem e no piso de preço.
4. Modelar keywords, tendências e categorias com origem, período, cobertura e limitações. Coleta respeita contratos e limitações de acesso; não depende de contornar bloqueios.
5. Integrar contas/campanhas Ads, gasto, cliques, conversões e janelas de atribuição. Comparar ROAS, contribuição após Ads e resultado atribuído; “incremental” exige experimento ou método causal explicitado.
6. Inventory & Purchase Intelligence projeta giro, ruptura, sazonalidade, lead time, orçamento, mínimo de compra e margem; cria sugestão de pedido com explicação e confiança.
7. Returns Intelligence agrupa motivos com evidências, detecta taxas anormais considerando volume e sugere alteração de anúncio, tabela de medidas ou fornecedor.
8. Transformar achados em tarefas no Mission Control e propostas no Listing Agent/Brand Studio, com medição do resultado depois.

**Aceite:** alerta possui fonte e data, comparação correta e impacto financeiro; previsão é confrontada com histórico e baseline simples; gasto sem venda permanece no resultado. **Testes:** fonte indisponível, concorrente não equivalente, estoque esgotado, regressão de previsão, baixa amostra, atribuição duplicada e comentários maliciosos.

### Fase 13 Chat voz e agentes especializados

**Depende de:** gateway/registro da fase 1 e dados/ferramentas dos módulos consumidores. Esta fase amplia chat, voz e a cobertura dos agentes; não marca o início da IA no Radar.

1. Ampliar AI Gateway com múltiplos provedores quando necessário, modelos versionados, fallback avaliado, orçamento por tenant e política de retenção comprovada.
2. Ampliar Agent Registry e suas avaliações: nome, versão, finalidade, entradas, ferramentas permitidas, escopo e nível máximo de autonomia.
3. Evoluir o catálogo de perguntas atual para consultas de produto, canal, período e causas. Manter números obtidos por ferramentas e recusar/esclarecer SKU ou período ambíguo.
4. Criar chat contextual com referências clicáveis, dados de origem, frescor, confiança e memória com retenção definida; histórico não concede novas permissões.
5. Adicionar voz: consentimento/contexto de uso, transcrição, correção de nomes/SKUs/valores, resposta textual e áudio opcional. Operação sensível exige confirmação visual dos parâmetros; voz não substitui autenticação.
6. Consolidar as ondas já iniciadas: Import Agent, Product Agent e Business Copilot nas fases 1/3; Listing Agent, Brand Studio e Profit Guardian nas fases 9/11; Support, Inventory/Purchase e Pricing conforme os módulos de atendimento, compras e precificação estejam prontos.
7. Acrescentar Reconciliation Agent, Ads Profit Agent, Competitor Agent, Returns Intelligence e Fiscal Agent conforme dados e ferramentas já homologados.
8. Para cada agente, implementar recomendar → propor → simular → aprovar → executar → verificar → medir. Nível automático depende de política previamente autorizada e limites de risco.
9. Testar ataques por documentos, mensagens, anúncios e fontes externas; permitir apenas ferramentas tipadas, sem SQL ou comandos livres.
10. Criar avaliação offline com casos reais anonimizados, conjunto adversarial, critérios numéricos e revisão humana. Medir correção, abstinência, segurança, latência, custo e trabalho evitado.

Contratos mínimos dos agentes aprovados:

| Agente | Dados e resultado esperado | Ações e comprovação |
|---|---|---|
| Import Agent | Planilha/XML → mapeamento, inconsistências e preview | Importar apenas lote confirmado; relatório por linha e ausência de duplicação |
| Product Agent | Ficha e documentos → campos faltantes e sugestões fundamentadas | Propor correção; gravar campo validado e histórico de origem |
| Business Copilot | Ferramentas de métricas → resposta por SKU/canal/período | Somente leitura inicialmente; número idêntico ao painel e fonte acessível |
| Listing Agent | Produto, canal, keywords, concorrência e histórico → conteúdo por destino | Publicar revisão aprovada; ligar versão ao desempenho observado |
| Profit Guardian | Vendas, taxas, CMV, frete, impostos, Ads e devoluções → prejuízo e causa | Simular, pausar ou propor preço; detectar dado insuficiente e confirmar estado externo |
| Support Agent | Mensagem, pedido, rastreio, políticas e ficha → resposta ou escalonamento | Responder conforme classe/autonomia; nenhuma promessa sem evidência ou envio duplicado |
| Inventory & Purchase Agent | Estoque, giro, lead time, sazonalidade, margem e caixa → quantidade/data de compra | Propor compra dentro do orçamento e destacar excesso/estoque imobilizado; conferir previsão depois |
| Pricing Agent | Custos, regras, alvo e comparáveis → preço seguro por canal | Aplicar pela política; impedir preço abaixo do piso e revalidar custos antes da escrita |
| Returns Intelligence | Motivos e evidências de devolução → causas e taxa comparável | Propor correção no Listing Agent/Brand Studio; conferir redução sem alegar causalidade automática |
| Ads Profit Agent | Gasto, atribuição, produto e margem → contribuição após Ads | Propor pausa/orçamento com limites; separar resultado atribuído de incremental comprovado |
| Reconciliation Agent | Devido versus recebido → divergência de taxa, devolução, retenção, ajuste, pendência ou erro | Executar matching determinístico e abrir exceção; ajuste financeiro só com autorização e trilha |
| Competitor Agent | Fontes permitidas e equivalência de produto → mudanças e oportunidades | Sugerir ação com impacto na margem; não tratar vendas estimadas do concorrente como fato |
| Fiscal Agent | Cadastro, notas, rejeições e certificados → risco e providência | Encaminhar emissão/correção à ferramenta fiscal; não decidir regra tributária livremente |
| Radar Operator | Objetivo autorizado, caixa, margem, estoque, Ads e políticas → plano coordenado | Executar workflow com checkpoints, orçamento, verificação e exceções humanas |

Para cada agente, manter dataset, limiares aprovados e relatório de erro por categoria. Uma boa resposta do chatbot não serve como aceite automático para o agente executar uma mudança financeira.

**Aceite:** resposta numérica coincide com serviço determinístico e cita evidência; acesso negado continua negado no chat; áudio ambíguo pede esclarecimento; limites de gasto e ação funcionam. **Testes:** prompt injection, instrução para outro tenant, pedido de segredo, alucinação de ferramenta, loop, indisponibilidade e confirmação obsoleta.

### Fase 14 Radar Operator e Service as Software

**Depende de:** fases 9 a 13, Policy/Action Engine e evidência de qualidade por agente.

1. Criar workflows duráveis, agenda, gatilhos e checkpoints. Operador de alto nível decompõe objetivos em tarefas com escopos e dependências explícitos.
2. Formalizar políticas: margem mínima, mudança máxima de preço, orçamento diário de Ads, número máximo de itens, canais permitidos, horário e exigência de aprovação.
3. Resolver conflitos entre agentes: Profit Guardian não aumenta preço ao mesmo tempo que campanha o reduz; priorizar e serializar ações sobre recurso compartilhado.
4. Disponibilizar modo sombra antes de ligar automação: registrar o que teria sido feito e comparar resultado/risco sem efeitos externos.
5. Implantar botão de interrupção por tenant/agente/conector, cancelamento de trabalhos ainda não iniciados, verificação após execução e compensação quando possível.
6. Tratar ação irreversível ou incerta como exceção humana; não chamar “rollback” o cancelamento fiscal/financeiro que requer procedimento próprio.
7. Criar ofertas por resultado operacional mensurável: conciliação realizada, atendimento elegível resolvido, anúncios publicados e exceções tratadas. Definir qualidade e obrigações, sem garantir lucro do cliente.
8. Implantar planos, entitlements, quotas, medição de consumo, cobrança, alertas de orçamento, cancelamento e exportação de saída. Preços e nomes comerciais permanecem decisão de produto.
9. Medir taxa de conclusão sem retrabalho, tempo poupado, custo por tarefa, intervenção humana, perdas evitadas verificáveis e margem do próprio Radar.

Preservar as três camadas comerciais aprovadas como nomes provisórios: **Radar ERP** para software operacional, **Radar AI** para inteligência/automações e **Radar Autopilot** para trabalho executado. O catálogo comercial deve descrever quais tarefas são cobertas, com quais dados e limites, como uma exceção é tratada e qual evidência comprova entrega. Os preços não estão definidos por este plano.

**Aceite:** rotina limitada roda em piloto com logs completos, orçamento respeitado e exceções encaminhadas; interrupção funciona; ação só é considerada concluída após confirmação. **Testes:** conflito entre agentes, revogação durante execução, política alterada, limite de valor, duas aprovações, falha parcial e retorno externo incerto.

### Fase 15 Expansão migração de ERPs e escala comercial

**Depende de:** V1 estável e mecanismos de importação, observabilidade e recuperação.

1. Liberar TikTok Shop e SHEIN com todos os gates por capacidade; depois avaliar Amazon, Magalu e lojas próprias conforme demanda. Nenhum canal entra apenas por ter adaptador de pedidos.
2. Criar conectores de migração Tiny/Bling/outros: autorização, inventário, prévia, mapeamento, importação de produtos/SKUs, clientes, fornecedores, estoque inicial, títulos, histórico e anexos suportados.
3. Fazer reconciliação de totais, quantidade, saldos e vínculos; discrepâncias impedem corte até resolução ou exceção formal.
4. Planejar coexistência curta com dono único de preço, estoque, fiscal e financeiro por fase. Bloquear loops e envio duplo; registrar data de corte e congelamento.
5. Importar histórico com marcação de legado, origem e cobertura. Histórico incompleto não vira lucro auditável completo por retroatividade.
6. Desenvolver onboarding autoguiado, treinamento por cargo, suporte, diagnóstico de conexões, exportação e processo de encerramento.
7. Validar capacidade com picos e tenants grandes; otimizar índices/projeções, isolamento de workers e cache. Escalar componentes apenas com métricas.
8. Conduzir piloto comercial limitado, coletar retenção, ativação, custo de servir e valor demonstrado; expandir por coortes.

**Aceite:** migração ensaiada e revertível antes do corte; um único escritor por domínio; nenhum histórico perdido; novos canais passam os mesmos testes do primeiro. **Testes:** migração reiniciada, saldo divergente, duplicação por ERP/marketplace, reconexão, exportação e restauração completa.

## 7 Permissões política e contrato de ação

### 7.1 Matriz inicial proposta

| Papel | Acesso padrão proposto | Restrições principais |
|---|---|---|
| Dono/administrador | Resultado, caixa, configuração e políticas | Ações críticas com autenticação reforçada e auditoria |
| Gerente | Operação, preços, performance, estoque e SLA | Dados financeiros detalhados só por permissão; limites de lote |
| Financeiro | Pagar/receber, conciliação, DRE, repasses e notas | Sem editar anúncios ou estoque por padrão |
| Atendimento | Inbox, pedido e histórico necessário | Sem CMV, DRE, tokens, políticas ou exportação financeira |
| Estoque/expedição | Picking, packing, etiquetas e movimentos permitidos | Ajuste relevante exige aprovação; sem lucro por padrão |
| Marketing/marketplace | Anúncios, imagens, Ads, concorrência e simulação | Piso de preço protegido; custo/margem conforme permissão |
| Integração/agente | Ferramentas estritamente necessárias | Identidade própria, expiração, limite e nenhum privilégio administrativo implícito |

Essa é uma proposta para concretizar RBAC; produto deve aprovar a matriz na fase 1. Personalizações não podem remover isolamento ou auditoria.

### 7.2 Contrato obrigatório de uma ação

Toda ação contém tenant, ator, recurso, operação, parâmetros validados, versão do recurso, motivo, política, idempotency key, orçamento e prazo. Uma aprovação registra quem aprovou, os parâmetros exatos e seu vencimento.

Estados propostos: proposta → simulada → aguardando aprovação → autorizada → em execução → concluída, parcialmente concluída, falhou ou situação desconhecida. Situação desconhecida exige consulta externa antes de nova tentativa. Revogada e expirada são estados terminais para autorizações ainda não usadas.

Na execução, revalidar permissão, política, preço/custo e versão do recurso. Aprovação para mudar R$ 79,90 para R$ 84,90 não autoriza alterar outro anúncio nem um preço recalculado posteriormente. Para lotes, guardar um resultado por item; compensar somente efeitos cuja reversão seja válida.

## 8 Testes critérios globais e evidências de aceite

### 8.1 Estratégia de testes

| Camada | Casos obrigatórios |
|---|---|
| Unidade | Precisão monetária, rateio, custo, preço, regras de estoque e transições |
| Propriedades | Soma dos rateios conserva total; replay é idempotente; reserva não cria saldo |
| Integração com PostgreSQL | RLS, FKs por tenant, transações concorrentes, migrações e locks |
| Contratos externos | Fixtures sanitizadas reais, versão, campos ausentes, erros e limites de cada API |
| Ponta a ponta | Cadastro → publicação → venda → reserva → NF-e → envio → repasse → lucro → devolução |
| Navegador | Papéis, filtros, uploads, lotes, aprovação, falhas e acessibilidade |
| Segurança | IDOR, CSRF, SSRF, XSS, upload, segredo, isolamento em job/arquivo/IA e negação de RBAC |
| Resiliência | Queda depois do envio externo, replay, atraso, timeout, indisponibilidade e recuperação |
| Performance | Pico por tenant, mistura de tenants, lotes grandes, projeções e duração do fechamento |
| IA | Correção semântica, fontes, abstinência, ataques, política, custo, voz e ações indevidas |
| Operação | Backup restaurado, rollback ensaiado, rotação de credenciais e alertas acionáveis |

### 8.2 Dataset financeiro e operacional de referência

Preparar pelo menos estes cenários por canal homologado: venda simples; múltiplos itens; kit; cupom seller; cupom plataforma; frete cobrado; frete subsidiado; parcelamento; antecipação; custo ausente; imposto pendente; Ads sem venda; cancelamento antes de NF; cancelamento após NF; devolução parcial; devolução avariada; chargeback; retenção; repasse em dois lotes; venda espelhada por ERP; evento fora de ordem; correção retroativa; duas vendas disputando a última unidade.

Cada cenário deve ter arquivo de origem sanitizado, resultado esperado, justificativa, revisão do responsável de negócio e assertivas automáticas. Evidência de sandbox não substitui reconciliação do piloto real.

### 8.3 Gates de liberação

- **G0 Base confiável:** ambiente e testes reproduzidos, CI ativo, inventário e riscos revisados.
- **G1 Segurança:** RLS/RBAC e isolamento de jobs/arquivos aprovados; segredos e recuperação tratados.
- **G2 Primeiro fluxo completo:** um canal publica, vende, reserva, emite, expede e gera resultado com evidência.
- **G3 ERP multicanal:** segundo canal completa o mesmo fluxo e estoque cruzado é exercitado sob concorrência.
- **G4 Resultado financeiro:** fechamento real contém todos os componentes materiais ou lacunas explícitas; repasses conciliados ou exceções identificadas.
- **G5 IA assistiva:** avaliação aprovada por classe de tarefa; zero violação crítica conhecida na suíte de segurança.
- **G6 Automação limitada:** modo sombra aprovado, políticas/limites ativos, kill switch testado e responsável de exceções definido.
- **G7 Escala comercial:** restauração e suporte comprovados, custo de servir medido, contratos/cobrança/exportação prontos.

“Zero violação na suíte” é gate de teste, não garantia absoluta de segurança. Nenhuma fase é concluída apenas por interface pronta ou testes simulados.

## 9 Segurança observabilidade e operação contínua

### 9.1 Segurança e privacidade

1. Separar ambientes, contas e chaves; aplicar menor privilégio, TLS, backups cifrados, rotação, gestão de certificados e política de dependências.
2. Exigir tenant em tabelas e FKs apropriadas, propagação segura em workers, testes do usuário real de runtime e contextos limpos ao devolver conexão ao pool.
3. Validar URLs de concorrência, webhooks e imagens contra SSRF; limitar download, tamanho, tipo e tempo. Uploads têm quarentena, verificação e armazenamento privado.
4. Tratar planilhas/XML, mensagens e páginas externas como conteúdo não confiável, sem execução de fórmulas ou instruções de IA. Sanitizar HTML e exports CSV.
5. Classificar dados pessoais em pedidos, payloads, perguntas, áudio e auditoria. Definir finalidade, acesso, prazo, anonimização/expurgo e preservação legal por categoria, com responsável jurídico.
6. Mapear fornecedores de IA e transferências de dados, minimizar/redigir informações enviadas, definir uso/retenção contratual e impedir treinamento cruzado entre clientes.
7. Implantar atendimento a direitos do titular, registro de incidentes e resposta operacional. Base legal não deve ser substituída por um checkbox genérico.

Esses são requisitos de projeto a validar para o contexto do Radar; não constituem parecer jurídico. Usar as publicações da ANPD para orientar avaliação e documentação. [Materiais educativos da ANPD](https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes).

### 9.2 Telemetria e alertas

Instrumentar logs estruturados sem dados sensíveis, métricas e rastreamento distribuído com request ID, event ID, action ID e versão do conector. IDs de tenant/pedido podem ficar em logs protegidos; evitar sua inclusão irrestrita como labels de métricas para não explodir cardinalidade.

Medir: disponibilidade e latência; idade da fila e da sincronização; eventos em erro/quarentena; publicação e saldo divergentes; reservas bloqueadas; rejeição fiscal; repasse não conciliado; cobertura de CMV/imposto/Ads; perguntas recusadas; ações negadas; custo de IA; SLA de atendimento e tarefas com estado externo desconhecido.

Cada alerta tem severidade, responsável, janela, deduplicação e procedimento. Não basta enviar alerta: Mission Control deve permitir investigar, corrigir e verificar resolução.

### 9.3 Metas propostas para o piloto

Metas iniciais, a validar com volume e custo: disponibilidade mensal de 99,5%; API de leitura comum com p95 até 1 segundo sob carga acordada; processamento interno de evento com p95 até 30 segundos após recebimento; publicação de saldo ao canal com p95 até 60 segundos quando sua API estiver disponível. Medir separadamente o atraso imposto pelo marketplace e o atraso interno.

Propor RPO de até 15 minutos e RTO de até 4 horas para o piloto, somente se infraestrutura e testes de recuperação sustentarem isso. Uma VPS única com cópia diária não satisfaz automaticamente essas metas. Para expansão comercial, reavaliar disponibilidade, retenção, recuperação e plantão com orçamento e contratos reais.

### 9.4 Procedimentos operacionais mínimos

Criar procedimentos para: conta desconectada; segredo comprometido; webhook atrasado; API externa indisponível; fila travada; saldo divergente; NF-e com estado incerto; certificado vencendo; lucro inconsistente; vazamento entre tenants; custo de IA excedido; restauração e rollback. Cada procedimento informa diagnóstico, contenção, correção, verificação e comunicação ao cliente.

## 10 Migrações de dados e implantação

### 10.1 Sequência de evolução do schema

| Onda | Mudança | Como preservar a base |
|---|---|---|
| A | Organizações, memberships, permissões e auditoria | Mapear tenants/usuários existentes; permissões iniciais explícitas e revisão |
| B | Contas de marketplace, identidades e histórico bruto | Preservar canal original; rotular payload anterior como snapshot disponível, sem inventar versões perdidas |
| C | Produto mestre, SKU e listing | Criar equivalência entre IDs antigos/novos; revisar ambiguidades antes de união |
| D | Estoque, compras e custo | Inventário inicial e data de corte; movimento de abertura com origem |
| E | OMS, documentos fiscais e remessas | Manter snapshots e referências de pedidos históricos |
| F | Ledger, títulos, repasses e cálculo versionado | Converter custos com proveniência; comparar motor antigo/novo antes de virar leitura |
| G | Inbox, Studio, Market, IA e medição de uso | Acrescentar isolamento, retenção e permissões a cada tabela e arquivo |

Usar expandir → preencher em lotes → comparar → mudar leituras/escritas → observar → remover legado. Migrações de schema não devem disparar notas, preços, mensagens ou movimentações externas. Toda etapa tem contagem, somas de controle, checksum quando aplicável, relatório de divergência e checkpoint retomável.

Não editar migração já aplicada. Os scripts U existentes podem servir a ensaios locais, mas desfazer DDL em produção não restaura necessariamente dados perdidos. Priorizar compatibilidade com versão anterior, migração corretiva para frente e backup restaurável. Testar duração, locks e impacto em cópia representativa.

### 10.2 Caminho para produção

1. Criar ambiente isolado com segredos próprios, TLS, migrações e dados sintéticos.
2. Construir artefatos identificados, executar testes, análise de segurança e verificação de configuração.
3. Restaurar backup em ambiente separado e provar recuperação antes da estreia.
4. Aplicar migrações compatíveis, validar contagens e manter novas funcionalidades desligadas por tenant.
5. Implantar API/frontend/workers com health e readiness, smoke tests e monitoramento.
6. Ativar primeiro leitura e modo sombra; validar fontes e projeções.
7. Liberar escrita limitada no tenant piloto, com limites de lote e observação.
8. Executar fechamento e jornada real, registrar falhas e resolver causas.
9. Expandir gradualmente; rollback desliga flags e retorna binário compatível. Efeitos externos seguem compensações próprias.
10. Registrar versão, migrações, operador, evidências e decisão de liberação. Este plano não executa nem autoriza implicitamente um deploy agora.

## 11 Riscos e decisões que afetam a execução

| Risco | Mitigação e condição de avanço |
|---|---|
| Escopo amplo demais para a capacidade disponível | Fases e gates; concluir uma jornada antes de multiplicar canais; revisar capacidade real |
| Permissões externas demoradas/incompletas | Abrir apps cedo; matriz por capacidade; relatório/importação como cobertura explícita |
| Resultado financeiro com dado ausente | Completude por componente; não chamar estimativa de apuração final |
| Venda ou custo contado duas vezes | Identidade externa, política de espelhos, idempotência e conciliação |
| Overselling por latência entre canais | Reserva local atômica, buffer, fila prioritária e reconciliação |
| Fiscal incorreto ou nota duplicada | Provedor homologado, especialista, estados duráveis e consulta após timeout |
| Autorização insuficiente | RBAC em todas as entradas, testes negativos e revalidação na ação |
| Agente cria prejuízo ou altera recurso errado | Simulação, limites, política versionada, aprovação vinculada e kill switch |
| Previsão apresentada como fato | Intervalo/confiança, baseline e acompanhamento do erro |
| Dados de concorrência inacessíveis | Fontes permitidas, indicação de cobertura e ausência explícita |
| Migração destrói vínculo histórico | Equivalências, backups, comparações e corte reversível |
| Custo de IA excede receita do produto | Medição por tarefa, quotas, cache seguro, modelos por tarefa e precificação validada |
| Operação depende de uma pessoa | Procedimentos, alertas acionáveis, recuperação ensaiada e suporte definido |

Decisões pendentes com momento limite:

- **Antes da fase 1:** matriz de permissões e modelo organização/filial/multi-CNPJ.
- **Antes da homologação da fase 2:** conta piloto, acessos, categorias, permissões e limite de gasto externo.
- **Antes da fase 4:** política de estoque negativo, custo, kits, depósitos e necessidade de lotes/validade.
- **Antes da fase 7:** provedor fiscal, regime e regras aplicáveis, certificado e responsável técnico/contábil.
- **Antes do fechamento da fase 8:** competência, rateios, tratamento de Ads, despesas gerais, antecipação e política de reabertura.
- **Antes de modelos externos:** fornecedores de IA, dados permitidos, retenção e teto de gasto.
- **Antes do Autopilot:** limites por ação/agente, aprovações, operação de exceções e riscos aceitos.
- **Antes de venda comercial:** planos, preço, suporte, contratos, retenção, SLA e infraestrutura que os sustente.

Essas decisões não impedem construir interfaces, contratos e simulações. Impedem apresentar como validado aquilo que depende delas.

## 12 Dependências capacidade e marcos

O caminho crítico é: base reproduzível → identidade/autorização → evidência e conectores → produto mestre → estoque → anúncios/pedidos → fiscal → financeiro conciliado → precificação/painéis → segundo canal completo. Ações autônomas dependem adicionalmente de políticas, avaliação e verificação de resultado.

Fiscal, permissões de marketplace, benchmark e desenho financeiro começam na fase 0 por terem espera externa. Implementação fiscal usa pedidos/catálogo quando disponíveis. Gateway, registro de agentes e Policy/Action Engine entram na fase 1; Import/Product Agents entram na fase 3 e o copiloto de leitura evolui junto à base. A fase 13 amplia voz e agentes, sem concentrar toda a IA no fim. Segurança, observabilidade, testes e migrações acompanham todas as fases.

| Marco | Entrega que pode ser demonstrada | Condição |
|---|---|---|
| M0 | Base técnica confiável | G0 e G1 |
| M1 | ERP interno em um canal | Cadastro até resultado, fiscal e devolução em piloto |
| M2 | Radar ERP V1 multicanal | M1 repetido em segundo canal, estoque cruzado e fechamento |
| M3 | Operação assistida | Inbox, Studio, inteligência e chat com aprovação |
| M4 | Serviço automatizado limitado | Rotinas elegíveis verificadas, políticas e exceções humanas |
| M5 | Expansão comercial | Canais adicionais, migração, suporte e custo de servir sustentáveis |

O arquivo local `CLAUDE.md` descreve fundadora solo com cerca de 20 horas por semana; essa disponibilidade não foi reconfirmada. Não se deve transformar o escopo inteiro em promessa de poucas semanas com base nessa nota. Primeiro medir produtividade em duas iterações reais e estimar por épico, separando engenharia, revisão fiscal, design, homologação e operação.

Se houver equipe, organizar responsabilidades por produto/UX, backend/dados, integração/fiscal, frontend e qualidade/operação. Se for trabalho individual com assistência de IA, seguir o caminho crítico e limitar trabalho em andamento. Especialistas fiscais e validação da operação continuam necessários. Automatização de código não elimina homologação nem responsabilidade por cálculo.

## 13 Matriz de cobertura de todos os requisitos definidos

| Requisito | Estado observado | Fases de entrega | Evidência de conclusão |
|---|---|---|---|
| ERP exclusivo para e-commerce | Base analítica, escopo antigo parcialmente divergente | 0 a 9 | Jornada operacional independente |
| Produto mestre e SKU | Produto/variação de origem | 3 | Cadastro central e equivalências |
| CSV XLS XLSX e IA no mapeamento | Ausente no fluxo de produto | 1 e 3 | Preview, templates, lote e erros |
| XML de entrada | Não localizado | 3 e 4 | Pré-cadastro e recebimento sem duplicação |
| Publicação multicanal | Não localizada | 5 e 9 | Anúncios confirmados em dois canais |
| Ver anúncios do mesmo produto | Não localizado | 5 | Produto → listings, status e insights |
| Alterar preço e pausar individual/em massa | Não localizado | 5 e 9 | Resultado por item e auditoria |
| Pedidos e clientes | Modelos e ingestão parcial | 6 | OMS com estados e exceções |
| Estoque sincronizado | Não localizado | 4, 5 e 9 | Reserva, movimentos e comparação externa |
| Compras fornecedores reposição | Não localizado | 4 e 12 | Recebimento e sugestões explicadas |
| NF-e XML DANFE e retorno ao canal | Não localizado | 7 | Homologação e prova controlada real |
| Picking packing etiquetas rastreio | Não localizado | 6 e 7 | Pedido expedido com vínculo fiscal |
| Contas pagar/receber caixa DRE | Não localizado como operação | 8 | Títulos, baixas e fechamento |
| Profit por canal pedido SKU | Motor por pedido/período parcial | 8 e 9 | Memória completa e conciliação |
| Taxas frete CMV impostos Ads despesas | Naturezas e regras parciais | 4, 7, 8 e 12 | Cobertura/fonte por componente |
| Devoluções reembolsos chargebacks | Modelos parciais | 6, 8 e 12 | Estoque/fiscal/financeiro consistentes |
| Precificação e simulação | Não localizada como módulo | 9 | Piso/margem e aplicação controlada |
| Dashboards por cargo | Três visões, sem RBAC efetivo | 1 e 9 | Seis contextos e negação comprovada |
| Mission Control | Pendências como ponto de partida | 9, 12 e 14 | Exceção até resolução mensurada |
| Atendimento unificado e comentários | Entidades sem inbox operacional | 10 | Canais autorizados e SLA |
| Concorrência por link e descoberta | Não localizado | 12 | Comparáveis com fonte e histórico |
| Brand Studio | Não localizado | 11 | Marca, imagens e revisão por canal |
| Listing Agent | Não localizado | 11 e 13 | Conteúdo aprovado e desempenho ligado à versão |
| Chat de negócios | Heurística e intenções restritas | 1, 9 e 13 | Fonte numérica e esclarecimento |
| Voz | Não localizada | 13 | Transcrição, confirmação e resposta |
| Agentes especializados | Não encontrados no runtime | 1, 3, 9 a 14 | Registro desde a fundação, ferramentas e avaliação por tarefa |
| RBAC | Campo de papel sem controle granular | 1 | Matriz permitir/negar em todas as entradas |
| Policy/Action Engine | Não localizado | 1, 9 e 14 | Simulação, aprovação e efeito verificado |
| Audit log | Auditoria de consultas parcial | 1, 2 e 14 | Origem e histórico de ações completos |
| Service as Software | Visão de produto | 14 | Trabalho concluído e medido com exceções |
| Modular e multi-tenant | Base relevante existente | Todas | Isolamento e contratos preservados |
| Tiny Bling como migração posterior | Adaptador Bling já existe | 15 | Migração ensaiada sem dependência do ERP |
| Segurança observabilidade migrações | Fundamentos parciais | Todas | Gates operacionais das seções 8 a 10 |

### 13.1 Conferência dos 26 pontos aprovados no outro chat

| Pontos da mensagem aprovada | Onde estão contemplados |
|---|---|
| 1 Importação e XML | Fase 3, Import/Product Agents e recebimento na fase 4 |
| 2 Concorrentes e diferenciação | Seção 5, benchmark por tarefa e resultado auditável |
| 3 Visão em camadas | Abertura e seção 4, Data Core transversal e nove domínios |
| 4 Telas por cargo | Fases 1/9 e matriz 7.1 |
| 5 Mission Control | Fase 9 com responsáveis, evidências e resolução |
| 6 Perguntar compreender recomendar executar | Abertura, fases 9/13 e contrato de ação |
| 7 Três níveis de autonomia e audit log | Fases 1/13/14 e seção 7.2 |
| 8 Inbox multicanal | Fase 10 e matriz de capacidades por canal |
| 9 Automático aprovação humano | Fase 10 e avaliações por classe de atendimento |
| 10 Brand Studio | Fase 11 com Brand Profile e versões de imagens |
| 11 Listing Agent e desempenho | Fase 11 e contrato do agente |
| 12 Competitor Watch | Fase 12, URL, descoberta e margem |
| 13 Profit Guardian | Primeira liberação na fase 9, expansão nas fases 13/14 |
| 14 Inventory & Purchase Agent | Fases 4/12/13, caixa e custo de oportunidade |
| 15 Returns Intelligence | Fases 6/12/13 e retorno ao Studio/Listing |
| 16 Ads Profit Agent | Fases 8/12/13, atribuição e contribuição após Ads |
| 17 Reconciliation Agent | Fases 8/13, devido versus recebido e classificação de exceção |
| 18 Fiscal Agent | Fases 7/13, cadastro, rejeição, certificado e emissão |
| 19 Service as Software | Fase 14, entrega verificável de trabalho |
| 20 ERP AI Autopilot comerciais | Fase 14, entitlements e custo por tarefa |
| 21 Sistema que executa e chama pessoas | Abertura, Mission Control e Radar Operator |
| 22 Nove domínios | Seção 4.1 |
| 23 IA desde o início e ondas de agentes | Fases 1/3/9/11/13/14 e seção 12 |
| 24 Ferramentas com políticas sem SQL livre | Seções 4/7 e fases 1/13 |
| 25 AI Gateway Agent Registry Policy Engine desde o começo | Fase 1, itens 7/9/10 |
| 26 Diferenciação em cinco pilares | Ledger, conhecimento operacional nos agentes, execução, experiências por cargo e trabalho entregue ao longo do plano |

Os exemplos numéricos da mensagem aprovada são ilustrações de experiência, não metas garantidas de lucro, vendas ou eficiência. As afirmações sobre concorrentes continuam sujeitas à verificação de fonte e disponibilidade registrada na seção 5.

## 14 Primeira fila recomendada de implementação

1. Reproduzir testes backend e staging e registrar baseline atual.
2. Atualizar decisões do projeto para ERP Radar independente e inventariar anexos de referência.
3. Definir piloto, contas, permissões e iniciar homologações/fiscal.
4. Corrigir autorização por cargo, revisar sessão/CSRF e implantar os contratos iniciais de AI Gateway, Agent Registry e Policy/Action Engine.
5. Concluir contagem de pedidos por canal e expor divergências financeiras como pendências.
6. Criar histórico bruto imutável e separar identidade de conta/canal.
7. Especificar e migrar produto mestre, SKU e vínculo externo.
8. Entregar cadastro/importação com Import Agent, Product Agent, preview, validação e relatório de erros.
9. Construir movimentos/reservas de estoque e custo de aquisição.
10. Homologar conector do primeiro canal, anúncio, pedido e sincronização de saldo.
11. Integrar NF-e e expedição, com estados recuperáveis.
12. Fechar ledger/recebíveis/conciliação e comprovar Profit por pedido/SKU/canal.
13. Liberar precificação e painéis com permissões.
14. Repetir a jornada completa no segundo marketplace e validar V1.
15. Avançar Inbox, Studio, Market, chat/voz e agentes conforme as fases, antes do Autopilot limitado.

A condição de sucesso é uma operação que consegue cadastrar, vender, cumprir o pedido, explicar cada componente do resultado e agir com controle. Cada módulo posterior amplia essa mesma operação, com origem verificável e responsabilidades claras.

## 15 Evidências locais e limites desta entrega

Referências do repositório, todas relativas à raiz inspecionada:

- `backend/pom.xml` e `frontend/package.json`: stack e dependências declaradas.
- `backend/src/main/resources/db/migration/V001` a `V016`: modelo e mecanismos de isolamento.
- `backend/src/main/java/com/plataforma/autenticacao/ConfiguracaoSeguranca.java`: autorização autenticada geral e CSRF desabilitado.
- `backend/src/main/java/com/plataforma/catalogo/Produto.java`: produto com vínculo de canal/origem.
- `backend/src/main/java/com/plataforma/ingestao/ServicoIngestao.java`: upsert que substitui payload e fluxo idempotente.
- `backend/src/main/java/com/plataforma/margem/MotorMargemPedido.java`: cálculo determinístico N0 a N3, precisão e IDs de custos.
- `backend/src/main/java/com/plataforma/margem/ServicoMargemPeriodo.java`: agregação e consulta de itens/custos por pedido.
- `backend/src/main/java/com/plataforma/canal/RespostaCanal.java`: DTO sem quantidade de pedidos.
- `frontend/src/lib/marca.ts`: nome de produto ainda definido como Plataforma.
- `docs/ESTADO.md`, `docs/PENDENCIAS.md`, `docs/fiscal/regras-de-margem.md`: limitações, histórico e regras propostas.
- `backend/target/surefire-reports`: 300 testes em relatórios existentes, sem reexecução atual.

Não foram alterados código, configuração, banco ou documentação dentro de ECP. Não houve commit, push, deploy, chamada autenticada a marketplace, contratação ou envio de dados do projeto a provedor de IA. Os arquivos desta entrega foram criados na pasta de outputs da tarefa.

A inspeção confirma o conteúdo local e os testes frontend executados; não confirma saúde de produção, estado do banco ativo, funcionamento dos conectores reais, aprovação fiscal ou qualidade de agentes ainda não implementados. O plano cobre a solicitação e os 26 pontos da mensagem aprovada, agora fornecida integralmente pelo usuário. Os anexos e demais trechos históricos não retornados integralmente ficam como reconciliação documental da fase 0, sem alegação de leitura completa.
