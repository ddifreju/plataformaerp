# Pendências — só a Juliana pode resolver

Este arquivo é escrito pelo `gerente-projeto` quando ele encontra algo que
não consegue resolver sozinho. Ele NÃO fica esperando — segue para outra
tarefa e registra aqui.

Revise este arquivo uma vez por dia.

---

## Ferramentas da máquina (BLOQUEIA validação da Fase 0)

Descoberto em 12/08/2026: **a máquina não tem nenhuma ferramenta de
desenvolvimento instalada** além do git. Verificado em PowerShell e Git Bash.

Isso não impediu a Fase 0 de ser **escrita**, mas impede que ela seja
**executada**. O código está pronto e não foi compilado nem rodado uma vez.

Instale, nesta ordem de importância:

- [ ] **JDK 21** — `winget install EclipseAdoptium.Temurin.21.JDK`
      Sem isso o backend não compila.
- [ ] **Maven** — `winget install Apache.Maven`
      Depois de instalar, rode `cd backend && mvn wrapper:wrapper` uma vez.
      Isso gera o `mvnw` e a partir daí o projeto fica autossuficiente.
- [ ] **Docker Desktop** — https://docker.com/products/docker-desktop
      Necessário para `make dev` (Postgres) e para `make test` (Testcontainers).
      Exige WSL2 e reinicialização. É instalação de administrador, por isso
      não foi feita automaticamente.
- [ ] **make** — `winget install ezwinports.make` (use no Git Bash)
      Opcional: todo comando do Makefile é uma linha de shell que pode ser
      copiada à mão.
- [ ] **Node 20+** — `winget install OpenJS.NodeJS.LTS`
      Só necessário na Fase 3 (interface).

**Primeira coisa a rodar depois de instalar:**
```bash
make dev && make migrate && make test
```
Se algo quebrar, é esperado — nada foi executado ainda. O checklist do que
tem maior chance de falhar está em `docs/ESTADO.md`.

## Repositório remoto

- [ ] **Decidir onde hospedar o código** (GitHub/GitLab, público ou privado)
      O git foi inicializado localmente com commits, mas **não há remoto**.
      Enquanto não houver, não existe backup fora desta máquina — se o disco
      falhar, o projeto inteiro se perde. Criar repositório remoto é ação
      externa e com efeito público, por isso não foi feita automaticamente.

## Credenciais e contas (resolva TUDO antes de rodar autônomo)

Marque conforme for conseguindo. Cada item não resolvido é uma parte do
sistema que não pode ser construída nem testada de verdade.

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

## Decisões de negócio pendentes

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

## Como usar

Enquanto uma credencial não existe, o gerente constrói contra **mock**:
adaptador com payload gravado, teste passando, interface funcionando com
dado falso. Quando a credencial chegar, troca o mock pelo real.

Isso significa que a falta de credencial atrasa a VALIDAÇÃO, não a
CONSTRUÇÃO. O sistema é construído mesmo assim.
