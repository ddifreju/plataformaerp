# Pendências — só a Juliana pode resolver

Este arquivo é escrito pelo `gerente-projeto` quando ele encontra algo que
não consegue resolver sozinho. Ele NÃO fica esperando — segue para outra
tarefa e registra aqui.

Revise este arquivo uma vez por dia.

---

## Ferramentas da máquina — RESOLVIDO em 12/08/2026

- [x] **JDK 21** — Temurin 21.0.12 instalado
- [x] **Maven 3.9.9** — instalado. `mvnw` (Maven Wrapper) gerado no `backend/`,
      então o projeto agora é autossuficiente
- [x] **make 4.4.1** — instalado
- [x] **Node 24** — instalado (só necessário na Fase 3)
- [ ] **Docker Desktop** — **instalado, mas o engine não sobe até reiniciar**
      ("Virtualization support not detected"). É o único bloqueio de validação
      que resta. Sem ele não roda: `make dev`, `make migrate` e a parte da
      suíte que usa Testcontainers.
      **Ação: reiniciar o computador.** Depois: `make preparar && make test`.

Estado da validação em 12/08/2026:
- `mvn test-compile`: **BUILD SUCCESS**, 68 fontes principais + 12 de teste
- Testes que não dependem de banco: **26 passando, 0 falhas**
- O que ainda não foi provado: `ddl-auto: validate` contra o Postgres real
  (o risco `char(n)` vs `varchar` e o mapeamento `jsonb`) e toda a suíte de
  isolamento por Testcontainers

## Repositório remoto — RESOLVIDO

- [x] **GitHub**: `https://github.com/ddifreju/plataformaerp.git`
      Push inicial feito pela fundadora. Existe backup fora da máquina.
      **O push continua sendo dela** — eu commito localmente, ela publica.

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
- [ ] **Chave HMAC para hash de documento de cliente**
      Variável `APP_DOCUMENTO_HMAC_CHAVE`. A tabela `cliente` guarda
      `documento_hash` = HMAC-SHA256 do CPF/CNPJ, nunca o documento em claro
      (SHA-256 puro seria quebrável por força bruta: o espaço de CPF é pequeno).
      Gere uma chave aleatória longa e guarde fora do banco e fora do git.
      **Trocar a chave depois invalida todos os hashes existentes** — decida uma
      vez e guarde bem. Sem ela o adaptador não consegue preencher a coluna.

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

## Como usar

Enquanto uma credencial não existe, o gerente constrói contra **mock**:
adaptador com payload gravado, teste passando, interface funcionando com
dado falso. Quando a credencial chegar, troca o mock pelo real.

Isso significa que a falta de credencial atrasa a VALIDAÇÃO, não a
CONSTRUÇÃO. O sistema é construído mesmo assim.
